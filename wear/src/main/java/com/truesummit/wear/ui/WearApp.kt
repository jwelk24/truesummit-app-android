package com.truesummit.wear.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.*
import com.truesummit.wear.BillSummary
import com.truesummit.wear.WatchSnapshot
import com.truesummit.wear.ui.theme.*
import java.text.NumberFormat
import java.util.Locale

@Composable
fun TrueSummitWearApp(snapshot: WatchSnapshot?) {
    val listState = rememberScalingLazyListState()

    Scaffold(
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
        timeText = { TimeText() }
    ) {
        if (snapshot == null) {
            NoDataScreen()
        } else {
            ScalingLazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 28.dp, bottom = 16.dp, start = 8.dp, end = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                item { AppTitle() }
                item { SafeToSpendCard(snapshot) }
                item { NetWorthCard(snapshot) }
                item { BudgetCard(snapshot) }
                if (snapshot.upcomingBill != null) {
                    item { NextBillCard(snapshot.upcomingBill) }
                }
                if (snapshot.healthScore != null) {
                    item { HealthScoreCard(snapshot.healthScore, snapshot.healthGrade) }
                }
            }
        }
    }
}

// ── Title chip ───────────────────────────────────────────────────────────────

@Composable
private fun AppTitle() {
    Text(
        "TrueSummit",
        style = MaterialTheme.typography.title3.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        ),
        color = Teal,
        modifier = Modifier.padding(bottom = 4.dp)
    )
}

// ── No-data placeholder ──────────────────────────────────────────────────────

@Composable
private fun NoDataScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            Text("📱", fontSize = 28.sp)
            Text(
                "Open TrueSummit on your phone to sync.",
                style = MaterialTheme.typography.body2,
                color = MaterialTheme.colors.onBackground.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )
        }
    }
}

// ── Safe to Spend ────────────────────────────────────────────────────────────

@Composable
private fun SafeToSpendCard(snap: WatchSnapshot) {
    val amount = snap.safeToSpendToday
    val tint = when {
        amount == null -> MaterialTheme.colors.onSurface.copy(alpha = 0.5f)
        amount <= 0 -> Amber
        else -> Green
    }
    WearCard(label = "Safe to Spend") {
        Text(
            amount?.let { formatCurrency(it, snap.currencyCode) } ?: "—",
            style = MaterialTheme.typography.title2.copy(fontWeight = FontWeight.Bold),
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        snap.safePerDay?.let { perDay ->
            Text(
                "${formatCurrency(perDay, snap.currencyCode)}/day",
                style = MaterialTheme.typography.caption3,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

// ── Net Worth ────────────────────────────────────────────────────────────────

@Composable
private fun NetWorthCard(snap: WatchSnapshot) {
    val color = if (snap.netWorth >= 0) Green else Red
    WearCard(label = "Net Worth") {
        Text(
            formatCurrency(snap.netWorth, snap.currencyCode),
            style = MaterialTheme.typography.title2.copy(fontWeight = FontWeight.Bold),
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ── Budget ───────────────────────────────────────────────────────────────────

@Composable
private fun BudgetCard(snap: WatchSnapshot) {
    val frac = snap.budgetUsedFraction.toFloat()
    val tint = when {
        frac > 0.9f -> Red
        frac > 0.7f -> Amber
        else -> Green
    }
    WearCard(label = "Budget Left · ${snap.monthLabel}") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                formatCurrency(snap.budgetRemaining, snap.currencyCode),
                style = MaterialTheme.typography.title3.copy(fontWeight = FontWeight.Bold),
                color = if (snap.budgetRemaining >= 0) MaterialTheme.colors.onSurface else Red,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            CircularProgressIndicator(
                progress = frac,
                modifier = Modifier.size(32.dp),
                strokeWidth = 4.dp,
                indicatorColor = tint,
                trackColor = MaterialTheme.colors.onSurface.copy(alpha = 0.15f)
            )
        }
    }
}

// ── Next Bill ────────────────────────────────────────────────────────────────

@Composable
private fun NextBillCard(bill: BillSummary) {
    WearCard(label = "Next Bill") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                bill.name,
                style = MaterialTheme.typography.body1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                formatCurrency(Math.abs(bill.amount), "USD"),
                style = MaterialTheme.typography.body1.copy(fontWeight = FontWeight.SemiBold),
                color = Red
            )
        }
        val dueLabel = when (bill.daysUntil) {
            0 -> "Due today"
            1 -> "Due tomorrow"
            else -> "Due in ${bill.daysUntil} days"
        }
        Text(
            dueLabel,
            style = MaterialTheme.typography.caption3,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
        )
    }
}

// ── Health Score ─────────────────────────────────────────────────────────────

@Composable
private fun HealthScoreCard(score: Int, grade: String?) {
    // Thresholds mirror FinancialHealthScore.grade on the phone
    val tint = when {
        score >= 80 -> Green
        score >= 65 -> Teal
        score >= 45 -> Amber
        else -> Red
    }
    WearCard(label = "Financial Health") {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    score.toString(),
                    style = MaterialTheme.typography.title3.copy(fontWeight = FontWeight.Bold),
                    color = tint
                )
            }
            Column {
                Text(
                    grade ?: "$score / 100",
                    style = MaterialTheme.typography.body1.copy(fontWeight = FontWeight.SemiBold),
                    color = tint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "out of 100",
                    style = MaterialTheme.typography.caption3,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}

// ── Shared card shell ─────────────────────────────────────────────────────────

@Composable
private fun WearCard(label: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        onClick = {},
        modifier = Modifier.fillMaxWidth(),
        backgroundPainter = CardDefaults.cardBackgroundPainter(
            startBackgroundColor = Slate2,
            endBackgroundColor = Slate2
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                label.uppercase(),
                style = MaterialTheme.typography.caption3.copy(
                    letterSpacing = 0.4.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = Teal.copy(alpha = 0.8f)
            )
            content()
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun formatCurrency(amount: Double, currencyCode: String): String {
    return try {
        val fmt = NumberFormat.getCurrencyInstance(Locale.US)
        fmt.maximumFractionDigits = 0
        fmt.format(amount)
    } catch (_: Exception) {
        "$${amount.toInt()}"
    }
}
