package com.truesummit.android.ui.networth

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.line.lineChart
import com.patrykandpatrick.vico.compose.m3.style.m3ChartStyle
import com.patrykandpatrick.vico.compose.style.ProvideChartStyle
import com.patrykandpatrick.vico.core.axis.AxisItemPlacer
import com.patrykandpatrick.vico.core.chart.values.AxisValuesOverrider
import com.patrykandpatrick.vico.core.entry.entryModelOf
import com.truesummit.android.billing.SubscriptionTier
import com.truesummit.android.data.entity.AccountEntity
import com.truesummit.android.ui.networth.viewmodel.NetWorthPoint
import com.truesummit.android.ui.networth.viewmodel.NetWorthTimeRange
import com.truesummit.android.ui.networth.viewmodel.NetWorthUiState
import com.truesummit.android.ui.networth.viewmodel.NetWorthViewModel
import com.truesummit.android.ui.transactions.formatCurrency
import java.math.BigDecimal
import java.text.SimpleDateFormat
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
                NetWorthHeroCard(uiState)
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
                item { SectionHeader("Investments", Icons.Default.ShowChart) }
                items(uiState.holdings) { holding ->
                    HoldingRow(holding)
                }
            }

            item { SectionHeader("Assets", Icons.Default.ArrowCircleUp) }
            items(uiState.assets) { account ->
                AccountRow(account, onReconcile = { reconcileAccount = account })
            }

            item { SectionHeader("Liabilities", Icons.Default.ArrowCircleDown) }
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
fun NetWorthHeroCard(uiState: NetWorthUiState) {
    val netWorth = uiState.netWorth
    val netIsPositive = netWorth >= BigDecimal.ZERO
    val netColor = when {
        netWorth == BigDecimal.ZERO -> MaterialTheme.colorScheme.onSurfaceVariant
        netIsPositive -> Color(0xFF10B981)
        else -> Color(0xFFEF4444)
    }
    val pool = uiState.totalAssets + uiState.totalLiabilities
    val assetFraction = if (pool > BigDecimal.ZERO)
        (uiState.totalAssets.toDouble() / pool.toDouble()).coerceIn(0.0, 1.0)
    else 1.0
    val delta = uiState.delta
    val deltaPositive = delta != null && delta >= BigDecimal.ZERO
    val deltaColor = if (deltaPositive) Color(0xFF10B981) else Color(0xFFEF4444)
    val deltaText = delta?.let { d ->
        val pct = uiState.deltaPercent
        if (pct != null) "${if (pct >= 0) "+" else ""}${"%.1f".format(pct)}%"
        else formatCurrency(d.toDouble())
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.ShowChart,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "NET WORTH",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.6.sp, fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.weight(1f))
                if (deltaText != null) {
                    Surface(shape = CircleShape, color = deltaColor.copy(alpha = 0.15f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Icon(
                                if (deltaPositive) Icons.Default.ArrowOutward else Icons.Default.SouthEast,
                                contentDescription = null,
                                tint = deltaColor,
                                modifier = Modifier.size(11.dp)
                            )
                            Text(
                                deltaText,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = deltaColor
                            )
                        }
                    }
                }
            }

            Text(
                formatCurrency(netWorth.toDouble()),
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = netColor
            )

            LinearProgressIndicator(
                progress = { assetFraction.toFloat() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape),
                color = Color(0xFF10B981),
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Row(modifier = Modifier.fillMaxWidth()) {
                NetWorthMiniStat("Assets", formatCurrency(uiState.totalAssets.toDouble()), Color(0xFF10B981), Modifier.weight(1f))
                Box(modifier = Modifier.width(1.dp).height(28.dp).background(MaterialTheme.colorScheme.outlineVariant))
                NetWorthMiniStat("Liabilities", "-${formatCurrency(uiState.totalLiabilities.toDouble())}", Color(0xFFEF4444), Modifier.weight(1f).padding(start = 12.dp))
                Box(modifier = Modifier.width(1.dp).height(28.dp).background(MaterialTheme.colorScheme.outlineVariant))
                NetWorthMiniStat("vs ${uiState.timeRange.label}", delta?.let { formatCurrency(it.toDouble()) } ?: "—", netColor, Modifier.weight(1f).padding(start = 12.dp))
            }
        }
    }
}

@Composable
private fun NetWorthMiniStat(label: String, value: String, tint: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 0.4.sp
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = tint,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
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

/**
 * Abbreviates a value for a chart axis: 70940 -> "$71k", 1250000 -> "$1.3M".
 *
 * Axis ticks land on arbitrary values, so the full currency format printed
 * things like "$60,805.72" — eight characters of noise per tick on a label
 * whose only job is to give the eye a rough magnitude.
 */
private fun formatAxisCurrency(value: Double, span: Double): String {
    val abs = kotlin.math.abs(value)
    val sign = if (value < 0) "-" else ""
    // Decimals come from how much ground the axis covers, not from the
    // magnitude of the value. Ticks are evenly spaced, so rounding a narrow
    // range to whole units makes them read as uneven — $66k, $67k, $69k, $70k.
    return when {
        abs >= 1_000_000 -> {
            val decimals = if (span < 5_000_000) 2 else 1
            "$sign$" + String.format(Locale.US, "%.${decimals}fM", abs / 1_000_000)
        }
        abs >= 1_000 -> {
            val decimals = if (span < 20_000) 1 else 0
            "$sign$" + String.format(Locale.US, "%.${decimals}fk", abs / 1_000)
        }
        else -> "$sign$" + String.format(Locale.US, "%.0f", abs)
    }
}

@Composable
fun NetWorthChart(points: List<NetWorthPoint>) {
    if (points.size < 2) return

    val chartEntryModel = entryModelOf(*(points.map { it.value.toFloat() }.toTypedArray()))

    // Points are plotted at their list index, so an axis label has to look the
    // date back up by index. Once the series spans more than about two months,
    // day numbers stop fitting, so drop to the month alone.
    val spanDays = ((points.last().date.time - points.first().date.time) /
        (1000L * 60 * 60 * 24)).toInt()
    val labelPattern = if (spanDays > 70) "MMM" else "MMM d"
    val labelFormat = remember(labelPattern) { SimpleDateFormat(labelPattern, Locale.US) }

    // Show at most ~5 date labels regardless of how many points there are.
    val labelSpacing = remember(points.size) { maxOf(1, points.size / 5) }

    // Frame the y-axis around the data rather than anchoring it at zero. On a
    // balance in the tens of thousands, a zero baseline squashes the whole
    // series into the top sliver of the card and real movement reads as flat.
    // The axis labels still carry the actual values, so the numbers are
    // unchanged — only the window onto them is tighter.
    val (axisMinY, axisMaxY) = remember(points) {
        val values = points.map { it.value.toFloat() }
        val dataMin = values.min()
        val dataMax = values.max()
        val span = dataMax - dataMin
        // A perfectly flat series has no span to pad, so fall back to a
        // proportion of the value itself to avoid a zero-height axis.
        val padding = if (span > 0f) span * 0.18f else maxOf(kotlin.math.abs(dataMax) * 0.1f, 1f)
        val paddedMin = dataMin - padding
        // Don't introduce a negative axis for an all-positive series.
        val min = if (dataMin >= 0f && paddedMin < 0f) 0f else paddedMin
        min to (dataMax + padding)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(250.dp)
            .padding(16.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        // Vico's default axis label/guideline colors are dark and don't follow
        // the Compose theme, so they vanish against the dark card. m3ChartStyle
        // derives them from MaterialTheme (labels from onBackground, guidelines
        // and axis lines from outline).
        ProvideChartStyle(m3ChartStyle()) {
            Box(modifier = Modifier.padding(16.dp)) {
                Chart(
                    chart = lineChart(
                        axisValuesOverrider = remember(axisMinY, axisMaxY) {
                            AxisValuesOverrider.fixed(minY = axisMinY, maxY = axisMaxY)
                        }
                    ),
                    model = chartEntryModel,
                    startAxis = rememberStartAxis(
                        valueFormatter = { value, _ ->
                            formatAxisCurrency(value.toDouble(), (axisMaxY - axisMinY).toDouble())
                        },
                        itemPlacer = remember { AxisItemPlacer.Vertical.default(maxItemCount = 5) }
                    ),
                    bottomAxis = rememberBottomAxis(
                        valueFormatter = { value, _ ->
                            points.getOrNull(value.toInt())
                                ?.let { labelFormat.format(it.date) }
                                .orEmpty()
                        },
                        itemPlacer = remember(labelSpacing) {
                            AxisItemPlacer.Horizontal.default(spacing = labelSpacing)
                        }
                    ),
                    modifier = Modifier.fillMaxSize()
                )
            }
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
fun SectionHeader(title: String, icon: ImageVector? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp)
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp)
            )
        }
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.secondary
        )
    }
}
