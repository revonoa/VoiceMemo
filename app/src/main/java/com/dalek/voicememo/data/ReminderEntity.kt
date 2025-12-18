package com.dalek.voicememo.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reminders")
data class ReminderEntity( @PrimaryKey(autoGenerate = true)  val id: Long = 0,
                           val title: String,
                           val body: String,
                           val remindAtMillis: Long?,   // 없으면 null (메모만)
                           val createdAtMillis: Long = System.currentTimeMillis()
)

