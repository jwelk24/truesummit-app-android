package com.truesummit.android.ui.coach

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.truesummit.android.data.AppDatabase
import com.truesummit.android.service.CoachInsight
import com.truesummit.android.service.CoachSentiment
import com.truesummit.android.service.FinancialCoachService
import com.truesummit.android.service.SmartAlertsService
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.math.BigDecimal

class CoachViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getInstance(application)

    val insights: StateFlow<List<CoachInsight>> = combine(
        db.transactionDao().getAll(),
        db.accountDao().getAll(),
        db.scheduledItemDao().getAll()
    ) { txs, accounts, scheduled ->
        val cushion = SmartAlertsService.getLowBalanceThreshold(application)
        FinancialCoachService.insights(txs, accounts, scheduled, cushion)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoachScreen(
    onBack: () -> Unit,
    viewModel: CoachViewModel = viewModel()
) {
    val insights by viewModel.insights.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Financial Coach") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (insights.isEmpty()) {
            Box(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Psychology, contentDescription = null,
                        modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                    Text("No insights yet", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Add transactions and accounts to get personalized coaching.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        "Based on your last 3 months of activity — all computed on your device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                items(insights) { insight ->
                    InsightCard(insight)
                }
            }
        }
    }
}

@Composable
fun InsightCard(insight: CoachInsight) {
    val (icon, tint) = insightIconAndTint(insight)
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp).padding(top = 2.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(insight.title, style = MaterialTheme.typography.titleSmall)
                Text(insight.detail, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun insightIconAndTint(insight: CoachInsight): Pair<ImageVector, Color> {
    val icon = when (insight.icon) {
        "warning" -> Icons.Default.Warning
        "flame" -> Icons.Default.LocalFireDepartment
        "trophy" -> Icons.Default.EmojiEvents
        "trending_up" -> Icons.Default.TrendingUp
        "trending_down" -> Icons.Default.TrendingDown
        "arrow_upward" -> Icons.Default.ArrowUpward
        "arrow_downward" -> Icons.Default.ArrowDownward
        "event_busy" -> Icons.Default.EventBusy
        else -> Icons.Default.Lightbulb
    }
    val tint = when (insight.sentiment) {
        CoachSentiment.POSITIVE -> Color(0xFF10B981)
        CoachSentiment.NEGATIVE -> Color(0xFFEF4444)
        CoachSentiment.WARNING -> Color(0xFFF59E0B)
        CoachSentiment.NEUTRAL -> Color(0xFF6B7280)
    }
    return icon to tint
}
