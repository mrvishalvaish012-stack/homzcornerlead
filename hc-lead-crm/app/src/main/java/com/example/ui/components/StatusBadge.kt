package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

@Composable
fun StatusBadge(status: String, modifier: Modifier = Modifier) {
    val (bgColor, textColor, label) = when (status) {
        "NEW" -> Triple(StatusNewBg, StatusNewText, "New Lead")
        "CONTACTED" -> Triple(StatusContactedBg, StatusContactedText, "Contacted")
        "FOLLOW_UP" -> Triple(StatusFollowUpBg, StatusFollowUpText, "Follow-Up Due")
        "QUOTATION" -> Triple(StatusQuotationBg, StatusQuotationText, "Quotation Sent")
        "WON" -> Triple(StatusWonBg, StatusWonText, "Won / Converted")
        "LOST" -> Triple(StatusLostBg, StatusLostText, "Lost / Closed")
        else -> Triple(Slate200, Slate700, status)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun PriorityBadge(priority: String, modifier: Modifier = Modifier) {
    val (color, label) = when (priority) {
        "HOT" -> Pair(PriorityHot, "🔥 HOT")
        "WARM" -> Pair(PriorityWarm, "⚡ WARM")
        else -> Pair(PriorityCold, "❄ COLD")
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 6.dp, vertical = 3.dp)
    ) {
        Text(
            text = label,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun DuplicateBadge(hitCount: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFFFEF3C7))
            .padding(horizontal = 7.dp, vertical = 3.dp)
    ) {
        Text(
            text = "🔁 Repeat Lead (x$hitCount)",
            color = Color(0xFFB45309),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
