package com.example.groupaac.ui.navigation

import com.example.groupaac.model.HomeExperience
import com.example.groupaac.model.SessionRole
import com.example.groupaac.model.SessionConnectionState

enum class AppShell {
    Restoring,
    ParticipantOutsideSession,
    ParticipantInSession,
    FacilitatorOutsideSession,
    FacilitatorInSession
}

fun resolveAppShell(
    homeExperience: HomeExperience,
    state: SessionConnectionState
): AppShell {
    return when (state) {
        SessionConnectionState.Restoring ->
            AppShell.Restoring

        SessionConnectionState.NotInSession,
        is SessionConnectionState.Joining -> {
            when (homeExperience) {
                HomeExperience.SIMPLE ->
                    AppShell.ParticipantOutsideSession

                HomeExperience.ADVANCED ->
                    AppShell.FacilitatorOutsideSession
            }
        }

        is SessionConnectionState.Connected -> {
            when (state.session.role) {
                SessionRole.PARTICIPANT ->
                    AppShell.ParticipantInSession

                SessionRole.FACILITATOR,
                SessionRole.HOST ->
                    AppShell.FacilitatorInSession
            }
        }

        is SessionConnectionState.Reconnecting -> {
            when (state.session.role) {
                SessionRole.PARTICIPANT ->
                    AppShell.ParticipantInSession

                SessionRole.FACILITATOR,
                SessionRole.HOST ->
                    AppShell.FacilitatorInSession
            }
        }

        is SessionConnectionState.Leaving -> {
            when (state.session.role) {
                SessionRole.PARTICIPANT ->
                    AppShell.ParticipantInSession

                SessionRole.FACILITATOR,
                SessionRole.HOST ->
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
