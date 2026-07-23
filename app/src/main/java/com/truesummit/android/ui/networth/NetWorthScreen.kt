package com.truesummit.android.ui.networth

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
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
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.line.lineChart
import com.patrykandpatrick.vico.core.entry.entryModelOf
import com.truesummit.android.billing.SubscriptionTier
import com.truesummit.android.data.entity.AccountEntity
import com.truesummit.android.ui.networth.viewmodel.NetWorthTimeRange
import com.truesummit.android.ui.networth.viewmodel.NetWorthViewModel
import com.truesummit.android.ui.transactions.formatCurrency
import java.math.BigDecimal
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetWorthScreen(
    onManageConnections: () -> Unit,
    viewModel: NetWorthViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var reconcileAccount by remember { mutableStateOf<AccountEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Net Worth") },
                actions = {
                    IconButton(onClick = onManageConnections) {
                        Icon(Icons.Default.Settings, contentDescription = "Connections")
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
            item {
                NetWorthHero(uiState.netWorth)
            }

            item {
                TimeRangeSelector(
                    selectedRange = uiState.timeRange,
                    onRangeSelected = { viewModel.setTimeRange(it) }
                )
            }

            item {
                NetWorthChart(uiState.chartPoints)
            }

            uiState.milestone?.let { milestone ->
                item {
                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        NetWorthMilestoneCard(milestone)
                    }
                }
            }

            if (uiState.currentTier == SubscriptionTier.PREMIUM && uiState.holdings.isNotEmpty()) {
                item { SectionHeader("Investments") }
                items(uiState.holdings) { holding ->
                    HoldingRow(holding)
                }
            }

            item { SectionHeader("Assets") }
            items(uiState.assets) { account ->
                AccountRow(account, onReconcile = { reconcileAccount = account })
            }

            item { SectionHeader("Liabilities") }
            items(uiState.liabilities) { account ->
                AccountRow(account, onReconcile = { reconcileAccount = account })
            }
        }
    }

    reconcileAccount?.let { account ->
        ReconcileSheet(
            accountId = account.id,
            accountName = account.name,
            currentBalance = account.balance,
            onDismiss = { reconcileAccount = null }
        )
    }
}

@Composable
fun NetWorthHero(netWorth: BigDecimal) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Net Worth", style = MaterialTheme.typography.titleMedium)
        Text(
            text = formatCurrency(netWorth.toDouble()),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = if (netWorth >= BigDecimal.ZERO) Color(0xFF10B981) else Color(0xFFEF4444)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeRangeSelector(
    selectedRange: NetWorthTimeRange,
    onRangeSelected: (NetWorthTimeRange) -> Unit
) {
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        NetWorthTimeRange.entries.forEachIndexed { index, range ->
            SegmentedButton(
                selected = range == selectedRange,
                onClick = { onRangeSelected(range) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = NetWorthTimeRange.entries.size)
            ) {
                Text(range.label)
            }
        }
    }
}

@Composable
fun NetWorthChart(points: List<BigDecimal>) {
    if (points.size < 2) return
    
    val chartEntryModel = entryModelOf(*(points.map { it.toFloat() }.toTypedArray()))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(250.dp)
            .padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Box(modifier = Modifier.padding(16.dp)) {
            Chart(
                chart = lineChart(),
                model = chartEntryModel,
                startAxis = rememberStartAxis(
                    valueFormatter = { value, _ -> formatCurrency(value.toDouble()) }
                ),
                bottomAxis = rememberBottomAxis(),
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun HoldingRow(holding: com.truesummit.android.data.entity.InvestmentHoldingEntity) {
    ListItem(
        headlineContent = { Text(holding.securityName ?: "Unknown Security") },
        supportingContent = { Text("${holding.quantity} shares") },
        trailingContent = {
            Text(
                formatCurrency(holding.institutionValue.toDouble()),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
        }
    )
}

@Composable
fun AccountRow(account: AccountEntity, onReconcile: (() -> Unit)? = null) {
    ListItem(
        headlineContent = { Text(account.name) },
        supportingContent = { Text(account.type.displayName) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    formatCurrency(account.balance.toDouble()),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                if (onReconcile != null) {
                    IconButton(onClick = onReconcile, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Checklist, contentDescription = "Reconcile", modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    )
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.secondary,
        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp)
    )
}
