package com.example.data.dao

import androidx.room.*
import com.example.data.model.CampaignRoutingRuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CampaignRoutingDao {
    @Query("SELECT * FROM campaign_routing_rules ORDER BY createdAt DESC")
    fun getAllRulesFlow(): Flow<List<CampaignRoutingRuleEntity>>

    @Query("SELECT * FROM campaign_routing_rules WHERE isActive = 1")
    suspend fun getActiveRules(): List<CampaignRoutingRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: CampaignRoutingRuleEntity): Long

    @Update
    suspend fun updateRule(rule: CampaignRoutingRuleEntity)

    @Query("UPDATE campaign_routing_rules SET isActive = :isActive WHERE id = :ruleId")
    suspend fun setRuleActive(ruleId: Long, isActive: Boolean)

    @Query("UPDATE campaign_routing_rules SET leadsRoutedCount = leadsRoutedCount + 1 WHERE id = :ruleId")
    suspend fun incrementRoutedCount(ruleId: Long)

    @Query("DELETE FROM campaign_routing_rules WHERE id = :ruleId")
    suspend fun deleteRuleById(ruleId: Long)
}
