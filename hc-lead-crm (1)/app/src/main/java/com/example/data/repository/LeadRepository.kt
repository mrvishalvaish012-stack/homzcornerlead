package com.example.data.repository

import android.content.Context
import com.example.data.dao.CampaignRoutingDao
import com.example.data.dao.FeedbackDao
import com.example.data.dao.LeadDao
import com.example.data.dao.MetaCampaignDao
import com.example.data.dao.NotificationDao
import com.example.data.dao.SalesAgentDao
import com.example.data.model.AppNotificationEntity
import com.example.data.model.CampaignRoutingRuleEntity
import com.example.data.model.FeedbackLogEntity
import com.example.data.model.LeadEntity
import com.example.data.model.MetaCampaignEntity
import com.example.data.model.SalesAgentEntity
import com.example.data.util.CustomerMessageHelper
import com.example.notification.NotificationHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class RawLeadInput(
    val name: String,
    val phone: String,
    val email: String = "",
    val campaign: String = "Excel Import",
    val source: String = "Excel Import",
    val notes: String = "",
    val priority: String = "WARM"
)

data class BulkImportResult(
    val totalProcessed: Int,
    val newLeadsDistributed: Int,
    val duplicatesRetained: Int,
    val distributionSummary: Map<String, Int>
)

class LeadRepository(
    private val leadDao: LeadDao,
    private val salesAgentDao: SalesAgentDao,
    private val feedbackDao: FeedbackDao,
    private val notificationDao: NotificationDao,
    private val campaignRoutingDao: CampaignRoutingDao,
    private val metaCampaignDao: MetaCampaignDao,
    private val appContext: Context
) {
    val allLeads: Flow<List<LeadEntity>> = leadDao.getAllLeadsFlow()
    val allAgents: Flow<List<SalesAgentEntity>> = salesAgentDao.getAllAgentsFlow()
    val allNotifications: Flow<List<AppNotificationEntity>> = notificationDao.getAllNotificationsFlow()
    val allCampaignRules: Flow<List<CampaignRoutingRuleEntity>> = campaignRoutingDao.getAllRulesFlow()
    val allMetaCampaigns: Flow<List<MetaCampaignEntity>> = metaCampaignDao.getAllCampaignsFlow()
    val totalLeadsCount: Flow<Int> = leadDao.getTotalLeadsCount()
    val convertedLeadsCount: Flow<Int> = leadDao.getConvertedLeadsCount()
    val recentFeedbacks: Flow<List<FeedbackLogEntity>> = feedbackDao.getAllRecentFeedbackLogs()

    suspend fun addMetaCampaign(campaign: MetaCampaignEntity): Long = metaCampaignDao.insertCampaign(campaign)
    suspend fun updateMetaCampaign(campaign: MetaCampaignEntity) = metaCampaignDao.updateCampaign(campaign)
    suspend fun updateCampaignDistribution(campaignId: Long, mode: String, agentId: Long, agentName: String) =
        metaCampaignDao.updateDistributionRule(campaignId, mode, agentId, agentName)
    suspend fun toggleMetaCampaignActive(campaignId: Long, isActive: Boolean) =
        metaCampaignDao.setCampaignActive(campaignId, isActive)
    suspend fun deleteMetaCampaign(campaignId: Long) = metaCampaignDao.deleteCampaignById(campaignId)

    fun getLeadsForAgent(agentId: Long): Flow<List<LeadEntity>> {
        return leadDao.getLeadsForAgentFlow(agentId)
    }

    fun getNotificationsForAgent(agentId: Long): Flow<List<AppNotificationEntity>> {
        return notificationDao.getNotificationsForAgentFlow(agentId)
    }

    fun getFeedbackForLead(leadId: Long): Flow<List<FeedbackLogEntity>> {
        return feedbackDao.getFeedbackLogsForLead(leadId)
    }

    /**
     * Ingest lead from Meta Ads or WhatsApp.
     * Implements strict Round-Robin distribution among active agents,
     * AND ensures duplicate leads stay assigned to the same agent!
     */
    suspend fun ingestLead(
        name: String,
        phoneNumber: String,
        email: String = "",
        source: String = "Meta Ads",
        campaignName: String = "Meta Lead Gen Campaign",
        initialMessage: String = "",
        budget: String = "Standard",
        dealValue: Double = 25000.0,
        priority: String = "HOT"
    ): Pair<LeadEntity, Boolean> {
        val cleanPhone = phoneNumber.replace(Regex("[^0-9+]"), "").trim()
        val existingLead = leadDao.findLeadByPhoneNumber(cleanPhone)

        val now = System.currentTimeMillis()

        if (existingLead != null) {
            // DUPLICATE LEAD DETECTED:
            // Policy: Duplicate leads must stay assigned to the same original agent!
            val finalCleanIncoming = CustomerMessageHelper.resolveCustomerMessage(initialMessage, campaignName, cleanPhone)

            // Check if existing message was corrupted with phone numbers
            val isExistingCorrupted = CustomerMessageHelper.isPhoneOnlyString(existingLead.initialMessage, cleanPhone)

            val combinedMessage = if (isExistingCorrupted) {
                finalCleanIncoming
            } else {
                "${existingLead.initialMessage}\n• Latest: $finalCleanIncoming".trim()
            }

            val updatedDuplicate = existingLead.copy(
                name = if (name.isNotBlank() && !name.startsWith("+")) name else existingLead.name,
                email = if (email.isNotBlank()) email else existingLead.email,
                initialMessage = combinedMessage,
                isDuplicate = true,
                duplicateHitCount = existingLead.duplicateHitCount + 1,
                updatedAt = now,
                // If it was lost, re-open to FOLLOW_UP
                status = if (existingLead.status in listOf("LOST", "JUNK")) "FOLLOW_UP" else existingLead.status
            )
            leadDao.updateLead(updatedDuplicate)

            // Add audit feedback log
            feedbackDao.insertFeedback(
                FeedbackLogEntity(
                    leadId = updatedDuplicate.id,
                    agentId = updatedDuplicate.assignedAgentId,
                    agentName = updatedDuplicate.assignedAgentName,
                    timestamp = now,
                    disposition = "Duplicate Inquiry Received",
                    notes = "Repeat customer inquiry via $source ($campaignName). Retained with agent ${updatedDuplicate.assignedAgentName}.",
                    previousStatus = existingLead.status,
                    newStatus = updatedDuplicate.status
                )
            )

            // Alert agent and manager
            val notif = AppNotificationEntity(
                targetAgentId = updatedDuplicate.assignedAgentId,
                leadId = updatedDuplicate.id,
                title = "⚠️ Duplicate Lead Re-inquiry!",
                message = "${updatedDuplicate.name} ($cleanPhone) submitted another inquiry. Lead remains assigned to ${updatedDuplicate.assignedAgentName}.",
                type = "DUPLICATE_ALERT",
                timestamp = now
            )
            val notifId = notificationDao.insertNotification(notif).toInt()

            NotificationHelper.showSystemNotification(
                context = appContext,
                notificationId = notifId,
                title = notif.title,
                message = notif.message,
                type = "DUPLICATE_ALERT"
            )

            return Pair(updatedDuplicate, true)
        } else {
            // NEW LEAD: 1. Check Meta Campaigns distribution configuration first!
            val activeMetaCampaigns = metaCampaignDao.getActiveCampaigns()
            val cleanCampaign = campaignName.trim().lowercase()
            val matchedMeta = activeMetaCampaigns.firstOrNull { mc ->
                val cName = mc.campaignName.trim().lowercase()
                cName.isNotBlank() && (cleanCampaign.contains(cName) || cName.contains(cleanCampaign))
            }

            var ruleExplanation: String? = null
            val assignedAgent: SalesAgentEntity = if (matchedMeta != null) {
                metaCampaignDao.incrementLeadsCount(matchedMeta.id, now)
                if (matchedMeta.distributionMode == "SINGLE_AGENT" && matchedMeta.assignedAgentId > 0L) {
                    val singleRep = salesAgentDao.getAgentById(matchedMeta.assignedAgentId)
                    if (singleRep != null && singleRep.isActive) {
                        ruleExplanation = "🎯 Dedicated Single Salesperson Rule for '${matchedMeta.campaignName}'"
                        singleRep
                    } else {
                        val activeAgents = salesAgentDao.getActiveAgentsForRoundRobin()
                        val fallback = activeAgents.firstOrNull() ?: fallbackAgent()
                        ruleExplanation = "🔄 Round-Robin Fallback (Dedicated agent paused)"
                        fallback
                    }
                } else {
                    // Campaign is set to ROUND_ROBIN distribution!
                    val activeAgents = salesAgentDao.getActiveAgentsForRoundRobin()
                    val rotated = activeAgents.firstOrNull() ?: fallbackAgent()
                    ruleExplanation = "🔄 Round-Robin Team Rotation (Campaign: '${matchedMeta.campaignName}')"
                    rotated
                }
            } else {
                // 2. Check Custom Campaign Routing Rules next
                val activeRules = campaignRoutingDao.getActiveRules()
                val matchedRule = activeRules.firstOrNull { rule ->
                    val pattern = rule.campaignPattern.trim().lowercase()
                    pattern.isNotBlank() && (cleanCampaign.contains(pattern) || pattern.contains(cleanCampaign))
                }

                if (matchedRule != null) {
                    val targetAgent = salesAgentDao.getAgentById(matchedRule.assignedAgentId)
                    if (targetAgent != null && targetAgent.isActive) {
                        campaignRoutingDao.incrementRoutedCount(matchedRule.id)
                        ruleExplanation = "🎯 Custom Routing Rule ('${matchedRule.campaignPattern}')"
                        targetAgent
                    } else {
                        val activeAgents = salesAgentDao.getActiveAgentsForRoundRobin()
                        val fallback = activeAgents.firstOrNull() ?: fallbackAgent()
                        ruleExplanation = "🔄 Round-Robin Fallback"
                        fallback
                    }
                } else {
                    // 3. Default Team Round-Robin
                    val activeAgents = salesAgentDao.getActiveAgentsForRoundRobin()
                    val fallback = activeAgents.firstOrNull() ?: fallbackAgent()
                    ruleExplanation = "🔄 Team Round-Robin Rotation"
                    fallback
                }
            }

            // Update agent lastAssignedAt and assigned count
            salesAgentDao.recordLeadAssignment(assignedAgent.id, now)

            val finalNewMessage = CustomerMessageHelper.resolveCustomerMessage(initialMessage, campaignName, cleanPhone)

            val newLead = LeadEntity(
                name = name.ifBlank { "Meta Lead #$now" },
                phoneNumber = cleanPhone,
                email = email,
                source = source,
                campaignName = campaignName,
                initialMessage = finalNewMessage,
                assignedAgentId = assignedAgent.id,
                assignedAgentName = assignedAgent.name,
                status = "NEW",
                priority = priority,
                budget = budget,
                dealValue = dealValue,
                createdAt = now,
                updatedAt = now,
                nextFollowUpDate = now + 1800_000, // 30 min initial SLA
                nextFollowUpNote = "First touch follow-up scheduled",
                isDuplicate = false,
                duplicateHitCount = 1
            )

            val leadId = leadDao.insertLead(newLead)
            val createdLead = newLead.copy(id = leadId)

            // Dispatch notification with transparent rule info
            val isDirectSingle = ruleExplanation?.contains("🎯") == true
            val notifTitle = if (isDirectSingle) {
                "🎯 Campaign Lead Ingested (Single Salesperson Rule)!"
            } else {
                "🎉 New Lead Assigned (Round-Robin)"
            }

            val notifMessage = if (isDirectSingle) {
                "Lead: ${createdLead.name} ($cleanPhone) from Campaign '${createdLead.campaignName}' sent exclusively to ${assignedAgent.name} ($ruleExplanation)."
            } else {
                "New lead: ${createdLead.name} ($cleanPhone) assigned to ${assignedAgent.name} via $ruleExplanation."
            }

            val notif = AppNotificationEntity(
                targetAgentId = assignedAgent.id,
                leadId = leadId,
                title = notifTitle,
                message = notifMessage,
                type = "NEW_LEAD",
                timestamp = now
            )
            val notifId = notificationDao.insertNotification(notif).toInt()

            NotificationHelper.showSystemNotification(
                context = appContext,
                notificationId = notifId,
                title = notif.title,
                message = notif.message,
                type = "NEW_LEAD"
            )

            return Pair(createdLead, false)
        }
    }

    suspend fun addFeedback(
        lead: LeadEntity,
        agentId: Long,
        agentName: String,
        disposition: String,
        notes: String,
        newStatus: String,
        nextFollowUpDate: Long?,
        nextFollowUpNote: String
    ) {
        val now = System.currentTimeMillis()
        val prevStatus = lead.status

        feedbackDao.insertFeedback(
            FeedbackLogEntity(
                leadId = lead.id,
                agentId = agentId,
                agentName = agentName,
                timestamp = now,
                disposition = disposition,
                notes = notes,
                nextFollowUpDate = nextFollowUpDate,
                previousStatus = prevStatus,
                newStatus = newStatus
            )
        )

        val updatedLead = lead.copy(
            status = newStatus,
            updatedAt = now,
            nextFollowUpDate = nextFollowUpDate,
            nextFollowUpNote = nextFollowUpNote
        )
        leadDao.updateLead(updatedLead)

        if (newStatus == "WON" && prevStatus != "WON") {
            salesAgentDao.recordLeadConversion(lead.assignedAgentId)

            val notif = AppNotificationEntity(
                targetAgentId = null, // Broadcast to Manager
                leadId = lead.id,
                title = "🏆 Deal Won!",
                message = "Agent ${lead.assignedAgentName} successfully closed and converted lead ${lead.name}!",
                type = "STATUS_CHANGE",
                timestamp = now
            )
            val notifId = notificationDao.insertNotification(notif).toInt()
            NotificationHelper.showSystemNotification(
                context = appContext,
                notificationId = notifId,
                title = notif.title,
                message = notif.message,
                type = "STATUS_CHANGE"
            )
        }
    }

    suspend fun reassignLead(lead: LeadEntity, newAgent: SalesAgentEntity, reason: String) {
        val now = System.currentTimeMillis()
        val oldAgentName = lead.assignedAgentName
        val updatedLead = lead.copy(
            assignedAgentId = newAgent.id,
            assignedAgentName = newAgent.name,
            updatedAt = now
        )
        leadDao.updateLead(updatedLead)

        feedbackDao.insertFeedback(
            FeedbackLogEntity(
                leadId = lead.id,
                agentId = newAgent.id,
                agentName = newAgent.name,
                timestamp = now,
                disposition = "Manual Reassignment",
                notes = "Reassigned from $oldAgentName to ${newAgent.name}. Reason: $reason",
                previousStatus = lead.status,
                newStatus = lead.status
            )
        )

        val notif = AppNotificationEntity(
            targetAgentId = newAgent.id,
            leadId = lead.id,
            title = "🔄 Lead Reassigned to You",
            message = "Lead ${lead.name} has been reassigned to you. Reason: $reason",
            type = "NEW_LEAD",
            timestamp = now
        )
        notificationDao.insertNotification(notif)
    }

    suspend fun checkAndTriggerOverdueReminders() {
        val now = System.currentTimeMillis()
        val dueLeads = leadDao.getDueFollowUps(now)

        for (lead in dueLeads) {
            val notif = AppNotificationEntity(
                targetAgentId = lead.assignedAgentId,
                leadId = lead.id,
                title = "⏰ Follow-up Overdue!",
                message = "Reminder: Follow-up with ${lead.name} (${lead.phoneNumber}) is pending! Note: ${lead.nextFollowUpNote.ifBlank { "Pending response" }}",
                type = "FOLLOW_UP_REMINDER",
                timestamp = now
            )
            val notifId = notificationDao.insertNotification(notif).toInt()
            NotificationHelper.showSystemNotification(
                context = appContext,
                notificationId = notifId,
                title = notif.title,
                message = notif.message,
                type = "FOLLOW_UP_REMINDER"
            )
        }
    }

    suspend fun updateAgentStatus(agent: SalesAgentEntity, isActive: Boolean) {
        salesAgentDao.updateAgent(agent.copy(isActive = isActive))
    }

    suspend fun addAgent(agent: SalesAgentEntity) {
        salesAgentDao.insertAgent(agent)
    }

    suspend fun getAgentById(id: Long): SalesAgentEntity? {
        return salesAgentDao.getAgentById(id)
    }

    data class OtpSession(
        val otp: String,
        val timestamp: Long = System.currentTimeMillis()
    )

    private val otpCache = java.util.concurrent.ConcurrentHashMap<String, OtpSession>()

    fun normalizePhone(phone: String): String {
        return phone.replace(Regex("[^0-9+]"), "").trim()
    }

    suspend fun sendLoginOtp(phoneNumber: String): Result<String> {
        val cleanPhone = normalizePhone(phoneNumber)
        if (cleanPhone.length < 8) {
            return Result.failure(IllegalArgumentException("Please enter a valid mobile phone number (min 8 digits)."))
        }

        val agent = salesAgentDao.findAgentByPhone(cleanPhone)
            ?: return Result.failure(IllegalArgumentException("No sales person account found for phone $cleanPhone. Please register your account."))

        val otp = String.format(Locale.US, "%06d", (100000..999999).random())
        otpCache[cleanPhone] = OtpSession(otp = otp)

        val notifId = (System.currentTimeMillis() % 100000).toInt()
        NotificationHelper.showSystemNotification(
            context = appContext,
            notificationId = notifId,
            title = "📲 LeadPulse Login OTP",
            message = "Your login verification OTP is $otp. Enter this code to access your sales portal.",
            type = "AUTH_OTP"
        )

        return Result.success(otp)
    }

    suspend fun verifyLoginOtp(phoneNumber: String, enteredOtp: String): Result<SalesAgentEntity> {
        val cleanPhone = normalizePhone(phoneNumber)
        val cleanOtp = enteredOtp.trim()

        if (cleanPhone.isBlank()) {
            return Result.failure(IllegalArgumentException("Phone number is required."))
        }
        if (cleanOtp.isBlank()) {
            return Result.failure(IllegalArgumentException("Please enter the 6-digit OTP."))
        }

        val cached = otpCache[cleanPhone]
        val isValid = cleanOtp == "123456" || (cached != null && cached.otp == cleanOtp && System.currentTimeMillis() - cached.timestamp < 10 * 60 * 1000L)

        if (!isValid) {
            return Result.failure(IllegalArgumentException("Incorrect or expired OTP. Please enter the valid 6-digit OTP or test with 123456."))
        }

        val agent = salesAgentDao.findAgentByPhone(cleanPhone)
            ?: return Result.failure(IllegalArgumentException("Sales account not found for phone $cleanPhone."))

        otpCache.remove(cleanPhone)
        return Result.success(agent)
    }

    suspend fun sendRegistrationOtp(phoneNumber: String): Result<String> {
        val cleanPhone = normalizePhone(phoneNumber)
        if (cleanPhone.length < 8) {
            return Result.failure(IllegalArgumentException("Please enter a valid mobile phone number (min 8 digits)."))
        }

        val existing = salesAgentDao.findAgentByPhone(cleanPhone)
        if (existing != null) {
            return Result.failure(IllegalStateException("An account with phone $cleanPhone already exists. Please use 'Sign In via OTP'."))
        }

        val otp = String.format(Locale.US, "%06d", (100000..999999).random())
        otpCache[cleanPhone] = OtpSession(otp = otp)

        val notifId = (System.currentTimeMillis() % 100000).toInt()
        NotificationHelper.showSystemNotification(
            context = appContext,
            notificationId = notifId,
            title = "📲 LeadPulse Registration OTP",
            message = "Your verification OTP for new sales registration is $otp.",
            type = "AUTH_OTP"
        )

        return Result.success(otp)
    }

    suspend fun verifyAndRegisterSalesPerson(
        name: String,
        email: String,
        phone: String,
        department: String = "Sales",
        enteredOtp: String
    ): Result<SalesAgentEntity> {
        val cleanName = name.trim()
        val cleanEmail = email.trim().lowercase()
        val cleanPhone = normalizePhone(phone)
        val cleanOtp = enteredOtp.trim()

        if (cleanName.isBlank()) {
            return Result.failure(IllegalArgumentException("Please enter your full name."))
        }
        if (cleanPhone.length < 8) {
            return Result.failure(IllegalArgumentException("Please enter a valid phone number."))
        }
        if (cleanEmail.isNotBlank() && !cleanEmail.contains("@")) {
            return Result.failure(IllegalArgumentException("Please enter a valid email address."))
        }
        if (cleanOtp.isBlank()) {
            return Result.failure(IllegalArgumentException("Please enter the verification OTP."))
        }

        val cached = otpCache[cleanPhone]
        val isValid = cleanOtp == "123456" || (cached != null && cached.otp == cleanOtp && System.currentTimeMillis() - cached.timestamp < 10 * 60 * 1000L)

        if (!isValid) {
            return Result.failure(IllegalArgumentException("Invalid or expired OTP. Please verify the code or use 123456."))
        }

        val existing = salesAgentDao.findAgentByPhone(cleanPhone)
        if (existing != null) {
            return Result.failure(IllegalStateException("Phone number already registered. Please sign in."))
        }

        val avatarColors = listOf(
            0xFF2563EB, 0xFF10B981, 0xFFF59E0B, 0xFF8B5CF6, 0xFFEC4899, 0xFF06B6D4, 0xFF059669
        )
        val colorHex = avatarColors[Math.abs(cleanName.hashCode()) % avatarColors.size]

        val newAgent = SalesAgentEntity(
            name = cleanName,
            email = cleanEmail,
            phoneNumber = cleanPhone,
            role = "AGENT",
            password = "OTP_VERIFIED",
            department = department.trim().ifBlank { "Sales" },
            isActive = true,
            leadsAssignedCount = 0,
            leadsConvertedCount = 0,
            lastAssignedAt = 0L,
            registeredAt = System.currentTimeMillis(),
            avatarColorHex = colorHex
        )

        val insertedId = salesAgentDao.insertAgent(newAgent)
        val registeredAgent = newAgent.copy(id = insertedId)

        val welcomeNotif = AppNotificationEntity(
            targetAgentId = insertedId,
            leadId = null,
            title = "🎉 Sales Account Verified & Active!",
            message = "Welcome to LeadPulse CRM, ${newAgent.name}! Inbound WhatsApp & Meta leads will now be routed to you.",
            type = "SYSTEM_WELCOME",
            timestamp = System.currentTimeMillis()
        )
        notificationDao.insertNotification(welcomeNotif)

        otpCache.remove(cleanPhone)
        return Result.success(registeredAgent)
    }

    suspend fun registerSalesPerson(
        name: String,
        email: String,
        phone: String,
        password: String,
        department: String = "Sales"
    ): Result<SalesAgentEntity> {
        val cleanName = name.trim()
        val cleanEmail = email.trim().lowercase()
        val cleanPhone = phone.trim()
        val cleanPass = password.trim()

        if (cleanName.isBlank()) {
            return Result.failure(IllegalArgumentException("Please enter your full name"))
        }
        if (cleanPhone.length < 8) {
            return Result.failure(IllegalArgumentException("Please enter a valid phone number (at least 8 digits)"))
        }
        if (cleanEmail.isNotBlank() && !cleanEmail.contains("@")) {
            return Result.failure(IllegalArgumentException("Please enter a valid email address"))
        }
        if (cleanPass.length < 4) {
            return Result.failure(IllegalArgumentException("Password must be at least 4 characters"))
        }

        // Check for duplicate email
        if (cleanEmail.isNotBlank()) {
            val existingByEmail = salesAgentDao.findAgentByEmail(cleanEmail)
            if (existingByEmail != null) {
                return Result.failure(IllegalStateException("An account with email '$cleanEmail' is already registered. Please login."))
            }
        }
        // Check for duplicate phone
        val existingByPhone = salesAgentDao.findAgentByPhone(cleanPhone)
        if (existingByPhone != null) {
            return Result.failure(IllegalStateException("An account with phone '$cleanPhone' is already registered. Please login."))
        }

        val avatarColors = listOf(
            0xFF2563EB, 0xFF10B981, 0xFFF59E0B, 0xFF8B5CF6, 0xFFEC4899, 0xFF06B6D4, 0xFF059669
        )
        val colorHex = avatarColors[Math.abs(cleanName.hashCode()) % avatarColors.size]

        val newAgent = SalesAgentEntity(
            name = cleanName,
            email = cleanEmail,
            phoneNumber = cleanPhone,
            role = "AGENT",
            password = cleanPass,
            department = department.trim().ifBlank { "Sales" },
            isActive = true,
            leadsAssignedCount = 0,
            leadsConvertedCount = 0,
            lastAssignedAt = 0L,
            registeredAt = System.currentTimeMillis(),
            avatarColorHex = colorHex
        )

        val insertedId = salesAgentDao.insertAgent(newAgent)
        val registeredAgent = newAgent.copy(id = insertedId)

        val welcomeNotif = AppNotificationEntity(
            targetAgentId = insertedId,
            leadId = null,
            title = "🎉 Welcome to LeadPulse CRM!",
            message = "Your sales account has been activated. New incoming leads will now be routed to you.",
            type = "SYSTEM_WELCOME",
            timestamp = System.currentTimeMillis()
        )
        notificationDao.insertNotification(welcomeNotif)

        return Result.success(registeredAgent)
    }

    suspend fun loginSalesPerson(
        identifier: String,
        password: String
    ): Result<SalesAgentEntity> {
        val cleanId = identifier.trim()
        val cleanPass = password.trim()

        if (cleanId.isBlank()) {
            return Result.failure(IllegalArgumentException("Please enter your Phone Number or Email"))
        }
        if (cleanPass.isBlank()) {
            return Result.failure(IllegalArgumentException("Please enter your password"))
        }

        val agent = salesAgentDao.findAgentByIdentifier(cleanId)
            ?: return Result.failure(IllegalArgumentException("No sales account found for '$cleanId'. Please register a new account."))

        // Password matching (allow "1234" master pin for quick recovery or exact match)
        if (agent.password.isNotBlank() && agent.password != cleanPass && cleanPass != "1234") {
            return Result.failure(IllegalArgumentException("Incorrect password for ${agent.name}. Please try again."))
        }

        return Result.success(agent)
    }

    suspend fun markAllNotificationsAsRead() {
        notificationDao.markAllAsRead()
    }

    suspend fun markNotificationAsRead(id: Long) {
        notificationDao.markAsRead(id)
    }

    suspend fun deleteLead(lead: LeadEntity) {
        leadDao.deleteLead(lead)
    }

    /**
     * Ingest batch leads from Excel or CSV sheet.
     * Round-robin distributes new leads to active sales reps,
     * and keeps duplicate phone number leads with the original agent.
     */
    suspend fun bulkIngestLeads(leadsList: List<RawLeadInput>): BulkImportResult {
        val now = System.currentTimeMillis()
        var newCount = 0
        var dupCount = 0
        val agentDistribution = mutableMapOf<String, Int>()

        for ((index, item) in leadsList.withIndex()) {
            val cleanPhone = item.phone.replace(Regex("[^0-9+]"), "").trim()
            if (cleanPhone.isBlank()) continue

            val existingLead = leadDao.findLeadByPhoneNumber(cleanPhone)
            val itemTime = now + (index * 1000L)

            if (existingLead != null) {
                // Duplicate handling: retained with original agent
                dupCount++
                val updatedDuplicate = existingLead.copy(
                    name = if (item.name.isNotBlank()) item.name else existingLead.name,
                    email = if (item.email.isNotBlank()) item.email else existingLead.email,
                    initialMessage = "${existingLead.initialMessage}\n[Excel Batch Import]: ${item.notes.ifBlank { "Re-inquiry via Excel sheet" }}".trim(),
                    isDuplicate = true,
                    duplicateHitCount = existingLead.duplicateHitCount + 1,
                    updatedAt = itemTime,
                    status = if (existingLead.status in listOf("LOST", "JUNK")) "FOLLOW_UP" else existingLead.status
                )
                leadDao.updateLead(updatedDuplicate)

                feedbackDao.insertFeedback(
                    FeedbackLogEntity(
                        leadId = updatedDuplicate.id,
                        agentId = updatedDuplicate.assignedAgentId,
                        agentName = updatedDuplicate.assignedAgentName,
                        timestamp = itemTime,
                        disposition = "Duplicate Excel Inquiry",
                        notes = "Lead re-entered via Excel sheet (${item.campaign}). Retained with original agent ${updatedDuplicate.assignedAgentName}.",
                        previousStatus = existingLead.status,
                        newStatus = updatedDuplicate.status
                    )
                )
            } else {
                // New Lead: 1. Check Meta Campaigns distribution configuration first!
                val activeMetaCampaigns = metaCampaignDao.getActiveCampaigns()
                val cleanCampaign = item.campaign.trim().lowercase()
                val matchedMeta = activeMetaCampaigns.firstOrNull { mc ->
                    val cName = mc.campaignName.trim().lowercase()
                    cName.isNotBlank() && (cleanCampaign.contains(cName) || cName.contains(cleanCampaign))
                }

                val assignedAgent: SalesAgentEntity = if (matchedMeta != null) {
                    metaCampaignDao.incrementLeadsCount(matchedMeta.id, itemTime)
                    if (matchedMeta.distributionMode == "SINGLE_AGENT" && matchedMeta.assignedAgentId > 0L) {
                        val singleRep = salesAgentDao.getAgentById(matchedMeta.assignedAgentId)
                        if (singleRep != null && singleRep.isActive) {
                            singleRep
                        } else {
                            val activeAgents = salesAgentDao.getActiveAgentsForRoundRobin()
                            activeAgents.firstOrNull() ?: fallbackAgent()
                        }
                    } else {
                        // Round-Robin mode
                        val activeAgents = salesAgentDao.getActiveAgentsForRoundRobin()
                        activeAgents.firstOrNull() ?: fallbackAgent()
                    }
                } else {
                    // 2. Fallback to Custom Campaign Routing Rules
                    val activeRules = campaignRoutingDao.getActiveRules()
                    val matchedRule = activeRules.firstOrNull { rule ->
                        val pattern = rule.campaignPattern.trim().lowercase()
                        pattern.isNotBlank() && (cleanCampaign.contains(pattern) || pattern.contains(cleanCampaign))
                    }

                    if (matchedRule != null) {
                        val targetAgent = salesAgentDao.getAgentById(matchedRule.assignedAgentId)
                        if (targetAgent != null && targetAgent.isActive) {
                            campaignRoutingDao.incrementRoutedCount(matchedRule.id)
                            targetAgent
                        } else {
                            val activeAgents = salesAgentDao.getActiveAgentsForRoundRobin()
                            activeAgents.firstOrNull() ?: fallbackAgent()
                        }
                    } else {
                        // 3. Standard Round-Robin Distribution
                        val activeAgents = salesAgentDao.getActiveAgentsForRoundRobin()
                        activeAgents.firstOrNull() ?: fallbackAgent()
                    }
                }

                // Increment assignment & update timestamp immediately so next loop picks next agent
                salesAgentDao.recordLeadAssignment(assignedAgent.id, itemTime)

                val newLead = LeadEntity(
                    name = item.name.ifBlank { "Excel Lead #${index + 1}" },
                    phoneNumber = cleanPhone,
                    email = item.email,
                    source = item.source.ifBlank { "Excel Import" },
                    campaignName = item.campaign.ifBlank { "Excel Batch Campaign" },
                    initialMessage = item.notes.ifBlank { "Imported from Excel spreadsheet" },
                    assignedAgentId = assignedAgent.id,
                    assignedAgentName = assignedAgent.name,
                    status = "NEW",
                    priority = item.priority.ifBlank { "WARM" },
                    budget = "Standard",
                    dealValue = 0.0,
                    createdAt = itemTime,
                    updatedAt = itemTime,
                    nextFollowUpDate = itemTime + 1800_000,
                    nextFollowUpNote = "First touch follow-up scheduled (Excel)",
                    isDuplicate = false,
                    duplicateHitCount = 1
                )
                val leadId = leadDao.insertLead(newLead)

                newCount++
                agentDistribution[assignedAgent.name] = (agentDistribution[assignedAgent.name] ?: 0) + 1

                val notif = AppNotificationEntity(
                    targetAgentId = assignedAgent.id,
                    leadId = leadId,
                    title = "📥 New Excel Lead Distributed",
                    message = "${newLead.name} ($cleanPhone) from Excel batch assigned to you.",
                    type = "NEW_LEAD",
                    timestamp = itemTime
                )
                notificationDao.insertNotification(notif)
            }
        }

        // Summary notification for Admin
        if (newCount > 0 || dupCount > 0) {
            val distText = if (agentDistribution.isNotEmpty()) {
                agentDistribution.entries.joinToString(", ") { "${it.key}: ${it.value}" }
            } else "0 distributed"

            val adminNotif = AppNotificationEntity(
                targetAgentId = null,
                leadId = null,
                title = "📊 Excel Bulk Leads Distributed",
                message = "Processed ${newCount + dupCount} leads: $newCount new distributed ($distText), $dupCount duplicate inquiries kept with existing reps.",
                type = "BULK_IMPORT",
                timestamp = now
            )
            val notifId = notificationDao.insertNotification(adminNotif).toInt()
            NotificationHelper.showSystemNotification(
                context = appContext,
                notificationId = notifId,
                title = adminNotif.title,
                message = adminNotif.message,
                type = "BULK_IMPORT"
            )
        }

        return BulkImportResult(
            totalProcessed = newCount + dupCount,
            newLeadsDistributed = newCount,
            duplicatesRetained = dupCount,
            distributionSummary = agentDistribution
        )
    }

    /**
     * Formats leads into RFC 4180 CSV spreadsheet format
     * ready for Microsoft Excel or Google Sheets.
     */
    fun exportLeadsToCsv(leads: List<LeadEntity>): String {
        val sb = StringBuilder()
        sb.append("Lead ID,Customer Name,Phone Number,Email,Source,Campaign,Assigned Agent,Status,Priority,Created Date,Next Follow-up,Inquiry Notes\n")
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        for (lead in leads) {
            val createdStr = dateFormat.format(Date(lead.createdAt))
            val followUpStr = lead.nextFollowUpDate?.let { dateFormat.format(Date(it)) } ?: "None"
            val cleanMsg = lead.initialMessage.replace("\"", "\"\"").replace("\n", " ")
            val cleanName = lead.name.replace("\"", "\"\"")
            val cleanCampaign = lead.campaignName.replace("\"", "\"\"")
            val cleanAgent = lead.assignedAgentName.replace("\"", "\"\"")
            val cleanEmail = lead.email.replace("\"", "\"\"")
            val cleanSource = lead.source.replace("\"", "\"\"")
            sb.append("${lead.id},\"$cleanName\",\"${lead.phoneNumber}\",\"$cleanEmail\",\"$cleanSource\",\"$cleanCampaign\",\"$cleanAgent\",${lead.status},${lead.priority},\"$createdStr\",\"$followUpStr\",\"$cleanMsg\"\n")
        }
        return sb.toString()
    }

    /**
     * Updates customer inquiry message directly for a lead.
     */
    suspend fun updateLeadMessage(leadId: Long, newMessage: String) {
        leadDao.updateLeadMessage(leadId, newMessage.trim(), System.currentTimeMillis())
    }

    /**
     * Sanitizes any leads where the initialMessage accidentally recorded
     * only phone numbers or timestamp inquiry strings.
     */
    suspend fun sanitizeCorruptedMessages() {
        val leads = leadDao.getAllLeadsFlow().first()
        leads.forEach { lead ->
            val msg = lead.initialMessage.trim()
            val cleanPhone = lead.phoneNumber.replace(Regex("[^0-9+]"), "").trim()

            val isPhoneCorrupted = CustomerMessageHelper.isPhoneOnlyString(msg, cleanPhone) ||
                !msg.any { it.isLetter() } ||
                msg.contains("New Inquiry", ignoreCase = true)

            if (isPhoneCorrupted) {
                val restoredMessage = CustomerMessageHelper.resolveCustomerMessage(msg, lead.campaignName, cleanPhone)
                leadDao.updateLead(lead.copy(initialMessage = restoredMessage))
            }
        }
    }

    /**
     * Fallback agent when no active agents are in the round-robin pool.
     */
    private suspend fun fallbackAgent(): SalesAgentEntity {
        val all = salesAgentDao.getAllAgentsFlow().first()
        return all.firstOrNull { it.role == "AGENT" && it.isActive }
            ?: all.firstOrNull { it.role == "AGENT" }
            ?: all.firstOrNull()
            ?: SalesAgentEntity(
                id = 1L,
                name = "Default Agent",
                email = "agent@leadpulse.com",
                phoneNumber = "+910000000000"
            )
    }

    /**
     * Campaign Routing Rule Management
     */
    suspend fun addOrUpdateCampaignRule(
        campaignPattern: String,
        agentId: Long,
        agentName: String,
        description: String = ""
    ): Long {
        val rule = CampaignRoutingRuleEntity(
            campaignPattern = campaignPattern.trim(),
            assignedAgentId = agentId,
            assignedAgentName = agentName,
            isActive = true,
            ruleDescription = description.trim()
        )
        return campaignRoutingDao.insertRule(rule)
    }

    suspend fun setCampaignRuleActive(ruleId: Long, isActive: Boolean) {
        campaignRoutingDao.setRuleActive(ruleId, isActive)
    }

    suspend fun deleteCampaignRule(ruleId: Long) {
        campaignRoutingDao.deleteRuleById(ruleId)
    }

    suspend fun getAllLeadsList(): List<LeadEntity> = leadDao.getAllLeadsList()
    suspend fun getAllFeedbacksList(): List<FeedbackLogEntity> = feedbackDao.getAllFeedbackLogsList()
    suspend fun getAllAgentsList(): List<SalesAgentEntity> = salesAgentDao.getAllAgentsFlow().first()
    suspend fun insertLeads(leads: List<LeadEntity>) = leadDao.insertLeads(leads)
    suspend fun insertFeedbacks(feedbacks: List<FeedbackLogEntity>) = feedbackDao.insertFeedbacks(feedbacks)
}
