package com.example.groupaac

import android.content.Context
import com.example.groupaac.data.AppDatabase
import com.example.groupaac.data.file.AttachmentStorage
import com.example.groupaac.data.pi.MockPiClient
import com.example.groupaac.data.pi.PiClient
import com.example.groupaac.data.prefs.AppPreferences
import com.example.groupaac.data.repository.AccountRepository
import com.example.groupaac.data.repository.AttachmentRepository
import com.example.groupaac.data.repository.FacilitatorRepository
import com.example.groupaac.data.repository.DebugRepository
import com.example.groupaac.data.repository.MessageRepository
import com.example.groupaac.data.repository.SessionRepository
import com.example.groupaac.data.repository.SettingsRepository
import com.example.groupaac.data.repository.SignalRepository

class AppContainer(context: Context) {
    val database: AppDatabase = AppDatabase.create(context)
    val preferences = AppPreferences(context)
    val attachmentStorage = AttachmentStorage(context)
    val piClient: PiClient = MockPiClient()

    val accountRepository = AccountRepository(database.userDao(), preferences)
    val settingsRepository = SettingsRepository(database.userDao())
    val sessionRepository = SessionRepository(database.sessionDao(), database.userDao(), preferences, piClient)
    val messageRepository = MessageRepository(database.messageDao(), database.sessionDao(), database.userDao(), piClient)
    val signalRepository = SignalRepository(database.statusSignalDao(), database.sessionDao(), database.userDao(), piClient)
    val facilitatorRepository = FacilitatorRepository(database.facilitatorDao())
    val attachmentRepository = AttachmentRepository(context, database.messageDao())
    val debugRepository = DebugRepository(
        sessionDao = database.sessionDao(),
        userDao = database.userDao(),
        signalDao = database.statusSignalDao(),
        messageDao = database.messageDao(),
        preferences = preferences
    )
}
