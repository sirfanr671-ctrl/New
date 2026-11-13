package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface VoiceHistoryDao {
    @Query("SELECT * FROM voice_history ORDER BY timestampMillis DESC")
    fun observeHistory(): Flow<List<VoiceHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: VoiceHistoryEntity): Long

    @Query("DELETE FROM voice_history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM voice_history")
    suspend fun clearAll()
}

@Dao
interface CustomRoutineDao {
    @Query("SELECT * FROM custom_routines ORDER BY updatedAtMillis DESC")
    fun observeRoutines(): Flow<List<CustomRoutineEntity>>

    @Query("SELECT * FROM custom_routines WHERE enabled = 1")
    suspend fun getEnabledRoutines(): List<CustomRoutineEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(routine: CustomRoutineEntity)

    @Query("DELETE FROM custom_routines WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface NoteReminderDao {
    @Query("SELECT * FROM notes_reminders ORDER BY updatedAtMillis DESC")
    fun observeItems(): Flow<List<LocalNoteReminderEntity>>

    @Query("SELECT * FROM notes_reminders WHERE kind = 'reminder' AND completed = 0 ORDER BY dueAtMillis ASC")
    suspend fun getActiveReminders(): List<LocalNoteReminderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: LocalNoteReminderEntity)

    @Query("DELETE FROM notes_reminders WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface NotificationRuleDao {
    @Query("SELECT * FROM notification_rules ORDER BY appLabel ASC")
    fun observeRules(): Flow<List<NotificationRuleEntity>>

    @Query("SELECT * FROM notification_rules WHERE packageName = :pkg LIMIT 1")
    suspend fun getRule(pkg: String): NotificationRuleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: NotificationRuleEntity)
}

@Dao
interface ErrorLogDao {
    @Query("SELECT * FROM error_logs ORDER BY timestampMillis DESC LIMIT 100")
    fun observeLogs(): Flow<List<ErrorLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: ErrorLogEntity)

    @Query("DELETE FROM error_logs")
    suspend fun clearAll()
}
