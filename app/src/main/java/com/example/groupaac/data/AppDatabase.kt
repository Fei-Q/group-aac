package com.example.groupaac.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.groupaac.data.dao.FacilitatorDao
import com.example.groupaac.data.dao.MessageDao
import com.example.groupaac.data.dao.SessionDao
import com.example.groupaac.data.dao.SessionJoinRequestDao
import com.example.groupaac.data.dao.StatusSignalDao
import com.example.groupaac.data.dao.UserDao
import com.example.groupaac.data.entity.AttachmentEntity
import com.example.groupaac.data.entity.FacilitatorNoteEntity
import com.example.groupaac.data.entity.MessageEntity
import com.example.groupaac.data.entity.QuickLogEntity
import com.example.groupaac.data.entity.SessionEntity
import com.example.groupaac.data.entity.SessionJoinRequestEntity
import com.example.groupaac.data.entity.SessionMemberEntity
import com.example.groupaac.data.entity.StatusSignalEntity
import com.example.groupaac.data.entity.UserEntity
import com.example.groupaac.data.entity.UserSettingsEntity

@Database(
    entities = [
        UserEntity::class,
        UserSettingsEntity::class,
        SessionEntity::class,
        SessionMemberEntity::class,
        SessionJoinRequestEntity::class,
        MessageEntity::class,
        StatusSignalEntity::class,
        AttachmentEntity::class,
        FacilitatorNoteEntity::class,
        QuickLogEntity::class
    ],
    version = 4,
    exportSchema = false
)
@TypeConverters(com.example.groupaac.data.TypeConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun sessionDao(): SessionDao
    abstract fun sessionJoinRequestDao(): SessionJoinRequestDao
    abstract fun messageDao(): MessageDao
    abstract fun statusSignalDao(): StatusSignalDao
    abstract fun facilitatorDao(): FacilitatorDao

    companion object {
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    ALTER TABLE user_settings
                    ADD COLUMN homeExperience TEXT NOT NULL DEFAULT 'SIMPLE'
                    """.trimIndent()
                )
                database.execSQL(
                    """
                    ALTER TABLE sessions
                    ADD COLUMN hostUserId TEXT
                    """.trimIndent()
                )
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS session_join_requests (
                        id TEXT NOT NULL,
                        sessionId TEXT NOT NULL,
                        userId TEXT NOT NULL,
                        displayName TEXT NOT NULL,
                        requestedRole TEXT NOT NULL,
                        status TEXT NOT NULL,
                        requestedAt INTEGER NOT NULL,
                        decidedAt INTEGER,
                        decidedByUserId TEXT,
                        PRIMARY KEY(id)
                    )
                    """.trimIndent()
                )
                database.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS index_session_join_requests_sessionId_status
                    ON session_join_requests(sessionId, status)
                    """.trimIndent()
                )
                database.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS index_session_join_requests_userId
                    ON session_join_requests(userId)
                    """.trimIndent()
                )
            }
        }

        fun create(context: Context): AppDatabase = Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            "group_aac.db"
        )
            .addMigrations(MIGRATION_3_4)
            .build()
    }
}
