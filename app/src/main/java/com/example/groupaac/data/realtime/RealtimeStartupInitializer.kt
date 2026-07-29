package com.example.groupaac.data.realtime

import com.example.groupaac.model.ActiveSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed interface AppStartupState {
    data object Initializing : AppStartupState
    data object Ready : AppStartupState
    data class Failed(
        val message: String
    ) : AppStartupState
}

sealed interface StartupRecoveryState {
    data object None : StartupRecoveryState
    data object Reconciled : StartupRecoveryState
    data class RecoveryRequired(
        val message: String
    ) : StartupRecoveryState
}

fun interface StartupSessionRecovery {
    suspend fun reconcile(
        userId: String,
        activeSession: ActiveSession
    ): StartupRecoveryState
}

class RealtimeStartupInitializer(
    private val scope: CoroutineScope,
    private val activeUserId: Flow<String?>,
    private val activeSessionProvider:
        (String) -> Flow<ActiveSession?>,
    private val realtimeClientManager: RealtimeClientManager,
    private val startSessionSubscriptions:
        () -> Unit,
    private val sessionRecovery: StartupSessionRecovery = StartupSessionRecovery { _, _ ->
        StartupRecoveryState.None
    }
) {
    private val _startupState = MutableStateFlow<AppStartupState>(
        AppStartupState.Initializing
    )
    val startupState: StateFlow<AppStartupState> =
        _startupState.asStateFlow()

    private val _recoveryState =
        MutableStateFlow<StartupRecoveryState>(
            StartupRecoveryState.None
        )
    val recoveryState: StateFlow<StartupRecoveryState> =
        _recoveryState.asStateFlow()

    private var startupJob: Job? = null

    fun start() {
        if (startupJob?.isActive == true) {
            return
        }

        startupJob = scope.launch {
            restoreStartupState()
        }
    }

    private suspend fun restoreStartupState() {
        _startupState.value = AppStartupState.Initializing
        _recoveryState.value = StartupRecoveryState.None

        try {
            val persistedUserId = activeUserId.first()
            if (persistedUserId != null) {
                realtimeClientManager.activateUser(
                    persistedUserId
                )
            }

            val restoredSession =
                persistedUserId?.let { userId ->
                    activeSessionProvider(userId)
                        .first()
                }

            startSessionSubscriptions()

            if (
                persistedUserId != null &&
                restoredSession != null
            ) {
                _recoveryState.value =
                    sessionRecovery.reconcile(
                        userId = persistedUserId,
                        activeSession = restoredSession
                    )
            }

            _startupState.value =
                AppStartupState.Ready
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            _startupState.value =
                AppStartupState.Failed(
                    error.message
                        ?: "Unable to restore application state."
                )
        }
    }
}
