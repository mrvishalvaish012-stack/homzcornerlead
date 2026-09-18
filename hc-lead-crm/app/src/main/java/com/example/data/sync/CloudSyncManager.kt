package com.example.data.sync

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.data.model.LeadEntity
import com.example.data.repository.LeadRepository
import com.example.data.service.CloudServerHealth
import com.example.data.service.CloudSyncService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

data class CloudSyncState(
    val isSyncing: Boolean = false,
    val lastSyncTimestamp: Long = 0L,
    val serverUrl: String = "",
    val isConnected: Boolean = false,
    val autoSyncEnabled: Boolean = true,
    val lastStatusMessage: String = "Configure Server URL in Team Cloud Sync",
    val lastLatencyMs: Long = 0L,
    val totalServerLeads: Int = 0,
    val lastSyncedCount: Int = 0
)

class CloudSyncManager(
    private val context: Context,
    private val repository: LeadRepository,
    private val coroutineScope: CoroutineScope
) {
    private val TAG = "CloudSyncManager"

    private val sharedPrefs: SharedPreferences =
        context.getSharedPreferences("leadpulse_cloud_sync_prefs", Context.MODE_PRIVATE)

    private val savedUrl: String = sharedPrefs.getString("server_url", "") ?: ""
    // Avoid running unverified default dummy domain
    private val initialUrl: String = if (savedUrl == "https://leadpulse-crm.onrender.com") "" else savedUrl

    private val _syncState = MutableStateFlow(
        CloudSyncState(
            serverUrl = initialUrl,
            autoSyncEnabled = sharedPrefs.getBoolean("auto_sync_enabled", true),
            lastSyncTimestamp = sharedPrefs.getLong("last_sync_timestamp", 0L)
        )
    )
    val syncState: StateFlow<CloudSyncState> = _syncState.asStateFlow()

    private val deviceId: String = sharedPrefs.getString("device_id", null) ?: run {
        val newId = "android_" + UUID.randomUUID().toString().take(8)
        sharedPrefs.edit().putString("device_id", newId).apply()
        newId
    }

    private var autoSyncJob: Job? = null

    init {
        startAutoSyncLoop()
    }

    fun updateServerUrl(url: String) {
        val clean = url.trim()
        sharedPrefs.edit().putString("server_url", clean).apply()
        _syncState.value = _syncState.value.copy(serverUrl = clean, isConnected = false)
        if (clean.isNotBlank()) {
            coroutineScope.launch {
                checkConnection()
            }
        }
    }

    fun setAutoSyncEnabled(enabled: Boolean) {
        sharedPrefs.edit().putBoolean("auto_sync_enabled", enabled).apply()
        _syncState.value = _syncState.value.copy(autoSyncEnabled = enabled)
        if (enabled) {
            startAutoSyncLoop()
        } else {
            autoSyncJob?.cancel()
            autoSyncJob = null
        }
    }

    private fun startAutoSyncLoop() {
        autoSyncJob?.cancel()
        autoSyncJob = coroutineScope.launch(Dispatchers.IO) {
            while (isActive) {
                val currentUrl = _syncState.value.serverUrl
                if (_syncState.value.autoSyncEnabled && currentUrl.isNotBlank()) {
                    // Only push/pull if server is verified to be online
                    if (!_syncState.value.isConnected) {
                        val health = CloudSyncService.checkHealth(currentUrl)
                        _syncState.value = _syncState.value.copy(
                            isConnected = health.isOnline,
                            lastLatencyMs = health.latencyMs,
                            totalServerLeads = health.leadsCount,
                            lastStatusMessage = if (health.isOnline) "🟢 Connected to Team Cloud (${health.latencyMs}ms)" else "🔴 Server Unreachable"
                        )
                    }

                    if (_syncState.value.isConnected) {
                        try {
                            syncInternal(isManual = false)
                        } catch (e: Exception) {
                            Log.w(TAG, "Background auto-sync tick error: ${e.message}")
                        }
                    }
                }
                // Sync every 20 seconds for multi-device real-time collaboration
                delay(20_000L)
            }
        }
    }

    suspend fun checkConnection(): CloudServerHealth = withContext(Dispatchers.IO) {
        val url = _syncState.value.serverUrl
        if (url.isBlank()) {
            val h = CloudServerHealth(isOnline = false, message = "Server URL is empty")
            _syncState.value = _syncState.value.copy(isConnected = false, lastStatusMessage = "Server URL not set")
            return@withContext h
        }

        val health = CloudSyncService.checkHealth(url)
        _syncState.value = _syncState.value.copy(
            isConnected = health.isOnline,
            lastLatencyMs = health.latencyMs,
            totalServerLeads = health.leadsCount,
            lastStatusMessage = if (health.isOnline) "🟢 Connected to Team Cloud (${health.latencyMs}ms)" else "🔴 Server Unreachable"
        )
        health
    }

    suspend fun triggerManualSync(): Result<String> = withContext(Dispatchers.IO) {
        syncInternal(isManual = true)
    }

    /**
     * Bidirectional Cloud Synchronization:
     * 1. PUSH local leads and feedbacks that have been added/modified to Cloud Server.
     * 2. PULL new/updated leads and feedbacks from other team members from Cloud Server.
     * 3. Upsert into local SQLite Room database.
     */
    private suspend fun syncInternal(isManual: Boolean): Result<String> = withContext(Dispatchers.IO) {
        val url = _syncState.value.serverUrl
        if (url.isBlank()) {
            return@withContext Result.failure(Exception("Cloud Server URL is empty"))
        }

        if (_syncState.value.isSyncing) {
            return@withContext Result.success("Sync already in progress")
        }

        _syncState.value = _syncState.value.copy(isSyncing = true)

        try {
            // STEP 1: PUSH LOCAL CHANGES TO CENTRAL SERVER
            val localLeads = repository.getAllLeadsList()
            val localFeedbacks = repository.getAllFeedbacksList()
            val localAgents = repository.getAllAgentsList()

            val pushResult = CloudSyncService.pushUpdates(
                serverUrl = url,
                clientDeviceId = deviceId,
                clientAgentName = "Sales App",
                leads = localLeads,
                feedbacks = localFeedbacks,
                agents = localAgents
            )

            // STEP 2: PULL CLOUD CHANGES (since last sync or full)
            val since = if (isManual) 0L else _syncState.value.lastSyncTimestamp
            val pullResult = CloudSyncService.pullUpdates(url, since)

            if (pullResult.isSuccess) {
                val payload = pullResult.getOrThrow()

                // Merge incoming leads from team into local Room database
                if (payload.leads.isNotEmpty()) {
                    val localMap = localLeads.associateBy { it.phoneNumber }
                    val mergedToInsert = mutableListOf<LeadEntity>()

                    for (cloudLead in payload.leads) {
                        val localExisting = localMap[cloudLead.phoneNumber]
                        if (localExisting == null) {
                            mergedToInsert.add(cloudLead)
                        } else {
                            // Update local if cloud has newer timestamp or status change
                            if (cloudLead.updatedAt >= localExisting.updatedAt) {
                                mergedToInsert.add(
                                    cloudLead.copy(id = localExisting.id)
                                )
                            }
                        }
                    }

                    if (mergedToInsert.isNotEmpty()) {
                        repository.insertLeads(mergedToInsert)
                    }
                }

                // Merge feedbacks/notes
                if (payload.feedbacks.isNotEmpty()) {
                    repository.insertFeedbacks(payload.feedbacks)
                }

                val now = System.currentTimeMillis()
                sharedPrefs.edit().putLong("last_sync_timestamp", now).apply()

                val summary = "Synced with Team Cloud: +${payload.leads.size} leads, +${payload.feedbacks.size} activities"
                _syncState.value = _syncState.value.copy(
                    isSyncing = false,
                    isConnected = true,
                    lastSyncTimestamp = now,
                    totalServerLeads = payload.totalServerLeads,
                    lastSyncedCount = payload.leads.size,
                    lastStatusMessage = "🟢 In Sync with Team (${payload.totalServerLeads} leads on Cloud)"
                )
                Result.success(summary)
            } else {
                val err = pullResult.exceptionOrNull()?.message ?: "Failed to pull cloud updates"
                _syncState.value = _syncState.value.copy(
                    isSyncing = false,
                    isConnected = false,
                    lastStatusMessage = "⚠️ Sync Error: $err"
                )
                Result.failure(Exception(err))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Sync exception: ${e.message}")
            _syncState.value = _syncState.value.copy(
                isSyncing = false,
                isConnected = false,
                lastStatusMessage = "⚠️ Connection error: ${e.localizedMessage}"
            )
            Result.failure(e)
        }
    }
}
