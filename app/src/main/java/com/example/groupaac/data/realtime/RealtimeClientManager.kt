package com.example.groupaac.data.realtime

data class ActiveRealtimeAccount(
    val userId: String,
    val client: SessionRealtimeClient
)

interface RealtimeClientManager {
    val activeUserId: String?
    fun currentAccount(): ActiveRealtimeAccount?
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
    private val clientFactory: suspend (String) -> SessionRealtimeClient,
    private val sessionAuthority: SessionAuthority = AllowAllSessionAuthority()
) : RealtimeClientManager {
    private val lock = Any()
    private var activeUid: String? = null
    private var activeClient: SessionRealtimeClient = defaultClientFactory()
    override val activeUserId: String?
        get() = synchronized(lock) { activeUid }

    override fun currentAccount(): ActiveRealtimeAccount? {
        return synchronized(lock) {
            val uid = activeUid ?: return null
            ActiveRealtimeAccount(
                userId = uid,
                client = activeClient
            )
        }
    }

    override suspend fun activateUser(uid: String) {
        check(sessionAuthority.canActivate(uid)) {
            "Realtime activation denied for $uid."
        }
        val nextClient = clientFactory(uid)
        val previousClient = synchronized(lock) {
            if (activeUid == uid) {
                return
            }
            val previousClient = activeClient
            activeUid = uid
            activeClient = nextClient
            previousClient
        }
        previousClient.close()
    }

    override suspend fun deactivateUser() {
        val previousClient = synchronized(lock) {
            val previousClient = activeClient
            activeUid = null
            activeClient = defaultClientFactory()
            previousClient
        }
        previousClient.close()
    }

    override fun requireClient(): SessionRealtimeClient =
        synchronized(lock) { activeClient }
}
