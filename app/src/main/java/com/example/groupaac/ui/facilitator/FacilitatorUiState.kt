package com.example.groupaac.ui.facilitator

import com.example.groupaac.data.dao.MessageWithSenderAndAttachments
import com.example.groupaac.data.dao.NoteWithParticipant
import com.example.groupaac.data.dao.QuickLogWithParticipant
import com.example.groupaac.data.dao.SignalWithUser
import com.example.groupaac.data.entity.DisplayStateEntity
import com.example.groupaac.data.entity.SessionEntity
import com.example.groupaac.data.entity.SessionJoinRequestEntity
import com.example.groupaac.data.entity.UserEntity
import com.example.groupaac.data.entity.UserSettingsEntity
import com.example.groupaac.model.ParticipantOverview
import com.example.groupaac.model.SessionSummaryUi

data class FacilitatorUiState(
    val facilitator: UserEntity? = null,
    val settings: UserSettingsEntity? = null,
    val sessionId: String? = null,
    val session: SessionEntity? = null,
    val pendingJoinRequests: List<SessionJoinRequestEntity> = emptyList(),
    val isHost: Boolean = false,
    val participants: List<ParticipantOverview> = emptyList(),
    val activeSignals: List<SignalWithUser> = emptyList(),
    val messages: List<MessageWithSenderAndAttachments> = emptyList(),
    val displayedMessage: MessageWithSenderAndAttachments? = null,
    val displayState: DisplayStateEntity? = null,
    val notes: List<NoteWithParticipant> = emptyList(),
    val quickLogs: List<QuickLogWithParticipant> = emptyList(),
    val selectedParticipantId: String? = null,
    val summary: SessionSummaryUi = SessionSummaryUi(
        participantCount = 0,
        sharedItemCount = 0,
        supportRequestCount = 0,
        savedItemCount = 0,
        participantRows = emptyList()
    )
)
