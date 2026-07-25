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
import com.example.groupaac.model.MessageTarget
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
    private val sessionId: String,
    private val accountRepository: AccountRepository,
    private val settingsRepository: SettingsRepository,
    private val sessionRepository: SessionRepository,
    private val messageRepository: MessageRepository,
    private val signalRepository: SignalRepository,
    private val facilitatorRepository: FacilitatorRepository
) : ViewModel() {

    val uiState = MutableStateFlow(
        FacilitatorUiState(sessionId = sessionId)
    )

    private var sessionObservationJob: Job? = null
    private var settingsObservationJob: Job? = null

    init {
        observeActiveFacilitator()
        sessionObservationJob = observeSession(sessionId)
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
                    uiState.update { state ->
                        state.copy(
                            facilitator = user,
                            isHost =
                                state.session?.hostUserId == user?.id
                        )
                    }

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

    private fun observeSession(sessionId: String): Job {
        return viewModelScope.launch {
            launch {
                sessionRepository.observeSession(sessionId).collect { session ->
                    uiState.update { state ->
                        state.copy(
                            session = session,
                            isHost =
                                session?.hostUserId ==
                                    state.facilitator?.id
                        )
                    }
                }
            }

            launch {
                sessionRepository.observePendingJoinRequests(sessionId)
                    .collect { requests ->
                        uiState.update {
                            it.copy(pendingJoinRequests = requests)
                        }
                    }
            }

            launch {
                combine(
                    facilitatorRepository.observeParticipantStats(sessionId),
                    signalRepository.observeActiveSignals(sessionId),
                    messageRepository.observeMessagesWithAttachments(sessionId)
                ) { stats, signals, messages ->
                    buildOverview(stats, signals, messages) to signals
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
        signals: List<SignalWithUser>,
        messages: List<com.example.groupaac.data.dao.MessageWithSenderAndAttachments>
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

            val lastGroupMessageAt = messages
                .asSequence()
                .map { it.message }
                .filter { it.senderUserId == row.userId && it.target == MessageTarget.GROUP }
                .maxOfOrNull { it.createdAt }

            val lastPrivateMessageAt = messages
                .asSequence()
                .map { it.message }
                .filter {
                    it.senderUserId == row.userId &&
                        (it.target == MessageTarget.FACILITATOR || it.target == MessageTarget.PRIVATE)
                }
                .maxOfOrNull { it.createdAt }

            val lastActivity = listOfNotNull(
                lastGroupMessageAt,
                lastPrivateMessageAt,
                row.lastSignalAt
            ).maxOrNull()

            val lastActivityLabel = when (lastActivity) {
                null -> "Last activity: no activity yet"
                lastGroupMessageAt -> "Last activity: group message"
                lastPrivateMessageAt -> "Last activity: private message"
                else -> "Last activity: signal"
            }

            val elapsedText = when (lastActivity) {
                null -> "Time: —"
                else -> {
                    val elapsed = TimeUtils.elapsedSince(lastActivity)
                    val agoLabel = if (elapsed == "now") "just now" else "$elapsed ago"
                    "Time: ${TimeUtils.clockTime(lastActivity)} ($agoLabel)"
                }
            }

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
                lastActivityLabel = lastActivityLabel,
                elapsedLabel = elapsedText,
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

    fun approveJoinRequest(requestId: String) {
        val facilitator = uiState.value.facilitator ?: return
        viewModelScope.launch {
            sessionRepository.approveJoinRequest(
                requestId = requestId,
                decidedByUserId = facilitator.id
            )
        }
    }

    fun declineJoinRequest(requestId: String) {
        val facilitator = uiState.value.facilitator ?: return
        viewModelScope.launch {
            sessionRepository.declineJoinRequest(
                requestId = requestId,
                decidedByUserId = facilitator.id
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
    private val sessionId: String,
    private val accountRepository: AccountRepository,
    private val settingsRepository: SettingsRepository,
    private val sessionRepository: SessionRepository,
    private val messageRepository: MessageRepository,
    private val signalRepository: SignalRepository,
    private val facilitatorRepository: FacilitatorRepository
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(
        modelClass: Class<T>
    ): T {
        if (
            modelClass.isAssignableFrom(
                FacilitatorViewModel::class.java
            )
        ) {
            @Suppress("UNCHECKED_CAST")
            return FacilitatorViewModel(
                sessionId = sessionId,
                accountRepository = accountRepository,
                settingsRepository = settingsRepository,
                sessionRepository = sessionRepository,
                messageRepository = messageRepository,
                signalRepository = signalRepository,
                facilitatorRepository = facilitatorRepository
            ) as T
        }

        throw IllegalArgumentException(
            "Unknown ViewModel class: ${modelClass.name}"
        )
    }
}
