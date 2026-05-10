package com.feelvision.ui.screens.people

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.feelvision.data.people.PeopleRepository
import com.feelvision.domain.model.Person
import com.feelvision.hardware.HardwareSource
import com.feelvision.speech.SpeechRecognitionManager
import com.feelvision.tts.TTSManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import com.feelvision.facedetection.FaceRecognitionHelper
import javax.inject.Inject

data class PeopleUiState(
    val people    : List<Person> = emptyList(),
    val isLoading : Boolean      = false
)

data class EnrollUiState(
    val personId      : Long?   = null,
    val name          : String  = "",
    val relation      : String  = "",
    val notes         : String  = "",
    val capturedCount : Int     = 0,
    val isSaving      : Boolean = false,
    val savedSuccess  : Boolean = false,
    val error         : String? = null,
    val hint          : String  = "Enter name, then capture photos from different angles",
    val isVoiceActive : Boolean = false,
    val voiceStage    : String  = ""
)

@HiltViewModel
class PeopleViewModel @Inject constructor(
    private val repo             : PeopleRepository,
    private val hardware         : HardwareSource,
    private val tts              : TTSManager,
    private val speechRecognizer : SpeechRecognitionManager,
    private val faceHelper       : FaceRecognitionHelper
) : ViewModel() {

    init {
        viewModelScope.launch {
            repo.load()
        }
    }

    // ── People list ───────────────────────────────────────────────────

    val state: StateFlow<PeopleUiState> =
        repo.getAllPeople()
            .map { PeopleUiState(people = it) }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                PeopleUiState(isLoading = true)
            )

    // ── Enroll ────────────────────────────────────────────────────────

    private val _enrollState = MutableStateFlow(EnrollUiState())
    val enrollState: StateFlow<EnrollUiState> = _enrollState.asStateFlow()

    private var voiceEnrollJob: Job? = null

    fun loadPerson(id: Long) {
        if (id == -1L) {
            _enrollState.value = EnrollUiState()
            return
        }
        viewModelScope.launch {
            val person = repo.getById(id) ?: return@launch
            _enrollState.value = EnrollUiState(
                personId      = person.id,
                name          = person.name,
                relation      = person.relation,
                notes         = person.notes,
                capturedCount = person.photoCount,
                hint          = if (person.photoCount > 0)
                    "${person.photoCount} photos captured. Add more or save."
                else
                    "Position face in frame, then press Capture"
            )
        }
    }

    // ── Form fields ───────────────────────────────────────────────────

    fun updateName(v: String)     = _enrollState.update { it.copy(name = v,     error = null) }
    fun updateRelation(v: String) = _enrollState.update { it.copy(relation = v, error = null) }
    fun updateNotes(v: String)    = _enrollState.update { it.copy(notes = v) }

    // ── Capture ───────────────────────────────────────────────────────

    private suspend fun performCapture(): Boolean {
        val s = _enrollState.value

        // Must have a name before capturing
        if (s.name.isBlank()) {
            _enrollState.update { it.copy(error = "Enter a name before capturing") }
            return false
        }

        val bmp = hardware.captureNow()
        if (bmp == null) {
            _enrollState.update { it.copy(error = "Camera capture failed, try again") }
            return false
        }

        // Create person record on first capture
        val personId = s.personId ?: run {
            val person = repo.enroll(
                name     = s.name.trim(),
                relation = s.relation.trim(),
                notes    = s.notes.trim()
            )
            _enrollState.update { it.copy(personId = person.id) }
            person.id
        }

        val newCount = s.capturedCount + 1
        repo.addPhoto(personId, bmp, newCount)
        bmp.recycle()

        _enrollState.update {
            it.copy(
                capturedCount = newCount,
                error         = null,
                hint          = when {
                    newCount < 3 ->
                        "Good! Capture ${3 - newCount} more from different angles."
                    newCount < 5 ->
                        "Looking good! You can save now or add more photos."
                    else ->
                        "Great coverage! Save when ready."
                }
            )
        }
        return true
    }

    fun captureEnrollPhoto() {
        viewModelScope.launch {
            performCapture()
        }
    }

    // ── Save ──────────────────────────────────────────────────────────

    private suspend fun performSave(): Boolean {
        val s = _enrollState.value

        if (s.name.isBlank()) {
            _enrollState.update { it.copy(error = "Name cannot be empty") }
            return false
        }

        _enrollState.update { it.copy(isSaving = true, error = null) }
        return try {
            if (s.personId != null) {
                // Person already created during capture — just update info
                repo.updateInfo(
                    id       = s.personId,
                    name     = s.name.trim(),
                    relation = s.relation.trim(),
                    notes    = s.notes.trim()
                )
            } else {
                // No photos captured yet — create person with info only
                repo.enroll(
                    name     = s.name.trim(),
                    relation = s.relation.trim(),
                    notes    = s.notes.trim()
                )
            }
            // Generate/update face embeddings from the captured photos
            faceHelper.ensureAllEmbeddingsComputed(repo)

            _enrollState.update { it.copy(isSaving = false, savedSuccess = true) }
            true
        } catch (e: Exception) {
            _enrollState.update {
                it.copy(isSaving = false, error = "Save failed: ${e.message}")
            }
            false
        }
    }

    fun saveEnrolledPerson() {
        viewModelScope.launch {
            performSave()
        }
    }

    // ── Voice Enrollment Flow ─────────────────────────────────────────

    private suspend fun speakAndWait(text: String) {
        _enrollState.update { it.copy(hint = text) }
        tts.speak(text)
        delay(500) // Give TTS brief time to begin
        try {
            withTimeoutOrNull(10000L) {
                tts.isSpeaking.first { !it }
            }
        } catch (e: Exception) {
            // timeout/fallback
        }
    }

    fun startVoiceEnrollment() {
        cancelVoiceEnrollment()
        voiceEnrollJob = viewModelScope.launch {
            _enrollState.update {
                it.copy(
                    isVoiceActive = true,
                    voiceStage    = "Starting...",
                    error         = null,
                    name          = "",
                    relation      = "",
                    notes         = "",
                    capturedCount = 0,
                    personId      = null
                )
            }
            try {
                // 1. Speak & Ask for Name
                _enrollState.update { it.copy(voiceStage = "Prompting Name...") }
                speakAndWait("Please say the name of the person you want to add.")
                
                _enrollState.update { it.copy(voiceStage = "Listening for Name...") }
                tts.playBeep()
                val spokenName = speechRecognizer.waitForSpeech(timeoutMs = 8000L)
                if (spokenName.isNullOrBlank()) {
                    speakAndWait("I didn't hear a name. Voice enrollment cancelled.")
                    return@launch
                }
                updateName(spokenName)

                // 2. Speak & Ask for Relation
                _enrollState.update { it.copy(voiceStage = "Prompting Relation...") }
                speakAndWait("Great! Now say the relationship, for example, family, friend, or colleague.")
                
                _enrollState.update { it.copy(voiceStage = "Listening for Relation...") }
                tts.playBeep()
                val spokenRelation = speechRecognizer.waitForSpeech(timeoutMs = 8000L)
                if (spokenRelation.isNullOrBlank()) {
                    speakAndWait("I didn't hear a relationship. I'll set it to Other.")
                    updateRelation("Other")
                } else {
                    updateRelation(spokenRelation)
                }

                // 3. Automated Captures (3 images)
                speakAndWait("Perfect. Now, please point the camera at their face. I will capture three photos. Please turn the head slightly between captures. Starting capture one.")
                
                // Photo 1
                _enrollState.update { it.copy(voiceStage = "Capturing Photo 1...") }
                delay(1200)
                val cap1 = performCapture()
                if (!cap1) {
                    speakAndWait("First capture failed. Voice enrollment cancelled.")
                    return@launch
                }

                // Photo 2
                speakAndWait("Captured photo one. Turn slightly and hold still for capture two.")
                _enrollState.update { it.copy(voiceStage = "Capturing Photo 2...") }
                delay(1500)
                val cap2 = performCapture()
                if (!cap2) {
                    speakAndWait("Second capture failed. Voice enrollment cancelled.")
                    return@launch
                }

                // Photo 3
                speakAndWait("Captured photo two. Turn slightly and hold still for the final capture.")
                _enrollState.update { it.copy(voiceStage = "Capturing Photo 3...") }
                delay(1500)
                val cap3 = performCapture()
                if (!cap3) {
                    speakAndWait("Third capture failed. Voice enrollment cancelled.")
                    return@launch
                }

                // Save
                speakAndWait("All photos captured successfully. Saving the person.")
                _enrollState.update { it.copy(voiceStage = "Saving...") }
                performSave()

            } catch (e: CancellationException) {
                // Handled cancellation
            } catch (e: Exception) {
                _enrollState.update { it.copy(error = "Voice enrollment failed: ${e.message}") }
            } finally {
                _enrollState.update { it.copy(isVoiceActive = false, voiceStage = "") }
            }
        }
    }

    fun cancelVoiceEnrollment() {
        voiceEnrollJob?.let {
            if (it.isActive) {
                it.cancel()
            }
        }
        speechRecognizer.stopListening()
        tts.silence()
        _enrollState.update { it.copy(isVoiceActive = false, voiceStage = "") }
    }

    // ── Delete ────────────────────────────────────────────────────────

    fun deletePerson(id: Long) = viewModelScope.launch { repo.delete(id) }

    // ── Reset ─────────────────────────────────────────────────────────

    fun resetEnrollState() {
        cancelVoiceEnrollment()
        _enrollState.value = EnrollUiState()
    }

    override fun onCleared() {
        super.onCleared()
        cancelVoiceEnrollment()
    }
}