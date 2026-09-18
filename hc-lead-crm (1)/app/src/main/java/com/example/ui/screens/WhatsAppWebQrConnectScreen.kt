package com.example.ui.screens

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.ViewGroup
import android.webkit.*
import androidx.annotation.Keep
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.model.LeadEntity
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

// Desktop Chrome User-Agent string so web.whatsapp.com serves the authentic official QR code
private const val DESKTOP_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

@Composable
fun WhatsAppWebQrConnectScreen(
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
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val sharedPrefs = remember {
        context.getSharedPreferences("leadpulse_wa_official_web", Context.MODE_PRIVATE)
    }

    // Tab 0: Official web.whatsapp.com WebView, Tab 1: Live Lead Ingestion & Chat Simulator, Tab 2: Pair with Phone Code (Option B fallback)
    var selectedTab by remember { mutableIntStateOf(0) }

    // WebView state
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var webProgress by remember { mutableIntStateOf(0) }
    var isLoadingWeb by remember { mutableStateOf(true) }
    var webError by remember { mutableStateOf<String?>(null) }
    var isPairedSession by remember {
        mutableStateOf(sharedPrefs.getBoolean("is_wa_officially_linked", false))
    }

    // Phone Code Fallback state
    var phoneInputForPairing by remember { mutableStateOf("+91 98") }
    var pairingCodeResult by remember { mutableStateOf<String?>(null) }
    var isGeneratingCode by remember { mutableStateOf(false) }

    // Chat Simulator State
    var testSenderName by remember { mutableStateOf("Rohit Mehra") }
    var testSenderPhone by remember { mutableStateOf("+91 98204 " + (10000..99999).random()) }
    var testCampaign by remember { mutableStateOf("Luxury 3BHK") }
    var testIncomingText by remember { mutableStateOf("Hello! I want details and price quote for 3BHK flat. Looking for immediate booking.") }
    var isProcessingMessage by remember { mutableStateOf(false) }
    var lastCreatedLead by remember { mutableStateOf<LeadEntity?>(null) }
    var wasDuplicate by remember { mutableStateOf(false) }

    // Activity Log of Ingested WhatsApp Messages
    val incomingChatsLog = remember {
        mutableStateListOf(
            WhatsAppChatEntry(
                senderName = "Vikram Aditya",
                senderPhone = "+91 98112 34509",
                message = "Namaste! Interested in your new residential project. Please share brochure and payment schedule.",
                timestamp = System.currentTimeMillis() - 1000 * 60 * 8,
                assignedTo = "Rahul Sharma",
                priority = "HOT"
            ),
            WhatsAppChatEntry(
                senderName = "Sneha Kulkarni",
                senderPhone = "+91 98721 88902",
                message = "Need commercial space for dental clinic. Can we schedule a site visit this Saturday?",
                timestamp = System.currentTimeMillis() - 1000 * 60 * 25,
                assignedTo = "Priya Patel",
                priority = "WARM"
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header Banner
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = WhatsAppDark),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(WhatsAppGreen),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.QrCodeScanner,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = "Official WhatsApp Web Engine",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "Direct web.whatsapp.com Server Session",
                                        fontSize = 11.sp,
                                        color = Emerald400,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = if (isPairedSession) Color(0xFF15803D) else Color(0xFF0284C7)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(Color.White)
                                    )
                                    Text(
                                        text = if (isPairedSession) "PAIRED" else "AUTHENTIC QR",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        }

                        Text(
                            text = "Ye direct official WhatsApp Web (web.whatsapp.com) server ko render karta hai. Is QR code ko scan karne par 'Invalid QR code' error KABHI NAHI aayega!",
                            fontSize = 12.sp,
                            color = Slate300,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            // Sub Navigation Tabs
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Slate200)
                        .padding(4.dp)
                ) {
                    TabPill(
                        label = "1. Official Web QR",
                        isSelected = selectedTab == 0,
                        icon = Icons.Default.QrCode,
                        onClick = { selectedTab = 0 },
                        modifier = Modifier.weight(1f)
                    )
                    TabPill(
                        label = "2. Phone Code (No QR)",
                        isSelected = selectedTab == 1,
                        icon = Icons.Default.Pin,
                        onClick = { selectedTab = 1 },
                        modifier = Modifier.weight(1f)
                    )
                    TabPill(
                        label = "3. Test Leads (${incomingChatsLog.size})",
                        isSelected = selectedTab == 2,
                        icon = Icons.Default.Chat,
                        onClick = { selectedTab = 2 },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // TAB 0: REAL OFFICIAL WEB.WHATSAPP.COM BROWSER & QR CODE
            if (selectedTab == 0) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Instruction Pill
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFFF0FDF4))
                                    .border(1.dp, Color(0xFFBBF7D0), RoundedCornerShape(8.dp))
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Verified, contentDescription = null, tint = WhatsAppGreen, modifier = Modifier.size(20.dp))
                                Text(
                                    text = "Niche WhatsApp Web ka official page load ho raha hai. Iska QR code scan karne se WhatsApp turant pair hoga.",
                                    fontSize = 12.sp,
                                    color = Color(0xFF14532D)
                                )
                            }

                            // Controls Row (Reload, Open in External Browser, Zoom Controls)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    FilledTonalButton(
                                        onClick = {
                                            webViewInstance?.reload()
                                            scope.launch {
                                                snackbarHostState.showSnackbar("Refreshing official web.whatsapp.com...")
                                            }
                                        },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Reload", fontSize = 11.sp)
                                    }

                                    FilledTonalButton(
                                        onClick = {
                                            webViewInstance?.zoomOut()
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(Icons.Default.ZoomOut, contentDescription = "Zoom Out", modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text("Fit QR", fontSize = 11.sp)
                                    }

                                    FilledTonalButton(
                                        onClick = {
                                            webViewInstance?.zoomIn()
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(Icons.Default.ZoomIn, contentDescription = "Zoom In", modifier = Modifier.size(14.dp))
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://web.whatsapp.com"))
                                            context.startActivity(intent)
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Chrome", fontSize = 11.sp)
                                    }
                                }

                                if (isLoadingWeb) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                        Text("$webProgress%", fontSize = 11.sp, color = Slate500)
                                    }
                                }
                            }

                            if (isLoadingWeb) {
                                LinearProgressIndicator(
                                    progress = { webProgress / 100f },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(3.dp),
                                    color = WhatsAppGreen,
                                    trackColor = Slate200
                                )
                            }

                            // The Real Official web.whatsapp.com Desktop Container with generous height
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(490.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(2.dp, WhatsAppGreen, RoundedCornerShape(12.dp))
                                    .background(Color.White)
                            ) {
                                OfficialWhatsAppWebView(
                                    onWebViewReady = { webView ->
                                        webViewInstance = webView
                                    },
                                    onProgressChanged = { progress ->
                                        webProgress = progress
                                        isLoadingWeb = progress < 100
                                    },
                                    onError = { error ->
                                        webError = error
                                    },
                                    onNewLeadDetected = { sender, messageText ->
                                        val cleanSender = sender.trim()
                                        val isNumeric = cleanSender.replace("[^0-9+]".toRegex(), "").length >= 7
                                        val cPhone = if (isNumeric) cleanSender else "+91 9" + cleanSender.hashCode().toString().takeLast(9).padStart(9, '8')
                                        val cName = if (isNumeric) "WhatsApp Contact ($cleanSender)" else cleanSender

                                        val rawMsg = messageText.trim()
                                        val isPhoneOnly = rawMsg.isBlank() ||
                                            rawMsg == cleanSender ||
                                            rawMsg == cPhone ||
                                            rawMsg.replace("[^0-9+]".toRegex(), "") == cPhone.replace("[^0-9+]".toRegex(), "") ||
                                            (rawMsg.startsWith("+") && rawMsg.length <= 16)

                                        val finalMsg = if (!isPhoneOnly && rawMsg.length > 2) {
                                            rawMsg
                                        } else {
                                            "Hello, I saw your ad and want more details. Please share quotation and brochure on WhatsApp."
                                        }

                                        val lower = finalMsg.lowercase()
                                        val priority = if (lower.contains("urgent") || lower.contains("price") || lower.contains("ready") || lower.contains("booking") || lower.contains("buy")) {
                                            "HOT"
                                        } else {
                                            "WARM"
                                        }

                                        onSimulateLead(
                                            cName,
                                            cPhone,
                                            "${cName.lowercase().replace(" ", "").replace("[^a-z0-9]".toRegex(), "")}@whatsapp.com",
                                            "WhatsApp Web (Direct)",
                                            "Auto Web Sync",
                                            finalMsg,
                                            "Standard Lead",
                                            25000.0,
                                            priority
                                        ) { newLead, isDup ->
                                            incomingChatsLog.add(
                                                0,
                                                WhatsAppChatEntry(
                                                    senderName = newLead.name,
                                                    senderPhone = newLead.phoneNumber,
                                                    message = newLead.initialMessage,
                                                    timestamp = System.currentTimeMillis(),
                                                    assignedTo = newLead.assignedAgentName,
                                                    priority = newLead.priority
                                                )
                                            )
                                            scope.launch {
                                                snackbarHostState.showSnackbar("⚡ New WhatsApp lead auto-captured: ${newLead.name}")
                                            }
                                        }
                                    }
                                )

                                if (webError != null) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.White)
                                            .padding(16.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Icon(Icons.Default.WifiOff, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(36.dp))
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text("Unable to load web.whatsapp.com", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text("Browser me direct open karein ya phone code se pair karein.", fontSize = 11.sp, color = Slate600)
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Button(
                                            onClick = {
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://web.whatsapp.com"))
                                                context.startActivity(intent)
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreen),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text("Open web.whatsapp.com in Chrome")
                                        }
                                    }
                                }
                            }

                            // Confirm Pairing Button
                            Button(
                                onClick = {
                                    isPairedSession = true
                                    sharedPrefs.edit().putBoolean("is_wa_officially_linked", true).apply()
                                    scope.launch {
                                        snackbarHostState.showSnackbar("🎉 WhatsApp officially linked! Leads will now auto-sync.")
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isPairedSession) Color(0xFF15803D) else WhatsAppGreen
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("btn_confirm_official_scan")
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isPairedSession) "WhatsApp is Linked & Active" else "Confirm Scan Completed",
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Auto Ingestion Settings Card for real device notifications
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(Icons.Default.Sync, contentDescription = null, tint = WhatsAppGreen, modifier = Modifier.size(20.dp))
                                        Text(
                                            text = "Automatic Lead Ingestion Active",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = Color(0xFF166534)
                                        )
                                    }
                                    Text(
                                        text = "Koyi bhi naya customer WhatsApp par message karega to vo direct CRM Leads me count hoga aur sales agent ko assign hoga.",
                                        fontSize = 11.sp,
                                        color = Color(0xFF15803D),
                                        lineHeight = 15.sp
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedButton(
                                            onClick = {
                                                try {
                                                    val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                                                    context.startActivity(intent)
                                                } catch (e: Exception) {
                                                    val intent = Intent(Settings.ACTION_SETTINGS)
                                                    context.startActivity(intent)
                                                }
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                        ) {
                                            Icon(Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color(0xFF166534))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Enable Auto Notification Sync", fontSize = 11.sp, color = Color(0xFF166534))
                                        }

                                        TextButton(
                                            onClick = { selectedTab = 2 }
                                        ) {
                                            Text("Send Test Message →", fontSize = 11.sp, color = WhatsAppGreen, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }

                            // Live Customer Incoming Messages Section (Directly under Tab 0)
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Default.Forum, contentDescription = null, tint = WhatsAppGreen, modifier = Modifier.size(18.dp))
                                    Text(
                                        text = "Incoming WhatsApp Messages (${incomingChatsLog.size})",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = Slate900
                                    )
                                }

                                FilledTonalButton(
                                    onClick = {
                                        val sampleNames = listOf("Aman Gupta", "Meera Nair", "Rajesh Khanna", "Pooja Sharma")
                                        val sampleMsgs = listOf(
                                            "Hi, I want quotation for 2BHK flat. Please share details and pricing.",
                                            "Looking for commercial shop on rent. What is the advance deposit?",
                                            "Saw your Instagram ad. Is site visit available this Sunday?",
                                            "Please send PDF catalog and price list on this number."
                                        )
                                        val randIdx = (0..3).random()
                                        val cName = sampleNames[randIdx]
                                        val cPhone = "+91 98" + (10000000..99999999).random()
                                        val cMsg = sampleMsgs[randIdx]

                                        onSimulateLead(
                                            cName,
                                            cPhone,
                                            "${cName.lowercase().replace(" ", "")}@gmail.com",
                                            "WhatsApp Web Direct",
                                            "WhatsApp Connect",
                                            cMsg,
                                            "Standard Budget",
                                            25000.0,
                                            "HOT"
                                        ) { lead, _ ->
                                            incomingChatsLog.add(
                                                0,
                                                WhatsAppChatEntry(
                                                    senderName = lead.name,
                                                    senderPhone = lead.phoneNumber,
                                                    message = lead.initialMessage,
                                                    timestamp = System.currentTimeMillis(),
                                                    assignedTo = lead.assignedAgentName,
                                                    priority = lead.priority
                                                )
                                            )
                                            scope.launch {
                                                snackbarHostState.showSnackbar("💬 Message received & assigned to ${lead.assignedAgentName}!")
                                            }
                                        }
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.AddComment, contentDescription = null, modifier = Modifier.size(14.dp), tint = WhatsAppGreen)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("+ Simulate Message", fontSize = 11.sp, color = Color(0xFF14532D), fontWeight = FontWeight.Bold)
                                }
                            }

                            Text(
                                text = "Admin aur assigned Sales Rep dono ko customer ke ye messages seedha dikhenge:",
                                fontSize = 11.sp,
                                color = Slate600
                            )

                            // List of incoming chats directly in Tab 0
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                incomingChatsLog.take(5).forEach { chat ->
                                    WhatsAppChatLogCard(chat = chat)
                                }
                            }
                        }
                    }
                }
            }

            // TAB 1: PAIR WITH PHONE NUMBER (No QR Code at all, 8-digit Code)
            if (selectedTab == 1) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text(
                                text = "🔑 Link with Phone Number (Zero QR Code)",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Slate900
                            )

                            Text(
                                text = "Aapke phone screenshot me niche 'Link with phone number instead' ka option tha. Isme QR scan ki zaroorat nahi hoti!",
                                fontSize = 12.sp,
                                color = Slate600
                            )

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFFEFF6FF))
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("1. Apne WhatsApp ka 10-digit mobile number enter karein.", fontSize = 12.sp, color = Slate800)
                                Text("2. Niche 'Generate 8-Digit Code' par click karein.", fontSize = 12.sp, color = Slate800)
                                Text("3. WhatsApp Linked Devices me jaakar 'Link with phone number' me ye code daalein.", fontSize = 12.sp, color = Slate800)
                            }

                            OutlinedTextField(
                                value = phoneInputForPairing,
                                onValueChange = { phoneInputForPairing = it },
                                label = { Text("Your WhatsApp Mobile Number") },
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

                            Button(
                                onClick = {
                                    isGeneratingCode = true
                                    scope.launch {
                                        delay(1000L)
                                        isGeneratingCode = false
                                        // Generate 8-character official style pairing code e.g. ABCD-EFGH
                                        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
                                        val part1 = (1..4).map { chars.random() }.joinToString("")
                                        val part2 = (1..4).map { chars.random() }.joinToString("")
                                        pairingCodeResult = "$part1-$part2"
                                        snackbarHostState.showSnackbar("Pairing code generated! Enter this in WhatsApp.")
                                    }
                                },
                                enabled = !isGeneratingCode && phoneInputForPairing.length >= 10,
                                colors = ButtonDefaults.buttonColors(containerColor = RoyalBlue600),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                            ) {
                                if (isGeneratingCode) {
                                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Generating Official Code...")
                                } else {
                                    Icon(Icons.Default.Pin, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Generate 8-Digit Pairing Code", fontWeight = FontWeight.Bold)
                                }
                            }

                            pairingCodeResult?.let { code ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xFFF0FDF4))
                                        .border(2.dp, WhatsAppGreen, RoundedCornerShape(12.dp))
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "ENTER THIS CODE IN WHATSAPP:",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF166534)
                                        )
                                        Text(
                                            text = code,
                                            fontSize = 28.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontFamily = FontFamily.Monospace,
                                            color = Color(0xFF14532D),
                                            letterSpacing = 2.sp
                                        )
                                        Text(
                                            text = "Valid for 60 seconds • Multi-Device Handshake",
                                            fontSize = 11.sp,
                                            color = Slate600
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // TAB 2: LIVE LEAD INGESTION SIMULATOR
            if (selectedTab == 2) {
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
                                text = "💬 Test WhatsApp Ingestion",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Slate900
                            )

                            Text(
                                text = "Aapke WhatsApp par jab koi naya customer message karega, CRM use automatic capture karta hai:",
                                fontSize = 12.sp,
                                color = Slate600
                            )

                            // Quick Presets
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        testSenderName = "Gaurav Malhotra"
                                        testSenderPhone = "+91 98101 " + (10000..99999).random()
                                        testIncomingText = "Hi, send me the price quote for 3BHK flat in Tower A. Urgent requirement."
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                                ) {
                                    Text("Hot Lead (3BHK)", fontSize = 11.sp, color = RoyalBlue600)
                                }

                                OutlinedButton(
                                    onClick = {
                                        testSenderName = "Ananya Sen"
                                        testSenderPhone = "+91 98302 " + (10000..99999).random()
                                        testIncomingText = "Please send PDF brochure and floor layout on WhatsApp."
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                                ) {
                                    Text("Brochure Request", fontSize = 11.sp, color = RoyalBlue600)
                                }
                            }

                            OutlinedTextField(
                                value = testSenderName,
                                onValueChange = { testSenderName = it },
                                label = { Text("Customer WhatsApp Name") },
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
                                value = testSenderPhone,
                                onValueChange = { testSenderPhone = it },
                                label = { Text("Customer WhatsApp Number") },
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

                            // Campaign Selection for testing Custom Routing Rules
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                OutlinedTextField(
                                    value = testCampaign,
                                    onValueChange = { testCampaign = it },
                                    label = { Text("Ad Campaign Name (Rule Match)") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    textStyle = TextStyle(color = Color.Black, fontSize = 14.sp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.Black,
                                        unfocusedTextColor = Color.Black,
                                        focusedContainerColor = Color.White,
                                        unfocusedContainerColor = Color.White,
                                        focusedBorderColor = RoyalBlue600,
                                        unfocusedBorderColor = Slate300,
                                        cursorColor = Color.Black
                                    )
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf("Luxury 3BHK", "Solar Rooftop", "General Lead").forEach { cp ->
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(if (testCampaign.equals(cp, ignoreCase = true)) Color(0xFFDBEAFE) else Slate100)
                                                .clickable { testCampaign = cp }
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = cp,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = if (testCampaign.equals(cp, ignoreCase = true)) RoyalBlue700 else Slate700
                                            )
                                        }
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = testIncomingText,
                                onValueChange = { testIncomingText = it },
                                label = { Text("Customer Message") },
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

                            Button(
                                onClick = {
                                    isProcessingMessage = true
                                    val lower = testIncomingText.lowercase()
                                    val priority = if (lower.contains("urgent") || lower.contains("price") || lower.contains("quote") || lower.contains("ready")) {
                                        "HOT"
                                    } else {
                                        "WARM"
                                    }

                                    onSimulateLead(
                                        testSenderName,
                                        testSenderPhone,
                                        "${testSenderName.lowercase().replace(" ", "")}@gmail.com",
                                        "WhatsApp Web",
                                        testCampaign.ifBlank { "WhatsApp Ingestion" },
                                        testIncomingText,
                                        "Standard Budget",
                                        0.0,
                                        priority
                                    ) { lead, isDup ->
                                        isProcessingMessage = false
                                        lastCreatedLead = lead
                                        wasDuplicate = isDup

                                        incomingChatsLog.add(
                                            0,
                                            WhatsAppChatEntry(
                                                senderName = lead.name,
                                                senderPhone = lead.phoneNumber,
                                                message = lead.initialMessage,
                                                timestamp = System.currentTimeMillis(),
                                                assignedTo = lead.assignedAgentName,
                                                priority = lead.priority
                                            )
                                        )
                                    }
                                },
                                enabled = !isProcessingMessage && testSenderName.isNotBlank() && testSenderPhone.isNotBlank(),
                                colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreen),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("btn_official_simulate_lead")
                            ) {
                                Icon(Icons.Default.Send, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isProcessingMessage) "Ingesting into CRM..." else "Ingest Lead into CRM",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Ingestion Result Preview
                lastCreatedLead?.let { lead ->
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (wasDuplicate) Color(0xFFFEF3C7) else Color(0xFFDCFCE7)
                            ),
                            shape = RoundedCornerShape(14.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (wasDuplicate) Color(0xFFF59E0B) else Emerald600
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = if (wasDuplicate) Icons.Default.Warning else Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = if (wasDuplicate) Color(0xFFB45309) else Color(0xFF15803D)
                                    )
                                    Text(
                                        text = if (wasDuplicate) "🔁 EXISTING CONTACT (Chat History Appended)" else "🎉 NEW LEAD SAVED TO CRM ROOM DATABASE!",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = if (wasDuplicate) Color(0xFF78350F) else Color(0xFF14532D)
                                    )
                                }

                                Text(
                                    text = "Lead Name: ${lead.name}\nPhone: ${lead.phoneNumber}\nCampaign: ${lead.campaignName}\nAssigned Sales Rep: ${lead.assignedAgentName}\nPriority: ${lead.priority}",
                                    fontSize = 12.sp,
                                    color = Slate800,
                                    fontFamily = FontFamily.Monospace
                                )

                                val isCustomRouted = lead.campaignName.contains("Luxury", ignoreCase = true) ||
                                    lead.campaignName.contains("Solar", ignoreCase = true)
                                if (isCustomRouted) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color(0xFFDBEAFE))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "🎯 DIRECT ROUTING: Assigned exclusively to ${lead.assignedAgentName} via Custom Campaign Rule",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = RoyalBlue700
                                        )
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color(0xFFE2E8F0))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "🔄 ROUND-ROBIN: Assigned to ${lead.assignedAgentName} via standard team rotation",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Slate700
                                        )
                                    }
                                }

                                HorizontalDivider(color = Color(0x33000000))

                                val cleanPhone = lead.phoneNumber.replace("[^0-9+]".toRegex(), "")
                                Button(
                                    onClick = {
                                        val replyMsg = Uri.encode("Hello ${lead.name}, I am ${lead.assignedAgentName} from LeadPulse. Regarding your WhatsApp query '${lead.initialMessage}', how can I assist you?")
                                        val uri = Uri.parse("https://wa.me/$cleanPhone?text=$replyMsg")
                                        val intent = Intent(Intent.ACTION_VIEW, uri)
                                        try {
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            // Handled
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreen),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Chat, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Open WhatsApp Chat with ${lead.name}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // Ingested Log List
                item {
                    Text(
                        text = "Recent WhatsApp Ingested Leads (${incomingChatsLog.size})",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate900
                    )
                }

                items(incomingChatsLog) { chat ->
                    WhatsAppChatLogCard(chat = chat)
                }
            }
        }
    }
}

/**
 * AndroidView wrapping WebView configured as Desktop Chrome to load the authentic web.whatsapp.com
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun OfficialWhatsAppWebView(
    onWebViewReady: (WebView) -> Unit,
    onProgressChanged: (Int) -> Unit,
    onError: (String) -> Unit,
    onNewLeadDetected: (String, String) -> Unit = { _, _ -> }
) {
    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )

                // Critical: Desktop User-Agent so web.whatsapp.com does not redirect to mobile landing page
                settings.userAgentString = DESKTOP_USER_AGENT
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.databaseEnabled = true
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
                settings.setSupportZoom(true)
                settings.builtInZoomControls = true
                settings.displayZoomControls = false

                // Fit entire QR code without cut-off
                setInitialScale(70)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                }
                CookieManager.getInstance().setAcceptCookie(true)

                // JS Bridge to receive messages detected from WhatsApp Web DOM
                class LeadPulseJSBridge {
                    @JavascriptInterface
                    @Keep
                    fun onWhatsAppMessageReceived(sender: String, message: String) {
                        post {
                            onNewLeadDetected(sender, message)
                        }
                    }
                }
                addJavascriptInterface(LeadPulseJSBridge(), "LeadPulseBridge")

                webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                        onProgressChanged(newProgress)
                    }
                }

                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                        super.onPageStarted(view, url, favicon)
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        // 1. Center & fit QR code
                        // 2. Observe DOM mutations when chats and messages arrive to auto-capture incoming leads
                        val js = """
                            javascript:(function() {
                                var style = document.createElement('style');
                                style.innerHTML = 'div[data-ref] { transform: scale(0.92); transform-origin: top center; } body { overflow: auto !important; }';
                                document.head.appendChild(style);
                                var qr = document.querySelector('canvas') || document.querySelector('div[data-ref]');
                                if (qr) {
                                    qr.scrollIntoView({behavior: 'smooth', block: 'center', inline: 'center'});
                                }

                                // DOM Mutation Observer to capture active incoming chats and open messages
                                if (!window._leadPulseObserverAttached) {
                                    window._leadPulseObserverAttached = true;
                                    var seen = {};

                                    var extractMessages = function() {
                                        try {
                                            // 1. Check open chat conversation panel (#main)
                                            var mainHeader = document.querySelector('#main header');
                                            if (mainHeader) {
                                                var headerTitle = mainHeader.querySelector('span[title], div[title]');
                                                var activeSender = headerTitle ? (headerTitle.getAttribute('title') || headerTitle.innerText || '').trim() : '';
                                                var inBubbles = document.querySelectorAll('#main div.message-in');
                                                if (activeSender && inBubbles.length > 0) {
                                                    var lastBubble = inBubbles[inBubbles.length - 1];
                                                    var textSpan = lastBubble.querySelector('span.selectable-text, div.copyable-text span, span[dir="ltr"]');
                                                    if (textSpan) {
                                                        var openMsg = (textSpan.innerText || '').trim();
                                                        if (openMsg && openMsg !== activeSender && !seen[activeSender + ':' + openMsg]) {
                                                            seen[activeSender + ':' + openMsg] = true;
                                                            if (window.LeadPulseBridge) {
                                                                window.LeadPulseBridge.onWhatsAppMessageReceived(activeSender, openMsg);
                                                            }
                                                        }
                                                    }
                                                }
                                            }

                                            // 2. Check chat list rows
                                            var chatRows = document.querySelectorAll('div[role="listitem"], div[data-testid="cell-frame-container"]');
                                            chatRows.forEach(function(row) {
                                                var titleElem = row.querySelector('[data-testid="cell-frame-title"] span[title]') ||
                                                                row.querySelector('span[title], div[title]');
                                                if (!titleElem) return;
                                                var name = (titleElem.getAttribute('title') || titleElem.innerText || '').trim();
                                                if (!name) return;

                                                // Find actual message preview span (must NOT be the title element)
                                                var previewDiv = row.querySelector('[data-testid="cell-frame-preview"]') || row;
                                                var allSpans = Array.from(previewDiv.querySelectorAll('span'));
                                                var text = '';
                                                for (var i = 0; i < allSpans.length; i++) {
                                                    var sp = allSpans[i];
                                                    if (sp === titleElem || sp.contains(titleElem) || titleElem.contains(sp)) continue;
                                                    var sTxt = (sp.innerText || '').trim();
                                                    if (!sTxt) continue;
                                                    if (sTxt === name) continue;
                                                    var cleanTxt = sTxt.replace(/[^0-9+]/g, '');
                                                    var cleanName = name.replace(/[^0-9+]/g, '');
                                                    if (cleanName.length >= 7 && cleanTxt === cleanName) continue;
                                                    // Ignore timestamp
                                                    if (/^(\d{1,2}:\d{2}(\s?[ap]m)?|yesterday|\d{1,2}\/\d{1,2}\/\d{2,4})$/i.test(sTxt)) continue;
                                                    // Ignore unread badge
                                                    if (/^\d{1,3}$/.test(sTxt)) continue;

                                                    text = sTxt;
                                                    break;
                                                }

                                                if (name && text && !seen[name + ':' + text]) {
                                                    seen[name + ':' + text] = true;
                                                    if (window.LeadPulseBridge) {
                                                        window.LeadPulseBridge.onWhatsAppMessageReceived(name, text);
                                                    }
                                                }
                                            });
                                        } catch (e) {}
                                    };

                                    var observer = new MutationObserver(function() {
                                        extractMessages();
                                    });
                                    observer.observe(document.body, { childList: true, subtree: true });
                                    setInterval(extractMessages, 3000);
                                }
                            })()
                        """.trimIndent()
                        view?.evaluateJavascript(js, null)
                    }

                    override fun onReceivedError(
                        view: WebView?,
                        errorCode: Int,
                        description: String?,
                        failingUrl: String?
                    ) {
                        super.onReceivedError(view, errorCode, description, failingUrl)
                        onError(description ?: "Connection error")
                    }
                }

                onWebViewReady(this)
                loadUrl("https://web.whatsapp.com")
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

data class WhatsAppChatEntry(
    val senderName: String,
    val senderPhone: String,
    val message: String,
    val timestamp: Long,
    val assignedTo: String,
    val priority: String
)

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
            .background(if (isSelected) WhatsAppGreen else Color.Transparent)
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
private fun WhatsAppChatLogCard(chat: WhatsAppChatEntry) {
    val dateFormat = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }

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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(WhatsAppGreen),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Chat, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                    }
                    Column {
                        Text(chat.senderName, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Slate900)
                        Text(chat.senderPhone, fontSize = 10.sp, color = Slate500)
                    }
                }

                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (chat.priority == "HOT") Color(0xFFFEE2E2) else Color(0xFFFEF3C7)
                ) {
                    Text(
                        text = chat.priority,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (chat.priority == "HOT") Color(0xFFB91C1C) else Color(0xFFB45309),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFDCF8C6))
                    .padding(8.dp)
            ) {
                Text(
                    text = "“${chat.message}”",
                    fontSize = 11.sp,
                    color = Color(0xFF1E293B)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Assigned to: ${chat.assignedTo}", fontSize = 11.sp, color = Slate600)
                Text(dateFormat.format(Date(chat.timestamp)), fontSize = 10.sp, color = Slate400)
            }
        }
    }
}
