package com.example.groupaac.ui.facilitator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.groupaac.data.dao.ParticipantStatsRow
import com.example.groupaac.data.dao.SignalWithUser
import com.example.groupaac.data.entity.UserSettingsEntity
import com.example.groupaac.data.repository.AccountRepository
import com.example.groupaac.data.repository.FacilitatorRepository
import com.example.groupaac.data.repository.MessageRepository
import com.example.groupaac.data.repository.SessionRepository
import com.example.groupaac.data.repository.SettingsRepository
import com.example.groupaac.data.repository.SignalRepository
import com.example.groupaac.model.ParticipantOverview
import com.example.groupaac.model.SessionSummaryUi
import com.example.groupaac.model.SignalState
import com.example.groupaac.util.TimeUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class FacilitatorViewModel(
    private val accountRepository: AccountRepository,
    private val settingsRepository: SettingsRepository,
    private val sessionRepository: SessionRepository,
    private val messageRepository: MessageRepository,
    private val signalRepository: SignalRepository,
    private val facilitatorRepository: FacilitatorRepository
) : ViewModel() {

    val uiState = MutableStateFlow(FacilitatorUiState())

    private var sessionObservationJob: Job? = null
    private var settingsObservationJob: Job? = null

    init {
        observeActiveFacilitator()
        observeLastSession()
    }

    private fun observeActiveFacilitator() {
        viewModelScope.launch {
            accountRepository.activeUserId
                .flatMapLatest { id ->
                    if (id == null) {
                        flowOf(null)
                    } else {
                        accountRepository.observeUser(id)
                    }
                }
                .collect { user ->
                    uiState.update { it.copy(facilitator = user) }

                    settingsObservationJob?.cancel()
                    if (user != null) {
                        settingsObservationJob = observeSettings(user.id)
                    } else {
                        uiState.update { it.copy(settings = null) }
                    }
                }
        }
    }

    private fun observeSettings(userId: String): Job {
        return viewModelScope.launch {
            settingsRepository.observeSettings(userId)
                .collect { settings ->
                    uiState.update { it.copy(settings = settings) }
                }
        }
    }

    private fun observeLastSession() {
        viewModelScope.launch {
            sessionRepository.lastSessionId.collect { sessionId ->
                uiState.update {
                    it.copy(
                        sessionId = sessionId,
                        session = null,
                        messages = emptyList(),
                        displayedMessage = null
                    )
                }

                sessionObservationJob?.cancel()

                if (sessionId != null) {
                    sessionObservationJob = observeSession(sessionId)
                }
            }
        }
    }

    private fun observeSession(sessionId: String): Job {
        return viewModelScope.launch {
            launch {
                sessionRepository.observeSession(sessionId).collect { session ->
                    uiState.update { it.copy(session = session) }
                }
            }

            launch {
                combine(
                    facilitatorRepository.observeParticipantStats(sessionId),
                    signalRepository.observeActiveSignals(sessionId)
                ) { stats, signals ->
                    buildOverview(stats, signals) to signals
                }.collect { (overview, signals) ->
                    uiState.update {
                        it.copy(
                            participants = overview,
                            activeSignals = signals
                        )
                    }
                }
            }

            launch {
                messageRepository.observeMessagesWithAttachments(sessionId).collect { rows ->
                    uiState.update { it.copy(messages = rows) }
                }
            }

            launch {
                messageRepository.observeDisplayedMessageWithAttachments(sessionId).collect { row ->
                    uiState.update { it.copy(displayedMessage = row) }
                }
            }

            launch {
                facilitatorRepository.observeNotes(sessionId).collect { notes ->
                    uiState.update { it.copy(notes = notes) }
                }
            }

            launch {
                facilitatorRepository.observeQuickLogs(sessionId).collect { logs ->
                    uiState.update { it.copy(quickLogs = logs) }
                }
            }

            launch {
                combine(
                    facilitatorRepository.observeParticipantCount(sessionId),
                    facilitatorRepository.observeSharedItemCount(sessionId),
                    facilitatorRepository.observeSupportRequestCount(sessionId),
                    facilitatorRepository.observeSavedItemCount(sessionId)
                ) { participantCount, itemCount, requestCount, savedCount ->
                    SessionSummaryUi(
                        participantCount = participantCount,
                        sharedItemCount = itemCount,
                        supportRequestCount = requestCount,
                        savedItemCount = savedCount,
                        participantRows = uiState.value.participants
                    )
                }.collect { summary ->
                    uiState.update { it.copy(summary = summary) }
                }
            }
        }
    }

    private fun buildOverview(
        stats: List<ParticipantStatsRow>,
        signals: List<SignalWithUser>
    ): List<ParticipantOverview> {
        val settings = uiState.value.settings
        val lowParticipationThresholdMinutes =
            settings?.facilitatorLowParticipationThresholdMinutes ?: 10

        val now = System.currentTimeMillis()
        val lowParticipationThresholdMillis =
            lowParticipationThresholdMinutes * 60 * 1000L

        return stats.map { row ->
            val signal = signals
                .filter { it.userId == row.userId }
                .minByOrNull { it.type.priority }

            val lastActivity = listOfNotNull(
                row.lastMessageAt,
                row.lastSignalAt
            ).maxOrNull()

            val isLowParticipation =
                settings?.facilitatorShowLowParticipationAlerts == true &&
                        (
                                row.messageCount == 0 ||
                                        lastActivity == null ||
                                        now - lastActivity > lowParticipationThresholdMillis
                                )

            ParticipantOverview(
                userId = row.userId,
                displayName = row.displayName,
                activeSignal = signal?.type,
                signalState = signal?.state,
                lastActivityLabel = if (lastActivity == null) {
                    "No activity yet"
                } else {
                    "Last activity ${TimeUtils.clockTime(lastActivity)}"
                },
                elapsedLabel = if (lastActivity == null) {
                    "—"
                } else {
                    TimeUtils.elapsedSince(lastActivity)
                },
                messageCount = row.messageCount,
                supportRequests = row.supportRequests,
                isLowParticipation = isLowParticipation
            )
        }
    }

    fun selectParticipant(userId: String?) {
        uiState.update { it.copy(selectedParticipantId = userId) }
    }

    fun resolveParticipant(userId: String) {
        val sessionId = uiState.value.sessionId ?: return

        viewModelScope.launch {
            signalRepository.resolveSignalsForUser(sessionId, userId)
        }
    }

    @Suppress("DEPRECATION")
    fun toggleSnoozeParticipant(userId: String) {
        val signal = uiState.value.activeSignals
            .firstOrNull {
                it.userId == userId &&
                        (it.state == SignalState.CURRENT || it.state == SignalState.SNOOZED || it.state == SignalState.ACTIVE)
            }
            ?: return

        viewModelScope.launch {
            when (signal.state) {
                SignalState.SNOOZED -> signalRepository.unsnoozeSignal(signal.id)
                else -> signalRepository.snoozeSignal(signal.id)
            }
        }
    }

    fun addNote(participantUserId: String?, text: String) {
        val state = uiState.value
        val sessionId = state.sessionId ?: return
        val facilitator = state.facilitator ?: return

        viewModelScope.launch {
            facilitatorRepository.addNote(
                sessionId = sessionId,
                participantUserId = participantUserId,
                facilitatorUserId = facilitator.id,
                text = text
            )
        }
    }

    fun quickLog(participantUserId: String, label: String) {
        val state = uiState.value
        val sessionId = state.sessionId ?: return
        val facilitator = state.facilitator ?: return

        viewModelScope.launch {
            facilitatorRepository.quickLog(
                sessionId = sessionId,
                participantUserId = participantUserId,
                facilitatorUserId = facilitator.id,
                label = label
            )
        }
    }

    fun saveMessage(messageId: String) {
        viewModelScope.launch {
            messageRepository.saveMessage(messageId)
        }
    }

    fun displayMessage(messageId: String) {
        val sessionId = uiState.value.sessionId ?: return

        viewModelScope.launch {
            val settings = uiState.value.settings

            if (settings?.monitorRequireManualApproval == true) {
                messageRepository.displayMessage(sessionId, messageId)
            } else {
                messageRepository.displayMessage(sessionId, messageId)
            }
        }
    }

    fun deleteMessage(messageId: String) {
        viewModelScope.launch {
            messageRepository.deleteMessage(messageId)
        }
    }

    fun clearDisplay() {
        val sessionId = uiState.value.sessionId ?: return

        viewModelScope.launch {
            messageRepository.clearDisplay(sessionId)
        }
    }

    fun updateSettings(settings: UserSettingsEntity) {
        viewModelScope.launch {
            settingsRepository.updateSettings(settings)
        }
    }

    fun updateLowParticipationAlerts(enabled: Boolean) {
        val settings = uiState.value.settings ?: return
        updateSettings(
            settings.copy(
                facilitatorShowLowParticipationAlerts = enabled
            )
        )
    }

    fun updateLowParticipationThreshold(minutes: Int) {
        val settings = uiState.value.settings ?: return
        updateSettings(
            settings.copy(
                facilitatorLowParticipationThresholdMinutes = minutes.coerceIn(5, 60)
            )
        )
    }

    fun updateMonitorManualApproval(required: Boolean) {
        val settings = uiState.value.settings ?: return
        updateSettings(
            settings.copy(
                monitorRequireManualApproval = required
            )
        )
    }

    fun updateSoundEnabled(enabled: Boolean) {
        val settings = uiState.value.settings ?: return
        updateSettings(
            settings.copy(
                soundEnabled = enabled
            )
        )
    }

    fun updateKeepScreenAwake(enabled: Boolean) {
        val settings = uiState.value.settings ?: return
        updateSettings(
            settings.copy(
                keepScreenAwake = enabled
            )
        )
    }
}

class FacilitatorViewModelFactory(
    private val accountRepository: AccountRepository,
    private val settingsRepository: SettingsRepository,
    private val sessionRepository: SessionRepository,
    private val messageRepository: MessageRepository,
    private val signalRepository: SignalRepository,
    private val facilitatorRepository: FacilitatorRepository
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return FacilitatorViewModel(
            accountRepository = accountRepository,
            settingsRepository = settingsRepository,
            sessionRepository = sessionRepository,
            messageRepository = messageRepository,
            signalRepository = signalRepository,
            facilitatorRepository = facilitatorRepository
        ) as T
    }
}
