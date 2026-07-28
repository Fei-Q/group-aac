package com.example.groupaac.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.groupaac.data.dao.FacilitatorDao
import com.example.groupaac.data.dao.MessageDao
import com.example.groupaac.data.dao.ReliabilityDao
import com.example.groupaac.data.dao.SessionDao
import com.example.groupaac.data.dao.SessionJoinRequestDao
import com.example.groupaac.data.dao.StatusSignalDao
import com.example.groupaac.data.dao.UserDao
import com.example.groupaac.data.entity.AttachmentEntity
import com.example.groupaac.data.entity.ChannelCursorEntity
import com.example.groupaac.data.entity.DisplayStateEntity
import com.example.groupaac.data.entity.FacilitatorNoteEntity
import com.example.groupaac.data.entity.MessageEntity
import com.example.groupaac.data.entity.OutboxEventEntity
import com.example.groupaac.data.entity.ProcessedEventEntity
import com.example.groupaac.data.entity.QuickLogEntity
import com.example.groupaac.data.entity.SessionEntity
import com.example.groupaac.data.entity.SessionJoinRequestEntity
import com.example.groupaac.data.entity.SessionMemberEntity
import com.example.groupaac.data.entity.SignalSnoozeEntity
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
        SignalSnoozeEntity::class,
        OutboxEventEntity::class,
        ProcessedEventEntity::class,
        ChannelCursorEntity::class,
        DisplayStateEntity::class,
        AttachmentEntity::class,
        FacilitatorNoteEntity::class,
        QuickLogEntity::class
    ],
    version = 3,
    exportSchema = true
)
@TypeConverters(com.example.groupaac.data.TypeConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun sessionDao(): SessionDao
    abstract fun sessionJoinRequestDao(): SessionJoinRequestDao
    abstract fun messageDao(): MessageDao
    abstract fun statusSignalDao(): StatusSignalDao
    abstract fun facilitatorDao(): FacilitatorDao
    abstract fun reliabilityDao(): ReliabilityDao

    companion object {
        fun create(context: Context): AppDatabase = Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            "group_aac.db"
        )
            .fallbackToDestructiveMigration()
            .build()
    }
}
