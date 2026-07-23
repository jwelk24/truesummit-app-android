package com.truesummit.android.ui.alerts

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.truesummit.android.ui.transactions.formatCurrency
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartAlertsScreen(
    onBack: () -> Unit,
    onUpgrade: () -> Unit,
    viewModel: SmartAlertsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Smart Alerts") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            // Bill reminders — all tiers
            item {
                SectionHeader("Bill Reminders")
                AlertSettingRow(
                    title = "Remind me before bills are due",
                    message = "Get a heads-up before upcoming bills and subscriptions.",
                    enabled = uiState.billRemindersEnabled,
                    onToggle = { viewModel.toggleBillReminders() }
                )
                if (uiState.billRemindersEnabled) {
                    LeadDaysPicker(
                        selected = uiState.billLeadDays,
                        onSelect = { viewModel.setBillLeadDays(it) }
                    )
                }
                HorizontalDivider()
            }

            // Low-balance warning — all tiers
            item {
                SectionHeader("Low-Balance Warning")
                AlertSettingRow(
                    title = "Warn me before my balance runs low",
                    message = "Projects checking & savings over 30 days and alerts if it dips below your cushion.",
                    enabled = uiState.lowBalanceEnabled,
                    onToggle = { viewModel.toggleLowBalance() }
                )
                // Inline forecast card
                uiState.projectedLowDate?.let { date ->
                    val balance = uiState.projectedLowBalance
                    val df = SimpleDateFormat("MMM d", Locale.getDefault())
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3CD))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFB7791F))
                            Column {
                                Text(
                                    "Balance may dip ${if (balance != null) "to ${formatCurrency(balance.toDouble())} " else ""}on ${df.format(date)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF92400E)
                                )
                                Text(
                                    "Based on your scheduled bills and current balance.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF92400E)
                                )
                            }
                        }
                    }
                }
                HorizontalDivider()
            }

            // Price changes visible in-app (all tiers) when detected
            if (uiState.priceChanges.isNotEmpty()) {
                item {
                    SectionHeader("Subscription Price Changes")
                    uiState.priceChanges.forEach { change ->
                        ListItem(
                            leadingContent = {
                                Icon(
                                    if (change.isIncrease) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                                    contentDescription = null,
                                    tint = if (change.isIncrease) Color(0xFFEF4444) else Color(0xFF10B981)
                                )
                            },
                            headlineContent = { Text(change.merchant) },
                            supportingContent = {
                                val verb = if (change.isIncrease) "↑" else "↓"
                                Text("$verb ${formatCurrency(change.oldAmount.toDouble())} → ${formatCurrency(change.newAmount.toDouble())}")
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
                    }
                }
            }

            if (!uiState.isPremium) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("More Alerts", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Upgrade to Premium for category-overspend warnings, large/new-merchant charge alerts, and subscription price change detection.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Button(onClick = onUpgrade) { Text("View Plans") }
                        }
                    }
                }
            } else {
                // Budget thresholds — Premium
                item {
                    SectionHeader("Budget Thresholds")
                    AlertSettingRow(
                        title = "Alert near category limit",
                        message = "One notification per category per month when spending crosses your threshold.",
                        enabled = uiState.budgetThresholdsEnabled,
                        onToggle = { viewModel.toggleBudgetThresholds() }
                    )
                    HorizontalDivider()
                }

                // Unusual charges — Premium
                item {
                    SectionHeader("Unusual Charges")
                    AlertSettingRow(
                        title = "Alert on large or new-merchant charges",
                        message = "Flags outflows over your threshold, especially from new merchants.",
                        enabled = uiState.unusualActivityEnabled,
                        onToggle = { viewModel.toggleUnusualActivity() }
                    )
                    HorizontalDivider()
                }

                // Price changes — Premium
                item {
                    SectionHeader("Subscription Price Watch")
                    AlertSettingRow(
                        title = "Alert when a subscription price changes",
                        message = "Detects when a recurring charge goes up or down.",
                        enabled = uiState.priceChangeEnabled,
                        onToggle = { viewModel.togglePriceChange() }
                    )
                    HorizontalDivider()
                }
            }

            // Test notification
            item {
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = { viewModel.sendTestNotification() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Text("Send Test Notification")
                }
            }
        }
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp)
    )
}

@Composable
fun AlertSettingRow(
    title: String,
    message: String,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(message) },
        trailingContent = {
            Switch(checked = enabled, onCheckedChange = onToggle)
        }
    )
}

@Composable
fun LeadDaysPicker(selected: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        listOf(0 to "Same day", 1 to "1 day", 3 to "3 days", 7 to "1 week").forEach { (days, label) ->
            FilterChip(
                selected = selected == days,
                onClick = { onSelect(days) },
                label = { Text(label) }
            )
        }
    }
}
