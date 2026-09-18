package com.example.ui.screens

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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CampaignRoutingRuleEntity
import com.example.data.model.SalesAgentEntity
import com.example.ui.theme.*

@Composable
fun TeamManagementScreen(
    agents: List<SalesAgentEntity>,
    campaignRules: List<CampaignRoutingRuleEntity>,
    onToggleActive: (SalesAgentEntity, Boolean) -> Unit,
    onAddNewAgent: (name: String, email: String, phone: String) -> Unit,
    onAddCampaignRule: (pattern: String, agentId: Long, agentName: String, description: String) -> Unit,
    onToggleCampaignRule: (ruleId: Long, isActive: Boolean) -> Unit,
    onDeleteCampaignRule: (ruleId: Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedSection by remember { mutableIntStateOf(0) } // 0: Team Reps & Round-Robin, 1: Custom Campaign Distribution

    var showAddAgentDialog by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var newEmail by remember { mutableStateOf("") }
    var newPhone by remember { mutableStateOf("") }

    var showAddRuleDialog by remember { mutableStateOf(false) }
    var ruleCampaignPattern by remember { mutableStateOf("") }
    var ruleDescription by remember { mutableStateOf("") }
    var selectedAgentForRule by remember { mutableStateOf<SalesAgentEntity?>(null) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Slate50)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Section Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (selectedSection == 0) "Sales Team & Round-Robin" else "Custom Campaign Distribution",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate900
                    )
                    Text(
                        text = if (selectedSection == 0)
                            "Manage agent capacity and active round-robin status"
                        else
                            "Route specific campaigns directly to specific sales reps",
                        fontSize = 12.sp,
                        color = Slate600
                    )
                }

                if (selectedSection == 0) {
                    Button(
                        onClick = { showAddAgentDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Emerald600),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier.testTag("add_agent_btn")
                    ) {
                        Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Rep", fontSize = 12.sp)
                    }
                } else {
                    Button(
                        onClick = {
                            selectedAgentForRule = agents.firstOrNull { it.role == "AGENT" && it.isActive }
                            showAddRuleDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = RoyalBlue600),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier.testTag("add_campaign_rule_btn")
                    ) {
                        Icon(Icons.Default.AddCircleOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("New Rule", fontSize = 12.sp)
                    }
                }
            }
        }

        // Section Tabs
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Slate100),
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = selectedSection == 0,
                        onClick = { selectedSection = 0 },
                        label = {
                            Text(
                                text = "👥 Sales Reps (${agents.count { it.role == "AGENT" }})",
                                fontSize = 12.sp,
                                fontWeight = if (selectedSection == 0) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Emerald600,
                            selectedLabelColor = Color.White,
                            containerColor = Color.Transparent,
                            labelColor = Slate700
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    FilterChip(
                        selected = selectedSection == 1,
                        onClick = { selectedSection = 1 },
                        label = {
                            Text(
                                text = "🎯 Campaign Rules (${campaignRules.size})",
                                fontSize = 12.sp,
                                fontWeight = if (selectedSection == 1) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = RoyalBlue600,
                            selectedLabelColor = Color.White,
                            containerColor = Color.Transparent,
                            labelColor = Slate700
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // SECTION 0: SALES TEAM MEMBERS
        if (selectedSection == 0) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4)),
                    shape = RoundedCornerShape(10.dp),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Color(0xFFBBF7D0)))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.Sync, contentDescription = null, tint = Emerald600)
                        Text(
                            text = "Standard Round-Robin: Incoming leads from general sources are distributed evenly among active sales reps in turn. Specific campaigns matching Custom Rules bypass this queue.",
                            fontSize = 11.sp,
                            color = Slate800,
                            lineHeight = 15.sp
                        )
                    }
                }
            }

            items(agents, key = { it.id }) { agent ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
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
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = agent.name,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Slate900
                                    )
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(if (agent.role == "MANAGER") Color(0xFFE0E7FF) else Slate100)
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = agent.role,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (agent.role == "MANAGER") RoyalBlue700 else Slate700
                                        )
                                    }
                                }
                                Text(
                                    text = "${agent.email} • ${agent.phoneNumber}",
                                    fontSize = 11.sp,
                                    color = Slate600
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${agent.leadsAssignedCount} Leads Assigned • ${agent.leadsConvertedCount} Deals Won",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Emerald600
                                )
                            }
                        }

                        if (agent.role == "AGENT") {
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = if (agent.isActive) "Round-Robin: ON" else "Paused",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (agent.isActive) Emerald600 else Color.Gray
                                )
                                Switch(
                                    checked = agent.isActive,
                                    onCheckedChange = { isActive -> onToggleActive(agent, isActive) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = Emerald600
                                    ),
                                    modifier = Modifier.height(26.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // SECTION 1: CUSTOM CAMPAIGN DISTRIBUTION RULES
        if (selectedSection == 1) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
                    shape = RoundedCornerShape(10.dp),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Color(0xFFBFDBFE)))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.Bolt, contentDescription = null, tint = RoyalBlue600)
                        Text(
                            text = "Custom Campaign Rules direct 100% of leads from a specific campaign keyword directly to your chosen sales rep, bypassing the Round-Robin rotation.",
                            fontSize = 11.sp,
                            color = Slate800,
                            lineHeight = 15.sp
                        )
                    }
                }
            }

            if (campaignRules.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.AltRoute,
                                contentDescription = null,
                                tint = Slate400,
                                modifier = Modifier.size(36.dp)
                            )
                            Text(
                                text = "No Custom Campaign Rules Yet",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = Slate800
                            )
                            Text(
                                text = "Create a rule to link a specific Meta Ad or marketing campaign exclusively to a dedicated sales rep.",
                                fontSize = 12.sp,
                                color = Slate600,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Button(
                                onClick = {
                                    selectedAgentForRule = agents.firstOrNull { it.role == "AGENT" && it.isActive }
                                    showAddRuleDialog = true
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = RoyalBlue600),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Create First Rule")
                            }
                        }
                    }
                }
            } else {
                items(campaignRules, key = { it.id }) { rule ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Rule Header: Pattern badge & Active Switch
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
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (rule.isActive) Color(0xFFDBEAFE) else Slate100)
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "🎯 Campaign: \"${rule.campaignPattern}\"",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (rule.isActive) RoyalBlue700 else Slate700
                                        )
                                    }

                                    if (rule.leadsRoutedCount > 0) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Color(0xFFDCFCE7))
                                                .padding(horizontal = 6.dp, vertical = 3.dp)
                                        ) {
                                            Text(
                                                text = "${rule.leadsRoutedCount} Routed",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Emerald600
                                            )
                                        }
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Switch(
                                        checked = rule.isActive,
                                        onCheckedChange = { isActive ->
                                            onToggleCampaignRule(rule.id, isActive)
                                        },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color.White,
                                            checkedTrackColor = RoyalBlue600
                                        ),
                                        modifier = Modifier.height(24.dp)
                                    )

                                    IconButton(
                                        onClick = { onDeleteCampaignRule(rule.id) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.DeleteOutline,
                                            contentDescription = "Delete Rule",
                                            tint = PriorityHot,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }

                            // Visual Routing Direction
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Slate50)
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = "Incoming Campaign",
                                        fontSize = 10.sp,
                                        color = Slate500
                                    )
                                    Text(
                                        text = rule.campaignPattern,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Slate800
                                    )
                                }

                                Icon(
                                    Icons.Default.ArrowForward,
                                    contentDescription = null,
                                    tint = RoyalBlue600,
                                    modifier = Modifier.size(18.dp)
                                )

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "Directly Assigned Rep",
                                        fontSize = 10.sp,
                                        color = Slate500
                                    )
                                    Text(
                                        text = "👤 ${rule.assignedAgentName}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Emerald600
                                    )
                                }
                            }

                            if (rule.ruleDescription.isNotBlank()) {
                                Text(
                                    text = rule.ruleDescription,
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

    // Add Agent Dialog
    if (showAddAgentDialog) {
        AlertDialog(
            onDismissRequest = { showAddAgentDialog = false },
            title = { Text("Add Sales Representative", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Full Name *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
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
                        value = newEmail,
                        onValueChange = { newEmail = it },
                        label = { Text("Work Email") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
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
                        value = newPhone,
                        onValueChange = { newPhone = it },
                        label = { Text("WhatsApp Phone Number *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
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
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newName.isNotBlank() && newPhone.isNotBlank()) {
                            onAddNewAgent(newName, newEmail, newPhone)
                            newName = ""
                            newEmail = ""
                            newPhone = ""
                            showAddAgentDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Emerald600)
                ) {
                    Text("Add to Pool")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddAgentDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Add Custom Campaign Routing Rule Dialog
    if (showAddRuleDialog) {
        val activeAgentsList = agents.filter { it.role == "AGENT" }

        AlertDialog(
            onDismissRequest = { showAddRuleDialog = false },
            title = {
                Column {
                    Text("Connect Campaign to Sales Rep", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(
                        "Configure dedicated lead delivery for a specific ad campaign",
                        fontSize = 12.sp,
                        color = Slate600
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = ruleCampaignPattern,
                        onValueChange = { ruleCampaignPattern = it },
                        label = { Text("Campaign Name or Keyword *") },
                        placeholder = { Text("e.g. Luxury 3BHK or Solar Rooftop") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
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

                    // Quick Preset Chips
                    Text("Suggested Presets:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Slate600)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("Luxury 3BHK", "Solar Rooftop", "Instagram Leads", "High Intent").forEach { preset ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (ruleCampaignPattern.contains(preset)) Color(0xFFDBEAFE) else Slate100)
                                    .clickable { ruleCampaignPattern = preset }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = preset,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (ruleCampaignPattern.contains(preset)) RoyalBlue700 else Slate700
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text("Assign Exclusively To:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Slate800)

                    // Sales Rep Selection List
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Slate200, RoundedCornerShape(8.dp))
                            .padding(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        activeAgentsList.forEach { agent ->
                            val isSelected = selectedAgentForRule?.id == agent.id
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSelected) Color(0xFFDCFCE7) else Color.Transparent)
                                    .clickable { selectedAgentForRule = agent }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(26.dp)
                                            .clip(CircleShape)
                                            .background(Color(agent.avatarColorHex)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = agent.name.take(1).uppercase(),
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = agent.name,
                                            fontSize = 13.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = Slate900
                                        )
                                        Text(
                                            text = "${agent.phoneNumber} • ${if (agent.isActive) "Active" else "Paused"}",
                                            fontSize = 10.sp,
                                            color = Slate600
                                        )
                                    }
                                }

                                if (isSelected) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = "Selected",
                                        tint = Emerald600,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = ruleDescription,
                        onValueChange = { ruleDescription = it },
                        label = { Text("Note / Strategy (Optional)") },
                        placeholder = { Text("e.g. Dedicated closing agent for premium properties") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
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
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val targetAgent = selectedAgentForRule
                        if (ruleCampaignPattern.isNotBlank() && targetAgent != null) {
                            onAddCampaignRule(
                                ruleCampaignPattern.trim(),
                                targetAgent.id,
                                targetAgent.name,
                                ruleDescription.trim()
                            )
                            ruleCampaignPattern = ""
                            ruleDescription = ""
                            showAddRuleDialog = false
                        }
                    },
                    enabled = ruleCampaignPattern.isNotBlank() && selectedAgentForRule != null,
                    colors = ButtonDefaults.buttonColors(containerColor = RoyalBlue600)
                ) {
                    Text("Save Rule")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddRuleDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
