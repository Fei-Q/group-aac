package com.example.groupaac.data

import androidx.room.TypeConverter
import com.example.groupaac.model.FacilitatorDefaultTab
import com.example.groupaac.model.HomeExperience
import com.example.groupaac.model.JoinRequestStatus
import com.example.groupaac.model.MessageStatus
import com.example.groupaac.model.MessageTarget
import com.example.groupaac.model.ParticipantDefaultTab
import com.example.groupaac.model.SessionRole
import com.example.groupaac.model.SignalState
import com.example.groupaac.model.SignalType
import com.example.groupaac.model.UserRole

class TypeConverters {
    @TypeConverter fun userRoleToString(value: UserRole): String = value.name
    @TypeConverter
    fun stringToUserRole(value: String?): UserRole = UserRole.fromName(value)

    @TypeConverter
    fun homeExperienceToString(value: HomeExperience): String = value.name

    @TypeConverter
    fun stringToHomeExperience(value: String?): HomeExperience =
        HomeExperience.entries.firstOrNull { it.name == value }
            ?: HomeExperience.SIMPLE

    @TypeConverter
    fun sessionRoleToString(value: SessionRole): String = value.name

    @TypeConverter
    fun stringToSessionRole(value: String?): SessionRole =
        SessionRole.fromName(value)

    @TypeConverter
    fun joinRequestStatusToString(value: JoinRequestStatus): String = value.name

    @TypeConverter
    fun stringToJoinRequestStatus(value: String?): JoinRequestStatus =
        JoinRequestStatus.fromName(value)

    @TypeConverter
    fun participantDefaultTabToString(value: ParticipantDefaultTab): String =
        value.name

    @TypeConverter
    fun stringToParticipantDefaultTab(value: String): ParticipantDefaultTab =
        ParticipantDefaultTab.entries.firstOrNull { it.name == value }
            ?: ParticipantDefaultTab.SHARE

    @TypeConverter
    fun facilitatorDefaultTabToString(value: FacilitatorDefaultTab): String =
        value.name

    @TypeConverter
    fun stringToFacilitatorDefaultTab(value: String): FacilitatorDefaultTab =
        FacilitatorDefaultTab.entries.firstOrNull { it.name == value }
            ?: FacilitatorDefaultTab.PARTICIPANTS

    @TypeConverter fun messageTargetToString(value: MessageTarget): String = value.name
    @TypeConverter
    fun stringToMessageTarget(value: String): MessageTarget =
        MessageTarget.entries.firstOrNull { it.name == value }
            ?: MessageTarget.GROUP

    @TypeConverter fun messageStatusToString(value: MessageStatus): String = value.name
    @TypeConverter
    fun stringToMessageStatus(value: String): MessageStatus =
        MessageStatus.entries.firstOrNull { it.name == value }
            ?: MessageStatus.SENT

    @TypeConverter fun signalTypeToString(value: SignalType): String = value.name
    @TypeConverter
    fun stringToSignalType(value: String): SignalType =
        SignalType.entries.firstOrNull { it.name == value }
            ?: SignalType.HELP

    @TypeConverter fun signalStateToString(value: SignalState): String = value.name
    @TypeConverter
    fun stringToSignalState(value: String): SignalState =
        SignalState.entries.firstOrNull { it.name == value }
            ?: SignalState.CURRENT
}
