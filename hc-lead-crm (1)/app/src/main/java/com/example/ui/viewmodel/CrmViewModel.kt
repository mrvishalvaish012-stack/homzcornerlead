package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.auth.SessionManager
import com.example.data.database.AppDatabase
import com.example.data.model.AppNotificationEntity
import com.example.data.model.CampaignRoutingRuleEntity
import com.example.data.model.FeedbackLogEntity
import com.example.data.model.LeadEntity
import com.example.data.model.MetaCampaignEntity
import com.example.data.model.SalesAgentEntity
import com.example.data.repository.BulkImportResult
import com.example.data.repository.LeadRepository
import com.example.data.repository.RawLeadInput
import com.example.data.service.MetaGraphApiService
import com.example.data.service.WebhookTestResult
import com.example.data.sync.CloudSyncManager
import com.example.data.sync.CloudSyncState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class UserRole {
    ADMIN,
    SALES_PERSON
}

data class UserSession(
    val isLoggedIn: Boolean = true, // Default logged in as Admin for instant usability
    val role: UserRole = UserRole.ADMIN,
    val agent: SalesAgentEntity? = null
)

data class FilterState(
    val userSession: UserSession = UserSession(),
    val adminAgentFilterId: Long? = null,
    val query: String = "",
    val status: String = "ALL",
    val priority: String = "ALL"
)

data class AnalyticsState(
    val totalLeads: Int = 0,
    val totalWon: Int = 0,
    val totalLost: Int = 0,
    val totalFollowUps: Int = 0,
    val conversionRate: Float = 0f,
    val totalPipelineValue: Double = 0.0,
    val totalWonRevenue: Double = 0.0,
    val metaAdsLeadsCount: Int = 0,
    val whatsAppLeadsCount: Int = 0,
    val duplicateLeadsCount: Int = 0
)

class CrmViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: LeadRepository
    private val cloudSyncManager: CloudSyncManager
    private val sessionManager: SessionManager = SessionManager(application)

    init {
        val db = AppDatabase.getDatabase(application, viewModelScope)
        repository = LeadRepository(
            leadDao = db.leadDao(),
            salesAgentDao = db.salesAgentDao(),
            feedbackDao = db.feedbackDao(),
            notificationDao = db.notificationDao(),
            campaignRoutingDao = db.campaignRoutingDao(),
            metaCampaignDao = db.metaCampaignDao(),
            appContext = application
        )
        cloudSyncManager = CloudSyncManager(application, repository, viewModelScope)
        viewModelScope.launch {
            repository.sanitizeCorruptedMessages()
            // Restore saved session if present
            if (sessionManager.hasInitialized()) {
                if (sessionManager.isLoggedIn()) {
                    if (sessionManager.getRole() == "SALES_PERSON") {
                        val savedId = sessionManager.getAgentId()
                        val agent = if (savedId != null) repository.getAgentById(savedId) else null
                        if (agent != null) {
                            _userSession.value = UserSession(isLoggedIn = true, role = UserRole.SALES_PERSON, agent = agent)
                            _isManagerMode.value = false
                            _currentAgent.value = agent
                        } else {
                            _userSession.value = UserSession(isLoggedIn = true, role = UserRole.ADMIN, agent = null)
                            _isManagerMode.value = true
                        }
                    } else {
                        _userSession.value = UserSession(isLoggedIn = true, role = UserRole.ADMIN, agent = null)
                        _isManagerMode.value = true
                    }
                } else {
                    _userSession.value = UserSession(isLoggedIn = false)
                    _isManagerMode.value = false
                    _currentAgent.value = null
                }
            }
        }
    }

    val cloudSyncState: StateFlow<CloudSyncState> = cloudSyncManager.syncState

    // Role-based Authentication & Session State
    private val _userSession = MutableStateFlow(UserSession(isLoggedIn = true, role = UserRole.ADMIN, agent = null))
    val userSession: StateFlow<UserSession> = _userSession.asStateFlow()

    private val _isManagerMode = MutableStateFlow(true)
    val isManagerMode: StateFlow<Boolean> = _isManagerMode.asStateFlow()

    private val _currentAgent = MutableStateFlow<SalesAgentEntity?>(null)
    val currentAgent: StateFlow<SalesAgentEntity?> = _currentAgent.asStateFlow()

    // For Admin to inspect any specific sales rep's leads (null = all reps)
    private val _adminSelectedAgentFilter = MutableStateFlow<Long?>(null)
    val adminSelectedAgentFilter: StateFlow<Long?> = _adminSelectedAgentFilter.asStateFlow()

    // Navigation & UI State
    private val _currentTab = MutableStateFlow(0) // 0: Leads, 1: Analytics, 2: Webhooks, 3: Excel Import/Export, 4: Team
    val currentTab: StateFlow<Int> = _currentTab.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _statusFilter = MutableStateFlow("ALL")
    val statusFilter: StateFlow<String> = _statusFilter.asStateFlow()

    private val _priorityFilter = MutableStateFlow("ALL")
    val priorityFilter: StateFlow<String> = _priorityFilter.asStateFlow()

    private val _selectedLeadForDetail = MutableStateFlow<LeadEntity?>(null)
    val selectedLeadForDetail: StateFlow<LeadEntity?> = _selectedLeadForDetail.asStateFlow()

    private val _showNotificationsDrawer = MutableStateFlow(false)
    val showNotificationsDrawer: StateFlow<Boolean> = _showNotificationsDrawer.asStateFlow()

    val allAgents: StateFlow<List<SalesAgentEntity>> = repository.allAgents
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allLeadsRaw: StateFlow<List<LeadEntity>> = repository.allLeads
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val campaignRoutingRules: StateFlow<List<CampaignRoutingRuleEntity>> = repository.allCampaignRules
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allMetaCampaigns: StateFlow<List<MetaCampaignEntity>> = repository.allMetaCampaigns
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Internal combined filter state
    private val filterState: StateFlow<FilterState> = combine(
        combine(_userSession, _adminSelectedAgentFilter, _searchQuery) { session, adminFilter, q ->
            Triple(session, adminFilter, q)
        },
        _statusFilter,
        _priorityFilter
    ) { triple, stat, prio ->
        FilterState(
            userSession = triple.first,
            adminAgentFilterId = triple.second,
            query = triple.third,
            status = stat,
            priority = prio
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FilterState())

    // Combined filtered leads depending on role & filters
    val filteredLeads: StateFlow<List<LeadEntity>> = allLeadsRaw.combine(filterState) { leads, filter ->
        leads.filter { lead ->
            // RBAC Filter:
            // 1. If Sales Person: STRICTLY ONLY see their own assigned leads!
            // 2. If Admin: Can see all leads, OR filter by specific sales person!
            val roleMatch = if (!filter.userSession.isLoggedIn) {
                false
            } else if (filter.userSession.role == UserRole.SALES_PERSON) {
                val myAgentId = filter.userSession.agent?.id ?: -1L
                lead.assignedAgentId == myAgentId
            } else {
                // ADMIN mode: check if filtering by specific agent
                if (filter.adminAgentFilterId != null) {
                    lead.assignedAgentId == filter.adminAgentFilterId
                } else {
                    true
                }
            }

            // Search query filter
            val queryMatch = filter.query.isBlank() ||
                lead.name.contains(filter.query, ignoreCase = true) ||
                lead.phoneNumber.contains(filter.query, ignoreCase = true) ||
                lead.campaignName.contains(filter.query, ignoreCase = true) ||
                lead.assignedAgentName.contains(filter.query, ignoreCase = true)

            // Status filter
            val statusMatch = filter.status == "ALL" || lead.status == filter.status

            // Priority filter
            val priorityMatch = filter.priority == "ALL" || lead.priority == filter.priority

            roleMatch && queryMatch && statusMatch && priorityMatch
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Notifications filtered by role
    val notifications: StateFlow<List<AppNotificationEntity>> = combine(
        repository.allNotifications,
        _isManagerMode,
        _currentAgent
    ) { notifs, isManager, agent ->
        if (isManager) {
            notifs
        } else if (agent != null) {
            notifs.filter { it.targetAgentId == null || it.targetAgentId == agent.id }
        } else {
            emptyList()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Computed Analytics State for Manager & Agents
    val analyticsState: StateFlow<AnalyticsState> = allLeadsRaw.combine(_currentAgent) { leads, agent ->
        val relevantLeads = if (_isManagerMode.value) leads else leads.filter { it.assignedAgentId == agent?.id }
        val total = relevantLeads.size
        val won = relevantLeads.count { it.status == "WON" }
        val lost = relevantLeads.count { it.status == "LOST" }
        val followUps = relevantLeads.count { it.status in listOf("FOLLOW_UP", "CONTACTED", "QUOTATION") }
        val convRate = if (total > 0) (won.toFloat() / total.toFloat()) * 100f else 0f
        val pipelineVal = relevantLeads.filter { it.status != "LOST" }.sumOf { it.dealValue }
        val wonRev = relevantLeads.filter { it.status == "WON" }.sumOf { it.dealValue }
        val metaCount = relevantLeads.count { it.source.contains("Meta", ignoreCase = true) }
        val waCount = relevantLeads.count { it.source.contains("WhatsApp", ignoreCase = true) }
        val dupCount = relevantLeads.count { it.isDuplicate }

        AnalyticsState(
            totalLeads = total,
            totalWon = won,
            totalLost = lost,
            totalFollowUps = followUps,
            conversionRate = convRate,
            totalPipelineValue = pipelineVal,
            totalWonRevenue = wonRev,
            metaAdsLeadsCount = metaCount,
            whatsAppLeadsCount = waCount,
            duplicateLeadsCount = dupCount
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AnalyticsState())

    fun selectRole(isManager: Boolean, agent: SalesAgentEntity? = null) {
        if (isManager) {
            loginAsAdmin()
        } else if (agent != null) {
            loginAsSalesPerson(agent)
        }
    }

    fun loginAsAdmin() {
        sessionManager.saveSession(role = "ADMIN")
        _userSession.value = UserSession(isLoggedIn = true, role = UserRole.ADMIN, agent = null)
        _isManagerMode.value = true
        _currentAgent.value = null
        _adminSelectedAgentFilter.value = null
        _currentTab.value = 0
    }

    fun loginAsSalesPerson(agent: SalesAgentEntity) {
        sessionManager.saveSession(
            role = "SALES_PERSON",
            agentId = agent.id,
            agentName = agent.name,
            agentEmail = agent.email,
            agentPhone = agent.phoneNumber
        )
        _userSession.value = UserSession(isLoggedIn = true, role = UserRole.SALES_PERSON, agent = agent)
        _isManagerMode.value = false
        _currentAgent.value = agent
        _adminSelectedAgentFilter.value = null
        _currentTab.value = 0
    }

    fun logout() {
        sessionManager.clearSession()
        _userSession.value = UserSession(isLoggedIn = false)
        _isManagerMode.value = false
        _currentAgent.value = null
        _adminSelectedAgentFilter.value = null
        _currentTab.value = 0
    }

    fun sendLoginOtp(
        phoneNumber: String,
        onResult: (Boolean, String, String?) -> Unit
    ) {
        viewModelScope.launch {
            val result = repository.sendLoginOtp(phoneNumber)
            result.fold(
                onSuccess = { otp ->
                    onResult(true, "OTP sent successfully to $phoneNumber", otp)
                },
                onFailure = { error ->
                    onResult(false, error.localizedMessage ?: "Failed to send OTP", null)
                }
            )
        }
    }

    fun verifyLoginOtp(
        phoneNumber: String,
        enteredOtp: String,
        onResult: (Boolean, String, SalesAgentEntity?) -> Unit
    ) {
        viewModelScope.launch {
            val result = repository.verifyLoginOtp(phoneNumber, enteredOtp)
            result.fold(
                onSuccess = { agent ->
                    loginAsSalesPerson(agent)
                    onResult(true, "Welcome back, ${agent.name}!", agent)
                },
                onFailure = { error ->
                    onResult(false, error.localizedMessage ?: "Invalid OTP", null)
                }
            )
        }
    }

    fun sendRegistrationOtp(
        phoneNumber: String,
        onResult: (Boolean, String, String?) -> Unit
    ) {
        viewModelScope.launch {
            val result = repository.sendRegistrationOtp(phoneNumber)
            result.fold(
                onSuccess = { otp ->
                    onResult(true, "Verification OTP sent to $phoneNumber", otp)
                },
                onFailure = { error ->
                    onResult(false, error.localizedMessage ?: "Failed to send OTP", null)
                }
            )
        }
    }

    fun verifyAndRegisterSalesPerson(
        name: String,
        email: String,
        phone: String,
        department: String = "Sales",
        enteredOtp: String,
        onResult: (Boolean, String, SalesAgentEntity?) -> Unit
    ) {
        viewModelScope.launch {
            val result = repository.verifyAndRegisterSalesPerson(
                name = name,
                email = email,
                phone = phone,
                department = department,
                enteredOtp = enteredOtp
            )
            result.fold(
                onSuccess = { newAgent ->
                    loginAsSalesPerson(newAgent)
                    onResult(true, "Registration complete! Welcome to LeadPulse, ${newAgent.name}.", newAgent)
                },
                onFailure = { error ->
                    onResult(false, error.localizedMessage ?: "Registration verification failed", null)
                }
            )
        }
    }

    fun registerSalesPerson(
        name: String,
        email: String,
        phone: String,
        password: String,
        department: String = "Sales",
        onResult: (Boolean, String, SalesAgentEntity?) -> Unit
    ) {
        viewModelScope.launch {
            val result = repository.registerSalesPerson(
                name = name,
                email = email,
                phone = phone,
                password = password,
                department = department
            )
            result.fold(
                onSuccess = { newAgent ->
                    loginAsSalesPerson(newAgent)
                    onResult(true, "Registration successful! Welcome to LeadPulse, ${newAgent.name}.", newAgent)
                },
                onFailure = { error ->
                    onResult(false, error.localizedMessage ?: "Registration failed", null)
                }
            )
        }
    }

    fun loginSalesPersonWithCredentials(
        identifier: String,
        password: String,
        onResult: (Boolean, String, SalesAgentEntity?) -> Unit
    ) {
        viewModelScope.launch {
            val result = repository.loginSalesPerson(identifier, password)
            result.fold(
                onSuccess = { agent ->
                    loginAsSalesPerson(agent)
                    onResult(true, "Welcome back, ${agent.name}!", agent)
                },
                onFailure = { error ->
                    onResult(false, error.localizedMessage ?: "Login failed", null)
                }
            )
        }
    }

    fun loginAsAdminWithPin(
        pin: String,
        onResult: (Boolean, String) -> Unit
    ) {
        val cleanPin = pin.trim()
        if (cleanPin == "1234" || cleanPin.lowercase() == "admin" || cleanPin == "admin123") {
            loginAsAdmin()
            onResult(true, "Administrator access granted")
        } else {
            onResult(false, "Invalid Administrator PIN (Default PIN: 1234)")
        }
    }

    fun setAdminAgentFilter(agentId: Long?) {
        _adminSelectedAgentFilter.value = agentId
    }

    fun importExcelLeads(
        leads: List<RawLeadInput>,
        onResult: (BulkImportResult) -> Unit
    ) {
        viewModelScope.launch {
            val result = repository.bulkIngestLeads(leads)
            onResult(result)
        }
    }

    fun exportAllLeadsCsv(): String {
        return repository.exportLeadsToCsv(allLeadsRaw.value)
    }

    fun exportFilteredLeadsCsv(): String {
        return repository.exportLeadsToCsv(filteredLeads.value)
    }

    fun setTab(tab: Int) {
        _currentTab.value = tab
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setStatusFilter(status: String) {
        _statusFilter.value = status
    }

    fun setPriorityFilter(priority: String) {
        _priorityFilter.value = priority
    }

    fun selectLeadForDetail(lead: LeadEntity?) {
        _selectedLeadForDetail.value = lead
    }

    fun toggleNotificationsDrawer(show: Boolean) {
        _showNotificationsDrawer.value = show
    }

    fun getFeedbackForLead(leadId: Long): StateFlow<List<FeedbackLogEntity>> {
        return repository.getFeedbackForLead(leadId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    }

    fun ingestNewLead(
        name: String,
        phone: String,
        email: String,
        source: String,
        campaign: String,
        message: String,
        budget: String,
        dealValue: Double,
        priority: String,
        onComplete: (LeadEntity, Boolean) -> Unit
    ) {
        viewModelScope.launch {
            val result = repository.ingestLead(
                name = name,
                phoneNumber = phone,
                email = email,
                source = source,
                campaignName = campaign,
                initialMessage = message,
                budget = budget,
                dealValue = dealValue,
                priority = priority
            )
            onComplete(result.first, result.second)
        }
    }

    fun updateLeadMessage(leadId: Long, newMessage: String) {
        viewModelScope.launch {
            repository.updateLeadMessage(leadId, newMessage)
            // Update currently selected lead if open
            if (_selectedLeadForDetail.value?.id == leadId) {
                _selectedLeadForDetail.value = _selectedLeadForDetail.value?.copy(
                    initialMessage = newMessage.trim(),
                    updatedAt = System.currentTimeMillis()
                )
            }
        }
    }

    fun addFeedback(
        lead: LeadEntity,
        disposition: String,
        notes: String,
        newStatus: String,
        nextFollowUpDate: Long?,
        nextFollowUpNote: String
    ) {
        viewModelScope.launch {
            val agent = _currentAgent.value
            val agentId = agent?.id ?: lead.assignedAgentId
            val agentName = agent?.name ?: lead.assignedAgentName

            repository.addFeedback(
                lead = lead,
                agentId = agentId,
                agentName = agentName,
                disposition = disposition,
                notes = notes,
                newStatus = newStatus,
                nextFollowUpDate = nextFollowUpDate,
                nextFollowUpNote = nextFollowUpNote
            )
            // Update selected lead view
            _selectedLeadForDetail.value = lead.copy(
                status = newStatus,
                nextFollowUpDate = nextFollowUpDate,
                nextFollowUpNote = nextFollowUpNote,
                updatedAt = System.currentTimeMillis()
            )
        }
    }

    fun reassignLead(lead: LeadEntity, newAgent: SalesAgentEntity, reason: String) {
        viewModelScope.launch {
            repository.reassignLead(lead, newAgent, reason)
            _selectedLeadForDetail.value = lead.copy(
                assignedAgentId = newAgent.id,
                assignedAgentName = newAgent.name
            )
        }
    }

    fun toggleAgentActive(agent: SalesAgentEntity, isActive: Boolean) {
        viewModelScope.launch {
            repository.updateAgentStatus(agent, isActive)
        }
    }

    fun addNewAgent(name: String, email: String, phone: String) {
        viewModelScope.launch {
            val agent = SalesAgentEntity(
                name = name,
                email = email,
                phoneNumber = phone,
                role = "AGENT",
                isActive = true,
                leadsAssignedCount = 0,
                leadsConvertedCount = 0,
                avatarColorHex = 0xFF059669
            )
            repository.addAgent(agent)
        }
    }

    fun checkFollowUpRemindersNow() {
        viewModelScope.launch {
            repository.checkAndTriggerOverdueReminders()
        }
    }

    fun markNotificationsRead() {
        viewModelScope.launch {
            repository.markAllNotificationsAsRead()
        }
    }

    fun deleteLead(lead: LeadEntity) {
        viewModelScope.launch {
            repository.deleteLead(lead)
            if (_selectedLeadForDetail.value?.id == lead.id) {
                _selectedLeadForDetail.value = null
            }
        }
    }

    fun addOrUpdateCampaignRule(
        pattern: String,
        agentId: Long,
        agentName: String,
        description: String = ""
    ) {
        viewModelScope.launch {
            repository.addOrUpdateCampaignRule(pattern, agentId, agentName, description)
        }
    }

    fun toggleCampaignRule(ruleId: Long, isActive: Boolean) {
        viewModelScope.launch {
            repository.setCampaignRuleActive(ruleId, isActive)
        }
    }

    fun deleteCampaignRule(ruleId: Long) {
        viewModelScope.launch {
            repository.deleteCampaignRule(ruleId)
        }
    }

    // Meta Leads Campaigns Management & Distribution
    fun addMetaCampaign(
        campaignName: String,
        adAccountId: String,
        platform: String,
        leadFormName: String,
        distributionMode: String,
        assignedAgentId: Long,
        assignedAgentName: String,
        budgetDaily: String,
        onComplete: (Long) -> Unit = {}
    ) {
        viewModelScope.launch {
            val newCampaign = MetaCampaignEntity(
                campaignName = campaignName.trim(),
                adAccountId = adAccountId.ifBlank { "act_${(100000..999999).random()}" },
                platform = platform,
                leadFormName = leadFormName.ifBlank { "Meta Instant Lead Form" },
                distributionMode = distributionMode,
                assignedAgentId = if (distributionMode == "SINGLE_AGENT") assignedAgentId else 0L,
                assignedAgentName = if (distributionMode == "SINGLE_AGENT") assignedAgentName else "Team Round-Robin",
                campaignBudgetDaily = budgetDaily.ifBlank { "₹1,500/day" }
            )
            val id = repository.addMetaCampaign(newCampaign)
            onComplete(id)
        }
    }

    fun updateCampaignDistribution(
        campaignId: Long,
        mode: String,
        agentId: Long,
        agentName: String
    ) {
        viewModelScope.launch {
            repository.updateCampaignDistribution(campaignId, mode, agentId, agentName)
        }
    }

    fun toggleMetaCampaignActive(campaignId: Long, isActive: Boolean) {
        viewModelScope.launch {
            repository.toggleMetaCampaignActive(campaignId, isActive)
        }
    }

    fun deleteMetaCampaign(campaignId: Long) {
        viewModelScope.launch {
            repository.deleteMetaCampaign(campaignId)
        }
    }

    fun fetchLeadFromMetaCampaign(
        campaign: MetaCampaignEntity,
        customName: String? = null,
        customPhone: String? = null,
        customEmail: String? = null,
        customNotes: String? = null,
        customBudget: String? = null,
        customDealValue: Double? = null,
        customPriority: String? = null,
        onResult: (LeadEntity, Boolean) -> Unit
    ) {
        viewModelScope.launch {
            val randomSuffix = (1000..9999).random()
            val sampleNames = listOf("Arun Mehta", "Pooja Singhania", "Vikram Rathore", "Kavita Rao", "Deepak Bansal", "Meenakshi Joshi")
            val leadName = customName?.takeIf { it.isNotBlank() } ?: sampleNames.random()
            val leadPhone = customPhone?.takeIf { it.isNotBlank() } ?: ("+91 98" + (10000000..99999999).random())
            val leadEmail = customEmail?.takeIf { it.isNotBlank() } ?: "${leadName.lowercase().replace(" ", "")}$randomSuffix@gmail.com"
            val leadNotes = customNotes?.takeIf { it.isNotBlank() } ?: when {
                campaign.campaignName.contains("3BHK", ignoreCase = true) || campaign.campaignName.contains("Flat", ignoreCase = true) ->
                    "Interested in 3BHK flat. Want weekend site visit, brochure, and discount on immediate down payment."
                campaign.campaignName.contains("Solar", ignoreCase = true) ->
                    "Want 5KW residential rooftop solar panel installation under MNRE government subsidy."
                campaign.campaignName.contains("Commercial", ignoreCase = true) ->
                    "Inquiry for 1,200 sqft commercial retail space for lease/investment in prime market."
                else ->
                    "Saw your ad on ${campaign.platform}. Please share complete quotation and schedule consultation."
            }
            val leadBudget = customBudget?.takeIf { it.isNotBlank() } ?: "Standard Budget"
            val dealVal = customDealValue ?: 75000.0
            val prio = customPriority?.takeIf { it.isNotBlank() } ?: "HOT"

            val result = repository.ingestLead(
                name = leadName,
                phoneNumber = leadPhone,
                email = leadEmail,
                source = "Meta Ads (${campaign.platform})",
                campaignName = campaign.campaignName,
                initialMessage = leadNotes,
                budget = leadBudget,
                dealValue = dealVal,
                priority = prio
            )
            onResult(result.first, result.second)
        }
    }

    fun testWebhookHandshake(
        callbackUrl: String,
        verifyToken: String,
        onResult: (WebhookTestResult) -> Unit
    ) {
        viewModelScope.launch {
            val result = MetaGraphApiService.testWebhookHandshake(callbackUrl, verifyToken)
            onResult(result)
        }
    }

    fun syncLiveLeadsFromMetaGraphApi(
        pageAccessToken: String,
        formId: String,
        campaignName: String,
        onResult: (successCount: Int, errorMessage: String?) -> Unit
    ) {
        viewModelScope.launch {
            val fetchResult = MetaGraphApiService.fetchLiveLeadsFromMeta(pageAccessToken, formId)
            fetchResult.fold(
                onSuccess = { leads ->
                    if (leads.isEmpty()) {
                        onResult(0, "No leads returned from Meta for form ID '$formId'. (Make sure leads were submitted or use Meta Lead Ads Testing Tool).")
                        return@launch
                    }
                    var count = 0
                    for (lead in leads) {
                        repository.ingestLead(
                            name = lead.fullName,
                            phoneNumber = lead.phoneNumber,
                            email = lead.email,
                            source = "Meta Ads Live (Graph API)",
                            campaignName = campaignName.ifBlank { "Meta Lead Ads" },
                            initialMessage = lead.customNotes.ifBlank { "Live lead submitted on Facebook/Instagram Lead Form" },
                            budget = "Standard",
                            dealValue = 75000.0,
                            priority = "HOT"
                        )
                        count++
                    }
                    onResult(count, null)
                },
                onFailure = { error ->
                    onResult(0, error.localizedMessage ?: "Unknown error connecting to Meta Graph API")
                }
            )
        }
    }

    fun syncLeadsFromBackend(
        backendUrl: String,
        campaignName: String,
        onResult: (successCount: Int, errorMessage: String?) -> Unit
    ) {
        viewModelScope.launch {
            val fetchResult = MetaGraphApiService.fetchLeadsFromBackend(backendUrl)
            fetchResult.fold(
                onSuccess = { leads ->
                    if (leads.isEmpty()) {
                        onResult(0, "No leads waiting on backend server. Test a lead or send a webhook event from Meta.")
                        return@launch
                    }
                    var count = 0
                    for (lead in leads) {
                        repository.ingestLead(
                            name = lead.fullName,
                            phoneNumber = lead.phoneNumber,
                            email = lead.email,
                            source = lead.formId.ifBlank { "Meta Webhook Backend" },
                            campaignName = campaignName.ifBlank { "Meta Inbound" },
                            initialMessage = lead.customNotes.ifBlank { "Inbound Meta / WhatsApp Lead via Backend" },
                            budget = "Standard",
                            dealValue = 75000.0,
                            priority = "HOT"
                        )
                        count++
                    }
                    onResult(count, null)
                },
                onFailure = { error ->
                    onResult(0, error.localizedMessage ?: "Failed to connect to backend server")
                }
            )
        }
    }

    fun checkBackendHealth(
        backendUrl: String,
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            val result = MetaGraphApiService.checkBackendHealth(backendUrl)
            result.fold(
                onSuccess = { msg -> onResult(true, msg) },
                onFailure = { err -> onResult(false, err.localizedMessage ?: "Connection failed") }
            )
        }
    }

    /**
     * Central Team Cloud Sync (Multi-Device Backend)
     */
    fun triggerCloudSync(onResult: ((Boolean, String) -> Unit)? = null) {
        viewModelScope.launch {
            val result = cloudSyncManager.triggerManualSync()
            result.fold(
                onSuccess = { msg -> onResult?.invoke(true, msg) },
                onFailure = { err -> onResult?.invoke(false, err.localizedMessage ?: "Cloud sync failed") }
            )
        }
    }

    fun setCloudServerUrl(url: String) {
        cloudSyncManager.updateServerUrl(url)
    }

    fun setCloudAutoSync(enabled: Boolean) {
        cloudSyncManager.setAutoSyncEnabled(enabled)
    }

    fun checkCloudHealth(onResult: ((Boolean, String) -> Unit)? = null) {
        viewModelScope.launch {
            val health = cloudSyncManager.checkConnection()
            onResult?.invoke(health.isOnline, health.message)
        }
    }
}
