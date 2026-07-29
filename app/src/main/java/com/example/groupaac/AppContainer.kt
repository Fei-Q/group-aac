package com.example.groupaac

import android.content.Context
import com.example.groupaac.data.AppDatabase
import com.example.groupaac.data.account.LocalUserIdRegistry
import com.example.groupaac.data.file.AttachmentStorage
import com.example.groupaac.data.pi.PiClient
import com.example.groupaac.data.prefs.AppPreferences
import com.example.groupaac.data.realtime.AccountScopedRealtimeClientManager
import com.example.groupaac.data.realtime.DelegatingPiClient
import com.example.groupaac.data.realtime.FakeSessionRealtimeClient
import com.example.groupaac.data.realtime.InactiveSessionRealtimeClient
import com.example.groupaac.data.realtime.NoOpPubNubTokenProvider
import com.example.groupaac.data.realtime.PubNubConfigProvider
import com.example.groupaac.data.realtime.PubNubRuntimeConfig
import com.example.groupaac.data.realtime.PubNubSessionRealtimeClientFactory
import com.example.groupaac.data.realtime.AppStartupState
import com.example.groupaac.data.realtime.RealtimeClientManager
import com.example.groupaac.data.realtime.RealtimeStartupInitializer
import com.example.groupaac.data.realtime.SessionSubscriptionCoordinator
import com.example.groupaac.data.realtime.StartupRecoveryState
import com.example.groupaac.data.realtime.reliability.OutboxDispatcher
import com.example.groupaac.data.realtime.reliability.RealtimeReliabilityStore
import com.example.groupaac.data.realtime.sync.DefaultSessionRealtimeSync
import com.example.groupaac.data.repository.AccountRepository
import com.example.groupaac.data.repository.AttachmentRepository
import com.example.groupaac.data.repository.DebugRepository
import com.example.groupaac.data.repository.RoomTransactionRunner
import com.example.groupaac.data.repository.FacilitatorRepository
import com.example.groupaac.data.repository.MessageRepository
import com.example.groupaac.data.repository.SessionRepository
import com.example.groupaac.data.repository.SettingsRepository
import com.example.groupaac.data.repository.SignalRepository
import com.example.groupaac.data.session.ActiveSessionStore
import com.example.groupaac.data.session.DataStoreActiveSessionStore
import com.example.groupaac.data.sessiondirectory.FakeSessionDirectory
import com.example.groupaac.data.sessiondirectory.SessionDirectory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import com.example.groupaac.data.sessiondirectory.PubNubSessionDirectory
import com.example.groupaac.data.sessiondirectory.createPubNubMetadataTransport
import com.example.groupaac.data.pi.DisplayBindingCoordinator
import com.example.groupaac.data.pi.NoOpDisplayBindingCoordinator
import com.example.groupaac.data.pi.PubNubDisplayBindingCoordinator
import kotlinx.coroutines.flow.StateFlow

class AppContainer(context: Context) {
    val applicationScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val database: AppDatabase = AppDatabase.create(context)
    val preferences = AppPreferences(context)
    val attachmentStorage = AttachmentStorage(context)
    val pubNubConfig: PubNubRuntimeConfig = PubNubConfigProvider.fromBuildConfig()
    private val pubNubRealtimeClientFactory = PubNubSessionRealtimeClientFactory(
        runtimeConfig = pubNubConfig,
        tokenProvider = NoOpPubNubTokenProvider()
    )
    val realtimeClientManager: RealtimeClientManager =
        AccountScopedRealtimeClientManager(
            defaultClientFactory = {
                if (pubNubConfig.isConfigured) {
                    InactiveSessionRealtimeClient()
                } else {
                    FakeSessionRealtimeClient()
                }
            },
            clientFactory = { uid ->
                if (pubNubConfig.isConfigured) {
                    pubNubRealtimeClientFactory.create(uid)
                } else {
                    FakeSessionRealtimeClient()
                }
            }
        )
    val displayBindingCoordinator:
            DisplayBindingCoordinator =
        if (pubNubConfig.isConfigured) {
            PubNubDisplayBindingCoordinator(
                clientProvider = {
                    realtimeClientManager.requireClient()
                }
            )
        } else {
            NoOpDisplayBindingCoordinator
        }
    val piClient: PiClient = DelegatingPiClient(realtimeClientManager)
    val userIdRegistry = LocalUserIdRegistry(database)
    val realtimeReliabilityStore = RealtimeReliabilityStore(
        database = database,
        reliabilityDao = database.reliabilityDao()
    )
    val sessionRealtimeSync = DefaultSessionRealtimeSync(
        transactionRunner = RoomTransactionRunner(database),
        sessionDao = database.sessionDao(),
        sessionJoinRequestDao = database.sessionJoinRequestDao(),
        messageDao = database.messageDao(),
        statusSignalDao = database.statusSignalDao(),
        reliabilityDao = database.reliabilityDao(),
        reliabilityStore = realtimeReliabilityStore
    )
    val outboxDispatcher = OutboxDispatcher(
        context = context.applicationContext,
        database = database,
        reliabilityStore = realtimeReliabilityStore,
        realtimeClientManager = realtimeClientManager,
        scope = applicationScope
    )

    val activeSessionStore: ActiveSessionStore =
        DataStoreActiveSessionStore(preferences)
    private val pubNubMetadataTransport =
        if (pubNubConfig.isConfigured) {
            createPubNubMetadataTransport(
                pubNubConfig
            )
        } else {
            null
        }

    val sessionDirectory: SessionDirectory =
        pubNubMetadataTransport?.let { transport ->
            PubNubSessionDirectory(transport)
        } ?: FakeSessionDirectory()

    val accountRepository = AccountRepository(
        userIdRegistry = userIdRegistry,
        userDao = database.userDao(),
        preferences = preferences,
        realtimeClientManager = realtimeClientManager
    )

    val settingsRepository = SettingsRepository(
        userDao = database.userDao()
    )

    val sessionRepository = SessionRepository(
        transactionRunner = RoomTransactionRunner(database),
        sessionDao = database.sessionDao(),
        sessionJoinRequestDao = database.sessionJoinRequestDao(),
        userDao = database.userDao(),
        activeSessionStore = activeSessionStore,
        sessionDirectory = sessionDirectory,
        displayBindingCoordinator = displayBindingCoordinator,
        outboxDispatcher = outboxDispatcher,
        sessionRealtimeSync = sessionRealtimeSync,
        getDisplayState = database.reliabilityDao()::getDisplayState,
        upsertDisplayState = database.reliabilityDao()::upsertDisplayState
    )
    val sessionSubscriptionCoordinator = SessionSubscriptionCoordinator(
        activeUserId = accountRepository.activeUserId,
        activeSessionProvider = { userId ->
            sessionRepository.observeActiveSession(userId)
        },
        realtimeClientManager = realtimeClientManager,
        sessionRealtimeSync = sessionRealtimeSync,
        channelCursorProvider = { channel ->
            realtimeReliabilityStore.getChannelCursor(channel)?.lastProcessedTimetoken
        },
        scope = applicationScope
    )
    private val startupInitializer =
        RealtimeStartupInitializer(
            scope = applicationScope,
            activeUserId = accountRepository.activeUserId,
            activeSessionProvider = { userId ->
                sessionRepository.observeActiveSession(userId)
            },
            realtimeClientManager = realtimeClientManager,
            startSessionSubscriptions =
                sessionSubscriptionCoordinator::start,
            sessionRecovery =
                com.example.groupaac.data.realtime.StartupSessionRecovery {
                        userId,
                        activeSession ->
                    sessionRepository
                        .reconcileRestoredSession(
                            userId = userId,
                            activeSession = activeSession
                        )
                }
        )
    val startupState: StateFlow<AppStartupState> =
        startupInitializer.startupState
    val recoveryState: StateFlow<StartupRecoveryState> =
        startupInitializer.recoveryState

    val messageRepository = MessageRepository(
        transactionRunner = RoomTransactionRunner(database),
        messageDao = database.messageDao(),
        sessionDao = database.sessionDao(),
        userDao = database.userDao(),
        reliabilityDao = database.reliabilityDao(),
        reliabilityStore = realtimeReliabilityStore,
        outboxDispatcher = outboxDispatcher,
        sessionRealtimeSync = sessionRealtimeSync
    )

    val signalRepository = SignalRepository(
        transactionRunner = RoomTransactionRunner(database),
        signalDao = database.statusSignalDao(),
        sessionDao = database.sessionDao(),
        userDao = database.userDao(),
        outboxDispatcher = outboxDispatcher,
        sessionRealtimeSync = sessionRealtimeSync
    )

    val facilitatorRepository = FacilitatorRepository(
        facilitatorDao = database.facilitatorDao()
    )

    val attachmentRepository = AttachmentRepository(
        context = context,
        messageDao = database.messageDao()
    )

    val debugRepository = DebugRepository(
        sessionDao = database.sessionDao(),
        userDao = database.userDao(),
        signalDao = database.statusSignalDao(),
        messageDao = database.messageDao()
    )

    init {
        startupInitializer.start()
        outboxDispatcher.requestImmediateDispatch()
    }
}
