package com.example.groupaac

import android.content.Context
import com.example.groupaac.data.AppDatabase
import com.example.groupaac.data.account.LocalUserIdRegistry
import com.example.groupaac.data.file.AttachmentStorage
import com.example.groupaac.data.pi.MockPiClient
import com.example.groupaac.data.pi.PiClient
import com.example.groupaac.data.prefs.AppPreferences
import com.example.groupaac.data.repository.AccountRepository
import com.example.groupaac.data.repository.AttachmentRepository
import com.example.groupaac.data.repository.DebugRepository
import com.example.groupaac.data.repository.FacilitatorRepository
import com.example.groupaac.data.repository.MessageRepository
import com.example.groupaac.data.repository.SessionRepository
import com.example.groupaac.data.repository.SettingsRepository
import com.example.groupaac.data.repository.SignalRepository
import com.example.groupaac.data.session.ActiveSessionStore
import com.example.groupaac.data.session.DataStoreActiveSessionStore

class AppContainer(context: Context) {
    val database: AppDatabase = AppDatabase.create(context)
    val preferences = AppPreferences(context)
    val attachmentStorage = AttachmentStorage(context)
    val piClient: PiClient = MockPiClient()
    val userIdRegistry = LocalUserIdRegistry(database)

    val activeSessionStore: ActiveSessionStore =
        DataStoreActiveSessionStore(preferences)

    val accountRepository = AccountRepository(
        userIdRegistry = userIdRegistry,
        userDao = database.userDao(),
        preferences = preferences
    )

    val settingsRepository = SettingsRepository(
        userDao = database.userDao()
    )

    val sessionRepository = SessionRepository(
        sessionDao = database.sessionDao(),
        sessionJoinRequestDao = database.sessionJoinRequestDao(),
        userDao = database.userDao(),
        activeSessionStore = activeSessionStore,
        piClient = piClient
    )

    val messageRepository = MessageRepository(
        messageDao = database.messageDao(),
        sessionDao = database.sessionDao(),
        userDao = database.userDao(),
        piClient = piClient
    )

    val signalRepository = SignalRepository(
        signalDao = database.statusSignalDao(),
        sessionDao = database.sessionDao(),
        userDao = database.userDao(),
        piClient = piClient
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
}
