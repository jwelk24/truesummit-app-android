package com.truesummit.android.ui.transactions

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
import com.truesummit.android.data.AppDatabase
import com.truesummit.android.data.entity.TransactionEntity
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.*

data class RefundTrackerUiState(
    val awaiting: List<TransactionEntity> = emptyList(),
    val matched: List<Pair<TransactionEntity, TransactionEntity>> = emptyList(),
    val suggestions: List<Pair<TransactionEntity, TransactionEntity>> = emptyList()
)

class RefundTrackerViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getInstance(application)

    val uiState: StateFlow<RefundTrackerUiState> = db.transactionDao().getAll()
        .map { all: List<TransactionEntity> -> buildState(all) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RefundTrackerUiState())

    private fun buildState(all: List<TransactionEntity>): RefundTrackerUiState {
        val awaiting = all.filter { it.awaitingRefund && it.refundsTransactionId == null }
        val matched = all.filter { it.refundsTransactionId != null }
            .mapNotNull { refund ->
                val original = all.find { it.id == refund.refundsTransactionId }
                if (original != null) refund to original else null
            }

        // Suggest pairings: positive tx with similar amount to negative tx (same merchant, within 30 days)
        val expenses = awaiting
        val credits = all.filter { it.amount < BigDecimal.ZERO }
        val msPerDay = 86_400_000L
        val suggestions = expenses.mapNotNull { expense ->
            credits.firstOrNull { credit ->
                credit.merchant.equals(expense.merchant, ignoreCase = true) &&
                    credit.amount.abs().compareTo(expense.amount) == 0 &&
                    kotlin.math.abs(credit.date.time - expense.date.time) <= 30 * msPerDay &&
                    credit.refundsTransactionId == null
            }?.let { credit -> expense to credit }
        }

        return RefundTrackerUiState(awaiting = awaiting, matched = matched, suggestions = suggestions)
    }

    fun linkRefund(expenseId: UUID, refundId: UUID) {
        viewModelScope.launch {
            val all: List<TransactionEntity> = db.transactionDao().getAll().first()
            val refund = all.find { tx -> tx.id == refundId } ?: return@launch
            db.transactionDao().insert(refund.copy(refundsTransactionId = expenseId))
            val all2: List<TransactionEntity> = db.transactionDao().getAll().first()
            val expense = all2.find { tx -> tx.id == expenseId } ?: return@launch
            db.transactionDao().insert(expense.copy(awaitingRefund = false))
        }
    }

    fun markAwaitingRefund(transactionId: UUID, awaiting: Boolean) {
        viewModelScope.launch {
            val all: List<TransactionEntity> = db.transactionDao().getAll().first()
            val txn = all.find { tx -> tx.id == transactionId } ?: return@launch
            db.transactionDao().insert(txn.copy(awaitingRefund = awaiting))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RefundTrackerScreen(onBack: () -> Unit) {
    val vm: RefundTrackerViewModel = viewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Refund Tracker") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.suggestions.isNotEmpty()) {
                item { SectionHeader("Suggested Matches") }
                items(state.suggestions) { (expense, credit) ->
                    SuggestedMatchCard(expense, credit, onLink = { vm.linkRefund(expense.id, credit.id) })
                }
            }

            if (state.awaiting.isNotEmpty()) {
                item { SectionHeader("Awaiting Refund (${state.awaiting.size})") }
                items(state.awaiting) { tx ->
                    AwaitingRow(tx)
                }
            }

            if (state.matched.isNotEmpty()) {
                item { SectionHeader("Matched Refunds") }
                items(state.matched) { (refund, original) ->
                    MatchedRow(refund, original)
                }
            }

            if (state.awaiting.isEmpty() && state.matched.isEmpty() && state.suggestions.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("No pending refunds", style = MaterialTheme.typography.bodyLarge)
                        Text("Mark transactions as 'awaiting refund' from the transaction editor.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun AwaitingRow(tx: TransactionEntity) {
    val fmt = SimpleDateFormat("MMM d", Locale.getDefault())
    ListItem(
        headlineContent = { Text(tx.merchant) },
        supportingContent = { Text(fmt.format(tx.date)) },
        trailingContent = {
            Text(formatCurrency(tx.amount.toDouble()), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Medium)
        },
        leadingContent = {
            Icon(Icons.Default.HourglassEmpty, contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary)
        }
    )
}

@Composable
private fun MatchedRow(refund: TransactionEntity, original: TransactionEntity) {
    val fmt = SimpleDateFormat("MMM d", Locale.getDefault())
    ListItem(
        headlineContent = { Text(original.merchant) },
        supportingContent = { Text("Refunded ${fmt.format(refund.date)}") },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                Text(formatCurrency(original.amount.toDouble()), color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall)
                Text(formatCurrency(refund.amount.abs().toDouble()), color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall)
            }
        },
        leadingContent = {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    )
}

@Composable
private fun SuggestedMatchCard(expense: TransactionEntity, credit: TransactionEntity, onLink: () -> Unit) {
    val fmt = SimpleDateFormat("MMM d", Locale.getDefault())
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Possible refund", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(expense.merchant, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text("${fmt.format(expense.date)} → ${fmt.format(credit.date)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(formatCurrency(expense.amount.toDouble()), color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold)
            }
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                FilledTonalButton(onClick = onLink) { Text("Link") }
            }
        }
    }
}
