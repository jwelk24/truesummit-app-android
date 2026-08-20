package com.truesummit.android.ui.budget

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.truesummit.android.data.AppDatabase
import com.truesummit.android.data.entity.GoalEntity
import com.truesummit.android.data.entity.ScheduledItemEntity
import com.truesummit.android.service.PaycheckPlan
import com.truesummit.android.service.PaycheckPlanner
import com.truesummit.android.ui.transactions.formatCurrency
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.*

class PaycheckPlanViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getInstance(application)

    val plan = combine(
        db.scheduledItemDao().getAll(),
        db.goalDao().getAllGoals(),
        db.transactionDao().getAll()
    ) { scheduled, goals, transactions ->
        PaycheckPlanner.plan(scheduled, goals, transactions)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaycheckPlanScreen(onBack: () -> Unit) {
    val vm: PaycheckPlanViewModel = viewModel()
    val plan by vm.plan.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Paycheck Plan") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        val p = plan
        if (p == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                SuggestedAmountCard(p)
            }

            if (p.billsBeforeNextPaycheck.isNotEmpty()) {
                item {
                    Text("Bills Before Next Paycheck", style = MaterialTheme.typography.titleMedium)
                }
                items(p.billsBeforeNextPaycheck) { bill ->
                    BillRow(bill)
                }
            }

            if (p.goalItems.isNotEmpty()) {
                item {
                    Text("Goals to Fund", style = MaterialTheme.typography.titleMedium)
                }
                items(p.goalItems) { goal ->
                    GoalRow(goal)
                }
            }
        }
    }
}

@Composable
private fun SuggestedAmountCard(plan: PaycheckPlan) {
    val fmt = SimpleDateFormat("MMM d", Locale.getDefault())
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Suggested This Paycheck", style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(formatCurrency(plan.suggestedAmount.toDouble()),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary)
            plan.nextPaycheckDate?.let { date ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Next paycheck: ${fmt.format(date)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun BillRow(item: ScheduledItemEntity) {
    val fmt = SimpleDateFormat("MMM d", Locale.getDefault())
    ListItem(
        headlineContent = { Text(item.name) },
        supportingContent = { Text("Due ${fmt.format(item.nextDate)}") },
        trailingContent = {
            Text(formatCurrency(item.amount.toDouble()), fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.error)
        },
        leadingContent = {
            Icon(Icons.Default.Receipt, contentDescription = null,
                tint = MaterialTheme.colorScheme.primary)
        }
    )
}

@Composable
private fun GoalRow(goal: GoalEntity) {
    val fmt = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    ListItem(
        headlineContent = { Text(goal.type.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }) },
        supportingContent = {
            goal.targetDate?.let { Text("Target: ${fmt.format(it)}") }
        },
        trailingContent = {
            Text(formatCurrency(goal.targetAmount.toDouble()), fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary)
        },
        leadingContent = {
            Icon(Icons.Default.Flag, contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary)
        }
    )
}
