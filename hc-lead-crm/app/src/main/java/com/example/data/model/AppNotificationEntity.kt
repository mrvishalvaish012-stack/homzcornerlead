package com.example.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "app_notifications",
    indices = [
        Index(value = ["targetAgentId"]),
        Index(value = ["isRead"])
    ]
)
data class AppNotificationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val targetAgentId: Long? = null, // null means broadcast to Manager/all
    val leadId: Long? = null,
    val title: String,
    val message: String,
    val type: String = "NEW_LEAD", // NEW_LEAD, DUPLICATE_ALERT, FOLLOW_UP_REMINDER, STATUS_CHANGE
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false
)
