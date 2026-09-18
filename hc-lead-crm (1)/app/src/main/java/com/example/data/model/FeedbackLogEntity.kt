package com.example.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "feedback_logs",
    indices = [
        Index(value = ["leadId"]),
        Index(value = ["agentId"])
    ]
)
data class FeedbackLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val leadId: Long,
    val agentId: Long,
    val agentName: String,
    val timestamp: Long = System.currentTimeMillis(),
    val disposition: String, // e.g. "Call Answered - Interested", "Call Not Answered", "Follow-up Scheduled", "Quotation Shared", "Deal Won", "Lost"
    val notes: String,
    val nextFollowUpDate: Long? = null,
    val previousStatus: String = "",
    val newStatus: String = ""
)
