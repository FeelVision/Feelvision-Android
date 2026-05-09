package com.feelvision.domain.modes

import android.graphics.Bitmap
import android.util.Log
import com.feelvision.data.people.PeopleRepository
import com.feelvision.di.ApplicationScope
import com.feelvision.domain.model.AppMode
import com.feelvision.domain.model.CapturePolicy
import com.feelvision.domain.model.ModeResult
import com.feelvision.domain.model.Person
import com.feelvision.facedetection.FaceRecognitionHelper
import com.feelvision.logging.DebugLogBus
import com.feelvision.logging.DebugLogType
import com.feelvision.tts.TTSManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

class FaceRecStrategy @Inject constructor(
    private val repo: PeopleRepository,
    private val faceHelper: FaceRecognitionHelper,
    @ApplicationScope private val scope: CoroutineScope,
    private val tts: TTSManager,
    private val log: DebugLogBus
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
        return doProcessFrame(bitmap)
    }

    override suspend fun processFrameStreaming(
        bitmap: Bitmap,
        userPrompt: String?,
        onChunk: suspend (String) -> Unit
    ): ModeResult {
        val result = doProcessFrame(bitmap)
        if (result is ModeResult.PersonRecognized) {
            onChunk("${result.name}, your ${result.relation}")
        } else if (result is ModeResult.UnknownPerson) {
            onChunk("Unknown person")
        }
        return result
    }

    private suspend fun doProcessFrame(bitmap: Bitmap): ModeResult {
        if (bitmap.isRecycled || bitmap.width == 0 || bitmap.height == 0) {
            log.log(DebugLogType.ERROR, "FACE", "Invalid bitmap")
            return ModeResult.Error("Invalid bitmap")
        }

        return try {
            val faceBoxes = faceHelper.detectFaces(bitmap)
            if (faceBoxes.isEmpty()) {
                Log.d(TAG, "No faces detected in current frame.")
                return ModeResult.NoResult
            }

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

            if (matchedPeople.isEmpty() && unknownCount == 0) {
                return ModeResult.NoResult
            }

            // Formulate speakable text
            val announcement = buildAnnouncementText(matchedPeople, unknownCount)
            val currentDetectedSet = matchedPeople.map { "${it.id}_${it.name}" }.toSet() + "unknown_$unknownCount"

            // Smart TTS throttle to prevent spamming
            val hasChanged = currentDetectedSet != lastDetectedSet
            val isCooldownOver = (System.currentTimeMillis() - lastSpokenTime) > COOLDOWN_MS

            if (hasChanged || isCooldownOver) {
                lastDetectedSet = currentDetectedSet
                lastSpokenTime  = System.currentTimeMillis()
                tts.speak(announcement)
                log.log(DebugLogType.INFERENCE, "FACE", "Announced: $announcement")
            }

            // Determine appropriate ModeResult for display
            when {
                matchedPeople.isNotEmpty() -> {
                    val first = matchedPeople.first()
                    ModeResult.PersonRecognized(
                        name       = first.name,
                        relation   = first.relation,
                        confidence = 1f
                    )
                }
                unknownCount > 0 -> {
                    ModeResult.UnknownPerson(confidence = 1f)
                }
                else -> {
                    ModeResult.NoResult
                }
            }
        } catch (e: Exception) {
            log.log(DebugLogType.ERROR, "FACE", "Frame processing crashed: ${e.message}")
            ModeResult.Error(e.message ?: "Unknown error")
        } finally {
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
        }
    }

    private fun buildAnnouncementText(matched: List<Person>, unknowns: Int): String {
        val parts = mutableListOf<String>()
        
        if (matched.isNotEmpty()) {
            val matchedNames = matched.map { person ->
                if (person.relation.isNotBlank()) {
                    "${person.name}, your ${person.relation}"
                } else {
                    person.name
                }
            }
            if (matchedNames.size == 1) {
                parts.add(matchedNames.first())
            } else {
                val listExceptLast = matchedNames.dropLast(1).joinToString(", ")
                parts.add("$listExceptLast and ${matchedNames.last()}")
            }
        }

        if (unknowns > 0) {
            val unknownLabel = if (unknowns == 1) "one unknown person" else "$unknowns unknown people"
            parts.add(unknownLabel)
        }

        val combined = when (parts.size) {
            0 -> ""
            1 -> parts.first()
            else -> {
                val listExceptLast = parts.dropLast(1).joinToString(", ")
                "$listExceptLast, and ${parts.last()}"
            }
        }

        return "I see $combined."
    }
}