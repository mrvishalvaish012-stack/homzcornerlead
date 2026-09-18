package com.example.data.dao

import androidx.room.*
import com.example.data.model.MetaCampaignEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MetaCampaignDao {
    @Query("SELECT * FROM meta_campaigns ORDER BY createdAt DESC")
    fun getAllCampaignsFlow(): Flow<List<MetaCampaignEntity>>

    @Query("SELECT * FROM meta_campaigns ORDER BY createdAt DESC")
    suspend fun getAllCampaigns(): List<MetaCampaignEntity>

    @Query("SELECT * FROM meta_campaigns WHERE isActive = 1 ORDER BY createdAt DESC")
    suspend fun getActiveCampaigns(): List<MetaCampaignEntity>

    @Query("SELECT * FROM meta_campaigns WHERE id = :id LIMIT 1")
    suspend fun getCampaignById(id: Long): MetaCampaignEntity?

    @Query("SELECT * FROM meta_campaigns WHERE LOWER(campaignName) LIKE '%' || LOWER(:namePattern) || '%' LIMIT 1")
    suspend fun findCampaignByName(namePattern: String): MetaCampaignEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCampaign(campaign: MetaCampaignEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCampaigns(campaigns: List<MetaCampaignEntity>)

    @Update
    suspend fun updateCampaign(campaign: MetaCampaignEntity)

    @Query("UPDATE meta_campaigns SET isActive = :isActive WHERE id = :id")
    suspend fun setCampaignActive(id: Long, isActive: Boolean)

    @Query("UPDATE meta_campaigns SET distributionMode = :mode, assignedAgentId = :agentId, assignedAgentName = :agentName WHERE id = :campaignId")
    suspend fun updateDistributionRule(campaignId: Long, mode: String, agentId: Long, agentName: String)

    @Query("UPDATE meta_campaigns SET totalLeadsReceived = totalLeadsReceived + 1, lastSyncTimestamp = :syncTime WHERE id = :campaignId")
    suspend fun incrementLeadsCount(campaignId: Long, syncTime: Long = System.currentTimeMillis())

    @Query("DELETE FROM meta_campaigns WHERE id = :id")
    suspend fun deleteCampaignById(id: Long)
}
