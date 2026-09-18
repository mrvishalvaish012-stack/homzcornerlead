package com.example.data.service

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class WebhookTestResult(
    val isSuccess: Boolean,
    val statusCode: Int,
    val message: String,
    val responseBody: String? = null,
    val latencyMs: Long = 0L
)

data class MetaLeadPayload(
    val leadId: String,
    val createdTime: String,
    val fullName: String,
    val phoneNumber: String,
    val email: String,
    val customNotes: String,
    val formId: String
)

object MetaGraphApiService {
    private const val TAG = "MetaGraphApiService"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * Test the webhook URL by simulating Meta's GET verification handshake:
     * GET {url}?hub.mode=subscribe&hub.challenge={test_challenge}&hub.verify_token={verify_token}
     */
    suspend fun testWebhookHandshake(
        callbackUrl: String,
        verifyToken: String
    ): WebhookTestResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        try {
            val trimmedUrl = callbackUrl.trim()
            if (!trimmedUrl.startsWith("http://") && !trimmedUrl.startsWith("https://")) {
                return@withContext WebhookTestResult(
                    isSuccess = false,
                    statusCode = 0,
                    message = "Invalid URL: Must start with https://",
                    latencyMs = 0
                )
            }

            val testChallenge = "challenge_${System.currentTimeMillis() % 100000}"
            val delimiter = if (trimmedUrl.contains("?")) "&" else "?"
            val fullTestUrl = "$trimmedUrl${delimiter}hub.mode=subscribe&hub.challenge=$testChallenge&hub.verify_token=${verifyToken.trim()}"

            val request = Request.Builder()
                .url(fullTestUrl)
                .get()
                .header("User-Agent", "facebookplatform/1.0 (+http://developers.facebook.com)")
                .build()

            val response = client.newCall(request).execute()
            val latency = System.currentTimeMillis() - startTime
            val body = response.body?.string()?.trim() ?: ""

            if (response.isSuccessful) {
                if (body == testChallenge || body.contains(testChallenge)) {
                    WebhookTestResult(
                        isSuccess = true,
                        statusCode = response.code,
                        message = "Handshake verified! Server returned HTTP ${response.code} and matched the challenge string. Meta will accept this URL.",
                        responseBody = body,
                        latencyMs = latency
                    )
                } else {
                    WebhookTestResult(
                        isSuccess = false,
                        statusCode = response.code,
                        message = "Server returned HTTP ${response.code}, but response body did not match challenge string '$testChallenge'. Body: '$body'. Check token or GET handler.",
                        responseBody = body,
                        latencyMs = latency
                    )
                }
            } else {
                WebhookTestResult(
                    isSuccess = false,
                    statusCode = response.code,
                    message = "Server returned HTTP error ${response.code}: ${response.message}. Ensure your endpoint is live and accepts GET requests.",
                    responseBody = body,
                    latencyMs = latency
                )
            }
        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - startTime
            Log.e(TAG, "Webhook test error", e)
            WebhookTestResult(
                isSuccess = false,
                statusCode = 0,
                message = "Connection failed: ${e.localizedMessage ?: "Unknown network error"}",
                latencyMs = latency
            )
        }
    }

    /**
     * Fetch real leads directly from Meta Graph API using Page Access Token and Form ID.
     * GET https://graph.facebook.com/v21.0/{form_id}/leads?access_token={token}&fields=id,created_time,field_data
     */
    suspend fun fetchLiveLeadsFromMeta(
        pageAccessToken: String,
        formId: String
    ): Result<List<MetaLeadPayload>> = withContext(Dispatchers.IO) {
        try {
            val cleanToken = pageAccessToken.trim()
            val cleanFormId = formId.trim()

            if (cleanToken.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("Page Access Token cannot be blank."))
            }
            if (cleanFormId.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("Form ID cannot be blank."))
            }

            val url = "https://graph.facebook.com/v21.0/$cleanFormId/leads?access_token=$cleanToken&fields=id,created_time,field_data&limit=25"

            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                // Parse error message from Meta JSON
                val errorMsg = try {
                    val errorObj = JSONObject(body).optJSONObject("error")
                    errorObj?.optString("message") ?: "HTTP ${response.code}: ${response.message}"
                } catch (_: Exception) {
                    "HTTP ${response.code}: ${response.message}"
                }
                return@withContext Result.failure(Exception("Meta Graph API error: $errorMsg"))
            }

            val json = JSONObject(body)
            val dataArray = json.optJSONArray("data") ?: return@withContext Result.success(emptyList())

            val leads = mutableListOf<MetaLeadPayload>()
            for (i in 0 until dataArray.length()) {
                val item = dataArray.getJSONObject(i)
                val leadId = item.optString("id", "fb_${System.currentTimeMillis()}_$i")
                val createdTime = item.optString("created_time", "")
                val fieldDataArray = item.optJSONArray("field_data")

                var name = ""
                var phone = ""
                var email = ""
                val otherFields = StringBuilder()

                if (fieldDataArray != null) {
                    for (j in 0 until fieldDataArray.length()) {
                        val field = fieldDataArray.getJSONObject(j)
                        val fieldName = field.optString("name").lowercase()
                        val values = field.optJSONArray("values")
                        val firstValue = if (values != null && values.length() > 0) values.getString(0) else ""

                        when {
                            fieldName.contains("full_name") || fieldName == "name" || fieldName.contains("first_name") -> {
                                if (name.isBlank()) name = firstValue
                            }
                            fieldName.contains("phone") -> {
                                if (phone.isBlank()) phone = firstValue
                            }
                            fieldName.contains("email") -> {
                                if (email.isBlank()) email = firstValue
                            }
                            else -> {
                                if (firstValue.isNotBlank()) {
                                    if (otherFields.isNotEmpty()) otherFields.append(", ")
                                    otherFields.append("${field.optString("name")}: $firstValue")
                                }
                            }
                        }
                    }
                }

                if (name.isBlank()) name = "Facebook Lead $leadId"
                if (phone.isBlank()) phone = "+91 98000 00000"

                leads.add(
                    MetaLeadPayload(
                        leadId = leadId,
                        createdTime = createdTime,
                        fullName = name,
                        phoneNumber = phone,
                        email = email,
                        customNotes = otherFields.toString(),
                        formId = cleanFormId
                    )
                )
            }

            Result.success(leads)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching from Meta Graph API", e)
            Result.failure(e)
        }
    }

    /**
     * Check health status of custom backend webhook server
     */
    suspend fun checkBackendHealth(
        backendUrl: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val trimmedUrl = backendUrl.trim().removeSuffix("/")
            val url = "$trimmedUrl/health"
            val request = Request.Builder().url(url).get().build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""
            if (response.isSuccessful) {
                Result.success("Server Online: HTTP ${response.code}")
            } else {
                Result.failure(Exception("HTTP ${response.code}: $body"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch captured leads from custom backend server:
     * GET {backendUrl}/api/leads
     */
    suspend fun fetchLeadsFromBackend(
        backendUrl: String
    ): Result<List<MetaLeadPayload>> = withContext(Dispatchers.IO) {
        try {
            val trimmedUrl = backendUrl.trim().removeSuffix("/")
            val url = "$trimmedUrl/api/leads?limit=100"
            val request = Request.Builder().url(url).get().build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}: $body"))
            }

            val json = JSONObject(body)
            val leadsArray = json.optJSONArray("leads") ?: return@withContext Result.success(emptyList())

            val leads = mutableListOf<MetaLeadPayload>()
            for (i in 0 until leadsArray.length()) {
                val item = leadsArray.getJSONObject(i)
                leads.add(
                    MetaLeadPayload(
                        leadId = item.optString("id", "lead_${System.currentTimeMillis()}_$i"),
                        createdTime = item.optString("createdAt", ""),
                        fullName = item.optString("name", "Incoming Lead"),
                        phoneNumber = item.optString("phone", "+91 98000 00000"),
                        email = item.optString("email", ""),
                        customNotes = item.optString("message", ""),
                        formId = item.optString("source", "Meta Webhook Backend")
                    )
                )
            }
            Result.success(leads)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching from backend server", e)
            Result.failure(e)
        }
    }
}
