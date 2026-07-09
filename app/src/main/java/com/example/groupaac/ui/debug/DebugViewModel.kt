package com.example.groupaac.ui.debug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.groupaac.data.entity.SessionEntity
import com.example.groupaac.data.repository.DebugRepository
import com.example.groupaac.model.SignalType
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DebugUiState(
    val activeSessionId: String? = null,
    val activeSession: SessionEntity? = null,
    val statusMessage: String? = null
)

class DebugViewModel(
    private val debugRepository: DebugRepository
) : ViewModel() {
    val uiState = MutableStateFlow(DebugUiState())

    private var sessionObservationJob: Job? = null

    init {
        observeActiveSession()
    }

    private fun observeActiveSession() {
        viewModelScope.launch {
            debugRepository.activeSessionId.collect { sessionId ->
                uiState.update { it.copy(activeSessionId = sessionId) }

                sessionObservationJob?.cancel()
                if (sessionId == null) {
                    uiState.update { it.copy(activeSession = null) }
                } else {
                    sessionObservationJob = viewModelScope.launch {
                        debugRepository.observeSession(sessionId).collect { session ->
                            uiState.update { it.copy(activeSession = session) }
                        }
                    }
                }
            }
        }
    }

    fun setupDemoSession() {
        viewModelScope.launch {
            val sessionId = debugRepository.ensureDemoSession()
            uiState.update {
                it.copy(
                    activeSessionId = sessionId,
                    statusMessage = "Active session: $sessionId"
                )
            }
        }
    }

    fun addAlice() {
        runSessionAction("Alice added") { sessionId ->
            debugRepository.addDebugParticipantAlice(sessionId)
        }
    }

    fun addBob() {
        runSessionAction("Bob added") { sessionId ->
            debugRepository.addDebugParticipantBob(sessionId)
        }
    }

    fun aliceHelp() {
        runSessionAction("Alice Help signal created") { sessionId ->
            debugRepository.createDebugSignal(sessionId, DebugRepository.DEBUG_ALICE_ID, SignalType.HELP)
        }
    }

    fun bobWait() {
        runSessionAction("Bob Wait signal created") { sessionId ->
            debugRepository.createDebugSignal(sessionId, DebugRepository.DEBUG_BOB_ID, SignalType.WAIT)
        }
    }

    fun clearSignals() {
        runSessionAction("Signals cleared") { sessionId ->
            debugRepository.clearDebugSignals(sessionId)
        }
    }

    fun seedMessages() {
        runSessionAction("Sample messages seeded") { sessionId ->
            debugRepository.seedDebugMessages(sessionId)
        }
    }

    private fun runSessionAction(
        successMessage: String,
        action: suspend (String) -> Unit
    ) {
        viewModelScope.launch {
            val sessionId = debugRepository.ensureDemoSession()
            runCatching {
                action(sessionId)
            }.onSuccess {
                uiState.update {
                    it.copy(
                        activeSessionId = sessionId,
                        statusMessage = successMessage
                    )
                }
            }.onFailure { throwable ->
                uiState.update {
                    it.copy(statusMessage = throwable.message ?: "Debug action failed")
                }
            }
        }
    }
}

class DebugViewModelFactory(
    private val debugRepository: DebugRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DebugViewModel::class.java)) {
            return DebugViewModel(debugRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
