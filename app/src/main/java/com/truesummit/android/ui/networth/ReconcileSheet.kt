package com.truesummit.android.ui.networth

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.truesummit.android.data.AppDatabase
import com.truesummit.android.data.entity.BalanceSnapshotEntity
import com.truesummit.android.data.entity.TransactionEntity
import com.truesummit.android.ui.transactions.formatCurrency
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.util.*

class ReconcileViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getInstance(application)

    sealed class Result {
        object Idle : Result()
        object Balanced : Result()
        data class Adjusted(val amount: BigDecimal) : Result()
    }

    val result: StateFlow<Result> = MutableStateFlow(Result.Idle)

    fun reconcile(accountId: UUID, statementBalance: BigDecimal) {
        viewModelScope.launch {
            val now = Date()
            val allTxs = db.transactionDao().getAll().first()
            val accountTxs = allTxs.filter { it.accountId == accountId }

            // Mark all non-future cleared transactions as cleared (skip future-dated)
            for (tx in accountTxs) {
                if (!tx.date.after(now) && !tx.cleared) {
                    db.transactionDao().update(tx.copy(cleared = true))
                }
            }

            // Sum cleared transactions for this account
            val clearedTxs = db.transactionDao().getAll().first()
                .filter { it.accountId == accountId && it.cleared }
            val account = db.accountDao().getById(accountId) ?: return@launch
            val clearedSum = account.balance + clearedTxs.fold(BigDecimal.ZERO) { acc, tx -> acc + tx.amount }

            val diff = statementBalance - clearedSum

            if (diff.abs() < BigDecimal("0.01")) {
                // Exact match — just record the snapshot
                db.netWorthDao().insertSnapshot(
                    BalanceSnapshotEntity(date = now, balance = statementBalance, accountId = accountId)
                )
                (result as MutableStateFlow).value = Result.Balanced
            } else {
                // Create an adjustment transaction with TRANSFER_OUT so it doesn't inflate income/spending
                val adjustmentTx = TransactionEntity(
                    date = now,
                    amount = diff,
                    merchant = "Reconciliation Adjustment",
                    memo = "Auto-created during reconcile",
                    cleared = true,
                    flagColor = null,
                    pfcPrimary = "TRANSFER_OUT",
                    accountId = accountId,
                    categoryId = null
                )
                db.transactionDao().insert(adjustmentTx)
                db.netWorthDao().insertSnapshot(
                    BalanceSnapshotEntity(date = now, balance = statementBalance, accountId = accountId)
                )
                (result as MutableStateFlow).value = Result.Adjusted(diff)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReconcileSheet(
    accountId: UUID,
    accountName: String,
    currentBalance: BigDecimal,
    onDismiss: () -> Unit,
    viewModel: ReconcileViewModel = viewModel()
) {
    val result by viewModel.result.collectAsState()
    var statementText by remember { mutableStateOf("") }

    LaunchedEffect(result) {
        if (result is ReconcileViewModel.Result.Balanced || result is ReconcileViewModel.Result.Adjusted) {
            kotlinx.coroutines.delay(1800)
            onDismiss()
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Reconcile $accountName", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))

            when (val r = result) {
                is ReconcileViewModel.Result.Idle -> {
                    Text(
                        "Enter the ending balance from your bank statement. TrueSummit will mark all posted transactions as cleared and create a small adjustment if needed.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = statementText,
                        onValueChange = { statementText = it },
                        label = { Text("Statement Balance") },
                        prefix = { Text("$") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }
                        Button(
                            onClick = {
                                val amt = statementText.replace(",", "").toBigDecimalOrNull() ?: return@Button
                                viewModel.reconcile(accountId, amt)
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("Reconcile") }
                    }
                }

                is ReconcileViewModel.Result.Balanced -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(32.dp))
                        Column {
                            Text("Perfectly balanced!", style = MaterialTheme.typography.titleMedium, color = Color(0xFF10B981))
                            Text("Balance snapshot recorded.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                is ReconcileViewModel.Result.Adjusted -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(32.dp))
                        Column {
                            Text("Reconciled!", style = MaterialTheme.typography.titleMedium, color = Color(0xFF10B981))
                            Text(
                                "Adjustment of ${formatCurrency(r.amount.toDouble())} created and snapshot recorded.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
