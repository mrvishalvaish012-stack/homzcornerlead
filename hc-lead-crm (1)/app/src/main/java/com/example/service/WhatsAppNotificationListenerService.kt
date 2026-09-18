package com.example.service

import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.data.database.AppDatabase
import com.example.data.repository.LeadRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Android NotificationListenerService to automatically capture incoming
 * WhatsApp & WhatsApp Business messages directly into LeadPulse CRM database.
 */
class WhatsAppNotificationListenerService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var leadRepository: LeadRepository? = null

    override fun onCreate() {
        super.onCreate()
        val db = AppDatabase.getDatabase(applicationContext, serviceScope)
        leadRepository = LeadRepository(
            leadDao = db.leadDao(),
            salesAgentDao = db.salesAgentDao(),
            feedbackDao = db.feedbackDao(),
            notificationDao = db.notificationDao(),
            campaignRoutingDao = db.campaignRoutingDao(),
            metaCampaignDao = db.metaCampaignDao(),
            appContext = applicationContext
        )
        Log.d("WhatsAppListener", "WhatsApp Notification Listener Service Started!")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val pkg = sbn.packageName ?: return

        // Capture only from standard WhatsApp (com.whatsapp) or WhatsApp Business (com.whatsapp.w4b)
        if (pkg != "com.whatsapp" && pkg != "com.whatsapp.w4b") {
            return
        }

        val extras = sbn.notification.extras ?: return
        val senderTitle = extras.getString("android.title") ?: extras.getCharSequence("android.title")?.toString() ?: ""
        val messageText = extras.getString("android.text") ?: extras.getCharSequence("android.text")?.toString() ?: ""

        // Ignore summary headers, group meta notices, or delivery statuses
        if (senderTitle.isBlank() || messageText.isBlank()) return
        if (senderTitle.equals("WhatsApp", ignoreCase = true) || senderTitle.equals("WhatsApp Business", ignoreCase = true)) return
        if (messageText.contains("messages", ignoreCase = true) && senderTitle.contains("WhatsApp")) return
        if (messageText.equals("Checking for new messages", ignoreCase = true)) return

        Log.d("WhatsAppListener", "Incoming WhatsApp captured from: $senderTitle | Msg: $messageText")

        serviceScope.launch {
            // Check if senderTitle is phone number or name
            val isNumeric = senderTitle.replace("[^0-9+]".toRegex(), "").length >= 7
            val customerName = if (isNumeric) "WhatsApp Contact" else senderTitle
            val customerPhone = if (isNumeric) senderTitle else "+91 9" + senderTitle.hashCode().toString().takeLast(9).padStart(9, '8')

            val lower = messageText.lowercase()
            val priority = if (lower.contains("urgent") || lower.contains("price") || lower.contains("ready") || lower.contains("booking") || lower.contains("buy")) {
                "HOT"
            } else if (lower.contains("details") || lower.contains("brochure") || lower.contains("rate")) {
                "WARM"
            } else {
                "COLD"
            }

            leadRepository?.ingestLead(
                name = customerName,
                phoneNumber = customerPhone,
                email = "${customerName.lowercase().replace(" ", "")}@whatsapp.com",
                source = if (pkg == "com.whatsapp.w4b") "WhatsApp Business" else "WhatsApp Direct",
                campaignName = "Direct Incoming WhatsApp",
                initialMessage = messageText,
                budget = "Standard Inquiry",
                dealValue = 25000.0,
                priority = priority
            )
        }
    }
}
