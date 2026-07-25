package com.example.groupaac.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.groupaac.model.FacilitatorDefaultTab
import com.example.groupaac.model.HomeExperience
import com.example.groupaac.model.ParticipantDefaultTab
import com.example.groupaac.model.UserRole

@Entity(
    tableName = "user_settings",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["userId"], unique = true)
    ]
)
data class UserSettingsEntity(
    @PrimaryKey
    val userId: String,

    // Common profile/session defaults
    @Deprecated("Account-level default roles are retained only for Room compatibility.")
    val defaultRole: UserRole = UserRole.PARTICIPANT,
    val defaultSessionName: String = "Group AAC Session",
    val homeExperience: HomeExperience = HomeExperience.SIMPLE,

    // Common accessibility settings
    val textScale: Float = 1.0f,
    val highContrastEnabled: Boolean = false,
    val soundEnabled: Boolean = true,
    val reduceMotionEnabled: Boolean = false,
    val keepScreenAwake: Boolean = true,

    // Participant-specific settings
    val participantDefaultTab: ParticipantDefaultTab = ParticipantDefaultTab.SHARE,
    val participantReadAloudEnabled: Boolean = true,
    val participantReadAloudVoice: String = "system_default",
    val participantReadAloudRate: Float = 1.0f,
    val participantSaveDraftsAutomatically: Boolean = true,
    val participantDefaultShareTarget: String = "group",
    val participantShowTypingStatus: Boolean = true,

    // Facilitator-specific settings
    val facilitatorDefaultTab: FacilitatorDefaultTab = FacilitatorDefaultTab.PARTICIPANTS,
    val facilitatorShowLowParticipationAlerts: Boolean = true,
    val facilitatorLowParticipationThresholdMinutes: Int = 10,
    val facilitatorShowHelpAlerts: Boolean = true,
    val facilitatorShowRepeatAlerts: Boolean = true,
    val facilitatorShowWaitingAlerts: Boolean = true,
    val facilitatorDefaultSnoozeMinutes: Int = 3,
    val facilitatorAutoClearResolvedAlerts: Boolean = true,
    val facilitatorQuickLogEnabled: Boolean = true,
    val facilitatorAutoSaveNotes: Boolean = true,
    val facilitatorShowPrivateMessagesInLog: Boolean = true,
    val facilitatorIncludeResolvedSignalsInSummary: Boolean = true,

    // Shared monitor command preferences
    val monitorAutoDisplayGroupMessages: Boolean = false,
    val monitorRequireManualApproval: Boolean = true,
    val monitorPlaySoundOnNewMessage: Boolean = true,
    val monitorClearOnSessionEnd: Boolean = true,
    val monitorShowSenderName: Boolean = true,
    val monitorShowTimestamp: Boolean = true,
    val monitorDefaultDisplaySeconds: Int = 0,

    // Data retention
    val saveSessionHistory: Boolean = true,
    val saveFacilitatorNotes: Boolean = true,
    val saveAttachmentMetadata: Boolean = true,

    // Metadata
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
