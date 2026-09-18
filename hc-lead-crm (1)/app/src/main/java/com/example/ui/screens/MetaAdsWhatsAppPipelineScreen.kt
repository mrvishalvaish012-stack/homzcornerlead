package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.LeadEntity
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun MetaAdsWhatsAppPipelineScreen(
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
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Preferences for Admin WhatsApp Alert Number
    val sharedPrefs = remember {
        context.getSharedPreferences("leadpulse_pipeline_prefs", Context.MODE_PRIVATE)
    }

    var adminWhatsAppNumber by remember {
        mutableStateOf(sharedPrefs.getString("admin_wa_number", "+91 98765 43210") ?: "+91 98765 43210")
    }
    var isEditingWaNumber by remember { mutableStateOf(false) }

    // Screen Sub-Tabs: 0 = Pipeline Architecture & Test, 1 = Webhook Setup & Credentials, 2 = Activity Logs
    var selectedTab by remember { mutableIntStateOf(0) }

    // Test Simulator Form State
    var customerName by remember { mutableStateOf("Rahul Verma") }
    var customerPhone by remember { mutableStateOf("+91 98112 " + (10000..99999).random()) }
    var customerEmail by remember { mutableStateOf("rahul.verma@gmail.com") }
    var campaignName by remember { mutableStateOf("Meta Ads - Luxury 3BHK Flats") }
    var customerNote by remember { mutableStateOf("Interested in weekend site visit & price quote.") }
    var priority by remember { mutableStateOf("HOT") }

    // Result state of latest ingested lead
    var latestIngestedLead by remember { mutableStateOf<LeadEntity?>(null) }
    var latestIsDuplicate by remember { mutableStateOf(false) }
    var isSimulating by remember { mutableStateOf(false) }

    // Webhook constants
    val callbackUrl = "https://meta-leads-webhook.deno.dev/webhook"
    val verifyToken = "leadpulse_meta_verify_2026"

    val pipelineLogs = remember {
        mutableStateListOf(
            PipelineEvent(
                timestamp = System.currentTimeMillis() - 1000 * 60 * 14,
                source = "Meta Ads (Instagram Form)",
                campaign = "Luxury 3BHK Flats - Sector 62",
                leadName = "Vikas Sharma",
                assignedAgent = "Rahul Sharma",
                whatsappAlertDispatched = true
            ),
            PipelineEvent(
                timestamp = System.currentTimeMillis() - 1000 * 60 * 45,
                source = "Click-to-WhatsApp Ad",
                campaign = "Villa Project Launch",
                leadName = "Sonia Kapoor",
                assignedAgent = "Priya Patel",
                whatsappAlertDispatched = true
            )
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Slate50,
        modifier = modifier.fillMaxSize()
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header: Meta Ads to WhatsApp Pipeline Banner
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Slate900),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(WhatsAppGreen),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SyncAlt,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "Meta Ads ➔ WhatsApp Pipeline",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = "Automated Live Ingestion & WhatsApp Alert Dispatch",
                                    fontSize = 12.sp,
                                    color = Emerald400,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        Text(
                            text = "Jab bhi koi Facebook ya Instagram Ad se lead aati hai, wo turant CRM database mein save hoti hai aur aapke WhatsApp par instant lead alert notification bheji jaati hai.",
                            fontSize = 12.sp,
                            color = Slate300,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            // Tab Selector: [Pipeline & Test], [Webhook Setup], [Live Logs]
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Slate200)
                        .padding(4.dp)
                ) {
                    TabPill(
                        label = "Pipeline & Test",
                        isSelected = selectedTab == 0,
                        icon = Icons.Default.PlayArrow,
                        onClick = { selectedTab = 0 },
                        modifier = Modifier.weight(1f)
                    )
                    TabPill(
                        label = "Meta Setup",
                        isSelected = selectedTab == 1,
                        icon = Icons.Default.Settings,
                        onClick = { selectedTab = 1 },
                        modifier = Modifier.weight(1f)
                    )
                    TabPill(
                        label = "Activity (${pipelineLogs.size})",
                        isSelected = selectedTab == 2,
                        icon = Icons.Default.List,
                        onClick = { selectedTab = 2 },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // TAB 0: Pipeline Architecture, WhatsApp Alert Receiver & Simulator
            if (selectedTab == 0) {
                // 1. Visual 4-Step Pipeline Architecture Card
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(14.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Live Pipeline Architecture Flow",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Slate900
                            )

                            PipelineStepRow(
                                stepNumber = "1",
                                title = "Meta Ads (Instagram & Facebook)",
                                subtitle = "Customer fills Instant Form or clicks WhatsApp Ad",
                                icon = Icons.Default.Campaign,
                                iconColor = RoyalBlue600,
                                isCompleted = true
                            )

                            PipelineConnector()

                            PipelineStepRow(
                                stepNumber = "2",
                                title = "Real-Time Webhook Bridge",
                                subtitle = "Graph API forwards lead payload in < 1 second",
                                icon = Icons.Default.Webhook,
                                iconColor = Color(0xFF8B5CF6),
                                isCompleted = true
                            )

                            PipelineConnector()

                            PipelineStepRow(
                                stepNumber = "3",
                                title = "LeadPulse CRM Room DB (Auto-Save)",
                                subtitle = "Saved locally, duplicate phone checked, Round-Robin assigned",
                                icon = Icons.Default.Storage,
                                iconColor = Emerald600,
                                isCompleted = true
                            )

                            PipelineConnector()

                            PipelineStepRow(
                                stepNumber = "4",
                                title = "Instant WhatsApp Alert Dispatched",
                                subtitle = "Lead details & 1-tap chat sent to: $adminWhatsAppNumber",
                                icon = Icons.Default.Chat,
                                iconColor = WhatsAppGreen,
                                isCompleted = true
                            )
                        }
                    }
                }

                // 2. WhatsApp Alert Receiver Number Configuration
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4)),
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFBBF7D0))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PhoneAndroid,
                                        contentDescription = null,
                                        tint = WhatsAppGreen,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = "My WhatsApp Alert Number",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF14532D)
                                    )
                                }

                                TextButton(
                                    onClick = {
                                        if (isEditingWaNumber) {
                                            sharedPrefs.edit().putString("admin_wa_number", adminWhatsAppNumber).apply()
                                        }
                                        isEditingWaNumber = !isEditingWaNumber
                                    }
                                ) {
                                    Text(
                                        text = if (isEditingWaNumber) "Save" else "Edit",
                                        fontWeight = FontWeight.Bold,
                                        color = Emerald600
                                    )
                                }
                            }

                            if (isEditingWaNumber) {
                                OutlinedTextField(
                                    value = adminWhatsAppNumber,
                                    onValueChange = { adminWhatsAppNumber = it },
                                    label = { Text("Your WhatsApp Number (with country code)") },
                                    placeholder = { Text("+91 98765 43210", color = Slate400) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
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
                            } else {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.White)
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = adminWhatsAppNumber,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        color = Slate900
                                    )

                                    Text(
                                        text = "Active Alert Receiver",
                                        fontSize = 11.sp,
                                        color = Emerald600,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            Text(
                                text = "Meta Ads se aane wali har lead ka alert is WhatsApp number par automatic notify kiya jayega.",
                                fontSize = 11.sp,
                                color = Color(0xFF166534)
                            )
                        }
                    }
                }

                // 3. Test & Ingestion Simulator Section
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(14.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "⚡ Test Live Meta Ad ➔ Auto-Save CRM ➔ WhatsApp Dispatch",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Slate900
                            )

                            Text(
                                text = "Ek sample Meta Ad lead trigger karke dekhein ki wo yahan kaise save hoti hai aur WhatsApp par alert kaise aata hai:",
                                fontSize = 12.sp,
                                color = Slate600
                            )

                            // Quick Preset Buttons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        customerName = "Ananya Sharma"
                                        customerPhone = "+91 98200 " + (10000..99999).random()
                                        campaignName = "Instagram - Luxury 3BHK Flats"
                                        customerNote = "Interested in 3BHK flat in Sector 62. Looking for immediate booking."
                                        priority = "HOT"
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
                                ) {
                                    Text("Real Estate Lead", fontSize = 11.sp, color = RoyalBlue600)
                                }

                                OutlinedButton(
                                    onClick = {
                                        customerName = "Karan Malhotra"
                                        customerPhone = "+91 98110 " + (10000..99999).random()
                                        campaignName = "FB Ads - Solar Rooftop Subsidy"
                                        customerNote = "Want 5KW residential solar setup with govt subsidy scheme."
                                        priority = "HOT"
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
                                ) {
                                    Text("Solar Lead", fontSize = 11.sp, color = RoyalBlue600)
                                }
                            }

                            OutlinedTextField(
                                value = customerName,
                                onValueChange = { customerName = it },
                                label = { Text("Customer Name (from Meta Form)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
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
                                value = customerPhone,
                                onValueChange = { customerPhone = it },
                                label = { Text("Customer Mobile Number") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
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
                                value = campaignName,
                                onValueChange = { campaignName = it },
                                label = { Text("Meta Ad Campaign Name") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
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
                                value = customerNote,
                                onValueChange = { customerNote = it },
                                label = { Text("Customer Message / Requirement") },
                                singleLine = false,
                                maxLines = 3,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
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

                            // Action: Run Pipeline Test
                            Button(
                                onClick = {
                                    isSimulating = true
                                    onSimulateLead(
                                        customerName,
                                        customerPhone,
                                        customerEmail,
                                        "Meta Ads",
                                        campaignName,
                                        customerNote,
                                        "Standard Budget",
                                        0.0,
                                        priority
                                    ) { lead, wasDup ->
                                        isSimulating = false
                                        latestIngestedLead = lead
                                        latestIsDuplicate = wasDup

                                        pipelineLogs.add(
                                            0,
                                            PipelineEvent(
                                                timestamp = System.currentTimeMillis(),
                                                source = "Meta Ads (Simulated Test)",
                                                campaign = lead.campaignName,
                                                leadName = lead.name,
                                                assignedAgent = lead.assignedAgentName,
                                                whatsappAlertDispatched = true
                                            )
                                        )
                                    }
                                },
                                enabled = !isSimulating && customerName.isNotBlank() && customerPhone.isNotBlank(),
                                colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreen),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("btn_run_meta_pipeline_test")
                            ) {
                                Icon(Icons.Default.CloudUpload, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isSimulating) "Connecting & Ingesting..." else "Save Lead to CRM & Trigger WhatsApp Alert",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }

                // 4. Ingestion Result & Direct WhatsApp Dispatch Actions
                latestIngestedLead?.let { lead ->
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (latestIsDuplicate) Color(0xFFFEF3C7) else Color(0xFFDCFCE7)
                            ),
                            shape = RoundedCornerShape(14.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (latestIsDuplicate) Color(0xFFF59E0B) else Emerald600
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = if (latestIsDuplicate) Icons.Default.Warning else Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = if (latestIsDuplicate) Color(0xFFB45309) else Color(0xFF15803D)
                                    )
                                    Text(
                                        text = if (latestIsDuplicate) "🔁 DUPLICATE LEAD (Retained with ${lead.assignedAgentName})" else "🎉 LEAD SAVED & DISTRIBUTED IN CRM!",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = if (latestIsDuplicate) Color(0xFF78350F) else Color(0xFF14532D)
                                    )
                                }

                                Text(
                                    text = "Lead: ${lead.name} (${lead.phoneNumber})\nAssigned Rep: ${lead.assignedAgentName}\nCampaign: ${lead.campaignName}",
                                    fontSize = 12.sp,
                                    color = Slate800,
                                    fontFamily = FontFamily.Monospace
                                )

                                HorizontalDivider(color = Color(0x33000000))

                                Text(
                                    text = "👉 WhatsApp Actions for this Lead:",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = Slate900
                                )

                                // WhatsApp Action 1: Send Alert to Admin's / Owner's WhatsApp
                                val cleanCustomerPhone = lead.phoneNumber.replace("[^0-9+]".toRegex(), "")
                                val alertMessage = """
🚨 *NEW META ADS LEAD INGESTED!*
━━━━━━━━━━━━━━━━━━━━
👤 *Customer:* ${lead.name}
📞 *Phone:* ${lead.phoneNumber}
🎯 *Campaign:* ${lead.campaignName}
💬 *Message:* ${lead.initialMessage}
⚡ *Assigned Rep:* ${lead.assignedAgentName}
━━━━━━━━━━━━━━━━━━━━
👉 *Chat on WhatsApp:* https://wa.me/$cleanCustomerPhone
                                """.trimIndent()

                                Button(
                                    onClick = {
                                        val cleanAdminPhone = adminWhatsAppNumber.replace("[^0-9+]".toRegex(), "")
                                        val encoded = Uri.encode(alertMessage)
                                        val uri = Uri.parse("https://wa.me/$cleanAdminPhone?text=$encoded")
                                        val intent = Intent(Intent.ACTION_VIEW, uri)
                                        try {
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            // Fallback
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreen),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Send Alert to My WhatsApp ($adminWhatsAppNumber)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }

                                // WhatsApp Action 2: Direct Chat with the Customer
                                OutlinedButton(
                                    onClick = {
                                        val introMsg = Uri.encode("Hello ${lead.name}, thank you for your inquiry on ${lead.campaignName}. How can we assist you?")
                                        val uri = Uri.parse("https://wa.me/$cleanCustomerPhone?text=$introMsg")
                                        val intent = Intent(Intent.ACTION_VIEW, uri)
                                        try {
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            // Fallback
                                        }
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Chat, contentDescription = null, modifier = Modifier.size(16.dp), tint = WhatsAppGreen)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Direct 1-Tap Chat with Customer (${lead.name})", fontSize = 12.sp, color = Emerald600, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // TAB 1: Meta Developers Setup Guide & Webhook Credentials
            if (selectedTab == 1) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(14.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Meta Ads Direct Webhook Credentials",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Slate900
                            )

                            // Callback URL
                            CredentialRow(
                                title = "Callback Webhook URL (Meta Graph API)",
                                value = callbackUrl,
                                onCopy = {
                                    clipboardManager.setText(AnnotatedString(callbackUrl))
                                }
                            )

                            // Verify Token
                            CredentialRow(
                                title = "Verify Token",
                                value = verifyToken,
                                onCopy = {
                                    clipboardManager.setText(AnnotatedString(verifyToken))
                                }
                            )
                        }
                    }
                }

                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(14.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "How to Connect Meta Ads Manager (Step-by-Step)",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Slate900
                            )

                            SetupStepItem(
                                step = "1",
                                title = "Open Meta for Developers",
                                description = "Go to developers.facebook.com ➔ My Apps ➔ Webhooks ➔ Select 'Page' object."
                            )

                            SetupStepItem(
                                step = "2",
                                title = "Add Callback URL & Verify Token",
                                description = "Paste the Callback URL and Verify Token from above and click 'Verify and Save'."
                            )

                            SetupStepItem(
                                step = "3",
                                title = "Subscribe to 'leadgen' and 'messages'",
                                description = "Check 'leadgen' (for Meta Instant Forms) and 'messages' (for Click-to-WhatsApp ads)."
                            )

                            SetupStepItem(
                                step = "4",
                                title = "Test Ingestion",
                                description = "Use the Meta 'Lead Ads Testing Tool' to submit a test lead. It will immediately appear in your CRM and trigger a WhatsApp alert!"
                            )
                        }
                    }
                }
            }

            // TAB 2: Activity Logs
            if (selectedTab == 2) {
                item {
                    Text(
                        text = "Real-time Ingestion Event Log (${pipelineLogs.size})",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate900
                    )
                }

                items(pipelineLogs) { event ->
                    PipelineLogCard(event = event)
                }
            }
        }
    }
}

data class PipelineEvent(
    val timestamp: Long,
    val source: String,
    val campaign: String,
    val leadName: String,
    val assignedAgent: String,
    val whatsappAlertDispatched: Boolean
)

@Composable
private fun PipelineStepRow(
    stepNumber: String,
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    isCompleted: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(iconColor.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(20.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Slate900)
            Text(text = subtitle, fontSize = 11.sp, color = Slate600)
        }

        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = "Active",
            tint = Emerald600,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun PipelineConnector() {
    Box(
        modifier = Modifier
            .padding(start = 17.dp)
            .height(14.dp)
            .width(2.dp)
            .background(Slate300)
    )
}

@Composable
private fun TabPill(
    label: String,
    isSelected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) Emerald600 else Color.Transparent)
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) Color.White else Slate700,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) Color.White else Slate700
            )
        }
    }
}

@Composable
private fun CredentialRow(
    title: String,
    value: String,
    onCopy: () -> Unit
) {
    var copied by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Slate700)
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
                text = value,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = Slate900,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = {
                    onCopy()
                    copied = true
                },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                    contentDescription = "Copy",
                    tint = if (copied) Emerald600 else RoyalBlue600,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun SetupStepItem(step: String, title: String, description: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(RoyalBlue600),
            contentAlignment = Alignment.Center
        ) {
            Text(step, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Slate900)
            Text(description, fontSize = 11.sp, color = Slate600, lineHeight = 15.sp)
        }
    }
}

@Composable
private fun PipelineLogCard(event: PipelineEvent) {
    val dateFormat = remember { SimpleDateFormat("hh:mm:ss a", Locale.getDefault()) }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(10.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = Color(0xFFEFF6FF),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        event.source,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = RoyalBlue600,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Text(dateFormat.format(Date(event.timestamp)), fontSize = 10.sp, color = Slate400)
            }

            Text(
                text = "${event.leadName} • ${event.campaign}",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Slate900
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Assigned to: ${event.assignedAgent}", fontSize = 11.sp, color = Slate600)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = WhatsAppGreen, modifier = Modifier.size(12.dp))
                    Text("WhatsApp Alert Sent", fontSize = 10.sp, color = WhatsAppGreen, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
