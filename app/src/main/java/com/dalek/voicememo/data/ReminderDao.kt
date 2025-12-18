package com.dalek.voicememo.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: ReminderEntity): Long

    @Query("SELECT * FROM reminders ORDER BY COALESCE(remindAtMillis, createdAtMillis) DESC")
    fun observeAll(): Flow<List<ReminderEntity>>

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun deleteById(id: Long)
}