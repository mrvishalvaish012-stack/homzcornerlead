package com.example.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.LeadEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LeadDao {
    @Query("SELECT * FROM leads ORDER BY updatedAt DESC")
    fun getAllLeadsFlow(): Flow<List<LeadEntity>>

    @Query("SELECT * FROM leads WHERE assignedAgentId = :agentId ORDER BY updatedAt DESC")
    fun getLeadsForAgentFlow(agentId: Long): Flow<List<LeadEntity>>

    @Query("SELECT * FROM leads WHERE id = :id")
    fun getLeadById(id: Long): Flow<LeadEntity?>

    @Query("SELECT * FROM leads WHERE phoneNumber = :phoneNumber LIMIT 1")
    suspend fun findLeadByPhoneNumber(phoneNumber: String): LeadEntity?

    @Query("SELECT * FROM leads WHERE nextFollowUpDate IS NOT NULL AND nextFollowUpDate <= :cutoffTime AND status NOT IN ('WON', 'LOST') ORDER BY nextFollowUpDate ASC")
    suspend fun getDueFollowUps(cutoffTime: Long): List<LeadEntity>

    @Query("SELECT * FROM leads WHERE assignedAgentId = :agentId AND nextFollowUpDate IS NOT NULL AND nextFollowUpDate <= :cutoffTime AND status NOT IN ('WON', 'LOST') ORDER BY nextFollowUpDate ASC")
    suspend fun getDueFollowUpsForAgent(agentId: Long, cutoffTime: Long): List<LeadEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLead(lead: LeadEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLeads(leads: List<LeadEntity>)

    @Query("SELECT * FROM leads ORDER BY updatedAt DESC")
    suspend fun getAllLeadsList(): List<LeadEntity>

    @Update
    suspend fun updateLead(lead: LeadEntity)

    @Query("UPDATE leads SET initialMessage = :newMessage, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateLeadMessage(id: Long, newMessage: String, updatedAt: Long)

    @Delete
    suspend fun deleteLead(lead: LeadEntity)

    @Query("SELECT COUNT(*) FROM leads")
    fun getTotalLeadsCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM leads WHERE status = 'WON'")
    fun getConvertedLeadsCount(): Flow<Int>
}
