package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.SalesAgentEntity
import com.example.data.sync.CloudSyncState
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrmTopBar(
    isManagerMode: Boolean,
    currentAgent: SalesAgentEntity?,
    allAgents: List<SalesAgentEntity>,
    unreadNotificationCount: Int,
    cloudSyncState: CloudSyncState? = null,
    onCloudSyncClick: () -> Unit = {},
    onRoleSelected: (isManager: Boolean, agent: SalesAgentEntity?) -> Unit,
    onNotificationsClick: () -> Unit,
    onCheckRemindersClick: () -> Unit,
    onLogoutClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showRoleMenu by remember { mutableStateOf(false) }

    Surface(
        color = Slate900,
        contentColor = Color.White,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // App Logo & Title
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
                        Icon(
                            imageVector = Icons.Default.Chat,
                            contentDescription = "WhatsApp LeadPulse",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "LeadPulse CRM",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = if (isManagerMode) "Admin & Manager Portal" else "Sales Representative Portal",
                            fontSize = 10.sp,
                            color = if (isManagerMode) Color(0xFF38BDF8) else Emerald500
                        )
                    }
                }

                // Action Icons (Cloud Sync, Reminder check, Notifications, and Logout)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    // Team Cloud Sync Indicator & Trigger
                    val isSyncing = cloudSyncState?.isSyncing == true
                    val isConnected = cloudSyncState?.isConnected == true

                    val infiniteTransition = rememberInfiniteTransition(label = "topbar_sync_spin")
                    val spinAngle by infiniteTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 360f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1200, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart
                        ),
                        label = "topbar_spin"
                    )

                    IconButton(
                        onClick = onCloudSyncClick,
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("btn_cloud_sync")
                    ) {
                        Icon(
                            imageVector = when {
                                isSyncing -> Icons.Default.Sync
                                isConnected -> Icons.Default.CloudDone
                                else -> Icons.Default.CloudQueue
                            },
                            contentDescription = "Team Cloud Sync",
                            tint = when {
                                isSyncing -> Color(0xFF38BDF8)
                                isConnected -> Color(0xFF4ADE80)
                                else -> Color(0xFF94A3B8)
                            },
                            modifier = Modifier
                                .size(20.dp)
                                .then(if (isSyncing) Modifier.rotate(spinAngle) else Modifier)
                        )
                    }

                    // Check overdue reminders
                    IconButton(
                        onClick = onCheckRemindersClick,
                        modifier = Modifier.size(34.dp).testTag("check_reminders_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = "Trigger Reminder Check",
                            tint = Color(0xFFFBBF24),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Notification bell with badge
                    BadgedBox(
                        badge = {
                            if (unreadNotificationCount > 0) {
                                Badge(
                                    containerColor = Color(0xFFEF4444),
                                    contentColor = Color.White
                                ) {
                                    Text("$unreadNotificationCount")
                                }
                            }
                        }
                    ) {
                        IconButton(
                            onClick = onNotificationsClick,
                            modifier = Modifier.size(34.dp).testTag("notifications_bell_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = "View Notifications",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Logout Button
                    IconButton(
                        onClick = onLogoutClick,
                        modifier = Modifier.size(34.dp).testTag("btn_logout")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Logout,
                            contentDescription = "Logout",
                            tint = Color(0xFFF87171),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Role Status Pill
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Slate800)
                    .then(
                        if (isManagerMode) {
                            Modifier.clickable { showRoleMenu = true }
                        } else {
                            Modifier
                        }
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .testTag("role_switcher_pill"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = if (isManagerMode) Icons.Default.SupervisorAccount else Icons.Default.Lock,
                        contentDescription = "Active Role",
                        tint = if (isManagerMode) Color(0xFF38BDF8) else Emerald500,
                        modifier = Modifier.size(16.dp)
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = if (isManagerMode) "Admin / Manager" else currentAgent?.name ?: "Sales Rep",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = if (isManagerMode) "• Full Access" else "• My Leads Only",
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }

                if (isManagerMode) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "Switch",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF38BDF8)
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "Switch Role",
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Emerald900)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("Active", fontSize = 10.sp, color = Emerald100, fontWeight = FontWeight.Bold)
                    }
                }

                // Dropdown Menu for RBAC Switching (Admin only)
                if (isManagerMode) {
                    DropdownMenu(
                        expanded = showRoleMenu,
                        onDismissRequest = { showRoleMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(
                                        text = "👔 Manager (Full Admin)",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = "Review all leads, reports, conversion & agents",
                                        fontSize = 11.sp,
                                        color = Color.Gray
                                    )
                                }
                            },
                            leadingIcon = {
                                Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = Color(0xFF2563EB))
                            },
                            onClick = {
                                onRoleSelected(true, null)
                                showRoleMenu = false
                            }
                        )

                        HorizontalDivider()

                        Text(
                            text = "SALES REPS (DEMO REP VIEW)",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Gray,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )

                        allAgents.filter { it.role == "AGENT" }.forEach { agent ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = agent.name,
                                                fontWeight = FontWeight.Medium,
                                                fontSize = 13.sp
                                            )
                                            if (agent.isActive) {
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(Emerald100)
                                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                                ) {
                                                    Text("Round-Robin", fontSize = 9.sp, color = Emerald900)
                                                }
                                            }
                                        }
                                        Text(
                                            text = "${agent.leadsAssignedCount} assigned • ${agent.leadsConvertedCount} won",
                                            fontSize = 11.sp,
                                            color = Color.Gray
                                        )
                                    }
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.PersonOutline, contentDescription = null, tint = Emerald500)
                                },
                                onClick = {
                                    onRoleSelected(false, agent)
                                    showRoleMenu = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
