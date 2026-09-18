package com.example.data.auth

import android.content.Context
import android.content.SharedPreferences

class SessionManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("leadpulse_user_auth_session", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_ROLE = "user_role" // "ADMIN" or "SALES_PERSON"
        private const val KEY_AGENT_ID = "logged_agent_id"
        private const val KEY_AGENT_NAME = "logged_agent_name"
        private const val KEY_AGENT_EMAIL = "logged_agent_email"
        private const val KEY_AGENT_PHONE = "logged_agent_phone"
        private const val KEY_LAST_LOGIN_TIME = "last_login_time"
    }

    fun saveSession(
        role: String,
        agentId: Long? = null,
        agentName: String? = null,
        agentEmail: String? = null,
        agentPhone: String? = null
    ) {
        prefs.edit().apply {
            putBoolean(KEY_IS_LOGGED_IN, true)
            putString(KEY_ROLE, role)
            if (agentId != null) {
                putLong(KEY_AGENT_ID, agentId)
            } else {
                remove(KEY_AGENT_ID)
            }
            putString(KEY_AGENT_NAME, agentName ?: "")
            putString(KEY_AGENT_EMAIL, agentEmail ?: "")
            putString(KEY_AGENT_PHONE, agentPhone ?: "")
            putLong(KEY_LAST_LOGIN_TIME, System.currentTimeMillis())
            apply()
        }
    }

    fun isLoggedIn(): Boolean = prefs.getBoolean(KEY_IS_LOGGED_IN, false)

    fun hasInitialized(): Boolean = prefs.contains(KEY_IS_LOGGED_IN)

    fun getRole(): String = prefs.getString(KEY_ROLE, "ADMIN") ?: "ADMIN"

    fun getAgentId(): Long? {
        val id = prefs.getLong(KEY_AGENT_ID, -1L)
        return if (id != -1L) id else null
    }

    fun getAgentName(): String = prefs.getString(KEY_AGENT_NAME, "") ?: ""

    fun getAgentEmail(): String = prefs.getString(KEY_AGENT_EMAIL, "") ?: ""

    fun getAgentPhone(): String = prefs.getString(KEY_AGENT_PHONE, "") ?: ""

    fun clearSession() {
        prefs.edit().apply {
            putBoolean(KEY_IS_LOGGED_IN, false)
            remove(KEY_ROLE)
            remove(KEY_AGENT_ID)
            remove(KEY_AGENT_NAME)
            remove(KEY_AGENT_EMAIL)
            remove(KEY_AGENT_PHONE)
            apply()
        }
    }
}
