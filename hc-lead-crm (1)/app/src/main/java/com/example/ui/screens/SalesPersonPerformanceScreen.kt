package com.example.ui.screens

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.LeadEntity
import com.example.data.model.SalesAgentEntity
import com.example.ui.theme.*

@Composable
fun SalesPersonPerformanceScreen(
    agent: SalesAgentEntity,
    myLeads: List<LeadEntity>,
    onLeadClick: (LeadEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Computed metrics strictly for this sales person
    val totalLeads = myLeads.size
    val wonLeads = myLeads.count { it.status == "WON" }
    val lostLeads = myLeads.count { it.status == "LOST" }
    val inFollowUp = myLeads.count { it.status in listOf("FOLLOW_UP", "CONTACTED", "QUOTATION") }
    val conversionRate = if (totalLeads > 0) (wonLeads.toFloat() / totalLeads.toFloat()) * 100f else 0f

    // Personal monthly target: say 5 won deals or scaled
    val monthlyTarget = 5
    val targetProgress = (wonLeads.toFloat() / monthlyTarget.toFloat()).coerceIn(0f, 1f)

    // Filter priority leads requiring attention today
    val hotPendingLeads = myLeads.filter { it.status != "WON" && it.status != "LOST" }
        .sortedByDescending { if (it.priority == "HOT") 2 else if (it.priority == "WARM") 1 else 0 }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Slate50)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Agent Profile Header & Confidentiality Notice
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(50.dp)
                                    .clip(CircleShape)
                                    .background(Emerald600),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = agent.name.take(1).uppercase(),
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }

                            Column {
                                Text(
                                    text = agent.name,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Slate900
                                )
                                Text(
                                    text = "Sales Representative • Private Dashboard",
                                    fontSize = 12.sp,
                                    color = Emerald600,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "${agent.email} • ${agent.phoneNumber}",
                                    fontSize = 11.sp,
                                    color = Slate600
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (agent.isActive) Color(0xFFDCFCE7) else Color(0xFFFEE2E2)
                        ) {
                            Text(
                                text = if (agent.isActive) "Active Rep" else "Paused",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (agent.isActive) Color(0xFF15803D) else Color(0xFFB91C1C),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    // Strict Data Privacy Notice
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFEFF6FF))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Privacy Lock",
                            tint = RoyalBlue600,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Private & Confidential: Ye dashboard sirf aapki personal leads aur conversion performance dikhata hai. Dusre team members ka data strictly hidden hai.",
                            fontSize = 11.sp,
                            color = Slate800,
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        }

        // Personal KPI Metric Cards (2x2 Grid)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PersonalKpiCard(
                        title = "My Assigned Leads",
                        value = "$totalLeads",
                        subtitle = "Total inquiries assigned to me",
                        icon = Icons.Default.AssignmentInd,
                        iconTint = RoyalBlue600,
                        modifier = Modifier.weight(1f)
                    )

                    PersonalKpiCard(
                        title = "My Conversion Rate",
                        value = String.format("%.1f%%", conversionRate),
                        subtitle = "$wonLeads won / $totalLeads total",
                        icon = Icons.Default.TrendingUp,
                        iconTint = Emerald600,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PersonalKpiCard(
                        title = "My Won Deals",
                        value = "$wonLeads",
                        subtitle = "$lostLeads lost / dropped",
                        icon = Icons.Default.CheckCircle,
                        iconTint = Color(0xFF10B981),
                        modifier = Modifier.weight(1f)
                    )

                    PersonalKpiCard(
                        title = "Active Follow-ups",
                        value = "$inFollowUp",
                        subtitle = "Calls / Quotations pending",
                        icon = Icons.Default.Sync,
                        iconTint = Color(0xFFF59E0B),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Monthly Target Progress Card
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
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "My Monthly Conversion Target",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Slate900
                        )
                        Text(
                            text = "$wonLeads / $monthlyTarget Closed",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Emerald600
                        )
                    }

                    LinearProgressIndicator(
                        progress = { targetProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = Emerald600,
                        trackColor = Slate200
                    )

                    Text(
                        text = if (wonLeads >= monthlyTarget) {
                            "🎉 Congratulations! You have achieved your monthly target."
                        } else {
                            "Follow up actively on your hot leads to reach ${monthlyTarget - wonLeads} more closed deals."
                        },
                        fontSize = 11.sp,
                        color = Slate600
                    )
                }
            }
        }

        // Personal Pipeline Stage Funnel
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
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "My Lead Pipeline Funnel",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate900
                    )

                    val newCount = myLeads.count { it.status == "NEW" }
                    val contactedCount = myLeads.count { it.status == "CONTACTED" }
                    val followUpCount = myLeads.count { it.status == "FOLLOW_UP" }
                    val quotationCount = myLeads.count { it.status == "QUOTATION" }
                    val wonCount = myLeads.count { it.status == "WON" }
                    val lostCount = myLeads.count { it.status == "LOST" }
                    val maxVal = maxOf(totalLeads, 1)

                    PipelineBar(label = "1. New Leads", count = newCount, max = maxVal, color = StatusNewText)
                    PipelineBar(label = "2. Contacted", count = contactedCount, max = maxVal, color = StatusContactedText)
                    PipelineBar(label = "3. In Follow-up", count = followUpCount, max = maxVal, color = StatusFollowUpText)
                    PipelineBar(label = "4. Quotation Sent", count = quotationCount, max = maxVal, color = StatusQuotationText)
                    PipelineBar(label = "5. Won / Converted", count = wonCount, max = maxVal, color = StatusWonText)
                    PipelineBar(label = "6. Lost / Dropped", count = lostCount, max = maxVal, color = StatusLostText)
                }
            }
        }

        // Priority Leads & Immediate Follow-up Actions Section
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "My Active Leads Requiring Action (${hotPendingLeads.size})",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Slate900
                )
            }
        }

        if (hotPendingLeads.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircleOutline,
                            contentDescription = null,
                            tint = Emerald600,
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            text = "No pending follow-ups right now!",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Slate800
                        )
                        Text(
                            text = "New leads assigned to you from Meta Ads & WhatsApp will show up here.",
                            fontSize = 12.sp,
                            color = Slate600
                        )
                    }
                }
            }
        } else {
            items(hotPendingLeads, key = { it.id }) { lead ->
                MyActionLeadCard(
                    lead = lead,
                    onClick = { onLeadClick(lead) },
                    onWhatsAppClick = {
                        val cleanPhone = lead.phoneNumber.replace("[^0-9+]".toRegex(), "")
                        val message = "Hello ${lead.name}, I am ${agent.name} from LeadPulse regarding your inquiry on ${lead.campaignName}. How can I assist you today?"
                        val encodedMsg = Uri.encode(message)
                        val uri = Uri.parse("https://wa.me/$cleanPhone?text=$encodedMsg")
                        val intent = Intent(Intent.ACTION_VIEW, uri)
                        try {
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            // Fallback
                        }
                    },
                    onCallClick = {
                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${lead.phoneNumber}"))
                        try {
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            // Fallback
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun PersonalKpiCard(
    title: String,
    value: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Slate600
                )
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(iconTint.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = value,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Slate900
            )

            Text(
                text = subtitle,
                fontSize = 10.sp,
                color = Slate500,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun MyActionLeadCard(
    lead: LeadEntity,
    onClick: () -> Unit,
    onWhatsAppClick: () -> Unit,
    onCallClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("my_lead_action_${lead.id}"),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = lead.name,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate900
                    )
                    Text(
                        text = "${lead.phoneNumber} • ${lead.campaignName}",
                        fontSize = 11.sp,
                        color = Slate600
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (lead.priority == "HOT") Color(0xFFFEE2E2) else Color(0xFFFEF3C7)
                ) {
                    Text(
                        text = lead.priority,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (lead.priority == "HOT") Color(0xFFB91C1C) else Color(0xFFB45309),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            if (lead.initialMessage.isNotBlank()) {
                Text(
                    text = "“${lead.initialMessage}”",
                    fontSize = 12.sp,
                    color = Slate700,
                    maxLines = 2
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // WhatsApp Button
                Button(
                    onClick = onWhatsAppClick,
                    colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreen),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Chat, contentDescription = "WhatsApp", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("WhatsApp", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                // Phone Call Button
                OutlinedButton(
                    onClick = onCallClick,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Phone, contentDescription = "Call", modifier = Modifier.size(16.dp), tint = RoyalBlue600)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Call", fontSize = 12.sp, color = RoyalBlue600)
                }
            }
        }
    }
}
