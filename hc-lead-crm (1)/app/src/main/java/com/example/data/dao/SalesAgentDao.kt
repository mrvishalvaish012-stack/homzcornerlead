package com.example.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.SalesAgentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SalesAgentDao {
    @Query("SELECT * FROM sales_agents ORDER BY name ASC")
    fun getAllAgentsFlow(): Flow<List<SalesAgentEntity>>

    @Query("SELECT * FROM sales_agents WHERE role = 'AGENT' AND isActive = 1 ORDER BY lastAssignedAt ASC, id ASC")
    suspend fun getActiveAgentsForRoundRobin(): List<SalesAgentEntity>

    @Query("SELECT * FROM sales_agents WHERE id = :id LIMIT 1")
    suspend fun getAgentById(id: Long): SalesAgentEntity?

    @Query("SELECT * FROM sales_agents WHERE LOWER(TRIM(email)) = LOWER(TRIM(:identifier)) OR TRIM(phoneNumber) = TRIM(:identifier) OR REPLACE(REPLACE(phoneNumber, ' ', ''), '-', '') = REPLACE(REPLACE(:identifier, ' ', ''), '-', '') LIMIT 1")
    suspend fun findAgentByIdentifier(identifier: String): SalesAgentEntity?

    @Query("SELECT * FROM sales_agents WHERE LOWER(TRIM(email)) = LOWER(TRIM(:email)) LIMIT 1")
    suspend fun findAgentByEmail(email: String): SalesAgentEntity?

    @Query("SELECT * FROM sales_agents WHERE REPLACE(REPLACE(phoneNumber, ' ', ''), '-', '') = REPLACE(REPLACE(:phone, ' ', ''), '-', '') LIMIT 1")
    suspend fun findAgentByPhone(phone: String): SalesAgentEntity?

    @Query("UPDATE sales_agents SET password = :newPassword WHERE id = :agentId")
    suspend fun updatePassword(agentId: Long, newPassword: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAgent(agent: SalesAgentEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAgents(agents: List<SalesAgentEntity>)

    @Update
    suspend fun updateAgent(agent: SalesAgentEntity)

    @Delete
    suspend fun deleteAgent(agent: SalesAgentEntity)

    @Query("UPDATE sales_agents SET leadsAssignedCount = leadsAssignedCount + 1, lastAssignedAt = :assignedAt WHERE id = :agentId")
    suspend fun recordLeadAssignment(agentId: Long, assignedAt: Long)

    @Query("UPDATE sales_agents SET leadsConvertedCount = leadsConvertedCount + 1 WHERE id = :agentId")
    suspend fun recordLeadConversion(agentId: Long)
}
