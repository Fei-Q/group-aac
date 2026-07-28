package com.example.groupaac.data.realtime

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class RealtimeStartupInitializerTest {
    @Test
    fun persistedUidActivatesRealtimeClientOnStartup() = runTest {
        val activatedUsers = mutableListOf<String>()
        val initializer = RealtimeStartupInitializer(
            activeUserId = MutableStateFlow("alice"),
            realtimeClientManager = object : RealtimeClientManager {
                override suspend fun activateUser(uid: String) {
                    activatedUsers += uid
                }

                override suspend fun deactivateUser() = Unit

                override fun requireClient(): SessionRealtimeClient =
                    FakeSessionRealtimeClient()
            }
        )

        initializer.initialize()

        assertEquals(listOf("alice"), activatedUsers)
    }
}
