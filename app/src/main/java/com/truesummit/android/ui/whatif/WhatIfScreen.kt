package com.truesummit.android.ui.whatif

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.room.Room
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.m3.style.m3ChartStyle
import com.patrykandpatrick.vico.compose.style.ProvideChartStyle
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.line.lineChart
import com.patrykandpatrick.vico.core.entry.entryModelOf
import com.patrykandpatrick.vico.core.entry.entryOf
import com.truesummit.android.data.AppDatabase
import com.truesummit.android.service.*
import com.truesummit.android.ui.transactions.formatCurrency
import kotlinx.coroutines.flow.*
import java.math.BigDecimal
import java.util.*

private fun WhatIfProjection.monthlyValues(): List<BigDecimal> = points.map { it.scenario }

data class WhatIfUiState(
    val changes: List<WhatIfChange> = emptyList(),
    val projection: WhatIfProjection? = null,
    val currentNetWorth: BigDecimal = BigDecimal.ZERO
)

class WhatIfViewModel(application: Application) : AndroidViewModel(application) {
    private val db = Room.databaseBuilder(application, AppDatabase::class.java, "truesummit-db")
        .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4).build()

    private val _changes = MutableStateFlow<List<WhatIfChange>>(emptyList())

    val uiState: StateFlow<WhatIfUiState> = combine(
        db.accountDao().getAll(),
        db.transactionDao().getAll(),
        _changes
    ) { accounts, transactions, changes ->
        val netWorth = accounts.sumOf { it.balance }
        val msPerDay = 86_400_000L
        val thirtyDaysAgo = Date(System.currentTimeMillis() - 30 * msPerDay)
        val baselineMonthly = transactions
            .filter { it.date.after(thirtyDaysAgo) && it.amount > BigDecimal.ZERO }
            .sumOf { it.amount }
            .negate()
            .add(
                transactions.filter { it.date.after(thirtyDaysAgo) && it.amount < BigDecimal.ZERO }
                    .sumOf { it.amount.abs() }
            )
        WhatIfUiState(
            changes = changes,
            projection = WhatIfService.projectNetWorth(netWorth, baselineMonthly, changes, 24),
            currentNetWorth = netWorth
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WhatIfUiState())

    fun addChange(change: WhatIfChange) {
        _changes.value = _changes.value + change
    }

    fun removeChange(id: UUID) {
        _changes.value = _changes.value.filter { it.id != id }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhatIfScreen(onBack: () -> Unit) {
    val vm: WhatIfViewModel = viewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("What If?") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add scenario")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Chart
            state.projection?.let { proj ->
                item {
                    ProjectionCard(proj, state.currentNetWorth)
                }
            }

            // Scenarios
            if (state.changes.isNotEmpty()) {
                item { Text("Scenarios", style = MaterialTheme.typography.titleMedium) }
                items(state.changes) { change ->
                    ScenarioRow(change, onRemove = { vm.removeChange(change.id) })
                }
            } else {
                item {
                    Text("Tap + to add a scenario and see how it affects your net worth.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    if (showAddDialog) {
        AddScenarioDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { vm.addChange(it); showAddDialog = false }
        )
    }
}

@Composable
private fun ProjectionCard(proj: WhatIfProjection, currentNetWorth: BigDecimal) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("24-Month Projection", style = MaterialTheme.typography.titleSmall)
            val vals = proj.monthlyValues()
            if (vals.isNotEmpty()) {
                val model = entryModelOf(*vals.map { it.toFloat() }.toTypedArray())
                // Vico's default axis colors don't follow the Compose theme and
                // vanish against the dark card; m3ChartStyle derives them from
                // MaterialTheme.
                ProvideChartStyle(m3ChartStyle()) {
                    Chart(
                        chart = lineChart(),
                        model = model,
                        startAxis = rememberStartAxis(),
                        bottomAxis = rememberBottomAxis(),
                        modifier = Modifier.fillMaxWidth().height(160.dp)
                    )
                }
            }
            val finalVal = proj.monthlyValues().lastOrNull() ?: currentNetWorth
            Text("Projected: ${formatCurrency(finalVal.toDouble())}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun ScenarioRow(change: WhatIfChange, onRemove: () -> Unit) {
    ListItem(
        headlineContent = { Text(change.name) },
        supportingContent = {
            Text("${change.kind.name.lowercase().replaceFirstChar { it.uppercase() }} • ${if (change.amount >= BigDecimal.ZERO) "+" else ""}${formatCurrency(change.amount.toDouble())}")
        },
        trailingContent = {
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.DeleteOutline, contentDescription = "Remove")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddScenarioDialog(onDismiss: () -> Unit, onAdd: (WhatIfChange) -> Unit) {
    var name by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(WhatIfKind.MONTHLY) }
    var duration by remember { mutableStateOf("12") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Scenario") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Monthly amount (negative = expense)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WhatIfKind.entries.forEach { k ->
                        FilterChip(
                            selected = kind == k,
                            onClick = { kind = k },
                            label = { Text(k.name.lowercase().replaceFirstChar { it.uppercase() }) }
                        )
                    }
                }
                if (kind == WhatIfKind.MONTHLY) {
                    OutlinedTextField(value = duration, onValueChange = { duration = it },
                        label = { Text("Duration (months)") }, modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val amt = amountText.toDoubleOrNull() ?: return@TextButton
                onAdd(WhatIfChange(
                    name = name.ifBlank { "Scenario" },
                    kind = kind,
                    amount = BigDecimal.valueOf(amt),
                    startDate = Date(),
                    durationMonths = duration.toIntOrNull() ?: 12
                ))
            }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
