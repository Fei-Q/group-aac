package com.example.groupaac.data

import androidx.room.TypeConverter
import com.example.groupaac.model.FacilitatorDefaultTab
import com.example.groupaac.model.MessageStatus
import com.example.groupaac.model.MessageTarget
import com.example.groupaac.model.ParticipantDefaultTab
import com.example.groupaac.model.SignalState
import com.example.groupaac.model.SignalType
import com.example.groupaac.model.UserRole

class TypeConverters {
    @TypeConverter fun userRoleToString(value: UserRole): String = value.name
    @TypeConverter fun stringToUserRole(value: String): UserRole = UserRole.valueOf(value)

    @TypeConverter
    fun participantDefaultTabToString(value: ParticipantDefaultTab): String =
        value.name

    @TypeConverter
    fun stringToParticipantDefaultTab(value: String): ParticipantDefaultTab =
        ParticipantDefaultTab.valueOf(value)

    @TypeConverter
    fun facilitatorDefaultTabToString(value: FacilitatorDefaultTab): String =
        value.name

    @TypeConverter
    fun stringToFacilitatorDefaultTab(value: String): FacilitatorDefaultTab =
        FacilitatorDefaultTab.valueOf(value)

    @TypeConverter fun messageTargetToString(value: MessageTarget): String = value.name
    @TypeConverter fun stringToMessageTarget(value: String): MessageTarget = MessageTarget.valueOf(value)

    @TypeConverter fun messageStatusToString(value: MessageStatus): String = value.name
    @TypeConverter fun stringToMessageStatus(value: String): MessageStatus = MessageStatus.valueOf(value)

    @TypeConverter fun signalTypeToString(value: SignalType): String = value.name
    @TypeConverter fun stringToSignalType(value: String): SignalType = SignalType.valueOf(value)

    @TypeConverter fun signalStateToString(value: SignalState): String = value.name
    @TypeConverter fun stringToSignalState(value: String): SignalState = SignalState.valueOf(value)
}
