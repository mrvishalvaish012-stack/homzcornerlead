package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.FeedbackLogEntity
import com.example.data.model.LeadEntity
import com.example.data.model.SalesAgentEntity
import com.example.ui.components.DuplicateBadge
import com.example.ui.components.PriorityBadge
import com.example.ui.components.StatusBadge
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeadDetailAndFeedbackDialog(
    lead: LeadEntity,
    feedbackLogs: List<FeedbackLogEntity>,
    isManager: Boolean,
    allAgents: List<SalesAgentEntity>,
    onDismiss: () -> Unit,
    onSubmitFeedback: (disposition: String, notes: String, newStatus: String, nextFollowUpDate: Long?, nextFollowUpNote: String) -> Unit,
    onReassign: (newAgent: SalesAgentEntity, reason: String) -> Unit,
    onDeleteLead: () -> Unit,
    onUpdateLeadMessage: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }

    var activeTab by remember { mutableStateOf(0) } // 0: Details & Feedback Entry, 1: Activity Timeline, 2: Manager Reassign

    // Feedback Form State
    var disposition by remember { mutableStateOf("Call Answered - Interested") }
    var notes by remember { mutableStateOf("") }
    var selectedStatus by remember { mutableStateOf(lead.status) }
    var followUpPreset by remember { mutableStateOf("Tomorrow 11:00 AM") }
    var customFollowUpNote by remember { mutableStateOf("") }

    // Reassign state
    var selectedReassignAgent by remember { mutableStateOf(allAgents.firstOrNull { it.id != lead.assignedAgentId }) }
    var reassignReason by remember { mutableStateOf("Workload re-balancing") }

    val dispositionOptions = listOf(
        "Call Answered - Interested",
        "Call Not Picked / Busy",
        "Follow-up Scheduled",
        "Quotation & Brochure Shared",
        "Price Objection / Negotiation",
        "Site Visit / Demo Booked",
        "Deal Won / Closed",
        "Not Interested / Lost",
        "Invalid / Wrong Number"
    )

    val statusOptions = listOf(
        "NEW" to "New",
        "CONTACTED" to "Contacted",
        "FOLLOW_UP" to "Follow-up",
        "QUOTATION" to "Quotation",
        "WON" to "Won",
        "LOST" to "Lost"
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.92f),
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            shadowElevation = 8.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Surface(
                    color = Slate900,
                    contentColor = Color.White,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = lead.name,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                if (lead.isDuplicate) {
                                    DuplicateBadge(hitCount = lead.duplicateHitCount)
                                }
                            }
                            Text(
                                text = "${lead.phoneNumber} • ${lead.source}",
                                fontSize = 12.sp,
                                color = Slate200
                            )
                        }

                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                        }
                    }
                }

                // Sub-Tabs: 0 = Feedback & Actions, 1 = History/Timeline, 2 = Manager Controls
                TabRow(
                    selectedTabIndex = activeTab,
                    containerColor = Slate100,
                    contentColor = Emerald600
                ) {
                    Tab(
                        selected = activeTab == 0,
                        onClick = { activeTab = 0 },
                        text = { Text("Feedback & Actions", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                    )
                    Tab(
                        selected = activeTab == 1,
                        onClick = { activeTab = 1 },
                        text = { Text("Activity Log (${feedbackLogs.size})", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                    )
                    if (isManager) {
                        Tab(
                            selected = activeTab == 2,
                            onClick = { activeTab = 2 },
                            text = { Text("Manager Controls", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                        )
                    }
                }

                // Content Body
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    when (activeTab) {
                        0 -> FeedbackAndActionsTab(
                            lead = lead,
                            disposition = disposition,
                            onDispositionChange = { disposition = it },
                            dispositionOptions = dispositionOptions,
                            notes = notes,
                            onNotesChange = { notes = it },
                            selectedStatus = selectedStatus,
                            onStatusChange = { selectedStatus = it },
                            statusOptions = statusOptions,
                            followUpPreset = followUpPreset,
                            onFollowUpPresetChange = { followUpPreset = it },
                            customFollowUpNote = customFollowUpNote,
                            onCustomFollowUpNoteChange = { customFollowUpNote = it },
                            onSubmit = {
                                val followUpMillis = calculateFollowUpMillis(followUpPreset)
                                onSubmitFeedback(
                                    disposition,
                                    notes,
                                    selectedStatus,
                                    followUpMillis,
                                    customFollowUpNote.ifBlank { "Follow-up: $disposition" }
                                )
                                onDismiss()
                            },
                            onWhatsAppClick = {
                                val cleanPhone = lead.phoneNumber.replace(Regex("[^0-9]"), "")
                                val url = "https://api.whatsapp.com/send?phone=$cleanPhone"
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                context.startActivity(intent)
                            },
                            onCallClick = {
                                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${lead.phoneNumber}"))
                                context.startActivity(intent)
                            },
                            onUpdateLeadMessage = onUpdateLeadMessage
                        )

                        1 -> ActivityLogTimelineTab(
                            feedbackLogs = feedbackLogs,
                            lead = lead,
                            dateFormat = dateFormat
                        )

                        2 -> ManagerControlsTab(
                            lead = lead,
                            allAgents = allAgents,
                            selectedReassignAgent = selectedReassignAgent,
                            onAgentSelected = { selectedReassignAgent = it },
                            reassignReason = reassignReason,
                            onReasonChange = { reassignReason = it },
                            onReassignClick = {
                                selectedReassignAgent?.let { agent ->
                                    onReassign(agent, reassignReason)
                                    onDismiss()
                                }
                            },
                            onDeleteClick = {
                                onDeleteLead()
                                onDismiss()
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeedbackAndActionsTab(
    lead: LeadEntity,
    disposition: String,
    onDispositionChange: (String) -> Unit,
    dispositionOptions: List<String>,
    notes: String,
    onNotesChange: (String) -> Unit,
    selectedStatus: String,
    onStatusChange: (String) -> Unit,
    statusOptions: List<Pair<String, String>>,
    followUpPreset: String,
    onFollowUpPresetChange: (String) -> Unit,
    customFollowUpNote: String,
    onCustomFollowUpNoteChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onWhatsAppClick: () -> Unit,
    onCallClick: () -> Unit,
    onUpdateLeadMessage: ((String) -> Unit)? = null
) {
    var dispositionExpanded by remember { mutableStateOf(false) }
    var isEditingMessage by remember { mutableStateOf(false) }
    var editedMessageText by remember(lead.initialMessage) { mutableStateOf(lead.initialMessage) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Duplicate Protection Notice banner if duplicate
        if (lead.isDuplicate) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = Color(0xFFB45309))
                        Column {
                            Text(
                                text = "Duplicate Lead Protection Active",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF92400E)
                            )
                            Text(
                                text = "Customer sent repeat inquiries (x${lead.duplicateHitCount}). Retained strictly under ${lead.assignedAgentName}'s ownership.",
                                fontSize = 11.sp,
                                color = Color(0xFF78350F)
                            )
                        }
                    }
                }
            }
        }

        // Quick Actions Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onWhatsAppClick,
                    colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreen),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Chat, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("WhatsApp Chat", color = Color.White)
                }

                Button(
                    onClick = onCallClick,
                    colors = ButtonDefaults.buttonColors(containerColor = RoyalBlue600),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Phone, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Call Customer", color = Color.White)
                }
            }
        }

        // Customer Message Highlight Card (Shows full message sent by the customer)
        item {
            val isWhatsApp = lead.source.contains("WhatsApp", ignoreCase = true)
            val cleanPhone = lead.phoneNumber.replace(Regex("[^0-9+]"), "").trim()
            val rawMsg = lead.initialMessage.trim()

            // Filter out any line that is purely a phone number or [New Inquiry timestamp]: phone
            val nonPhoneLines = rawMsg.lines()
                .map { it.replace(Regex("\\[New Inquiry.*?\\]:?"), "").trim() }
                .filter { line ->
                    val cleanDigits = line.replace(Regex("[^0-9+]"), "")
                    line.isNotBlank() && !(cleanDigits.length >= 7 && (cleanDigits == cleanPhone || line.startsWith("+")))
                }

            val fullMsg = if (nonPhoneLines.isNotEmpty()) {
                nonPhoneLines.joinToString("\n").trim()
            } else {
                when {
                    lead.campaignName.contains("3BHK", ignoreCase = true) -> "Hello, saw your 3BHK luxury apartment ad. Please share price, floor plan and payment options."
                    lead.campaignName.contains("Solar", ignoreCase = true) -> "Hi, need cost estimate and subsidy info for 5kW solar rooftop installation."
                    lead.campaignName.contains("Marketing", ignoreCase = true) -> "Hello, interested in your Meta Ads and lead generation service package."
                    lead.campaignName.contains("SaaS", ignoreCase = true) -> "Hi, want quotation and demo for team CRM software."
                    else -> "Hello, I am interested in ${lead.campaignName}. Please share quotation, brochure and pricing details on WhatsApp."
                }
            }

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isWhatsApp) Color(0xFFDCF8C6) else Color(0xFFEFF6FF)
                ),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isWhatsApp) Color(0xFF86EFAC) else Color(0xFF93C5FD)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
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
                            Icon(
                                if (isWhatsApp) Icons.Default.ChatBubble else Icons.Default.Email,
                                contentDescription = null,
                                tint = if (isWhatsApp) Color(0xFF15803D) else RoyalBlue600,
                                modifier = Modifier.size(17.dp)
                            )
                            Text(
                                text = if (isWhatsApp) "CUSTOMER WHATSAPP INCOMING MESSAGE" else "CUSTOMER INCOMING MESSAGE",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isWhatsApp) Color(0xFF166534) else RoyalBlue600,
                                letterSpacing = 0.5.sp
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color.White.copy(alpha = 0.8f)
                            ) {
                                Text(
                                    text = "Admin & ${lead.assignedAgentName}",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Slate700,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }

                            if (onUpdateLeadMessage != null) {
                                IconButton(
                                    onClick = {
                                        if (!isEditingMessage) {
                                            editedMessageText = fullMsg
                                        }
                                        isEditingMessage = !isEditingMessage
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isEditingMessage) Icons.Default.Close else Icons.Default.Edit,
                                        contentDescription = "Edit Customer Message",
                                        tint = Slate600,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (isEditingMessage) {
                        OutlinedTextField(
                            value = editedMessageText,
                            onValueChange = { editedMessageText = it },
                            label = { Text("Edit Customer Inquiry / Message", fontSize = 12.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            maxLines = 4,
                            textStyle = TextStyle(color = Color.Black, fontSize = 13.sp),
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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { isEditingMessage = false }) {
                                Text("Cancel", fontSize = 12.sp)
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Button(
                                onClick = {
                                    if (editedMessageText.isNotBlank()) {
                                        onUpdateLeadMessage?.invoke(editedMessageText.trim())
                                        isEditingMessage = false
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreen),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Text("Save Message", fontSize = 12.sp, color = Color.White)
                            }
                        }
                    } else {
                        Text(
                            text = "“$fullMsg”",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF0F172A),
                            lineHeight = 20.sp
                        )
                    }

                    HorizontalDivider(color = Color(0x22000000), thickness = 0.5.dp)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Assigned To: ${lead.assignedAgentName}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Slate700
                        )
                        Text(
                            text = "Source: ${lead.source}",
                            fontSize = 11.sp,
                            color = Slate600
                        )
                    }
                }
            }
        }

        // Lead Details Overview Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Slate50),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Lead Source & Meta Ad Info", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Slate900)
                    Text("Campaign: ${lead.campaignName}", fontSize = 12.sp, color = Slate700)
                    Text("Assigned Agent: ${lead.assignedAgentName}", fontSize = 12.sp, color = Slate700)
                    Text("Priority: ${lead.priority} • Est. Deal Value: ₹${String.format("%,.0f", lead.dealValue)}", fontSize = 12.sp, color = Slate700)
                }
            }
        }

        // Section Header: Agent Feedback
        item {
            Text(
                text = "Log Call / WhatsApp Feedback",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Slate900
            )
        }

        // Disposition Dropdown
        item {
            Column {
                Text("Call Outcome / Disposition", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Slate700)
                Spacer(modifier = Modifier.height(4.dp))
                ExposedDropdownMenuBox(
                    expanded = dispositionExpanded,
                    onExpandedChange = { dispositionExpanded = it }
                ) {
                    OutlinedTextField(
                        value = disposition,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dispositionExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        shape = RoundedCornerShape(8.dp),
                        textStyle = TextStyle(color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Medium),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.Black,
                            unfocusedTextColor = Color.Black,
                            focusedContainerColor = Slate50,
                            unfocusedContainerColor = Slate50
                        )
                    )
                    ExposedDropdownMenu(
                        expanded = dispositionExpanded,
                        onDismissRequest = { dispositionExpanded = false }
                    ) {
                        dispositionOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    onDispositionChange(option)
                                    dispositionExpanded = false
                                    // Smart status preset based on disposition
                                    if (option.contains("Won")) onStatusChange("WON")
                                    else if (option.contains("Lost") || option.contains("Invalid")) onStatusChange("LOST")
                                    else if (option.contains("Quotation")) onStatusChange("QUOTATION")
                                    else if (option.contains("Follow-up")) onStatusChange("FOLLOW_UP")
                                    else onStatusChange("CONTACTED")
                                }
                            )
                        }
                    }
                }
            }
        }

        // Update Lead Pipeline Status
        item {
            Column {
                Text("Update Pipeline Stage", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Slate700)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    statusOptions.take(3).forEach { (statusKey, label) ->
                        val selected = selectedStatus == statusKey
                        FilterChip(
                            selected = selected,
                            onClick = { onStatusChange(statusKey) },
                            label = { Text(label, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Emerald600,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    statusOptions.drop(3).forEach { (statusKey, label) ->
                        val selected = selectedStatus == statusKey
                        FilterChip(
                            selected = selected,
                            onClick = { onStatusChange(statusKey) },
                            label = { Text(label, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = if (statusKey == "WON") Color(0xFF15803D) else if (statusKey == "LOST") Color(0xFFDC2626) else RoyalBlue600,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
            }
        }

        // Notes text field
        item {
            Column {
                Text("Feedback Notes / Conversation Details", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Slate700)
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = notes,
                    onValueChange = onNotesChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("feedback_notes_field"),
                    placeholder = { Text("e.g. Customer asked for brochure. Price was negotiated. Agreed to visit site on Saturday...", fontSize = 12.sp, color = Slate500) },
                    minLines = 3,
                    shape = RoundedCornerShape(8.dp),
                    textStyle = TextStyle(color = Color.Black, fontSize = 13.sp),
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
            }
        }

        // Next Follow-Up Auto-Reminder Scheduler
        item {
            Column {
                Text("Schedule Next Auto-Reminder", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Slate700)
                Spacer(modifier = Modifier.height(6.dp))
                val presets = listOf("In 2 Hours", "Tomorrow 11:00 AM", "In 3 Days", "No Follow-up")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    presets.forEach { preset ->
                        val isSel = followUpPreset == preset
                        FilterChip(
                            selected = isSel,
                            onClick = { onFollowUpPresetChange(preset) },
                            label = { Text(preset, fontSize = 11.sp) }
                        )
                    }
                }

                if (followUpPreset != "No Follow-up") {
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = customFollowUpNote,
                        onValueChange = onCustomFollowUpNoteChange,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Reminder objective (e.g. Call for advance token)", fontSize = 12.sp, color = Slate500) },
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true,
                        textStyle = TextStyle(color = Color.Black, fontSize = 13.sp),
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
                }
            }
        }

        // Submit Button
        item {
            Button(
                onClick = onSubmit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("save_feedback_btn"),
                colors = ButtonDefaults.buttonColors(containerColor = Emerald600),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Save Feedback & Update Lead", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ActivityLogTimelineTab(
    feedbackLogs: List<FeedbackLogEntity>,
    lead: LeadEntity,
    dateFormat: SimpleDateFormat
) {
    if (feedbackLogs.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.History, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(8.dp))
                Text("No past activity recorded yet", color = Color.Gray, fontSize = 14.sp)
                Text("Submit feedback from the main tab to build history.", color = Color.LightGray, fontSize = 12.sp)
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(feedbackLogs, key = { it.id }) { log ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = Slate50)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = log.disposition,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Slate900
                            )
                            Text(
                                text = dateFormat.format(Date(log.timestamp)),
                                fontSize = 10.sp,
                                color = Slate600
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Agent: ${log.agentName}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = RoyalBlue600
                        )

                        if (log.notes.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = log.notes,
                                fontSize = 12.sp,
                                color = Slate700
                            )
                        }

                        if (log.previousStatus.isNotBlank() && log.newStatus.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Status changed: ${log.previousStatus} ➔ ${log.newStatus}",
                                fontSize = 10.sp,
                                color = Emerald600,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ManagerControlsTab(
    lead: LeadEntity,
    allAgents: List<SalesAgentEntity>,
    selectedReassignAgent: SalesAgentEntity?,
    onAgentSelected: (SalesAgentEntity) -> Unit,
    reassignReason: String,
    onReasonChange: (String) -> Unit,
    onReassignClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Slate50),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Manual Agent Reassignment",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Slate900
                )
                Text(
                    text = "Currently assigned to: ${lead.assignedAgentName}",
                    fontSize = 12.sp,
                    color = Slate700
                )

                Text("Select New Sales Agent:", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                allAgents.filter { it.role == "AGENT" }.forEach { agent ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedReassignAgent?.id == agent.id,
                            onClick = { onAgentSelected(agent) }
                        )
                        Text(
                            text = "${agent.name} (${if (agent.isActive) "Active" else "Paused"})",
                            fontSize = 13.sp
                        )
                    }
                }

                OutlinedTextField(
                    value = reassignReason,
                    onValueChange = onReasonChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Reassignment Reason") },
                    shape = RoundedCornerShape(8.dp),
                    textStyle = TextStyle(color = Color.Black, fontSize = 13.sp),
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

                Button(
                    onClick = onReassignClick,
                    colors = ButtonDefaults.buttonColors(containerColor = RoyalBlue600),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.SwapHoriz, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Confirm Reassignment")
                }
            }
        }

        // Delete / Close Lead Option
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFEE2E2)),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Danger Zone",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Color(0xFFDC2626)
                )
                Text(
                    text = "Permanently remove this customer record and associated logs.",
                    fontSize = 11.sp,
                    color = Color(0xFF991B1B)
                )
                OutlinedButton(
                    onClick = onDeleteClick,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFDC2626)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Delete Lead Record")
                }
            }
        }
    }
}

private fun calculateFollowUpMillis(preset: String): Long? {
    val now = System.currentTimeMillis()
    return when (preset) {
        "In 2 Hours" -> now + 2 * 3600_000
        "Tomorrow 11:00 AM" -> now + 24 * 3600_000
        "In 3 Days" -> now + 72 * 3600_000
        else -> null
    }
}
