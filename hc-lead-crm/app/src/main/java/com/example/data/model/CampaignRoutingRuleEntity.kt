package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "campaign_routing_rules")
data class CampaignRoutingRuleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val campaignPattern: String, // e.g., "Luxury 3BHK", "Solar Rooftop", "Meta Lead Gen"
    val assignedAgentId: Long,
    val assignedAgentName: String,
    val isActive: Boolean = true,
    val ruleDescription: String = "",
    val leadsRoutedCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)
