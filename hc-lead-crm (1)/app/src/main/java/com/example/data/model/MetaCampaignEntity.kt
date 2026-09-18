package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "meta_campaigns")
data class MetaCampaignEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val campaignName: String,
    val adAccountId: String = "act_4091827401",
    val platform: String = "Instagram & Facebook", // "Instagram", "Facebook", "Instagram & Facebook"
    val leadFormName: String = "Instant Lead Gen Form",
    val distributionMode: String = "ROUND_ROBIN", // "ROUND_ROBIN" or "SINGLE_AGENT"
    val assignedAgentId: Long = 0L, // 0 if ROUND_ROBIN, or specific agent ID
    val assignedAgentName: String = "Team Round-Robin", // "Team Round-Robin" or Agent name
    val isActive: Boolean = true,
    val totalLeadsReceived: Int = 0,
    val lastSyncTimestamp: Long = System.currentTimeMillis(),
    val campaignBudgetDaily: String = "₹1,500/day",
    val campaignObjective: String = "LEAD_GENERATION",
    val createdAt: Long = System.currentTimeMillis()
)
