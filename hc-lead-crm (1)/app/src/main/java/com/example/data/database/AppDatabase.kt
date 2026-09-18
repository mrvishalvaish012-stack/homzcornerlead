package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        LeadEntity::class,
        SalesAgentEntity::class,
        FeedbackLogEntity::class,
        AppNotificationEntity::class,
        CampaignRoutingRuleEntity::class,
        MetaCampaignEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun leadDao(): LeadDao
    abstract fun salesAgentDao(): SalesAgentDao
    abstract fun feedbackDao(): FeedbackDao
    abstract fun notificationDao(): NotificationDao
    abstract fun campaignRoutingDao(): CampaignRoutingDao
    abstract fun metaCampaignDao(): MetaCampaignDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "leadpulse_secure_crm.db"
                )
                    .fallbackToDestructiveMigration()
                    .addCallback(DatabaseCallback(scope))
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback(
            private val scope: CoroutineScope
        ) : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                INSTANCE?.let { database ->
                    scope.launch(Dispatchers.IO) {
                        populateInitialData(database)
                    }
                }
            }

            private suspend fun populateInitialData(db: AppDatabase) {
                val agentDao = db.salesAgentDao()
                val leadDao = db.leadDao()
                val notifDao = db.notificationDao()
                val feedbackDao = db.feedbackDao()

                val now = System.currentTimeMillis()

                // Initial Agents
                val agents = listOf(
                    SalesAgentEntity(
                        id = 1L,
                        name = "Rahul Sharma",
                        email = "rahul.sharma@leadpulse.com",
                        phoneNumber = "+91 98765 43210",
                        role = "AGENT",
                        isActive = true,
                        leadsAssignedCount = 3,
                        leadsConvertedCount = 1,
                        lastAssignedAt = now - 3600_000 * 2,
                        avatarColorHex = 0xFF2563EB
                    ),
                    SalesAgentEntity(
                        id = 2L,
                        name = "Priya Patel",
                        email = "priya.patel@leadpulse.com",
                        phoneNumber = "+91 98123 45678",
                        role = "AGENT",
                        isActive = true,
                        leadsAssignedCount = 2,
                        leadsConvertedCount = 1,
                        lastAssignedAt = now - 3600_000 * 4,
                        avatarColorHex = 0xFF10B981
                    ),
                    SalesAgentEntity(
                        id = 3L,
                        name = "Amit Verma",
                        email = "amit.verma@leadpulse.com",
                        phoneNumber = "+91 97654 32109",
                        role = "AGENT",
                        isActive = true,
                        leadsAssignedCount = 2,
                        leadsConvertedCount = 0,
                        lastAssignedAt = now - 3600_000 * 6,
                        avatarColorHex = 0xFFF59E0B
                    ),
                    SalesAgentEntity(
                        id = 4L,
                        name = "Neha Singh",
                        email = "neha.singh@leadpulse.com",
                        phoneNumber = "+91 99887 76655",
                        role = "AGENT",
                        isActive = false, // Currently paused / on leave
                        leadsAssignedCount = 1,
                        leadsConvertedCount = 0,
                        lastAssignedAt = now - 3600_000 * 24,
                        avatarColorHex = 0xFF8B5CF6
                    ),
                    SalesAgentEntity(
                        id = 5L,
                        name = "Vikram Malhotra (Head)",
                        email = "vikram.manager@leadpulse.com",
                        phoneNumber = "+91 98999 11223",
                        role = "MANAGER",
                        isActive = true,
                        leadsAssignedCount = 0,
                        leadsConvertedCount = 0,
                        lastAssignedAt = 0L,
                        avatarColorHex = 0xFF0F172A
                    )
                )
                agentDao.insertAgents(agents)

                // Seed initial realistic Meta Ads & WhatsApp leads
                val leads = listOf(
                    LeadEntity(
                        id = 1L,
                        name = "Rohan Mehra",
                        phoneNumber = "+919820011223",
                        email = "rohan.mehra@gmail.com",
                        source = "Meta Ads - WhatsApp Click",
                        campaignName = "Real Estate Luxury 3BHK",
                        initialMessage = "Hi, saw your ad on Instagram. Need quotation and site visit for 3BHK.",
                        assignedAgentId = 1L,
                        assignedAgentName = "Rahul Sharma",
                        status = "FOLLOW_UP",
                        priority = "HOT",
                        budget = "₹85,00,000",
                        createdAt = now - 3600_000 * 5,
                        updatedAt = now - 3600_000 * 2,
                        nextFollowUpDate = now - 1800_000, // Overdue!
                        nextFollowUpNote = "Call regarding floor plan options and schedule site visit",
                        isDuplicate = false,
                        duplicateHitCount = 1,
                        dealValue = 85000.0
                    ),
                    LeadEntity(
                        id = 2L,
                        name = "Sunita Rao",
                        phoneNumber = "+919711223344",
                        email = "sunita.rao@outlook.com",
                        source = "Meta Ads - Lead Form",
                        campaignName = "SaaS ERP Software Demo",
                        initialMessage = "Interested in pricing plan for 25-person team.",
                        assignedAgentId = 2L,
                        assignedAgentName = "Priya Patel",
                        status = "WON",
                        priority = "HOT",
                        budget = "₹45,000 / month",
                        createdAt = now - 3600_000 * 48,
                        updatedAt = now - 3600_000 * 3,
                        nextFollowUpDate = null,
                        nextFollowUpNote = "Deal Closed! Onboarding scheduled for Monday.",
                        isDuplicate = false,
                        duplicateHitCount = 1,
                        dealValue = 45000.0
                    ),
                    LeadEntity(
                        id = 3L,
                        name = "Deepak Joshi",
                        phoneNumber = "+919933445566",
                        email = "deepak.j@techcorp.in",
                        source = "WhatsApp Direct Inquiry",
                        campaignName = "Digital Marketing Growth Pack",
                        initialMessage = "Hello, want to know more about Meta Ads management service.",
                        assignedAgentId = 3L,
                        assignedAgentName = "Amit Verma",
                        status = "CONTACTED",
                        priority = "WARM",
                        budget = "₹30,000",
                        createdAt = now - 3600_000 * 8,
                        updatedAt = now - 3600_000 * 1,
                        nextFollowUpDate = now + 3600_000 * 4, // Due later today
                        nextFollowUpNote = "Send portfolio case studies and follow up on WhatsApp",
                        isDuplicate = false,
                        duplicateHitCount = 1,
                        dealValue = 30000.0
                    ),
                    LeadEntity(
                        id = 4L,
                        name = "Ananya Sen",
                        phoneNumber = "+919877001122",
                        email = "ananya.sen@gmail.com",
                        source = "Meta Ads - Instagram Story",
                        campaignName = "Solar Rooftop Subsidy Offer",
                        initialMessage = "Looking for 5kW solar installation for residential villa.",
                        assignedAgentId = 1L,
                        assignedAgentName = "Rahul Sharma",
                        status = "NEW",
                        priority = "HOT",
                        budget = "₹2,50,000",
                        createdAt = now - 1800_000,
                        updatedAt = now - 1800_000,
                        nextFollowUpDate = now + 1800_000, // Due in 30 mins
                        nextFollowUpNote = "First touch call within 30 minutes rule",
                        isDuplicate = false,
                        duplicateHitCount = 1,
                        dealValue = 250000.0
                    ),
                    LeadEntity(
                        id = 5L,
                        name = "Rohan Mehra", // Repeat lead example to showcase duplicate persistence!
                        phoneNumber = "+919820011223",
                        email = "rohan.mehra@gmail.com",
                        source = "WhatsApp Direct Inquiry",
                        campaignName = "Real Estate Luxury 3BHK",
                        initialMessage = "Hey Rahul, also wanted to know if bank loan pre-approval is available?",
                        assignedAgentId = 1L, // Kept to same agent Rahul!
                        assignedAgentName = "Rahul Sharma",
                        status = "FOLLOW_UP",
                        priority = "HOT",
                        budget = "₹85,00,000",
                        createdAt = now - 3600_000 * 5,
                        updatedAt = now - 900_000,
                        nextFollowUpDate = now - 900_000,
                        nextFollowUpNote = "Duplicate lead re-inquiry retained with Rahul Sharma",
                        isDuplicate = true,
                        duplicateHitCount = 2,
                        dealValue = 85000.0
                    )
                )

                // Note: For id 5, since it's the duplicate of id 1, we can just insert the first 4 and update id 1 with duplicate flag
                leadDao.insertLead(leads[0].copy(isDuplicate = true, duplicateHitCount = 2))
                leadDao.insertLead(leads[1])
                leadDao.insertLead(leads[2])
                leadDao.insertLead(leads[3])

                // Add feedback logs
                feedbackDao.insertFeedback(
                    FeedbackLogEntity(
                        leadId = 1L,
                        agentId = 1L,
                        agentName = "Rahul Sharma",
                        timestamp = now - 3600_000 * 2,
                        disposition = "Call Answered - Interested",
                        notes = "Customer was positive. Discussed 3BHK tower A. Demanded site visit on Saturday.",
                        nextFollowUpDate = now - 1800_000,
                        previousStatus = "NEW",
                        newStatus = "FOLLOW_UP"
                    )
                )
                feedbackDao.insertFeedback(
                    FeedbackLogEntity(
                        leadId = 2L,
                        agentId = 2L,
                        agentName = "Priya Patel",
                        timestamp = now - 3600_000 * 3,
                        disposition = "Deal Won",
                        notes = "Annual contract signed. Payment link processed successfully.",
                        nextFollowUpDate = null,
                        previousStatus = "QUOTATION",
                        newStatus = "WON"
                    )
                )

                // Add sample notifications
                notifDao.insertNotification(
                    AppNotificationEntity(
                        targetAgentId = 1L,
                        leadId = 1L,
                        title = "⚠️ Duplicate Lead Re-inquiry",
                        message = "Rohan Mehra (+919820011223) sent a new WhatsApp inquiry. Retained under your ownership as per policy.",
                        type = "DUPLICATE_ALERT",
                        timestamp = now - 900_000,
                        isRead = false
                    )
                )
                notifDao.insertNotification(
                    AppNotificationEntity(
                        targetAgentId = 1L,
                        leadId = 4L,
                        title = "🎉 New Lead Assigned (Round-Robin)",
                        message = "Ananya Sen assigned to you via Meta Ads (Solar Rooftop Subsidy). Please connect within 30 mins.",
                        type = "NEW_LEAD",
                        timestamp = now - 1800_000,
                        isRead = false
                    )
                )
                notifDao.insertNotification(
                    AppNotificationEntity(
                        targetAgentId = 1L,
                        leadId = 1L,
                        title = "⏰ Follow-up Reminder Due",
                        message = "Scheduled follow-up for Rohan Mehra is overdue! Call or WhatsApp now.",
                        type = "FOLLOW_UP_REMINDER",
                        timestamp = now - 600_000,
                        isRead = false
                    )
                )

                // Seed initial Campaign-to-Agent Custom Routing Rules
                val routingDao = db.campaignRoutingDao()
                routingDao.insertRule(
                    CampaignRoutingRuleEntity(
                        id = 1L,
                        campaignPattern = "Luxury 3BHK",
                        assignedAgentId = 2L, // Priya Patel
                        assignedAgentName = "Priya Patel",
                        isActive = true,
                        ruleDescription = "Direct all 3BHK Real Estate Ad leads exclusively to Priya Patel",
                        leadsRoutedCount = 1
                    )
                )
                routingDao.insertRule(
                    CampaignRoutingRuleEntity(
                        id = 2L,
                        campaignPattern = "Solar Rooftop",
                        assignedAgentId = 3L, // Amit Verma
                        assignedAgentName = "Amit Verma",
                        isActive = true,
                        ruleDescription = "Direct all CleanTech / Solar Ad leads exclusively to Amit Verma",
                        leadsRoutedCount = 1
                    )
                )

                // Seed initial Meta Lead Campaigns (supporting both Single Salesperson and Round-Robin)
                val metaDao = db.metaCampaignDao()
                metaDao.insertCampaign(
                    MetaCampaignEntity(
                        id = 1L,
                        campaignName = "Luxury 3BHK Flats - Sector 62",
                        adAccountId = "act_8830192841",
                        platform = "Instagram & Facebook",
                        leadFormName = "Instant Luxury Booking Form",
                        distributionMode = "SINGLE_AGENT",
                        assignedAgentId = 2L, // Priya Patel
                        assignedAgentName = "Priya Patel",
                        isActive = true,
                        totalLeadsReceived = 14,
                        lastSyncTimestamp = now - 1800_000,
                        campaignBudgetDaily = "₹2,500/day",
                        campaignObjective = "LEAD_GENERATION"
                    )
                )
                metaDao.insertCampaign(
                    MetaCampaignEntity(
                        id = 2L,
                        campaignName = "Solar Rooftop 5KW Subsidy",
                        adAccountId = "act_8830192841",
                        platform = "Facebook Ads",
                        leadFormName = "Govt Solar Subsidy Survey Form",
                        distributionMode = "SINGLE_AGENT",
                        assignedAgentId = 3L, // Amit Verma
                        assignedAgentName = "Amit Verma",
                        isActive = true,
                        totalLeadsReceived = 9,
                        lastSyncTimestamp = now - 3600_000 * 2,
                        campaignBudgetDaily = "₹1,800/day",
                        campaignObjective = "LEAD_GENERATION"
                    )
                )
                metaDao.insertCampaign(
                    MetaCampaignEntity(
                        id = 3L,
                        campaignName = "Commercial Office & Retail Space",
                        adAccountId = "act_9918273645",
                        platform = "Instagram & Facebook",
                        leadFormName = "Retail Investors Inquiry Form",
                        distributionMode = "ROUND_ROBIN",
                        assignedAgentId = 0L,
                        assignedAgentName = "Team Round-Robin",
                        isActive = true,
                        totalLeadsReceived = 6,
                        lastSyncTimestamp = now - 3600_000 * 4,
                        campaignBudgetDaily = "₹3,000/day",
                        campaignObjective = "LEAD_GENERATION"
                    )
                )
                metaDao.insertCampaign(
                    MetaCampaignEntity(
                        id = 4L,
                        campaignName = "Digital Marketing & CRM Demo",
                        adAccountId = "act_9918273645",
                        platform = "Instagram Ads",
                        leadFormName = "Free 14-Day Growth Trial",
                        distributionMode = "ROUND_ROBIN",
                        assignedAgentId = 0L,
                        assignedAgentName = "Team Round-Robin",
                        isActive = true,
                        totalLeadsReceived = 4,
                        lastSyncTimestamp = now - 3600_000 * 8,
                        campaignBudgetDaily = "₹1,200/day",
                        campaignObjective = "LEAD_GENERATION"
                    )
                )
            }
        }
    }
}
