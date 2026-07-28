package com.example.groupaac.ui.navigation

import com.example.groupaac.model.HomeExperience
import com.example.groupaac.model.SessionRole
import com.example.groupaac.model.SessionConnectionState

enum class AppShell {
    Restoring,
    SimpleOutsideSession,
    AdvancedOutsideSession,
    ParticipantInSession,
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
        is SessionConnectionState.AwaitingApproval,
        is SessionConnectionState.Joining -> {
            when (homeExperience) {
                HomeExperience.SIMPLE ->
                    AppShell.SimpleOutsideSession

                HomeExperience.ADVANCED ->
                    AppShell.AdvancedOutsideSession
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

object OutsideRoutes {
    const val Home = "outside/home"
    const val Join = "outside/join"
    const val Sessions = "outside/sessions"
    const val Schedule = "outside/sessions/schedule"
    const val Groups = "outside/groups"
    const val Tools = "outside/tools"
    const val Settings = "outside/settings"
}

object ParticipantInsideRoutes {
    const val Share = "participant_inside/share"
    const val Signal = "participant_inside/signal"
    const val Debug = "participant_inside/debug"
}

object FacilitatorInsideRoutes {
    const val Participants = "facilitator_inside/participants"
    const val SessionLog = "facilitator_inside/session_log"
    const val Summary = "facilitator_inside/summary"
    const val Debug = "facilitator_inside/debug"
}
