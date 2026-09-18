package com.example.data.service

import android.util.Log
import com.example.data.model.FeedbackLogEntity
import com.example.data.model.LeadEntity
import com.example.data.model.SalesAgentEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class CloudServerHealth(
    val isOnline: Boolean,
    val leadsCount: Int = 0,
    val agentsCount: Int = 0,
    val feedbacksCount: Int = 0,
    val serverTime: Long = 0L,
    val latencyMs: Long = 0L,
    val message: String = ""
)

data class CloudSyncPayload(
    val leads: List<LeadEntity>,
    val feedbacks: List<FeedbackLogEntity>,
    val agents: List<SalesAgentEntity>,
    val serverTime: Long,
    val totalServerLeads: Int
)

data class CloudSyncPushResult(
    val isSuccess: Boolean,
    val mergedLeads: Int = 0,
    val mergedFeedbacks: Int = 0,
    val serverTime: Long = 0L,
    val message: String = ""
)

object CloudSyncService {
    private const val TAG = "CloudSyncService"

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    private fun normalizeBaseUrl(rawUrl: String): String {
        var url = rawUrl.trim()
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://$url"
        }
        return url.removeSuffix("/")
    }

    /**
     * Check if the central team backend server is reachable and online.
     */
    suspend fun checkHealth(serverUrl: String): CloudServerHealth = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        try {
            val base = normalizeBaseUrl(serverUrl)
            if (base.isBlank() || base == "https://" || base == "http://") {
                return@withContext CloudServerHealth(isOnline = false, message = "Server URL is empty")
            }

            val request = Request.Builder()
                .url("$base/health")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val latency = System.currentTimeMillis() - startTime
                val body = response.body?.string()?.trim() ?: ""

                if (!response.isSuccessful) {
                    return@withContext CloudServerHealth(
                        isOnline = false,
                        latencyMs = latency,
                        message = "Server returned HTTP ${response.code}"
                    )
                }

                if (body.isEmpty() || !body.startsWith("{")) {
                    return@withContext CloudServerHealth(
                        isOnline = false,
                        latencyMs = latency,
                        message = "Non-JSON response (HTTP ${response.code}). Check server URL."
                    )
                }

                val json = JSONObject(body)
                CloudServerHealth(
                    isOnline = true,
                    leadsCount = json.optInt("leadsCount", 0),
                    agentsCount = json.optInt("agentsCount", 0),
                    feedbacksCount = json.optInt("feedbacksCount", 0),
                    serverTime = json.optLong("serverTime", System.currentTimeMillis()),
                    latencyMs = latency,
                    message = "Connected to Team Cloud ($latency ms)"
                )
            }
        } catch (e: Exception) {
            CloudServerHealth(
                isOnline = false,
                latencyMs = System.currentTimeMillis() - startTime,
                message = e.localizedMessage ?: "Could not reach server. Check network connection."
            )
        }
    }

    /**
     * Pull delta updates from the cloud server since [sinceTimestamp].
     */
    suspend fun pullUpdates(serverUrl: String, sinceTimestamp: Long): Result<CloudSyncPayload> = withContext(Dispatchers.IO) {
        try {
            val base = normalizeBaseUrl(serverUrl)
            if (base.isBlank() || base == "https://" || base == "http://") {
                return@withContext Result.failure(Exception("Server URL is empty"))
            }

            val url = "$base/api/sync/pull?since=$sinceTimestamp"

            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string()?.trim() ?: ""

                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("Cloud pull failed with HTTP ${response.code}"))
                }

                if (body.isEmpty() || !body.startsWith("{")) {
                    return@withContext Result.failure(Exception("Server returned non-JSON response (HTTP ${response.code})"))
                }

                val json = JSONObject(body)
                val serverTime = json.optLong("serverTime", System.currentTimeMillis())
                val totalServerLeads = json.optInt("totalServerLeads", 0)

                // Parse Leads
                val leadsArray = json.optJSONArray("leads") ?: JSONArray()
                val parsedLeads = mutableListOf<LeadEntity>()
                for (i in 0 until leadsArray.length()) {
                    val item = leadsArray.getJSONObject(i)
                    parsedLeads.add(
                        LeadEntity(
                            id = item.optLong("id", 0L),
                            name = item.optString("name", "Unnamed Customer"),
                            phoneNumber = item.optString("phoneNumber", item.optString("phone", "")),
                            email = item.optString("email", ""),
                            source = item.optString("source", "Cloud Sync"),
                            campaignName = item.optString("campaignName", "All India Campaign"),
                            initialMessage = item.optString("initialMessage", item.optString("message", "")),
                            assignedAgentId = item.optLong("assignedAgentId", 1L),
                            assignedAgentName = item.optString("assignedAgentName", "Vikram Malhotra"),
                            status = item.optString("status", "NEW"),
                            priority = item.optString("priority", "HOT"),
                            budget = item.optString("budget", "₹15,000 - ₹50,000"),
                            createdAt = item.optLong("createdAt", System.currentTimeMillis()),
                            updatedAt = item.optLong("updatedAt", System.currentTimeMillis()),
                            nextFollowUpDate = if (item.has("nextFollowUpDate") && !item.isNull("nextFollowUpDate")) item.optLong("nextFollowUpDate") else null,
                            nextFollowUpNote = item.optString("nextFollowUpNote", ""),
                            isDuplicate = item.optBoolean("isDuplicate", false),
                            duplicateHitCount = item.optInt("duplicateHitCount", 1),
                            dealValue = item.optDouble("dealValue", 25000.0)
                        )
                    )
                }

                // Parse Feedbacks
                val feedbacksArray = json.optJSONArray("feedbacks") ?: JSONArray()
                val parsedFeedbacks = mutableListOf<FeedbackLogEntity>()
                for (i in 0 until feedbacksArray.length()) {
                    val item = feedbacksArray.getJSONObject(i)
                    parsedFeedbacks.add(
                        FeedbackLogEntity(
                            id = item.optLong("id", 0L),
                            leadId = item.optLong("leadId", 0L),
                            agentId = item.optLong("agentId", 1L),
                            agentName = item.optString("agentName", "Sales Agent"),
                            timestamp = item.optLong("timestamp", System.currentTimeMillis()),
                            disposition = item.optString("disposition", "Update"),
                            notes = item.optString("notes", ""),
                            nextFollowUpDate = if (item.has("nextFollowUpDate") && !item.isNull("nextFollowUpDate")) item.optLong("nextFollowUpDate") else null,
                            previousStatus = item.optString("previousStatus", ""),
                            newStatus = item.optString("newStatus", "")
                        )
                    )
                }

                // Parse Agents
                val agentsArray = json.optJSONArray("agents") ?: JSONArray()
                val parsedAgents = mutableListOf<SalesAgentEntity>()
                for (i in 0 until agentsArray.length()) {
                    val item = agentsArray.getJSONObject(i)
                    parsedAgents.add(
                        SalesAgentEntity(
                            id = item.optLong("id", 0L),
                            name = item.optString("name", "Agent"),
                            email = item.optString("email", ""),
                            phoneNumber = item.optString("phoneNumber", ""),
                            role = item.optString("role", "AGENT"),
                            isActive = item.optBoolean("isActive", true),
                            leadsAssignedCount = item.optInt("leadsAssignedCount", 0),
                            leadsConvertedCount = item.optInt("leadsConvertedCount", 0),
                            lastAssignedAt = item.optLong("lastAssignedAt", 0L),
                            avatarColorHex = item.optLong("avatarColorHex", 0xFF2563EB)
                        )
                    )
                }

                Result.success(
                    CloudSyncPayload(
                        leads = parsedLeads,
                        feedbacks = parsedFeedbacks,
                        agents = parsedAgents,
                        serverTime = serverTime,
                        totalServerLeads = totalServerLeads
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Push local changes (leads, call feedbacks, notes) to the central cloud server.
     */
    suspend fun pushUpdates(
        serverUrl: String,
        clientDeviceId: String,
        clientAgentName: String,
        leads: List<LeadEntity>,
        feedbacks: List<FeedbackLogEntity>,
        agents: List<SalesAgentEntity>
    ): CloudSyncPushResult = withContext(Dispatchers.IO) {
        try {
            val base = normalizeBaseUrl(serverUrl)
            if (base.isBlank() || base == "https://" || base == "http://") {
                return@withContext CloudSyncPushResult(isSuccess = false, message = "Server URL is empty")
            }

            val root = JSONObject()
            root.put("clientDeviceId", clientDeviceId)
            root.put("clientAgentName", clientAgentName)

            val leadsArray = JSONArray()
            leads.forEach { lead ->
                val lObj = JSONObject()
                lObj.put("id", lead.id)
                lObj.put("name", lead.name)
                lObj.put("phoneNumber", lead.phoneNumber)
                lObj.put("email", lead.email)
                lObj.put("source", lead.source)
                lObj.put("campaignName", lead.campaignName)
                lObj.put("initialMessage", lead.initialMessage)
                lObj.put("assignedAgentId", lead.assignedAgentId)
                lObj.put("assignedAgentName", lead.assignedAgentName)
                lObj.put("status", lead.status)
                lObj.put("priority", lead.priority)
                lObj.put("budget", lead.budget)
                lObj.put("createdAt", lead.createdAt)
                lObj.put("updatedAt", lead.updatedAt)
                lead.nextFollowUpDate?.let { lObj.put("nextFollowUpDate", it) }
                lObj.put("nextFollowUpNote", lead.nextFollowUpNote)
                lObj.put("isDuplicate", lead.isDuplicate)
                lObj.put("duplicateHitCount", lead.duplicateHitCount)
                lObj.put("dealValue", lead.dealValue)
                leadsArray.put(lObj)
            }
            root.put("leads", leadsArray)

            val feedbacksArray = JSONArray()
            feedbacks.forEach { fb ->
                val fObj = JSONObject()
                fObj.put("id", fb.id)
                fObj.put("leadId", fb.leadId)
                fObj.put("agentId", fb.agentId)
                fObj.put("agentName", fb.agentName)
                fObj.put("timestamp", fb.timestamp)
                fObj.put("disposition", fb.disposition)
                fObj.put("notes", fb.notes)
                fb.nextFollowUpDate?.let { fObj.put("nextFollowUpDate", it) }
                fObj.put("previousStatus", fb.previousStatus)
                fObj.put("newStatus", fb.newStatus)
                feedbacksArray.put(fObj)
            }
            root.put("feedbacks", feedbacksArray)

            val body = root.toString().toRequestBody(JSON_MEDIA_TYPE)
            val request = Request.Builder()
                .url("$base/api/sync/push")
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                val respBody = response.body?.string()?.trim() ?: ""

                if (!response.isSuccessful) {
                    return@withContext CloudSyncPushResult(
                        isSuccess = false,
                        message = "Server rejected push with HTTP ${response.code}"
                    )
                }

                if (respBody.isEmpty() || !respBody.startsWith("{")) {
                    return@withContext CloudSyncPushResult(
                        isSuccess = false,
                        message = "Server returned non-JSON response (HTTP ${response.code})"
                    )
                }

                val respJson = JSONObject(respBody)
                CloudSyncPushResult(
                    isSuccess = respJson.optBoolean("success", true),
                    mergedLeads = respJson.optInt("mergedLeads", 0),
                    mergedFeedbacks = respJson.optInt("mergedFeedbacks", 0),
                    serverTime = respJson.optLong("serverTime", System.currentTimeMillis()),
                    message = "Synced successfully"
                )
            }
        } catch (e: Exception) {
            CloudSyncPushResult(
                isSuccess = false,
                message = e.localizedMessage ?: "Failed to push updates"
            )
        }
    }
}
