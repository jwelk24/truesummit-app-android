package com.truesummit.android.ui.budget

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.room.Room
import com.truesummit.android.data.AppDatabase
import com.truesummit.android.data.entity.GoalEntity
import com.truesummit.android.data.entity.TransactionEntity
import com.truesummit.android.service.BudgetEngine
import com.truesummit.android.service.GoalForecast
import com.truesummit.android.service.GoalPace
import com.truesummit.android.ui.theme.summitCategoryEmoji
import com.truesummit.android.ui.transactions.TransactionRow
import com.truesummit.android.ui.transactions.formatCurrency
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.util.*

// ─── ViewModel ───────────────────────────────────────────────

data class CategoryDetailState(
    val assigned: BigDecimal = BigDecimal.ZERO,
    val activity: BigDecimal = BigDecimal.ZERO,
    val goal: GoalEntity? = null,
    val goalPace: GoalPace? = null,
    val transactions: List<TransactionEntity> = emptyList(),
    val lastMonthAssigned: BigDecimal = BigDecimal.ZERO,
    val isLoading: Boolean = true
)

class CategoryDetailViewModel(application: Application) : AndroidViewModel(application) {
    private val db = Room.databaseBuilder(
        application,
        AppDatabase::class.java, "truesummit-db"
    ).addMigrations(
        AppDatabase.MIGRATION_1_2,
        AppDatabase.MIGRATION_2_3,
        AppDatabase.MIGRATION_3_4
    ).build()
    private val engine = BudgetEngine(application)

    private val _state = MutableStateFlow(CategoryDetailState())
    val state: StateFlow<CategoryDetailState> = _state

    fun load(categoryId: UUID, year: Int, month: Int) {
        viewModelScope.launch {
            val category = db.categoryDao().getCategoryById(categoryId) ?: return@launch
            val budgetMonth = db.budgetDao().getMonth(year, month)
            val assigned = engine.assigned(category, budgetMonth)
            val activity = engine.activity(category, year, month)
            val availableNow = assigned + activity
            val goal = db.goalDao().getGoalForCategory(categoryId)
            val avgMonthly = engine.averageAssigned(category, 3, year, month)
            val lastMonth = engine.lastMonthAssigned(category, year, month)

            val goalPace = goal?.let {
                GoalForecast.pace(
                    goal = it,
                    assignedThisMonth = assigned,
                    availableNow = availableNow,
                    avgMonthlyAssigned = avgMonthly,
                    currentYear = year,
                    currentMonth = month
                )
            }

            val cal = Calendar.getInstance()
            val txs = db.transactionDao().getAll().first().filter { tx ->
                cal.time = tx.date
                tx.categoryId == categoryId &&
                cal.get(Calendar.YEAR) == year &&
                (cal.get(Calendar.MONTH) + 1) == month
            }.sortedByDescending { it.date }

            _state.value = CategoryDetailState(
                assigned = assigned,
                activity = activity,
                goal = goal,
                goalPace = goalPace,
                transactions = txs,
                lastMonthAssigned = lastMonth,
                isLoading = false
            )
        }
    }

    fun setAssigned(categoryId: UUID, amount: BigDecimal, year: Int, month: Int) {
        viewModelScope.launch {
            val category = db.categoryDao().getCategoryById(categoryId) ?: return@launch
            val budgetMonth = engine.ensureMonth(year, month)
            engine.setAssigned(amount, category, budgetMonth)
            load(categoryId, year, month)
        }
    }
}

// ─── Sheet ───────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryDetailSheet(
    categoryId: UUID,
    categoryName: String,
    year: Int,
    month: Int,
    onDismiss: () -> Unit,
    viewModel: CategoryDetailViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var barColor by remember { mutableStateOf(CategoryBarColor.effectiveColor(context, categoryId, categoryName)) }

    LaunchedEffect(categoryId) { viewModel.load(categoryId, year, month) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        if (state.isLoading) {
            Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@ModalBottomSheet
        }

        val available = state.assigned + state.activity
        val spent = state.activity.abs().coerceAtLeast(BigDecimal.ZERO)
        val fraction = if (state.assigned > BigDecimal.ZERO)
            (spent.toDouble() / state.assigned.toDouble()).coerceIn(0.0, 1.0) else 0.0

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(summitCategoryEmoji(categoryName), style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.width(10.dp))
                    Text(categoryName, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                }
                HorizontalDivider()
            }

            // This Month section
            item {
                SectionHeader("This Month")
                AssignedEditor(
                    assigned = state.assigned,
                    lastMonth = state.lastMonthAssigned,
                    onCommit = { viewModel.setAssigned(categoryId, it, year, month) }
                )
                StatRow("Spent", formatCurrency(spent.toDouble()))
                StatRow(
                    label = if (available < BigDecimal.ZERO) "Overspent" else "Available",
                    value = formatCurrency(available.abs().toDouble()),
                    valueColor = if (available < BigDecimal.ZERO) Color(0xFFFF6B6B) else null
                )
                Spacer(Modifier.height(8.dp))
                SummitGradientBar(
                    fraction = fraction,
                    height = 6,
                    tint = barColor,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
                )
                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
            }

            // Goal section
            state.goal?.let { goal ->
                item {
                    SectionHeader("Goal")
                    StatRow("Target", formatCurrency(goal.targetAmount.toDouble()))
                    if (goal.targetDate != null) {
                        StatRow("Target Date", java.text.SimpleDateFormat("MMM yyyy", Locale.getDefault()).format(goal.targetDate))
                    }
                    state.goalPace?.let { pace ->
                        ListItem(
                            headlineContent = {
                                TrueSummitPacePill(pace = pace)
                            }
                        )
                    }
                    HorizontalDivider()
                }
            }

            // Bar Color section
            item {
                SectionHeader("Bar Color")
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CategoryBarColor.palette.take(8).forEach { (name, argb) ->
                        val swatchColor = Color(argb)
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(swatchColor)
                                .clickable {
                                    barColor = swatchColor
                                    CategoryBarColor.setColor(context, categoryId, swatchColor)
                                }
                        )
                    }
                }
                TextButton(
                    onClick = {
                        CategoryBarColor.setColor(context, categoryId, null)
                        barColor = CategoryBarColor.effectiveColor(context, categoryId, categoryName)
                    },
                    modifier = Modifier.padding(start = 16.dp)
                ) { Text("Reset to Automatic") }
                HorizontalDivider()
            }

            // Transactions section
            item { SectionHeader("Transactions This Month") }
            if (state.transactions.isEmpty()) {
                item {
                    Text(
                        "No transactions this month",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                    )
                }
            } else {
                items(state.transactions) { tx ->
                    TransactionRow(transaction = tx)
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 24.dp, top = 16.dp, end = 24.dp, bottom = 4.dp)
    )
}

@Composable
private fun StatRow(label: String, value: String, valueColor: Color? = null) {
    ListItem(
        headlineContent = { Text(label) },
        trailingContent = {
            Text(
                value,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium
                ),
                color = valueColor ?: MaterialTheme.colorScheme.onSurface
            )
        }
    )
}

@Composable
private fun AssignedEditor(
    assigned: BigDecimal,
    lastMonth: BigDecimal,
    onCommit: (BigDecimal) -> Unit
) {
    var text by remember(assigned) { mutableStateOf(assigned.stripTrailingZeros().toPlainString()) }

    ListItem(
        headlineContent = { Text("Assigned") },
        trailingContent = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.width(120.dp)
            )
        }
    )
    if (lastMonth > BigDecimal.ZERO) {
        ListItem(
            headlineContent = {
                Text(
                    "Match Last Month  ${formatCurrency(lastMonth.toDouble())}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            },
            leadingContent = { Icon(Icons.Default.History, contentDescription = null) },
            modifier = Modifier.clickable {
                text = lastMonth.stripTrailingZeros().toPlainString()
                onCommit(lastMonth)
            }
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.End
    ) {
        Button(onClick = {
            text.toBigDecimalOrNull()?.let { onCommit(it) }
        }) { Text("Apply") }
    }
}
