package com.example.groupaac.data.realtime

interface RealtimeClientManager {
    suspend fun activateUser(uid: String)
    suspend fun deactivateUser()
    fun requireClient(): SessionRealtimeClient
}

interface PubNubTokenProvider {
    suspend fun tokenForUser(uid: String): String?
}

interface SessionAuthority {
    suspend fun canActivate(uid: String): Boolean
}

class AllowAllSessionAuthority : SessionAuthority {
    override suspend fun canActivate(uid: String): Boolean = true
}

class NoOpPubNubTokenProvider : PubNubTokenProvider {
    override suspend fun tokenForUser(uid: String): String? = null
}

class AccountScopedRealtimeClientManager(
    private val defaultClientFactory: () -> SessionRealtimeClient,
    private val clientFactory: (String) -> SessionRealtimeClient,
    private val sessionAuthority: SessionAuthority = AllowAllSessionAuthority()
) : RealtimeClientManager {
    private var activeUid: String? = null
    private var activeClient: SessionRealtimeClient = defaultClientFactory()

    override suspend fun activateUser(uid: String) {
        if (activeUid == uid) {
            return
        }
        check(sessionAuthority.canActivate(uid)) {
            "Realtime activation denied for $uid."
        }
        activeClient.close()
        activeUid = uid
        activeClient = clientFactory(uid)
    }

    override suspend fun deactivateUser() {
        activeClient.close()
        activeUid = null
        activeClient = defaultClientFactory()
    }

    override fun requireClient(): SessionRealtimeClient = activeClient
}
