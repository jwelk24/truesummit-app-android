package com.truesummit.android.ui.review

import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.truesummit.android.data.AppDatabase
import com.truesummit.android.data.entity.TransactionEntity
import com.truesummit.android.ui.transactions.formatCurrency
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.util.*

private const val PREFS_REVIEW = "truesummit_weekly_review"
private const val KEY_STREAK = "review_streak"
private const val KEY_LAST_REVIEW = "last_review_ms"

data class WeeklyReviewState(
    val step: Int = 0,
    val totalSpent: BigDecimal = BigDecimal.ZERO,
    val totalIncome: BigDecimal = BigDecimal.ZERO,
    val topCategory: String? = null,
    val uncategorizedCount: Int = 0,
    val overspentCategories: List<String> = emptyList(),
    val upcomingBills: Int = 0,
    val streak: Int = 0,
    val isComplete: Boolean = false
)

class WeeklyReviewViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getInstance(application)
    private val prefs = application.getSharedPreferences(PREFS_REVIEW, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(WeeklyReviewState())
    val state: StateFlow<WeeklyReviewState> = _state

    init {
        viewModelScope.launch {
            db.transactionDao().getAll().collect { transactions ->
                val streak = prefs.getInt(KEY_STREAK, 0)
                _state.value = buildState(transactions, streak)
            }
        }
    }

    private fun buildState(transactions: List<TransactionEntity>, streak: Int): WeeklyReviewState {
        val now = Date()
        val msPerDay = 86_400_000L
        val weekAgo = Date(now.time - 7 * msPerDay)
        val week = transactions.filter { it.date.after(weekAgo) }

        val spent = week.filter { it.amount > BigDecimal.ZERO }.sumOf { it.amount }
        val income = week.filter { it.amount < BigDecimal.ZERO }.sumOf { it.amount.abs() }
        val topCat = week.filter { it.categoryId != null }
            .groupBy { it.categoryId!! }
            .maxByOrNull { (_, txs) -> txs.sumOf { it.amount } }
            ?.key?.toString()
        val uncategorized = week.count { it.categoryId == null && it.amount > BigDecimal.ZERO }

        return WeeklyReviewState(
            totalSpent = spent,
            totalIncome = income,
            topCategory = topCat,
            uncategorizedCount = uncategorized,
            streak = streak
        )
    }

    fun nextStep() {
        val current = _state.value
        val nextStep = current.step + 1
        if (nextStep >= 5) {
            // Complete — update streak
            val lastReview = prefs.getLong(KEY_LAST_REVIEW, 0L)
            val msPerDay = 86_400_000L
            val now = System.currentTimeMillis()
            val daysSinceLast = (now - lastReview) / msPerDay
            val newStreak = if (daysSinceLast <= 8) current.streak + 1 else 1
            prefs.edit().putInt(KEY_STREAK, newStreak).putLong(KEY_LAST_REVIEW, now).apply()
            _state.value = current.copy(step = nextStep, streak = newStreak, isComplete = true)
        } else {
            _state.value = current.copy(step = nextStep)
        }
    }

    fun prevStep() {
        val current = _state.value
        if (current.step > 0) _state.value = current.copy(step = current.step - 1)
    }
}

private val stepTitles = listOf("Summary", "Categorize", "Overspent?", "Upcoming", "Wins")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeeklyReviewScreen(onBack: () -> Unit) {
    val vm: WeeklyReviewViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()

    if (state.isComplete) {
        CompletionScreen(streak = state.streak, onDone = onBack)
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Weekly Review") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            // Step indicator
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                stepTitles.forEachIndexed { i, _ ->
                    LinearProgressIndicator(
                        progress = { if (i < state.step) 1f else if (i == state.step) 0.5f else 0f },
                        modifier = Modifier.weight(1f).height(4.dp)
                    )
                }
            }
            Text(
                stepTitles[state.step.coerceIn(0, stepTitles.lastIndex)],
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            Box(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
                when (state.step) {
                    0 -> StepSummary(state)
                    1 -> StepCategorize(state)
                    2 -> StepOverspent(state)
                    3 -> StepUpcoming(state)
                    4 -> StepWins(state)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (state.step > 0) {
                    OutlinedButton(onClick = { vm.prevStep() }) { Text("Back") }
                } else {
                    Spacer(Modifier.width(1.dp))
                }
                Button(onClick = { vm.nextStep() }) {
                    Text(if (state.step == 4) "Finish" else "Next")
                }
            }
        }
    }
}

@Composable
private fun StepSummary(state: WeeklyReviewState) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Here's your week at a glance", style = MaterialTheme.typography.bodyLarge)
        StatRow("Spent this week", formatCurrency(state.totalSpent.toDouble()))
        StatRow("Earned this week", formatCurrency(state.totalIncome.toDouble()))
        val net = state.totalIncome.subtract(state.totalSpent)
        StatRow("Net", formatCurrency(net.toDouble()),
            color = if (net >= BigDecimal.ZERO) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun StepCategorize(state: WeeklyReviewState) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (state.uncategorizedCount > 0) {
            Text("You have ${state.uncategorizedCount} uncategorized transaction(s).",
                style = MaterialTheme.typography.bodyLarge)
            Text("Head to the Transactions tab to assign categories.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Icon(Icons.Default.CheckCircle, contentDescription = null,
                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
            Text("All transactions are categorized!", style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun StepOverspent(state: WeeklyReviewState) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Did you overspend anywhere this week?", style = MaterialTheme.typography.bodyLarge)
        state.topCategory?.let {
            Text("Top spending category: $it",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("Check your budget screen to see category progress.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StepUpcoming(state: WeeklyReviewState) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Coming up next week", style = MaterialTheme.typography.bodyLarge)
        Text("Review your scheduled items and bills in the Horizon tab to plan ahead.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StepWins(state: WeeklyReviewState) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Celebrate your wins!", style = MaterialTheme.typography.bodyLarge)
        if (state.totalIncome > state.totalSpent) {
            WinRow("Positive cash flow this week")
        }
        if (state.uncategorizedCount == 0) {
            WinRow("Every transaction categorized")
        }
        WinRow("Completed weekly review • ${state.streak + 1} week streak")
    }
}

@Composable
private fun StatRow(label: String, value: String, color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = color)
    }
}

@Composable
private fun WinRow(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Star, contentDescription = null,
            tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(20.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun CompletionScreen(streak: Int, onDone: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(Icons.Default.EmojiEvents, contentDescription = null,
                modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.primary)
            Text("Review Complete!", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("$streak week streak", style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Done") }
        }
    }
}
