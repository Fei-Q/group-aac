package com.example.groupaac.ui.navigation

import com.example.groupaac.model.SessionConnectionState
import com.example.groupaac.model.UserRole

enum class AppShell {
    Restoring,
    ParticipantOutsideSession,
    ParticipantInSession,
    FacilitatorOutsideSession,
    FacilitatorInSession
}

fun resolveAppShell(
    role: UserRole,
    state: SessionConnectionState
): AppShell {
    return when (state) {
        SessionConnectionState.Restoring ->
            AppShell.Restoring

        SessionConnectionState.NotInSession,
        is SessionConnectionState.Joining -> {
            when (role) {
                UserRole.PARTICIPANT ->
                    AppShell.ParticipantOutsideSession

                UserRole.FACILITATOR ->
                    AppShell.FacilitatorOutsideSession
            }
        }

        is SessionConnectionState.Connected,
        is SessionConnectionState.Reconnecting,
        is SessionConnectionState.Leaving -> {
            when (role) {
                UserRole.PARTICIPANT ->
                    AppShell.ParticipantInSession

                UserRole.FACILITATOR ->
                    AppShell.FacilitatorInSession
            }
        }
    }
}

object ParticipantOutsideRoutes {
    const val Join = "participant_outside/join"
    const val Social = "participant_outside/social"
    const val Settings = "participant_outside/settings"
    const val Debug = "participant_outside/debug"
}

object ParticipantInsideRoutes {
    const val Share = "participant_inside/share"
    const val Signal = "participant_inside/signal"
    const val Debug = "participant_inside/debug"
}

object FacilitatorOutsideRoutes {
    const val Sessions = "facilitator_outside/sessions"
    const val Settings = "facilitator_outside/settings"
    const val Debug = "facilitator_outside/debug"
}

object FacilitatorInsideRoutes {
    const val Participants = "facilitator_inside/participants"
    const val SessionLog = "facilitator_inside/session_log"
    const val Summary = "facilitator_inside/summary"
    const val Debug = "facilitator_inside/debug"
}