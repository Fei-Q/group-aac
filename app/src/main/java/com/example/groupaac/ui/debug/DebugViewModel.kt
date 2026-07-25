package com.example.groupaac.ui.debug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.groupaac.data.entity.SessionEntity
import com.example.groupaac.data.repository.DebugRepository
import com.example.groupaac.model.SignalType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DebugUiState(
    val activeSessionId: String? = null,
    val activeSession: SessionEntity? = null,
    val statusMessage: String? = null
)

class DebugViewModel(
    private val sessionId: String,
    private val debugRepository: DebugRepository
) : ViewModel() {
    val uiState = MutableStateFlow(
        DebugUiState(activeSessionId = sessionId)
    )

    init {
        observeSession()
    }

    private fun observeSession() {
        viewModelScope.launch {
            debugRepository.observeSession(sessionId)
                .collect { session ->
                    uiState.update {
                        it.copy(activeSession = session)
                    }
                }
        }
    }

    fun setupDemoSession() {
        viewModelScope.launch {
            runCatching {
                debugRepository.ensureDemoSession(sessionId)
            }.onSuccess {
                uiState.update {
                    it.copy(
                        activeSessionId = sessionId,
                        statusMessage = "Debug session is ready."
                    )
                }
            }.onFailure { error ->
                uiState.update {
                    it.copy(
                        statusMessage = error.message
                            ?: "Unable to prepare the debug session."
                    )
                }
            }
        }
    }

    fun addAlice() {
        runSessionAction("Alice added") {
            debugRepository.addDebugParticipantAlice(sessionId)
        }
    }

    fun addBob() {
        runSessionAction("Bob added") {
            debugRepository.addDebugParticipantBob(sessionId)
        }
    }

    fun aliceHelp() {
        runSessionAction("Alice Help signal created") {
            debugRepository.createDebugSignal(
                sessionId = sessionId,
                userId = DebugRepository.DEBUG_ALICE_ID,
                type = SignalType.HELP
            )
        }
    }

    fun bobWait() {
        runSessionAction("Bob Wait signal created") {
            debugRepository.createDebugSignal(
                sessionId = sessionId,
                userId = DebugRepository.DEBUG_BOB_ID,
                type = SignalType.WAIT
            )
        }
    }

    fun clearSignals() {
        runSessionAction("Signals cleared") {
            debugRepository.clearDebugSignals(sessionId)
        }
    }

    fun seedMessages() {
        runSessionAction("Sample messages seeded") {
            debugRepository.seedDebugMessages(sessionId)
        }
    }

    private fun runSessionAction(
        successMessage: String,
        action: suspend () -> Unit
    ) {
        viewModelScope.launch {
            runCatching {
                debugRepository.ensureDemoSession(sessionId)
                action()
            }.onSuccess {
                uiState.update {
                    it.copy(
                        activeSessionId = sessionId,
                        statusMessage = successMessage
                    )
                }
            }.onFailure { error ->
                uiState.update {
                    it.copy(
                        statusMessage = error.message
                            ?: "Debug action failed."
                    )
                }
            }
        }
    }
}

class DebugViewModelFactory(
    private val debugRepository: DebugRepository,
    private val sessionId: String
) : ViewModelProvider.Factory {

    constructor(
        debugRepository: DebugRepository
    ) : this(
        debugRepository = debugRepository,
        sessionId = DebugRepository.DEBUG_SESSION_ID
    )

    override fun <T : ViewModel> create(
        modelClass: Class<T>
    ): T {
        if (
            modelClass.isAssignableFrom(
                DebugViewModel::class.java
            )
        ) {
            @Suppress("UNCHECKED_CAST")
            return DebugViewModel(
                sessionId = sessionId,
                debugRepository = debugRepository
            ) as T
        }

        throw IllegalArgumentException(
            "Unknown ViewModel class: ${modelClass.name}"
        )
    }
}
