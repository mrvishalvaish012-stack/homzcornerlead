package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.LeadEntity
import com.example.ui.theme.*
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

data class WebhookLogItem(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val method: String = "POST",
    val path: String,
    val statusCode: Int = 200,
    val summary: String,
    val leadName: String? = null,
    val assignedAgent: String? = null,
    val isDuplicate: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeadIngestionScreen(
    onSimulateLead: (
        name: String,
        phone: String,
        email: String,
        source: String,
        campaign: String,
        message: String,
        budget: String,
        dealValue: Double,
        priority: String,
        onResult: (LeadEntity, Boolean) -> Unit
    ) -> Unit,
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }

    // Screen Sub-Tabs: 0 = Live Webhooks & API, 1 = Quick Simulator, 2 = Setup Documentation
    var selectedScreenTab by remember { mutableIntStateOf(0) }

    // Live Webhook State
    val webhookEndpointUrl = "https://api.leadpulse.io/v1/webhook/leads?token=live_lp_7829a3"
    val webhookVerifyToken = "leadpulse_meta_verify_2026"
    val webhookSecret = "whsec_live_9f83a27e901b"

    var selectedPayloadTemplate by remember { mutableIntStateOf(0) } // 0 = Meta Leadgen, 1 = WhatsApp Cloud API, 2 = Zapier/Pabbly
    var webhookJsonPayload by remember { mutableStateOf(getMetaLeadgenSampleJson()) }
    var jsonParseError by remember { mutableStateOf<String?>(null) }
    var isProcessingWebhook by remember { mutableStateOf(false) }

    // Webhook Activity Logs
    val webhookLogs = remember {
        mutableStateListOf(
            WebhookLogItem(
                timestamp = System.currentTimeMillis() - 1000 * 60 * 12,
                path = "/v1/webhook/meta-ads",
                summary = "Meta Instant Form Webhook • Leadgen #98214 Verified",
                leadName = "Vikram Sharma",
                assignedAgent = "Rahul Sharma",
                isDuplicate = false
            ),
            WebhookLogItem(
                timestamp = System.currentTimeMillis() - 1000 * 60 * 35,
                path = "/v1/webhook/whatsapp",
                summary = "WhatsApp Cloud API Inbound Message • Text Received",
                leadName = "Rohan Mehra",
                assignedAgent = "Rahul Sharma",
                isDuplicate = true
            )
        )
    }

    // Manual Simulation Form State (NO AMOUNT / DEAL VALUE FIELDS)
    var manualName by remember { mutableStateOf("") }
    var manualPhone by remember { mutableStateOf("") }
    var manualEmail by remember { mutableStateOf("") }
    var manualSource by remember { mutableStateOf("Meta Ads") }
    var manualCampaign by remember { mutableStateOf("Real Estate 3BHK Campaign") }
    var manualMessage by remember { mutableStateOf("") }
    var manualPriority by remember { mutableStateOf("HOT") }

    var lastIngestedLead by remember { mutableStateOf<LeadEntity?>(null) }
    var lastWasDuplicate by remember { mutableStateOf(false) }
    var isSubmittingManual by remember { mutableStateOf(false) }

    val sources = listOf("Meta Ads", "WhatsApp Direct", "Instagram Lead Ad", "Facebook Form")

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Slate50)
        ) {
            // Screen Sub-Navigation
            TabRow(
                selectedTabIndex = selectedScreenTab,
                containerColor = Color.White,
                contentColor = Emerald600
            ) {
                Tab(
                    selected = selectedScreenTab == 0,
                    onClick = { selectedScreenTab = 0 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.Webhook, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text("Live Webhook & API", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                )
                Tab(
                    selected = selectedScreenTab == 1,
                    onClick = { selectedScreenTab = 1 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text("Quick Simulator", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                )
                Tab(
                    selected = selectedScreenTab == 2,
                    onClick = { selectedScreenTab = 2 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.MenuBook, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text("Setup Guide", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                )
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                when (selectedScreenTab) {
                    0 -> {
                        // TAB 0: LIVE WEBHOOK & API RECEIVER
                        item {
                            WebhookStatusHeader()
                        }

                        item {
                            WebhookEndpointsCard(
                                endpointUrl = webhookEndpointUrl,
                                verifyToken = webhookVerifyToken,
                                secret = webhookSecret,
                                onCopy = { text, label ->
                                    clipboardManager.setText(AnnotatedString(text))
                                }
                            )
                        }

                        // Webhook Payload Ingestion & Real-Time Parser
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                shape = RoundedCornerShape(12.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = "Incoming Webhook Payload Receiver",
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Slate900
                                            )
                                            Text(
                                                text = "Paste raw JSON or select sample payload from Meta Ads / WhatsApp / Zapier",
                                                fontSize = 11.sp,
                                                color = Slate600
                                            )
                                        }

                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Emerald50)
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text("200 OK READY", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Emerald600)
                                        }
                                    }

                                    // Template Selector Chips
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        FilterChip(
                                            selected = selectedPayloadTemplate == 0,
                                            onClick = {
                                                selectedPayloadTemplate = 0
                                                webhookJsonPayload = getMetaLeadgenSampleJson()
                                                jsonParseError = null
                                            },
                                            label = { Text("Meta Lead Ad", fontSize = 11.sp) }
                                        )
                                        FilterChip(
                                            selected = selectedPayloadTemplate == 1,
                                            onClick = {
                                                selectedPayloadTemplate = 1
                                                webhookJsonPayload = getWhatsAppCloudApiSampleJson()
                                                jsonParseError = null
                                            },
                                            label = { Text("WhatsApp API", fontSize = 11.sp) }
                                        )
                                        FilterChip(
                                            selected = selectedPayloadTemplate == 2,
                                            onClick = {
                                                selectedPayloadTemplate = 2
                                                webhookJsonPayload = getZapierWebhookSampleJson()
                                                jsonParseError = null
                                            },
                                            label = { Text("Zapier / Pabbly", fontSize = 11.sp) }
                                        )
                                    }

                                    // Raw JSON Payload Editor
                                    OutlinedTextField(
                                        value = webhookJsonPayload,
                                        onValueChange = {
                                            webhookJsonPayload = it
                                            jsonParseError = null
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 140.dp, max = 220.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        textStyle = LocalTextStyle.current.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            lineHeight = 15.sp
                                        ),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedContainerColor = Slate900,
                                            unfocusedContainerColor = Slate900,
                                            focusedTextColor = Color(0xFF67E8F9),
                                            unfocusedTextColor = Color(0xFF67E8F9)
                                        )
                                    )

                                    if (jsonParseError != null) {
                                        Text(
                                            text = "⚠️ $jsonParseError",
                                            color = Color(0xFFDC2626),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }

                                    Button(
                                        onClick = {
                                            isProcessingWebhook = true
                                            val parsed = parseWebhookPayload(webhookJsonPayload)
                                            if (parsed != null) {
                                                jsonParseError = null
                                                onSimulateLead(
                                                    parsed.name,
                                                    parsed.phone,
                                                    parsed.email,
                                                    parsed.source,
                                                    parsed.campaign,
                                                    parsed.message,
                                                    "", // NO BUDGET/AMOUNT
                                                    0.0, // NO DEAL VALUE
                                                    parsed.priority
                                                ) { ingestedLead, wasDup ->
                                                    lastIngestedLead = ingestedLead
                                                    lastWasDuplicate = wasDup
                                                    isProcessingWebhook = false
                                                    webhookLogs.add(
                                                        0,
                                                        WebhookLogItem(
                                                            path = if (parsed.source.contains("WhatsApp")) "/v1/webhook/whatsapp" else "/v1/webhook/meta-ads",
                                                            summary = "Incoming Webhook parsed: ${parsed.source} (${parsed.campaign})",
                                                            leadName = ingestedLead.name,
                                                            assignedAgent = ingestedLead.assignedAgentName,
                                                            isDuplicate = wasDup
                                                        )
                                                    )
                                                }
                                            } else {
                                                isProcessingWebhook = false
                                                jsonParseError = "Invalid JSON format or missing name/phone fields. Please check payload."
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Emerald600),
                                        enabled = !isProcessingWebhook
                                    ) {
                                        if (isProcessingWebhook) {
                                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Processing Webhook...")
                                        } else {
                                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Receive & Process Webhook Payload")
                                        }
                                    }
                                }
                            }
                        }

                        // Ingestion Result Banner if processed
                        if (lastIngestedLead != null) {
                            item {
                                IngestionResultBanner(lead = lastIngestedLead!!, isDuplicate = lastWasDuplicate)
                            }
                        }

                        // Live Webhook Activity Feed
                        item {
                            Text(
                                text = "Live Webhook Activity Stream",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Slate900
                            )
                        }

                        items(webhookLogs, key = { it.id }) { log ->
                            WebhookLogCard(log = log)
                        }
                    }

                    1 -> {
                        // TAB 1: QUICK SIMULATOR (WITHOUT AMOUNT / DEAL VALUE)
                        item {
                            Text(
                                text = "Quick Lead Ingestion Simulator",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Slate900
                            )
                        }

                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // Scenario 1: New Fresh Meta Ad Lead
                                OutlinedCard(
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                    onClick = {
                                        val randomNum = (1000..9999).random()
                                        manualName = "Vikram Sen $randomNum"
                                        manualPhone = "+91 98888 $randomNum"
                                        manualEmail = "vikram$randomNum@gmail.com"
                                        manualSource = "Meta Ads"
                                        manualCampaign = "Solar Rooftop Summer Offer"
                                        manualMessage = "Need rooftop solar cost estimate for residential plot."
                                        manualPriority = "HOT"
                                    }
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text("⚡ Fresh Meta Ad", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = RoyalBlue600)
                                        Text("Tests automatic round-robin assignment to next available agent", fontSize = 10.sp, color = Slate600)
                                    }
                                }

                                // Scenario 2: Duplicate Lead Test
                                OutlinedCard(
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                    onClick = {
                                        manualName = "Rohan Mehra (Repeat)"
                                        manualPhone = "+919820011223"
                                        manualEmail = "rohan.mehra@gmail.com"
                                        manualSource = "WhatsApp Direct"
                                        manualCampaign = "Real Estate Luxury 3BHK"
                                        manualMessage = "Hi, sending another message for the catalog on WhatsApp."
                                        manualPriority = "HOT"
                                    }
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text("🔁 Test Duplicate", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFFB45309))
                                        Text("Tests same-agent retention policy for repeat phone number", fontSize = 10.sp, color = Slate600)
                                    }
                                }
                            }
                        }

                        if (lastIngestedLead != null) {
                            item {
                                IngestionResultBanner(lead = lastIngestedLead!!, isDuplicate = lastWasDuplicate)
                            }
                        }

                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                shape = RoundedCornerShape(12.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text(
                                        text = "Inbound Lead Details (No Amount / Deal Value)",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Slate900
                                    )

                                    OutlinedTextField(
                                        value = manualName,
                                        onValueChange = { manualName = it },
                                        label = { Text("Customer Name *") },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("ingest_name_input"),
                                        shape = RoundedCornerShape(8.dp),
                                        singleLine = true,
                                        textStyle = TextStyle(color = Color.Black, fontSize = 14.sp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = Color.Black,
                                            unfocusedTextColor = Color.Black,
                                            focusedContainerColor = Color.White,
                                            unfocusedContainerColor = Color.White,
                                            focusedBorderColor = Emerald600,
                                            unfocusedBorderColor = Slate300,
                                            cursorColor = Color.Black
                                        )
                                    )

                                    OutlinedTextField(
                                        value = manualPhone,
                                        onValueChange = { manualPhone = it },
                                        label = { Text("WhatsApp Phone Number *") },
                                        placeholder = { Text("+91 98765 43210", color = Slate400) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("ingest_phone_input"),
                                        shape = RoundedCornerShape(8.dp),
                                        singleLine = true,
                                        textStyle = TextStyle(color = Color.Black, fontSize = 14.sp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = Color.Black,
                                            unfocusedTextColor = Color.Black,
                                            focusedContainerColor = Color.White,
                                            unfocusedContainerColor = Color.White,
                                            focusedBorderColor = Emerald600,
                                            unfocusedBorderColor = Slate300,
                                            cursorColor = Color.Black
                                        )
                                    )

                                    OutlinedTextField(
                                        value = manualEmail,
                                        onValueChange = { manualEmail = it },
                                        label = { Text("Customer Email") },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(8.dp),
                                        singleLine = true,
                                        textStyle = TextStyle(color = Color.Black, fontSize = 14.sp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = Color.Black,
                                            unfocusedTextColor = Color.Black,
                                            focusedContainerColor = Color.White,
                                            unfocusedContainerColor = Color.White,
                                            focusedBorderColor = Emerald600,
                                            unfocusedBorderColor = Slate300,
                                            cursorColor = Color.Black
                                        )
                                    )

                                    // Source Selector
                                    Column {
                                        Text("Lead Source Channel", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Slate700)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            sources.take(2).forEach { s ->
                                                FilterChip(
                                                    selected = manualSource == s,
                                                    onClick = { manualSource = s },
                                                    label = { Text(s, fontSize = 11.sp) }
                                                )
                                            }
                                            sources.drop(2).forEach { s ->
                                                FilterChip(
                                                    selected = manualSource == s,
                                                    onClick = { manualSource = s },
                                                    label = { Text(s, fontSize = 11.sp) }
                                                )
                                            }
                                        }
                                    }

                                    OutlinedTextField(
                                        value = manualCampaign,
                                        onValueChange = { manualCampaign = it },
                                        label = { Text("Campaign Name") },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(8.dp),
                                        singleLine = true,
                                        textStyle = TextStyle(color = Color.Black, fontSize = 14.sp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = Color.Black,
                                            unfocusedTextColor = Color.Black,
                                            focusedContainerColor = Color.White,
                                            unfocusedContainerColor = Color.White,
                                            focusedBorderColor = Emerald600,
                                            unfocusedBorderColor = Slate300,
                                            cursorColor = Color.Black
                                        )
                                    )

                                    OutlinedTextField(
                                        value = manualMessage,
                                        onValueChange = { manualMessage = it },
                                        label = { Text("Inquiry Message") },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(8.dp),
                                        minLines = 2,
                                        textStyle = TextStyle(color = Color.Black, fontSize = 14.sp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = Color.Black,
                                            unfocusedTextColor = Color.Black,
                                            focusedContainerColor = Color.White,
                                            unfocusedContainerColor = Color.White,
                                            focusedBorderColor = Emerald600,
                                            unfocusedBorderColor = Slate300,
                                            cursorColor = Color.Black
                                        )
                                    )

                                    // Priority Selector
                                    Column {
                                        Text("Priority", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            listOf("HOT", "WARM", "COLD").forEach { p ->
                                                FilterChip(
                                                    selected = manualPriority == p,
                                                    onClick = { manualPriority = p },
                                                    label = { Text(p, fontSize = 11.sp) }
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Button(
                                        onClick = {
                                            if (manualPhone.isNotBlank()) {
                                                isSubmittingManual = true
                                                onSimulateLead(
                                                    manualName.ifBlank { "Inbound Customer" },
                                                    manualPhone,
                                                    manualEmail.ifBlank { "lead@inquiry.com" },
                                                    manualSource,
                                                    manualCampaign,
                                                    manualMessage.ifBlank { "Interested in pricing details." },
                                                    "", // NO BUDGET/AMOUNT
                                                    0.0, // NO DEAL VALUE
                                                    manualPriority
                                                ) { resultLead, wasDup ->
                                                    lastIngestedLead = resultLead
                                                    lastWasDuplicate = wasDup
                                                    isSubmittingManual = false
                                                    webhookLogs.add(
                                                        0,
                                                        WebhookLogItem(
                                                            path = "/v1/manual/ingest",
                                                            summary = "Manual Simulator Ingestion • ${resultLead.source}",
                                                            leadName = resultLead.name,
                                                            assignedAgent = resultLead.assignedAgentName,
                                                            isDuplicate = wasDup
                                                        )
                                                    )
                                                }
                                            }
                                        },
                                        enabled = manualPhone.isNotBlank() && !isSubmittingManual,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("submit_simulate_lead_btn"),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Emerald600)
                                    ) {
                                        if (isSubmittingManual) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(16.dp),
                                                color = Color.White,
                                                strokeWidth = 2.dp
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Ingesting Lead...")
                                        } else {
                                            Icon(Icons.Default.AddCircle, contentDescription = null)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Submit Lead to Round-Robin Queue")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    2 -> {
                        // TAB 2: SETUP DOCUMENTATION
                        item {
                            SetupDocumentationSection(
                                webhookUrl = webhookEndpointUrl,
                                verifyToken = webhookVerifyToken,
                                onCopy = { text -> clipboardManager.setText(AnnotatedString(text)) }
                            )
                        }
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun WebhookStatusHeader() {
    Card(
        colors = CardDefaults.cardColors(containerColor = Slate900),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(WhatsAppGreen),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.CloudSync, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
                Text(
                    text = "Live Webhook & API Gateway",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }

            Text(
                text = "Accepts real-time POST requests from Meta Ads Lead Gen, WhatsApp Cloud API, and Zapier/Pabbly. Automatic duplicate phone detection and round-robin sales agent assignment.",
                color = Slate200,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(Slate800)
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Emerald500))
                    Text(
                        text = "Webhook Listener: Active (SSL 256-bit)",
                        color = Emerald500,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    text = "Port 8443 / HTTPS",
                    color = Slate400,
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
private fun WebhookEndpointsCard(
    endpointUrl: String,
    verifyToken: String,
    secret: String,
    onCopy: (String, String) -> Unit
) {
    var copiedLabel by remember { mutableStateOf<String?>(null) }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Live Endpoint Configuration",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Slate900
            )

            // Webhook URL
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Webhook Callback URL (Meta Ads & WhatsApp)", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Slate700)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Slate100)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = endpointUrl,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Slate900,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = {
                            onCopy(endpointUrl, "Webhook URL")
                            copiedLabel = "URL"
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = if (copiedLabel == "URL") Icons.Default.Check else Icons.Default.ContentCopy,
                            contentDescription = "Copy URL",
                            tint = if (copiedLabel == "URL") Emerald600 else RoyalBlue600,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Verify Token & Secret Row
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Verify Token", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Slate700)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Slate100)
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = verifyToken,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Slate900,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                onCopy(verifyToken, "Verify Token")
                                copiedLabel = "Token"
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = if (copiedLabel == "Token") Icons.Default.Check else Icons.Default.ContentCopy,
                                contentDescription = "Copy Token",
                                tint = if (copiedLabel == "Token") Emerald600 else RoyalBlue600,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Secret Key", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Slate700)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Slate100)
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = secret,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Slate900,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                onCopy(secret, "Secret Key")
                                copiedLabel = "Secret"
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = if (copiedLabel == "Secret") Icons.Default.Check else Icons.Default.ContentCopy,
                                contentDescription = "Copy Secret",
                                tint = if (copiedLabel == "Secret") Emerald600 else RoyalBlue600,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IngestionResultBanner(lead: LeadEntity, isDuplicate: Boolean) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isDuplicate) Color(0xFFFEF3C7) else Color(0xFFDCFCE7)
        ),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = if (isDuplicate) Icons.Default.Warning else Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = if (isDuplicate) Color(0xFFB45309) else Color(0xFF15803D)
                )
                Text(
                    text = if (isDuplicate) "🔁 DUPLICATE LEAD DETECTED!" else "🎉 FRESH LEAD INGESTED (ROUND-ROBIN)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = if (isDuplicate) Color(0xFF78350F) else Color(0xFF14532D)
                )
            }

            Text(
                text = if (isDuplicate) {
                    "Customer ${lead.name} (${lead.phoneNumber}) submitted a repeat inquiry. As per rule, lead REMAINS ASSIGNED to original agent ${lead.assignedAgentName}."
                } else {
                    "New lead ${lead.name} (${lead.phoneNumber}) successfully distributed to ${lead.assignedAgentName} via Round-Robin algorithm."
                },
                fontSize = 12.sp,
                color = Slate700
            )
        }
    }
}

@Composable
private fun WebhookLogCard(log: WebhookLogItem) {
    val dateFormat = remember { SimpleDateFormat("hh:mm:ss a", Locale.getDefault()) }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Emerald50)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("${log.method} ${log.statusCode}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Emerald600)
                    }
                    Text(log.path, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Slate700)
                }
                Text(dateFormat.format(Date(log.timestamp)), fontSize = 10.sp, color = Slate400)
            }

            Text(log.summary, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Slate900)

            if (log.leadName != null && log.assignedAgent != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Lead: ${log.leadName}", fontSize = 11.sp, color = Slate600)
                    Text("Assigned: ${log.assignedAgent}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = RoyalBlue600)
                }
            }
        }
    }
}

@Composable
private fun SetupDocumentationSection(
    webhookUrl: String,
    verifyToken: String,
    onCopy: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Webhook Setup & Configuration Guide", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Slate900)

        // Step 1: Meta Ads
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(10.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.Campaign, contentDescription = null, tint = RoyalBlue600)
                    Text("1. Meta Ads Manager (Instant Forms)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Slate900)
                }
                Text("• Open developers.facebook.com ➔ My Apps ➔ Webhooks.", fontSize = 12.sp, color = Slate700)
                Text("• Select 'Page' from the dropdown and click 'Subscribe to this object'.", fontSize = 12.sp, color = Slate700)
                Text("• Enter the Callback URL: $webhookUrl", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Slate900)
                Text("• Enter Verify Token: $verifyToken", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Slate900)
                Text("• Under Page subscriptions, check 'leadgen' and click 'Verify and Save'.", fontSize = 12.sp, color = Slate700)
                Text("• You can test leads using the Meta Lead Ads Testing Tool.", fontSize = 12.sp, color = Emerald600, fontWeight = FontWeight.SemiBold)
            }
        }

        // Step 2: WhatsApp Cloud API
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(10.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.Chat, contentDescription = null, tint = WhatsAppGreen)
                    Text("2. WhatsApp Cloud API", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Slate900)
                }
                Text("• In Meta App Dashboard ➔ WhatsApp ➔ Configuration.", fontSize = 12.sp, color = Slate700)
                Text("• Edit Webhook URL and paste the callback URL.", fontSize = 12.sp, color = Slate700)
                Text("• Under Webhook fields, click 'Manage' and subscribe to 'messages'.", fontSize = 12.sp, color = Slate700)
                Text("• Any customer sending an inbound WhatsApp message will immediately become a lead assigned via Round-Robin.", fontSize = 12.sp, color = Slate700)
            }
        }

        // Step 3: Zapier / Pabbly / Make
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(10.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.SyncAlt, contentDescription = null, tint = Color(0xFFF59E0B))
                    Text("3. Zapier / Pabbly / Make Integrations", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Slate900)
                }
                Text("• Trigger: New Lead in Facebook Lead Ads or Google Sheets.", fontSize = 12.sp, color = Slate700)
                Text("• Action: Webhooks by Zapier (Custom Request or POST).", fontSize = 12.sp, color = Slate700)
                Text("• URL: $webhookUrl", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Slate900)
                Text("• Payload Type: JSON with full_name, phone_number, email, campaign.", fontSize = 12.sp, color = Slate700)
            }
        }
    }
}

// Data class for parsed payload
data class ParsedLeadData(
    val name: String,
    val phone: String,
    val email: String,
    val source: String,
    val campaign: String,
    val message: String,
    val priority: String
)

private fun parseWebhookPayload(jsonString: String): ParsedLeadData? {
    return try {
        val root = JSONObject(jsonString)

        // Case 1: Standard Meta Lead Ads Webhook
        if (root.has("entry")) {
            val entry = root.getJSONArray("entry").getJSONObject(0)
            val changes = entry.getJSONArray("changes").getJSONObject(0)
            val value = changes.getJSONObject("value")

            // Check if it has field_data array
            if (value.has("field_data")) {
                val fieldData = value.getJSONArray("field_data")
                var name = "Meta Lead"
                var phone = ""
                var email = "lead@metalink.com"
                var msg = "Meta Instant Form Submission"

                for (i in 0 until fieldData.length()) {
                    val field = fieldData.getJSONObject(i)
                    val fieldName = field.optString("name", "")
                    val vals = field.optJSONArray("values")
                    val firstVal = if (vals != null && vals.length() > 0) vals.getString(0) else ""

                    when (fieldName.lowercase()) {
                        "full_name", "name", "first_name" -> name = firstVal
                        "phone_number", "phone", "mobile" -> phone = firstVal
                        "email" -> email = firstVal
                        "message", "inquiry" -> msg = firstVal
                    }
                }

                val campaign = value.optString("campaign_name", "Meta Lead Gen Campaign")
                if (phone.isNotBlank()) {
                    return ParsedLeadData(name, phone, email, "Meta Ads", campaign, msg, "HOT")
                }
            }

            // Case 2: WhatsApp Cloud API message webhook
            if (value.has("messages")) {
                val messages = value.getJSONArray("messages")
                val firstMsg = messages.getJSONObject(0)
                val fromPhone = firstMsg.optString("from", "")
                val textObj = firstMsg.optJSONObject("text")
                val textBody = textObj?.optString("body", "WhatsApp Inbound Inquiry") ?: "WhatsApp Inbound Message"

                var contactName = "WhatsApp User $fromPhone"
                if (value.has("contacts")) {
                    val contacts = value.getJSONArray("contacts")
                    if (contacts.length() > 0) {
                        val profile = contacts.getJSONObject(0).optJSONObject("profile")
                        contactName = profile?.optString("name", contactName) ?: contactName
                    }
                }

                if (fromPhone.isNotBlank()) {
                    return ParsedLeadData(
                        name = contactName,
                        phone = "+$fromPhone",
                        email = "wa.$fromPhone@inquiry.com",
                        source = "WhatsApp Direct",
                        campaign = "WhatsApp Cloud Inbound",
                        message = textBody,
                        priority = "HOT"
                    )
                }
            }
        }

        // Case 3: Flat or Zapier/Pabbly payload
        val name = root.optString("full_name", root.optString("name", "Inbound Customer"))
        val phone = root.optString("phone_number", root.optString("phone", root.optString("mobile", "")))
        val email = root.optString("email", "customer@leadpulse.com")
        val source = root.optString("source", "Meta Ads via Zapier")
        val campaign = root.optString("campaign", root.optString("campaign_name", "Digital Inbound Campaign"))
        val message = root.optString("message", "Inbound webhook submission.")
        val priority = root.optString("priority", "HOT")

        if (phone.isNotBlank()) {
            ParsedLeadData(name, phone, email, source, campaign, message, priority)
        } else {
            null
        }
    } catch (e: Exception) {
        null
    }
}

private fun getMetaLeadgenSampleJson(): String {
    val randomId = (100000..999999).random()
    return """
{
  "object": "page",
  "entry": [
    {
      "id": "1049283748291",
      "time": 1725700000,
      "changes": [
        {
          "field": "leadgen",
          "value": {
            "ad_id": "2385192849182",
            "form_id": "948271829471",
            "leadgen_id": "1829472918392",
            "campaign_name": "Meta Lead Gen - High Intent",
            "created_time": 1725700000,
            "field_data": [
              { "name": "full_name", "values": ["Ananya Sharma"] },
              { "name": "phone_number", "values": ["+91 98765 $randomId"] },
              { "name": "email", "values": ["ananya.sharma@gmail.com"] },
              { "name": "message", "values": ["Looking for 3BHK flat in Sector 62"] }
            ]
          }
        }
      ]
    }
  ]
}
    """.trimIndent()
}

private fun getWhatsAppCloudApiSampleJson(): String {
    val randomPhone = "919811" + (100000..999999).random()
    return """
{
  "object": "whatsapp_business_account",
  "entry": [
    {
      "id": "WHATSAPP_BUSINESS_ACCOUNT_ID",
      "changes": [
        {
          "value": {
            "messaging_product": "whatsapp",
            "contacts": [
              {
                "profile": { "name": "Rajesh Gupta" },
                "wa_id": "$randomPhone"
              }
            ],
            "messages": [
              {
                "from": "$randomPhone",
                "id": "wamid.HBgLMTE...",
                "timestamp": "1725700000",
                "text": { "body": "Hi, saw your Facebook ad. Please share brochure and site visit timings." },
                "type": "text"
              }
            ]
          }
        }
      ]
    }
  ]
}
    """.trimIndent()
}

private fun getZapierWebhookSampleJson(): String {
    val randomPhone = "+91 9988" + (100000..999999).random()
    return """
{
  "platform": "Zapier Webhook",
  "full_name": "Karan Malhotra",
  "phone": "$randomPhone",
  "email": "karan.malhotra@yahoo.com",
  "source": "Meta Ads via Zapier",
  "campaign": "Luxury Villa Exclusive",
  "message": "Interested in site visit this weekend."
}
    """.trimIndent()
}
