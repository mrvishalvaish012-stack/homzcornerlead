package com.example.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "leads",
    indices = [
        Index(value = ["phoneNumber"]),
        Index(value = ["assignedAgentId"]),
        Index(value = ["status"])
    ]
)
data class LeadEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    val phoneNumber: String,
    val email: String = "",
    val source: String = "Meta Ads",
    val campaignName: String = "All India WhatsApp Campaign",
    val initialMessage: String = "",
    val assignedAgentId: Long,
    val assignedAgentName: String,
    val status: String = "NEW", // NEW, CONTACTED, FOLLOW_UP, QUOTATION, WON, LOST
    val priority: String = "HOT", // HOT, WARM, COLD
    val budget: String = "₹15,000 - ₹50,000",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val nextFollowUpDate: Long? = null,
    val nextFollowUpNote: String = "",
    val isDuplicate: Boolean = false,
    val duplicateHitCount: Int = 1,
    val dealValue: Double = 25000.0
)
