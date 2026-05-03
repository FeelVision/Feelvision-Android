package com.feelvision.ui.screens.people

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.feelvision.data.people.PeopleRepository
import com.feelvision.domain.model.Person
import com.feelvision.hardware.HardwareSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PeopleUiState(val people: List<Person> = emptyList())

data class EnrollUiState(
    val name: String = "",
    val relation: String = "",
    val capturedCount: Int = 0,
    val hint: String = "Position face in frame, then press Capture"
)

@HiltViewModel
class PeopleViewModel @Inject constructor(
    private val repo: PeopleRepository,
    private val hardware: HardwareSource
) : ViewModel() {

    val state: StateFlow<PeopleUiState> =
        repo.getAllPeople().map { PeopleUiState(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PeopleUiState())

    private val _enrollState = MutableStateFlow(EnrollUiState())
    val enrollState: StateFlow<EnrollUiState> = _enrollState.asStateFlow()

    fun updateName(v: String) = _enrollState.update { it.copy(name = v) }
    fun updateRelation(v: String) = _enrollState.update { it.copy(relation = v) }

    fun captureEnrollPhoto() {
        viewModelScope.launch {
            val bmp = hardware.captureNow() ?: return@launch
            val count = _enrollState.value.capturedCount + 1
            _enrollState.update {
                it.copy(
                    capturedCount = count,
                    hint = when {
                        count < 3 -> "Good. Capture ${3 - count} more from different angles."
                        count < 5 -> "Looking good! You can save now or add more photos."
                        else -> "Great coverage! Save when ready."
                    }
                )
            }
        }
    }

    fun saveEnrolledPerson() {
        viewModelScope.launch {
            val s = _enrollState.value
            if (s.name.isBlank()) return@launch
            repo.enroll(s.name.trim(), s.relation.trim())
            _enrollState.value = EnrollUiState()
        }
    }

    fun deletePerson(id: Long) = viewModelScope.launch { repo.delete(id) }
}