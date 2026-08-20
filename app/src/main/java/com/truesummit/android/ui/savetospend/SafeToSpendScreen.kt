package com.truesummit.android.ui.savetospend

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.truesummit.android.data.AppDatabase
import com.truesummit.android.service.SafeToSpend
import com.truesummit.android.service.SafeToSpendService
import com.truesummit.android.service.SmartAlertsService
import java.math.BigDecimal
import kotlinx.coroutines.flow.*
import java.text.NumberFormat
import java.util.*

class SafeToSpendViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getInstance(application)

    val result: StateFlow<SafeToSpend?> = combine(
        db.accountDao().getAll(),
        db.scheduledItemDao().getAll(),
        db.transactionDao().getAll()
    ) { accounts, scheduled, transactions ->
        SafeToSpendService.compute(
            accounts, scheduled, transactions,
            SmartAlertsService.getLowBalanceThreshold(application)
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SafeToSpendScreen(
    onBack: () -> Unit,
    viewModel: SafeToSpendViewModel = viewModel()
) {
    val result by viewModel.result.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Safe to Spend") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        val r = result
        if (r == null) {
            Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                SafeToSpendCard(r)
            }
        }
    }
}

@Composable
fun SafeToSpendCard(result: SafeToSpend) {
    val tint = when {
        !result.hasSpendableAccount -> MaterialTheme.colorScheme.secondary
        result.safeToday <= BigDecimal.ZERO -> Color(0xFFF59E0B)
        else -> Color(0xFF10B981)
    }

    val paydayText = result.nextIncomeDate?.let {
        val cal = Calendar.getInstance(); cal.time = it
        val month = cal.getDisplayName(Calendar.MONTH, Calendar.SHORT, Locale.getDefault()) ?: ""
        val day = cal.get(Calendar.DAY_OF_MONTH)
        "$month $day"
    } ?: "the next 2 weeks"

    val subtitle = when {
        !result.hasSpendableAccount -> "Add a checking or savings account to see what's safe to spend."
        result.isTight -> "Bills before your next income leave nothing extra — spend carefully until $paydayText."
        else -> "About ${currencyWhole(result.perDay)}/day until $paydayText, keeping your ${currencyWhole(result.cushion)} cushion."
    }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.AttachMoney, contentDescription = null,
                    tint = tint, modifier = Modifier.size(24.dp))
                Text("Safe to Spend", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (result.nextIncomeDate != null) {
                    Spacer(Modifier.weight(1f))
                    SuggestionChip(
                        onClick = {},
                        label = { Text("${result.daysUntilIncome}d", style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            Text(
                currencyWhole(result.safeToday),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = tint
            )
            Text("to spend today", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)

            if (result.hasSpendableAccount) {
                val meter = if (result.perDay > BigDecimal.ZERO)
                    (result.spentToday.toDouble() / result.perDay.toDouble()).coerceIn(0.0, 1.0).toFloat()
                else 1f
                LinearProgressIndicator(
                    progress = { meter },
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    color = tint
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    MiniStat("Per Day", currencyWhole(result.perDay))
                    MiniStat("Spent Today", currencyWhole(result.spentToday))
                    MiniStat("Next Income", paydayText)
                }
            }

            Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
    }
}

private fun currencyWhole(d: BigDecimal): String {
    val fmt = NumberFormat.getCurrencyInstance(Locale.US)
    fmt.maximumFractionDigits = 0
    return fmt.format(d)
}
