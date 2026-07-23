package com.truesummit.android.ui.reports

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.truesummit.android.service.ReportCompareMode
import com.truesummit.android.service.ReportRange
import com.truesummit.android.service.ReportSummary
import com.truesummit.android.ui.reports.viewmodel.CategorySpending
import com.truesummit.android.ui.reports.viewmodel.MonthlyFlow
import com.truesummit.android.ui.reports.viewmodel.ReportsViewModel
import com.truesummit.android.ui.transactions.formatCurrency
import java.io.File
import java.math.BigDecimal
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(viewModel: ReportsViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showExportSheet by remember { mutableStateOf(false) }
    var drillDownCategory by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reports") },
                actions = {
                    IconButton(onClick = { showExportSheet = true }) {
                        Icon(Icons.Default.Share, contentDescription = "Export")
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
            uiState.currentSummary?.let { summary ->
                item { ReportsHeroCard(summary) }
            }

            // Tag filter chips
            if (uiState.allTags.isNotEmpty()) {
                item {
                    TagFilterRow(
                        tags = uiState.allTags,
                        selected = uiState.selectedTag,
                        onSelect = { viewModel.selectTag(it) }
                    )
                }
            }

            // Filtered label
            uiState.selectedTag?.let { tag ->
                item {
                    Text(
                        "Filtered: #$tag",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 0.dp)
                    )
                }
            }

            // Compare mode picker (mirrors iOS "Compare to" picker in the range section)
            item {
                CompareModePicker(
                    selected = uiState.compareMode,
                    onSelect = { viewModel.setCompareMode(it) }
                )
            }

            // Comparison section — shown when a mode is active and we have data
            val currentSummary = uiState.currentSummary
            val compareSummary = uiState.compareSummary
            if (uiState.compareMode != ReportCompareMode.OFF &&
                currentSummary != null && compareSummary != null
            ) {
                item {
                    SectionHeader("vs ${compareSummary.period.label}")
                }
                item {
                    ReportComparisonSection(
                        current = currentSummary,
                        previous = compareSummary
                    )
                }
            }

            item {
                SectionHeader("Spending This Month")
            }

            if (uiState.currentMonthSpending.isEmpty()) {
                item {
                    Text(
                        "No spending recorded this month.",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            } else {
                items(uiState.currentMonthSpending) { spending ->
                    SpendingBar(
                        spending = spending,
                        maxAmount = uiState.currentMonthSpending.first().amount,
                        onClick = { drillDownCategory = spending.categoryName }
                    )
                }
            }

            item {
                SectionHeader("Income vs Spending (6 months)")
            }

            items(uiState.sixMonthFlow) { flow ->
                FlowRow(flow)
            }
        }
    }

    if (showExportSheet) {
        ModalBottomSheet(onDismissRequest = { showExportSheet = false }) {
            ReportsExportContent(
                onDismiss = { showExportSheet = false },
                onExport = { file ->
                    if (file != null) {
                        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = if (file.extension == "pdf") "application/pdf" else "text/csv"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "Share Report"))
                    }
                    showExportSheet = false
                },
                viewModel = viewModel
            )
        }
    }

    drillDownCategory?.let { categoryName ->
        uiState.currentSummary?.let { summary ->
            CategoryTransactionsSheet(
                categoryName = categoryName,
                period = summary.period,
                transactions = uiState.periodTransactions,
                categoryNames = uiState.categoryNames,
                onDismiss = { drillDownCategory = null }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryTransactionsSheet(
    categoryName: String,
    period: com.truesummit.android.service.ReportPeriod,
    transactions: List<com.truesummit.android.data.entity.TransactionEntity>,
    categoryNames: Map<UUID, String>,
    onDismiss: () -> Unit
) {
    data class Entry(
        val id: UUID,
        val merchant: String,
        val date: Date,
        val amount: java.math.BigDecimal,
        val isRefund: Boolean,
        val memo: String?
    )

    val entries = remember(categoryName, transactions) {
        val result = mutableListOf<Entry>()
        for (tx in transactions) {
            if (tx.date < period.start || tx.date > period.end) continue
            val txCategoryName = categoryNames[tx.categoryId] ?: "Uncategorized"
            if (tx.amount > java.math.BigDecimal.ZERO && tx.refundsTransactionId != null) {
                if (txCategoryName == categoryName) {
                    result.add(Entry(tx.id, tx.merchant, tx.date, tx.amount, isRefund = true, tx.memo))
                }
                continue
            }
            if (tx.amount >= java.math.BigDecimal.ZERO) continue
            if (txCategoryName == categoryName) {
                result.add(Entry(tx.id, tx.merchant, tx.date, tx.amount.abs(), isRefund = false, tx.memo))
            }
        }
        result.sortedByDescending { it.date }
    }

    val total = entries.fold(java.math.BigDecimal.ZERO) { acc, e ->
        if (e.isRefund) acc.subtract(e.amount) else acc.add(e.amount)
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 32.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(categoryName, style = MaterialTheme.typography.titleMedium)
                Text(formatCurrency(total.toDouble()), style = MaterialTheme.typography.titleMedium)
            }
            Text(
                "${entries.size} transaction${if (entries.size == 1) "" else "s"} · ${period.label}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            if (entries.isEmpty()) {
                Text("No transactions in this range.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                val df = java.text.SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                entries.forEach { entry ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(entry.merchant, style = MaterialTheme.typography.bodyMedium)
                            val sub = buildString {
                                append(df.format(entry.date))
                                if (entry.isRefund) append(" · Refund")
                                else if (!entry.memo.isNullOrBlank()) append(" · ${entry.memo}")
                            }
                            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(
                            if (entry.isRefund) "-${formatCurrency(entry.amount.toDouble())}" else formatCurrency(entry.amount.toDouble()),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (entry.isRefund) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ReportsHeroCard(summary: ReportSummary) {
    val income = summary.totalIncome.toDouble()
    val spending = summary.totalSpending.toDouble()
    val net = income - spending
    val isPositive = net >= 0
    val netColor = if (isPositive) Color(0xFF10B981) else Color(0xFFEF4444)
    val progress = if (income > 0) (spending / income).toFloat().coerceIn(0f, 1f) else 0f

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Gradient net amount + caption
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    formatCurrency(Math.abs(net)),
                    style = MaterialTheme.typography.headlineSmall,
                    color = netColor,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    if (isPositive) "net" else "net loss",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }
            // 6pt spend meter
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color = if (progress >= 1f) Color(0xFFEF4444) else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            // Inline compact stats
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                CompactStat("Income", formatCurrency(income), Color(0xFF10B981))
                CompactStat("Spending", formatCurrency(spending), Color(0xFFEF4444))
                CompactStat("Net", formatCurrency(Math.abs(net)), netColor)
            }
        }
    }
}

@Composable
private fun CompactStat(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, color = color, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun ReportsExportContent(
    onDismiss: () -> Unit,
    onExport: (File?) -> Unit,
    viewModel: ReportsViewModel
) {
    var range by remember { mutableStateOf(ReportRange.THIS_MONTH) }
    var customStart by remember { mutableStateOf(Date()) }
    var customEnd by remember { mutableStateOf(Date()) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .padding(bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Export Data", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(16.dp))

        // Range Picker
        var expanded by remember { mutableStateOf(false) }
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Range: ${range.displayName}")
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                ReportRange.values().forEach { r ->
                    DropdownMenuItem(
                        text = { Text(r.displayName) },
                        onClick = {
                            range = r
                            expanded = false
                        }
                    )
                }
            }
        }

        if (range == ReportRange.CUSTOM) {
            // Simplified date pickers for brevity, ideally would use full DatePickerDialog
            Text("Custom dates enabled (Implementation pending full picker)", style = MaterialTheme.typography.bodySmall)
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = { viewModel.exportCSV(range, customStart, customEnd, onExport) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Export as CSV")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = { viewModel.exportPDF(range, customStart, customEnd, onExport) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Export as PDF")
        }
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
            Text("Cancel")
        }
    }
}

@Composable
fun SpendingBar(spending: CategorySpending, maxAmount: BigDecimal, onClick: (() -> Unit)? = null) {
    val fraction = if (maxAmount > BigDecimal.ZERO) {
        spending.amount.toDouble() / maxAmount.toDouble()
    } else 0.0

    Column(modifier = Modifier
        .padding(horizontal = 16.dp, vertical = 8.dp)
        .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(spending.categoryName, style = MaterialTheme.typography.bodyMedium)
            Text(formatCurrency(spending.amount.toDouble()), style = MaterialTheme.typography.bodySmall)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction.toFloat())
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}

@Composable
fun FlowRow(flow: MonthlyFlow) {
    ListItem(
        headlineContent = { Text(flow.monthLabel) },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "Income: ${formatCurrency(flow.income.toDouble())}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF10B981)
                )
                Text(
                    "Spending: ${formatCurrency(flow.spending.toDouble())}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFEF4444)
                )
            }
        }
    )
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp)
    )
}

@Composable
fun TagFilterRow(
    tags: List<String>,
    selected: String?,
    onSelect: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        tags.forEach { tag ->
            val isSelected = tag == selected
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(tag) },
                label = { Text("#$tag") }
            )
        }
    }
}

@Composable
fun CompareModePicker(
    selected: ReportCompareMode,
    onSelect: (ReportCompareMode) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text("Compare to", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box {
            OutlinedButton(onClick = { expanded = true }) {
                Text(selected.displayName)
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Default.ArrowDropDown,
                    contentDescription = null, modifier = Modifier.size(18.dp))
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                ReportCompareMode.values().forEach { mode ->
                    DropdownMenuItem(
                        text = { Text(mode.displayName) },
                        onClick = { onSelect(mode); expanded = false },
                        trailingIcon = {
                            if (mode == selected)
                                Icon(Icons.Default.Check,
                                    contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    )
                }
            }
        }
    }
}
