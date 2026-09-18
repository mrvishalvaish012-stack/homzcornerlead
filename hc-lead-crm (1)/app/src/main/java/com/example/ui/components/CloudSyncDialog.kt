package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.sync.CloudSyncState
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudSyncDialog(
    syncState: CloudSyncState,
    onDismiss: () -> Unit,
    onSyncNow: ((onResult: (Boolean, String) -> Unit) -> Unit),
    onUpdateServerUrl: (String) -> Unit,
    onToggleAutoSync: (Boolean) -> Unit,
    onCheckHealth: ((onResult: (Boolean, String) -> Unit) -> Unit)
) {
    val clipboardManager = LocalClipboardManager.current
    var inputUrl by remember(syncState.serverUrl) { mutableStateOf(syncState.serverUrl) }
    var isCheckingHealth by remember { mutableStateOf(false) }
    var healthMessage by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var syncFeedbackMessage by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var copiedToClipboard by remember { mutableStateOf(false) }

    // Rotating animation for sync icon
    val infiniteTransition = rememberInfiniteTransition(label = "sync_rotation")
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotate_angle"
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f)
                .clip(RoundedCornerShape(24.dp))
                .testTag("cloud_sync_dialog"),
            color = Color.White,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
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
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFEFF6FF)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudSync,
                                contentDescription = null,
                                tint = Color(0xFF2563EB),
                                modifier = Modifier
                                    .size(24.dp)
                                    .then(if (syncState.isSyncing) Modifier.rotate(rotationAngle) else Modifier)
                            )
                        }
                        Column {
                            Text(
                                text = "Team Cloud Backend",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Slate900
                            )
                            Text(
                                text = "Multi-device real-time sync for sales teams",
                                fontSize = 12.sp,
                                color = Slate500
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Slate400)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Scrollable Content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Live Status Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (syncState.isConnected) Color(0xFFF0FDF4) else Color(0xFFF8FAFC)
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (syncState.isConnected) Color(0xFFBBF7D0) else Slate200
                        )
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(if (syncState.isConnected) Color(0xFF16A34A) else Color(0xFFEF4444))
                                    )
                                    Text(
                                        text = if (syncState.isConnected) "Connected to Central Cloud" else "Cloud Disconnected / Offline",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (syncState.isConnected) Color(0xFF15803D) else Color(0xFFB91C1C)
                                    )
                                }

                                if (syncState.lastLatencyMs > 0 && syncState.isConnected) {
                                    Text(
                                        "${syncState.lastLatencyMs} ms",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFF16A34A)
                                    )
                                }
                            }

                            val timeText = if (syncState.lastSyncTimestamp > 0) {
                                val sdf = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
                                "Last Synced: ${sdf.format(Date(syncState.lastSyncTimestamp))}"
                            } else {
                                "Not synced yet"
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(timeText, fontSize = 11.sp, color = Slate600)
                                if (syncState.totalServerLeads > 0) {
                                    Text("Leads on Cloud: ${syncState.totalServerLeads}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Slate700)
                                }
                            }
                        }
                    }

                    // Server URL Configuration
                    Column {
                        Text(
                            text = "Central Cloud Backend URL",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Slate800
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = inputUrl,
                            onValueChange = { inputUrl = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("cloud_server_url_input"),
                            placeholder = { Text("https://your-crm-server.onrender.com", fontSize = 12.sp) },
                            singleLine = true,
                            textStyle = TextStyle(fontSize = 12.sp, fontFamily = FontFamily.Monospace),
                            shape = RoundedCornerShape(12.dp),
                            trailingIcon = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = {
                                        val clip = clipboardManager.getText()?.text
                                        if (!clip.isNullOrBlank()) {
                                            inputUrl = clip.trim()
                                        }
                                    }) {
                                        Icon(Icons.Default.ContentPaste, contentDescription = "Paste", tint = Color(0xFF2563EB), modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Every sales team member must enter this same URL on their phone to share data.",
                            fontSize = 10.sp,
                            color = Slate500
                        )
                    }

                    // Save URL & Test Connection Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                isCheckingHealth = true
                                healthMessage = null
                                onUpdateServerUrl(inputUrl)
                                onCheckHealth { success, msg ->
                                    isCheckingHealth = false
                                    healthMessage = Pair(success, msg)
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            enabled = !isCheckingHealth && inputUrl.isNotBlank()
                        ) {
                            if (isCheckingHealth) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Pinging...", fontSize = 11.sp)
                            } else {
                                Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Test Health", fontSize = 11.sp)
                            }
                        }

                        Button(
                            onClick = {
                                onUpdateServerUrl(inputUrl)
                                syncFeedbackMessage = null
                                onSyncNow { success, msg ->
                                    syncFeedbackMessage = Pair(success, msg)
                                }
                            },
                            modifier = Modifier.weight(1.2f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                            enabled = !syncState.isSyncing && inputUrl.isNotBlank()
                        ) {
                            if (syncState.isSyncing) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Color.White, strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Syncing...", fontSize = 11.sp)
                            } else {
                                Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Sync All Devices", fontSize = 11.sp)
                            }
                        }
                    }

                    // Health / Sync Result Messages
                    healthMessage?.let { (success, msg) ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (success) Color(0xFFDCFCE7) else Color(0xFFFEE2E2))
                                .padding(8.dp)
                        ) {
                            Text(
                                text = msg,
                                fontSize = 11.sp,
                                color = if (success) Color(0xFF166534) else Color(0xFF991B1B)
                            )
                        }
                    }

                    syncFeedbackMessage?.let { (success, msg) ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (success) Color(0xFFDCFCE7) else Color(0xFFFEE2E2))
                                .padding(8.dp)
                        ) {
                            Text(
                                text = msg,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (success) Color(0xFF166534) else Color(0xFF991B1B)
                            )
                        }
                    }

                    // Auto-sync Switch Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Slate50),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Slate200)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "⚡ Real-Time Auto Sync",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Slate900
                                )
                                Text(
                                    text = "Automatically polls & pushes updates every 20s so your team sees leads immediately",
                                    fontSize = 11.sp,
                                    color = Slate600
                                )
                            }
                            Switch(
                                checked = syncState.autoSyncEnabled,
                                onCheckedChange = onToggleAutoSync,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = Color(0xFF2563EB)
                                )
                            )
                        }
                    }

                    // Multi-Device Setup Guide
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Slate200)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.Devices, contentDescription = null, tint = Color(0xFF0284C7), modifier = Modifier.size(18.dp))
                                Text(
                                    text = "📱 How to Connect Your Entire Sales Team",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Slate900
                                )
                            }

                            Text("1. Install this app on all your sales representatives' Android phones.", fontSize = 11.sp, color = Slate700)
                            Text("2. Open this 'Team Cloud Backend' popup on each phone.", fontSize = 11.sp, color = Slate700)
                            Text("3. Enter or paste this exact Backend URL on every phone.", fontSize = 11.sp, color = Slate700)
                            Text("4. Click 'Sync All Devices'.", fontSize = 11.sp, color = Slate700)
                            Text("✨ That's it! When any salesperson updates a lead status or note, it instantly reflects on all team members' screens!", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF0284C7))

                            Spacer(modifier = Modifier.height(4.dp))

                            Button(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(syncState.serverUrl))
                                    copiedToClipboard = true
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Slate800)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (copiedToClipboard) "Copied to Clipboard! ✓" else "Copy Backend URL for Team", fontSize = 11.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Bottom Close Button
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Slate100, contentColor = Slate800)
                ) {
                    Text("Done", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
