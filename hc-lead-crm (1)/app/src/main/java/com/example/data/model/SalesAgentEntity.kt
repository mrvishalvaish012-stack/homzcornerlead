package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sales_agents")
data class SalesAgentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    val email: String,
    val phoneNumber: String,
    val role: String = "AGENT", // AGENT, MANAGER
    val password: String = "123456", // Default password for initial seeds, customizable on registration
    val department: String = "Sales",
    val isActive: Boolean = true, // Whether agent receives leads in round-robin
    val leadsAssignedCount: Int = 0,
    val leadsConvertedCount: Int = 0,
    val lastAssignedAt: Long = 0L,
    val registeredAt: Long = System.currentTimeMillis(),
    val avatarColorHex: Long = 0xFF2563EB
)
