package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        VoiceHistoryEntity::class,
        CustomRoutineEntity::class,
        LocalNoteReminderEntity::class,
        NotificationRuleEntity::class,
        ErrorLogEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class CsiDatabase : RoomDatabase() {
    abstract fun voiceHistoryDao(): VoiceHistoryDao
    abstract fun customRoutineDao(): CustomRoutineDao
    abstract fun noteReminderDao(): NoteReminderDao
    abstract fun notificationRuleDao(): NotificationRuleDao
    abstract fun errorLogDao(): ErrorLogDao

    companion object {
        @Volatile
        private var INSTANCE: CsiDatabase? = null

        fun getInstance(context: Context): CsiDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    CsiDatabase::class.java,
                    "csi_voice_master.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
