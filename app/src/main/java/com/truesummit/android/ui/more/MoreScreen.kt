package com.truesummit.android.ui.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.truesummit.android.ui.navigation.Screen

/**
 * Overflow directory for destinations that don't fit in the bottom bar.
 * iOS shows its first few tabs and tucks the rest behind "More"; this is the
 * Android equivalent, and it's the only route to Insights and the coaching /
 * check-in screens that hang off it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen(
    overflowTabs: List<Screen> = emptyList(),
    onTab: (Screen) -> Unit = {},
    onCoach: () -> Unit = {},
    onSafeToSpend: () -> Unit = {},
    onFinancialHealth: () -> Unit = {},
    onWeeklyReview: () -> Unit = {},
    onWrapped: () -> Unit = {},
    onChallenges: () -> Unit = {},
    onMonthRecap: () -> Unit = {},
    onSettings: () -> Unit = {}
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("More") }) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            // Tabs that didn't fit in the bottom bar, in the user's chosen order.
            if (overflowTabs.isNotEmpty()) {
                item { MoreSectionHeader("Tabs", Icons.Default.Dashboard) }
                items(overflowTabs, key = { it.route }) { screen ->
                    MoreRow(screen.title, tabSubtitle(screen.route), screen.icon) { onTab(screen) }
                }
            }

            item { MoreSectionHeader("Coaching", Icons.Default.Psychology) }
            item { MoreRow("Financial Coach", "Proactive tips from your own data", Icons.Default.Psychology, onCoach) }
            item { MoreRow("Safe to Spend", "What's actually free to spend today", Icons.Default.AttachMoney, onSafeToSpend) }
            item { MoreRow("Financial Health", "Your overall money score", Icons.Default.Favorite, onFinancialHealth) }

            item { MoreSectionHeader("Check-Ins", Icons.Default.CalendarToday) }
            item { MoreRow("Weekly Review", "A 3-minute weekly tidy-up", Icons.Default.Checklist, onWeeklyReview) }
            item { MoreRow("Month Recap", "How last month actually went", Icons.Default.CalendarMonth, onMonthRecap) }
            item { MoreRow("Summit Wrapped", "Your year in review", Icons.Default.AutoAwesome, onWrapped) }
            item { MoreRow("Challenges", "Money missions against real spending", Icons.Default.EmojiEvents, onChallenges) }

            item { MoreSectionHeader("App", Icons.Default.Settings) }
            item { MoreRow("Settings", "Account, automation, appearance, privacy", Icons.Default.Settings, onSettings) }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

private fun tabSubtitle(route: String): String = when (route) {
    Screen.Budget.route -> "Give every dollar a job"
    Screen.Transactions.route -> "Everything you've spent and earned"
    Screen.NetWorth.route -> "Assets, liabilities, and the trend"
    Screen.Horizon.route -> "Upcoming bills and projected balance"
    Screen.Peaks.route -> "Savings goals and progress"
    Screen.Reports.route -> "Spending breakdowns and exports"
    Screen.Insights.route -> "Ask your money, digests, categorization"
    else -> ""
}

@Composable
private fun MoreSectionHeader(title: String, icon: ImageVector) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.secondary
        )
    }
}

@Composable
private fun MoreRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(title, fontWeight = FontWeight.Medium) },
        supportingContent = {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        leadingContent = {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        },
        trailingContent = {
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
    HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
}
