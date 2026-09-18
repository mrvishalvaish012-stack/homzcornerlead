package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.FeedbackLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FeedbackDao {
    @Query("SELECT * FROM feedback_logs WHERE leadId = :leadId ORDER BY timestamp DESC")
    fun getFeedbackLogsForLead(leadId: Long): Flow<List<FeedbackLogEntity>>

    @Query("SELECT * FROM feedback_logs ORDER BY timestamp DESC LIMIT 100")
    fun getAllRecentFeedbackLogs(): Flow<List<FeedbackLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFeedback(feedback: FeedbackLogEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFeedbacks(feedbacks: List<FeedbackLogEntity>)

    @Query("SELECT * FROM feedback_logs ORDER BY timestamp DESC")
    suspend fun getAllFeedbackLogsList(): List<FeedbackLogEntity>
}
