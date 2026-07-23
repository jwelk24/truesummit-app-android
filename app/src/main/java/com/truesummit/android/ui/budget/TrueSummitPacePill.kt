package com.truesummit.android.ui.budget

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.truesummit.android.service.GoalPace
import com.truesummit.android.ui.transactions.formatCurrency

@Composable
fun TrueSummitPacePill(
    pace: GoalPace,
    modifier: Modifier = Modifier
) {
    val label = when (pace) {
        GoalPace.Reached -> "Goal reached"
        is GoalPace.OnTrack -> if (pace.monthsEarly >= 1) "On pace · ${pace.monthsEarly}mo early" else "On pace"
        is GoalPace.Behind -> if (pace.monthsLate >= 1) "Behind · ${pace.monthsLate}mo late" else "Behind"
        GoalPace.Unfunded -> "No contributions"
        is GoalPace.ShortThisMonth -> "+${formatCurrency(pace.amountNeeded.toDouble())} this month"
        is GoalPace.Projecting -> "${pace.monthsToGoal}mo to goal"
        GoalPace.FundedThisMonth -> "Funded this month"
        is GoalPace.NeedToStayOnTrack -> "+${formatCurrency(pace.amountNeeded.toDouble())} to stay on track"
    }
    val icon: ImageVector = when (pace) {
        GoalPace.Reached -> Icons.Default.CheckCircle
        is GoalPace.OnTrack, GoalPace.FundedThisMonth -> Icons.Default.CheckCircle
        is GoalPace.Behind -> Icons.Default.Warning
        GoalPace.Unfunded, is GoalPace.ShortThisMonth -> Icons.Default.RemoveCircle
        is GoalPace.Projecting -> Icons.Default.CalendarMonth
        is GoalPace.NeedToStayOnTrack -> Icons.Default.GpsFixed
    }
    val tint: Color = when (pace) {
        GoalPace.Reached, is GoalPace.OnTrack, GoalPace.FundedThisMonth -> Color(0xFF34C759)
        is GoalPace.Behind, GoalPace.Unfunded, is GoalPace.ShortThisMonth, is GoalPace.NeedToStayOnTrack -> Color(0xFFFF9F0A)
        is GoalPace.Projecting -> Color(0xFF64D2FF)
    }

    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = tint.copy(alpha = 0.15f),
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .border(width = 0.5.dp, color = tint.copy(alpha = 0.3f), shape = CircleShape)
                .padding(horizontal = 7.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(10.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 10.sp
                ),
                color = tint,
                maxLines = 1
            )
        }
    }
}
