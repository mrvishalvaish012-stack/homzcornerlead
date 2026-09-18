package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.AppNotificationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationDao {
    @Query("SELECT * FROM app_notifications ORDER BY timestamp DESC")
    fun getAllNotificationsFlow(): Flow<List<AppNotificationEntity>>

    @Query("SELECT * FROM app_notifications WHERE targetAgentId IS NULL OR targetAgentId = :agentId ORDER BY timestamp DESC")
    fun getNotificationsForAgentFlow(agentId: Long): Flow<List<AppNotificationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotification(notification: AppNotificationEntity): Long

    @Query("UPDATE app_notifications SET isRead = 1")
    suspend fun markAllAsRead()

    @Query("UPDATE app_notifications SET isRead = 1 WHERE id = :id")
    suspend fun markAsRead(id: Long)

    @Query("SELECT COUNT(*) FROM app_notifications WHERE isRead = 0")
    fun getUnreadCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM app_notifications WHERE isRead = 0 AND (targetAgentId IS NULL OR targetAgentId = :agentId)")
    fun getUnreadCountForAgentFlow(agentId: Long): Flow<Int>
}
