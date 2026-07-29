package com.example.groupaac.data.realtime

import com.example.groupaac.model.ActiveSession
import com.example.groupaac.model.SessionRole
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class RealtimeStartupInitializerTest {
    @Test
    fun startupStateProgressesFromInitializingToReady() =
        runTest {
            var startCount = 0
            val initializer =
                RealtimeStartupInitializer(
                    scope = this,
                    activeUserId =
                        MutableStateFlow("alice"),
                    activeSessionProvider = {
                        MutableStateFlow(
                            activeSession()
                        )
                    },
                    realtimeClientManager =
                        RecordingRealtimeClientManager(),
                    requestOutboxDispatch = {},
                    startSessionSubscriptions = {
                        startCount += 1
                    }
                )

            initializer.start()
            assertEquals(
                AppStartupState.Initializing,
                initializer.startupState.value
            )

            advanceUntilIdle()

            assertEquals(
                AppStartupState.Ready,
                initializer.startupState.value
            )
            assertEquals(1, startCount)
        }

    @Test
    fun startupFailureSurfacesFailedState() =
        runTest {
            val initializer =
                RealtimeStartupInitializer(
                    scope = this,
                    activeUserId =
                        MutableStateFlow("alice"),
                    activeSessionProvider = {
                        MutableStateFlow<ActiveSession?>(null)
                    },
                    realtimeClientManager =
                        object : RealtimeClientManager {
                            override val activeUserId: String? = null
                            override suspend fun activateUser(
                                uid: String
                            ) {
                                error("boom")
                            }

                            override suspend fun deactivateUser() = Unit

                            override fun requireClient():
                                    SessionRealtimeClient =
                                FakeSessionRealtimeClient()
                        },
                    requestOutboxDispatch = {},
                    startSessionSubscriptions = {}
                )

            initializer.start()
            advanceUntilIdle()

            assertEquals(
                AppStartupState.Failed("boom"),
                initializer.startupState.value
            )
        }

    @Test
    fun activeUidIsRestoredBeforeActiveSession() =
        runTest {
            val steps = mutableListOf<String>()
            val initializer =
                RealtimeStartupInitializer(
                    scope = this,
                    activeUserId =
                        MutableStateFlow("alice"),
                    activeSessionProvider = { userId ->
                        steps += "session:$userId"
                        MutableStateFlow(
                            activeSession(
                                userId = userId
                            )
                        )
                    },
                    realtimeClientManager =
                        object : RealtimeClientManager {
                            override val activeUserId: String? = null
                            override suspend fun activateUser(
                                uid: String
                            ) {
                                steps += "activate:$uid"
                            }

                            override suspend fun deactivateUser() = Unit

                            override fun requireClient():
                                    SessionRealtimeClient =
                                FakeSessionRealtimeClient()
                        },
                    requestOutboxDispatch = {},
                    startSessionSubscriptions = {}
                )

            initializer.start()
            advanceUntilIdle()

            assertEquals(
                listOf(
                    "activate:alice",
                    "session:alice"
                ),
                steps
            )
        }

    @Test
    fun startupDoesNotBlockCallerThread() = runTest(
        StandardTestDispatcher()
    ) {
        val gate = CompletableDeferred<Unit>()
        val initializer =
            RealtimeStartupInitializer(
                scope = this,
                activeUserId =
                    MutableStateFlow("alice"),
                activeSessionProvider = {
                    MutableStateFlow<ActiveSession?>(null)
                },
                realtimeClientManager =
                    object : RealtimeClientManager {
                        override val activeUserId: String? = null
                        override suspend fun activateUser(
                            uid: String
                        ) {
                            gate.await()
                        }

                        override suspend fun deactivateUser() = Unit

                        override fun requireClient():
                                SessionRealtimeClient =
                            FakeSessionRealtimeClient()
                    },
                requestOutboxDispatch = {},
                startSessionSubscriptions = {}
            )

        initializer.start()

        assertEquals(
            AppStartupState.Initializing,
            initializer.startupState.value
        )

        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(
            AppStartupState.Ready,
            initializer.startupState.value
        )
    }

    private fun activeSession(
        userId: String = "alice"
    ): ActiveSession =
        ActiveSession(
            sessionId = "session-1",
            joinCode = "1234-5678",
            sessionName = "Friday Group",
            userId = userId,
            role = SessionRole.HOST,
            joinedAt = 10L,
            displayId = "pi-1",
            actualStartedAt = 20L
        )
}

private class RecordingRealtimeClientManager :
    RealtimeClientManager {
    val activatedUsers = mutableListOf<String>()
    override var activeUserId: String? = null
        private set

    override suspend fun activateUser(uid: String) {
        activatedUsers += uid
        activeUserId = uid
    }

    override suspend fun deactivateUser() {
        activeUserId = null
    }

    override fun requireClient(): SessionRealtimeClient =
        FakeSessionRealtimeClient()
}
