package com.example.groupaac.data.realtime

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RealtimeClientManagerTest {
    @Test
    fun activatingNewUserClosesPreviousClient() = runTest {
        val createdClients = mutableListOf<RecordingRealtimeClient>()
        val manager = AccountScopedRealtimeClientManager(
            defaultClientFactory = {
                RecordingRealtimeClient().also(createdClients::add)
            },
            clientFactory = {
                RecordingRealtimeClient().also(createdClients::add)
            }
        )

        val initial = manager.requireClient() as RecordingRealtimeClient
        manager.activateUser("alice")
        val aliceClient = manager.requireClient() as RecordingRealtimeClient
        manager.activateUser("bob")
        val bobClient = manager.requireClient() as RecordingRealtimeClient
        val snapshot = manager.currentAccount()

        assertTrue(initial.closed)
        assertTrue(aliceClient.closed)
        assertSame(bobClient, manager.requireClient())
        assertEquals("bob", manager.activeUserId)
        assertEquals("bob", snapshot?.userId)
        assertSame(bobClient, snapshot?.client)
        assertEquals(3, createdClients.size)
    }

    @Test
    fun activatingSameUserReusesCurrentClient() = runTest {
        val manager = AccountScopedRealtimeClientManager(
            defaultClientFactory = { RecordingRealtimeClient() },
            clientFactory = { RecordingRealtimeClient() }
        )

        manager.activateUser("alice")
        val first = manager.requireClient()
        manager.activateUser("alice")

        assertSame(first, manager.requireClient())
        assertEquals("alice", manager.activeUserId)
    }

    @Test
    fun deactivateClosesActiveClientForSignOut() = runTest {
        val manager = AccountScopedRealtimeClientManager(
            defaultClientFactory = { RecordingRealtimeClient() },
            clientFactory = { RecordingRealtimeClient() }
        )

        val initial = manager.requireClient()
        manager.activateUser("alice")
        val aliceClient = manager.requireClient() as RecordingRealtimeClient
        manager.deactivateUser()

        assertTrue((initial as RecordingRealtimeClient).closed)
        assertTrue(aliceClient.closed)
        assertEquals(null, manager.activeUserId)
        assertTrue(manager.requireClient() is RecordingRealtimeClient)
    }
}
