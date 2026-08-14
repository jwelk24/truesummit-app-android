package com.truesummit.android.ui.budget

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.room.Room
import com.truesummit.android.data.AppDatabase
import com.truesummit.android.data.entity.CategoryEntity
import com.truesummit.android.service.BudgetEngine
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.*

data class BudgetSuggestion(
    val category: CategoryEntity,
    val monthlyAverage: BigDecimal,
    val suggested: BigDecimal
)

object BudgetDrafter {
    fun suggestions(
        transactions: List<com.truesummit.android.data.entity.TransactionEntity>,
        categories: Map<UUID, CategoryEntity>,
        now: Date = Date()
    ): List<BudgetSuggestion> {
        val cal = Calendar.getInstance()
        cal.time = now
        cal.add(Calendar.MONTH, -3)
        val start = cal.time

        val totals = mutableMapOf<UUID, BigDecimal>()
        for (tx in transactions) {
            if (tx.date < start || tx.date > now || tx.amount >= BigDecimal.ZERO) continue
            val catId = tx.categoryId ?: continue
            totals[catId] = (totals[catId] ?: BigDecimal.ZERO).add(tx.amount.abs())
        }

        return totals.mapNotNull { (catId, total) ->
            val cat = categories[catId] ?: return@mapNotNull null
            val avg = total.divide(BigDecimal(3), 2, RoundingMode.HALF_UP)
            if (avg < BigDecimal("5")) return@mapNotNull null
            BudgetSuggestion(cat, avg, rounded(avg))
        }.sortedByDescending { it.suggested }
    }

    fun monthlyIncome(
        transactions: List<com.truesummit.android.data.entity.TransactionEntity>,
        now: Date = Date()
    ): BigDecimal {
        val cal = Calendar.getInstance()
        cal.time = now
        cal.add(Calendar.MONTH, -3)
        val start = cal.time
        val total = transactions
            .filter { it.date >= start && it.date <= now && it.amount > BigDecimal.ZERO }
            .fold(BigDecimal.ZERO) { acc, tx -> acc.add(tx.amount) }
        return total.divide(BigDecimal(3), 2, RoundingMode.HALF_UP)
    }

    private fun rounded(amount: BigDecimal): BigDecimal {
        val value = amount.toDouble()
        val step = when {
            value < 25 -> 5.0
            value < 100 -> 10.0
            value < 500 -> 25.0
            else -> 50.0
        }
        return BigDecimal.valueOf(Math.ceil(value / step) * step)
    }
}

class BudgetDraftViewModel(application: Application) : AndroidViewModel(application) {
    private val db = Room.databaseBuilder(application, AppDatabase::class.java, "truesummit-db")
        .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4).build()
    private val engine = BudgetEngine(application)

    data class DraftRow(
        val suggestion: BudgetSuggestion,
        val include: Boolean = true,
        val amount: BigDecimal
    )

    data class UiState(
        val rows: List<DraftRow> = emptyList(),
        val monthlyIncome: BigDecimal = BigDecimal.ZERO,
        val applied: Boolean = false,
        val isLoading: Boolean = true
    )

    private val _state = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(db.transactionDao().getAll(), db.categoryDao().getCategories()) { txs, cats ->
                val catMap = cats.associateBy { it.id }
                val suggestions = BudgetDrafter.suggestions(txs, catMap)
                val income = BudgetDrafter.monthlyIncome(txs)
                val rows = suggestions.map { s -> DraftRow(s, amount = s.suggested) }
                _state.value = UiState(rows = rows, monthlyIncome = income, isLoading = false)
            }.collect()
        }
    }

    fun setInclude(index: Int, include: Boolean) {
        val rows = _state.value.rows.toMutableList()
        rows[index] = rows[index].copy(include = include)
        _state.value = _state.value.copy(rows = rows)
    }

    fun setAmount(index: Int, amount: BigDecimal) {
        val rows = _state.value.rows.toMutableList()
        rows[index] = rows[index].copy(amount = amount)
        _state.value = _state.value.copy(rows = rows)
    }

    fun apply(onDone: () -> Unit) {
        viewModelScope.launch {
            val cal = Calendar.getInstance()
            val bm = engine.ensureMonth(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)
            for (row in _state.value.rows.filter { it.include && it.amount > BigDecimal.ZERO }) {
                engine.setAssigned(row.amount, row.suggestion.category, bm)
            }
            onDone()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetDraftScreen(
    onBack: () -> Unit,
    viewModel: BudgetDraftViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val includedTotal = state.rows.filter { it.include }.fold(BigDecimal.ZERO) { acc, r -> acc.add(r.amount) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Draft from History") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = { viewModel.apply(onBack) },
                        enabled = state.rows.any { it.include }
                    ) { Text("Apply") }
                }
            )
        }
    ) { padding ->
        if (state.isLoading) {
            Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        if (state.rows.isEmpty()) {
            Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(48.dp))
                    Text("Not enough history", style = MaterialTheme.typography.titleMedium)
                    Text("Once a few weeks of categorized spending are in TrueSummit, it can draft a budget for you.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 32.dp))
                }
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Draft Budget", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(currencyWhole(includedTotal) + "/month",
                            style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        if (state.monthlyIncome > BigDecimal.ZERO) {
                            val headroom = state.monthlyIncome.subtract(includedTotal)
                            Text(
                                if (headroom >= BigDecimal.ZERO)
                                    "vs ${currencyWhole(state.monthlyIncome)} avg income — ${currencyWhole(headroom)} left for goals & saving."
                                else
                                    "That's ${currencyWhole(headroom.abs())} more than your ${currencyWhole(state.monthlyIncome)} avg income — consider trimming.",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (headroom >= BigDecimal.ZERO)
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                else
                                    androidx.compose.ui.graphics.Color(0xFFF59E0B)
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Built from your last 3 months of spending. Adjust anything before applying.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                Text("Categories", style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp))
            }

            itemsIndexed(state.rows) { index, row ->
                var amountText by remember(index) { mutableStateOf(row.amount.toPlainString()) }
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Switch(
                            checked = row.include,
                            onCheckedChange = { viewModel.setInclude(index, it) },
                            modifier = Modifier.height(24.dp)
                        )
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(row.suggestion.category.name,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1)
                            Text("avg ${currencyWhole(row.suggestion.monthlyAverage)}/mo",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        OutlinedTextField(
                            value = amountText,
                            onValueChange = { v ->
                                amountText = v
                                val parsed = v.toBigDecimalOrNull()
                                if (parsed != null) viewModel.setAmount(index, parsed)
                            },
                            modifier = Modifier.width(90.dp),
                            enabled = row.include,
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}

private fun currencyWhole(d: BigDecimal): String {
    val fmt = NumberFormat.getCurrencyInstance(Locale.US)
    fmt.maximumFractionDigits = 0
    return fmt.format(d)
}
