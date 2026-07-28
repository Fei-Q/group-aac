package com.example.groupaac.data.sessiondirectory

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteSessionDirectoryTest {
    @Test
    fun resolveMapsExplicitResultKinds() = runTest {
        val directory = RemoteSessionDirectory(
            api = object : GroupAacApi {
                override suspend fun createSession(
                    request: CreateSessionApiRequest
                ): SessionApiResponse = error("unused")

                override suspend fun resolveJoinCode(
                    request: ResolveCodeApiRequest
                ): SessionApiResponse {
                    return when (request.joinCode) {
                        "11112222" -> SessionApiResponse(
                            result = "FOUND",
                            session = samplePayload(status = "LIVE")
                        )
                        "33334444" -> SessionApiResponse(result = "ENDED")
                        "55556666" -> SessionApiResponse(result = "CANCELLED")
                        "77778888" -> SessionApiResponse(result = "EXPIRED")
                        else -> SessionApiResponse(result = "NOT_FOUND")
                    }
                }

                override suspend fun updateSession(
                    sessionId: String,
                    request: UpdateSessionApiRequest
                ): SessionApiResponse = error("unused")

                override suspend fun endSession(
                    sessionId: String,
                    request: CloseSessionApiRequest
                ): SessionApiResponse = error("unused")

                override suspend fun cancelSession(
                    sessionId: String,
                    request: CloseSessionApiRequest
                ): SessionApiResponse = error("unused")
            }
        )

        val found = directory.resolveJoinCode("1111-2222", "alice")
        val ended = directory.resolveJoinCode("3333-4444", "alice")
        val cancelled = directory.resolveJoinCode("5555-6666", "alice")
        val expired = directory.resolveJoinCode("7777-8888", "alice")
        val missing = directory.resolveJoinCode("9999-0000", "alice")

        assertTrue(found is ResolveJoinCodeResult.Found)
        assertEquals(
            "session-1",
            (found as ResolveJoinCodeResult.Found).session.sessionId
        )
        assertTrue(ended === ResolveJoinCodeResult.Ended)
        assertTrue(cancelled === ResolveJoinCodeResult.Cancelled)
        assertTrue(expired === ResolveJoinCodeResult.Expired)
        assertTrue(missing === ResolveJoinCodeResult.NotFound)
    }

    @Test
    fun createAndUpdateMapReturnedSessionPayloads() = runTest {
        val directory = RemoteSessionDirectory(
            api = object : GroupAacApi {
                override suspend fun createSession(
                    request: CreateSessionApiRequest
                ): SessionApiResponse = SessionApiResponse(
                    result = "CREATED",
                    session = samplePayload(
                        status = request.status,
                        sessionName = request.name,
                        scheduledStartAt = request.scheduledStartAt
                    )
                )

                override suspend fun resolveJoinCode(
                    request: ResolveCodeApiRequest
                ): SessionApiResponse = error("unused")

                override suspend fun updateSession(
                    sessionId: String,
                    request: UpdateSessionApiRequest
                ): SessionApiResponse = SessionApiResponse(
                    result = "UPDATED",
                    session = samplePayload(
                        status = request.status,
                        sessionName = request.name,
                        scheduledStartAt = request.scheduledStartAt
                    )
                )

                override suspend fun endSession(
                    sessionId: String,
                    request: CloseSessionApiRequest
                ): SessionApiResponse = SessionApiResponse(
                    result = "ENDED",
                    session = samplePayload(status = "ENDED", actualEndedAt = 99L)
                )

                override suspend fun cancelSession(
                    sessionId: String,
                    request: CloseSessionApiRequest
                ): SessionApiResponse = SessionApiResponse(
                    result = "CANCELLED",
                    session = samplePayload(status = "CANCELLED")
                )
            }
        )

        val created = directory.createSession(
            CreateRemoteSessionRequest(
                hostUid = "host",
                name = "Tuesday Group",
                status = RemoteSessionStatus.SCHEDULED,
                scheduledStartAt = 12L,
                scheduledDurationMinutes = 45
            )
        )
        val updated = directory.updateSession(
            UpdateRemoteSessionRequest(
                sessionId = "session-1",
                hostUid = "host",
                name = "Renamed",
                status = RemoteSessionStatus.LIVE,
                scheduledStartAt = 12L,
                scheduledDurationMinutes = 45
            )
        )
        val ended = directory.endSession(
            CloseRemoteSessionRequest("session-1", "host")
        )
        val cancelled = directory.cancelSession(
            CloseRemoteSessionRequest("session-1", "host")
        )

        assertTrue(created is CreateRemoteSessionResult.Created)
        assertEquals(
            RemoteSessionStatus.SCHEDULED,
            (created as CreateRemoteSessionResult.Created).session.status
        )
        assertTrue(updated is UpdateRemoteSessionResult.Updated)
        assertEquals(
            RemoteSessionStatus.LIVE,
            (updated as UpdateRemoteSessionResult.Updated).session.status
        )
        assertTrue(ended is EndRemoteSessionResult.Ended)
        assertTrue(cancelled is CancelRemoteSessionResult.Cancelled)
    }

    private fun samplePayload(
        status: String,
        sessionName: String = "Friday Group",
        scheduledStartAt: Long? = null,
        actualEndedAt: Long? = null
    ): SessionApiPayload {
        return SessionApiPayload(
            sessionId = "session-1",
            joinCode = "1234-5678",
            sessionName = sessionName,
            hostUid = "host",
            status = status,
            scheduledStartAt = scheduledStartAt,
            scheduledDurationMinutes = 45,
            actualStartedAt = if (status == "LIVE") 11L else null,
            actualEndedAt = actualEndedAt,
            expiresAt = 99_999L
        )
    }
}
