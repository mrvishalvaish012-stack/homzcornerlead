package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.LeadEntity
import com.example.data.model.SalesAgentEntity
import com.example.ui.components.DuplicateBadge
import com.example.ui.components.PriorityBadge
import com.example.ui.components.StatusBadge
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LeadListScreen(
    leads: List<LeadEntity>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedStatus: String,
    onStatusSelected: (String) -> Unit,
    selectedPriority: String,
    onPrioritySelected: (String) -> Unit,
    onLeadClick: (LeadEntity) -> Unit,
    onAddLeadClick: () -> Unit,
    isAdmin: Boolean = true,
    allAgents: List<SalesAgentEntity> = emptyList(),
    adminSelectedAgentId: Long? = null,
    onAdminAgentFilterChange: (Long?) -> Unit = {},
    onExportLeadsClick: (() -> Unit)? = null,
    currentAgentName: String? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val statuses = listOf(
        "ALL" to "All Leads",
        "NEW" to "New",
        "FOLLOW_UP" to "Follow-up",
        "CONTACTED" to "Contacted",
        "QUOTATION" to "Quotation",
        "WON" to "Won",
        "LOST" to "Lost"
    )

    // Active Leads Summary & Live Conversion Feed calculations
    val overdueCount = leads.count {
        it.nextFollowUpDate != null && it.nextFollowUpDate <= System.currentTimeMillis() && it.status !in listOf("WON", "LOST")
    }
    val duplicateCount = leads.count { it.isDuplicate }
    val wonCount = leads.count { it.status == "WON" }
    val totalRevenue = leads.filter { it.status == "WON" }.sumOf { it.dealValue }
    val convRate = if (leads.isNotEmpty()) (wonCount.toFloat() / leads.size) * 100f else 0f

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Slate50),
        contentPadding = PaddingValues(bottom = 90.dp)
    ) {
        // TOP HEADER: Search Bar, Conversion Feed Card, and Summary (Scrolls away smoothly)
        item(key = "top_header_section") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                // Search Input Field with pure high-contrast black text
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("lead_search_field"),
                    textStyle = TextStyle(
                        color = Color.Black,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    placeholder = {
                        Text(
                            "Search name, phone, or campaign...",
                            fontSize = 13.sp,
                            color = Slate600
                        )
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = "Search", tint = Slate700)
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchQueryChange("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear", tint = Slate700)
                            }
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.Black,
                        unfocusedTextColor = Color.Black,
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedBorderColor = Emerald600,
                        unfocusedBorderColor = Slate300,
                        focusedPlaceholderColor = Slate500,
                        unfocusedPlaceholderColor = Slate500,
                        cursorColor = Color.Black
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Live Total Conversion Stats Feed Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Emerald600),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.TrendingUp, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                            Column {
                                Text(
                                    text = "Live Conversion Feed",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF166534)
                                )
                                Text(
                                    text = if (isAdmin) "All Reps • Total Converted: $wonCount" else "My Conversions: $wonCount Leads Won",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF15803D)
                                )
                            }
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "${String.format("%.1f", convRate)}% Converted",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF15803D)
                            )
                            Text(
                                text = "₹${String.format("%,.0f", totalRevenue)} Won",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF166534)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Summary Row: Leads count & Badges
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "${leads.size} Leads Active",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Slate800
                        )

                        if (onExportLeadsClick != null) {
                            IconButton(
                                onClick = onExportLeadsClick,
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFEFF6FF))
                                    .testTag("btn_export_leads_header")
                            ) {
                                Icon(
                                    Icons.Default.FileDownload,
                                    contentDescription = "Extract / Export Leads",
                                    tint = RoyalBlue600,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (wonCount > 0) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFFDCFCE7))
                                    .padding(horizontal = 7.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = "🎉 $wonCount Won",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF15803D)
                                )
                            }
                        }

                        if (overdueCount > 0) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFFFEE2E2))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = "⚠️ $overdueCount Overdue",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFDC2626)
                                )
                            }
                        }

                        if (duplicateCount > 0) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFFFEF3C7))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = "🔁 $duplicateCount Repeat",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFB45309)
                                )
                            }
                        }
                    }
                }
            }
        }

        // STICKY FILTER BAR: Only this stays pinned at top when user scrolls!
        stickyHeader(key = "sticky_filter_bar") {
            Surface(
                color = Color.White,
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    // Status Filter Chips
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(statuses) { (statusKey, label) ->
                            val isSelected = selectedStatus == statusKey
                            FilterChip(
                                selected = isSelected,
                                onClick = { onStatusSelected(statusKey) },
                                label = { Text(label, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Emerald600,
                                    selectedLabelColor = Color.White,
                                    containerColor = Slate100,
                                    labelColor = Slate700
                                ),
                                border = null,
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                    }

                    // ADMIN: Filter by Sales Rep
                    if (isAdmin && allAgents.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Filter by Sales Person:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Slate600
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            item {
                                val isAllSelected = adminSelectedAgentId == null
                                FilterChip(
                                    selected = isAllSelected,
                                    onClick = { onAdminAgentFilterChange(null) },
                                    label = { Text("All Reps (Total)", fontSize = 11.sp, fontWeight = if (isAllSelected) FontWeight.Bold else FontWeight.Normal) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = RoyalBlue600,
                                        selectedLabelColor = Color.White,
                                        containerColor = Slate100,
                                        labelColor = Slate700
                                    ),
                                    border = null,
                                    shape = RoundedCornerShape(6.dp)
                                )
                            }

                            items(allAgents.filter { it.role == "AGENT" }, key = { it.id }) { agent ->
                                val isSelected = adminSelectedAgentId == agent.id
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { onAdminAgentFilterChange(agent.id) },
                                    label = { Text(agent.name, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = RoyalBlue600,
                                        selectedLabelColor = Color.White,
                                        containerColor = Slate100,
                                        labelColor = Slate700
                                    ),
                                    border = null,
                                    shape = RoundedCornerShape(6.dp)
                                )
                            }
                        }
                    } else if (!isAdmin && currentAgentName != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = Emerald600, modifier = Modifier.size(12.dp))
                            Text(
                                text = "Private View: Showing only leads assigned to $currentAgentName",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Emerald600
                            )
                        }
                    }
                }
            }
        }

        // LEADS LIST OR EMPTY STATE
        if (leads.isEmpty()) {
            item(key = "empty_leads_placeholder") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = null,
                            tint = Color.LightGray,
                            modifier = Modifier.size(56.dp)
                        )
                        Text(
                            text = "No leads match current filter",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = Slate600
                        )
                        Text(
                            text = "Use the WhatsApp QR or Excel tab to add leads.",
                            fontSize = 13.sp,
                            color = Color.Gray
                        )
                        Button(
                            onClick = onAddLeadClick,
                            colors = ButtonDefaults.buttonColors(containerColor = Emerald600)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Simulate Inbound Lead")
                        }
                    }
                }
            }
        } else {
            items(leads, key = { it.id }) { lead ->
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp)) {
                    LeadCardItem(
                        lead = lead,
                        onCardClick = { onLeadClick(lead) },
                        onWhatsAppClick = { openWhatsAppChat(context, lead) },
                        onCallClick = { openDialer(context, lead.phoneNumber) }
                    )
                }
            }
        }
    }
}

@Composable
fun LeadCardItem(
    lead: LeadEntity,
    onCardClick: () -> Unit,
    onWhatsAppClick: () -> Unit,
    onCallClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val now = System.currentTimeMillis()
    val isOverdue = lead.nextFollowUpDate != null && lead.nextFollowUpDate <= now && lead.status !in listOf("WON", "LOST")
    val dateFormat = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onCardClick() }
            .testTag("lead_card_${lead.id}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Top Row: Badges and Source
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusBadge(status = lead.status)
                    PriorityBadge(priority = lead.priority)
                    if (lead.isDuplicate) {
                        DuplicateBadge(hitCount = lead.duplicateHitCount)
                    }
                }

                // Lead Source Chip
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = if (lead.source.contains("WhatsApp", ignoreCase = true)) Icons.Default.Chat else Icons.Default.Campaign,
                        contentDescription = null,
                        tint = if (lead.source.contains("WhatsApp", ignoreCase = true)) WhatsAppGreen else RoyalBlue600,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = if (lead.source.contains("WhatsApp", ignoreCase = true)) "WhatsApp" else "Meta Ads",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = Slate600
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Customer Name & Phone
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = lead.name,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate900
                    )
                    Text(
                        text = lead.phoneNumber,
                        fontSize = 13.sp,
                        color = Slate600
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = dateFormat.format(java.util.Date(lead.createdAt)),
                        fontSize = 11.sp,
                        color = Slate400
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Customer Incoming Message bubble (Highly Prominent WhatsApp-style Chat Card)
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

            val actualMessage = if (nonPhoneLines.isNotEmpty()) {
                nonPhoneLines.joinToString(" ").trim()
            } else {
                when {
                    lead.campaignName.contains("3BHK", ignoreCase = true) -> "Hello, saw your 3BHK luxury apartment ad. Please share price, floor plan and payment options."
                    lead.campaignName.contains("Solar", ignoreCase = true) -> "Hi, need cost estimate and subsidy info for 5kW solar rooftop installation."
                    lead.campaignName.contains("Marketing", ignoreCase = true) -> "Hello, interested in your Meta Ads and lead generation service package."
                    lead.campaignName.contains("SaaS", ignoreCase = true) -> "Hi, want quotation and demo for team CRM software."
                    else -> "Hello, I am interested in ${lead.campaignName}. Please share quotation, brochure and pricing details on WhatsApp."
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isWhatsApp) Color(0xFFDCF8C6) else Color(0xFFE0F2FE))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = if (isWhatsApp) Icons.Default.ChatBubble else Icons.Default.MailOutline,
                                contentDescription = "Customer Message",
                                tint = if (isWhatsApp) Color(0xFF15803D) else Color(0xFF0369A1),
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = if (isWhatsApp) "CUSTOMER WHATSAPP MESSAGE" else "CUSTOMER INCOMING MESSAGE",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isWhatsApp) Color(0xFF166534) else Color(0xFF075985),
                                letterSpacing = 0.5.sp
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color.White.copy(alpha = 0.7f)
                        ) {
                            Text(
                                text = "Admin & ${lead.assignedAgentName}",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Slate700,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Text(
                        text = "“$actualMessage”",
                        fontSize = 13.sp,
                        color = Color(0xFF0F172A),
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 18.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))

            // Follow-up Reminder Alert Box
            if (lead.nextFollowUpDate != null && lead.status !in listOf("WON", "LOST")) {
                val timeStr = dateFormat.format(Date(lead.nextFollowUpDate))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isOverdue) Color(0xFFFEE2E2) else Color(0xFFEFF6FF))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = if (isOverdue) Icons.Default.Warning else Icons.Default.AccessTime,
                        contentDescription = null,
                        tint = if (isOverdue) Color(0xFFDC2626) else Color(0xFF2563EB),
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = if (isOverdue) "Overdue Follow-up: $timeStr" else "Next Follow-up: $timeStr",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isOverdue) Color(0xFFB91C1C) else Color(0xFF1D4ED8)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            HorizontalDivider(color = Slate100, thickness = 1.dp)

            Spacer(modifier = Modifier.height(8.dp))

            // Bottom Action Row: Assigned Agent & Direct Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Assigned Agent Pill with prominent icon and label
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(RoyalBlue600),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = lead.assignedAgentName.take(1).uppercase(),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    Column {
                        Text(
                            text = "Assigned Rep",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Slate400
                        )
                        Text(
                            text = lead.assignedAgentName,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Slate800
                        )
                    }
                }

                // Action Buttons (WhatsApp & Call)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // WhatsApp Button
                    FilledTonalButton(
                        onClick = onWhatsAppClick,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Color(0xFFDCFCE7),
                            contentColor = WhatsAppDark
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("whatsapp_btn_${lead.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Chat,
                            contentDescription = "WhatsApp",
                            modifier = Modifier.size(15.dp),
                            tint = WhatsAppDark
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("WhatsApp", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }

                    // Call Button
                    FilledTonalButton(
                        onClick = onCallClick,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = RoyalBlue100,
                            contentColor = RoyalBlue700
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("call_btn_${lead.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Phone,
                            contentDescription = "Call",
                            modifier = Modifier.size(15.dp),
                            tint = RoyalBlue700
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Call", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

// Helpers for WhatsApp and Call Intents
private fun openWhatsAppChat(context: Context, lead: LeadEntity) {
    try {
        val cleanPhone = lead.phoneNumber.replace(Regex("[^0-9]"), "")
        val prefilledMsg = Uri.encode(
            "Hello ${lead.name}, thank you for reaching out regarding our ${lead.campaignName}! I am ${lead.assignedAgentName} from the sales team. How can I help you today?"
        )
        val url = "https://api.whatsapp.com/send?phone=$cleanPhone&text=$prefilledMsg"
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse(url)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        // Fallback or error handled gracefully
    }
}

private fun openDialer(context: Context, phoneNumber: String) {
    try {
        val intent = Intent(Intent.ACTION_DIAL).apply {
            data = Uri.parse("tel:${phoneNumber.replace(" ", "")}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        // Handled
    }
}
