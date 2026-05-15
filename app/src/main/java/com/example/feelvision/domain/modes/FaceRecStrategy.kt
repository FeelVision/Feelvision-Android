package com.feelvision.domain.modes

import android.graphics.Bitmap
import android.util.Log
import com.feelvision.data.people.PeopleRepository
import com.feelvision.data.settings.SettingsRepository
import com.feelvision.di.ApplicationScope
import com.feelvision.domain.model.AppMode
import com.feelvision.domain.model.CapturePolicy
import com.feelvision.domain.model.ModeResult
import com.feelvision.domain.model.Person
import com.feelvision.facedetection.FaceRecognitionHelper
import com.feelvision.inference.GemmaInferenceManager
import com.feelvision.inference.InferenceResult
import com.feelvision.inference.ModePrompts
import com.feelvision.logging.DebugLogBus
import com.feelvision.logging.DebugLogType
import com.feelvision.tts.TTSManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

class FaceRecStrategy @Inject constructor(
    private val repo: PeopleRepository,
    private val faceHelper: FaceRecognitionHelper,
    @ApplicationScope private val scope: CoroutineScope,
    private val tts: TTSManager,
    private val log: DebugLogBus,
    private val gemma: GemmaInferenceManager,
    private val settings: SettingsRepository
) : ModeStrategy {

    private val TAG = "FaceRecStrategy"
    override val mode          = AppMode.Face
    override val capturePolicy = CapturePolicy.SingleShot

    // Cooldown state for TTS announcements to prevent repetitive speaking
    private var lastDetectedSet = setOf<String>()
    private var lastSpokenTime  = 0L
    private val COOLDOWN_MS     = 15_000L

    override fun activate() {
        tts.speak("People mode active.")
        log.log(DebugLogType.MODE, "FACE", "Activated — continuous capture")
        
        // Trigger background compilation of embeddings for any enrolled people without embeddings
        scope.launch {
            repo.load()
            faceHelper.ensureAllEmbeddingsComputed(repo)
        }
    }

    override fun deactivate() {
        log.log(DebugLogType.MODE, "FACE", "Deactivated")
        faceHelper.close()
    }

    override suspend fun processFrame(bitmap: Bitmap, userPrompt: String?): ModeResult {
        return processFrameStreaming(bitmap, userPrompt) { }
    }

    override suspend fun processFrameStreaming(
        bitmap: Bitmap,
        userPrompt: String?,
        onChunk: suspend (String) -> Unit
    ): ModeResult {
        if (bitmap.isRecycled || bitmap.width == 0 || bitmap.height == 0) {
            log.log(DebugLogType.ERROR, "FACE", "Invalid bitmap")
            return ModeResult.Error("Invalid bitmap")
        }

        return try {
            val language = try {
                settings.language.first()
            } catch (e: Exception) {
                "English"
            }

            val faceBoxes = faceHelper.detectFaces(bitmap)
            var announcement = ""
            var bestPerson: Person? = null
            var shouldSpeak = true

            if (faceBoxes.isNotEmpty()) {
                Log.d(TAG, "Detected ${faceBoxes.size} faces in frame. Extracting embeddings...")
                val enrolledPeople = repo.getAllPeople().first()
                val matchedPeople = mutableListOf<Person>()
                var unknownCount = 0

                for (bbox in faceBoxes) {
                    val embedding = faceHelper.extractEmbedding(bitmap, bbox)
                    if (embedding == null) {
                        unknownCount++
                        continue
                    }

                    // Log similarity with every enrolled photo in the system
                    faceHelper.logDetailedSimilarityScores(embedding, repo)

                    var bestMatch: Person? = null
                    var minDistance = Float.MAX_VALUE

                    for (person in enrolledPeople) {
                        if (person.embedding.isEmpty()) continue
                        
                        val distance = faceHelper.l2Distance(embedding, person.embedding.toFloatArray())
                        if (distance < 0.95f && distance < minDistance) {
                            minDistance = distance
                            bestMatch = person
                        }
                    }

                    if (bestMatch != null) {
                        matchedPeople.add(bestMatch)
                    } else {
                        unknownCount++
                    }
                }

                announcement = ModePrompts.buildAnnouncementText(matchedPeople, unknownCount, language)
                val currentDetectedSet = matchedPeople.map { "${it.id}_${it.name}" }.toSet() + "unknown_$unknownCount"

                // Smart TTS throttle to prevent spamming
                val hasChanged = currentDetectedSet != lastDetectedSet
                val isCooldownOver = (System.currentTimeMillis() - lastSpokenTime) > COOLDOWN_MS

                shouldSpeak = hasChanged || isCooldownOver

                if (shouldSpeak) {
                    lastDetectedSet = currentDetectedSet
                    lastSpokenTime  = System.currentTimeMillis()
                    log.log(DebugLogType.INFERENCE, "FACE", "Announced (queued): $announcement")
                }

                bestPerson = matchedPeople.firstOrNull()
            } else {
                Log.d(TAG, "No faces detected in current frame.")
                val isCooldownOver = (System.currentTimeMillis() - lastSpokenTime) > COOLDOWN_MS
                shouldSpeak = isCooldownOver
                if (shouldSpeak) {
                    lastSpokenTime = System.currentTimeMillis()
                }
            }

            if (!gemma.isReady()) {
                if (shouldSpeak && announcement.isNotEmpty()) {
                    tts.speak(announcement)
                    onChunk(announcement)
                }
                return when {
                    bestPerson != null -> ModeResult.PersonRecognized(
                        name = bestPerson.name,
                        relation = bestPerson.relation,
                        confidence = 1f
                    )
                    announcement.contains("unknown") -> ModeResult.UnknownPerson(confidence = 1f)
                    else -> ModeResult.NoResult
                }
            }

            val promptToUse = if (announcement.isNotEmpty()) {
                "The user already knows who is in the image ($announcement). Write a continuation describing what they are wearing, what they are doing, and the background. DO NOT say 'I see a person' or 'There is a man'. Start directly with words like 'who is', 'wearing', 'standing', or 'sitting'. Keep it to 1-2 lines."
            } else {
                userPrompt ?: "Provide a 1-2 line description of what is happening in the scene."
            }

            log.log(DebugLogType.INFERENCE, "FACE", "Calling gemma for background description")

            val sentenceBuffer = StringBuilder()
            val fullText = StringBuilder()
            var hadError: InferenceResult.Failure? = null

            if (announcement.isNotEmpty()) {
                sentenceBuffer.append(announcement.trimEnd('.')).append(" ")
            }

            gemma.generateStream(
                prompt           = promptToUse,
                images           = listOf(bitmap),
                baseSystemPrompt = ModePrompts.DEFAULT,
                modeTag          = "FACE",
                responseLanguage = language
            ).collect { result ->
                when (result) {
                    is InferenceResult.Streaming -> {
                        fullText.append(result.partial)
                        sentenceBuffer.append(result.partial)
                        val text = sentenceBuffer.toString()
                        val lastBoundary = text.lastIndexOfAny(charArrayOf('.', '!', '?', '\n'))
                        if (lastBoundary >= 0) {
                            val toSpeak = text.substring(0, lastBoundary + 1).trim()
                            if (toSpeak.isNotEmpty()) {
                                if (shouldSpeak) {
                                    tts.speakChunk(toSpeak)
                                }
                                onChunk(toSpeak)
                            }
                            sentenceBuffer.clear()
                            sentenceBuffer.append(text.substring(lastBoundary + 1))
                        }
                    }
                    is InferenceResult.Failure -> { hadError = result }
                    is InferenceResult.NotReady -> { hadError = InferenceResult.Failure("Not ready") }
                    else -> { }
                }
            }

            val remaining = sentenceBuffer.toString().trim()
            if (remaining.isNotEmpty()) {
                if (shouldSpeak) {
                    tts.speakChunk(remaining)
                }
                onChunk(remaining)
            }

            val finalDescription = if (announcement.isNotEmpty()) {
                val generated = fullText.toString().trim()
                if (generated.isNotEmpty() && generated.first().isLowerCase()) {
                    "${announcement.trimEnd('.')} $generated"
                } else {
                    "$announcement $generated"
                }
            } else {
                fullText.toString().trim()
            }

            if (hadError != null) {
                log.log(DebugLogType.ERROR, "FACE", "stream failed: ${hadError!!.error}")
                return when {
                    bestPerson != null -> ModeResult.PersonRecognized(
                        name = bestPerson.name,
                        relation = bestPerson.relation,
                        confidence = 1f
                    )
                    announcement.contains("unknown") -> ModeResult.UnknownPerson(confidence = 1f)
                    else -> ModeResult.Error(hadError!!.error)
                }
            } else if (finalDescription.isNotEmpty()) {
                return ModeResult.NarrationText(finalDescription)
            } else {
                return ModeResult.NoResult
            }

        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.log(DebugLogType.ERROR, "FACE", "Frame processing crashed: ${e.message}")
            ModeResult.Error(e.message ?: "Unknown error")
        } finally {
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
        }
    }

}