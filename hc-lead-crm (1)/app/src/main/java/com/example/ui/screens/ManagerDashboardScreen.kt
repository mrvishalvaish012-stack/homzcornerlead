package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.LeadEntity
import com.example.data.model.SalesAgentEntity
import com.example.ui.theme.*
import com.example.ui.viewmodel.AnalyticsState

@Composable
fun ManagerDashboardScreen(
    analytics: AnalyticsState,
    allAgents: List<SalesAgentEntity>,
    allLeads: List<LeadEntity>,
    onToggleAgentActive: (SalesAgentEntity, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Slate50)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Banner Title
        item {
            Column {
                Text(
                    text = "Performance & Conversion Analytics",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Slate900
                )
                Text(
                    text = "Real-time metrics from WhatsApp & Meta Ads lead pipelines",
                    fontSize = 12.sp,
                    color = Slate600
                )
            }
        }

        // 4 KPI Metric Cards (Grid of 2x2)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Total Leads Ingested
                    KpiCard(
                        title = "Total Ingested",
                        value = "${analytics.totalLeads}",
                        subtitle = "${analytics.metaAdsLeadsCount} Meta • ${analytics.whatsAppLeadsCount} WA",
                        icon = Icons.Default.CloudDownload,
                        iconTint = RoyalBlue600,
                        modifier = Modifier.weight(1f)
                    )

                    // Conversion Rate
                    KpiCard(
                        title = "Conversion Rate",
                        value = String.format("%.1f%%", analytics.conversionRate),
                        subtitle = "${analytics.totalWon} Closed / Won",
                        icon = Icons.Default.TrendingUp,
                        iconTint = Emerald600,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Won / Closed Deals
                    KpiCard(
                        title = "Won Deals",
                        value = "${analytics.totalWon}",
                        subtitle = "${analytics.totalLost} Lost / Dropped",
                        icon = Icons.Default.CheckCircle,
                        iconTint = Color(0xFF10B981),
                        modifier = Modifier.weight(1f)
                    )

                    // Active Follow-ups & Repeats
                    KpiCard(
                        title = "In Follow-up",
                        value = "${analytics.totalFollowUps}",
                        subtitle = "${analytics.duplicateLeadsCount} Repeat Inquiries",
                        icon = Icons.Default.Sync,
                        iconTint = Color(0xFFF59E0B),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Funnel & Pipeline Stage Distribution Card
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
                        text = "Lead Pipeline Funnel",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate900
                    )

                    val newCount = allLeads.count { it.status == "NEW" }
                    val contactedCount = allLeads.count { it.status == "CONTACTED" }
                    val followUpCount = allLeads.count { it.status == "FOLLOW_UP" }
                    val quotationCount = allLeads.count { it.status == "QUOTATION" }
                    val wonCount = allLeads.count { it.status == "WON" }
                    val lostCount = allLeads.count { it.status == "LOST" }
                    val maxVal = maxOf(analytics.totalLeads, 1)

                    PipelineBar(label = "1. New Leads", count = newCount, max = maxVal, color = StatusNewText)
                    PipelineBar(label = "2. Contacted", count = contactedCount, max = maxVal, color = StatusContactedText)
                    PipelineBar(label = "3. In Follow-up", count = followUpCount, max = maxVal, color = StatusFollowUpText)
                    PipelineBar(label = "4. Quotation Sent", count = quotationCount, max = maxVal, color = StatusQuotationText)
                    PipelineBar(label = "5. Won / Converted", count = wonCount, max = maxVal, color = StatusWonText)
                    PipelineBar(label = "6. Lost / Disqualified", count = lostCount, max = maxVal, color = StatusLostText)
                }
            }
        }

        // Lead Sources Comparison Card
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
                        text = "Inbound Channel Performance",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate900
                    )

                    val metaTotal = allLeads.filter { it.source.contains("Meta", ignoreCase = true) }
                    val metaWon = metaTotal.count { it.status == "WON" }
                    val metaConv = if (metaTotal.isNotEmpty()) (metaWon.toFloat() / metaTotal.size) * 100f else 0f

                    val waTotal = allLeads.filter { it.source.contains("WhatsApp", ignoreCase = true) }
                    val waWon = waTotal.count { it.status == "WON" }
                    val waConv = if (waTotal.isNotEmpty()) (waWon.toFloat() / waTotal.size) * 100f else 0f

                    ChannelMetricRow(
                        name = "Meta Ads (Instagram & FB)",
                        total = metaTotal.size,
                        won = metaWon,
                        convRate = metaConv,
                        color = RoyalBlue600
                    )

                    HorizontalDivider(color = Slate100)

                    ChannelMetricRow(
                        name = "WhatsApp Direct Click-to-Chat",
                        total = waTotal.size,
                        won = waWon,
                        convRate = waConv,
                        color = WhatsAppGreen
                    )
                }
            }
        }

        // Sales Agent Performance Leaderboard
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Agent Performance & Round-Robin Pool",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Slate900
                )
                Text(
                    text = "${allAgents.count { it.role == "AGENT" && it.isActive }} Active Reps",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Emerald600
                )
            }
        }

        items(allAgents.filter { it.role == "AGENT" }, key = { it.id }) { agent ->
            AgentPerformanceCard(
                agent = agent,
                leads = allLeads.filter { it.assignedAgentId == agent.id },
                onToggleActive = { isActive -> onToggleAgentActive(agent, isActive) }
            )
        }
    }
}

@Composable
fun KpiCard(
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
                Text(title, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Slate600)
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(iconTint.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(16.dp))
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Slate900)
            Spacer(modifier = Modifier.height(2.dp))
            Text(subtitle, fontSize = 11.sp, color = Slate600)
        }
    }
}

@Composable
fun PipelineBar(label: String, count: Int, max: Int, color: Color) {
    val progress = (count.toFloat() / max.toFloat()).coerceIn(0f, 1f)
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Slate700)
            Text("$count leads", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Slate900)
        }
        Spacer(modifier = Modifier.height(3.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = color,
            trackColor = Slate100
        )
    }
}

@Composable
fun ChannelMetricRow(name: String, total: Int, won: Int, convRate: Float, color: Color) {
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
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Column {
                Text(name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Slate900)
                Text("$total leads captured • $won won", fontSize = 11.sp, color = Slate600)
            }
        }
        Text(
            text = String.format("%.1f%% Conv.", convRate),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = Emerald600
        )
    }
}

@Composable
fun AgentPerformanceCard(
    agent: SalesAgentEntity,
    leads: List<LeadEntity>,
    onToggleActive: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val totalAssigned = leads.size
    val won = leads.count { it.status == "WON" }
    val followUpsPending = leads.count { it.status in listOf("FOLLOW_UP", "CONTACTED", "NEW") }
    val overdue = leads.count {
        it.nextFollowUpDate != null && it.nextFollowUpDate <= System.currentTimeMillis() && it.status !in listOf("WON", "LOST")
    }
    val convRate = if (totalAssigned > 0) (won.toFloat() / totalAssigned) * 100f else 0f

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("agent_card_${agent.id}"),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header: Agent Name, Round-Robin Active Switch
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
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(agent.avatarColorHex)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = agent.name.take(1).uppercase(),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }

                    Column {
                        Text(
                            text = agent.name,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Slate900
                        )
                        Text(
                            text = agent.phoneNumber,
                            fontSize = 11.sp,
                            color = Slate600
                        )
                    }
                }

                // Round-Robin Switch
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = if (agent.isActive) "Active Pool" else "Paused",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (agent.isActive) Emerald600 else Color.Gray
                    )
                    Switch(
                        checked = agent.isActive,
                        onCheckedChange = onToggleActive,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Emerald600
                        ),
                        modifier = Modifier.height(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Stats grid in card
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Slate50)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Assigned", fontSize = 10.sp, color = Slate600)
                    Text("$totalAssigned", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Slate900)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Won", fontSize = 10.sp, color = Slate600)
                    Text("$won", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Emerald600)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Conv. Rate", fontSize = 10.sp, color = Slate600)
                    Text(String.format("%.1f%%", convRate), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = RoyalBlue600)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Overdue", fontSize = 10.sp, color = Slate600)
                    Text("$overdue", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (overdue > 0) Color(0xFFDC2626) else Slate600)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Pending", fontSize = 10.sp, color = Slate600)
                    Text("$followUpsPending", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Slate900)
                }
            }
        }
    }
}
