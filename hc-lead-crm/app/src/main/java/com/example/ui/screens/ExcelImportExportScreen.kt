package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.LeadEntity
import com.example.data.model.SalesAgentEntity
import com.example.data.repository.BulkImportResult
import com.example.data.repository.RawLeadInput
import com.example.ui.theme.*
import java.io.BufferedReader
import java.io.InputStreamReader

@Composable
fun ExcelImportExportScreen(
    allLeads: List<LeadEntity>,
    activeAgents: List<SalesAgentEntity>,
    onImportLeads: (List<RawLeadInput>, (BulkImportResult) -> Unit) -> Unit,
    onExportAllCsv: () -> String,
    onBackClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedSection by remember { mutableIntStateOf(0) } // 0: Import & Distribute, 1: Extract & Export

    // Import State
    var rawTextData by remember { mutableStateOf("") }
    var parsedLeads by remember { mutableStateOf<List<RawLeadInput>>(emptyList()) }
    var parseError by remember { mutableStateOf<String?>(null) }
    var isImporting by remember { mutableStateOf(false) }
    var lastImportResult by remember { mutableStateOf<BulkImportResult?>(null) }

    // CSV file picker launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val reader = BufferedReader(InputStreamReader(inputStream))
                val content = reader.readText()
                reader.close()
                rawTextData = content
                parseInputData(content) { list, err ->
                    parsedLeads = list
                    parseError = err
                }
                Toast.makeText(context, "Loaded CSV file successfully", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                parseError = "Error reading file: ${e.message}"
            }
        }
    }

    // Parse whenever rawTextData changes
    LaunchedEffect(rawTextData) {
        if (rawTextData.isNotBlank()) {
            parseInputData(rawTextData) { list, err ->
                parsedLeads = list
                parseError = err
            }
        } else {
            parsedLeads = emptyList()
            parseError = null
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Slate50)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Header
        item {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (onBackClick != null) {
                        IconButton(
                            onClick = onBackClick,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Slate700)
                        }
                    }
                    Icon(Icons.Default.TableChart, contentDescription = null, tint = Emerald600)
                    Text(
                        text = "Excel Leads Engine",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate900
                    )
                }
                Text(
                    text = "Bulk import from Excel & auto-distribute to sales team, or extract all leads to CSV/Excel",
                    fontSize = 12.sp,
                    color = Slate600
                )
            }
        }

        // Section Tabs
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Slate200),
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth().padding(4.dp)) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selectedSection == 0) Emerald600 else Color.Transparent)
                            .clickable { selectedSection = 0 }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Default.CloudUpload,
                                contentDescription = null,
                                tint = if (selectedSection == 0) Color.White else Slate700,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "📥 Import & Distribute",
                                fontSize = 12.sp,
                                fontWeight = if (selectedSection == 0) FontWeight.Bold else FontWeight.Medium,
                                color = if (selectedSection == 0) Color.White else Slate700
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selectedSection == 1) RoyalBlue600 else Color.Transparent)
                            .clickable { selectedSection = 1 }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Default.FileDownload,
                                contentDescription = null,
                                tint = if (selectedSection == 1) Color.White else Slate700,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "📤 Extract & Export Leads",
                                fontSize = 12.sp,
                                fontWeight = if (selectedSection == 1) FontWeight.Bold else FontWeight.Medium,
                                color = if (selectedSection == 1) Color.White else Slate700
                            )
                        }
                    }
                }
            }
        }

        // SECTION 0: EXCEL BULK IMPORT & ROUND-ROBIN DISTRIBUTION
        if (selectedSection == 0) {
            // Explanatory Banner
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Emerald50),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Emerald600)
                            Text(
                                text = "Automatic Round-Robin Lead Distribution",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Emerald900
                            )
                        }
                        Text(
                            text = "Aap apne Excel sheet ya Google Sheets ke rows copy karke yahan paste kar sakte hain. Duplicate phone numbers automatically filter hote hain, Custom Campaign Rules (e.g. 'Luxury 3BHK' ➔ Priya, 'Solar' ➔ Amit) directly route hote hain, aur baaki leads active team me Round-Robin barabar distribute hoti hain.",
                            fontSize = 11.sp,
                            color = Slate700,
                            lineHeight = 15.sp
                        )
                    }
                }
            }

            // Quick Pre-built Excel Samples
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Sample Excel Lead Batches (1-Tap Load):",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate800
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                rawTextData = SAMPLE_EXCEL_REAL_ESTATE
                            },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
                        ) {
                            Text("🏠 Real Estate (10)", fontSize = 11.sp, color = Emerald600)
                        }

                        OutlinedButton(
                            onClick = {
                                rawTextData = SAMPLE_EXCEL_SOLAR
                            },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
                        ) {
                            Text("☀️ Solar Energy (12)", fontSize = 11.sp, color = RoyalBlue600)
                        }

                        OutlinedButton(
                            onClick = {
                                filePickerLauncher.launch("text/*")
                            },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
                        ) {
                            Text("📁 Pick CSV File", fontSize = 11.sp, color = Slate700)
                        }
                    }
                }
            }

            // Input Text Area
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Paste Excel / CSV Data:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Slate800
                        )

                        if (rawTextData.isNotEmpty()) {
                            TextButton(
                                onClick = { rawTextData = "" },
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("Clear", fontSize = 11.sp, color = Color.Red)
                            }
                        }
                    }

                    Text(
                        text = "Columns format: Name, Phone, Email, Campaign, Inquiry Notes, Priority (HOT/WARM/COLD)",
                        fontSize = 10.sp,
                        color = Slate600
                    )

                    OutlinedTextField(
                        value = rawTextData,
                        onValueChange = { rawTextData = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .testTag("excel_data_input"),
                        placeholder = {
                            Text(
                                text = "Aarav Sharma\t+91 98111 22334\taarav@gmail.com\tMeta Luxury Villa\tInterested in 3BHK\tHOT\nDiya Patel\t+91 98222 33445\tdiya@gmail.com\tWhatsApp Inquiry\tNeed quotation\tWARM",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Slate400
                            )
                        },
                        textStyle = LocalTextStyle.current.copy(
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color.Black
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.Black,
                            unfocusedTextColor = Color.Black,
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White,
                            focusedBorderColor = RoyalBlue600,
                            unfocusedBorderColor = Slate300,
                            cursorColor = Color.Black
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            }

            // Real-time Parser Status & Distribution Preview
            if (parsedLeads.isNotEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(Emerald100),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = Emerald600, modifier = Modifier.size(16.dp))
                                    }
                                    Text(
                                        text = "${parsedLeads.size} Valid Leads Found",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = Emerald900
                                    )
                                }

                                Text(
                                    text = "Ready to Distribute",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Emerald600
                                )
                            }

                            // Active agents receiver chips
                            val activeCount = activeAgents.filter { it.isActive && it.role == "AGENT" }
                            Text(
                                text = "Will be assigned equally across ${activeCount.size} active sales reps:",
                                fontSize = 11.sp,
                                color = Slate600
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                activeCount.forEach { agent ->
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Slate100)
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(Emerald500)
                                            )
                                            Text(agent.name, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Slate800)
                                        }
                                    }
                                }
                            }

                            // Preview first 3 leads
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "Preview of Leads to Import:",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Slate700
                                )

                                parsedLeads.take(4).forEachIndexed { idx, lead ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Slate50, RoundedCornerShape(6.dp))
                                            .padding(8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = "${idx + 1}. ${lead.name} (${lead.phone})",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Slate900
                                            )
                                            Text(
                                                text = "${lead.campaign} • ${lead.notes.take(35)}...",
                                                fontSize = 10.sp,
                                                color = Slate600
                                            )
                                        }

                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(if (lead.priority == "HOT") Color(0xFFFEE2E2) else Emerald100)
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = lead.priority,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (lead.priority == "HOT") Color(0xFFDC2626) else Emerald900
                                            )
                                        }
                                    }
                                }

                                if (parsedLeads.size > 4) {
                                    Text(
                                        text = "+ ${parsedLeads.size - 4} more leads in batch",
                                        fontSize = 10.sp,
                                        color = Slate600
                                    )
                                }
                            }

                            // Distribute Button
                            Button(
                                onClick = {
                                    isImporting = true
                                    onImportLeads(parsedLeads) { result ->
                                        isImporting = false
                                        lastImportResult = result
                                        rawTextData = ""
                                        parsedLeads = emptyList()
                                    }
                                },
                                enabled = !isImporting && parsedLeads.isNotEmpty(),
                                colors = ButtonDefaults.buttonColors(containerColor = Emerald600),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("btn_distribute_leads")
                            ) {
                                if (isImporting) {
                                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Distributing to Sales Team...")
                                } else {
                                    Icon(Icons.Default.Send, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Distribute & Import ${parsedLeads.size} Leads",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (parseError != null) {
                item {
                    Text(
                        text = parseError ?: "",
                        fontSize = 11.sp,
                        color = Color(0xFFDC2626)
                    )
                }
            }
        }

        // SECTION 1: EXTRACT & EXPORT ALL LEADS (CSV / EXCEL FORMAT)
        if (selectedSection == 1) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.FileDownload, contentDescription = null, tint = RoyalBlue600)
                            Text(
                                text = "Extract All Leads to Excel / CSV",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = RoyalBlue600
                            )
                        }
                        Text(
                            text = "Admin can download, share, or copy all leads in standard spreadsheet format compatible with Microsoft Excel, Google Sheets, Zoho, or Salesforce.",
                            fontSize = 11.sp,
                            color = Slate700,
                            lineHeight = 15.sp
                        )
                    }
                }
            }

            // Stats Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Total Leads Available to Extract:",
                                fontSize = 13.sp,
                                color = Slate700
                            )
                            Text(
                                text = "${allLeads.size} Leads",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Slate900
                            )
                        }

                        HorizontalDivider(color = Slate100)

                        // Export Action Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Share CSV
                            Button(
                                onClick = {
                                    val csvData = onExportAllCsv()
                                    val sendIntent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        putExtra(Intent.EXTRA_TEXT, csvData)
                                        putExtra(Intent.EXTRA_TITLE, "LeadPulse_Leads_Export.csv")
                                        type = "text/csv"
                                    }
                                    val shareIntent = Intent.createChooser(sendIntent, "Share Leads CSV to Excel / WhatsApp / Email")
                                    context.startActivity(shareIntent)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Emerald600),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("btn_share_csv")
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Share / Send CSV", fontSize = 12.sp)
                            }

                            // Copy CSV to Clipboard
                            OutlinedButton(
                                onClick = {
                                    val csvData = onExportAllCsv()
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("CRM Leads CSV", csvData)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "✅ Copied ${allLeads.size} leads CSV to clipboard! Ready to paste in Excel.", Toast.LENGTH_LONG).show()
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("btn_copy_csv")
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Copy for Excel", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // Interactive Table Preview of Extracted Data
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Spreadsheet Columns Preview (${allLeads.size} Rows):",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate800
                    )

                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(12.dp)
                        ) {
                            // Table Header Row
                            Row(
                                modifier = Modifier
                                    .background(Slate100, RoundedCornerShape(6.dp))
                                    .padding(8.dp)
                            ) {
                                Text("ID", fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.width(40.dp))
                                Text("Customer Name", fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.width(130.dp))
                                Text("Phone Number", fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.width(110.dp))
                                Text("Assigned Sales Rep", fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.width(130.dp))
                                Text("Status", fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.width(90.dp))
                                Text("Campaign", fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.width(140.dp))
                                Text("Priority", fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.width(70.dp))
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // Table Data Rows
                            allLeads.forEach { lead ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp, horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("#${lead.id}", fontSize = 11.sp, color = Slate600, modifier = Modifier.width(40.dp))
                                    Text(lead.name, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Slate900, modifier = Modifier.width(130.dp))
                                    Text(lead.phoneNumber, fontSize = 11.sp, color = Slate700, modifier = Modifier.width(110.dp))
                                    Text(lead.assignedAgentName, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Emerald600, modifier = Modifier.width(130.dp))
                                    Text(lead.status, fontSize = 11.sp, color = Slate800, modifier = Modifier.width(90.dp))
                                    Text(lead.campaignName, fontSize = 11.sp, color = Slate600, modifier = Modifier.width(140.dp))
                                    Text(lead.priority, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (lead.priority == "HOT") Color(0xFFDC2626) else Slate700, modifier = Modifier.width(70.dp))
                                }
                                HorizontalDivider(color = Slate100, thickness = 0.5.dp)
                            }
                        }
                    }
                }
            }
        }
    }

    // Distribution Success Result Dialog
    lastImportResult?.let { result ->
        Dialog(onDismissRequest = { lastImportResult = null }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(Emerald100),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Emerald600,
                            modifier = Modifier.size(34.dp)
                        )
                    }

                    Text(
                        text = "Leads Distributed Successfully!",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate900
                    )

                    Text(
                        text = "${result.totalProcessed} total leads processed from your Excel batch.",
                        fontSize = 12.sp,
                        color = Slate600
                    )

                    // Stats Pills
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Emerald50),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("${result.newLeadsDistributed}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Emerald600)
                                Text("New Leads", fontSize = 10.sp, color = Slate700)
                            }
                        }

                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("${result.duplicatesRetained}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB45309))
                                Text("Repeat Inquiries", fontSize = 10.sp, color = Slate700)
                            }
                        }
                    }

                    // Distribution Breakdown per Sales Rep
                    if (result.distributionSummary.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Slate50, RoundedCornerShape(10.dp))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Round-Robin Assignment Breakdown:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Slate800
                            )

                            result.distributionSummary.forEach { (agentName, count) ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(agentName, fontSize = 12.sp, color = Slate700)
                                    Text("+$count leads", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Emerald600)
                                }
                            }
                        }
                    }

                    Button(
                        onClick = { lastImportResult = null },
                        colors = ButtonDefaults.buttonColors(containerColor = Emerald600),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Done")
                    }
                }
            }
        }
    }
}

/**
 * Parses tab-separated or comma-separated rows copied from Excel or Sheets.
 */
private fun parseInputData(
    rawText: String,
    onResult: (List<RawLeadInput>, String?) -> Unit
) {
    val lines = rawText.trim().lines().filter { it.isNotBlank() }
    if (lines.isEmpty()) {
        onResult(emptyList(), null)
        return
    }

    val parsedList = mutableListOf<RawLeadInput>()

    for ((index, line) in lines.withIndex()) {
        // Skip header if line looks like header
        if (index == 0 && (line.contains("Name", ignoreCase = true) || line.contains("Phone", ignoreCase = true))) {
            continue
        }

        // Split by Tab or Comma
        val tokens = if (line.contains("\t")) {
            line.split("\t").map { it.trim() }
        } else if (line.contains(",")) {
            line.split(",").map { it.trim() }
        } else {
            line.split(" ").map { it.trim() }
        }

        if (tokens.isNotEmpty()) {
            val name = tokens.getOrNull(0)?.trim() ?: "Lead #$index"
            val phone = tokens.getOrNull(1)?.trim() ?: ""
            val email = tokens.getOrNull(2)?.trim() ?: ""
            val campaign = tokens.getOrNull(3)?.trim() ?: "Excel Batch Upload"
            val notes = tokens.getOrNull(4)?.trim() ?: "Imported from Excel spreadsheet"
            val priority = tokens.getOrNull(5)?.trim()?.uppercase() ?: "WARM"

            val cleanPriority = if (priority in listOf("HOT", "WARM", "COLD")) priority else "WARM"

            if (name.isNotBlank() && phone.isNotBlank()) {
                parsedList.add(
                    RawLeadInput(
                        name = name,
                        phone = phone,
                        email = email,
                        campaign = campaign,
                        source = "Excel Import",
                        notes = notes,
                        priority = cleanPriority
                    )
                )
            }
        }
    }

    if (parsedList.isEmpty() && lines.isNotEmpty()) {
        onResult(emptyList(), "Could not find valid rows with Name and Phone. Check column format.")
    } else {
        onResult(parsedList, null)
    }
}

// Pre-built sample datasets for quick 1-tap testing
private val SAMPLE_EXCEL_REAL_ESTATE = """
Manish Agarwal	+91 98110 12345	manish.ag@gmail.com	DLF Luxury Plots	Interested in 500 sq yard corner plot	HOT
Sunita Mehta	+91 98220 23456	sunita.m@yahoo.com	Godrej Horizon 3BHK	Wants brochure and site visit Sunday	HOT
Rajiv Khanna	+91 98330 34567	rkhanna@gmail.com	DLF Luxury Plots	Inquiry for commercial shop space	WARM
Kavita Iyer	+91 98440 45678	kavita.iyer@gmail.com	Emaar Palm Hills	Budget 1.5 Cr, ready to move in	HOT
Deepak Jain	+91 98550 56789	djain@hotmail.com	Godrej Horizon 3BHK	Needs payment plan details	WARM
Preeti Deshmukh	+91 98660 67890	preeti.d@outlook.com	DLF Luxury Plots	Looking for investment near airport	COLD
Alok Saxena	+91 98770 78901	alok.saxena@gmail.com	Godrej Horizon 3BHK	Scheduled phone discussion	WARM
Pooja Trivedi	+91 98880 89012	ptrivedi@gmail.com	Emaar Palm Hills	Wants video walkthrough on WhatsApp	WARM
Naveen Chopra	+91 98990 90123	nchopra@gmail.com	DLF Luxury Plots	NRI looking to invest in Q3	HOT
Ananya Roy	+91 99000 01234	ananya.roy@gmail.com	Emaar Palm Hills	Needs home loan assistance details	WARM
""".trimIndent()

private val SAMPLE_EXCEL_SOLAR = """
Vikas Gupta	+91 97111 11223	vikas.gupta@gmail.com	5kW Rooftop Solar	Residential villa, monthly bill 8000	HOT
Meenakshi Sundaram	+91 97222 22334	meenakshi.s@gmail.com	Commercial 25kW Solar	Factory rooftop installation quote	HOT
Gaurav Joshi	+91 97333 33445	gjoshi@gmail.com	3kW On-Grid System	Subsidized PM Surya Ghar scheme inquiry	WARM
Bhavna Nair	+91 97444 44556	bnair@yahoo.com	5kW Rooftop Solar	Requested engineer site inspection	HOT
Ritesh Pandey	+91 97555 55667	ritesh.p@gmail.com	10kW Hybrid Solar	Needs battery backup system quote	WARM
Anita Kulkarni	+91 97666 66778	anita.k@gmail.com	3kW On-Grid System	Wants ROI and net metering details	WARM
Harish Reddy	+91 97777 77889	hreddy@gmail.com	Commercial 25kW Solar	Hospital building solar setup inquiry	HOT
Sangeeta Bose	+91 97888 88990	sbose@outlook.com	5kW Rooftop Solar	Sent electricity bill on WhatsApp	HOT
Tarun Sethi	+91 97999 99001	tsethi@gmail.com	3kW On-Grid System	Comparing prices with local vendors	COLD
Vandana Kaushik	+91 98000 00112	vkaushik@gmail.com	5kW Rooftop Solar	Looking for zero-cost EMI financing	WARM
Arun Nambiar	+91 98111 22339	anambiar@gmail.com	10kW Hybrid Solar	Farmhouse off-grid solar inquiry	HOT
Shalini Verma	+91 98222 33449	sverma@gmail.com	3kW On-Grid System	Needs subsidy application assistance	WARM
""".trimIndent()
