package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.SalesAgentEntity
import com.example.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun LoginScreen(
    allAgents: List<SalesAgentEntity>,
    onLoginAsAdmin: (pin: String, onResult: (Boolean, String) -> Unit) -> Unit,
    onSendLoginOtp: (phone: String, onResult: (Boolean, String, String?) -> Unit) -> Unit,
    onVerifyLoginOtp: (phone: String, otp: String, onResult: (Boolean, String) -> Unit) -> Unit,
    onSendRegOtp: (phone: String, onResult: (Boolean, String, String?) -> Unit) -> Unit,
    onVerifyAndRegister: (name: String, email: String, phone: String, department: String, otp: String, onResult: (Boolean, String) -> Unit) -> Unit,
    modifier: Modifier = Modifier
) {
    // 0: Sales Representative, 1: Admin / Manager
    var selectedPortalTab by remember { mutableIntStateOf(0) }

    // Inside Sales Portal: 0 = Sign In with OTP, 1 = Register with OTP
    var salesAuthMode by remember { mutableIntStateOf(0) }

    // Sign In via OTP State
    var loginPhone by remember { mutableStateOf("") }
    var loginOtp by remember { mutableStateOf("") }
    var isLoginOtpSent by remember { mutableStateOf(false) }
    var receivedLoginOtpPreview by remember { mutableStateOf<String?>(null) }
    var loginResendCountdown by remember { mutableIntStateOf(0) }

    // Registration via OTP State
    var regName by remember { mutableStateOf("") }
    var regPhone by remember { mutableStateOf("") }
    var regEmail by remember { mutableStateOf("") }
    var regDepartment by remember { mutableStateOf("Inbound Sales") }
    var regOtp by remember { mutableStateOf("") }
    var isRegOtpSent by remember { mutableStateOf(false) }
    var receivedRegOtpPreview by remember { mutableStateOf<String?>(null) }
    var regResendCountdown by remember { mutableIntStateOf(0) }

    // Admin Access State
    var adminPin by remember { mutableStateOf("1234") }
    var isAdminPinVisible by remember { mutableStateOf(false) }

    // UI Feedback
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    // Countdown timers for OTP resend
    LaunchedEffect(isLoginOtpSent, loginResendCountdown) {
        if (isLoginOtpSent && loginResendCountdown > 0) {
            delay(1000L)
            loginResendCountdown -= 1
        }
    }
    LaunchedEffect(isRegOtpSent, regResendCountdown) {
        if (isRegOtpSent && regResendCountdown > 0) {
            delay(1000L)
            regResendCountdown -= 1
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Slate900)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Header Branding
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(Emerald600),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Chat,
                        contentDescription = "LeadPulse Logo",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "LeadPulse CRM",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Text(
                    text = "Passwordless Mobile OTP Login & Live Lead Routing",
                    fontSize = 12.sp,
                    color = Emerald500,
                    fontWeight = FontWeight.Medium
                )
            }

            // Main Container Card
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                color = Slate50,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Top Portal Tabs (Sales Rep vs Admin)
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Slate200),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(4.dp)
                            ) {
                                // Sales Rep Tab
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (selectedPortalTab == 0) Emerald600 else Color.Transparent)
                                        .clickable {
                                            selectedPortalTab = 0
                                            errorMessage = null
                                            successMessage = null
                                        }
                                        .padding(vertical = 10.dp)
                                        .testTag("tab_portal_sales_rep"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.PhoneIphone,
                                            contentDescription = null,
                                            tint = if (selectedPortalTab == 0) Color.White else Slate700,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "Sales Rep (OTP)",
                                            fontSize = 12.sp,
                                            fontWeight = if (selectedPortalTab == 0) FontWeight.Bold else FontWeight.Medium,
                                            color = if (selectedPortalTab == 0) Color.White else Slate700
                                        )
                                    }
                                }

                                // Admin Tab
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (selectedPortalTab == 1) RoyalBlue600 else Color.Transparent)
                                        .clickable {
                                            selectedPortalTab = 1
                                            errorMessage = null
                                            successMessage = null
                                        }
                                        .padding(vertical = 10.dp)
                                        .testTag("tab_portal_admin"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.AdminPanelSettings,
                                            contentDescription = null,
                                            tint = if (selectedPortalTab == 1) Color.White else Slate700,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "Admin / Manager",
                                            fontSize = 12.sp,
                                            fontWeight = if (selectedPortalTab == 1) FontWeight.Bold else FontWeight.Medium,
                                            color = if (selectedPortalTab == 1) Color.White else Slate700
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // TAB 0: SALES REPRESENTATIVE PORTAL (PHONE NUMBER OTP)
                    if (selectedPortalTab == 0) {
                        // Sub-toggle: Sign In via OTP vs Register via OTP
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, Slate200, RoundedCornerShape(10.dp))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (salesAuthMode == 0) Emerald50 else Color.Transparent)
                                            .clickable {
                                                salesAuthMode = 0
                                                errorMessage = null
                                            }
                                            .padding(vertical = 8.dp)
                                            .testTag("subtab_sales_otp_signin"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "Sign In with OTP",
                                            fontSize = 13.sp,
                                            fontWeight = if (salesAuthMode == 0) FontWeight.Bold else FontWeight.Normal,
                                            color = if (salesAuthMode == 0) Emerald700 else Slate600
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (salesAuthMode == 1) Emerald50 else Color.Transparent)
                                            .clickable {
                                                salesAuthMode = 1
                                                errorMessage = null
                                            }
                                            .padding(vertical = 8.dp)
                                            .testTag("subtab_sales_otp_register"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "Register with OTP",
                                            fontSize = 13.sp,
                                            fontWeight = if (salesAuthMode == 1) FontWeight.Bold else FontWeight.Normal,
                                            color = if (salesAuthMode == 1) Emerald700 else Slate600
                                        )
                                    }
                                }
                            }
                        }

                        // SUB-MODE 0: SIGN IN VIA PHONE NUMBER OTP
                        if (salesAuthMode == 0) {
                            if (!isLoginOtpSent) {
                                // Step 1: Enter Phone Number
                                item {
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = Emerald50),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Sms,
                                                contentDescription = null,
                                                tint = Emerald600,
                                                modifier = Modifier.size(24.dp)
                                            )
                                            Column {
                                                Text(
                                                    text = "Instant Mobile OTP Sign In",
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Emerald900
                                                )
                                                Text(
                                                    text = "No password needed! Enter your registered mobile number and we will send a 6-digit verification code.",
                                                    fontSize = 11.sp,
                                                    color = Slate700,
                                                    lineHeight = 15.sp
                                                )
                                            }
                                        }
                                    }
                                }

                                item {
                                    OutlinedTextField(
                                        value = loginPhone,
                                        onValueChange = {
                                            loginPhone = it
                                            errorMessage = null
                                        },
                                        label = { Text("Mobile Phone Number") },
                                        placeholder = { Text("+91 98765 43210") },
                                        leadingIcon = {
                                            Icon(Icons.Default.Phone, contentDescription = null, tint = Emerald600)
                                        },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth().testTag("input_login_phone")
                                    )
                                }

                                item {
                                    Button(
                                        onClick = {
                                            if (loginPhone.trim().length < 8) {
                                                errorMessage = "Please enter a valid mobile phone number."
                                                return@Button
                                            }
                                            isLoading = true
                                            errorMessage = null
                                            onSendLoginOtp(loginPhone) { success, msg, otp ->
                                                isLoading = false
                                                if (success) {
                                                    isLoginOtpSent = true
                                                    receivedLoginOtpPreview = otp
                                                    loginResendCountdown = 30
                                                    successMessage = "OTP sent to $loginPhone"
                                                } else {
                                                    errorMessage = msg
                                                }
                                            }
                                        },
                                        enabled = !isLoading,
                                        colors = ButtonDefaults.buttonColors(containerColor = Emerald600),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .testTag("btn_send_login_otp")
                                    ) {
                                        if (isLoading) {
                                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Sending OTP via SMS...")
                                        } else {
                                            Icon(Icons.Default.Send, contentDescription = null)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Send OTP to Mobile", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                // Quick Selector for registered team members
                                val registeredReps = allAgents.filter { it.role == "AGENT" }
                                if (registeredReps.isNotEmpty()) {
                                    item {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Or tap a team member to prefill phone:",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Slate600
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        LazyRow(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            items(registeredReps, key = { it.id }) { agent ->
                                                Surface(
                                                    color = Color.White,
                                                    shape = RoundedCornerShape(10.dp),
                                                    border = androidx.compose.foundation.BorderStroke(1.dp, Slate200),
                                                    modifier = Modifier
                                                        .clickable {
                                                            loginPhone = agent.phoneNumber
                                                            errorMessage = null
                                                        }
                                                        .testTag("quick_rep_phone_${agent.id}")
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                    ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(24.dp)
                                                                .clip(CircleShape)
                                                                .background(Color(agent.avatarColorHex)),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Text(
                                                                text = agent.name.take(1),
                                                                color = Color.White,
                                                                fontSize = 11.sp,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                        }
                                                        Column {
                                                            Text(
                                                                text = agent.name,
                                                                fontSize = 11.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = Slate800
                                                            )
                                                            Text(
                                                                text = agent.phoneNumber,
                                                                fontSize = 10.sp,
                                                                color = Slate500
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                // Step 2: OTP Sent -> Verify OTP
                                item {
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = Emerald50),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(14.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Emerald600)
                                                    Text(
                                                        text = "OTP Sent to $loginPhone",
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 13.sp,
                                                        color = Emerald900
                                                    )
                                                }
                                                TextButton(
                                                    onClick = {
                                                        isLoginOtpSent = false
                                                        loginOtp = ""
                                                        errorMessage = null
                                                    }
                                                ) {
                                                    Text("Change", fontSize = 12.sp, color = Emerald700)
                                                }
                                            }

                                            // Quick Auto-fill Banner
                                            receivedLoginOtpPreview?.let { otpCode ->
                                                Surface(
                                                    color = Color.White,
                                                    shape = RoundedCornerShape(8.dp),
                                                    border = androidx.compose.foundation.BorderStroke(1.dp, Emerald400),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Column {
                                                            Text(
                                                                text = "📲 SMS Notification Received:",
                                                                fontSize = 10.sp,
                                                                color = Slate500
                                                            )
                                                            Text(
                                                                text = "OTP: $otpCode",
                                                                fontSize = 14.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = Emerald900,
                                                                fontFamily = FontFamily.Monospace
                                                            )
                                                        }
                                                        Button(
                                                            onClick = { loginOtp = otpCode },
                                                            colors = ButtonDefaults.buttonColors(containerColor = Emerald600),
                                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                            shape = RoundedCornerShape(6.dp)
                                                        ) {
                                                            Text("Auto-fill", fontSize = 11.sp)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                item {
                                    OutlinedTextField(
                                        value = loginOtp,
                                        onValueChange = {
                                            if (it.length <= 6) {
                                                loginOtp = it
                                                errorMessage = null
                                            }
                                        },
                                        label = { Text("Enter 6-Digit OTP") },
                                        placeholder = { Text("e.g. 123456") },
                                        leadingIcon = {
                                            Icon(Icons.Default.Key, contentDescription = null, tint = Emerald600)
                                        },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        textStyle = TextStyle(
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 4.sp,
                                            textAlign = TextAlign.Center
                                        ),
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth().testTag("input_login_otp")
                                    )
                                }

                                item {
                                    Button(
                                        onClick = {
                                            if (loginOtp.isBlank()) {
                                                errorMessage = "Please enter the 6-digit OTP."
                                                return@Button
                                            }
                                            isLoading = true
                                            errorMessage = null
                                            onVerifyLoginOtp(loginPhone, loginOtp) { success, msg ->
                                                isLoading = false
                                                if (!success) {
                                                    errorMessage = msg
                                                }
                                            }
                                        },
                                        enabled = !isLoading,
                                        colors = ButtonDefaults.buttonColors(containerColor = Emerald600),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .testTag("btn_verify_login_otp")
                                    ) {
                                        if (isLoading) {
                                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Verifying OTP...")
                                        } else {
                                            Icon(Icons.Default.Verified, contentDescription = null)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Verify OTP & Enter CRM", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                item {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (loginResendCountdown > 0) {
                                            Text(
                                                text = "Resend OTP in ${loginResendCountdown}s",
                                                fontSize = 12.sp,
                                                color = Slate500
                                            )
                                        } else {
                                            TextButton(
                                                onClick = {
                                                    isLoading = true
                                                    onSendLoginOtp(loginPhone) { success, msg, otp ->
                                                        isLoading = false
                                                        if (success) {
                                                            receivedLoginOtpPreview = otp
                                                            loginResendCountdown = 30
                                                            successMessage = "New OTP resent to $loginPhone"
                                                        } else {
                                                            errorMessage = msg
                                                        }
                                                    }
                                                }
                                            ) {
                                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Resend OTP Now", fontSize = 12.sp, color = Emerald700)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // SUB-MODE 1: REGISTER VIA PHONE NUMBER OTP
                        if (salesAuthMode == 1) {
                            if (!isRegOtpSent) {
                                item {
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = Emerald50),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.PersonAdd,
                                                contentDescription = null,
                                                tint = Emerald600,
                                                modifier = Modifier.size(24.dp)
                                            )
                                            Column {
                                                Text(
                                                    text = "Register with Mobile Phone OTP",
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Emerald900
                                                )
                                                Text(
                                                    text = "No password required! We verify your sales account instantly with a mobile OTP. Once verified, inbound leads will be assigned to you.",
                                                    fontSize = 11.sp,
                                                    color = Slate700,
                                                    lineHeight = 15.sp
                                                )
                                            }
                                        }
                                    }
                                }

                                item {
                                    OutlinedTextField(
                                        value = regName,
                                        onValueChange = {
                                            regName = it
                                            errorMessage = null
                                        },
                                        label = { Text("Full Name *") },
                                        placeholder = { Text("e.g. Vikram Malhotra") },
                                        leadingIcon = {
                                            Icon(Icons.Default.Person, contentDescription = null, tint = Emerald600)
                                        },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth().testTag("input_reg_otp_name")
                                    )
                                }

                                item {
                                    OutlinedTextField(
                                        value = regPhone,
                                        onValueChange = {
                                            regPhone = it
                                            errorMessage = null
                                        },
                                        label = { Text("Mobile Phone Number *") },
                                        placeholder = { Text("+91 98765 43210") },
                                        leadingIcon = {
                                            Icon(Icons.Default.Phone, contentDescription = null, tint = Emerald600)
                                        },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth().testTag("input_reg_otp_phone")
                                    )
                                }

                                item {
                                    OutlinedTextField(
                                        value = regEmail,
                                        onValueChange = {
                                            regEmail = it
                                            errorMessage = null
                                        },
                                        label = { Text("Work Email (Optional)") },
                                        placeholder = { Text("vikram@company.com") },
                                        leadingIcon = {
                                            Icon(Icons.Default.Email, contentDescription = null, tint = Emerald600)
                                        },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth().testTag("input_reg_otp_email")
                                    )
                                }

                                item {
                                    OutlinedTextField(
                                        value = regDepartment,
                                        onValueChange = {
                                            regDepartment = it
                                            errorMessage = null
                                        },
                                        label = { Text("Department / Branch") },
                                        placeholder = { Text("Inbound Sales, Real Estate Desk") },
                                        leadingIcon = {
                                            Icon(Icons.Default.Business, contentDescription = null, tint = Emerald600)
                                        },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth().testTag("input_reg_otp_department")
                                    )
                                }

                                item {
                                    Button(
                                        onClick = {
                                            if (regName.isBlank()) {
                                                errorMessage = "Please enter your full name."
                                                return@Button
                                            }
                                            if (regPhone.trim().length < 8) {
                                                errorMessage = "Please enter a valid mobile phone number."
                                                return@Button
                                            }
                                            isLoading = true
                                            errorMessage = null
                                            onSendRegOtp(regPhone) { success, msg, otp ->
                                                isLoading = false
                                                if (success) {
                                                    isRegOtpSent = true
                                                    receivedRegOtpPreview = otp
                                                    regResendCountdown = 30
                                                    successMessage = "Verification OTP sent to $regPhone"
                                                } else {
                                                    errorMessage = msg
                                                }
                                            }
                                        },
                                        enabled = !isLoading,
                                        colors = ButtonDefaults.buttonColors(containerColor = Emerald600),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .testTag("btn_send_reg_otp")
                                    ) {
                                        if (isLoading) {
                                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Sending Verification OTP...")
                                        } else {
                                            Icon(Icons.Default.Send, contentDescription = null)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Send Verification OTP", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            } else {
                                // Step 2: Verification of Registration OTP
                                item {
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = Emerald50),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(14.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = Emerald600)
                                                    Text(
                                                        text = "Verify $regName ($regPhone)",
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 13.sp,
                                                        color = Emerald900
                                                    )
                                                }
                                                TextButton(
                                                    onClick = {
                                                        isRegOtpSent = false
                                                        regOtp = ""
                                                        errorMessage = null
                                                    }
                                                ) {
                                                    Text("Edit", fontSize = 12.sp, color = Emerald700)
                                                }
                                            }

                                            receivedRegOtpPreview?.let { otpCode ->
                                                Surface(
                                                    color = Color.White,
                                                    shape = RoundedCornerShape(8.dp),
                                                    border = androidx.compose.foundation.BorderStroke(1.dp, Emerald400),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Column {
                                                            Text(
                                                                text = "📲 SMS Notification Received:",
                                                                fontSize = 10.sp,
                                                                color = Slate500
                                                            )
                                                            Text(
                                                                text = "OTP: $otpCode",
                                                                fontSize = 14.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = Emerald900,
                                                                fontFamily = FontFamily.Monospace
                                                            )
                                                        }
                                                        Button(
                                                            onClick = { regOtp = otpCode },
                                                            colors = ButtonDefaults.buttonColors(containerColor = Emerald600),
                                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                            shape = RoundedCornerShape(6.dp)
                                                        ) {
                                                            Text("Auto-fill", fontSize = 11.sp)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                item {
                                    OutlinedTextField(
                                        value = regOtp,
                                        onValueChange = {
                                            if (it.length <= 6) {
                                                regOtp = it
                                                errorMessage = null
                                            }
                                        },
                                        label = { Text("Enter 6-Digit OTP") },
                                        placeholder = { Text("e.g. 123456") },
                                        leadingIcon = {
                                            Icon(Icons.Default.Key, contentDescription = null, tint = Emerald600)
                                        },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        textStyle = TextStyle(
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 4.sp,
                                            textAlign = TextAlign.Center
                                        ),
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth().testTag("input_reg_otp_code")
                                    )
                                }

                                item {
                                    Button(
                                        onClick = {
                                            if (regOtp.isBlank()) {
                                                errorMessage = "Please enter the 6-digit OTP code."
                                                return@Button
                                            }
                                            isLoading = true
                                            errorMessage = null
                                            onVerifyAndRegister(
                                                regName,
                                                regEmail,
                                                regPhone,
                                                regDepartment,
                                                regOtp
                                            ) { success, msg ->
                                                isLoading = false
                                                if (!success) {
                                                    errorMessage = msg
                                                }
                                            }
                                        },
                                        enabled = !isLoading,
                                        colors = ButtonDefaults.buttonColors(containerColor = Emerald600),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .testTag("btn_verify_reg_otp")
                                    ) {
                                        if (isLoading) {
                                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Activating Sales Account...")
                                        } else {
                                            Icon(Icons.Default.CheckCircle, contentDescription = null)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Verify & Activate Account", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                item {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (regResendCountdown > 0) {
                                            Text(
                                                text = "Resend OTP in ${regResendCountdown}s",
                                                fontSize = 12.sp,
                                                color = Slate500
                                            )
                                        } else {
                                            TextButton(
                                                onClick = {
                                                    isLoading = true
                                                    onSendRegOtp(regPhone) { success, msg, otp ->
                                                        isLoading = false
                                                        if (success) {
                                                            receivedRegOtpPreview = otp
                                                            regResendCountdown = 30
                                                            successMessage = "New OTP resent to $regPhone"
                                                        } else {
                                                            errorMessage = msg
                                                        }
                                                    }
                                                }
                                            ) {
                                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Resend OTP Now", fontSize = 12.sp, color = Emerald700)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // TAB 1: ADMIN / MANAGER PORTAL
                    if (selectedPortalTab == 1) {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = RoyalBlue50),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        Icons.Default.AdminPanelSettings,
                                        contentDescription = null,
                                        tint = RoyalBlue600,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Column {
                                        Text(
                                            text = "Administrator / Manager Portal",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = RoyalBlue800
                                        )
                                        Text(
                                            text = "Full CRM oversight: Master pipeline, Round-Robin campaign assignment, and Excel batch distribution.",
                                            fontSize = 11.sp,
                                            color = Slate700,
                                            lineHeight = 15.sp
                                        )
                                    }
                                }
                            }
                        }

                        item {
                            OutlinedTextField(
                                value = adminPin,
                                onValueChange = {
                                    adminPin = it
                                    errorMessage = null
                                },
                                label = { Text("Master Admin PIN / Password") },
                                placeholder = { Text("Default: 1234") },
                                leadingIcon = {
                                    Icon(Icons.Default.Key, contentDescription = null, tint = RoyalBlue600)
                                },
                                trailingIcon = {
                                    IconButton(onClick = { isAdminPinVisible = !isAdminPinVisible }) {
                                        Icon(
                                            imageVector = if (isAdminPinVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                            contentDescription = "Toggle PIN",
                                            tint = Slate500
                                        )
                                    }
                                },
                                visualTransformation = if (isAdminPinVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("input_admin_pin")
                            )
                        }

                        item {
                            Button(
                                onClick = {
                                    if (adminPin.isBlank()) {
                                        errorMessage = "Admin PIN is required (Default: 1234)."
                                        return@Button
                                    }
                                    isLoading = true
                                    errorMessage = null
                                    onLoginAsAdmin(adminPin) { success, msg ->
                                        isLoading = false
                                        if (!success) {
                                            errorMessage = msg
                                        }
                                    }
                                },
                                enabled = !isLoading,
                                colors = ButtonDefaults.buttonColors(containerColor = RoyalBlue600),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("btn_admin_login")
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Validating Access...")
                                } else {
                                    Icon(Icons.Default.VerifiedUser, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Login as Administrator", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // Error Message Banner
                    if (errorMessage != null) {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFFEE2E2)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth().testTag("auth_error_card")
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ErrorOutline,
                                        contentDescription = "Error",
                                        tint = Color(0xFFDC2626),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = errorMessage ?: "",
                                        color = Color(0xFF991B1B),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }

                    // Success Message Banner
                    if (successMessage != null) {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Emerald50),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Success",
                                        tint = Emerald600,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = successMessage ?: "",
                                        color = Emerald900,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
