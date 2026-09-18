package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.notification.NotificationHelper
import com.example.ui.components.CloudSyncDialog
import com.example.ui.components.CrmTopBar
import com.example.ui.screens.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.CrmViewModel
import com.example.ui.viewmodel.UserRole
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: CrmViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Create Android Notification Channel
        NotificationHelper.createNotificationChannel(this)

        setContent {
            MyApplicationTheme {
                LeadPulseApp(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun LeadPulseApp(viewModel: CrmViewModel) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // ViewModel State Observers
    val userSession by viewModel.userSession.collectAsStateWithLifecycle()
    val isManagerMode by viewModel.isManagerMode.collectAsStateWithLifecycle()
    val currentAgent by viewModel.currentAgent.collectAsStateWithLifecycle()
    val adminSelectedAgentFilter by viewModel.adminSelectedAgentFilter.collectAsStateWithLifecycle()
    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val statusFilter by viewModel.statusFilter.collectAsStateWithLifecycle()
    val priorityFilter by viewModel.priorityFilter.collectAsStateWithLifecycle()
    val selectedLead by viewModel.selectedLeadForDetail.collectAsStateWithLifecycle()
    val showNotificationsDrawer by viewModel.showNotificationsDrawer.collectAsStateWithLifecycle()

    val leads by viewModel.filteredLeads.collectAsStateWithLifecycle()
    val allLeadsRaw by viewModel.allLeadsRaw.collectAsStateWithLifecycle()
    val allAgents by viewModel.allAgents.collectAsStateWithLifecycle()
    val campaignRules by viewModel.campaignRoutingRules.collectAsStateWithLifecycle()
    val metaCampaigns by viewModel.allMetaCampaigns.collectAsStateWithLifecycle()
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()
    val analytics by viewModel.analyticsState.collectAsStateWithLifecycle()
    val cloudSyncState by viewModel.cloudSyncState.collectAsStateWithLifecycle()
    var showCloudSyncDialog by remember { mutableStateOf(false) }
    var showLogoutConfirmationDialog by remember { mutableStateOf(false) }

    // Request notification permission on Android 13+
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            scope.launch {
                snackbarHostState.showSnackbar("Notification alerts enabled for new leads!")
            }
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // IF NOT LOGGED IN: Show Login & Registration Screen (OTP Based)
    if (!userSession.isLoggedIn) {
        LoginScreen(
            allAgents = allAgents,
            onLoginAsAdmin = { pin, onResult ->
                viewModel.loginAsAdminWithPin(pin) { success, msg ->
                    if (success) {
                        scope.launch { snackbarHostState.showSnackbar(msg) }
                    }
                    onResult(success, msg)
                }
            },
            onSendLoginOtp = { phone, onResult ->
                viewModel.sendLoginOtp(phone) { success, msg, otp ->
                    if (success) {
                        scope.launch { snackbarHostState.showSnackbar(msg) }
                    }
                    onResult(success, msg, otp)
                }
            },
            onVerifyLoginOtp = { phone, otp, onResult ->
                viewModel.verifyLoginOtp(phone, otp) { success, msg, agent ->
                    if (success && agent != null) {
                        scope.launch { snackbarHostState.showSnackbar(msg) }
                    }
                    onResult(success, msg)
                }
            },
            onSendRegOtp = { phone, onResult ->
                viewModel.sendRegistrationOtp(phone) { success, msg, otp ->
                    if (success) {
                        scope.launch { snackbarHostState.showSnackbar(msg) }
                    }
                    onResult(success, msg, otp)
                }
            },
            onVerifyAndRegister = { name, email, phone, department, otp, onResult ->
                viewModel.verifyAndRegisterSalesPerson(name, email, phone, department, otp) { success, msg, agent ->
                    if (success && agent != null) {
                        scope.launch { snackbarHostState.showSnackbar(msg) }
                    }
                    onResult(success, msg)
                }
            }
        )
        return
    }

    val unreadNotifsCount = notifications.count { !it.isRead }
    val isAdmin = userSession.role == UserRole.ADMIN

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CrmTopBar(
                isManagerMode = isManagerMode,
                currentAgent = currentAgent,
                allAgents = allAgents,
                unreadNotificationCount = unreadNotifsCount,
                cloudSyncState = cloudSyncState,
                onCloudSyncClick = { showCloudSyncDialog = true },
                onRoleSelected = { isManager, agent ->
                    viewModel.selectRole(isManager, agent)
                    scope.launch {
                        val roleText = if (isManager) "Manager Mode" else "${agent?.name} (Sales Rep)"
                        snackbarHostState.showSnackbar("Switched to $roleText")
                    }
                },
                onNotificationsClick = {
                    viewModel.toggleNotificationsDrawer(true)
                },
                onCheckRemindersClick = {
                    viewModel.checkFollowUpRemindersNow()
                    scope.launch {
                        snackbarHostState.showSnackbar("Follow-up reminder scan complete!")
                    }
                },
                onLogoutClick = {
                    showLogoutConfirmationDialog = true
                }
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = Color.White,
                contentColor = Slate900,
                tonalElevation = 8.dp
            ) {
                // Tab 0: Leads Pipeline
                NavigationBarItem(
                    selected = currentTab == 0,
                    onClick = { viewModel.setTab(0) },
                    icon = { Icon(Icons.Default.FilterList, contentDescription = "Leads") },
                    label = {
                        Text(
                            if (isAdmin) "All Leads" else "My Leads",
                            fontSize = 11.sp,
                            fontWeight = if (currentTab == 0) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Emerald600,
                        selectedTextColor = Emerald600,
                        indicatorColor = Color(0xFFDCFCE7)
                    ),
                    modifier = Modifier.testTag("nav_tab_leads")
                )

                if (isAdmin) {
                    // Tab 1: Meta Ads Leads Engine (Campaigns & Lead Distribution)
                    NavigationBarItem(
                        selected = currentTab == 1,
                        onClick = { viewModel.setTab(1) },
                        icon = {
                            BadgedBox(badge = {
                                if (metaCampaigns.isNotEmpty()) {
                                    Badge(containerColor = Color(0xFF1877F2)) { Text("${metaCampaigns.size}") }
                                }
                            }) {
                                Icon(Icons.Default.Campaign, contentDescription = "Meta Ads")
                            }
                        },
                        label = {
                            Text(
                                "Meta Ads",
                                fontSize = 11.sp,
                                fontWeight = if (currentTab == 1) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFF1877F2),
                            selectedTextColor = Color(0xFF1877F2),
                            indicatorColor = Color(0xFFDBEAFE)
                        ),
                        modifier = Modifier.testTag("nav_tab_meta_ads")
                    )

                    // Tab 2: Analytics
                    NavigationBarItem(
                        selected = currentTab == 2,
                        onClick = { viewModel.setTab(2) },
                        icon = { Icon(Icons.Default.BarChart, contentDescription = "Analytics") },
                        label = {
                            Text(
                                "Analytics",
                                fontSize = 11.sp,
                                fontWeight = if (currentTab == 2) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Emerald600,
                            selectedTextColor = Emerald600,
                            indicatorColor = Color(0xFFDCFCE7)
                        ),
                        modifier = Modifier.testTag("nav_tab_analytics")
                    )

                    // Tab 3: WhatsApp Web QR Gateway
                    NavigationBarItem(
                        selected = currentTab == 3,
                        onClick = { viewModel.setTab(3) },
                        icon = { Icon(Icons.Default.QrCodeScanner, contentDescription = "WhatsApp QR") },
                        label = {
                            Text(
                                "WhatsApp QR",
                                fontSize = 10.sp,
                                fontWeight = if (currentTab == 3) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Emerald600,
                            selectedTextColor = Emerald600,
                            indicatorColor = Color(0xFFDCFCE7)
                        ),
                        modifier = Modifier.testTag("nav_tab_wa_qr")
                    )

                    // Tab 4: Sales Team & Round-Robin
                    NavigationBarItem(
                        selected = currentTab == 4,
                        onClick = { viewModel.setTab(4) },
                        icon = { Icon(Icons.Default.Groups, contentDescription = "Team") },
                        label = {
                            Text(
                                "Team",
                                fontSize = 11.sp,
                                fontWeight = if (currentTab == 4) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Emerald600,
                            selectedTextColor = Emerald600,
                            indicatorColor = Color(0xFFDCFCE7)
                        ),
                        modifier = Modifier.testTag("nav_tab_team")
                    )
                } else {
                    // Non-admin (Sales Person) Performance Tab
                    NavigationBarItem(
                        selected = currentTab == 1,
                        onClick = { viewModel.setTab(1) },
                        icon = { Icon(Icons.Default.BarChart, contentDescription = "Performance") },
                        label = {
                            Text(
                                "Performance",
                                fontSize = 11.sp,
                                fontWeight = if (currentTab == 1) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Emerald600,
                            selectedTextColor = Emerald600,
                            indicatorColor = Color(0xFFDCFCE7)
                        ),
                        modifier = Modifier.testTag("nav_tab_performance")
                    )
                }
            }
        },
        floatingActionButton = {
            if (isAdmin && currentTab == 0) {
                ExtendedFloatingActionButton(
                    onClick = { viewModel.setTab(5) },
                    containerColor = Emerald600,
                    contentColor = Color.White,
                    icon = { Icon(Icons.Default.TableChart, contentDescription = "Excel Import") },
                    text = { Text("Import Excel", fontWeight = FontWeight.Bold) },
                    modifier = Modifier.testTag("fab_excel_import")
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentTab) {
                0 -> LeadListScreen(
                    leads = leads,
                    searchQuery = searchQuery,
                    onSearchQueryChange = { viewModel.setSearchQuery(it) },
                    selectedStatus = statusFilter,
                    onStatusSelected = { viewModel.setStatusFilter(it) },
                    selectedPriority = priorityFilter,
                    onPrioritySelected = { viewModel.setPriorityFilter(it) },
                    onLeadClick = { lead -> viewModel.selectLeadForDetail(lead) },
                    onAddLeadClick = { viewModel.setTab(if (isAdmin) 1 else 0) },
                    isAdmin = isAdmin,
                    allAgents = allAgents,
                    adminSelectedAgentId = adminSelectedAgentFilter,
                    onAdminAgentFilterChange = { viewModel.setAdminAgentFilter(it) },
                    onExportLeadsClick = if (isAdmin) {
                        { viewModel.setTab(5) }
                    } else null,
                    currentAgentName = currentAgent?.name
                )

                1 -> if (isAdmin) {
                    MetaLeadsCampaignScreen(
                        campaigns = metaCampaigns,
                        salesAgents = allAgents,
                        onAddCampaign = { name, adAccountId, platform, formName, mode, agentId, agentName, budget ->
                            viewModel.addMetaCampaign(name, adAccountId, platform, formName, mode, agentId, agentName, budget)
                            scope.launch {
                                snackbarHostState.showSnackbar("Meta Campaign '$name' created ($mode)")
                            }
                        },
                        onUpdateDistribution = { campaignId, mode, agentId, agentName ->
                            viewModel.updateCampaignDistribution(campaignId, mode, agentId, agentName)
                            scope.launch {
                                val msg = if (mode == "SINGLE_AGENT") "Leads will route ONLY to $agentName" else "Leads will rotate across sales team (Round-Robin)"
                                snackbarHostState.showSnackbar("Updated distribution: $msg")
                            }
                        },
                        onToggleCampaignActive = { campaignId, isActive ->
                            viewModel.toggleMetaCampaignActive(campaignId, isActive)
                        },
                        onDeleteCampaign = { campaignId ->
                            viewModel.deleteMetaCampaign(campaignId)
                            scope.launch {
                                snackbarHostState.showSnackbar("Meta Campaign deleted")
                            }
                        },
                        onFetchLeadFromCampaign = { campaign, cName, cPhone, cEmail, cNotes, cBudget, cDeal, cPrio, onResult ->
                            viewModel.fetchLeadFromMetaCampaign(
                                campaign, cName, cPhone, cEmail, cNotes, cBudget, cDeal, cPrio
                            ) { resultLead, wasDup ->
                                onResult(resultLead, wasDup)
                                scope.launch {
                                    val routingType = if (campaign.distributionMode == "SINGLE_AGENT") "Single Rep" else "Round-Robin"
                                    snackbarHostState.showSnackbar("Lead assigned to ${resultLead.assignedAgentName} ($routingType)")
                                }
                            }
                        },
                        onTestWebhook = { url, token, onResult ->
                            viewModel.testWebhookHandshake(url, token, onResult)
                        },
                        onSyncGraphApiLeads = { token, formId, campaignName, onResult ->
                            viewModel.syncLiveLeadsFromMetaGraphApi(token, formId, campaignName, onResult)
                        },
                        onSyncBackendLeads = { backendUrl, campaignName, onResult ->
                            viewModel.syncLeadsFromBackend(backendUrl, campaignName, onResult)
                        },
                        onCheckBackendHealth = { backendUrl, onResult ->
                            viewModel.checkBackendHealth(backendUrl, onResult)
                        }
                    )
                } else {
                    val agent = currentAgent
                    if (agent != null) {
                        SalesPersonPerformanceScreen(
                            agent = agent,
                            myLeads = leads,
                            onLeadClick = { lead -> viewModel.selectLeadForDetail(lead) }
                        )
                    } else {
                        Box(modifier = Modifier.fillMaxSize())
                    }
                }

                2 -> if (isAdmin) {
                    ManagerDashboardScreen(
                        analytics = analytics,
                        allAgents = allAgents,
                        allLeads = allLeadsRaw,
                        onToggleAgentActive = { agent, isActive ->
                            viewModel.toggleAgentActive(agent, isActive)
                            scope.launch {
                                val status = if (isActive) "added to" else "paused from"
                                snackbarHostState.showSnackbar("${agent.name} $status Round-Robin queue")
                            }
                        }
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize())
                }

                3 -> if (isAdmin) {
                    WhatsAppWebQrConnectScreen(
                        onSimulateLead = { name, phone, email, source, campaign, message, budget, dealVal, priority, onResult ->
                            viewModel.ingestNewLead(
                                name, phone, email, source, campaign, message, budget, dealVal, priority
                            ) { resultLead, wasDup ->
                                onResult(resultLead, wasDup)
                                scope.launch {
                                    if (wasDup) {
                                        snackbarHostState.showSnackbar("🔁 Existing contact updated: ${resultLead.assignedAgentName}")
                                    } else {
                                        snackbarHostState.showSnackbar("🎉 WhatsApp lead saved in CRM & assigned to ${resultLead.assignedAgentName}!")
                                    }
                                }
                            }
                        }
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize())
                }

                4 -> if (isAdmin) {
                    TeamManagementScreen(
                        agents = allAgents,
                        campaignRules = campaignRules,
                        onToggleActive = { agent, isActive ->
                            viewModel.toggleAgentActive(agent, isActive)
                        },
                        onAddNewAgent = { name, email, phone ->
                            viewModel.addNewAgent(name, email, phone)
                            scope.launch {
                                snackbarHostState.showSnackbar("Added $name to sales team!")
                            }
                        },
                        onAddCampaignRule = { pattern, agentId, agentName, desc ->
                            viewModel.addOrUpdateCampaignRule(pattern, agentId, agentName, desc)
                            scope.launch {
                                snackbarHostState.showSnackbar("Routing rule created: '$pattern' ➔ $agentName")
                            }
                        },
                        onToggleCampaignRule = { ruleId, isActive ->
                            viewModel.toggleCampaignRule(ruleId, isActive)
                        },
                        onDeleteCampaignRule = { ruleId ->
                            viewModel.deleteCampaignRule(ruleId)
                            scope.launch {
                                snackbarHostState.showSnackbar("Campaign routing rule deleted")
                            }
                        }
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize())
                }

                5 -> if (isAdmin) {
                    ExcelImportExportScreen(
                        allLeads = allLeadsRaw,
                        activeAgents = allAgents,
                        onImportLeads = { leadsList, onResult ->
                            viewModel.importExcelLeads(leadsList) { res ->
                                onResult(res)
                            }
                        },
                        onExportAllCsv = { viewModel.exportAllLeadsCsv() },
                        onBackClick = { viewModel.setTab(0) }
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize())
                }
            }
        }

        // Lead Detail & Feedback Dialog Sheet
        selectedLead?.let { lead ->
            val feedbackLogs by viewModel.getFeedbackForLead(lead.id).collectAsStateWithLifecycle()
            LeadDetailAndFeedbackDialog(
                lead = lead,
                feedbackLogs = feedbackLogs,
                isManager = isManagerMode,
                allAgents = allAgents,
                onDismiss = { viewModel.selectLeadForDetail(null) },
                onSubmitFeedback = { disposition, notes, newStatus, followUpDate, followUpNote ->
                    viewModel.addFeedback(lead, disposition, notes, newStatus, followUpDate, followUpNote)
                    scope.launch {
                        snackbarHostState.showSnackbar("Feedback saved & lead status updated!")
                    }
                },
                onReassign = { newAgent, reason ->
                    viewModel.reassignLead(lead, newAgent, reason)
                    scope.launch {
                        snackbarHostState.showSnackbar("Lead reassigned to ${newAgent.name}")
                    }
                },
                onDeleteLead = {
                    viewModel.deleteLead(lead)
                    scope.launch {
                        snackbarHostState.showSnackbar("Lead deleted")
                    }
                },
                onUpdateLeadMessage = { updatedMsg ->
                    viewModel.updateLeadMessage(lead.id, updatedMsg)
                    scope.launch {
                        snackbarHostState.showSnackbar("Customer inquiry message updated!")
                    }
                }
            )
        }

        // Notifications Drawer Dialog
        if (showNotificationsDrawer) {
            NotificationDrawerDialog(
                notifications = notifications,
                onDismiss = { viewModel.toggleNotificationsDrawer(false) },
                onMarkAllRead = {
                    viewModel.markNotificationsRead()
                    scope.launch {
                        snackbarHostState.showSnackbar("All notifications marked as read")
                    }
                },
                onNotificationClick = { notif ->
                    if (notif.leadId != null) {
                        val targetLead = allLeadsRaw.find { it.id == notif.leadId }
                        if (targetLead != null) {
                            viewModel.selectLeadForDetail(targetLead)
                            viewModel.toggleNotificationsDrawer(false)
                        }
                    }
                }
            )
        }

        // Central Team Cloud Sync Dialog
        if (showCloudSyncDialog) {
            CloudSyncDialog(
                syncState = cloudSyncState,
                onDismiss = { showCloudSyncDialog = false },
                onSyncNow = { onResult ->
                    viewModel.triggerCloudSync { success, msg ->
                        onResult(success, msg)
                        scope.launch {
                            snackbarHostState.showSnackbar(msg)
                        }
                    }
                },
                onUpdateServerUrl = { newUrl ->
                    viewModel.setCloudServerUrl(newUrl)
                },
                onToggleAutoSync = { enabled ->
                    viewModel.setCloudAutoSync(enabled)
                },
                onCheckHealth = { onResult ->
                    viewModel.checkCloudHealth { isOnline, msg ->
                        onResult(isOnline, msg)
                    }
                }
            )
        }

        // Logout Confirmation Dialog
        if (showLogoutConfirmationDialog) {
            val userLabel = if (isAdmin) "Administrator Account" else "${currentAgent?.name ?: "Sales Person"} (${currentAgent?.phoneNumber ?: ""})"
            AlertDialog(
                onDismissRequest = { showLogoutConfirmationDialog = false },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Logout,
                        contentDescription = "Logout",
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(32.dp)
                    )
                },
                title = {
                    Text(
                        text = "Confirm Logout",
                        fontWeight = FontWeight.Bold,
                        color = Slate900
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Are you sure you want to log out from $userLabel?",
                            fontSize = 14.sp,
                            color = Slate700
                        )
                        Text(
                            text = "You will be redirected to the secure login portal where you or another representative can sign in.",
                            fontSize = 12.sp,
                            color = Slate500
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showLogoutConfirmationDialog = false
                            viewModel.logout()
                            scope.launch {
                                snackbarHostState.showSnackbar("Logged out successfully")
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                    ) {
                        Text("Log Out", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = { showLogoutConfirmationDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(text = "Hello $name!", modifier = modifier)
}
