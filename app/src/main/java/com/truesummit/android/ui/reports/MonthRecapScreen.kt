package com.truesummit.android.ui.reports

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.truesummit.android.data.AppDatabase
import com.truesummit.android.service.ReportCompareMode
import com.truesummit.android.service.ReportRange
import com.truesummit.android.service.ReportsService
import com.truesummit.android.ui.transactions.formatCurrency
import kotlinx.coroutines.flow.*
import java.text.SimpleDateFormat
import java.util.*

class MonthRecapViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getInstance(application)

    val uiState = combine(
        db.transactionDao().getAll(),
        db.categoryDao().getCategories()
    ) { transactions, categories ->
        val categoryNames = categories.associate { it.id to it.name }
        val period = ReportsService.resolvePeriod(ReportRange.LAST_MONTH, null, null)
        val summary = ReportsService.buildSummary(transactions, period, categoryNames)
        val priorPeriod = period.comparisonPeriod(ReportCompareMode.PREVIOUS)
        val prior = priorPeriod?.let { ReportsService.buildSummary(transactions, it, categoryNames) }
        Triple(summary, prior, period)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthRecapScreen(
    onBack: () -> Unit,
    viewModel: MonthRecapViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val df = remember { SimpleDateFormat("MMMM", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("${state?.third?.start?.let { df.format(it) } ?: "Last Month"} Recap") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        state?.let { (summary, prior, period) ->
            LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
            ) {
                item {
                    ReportsHeroCard(summary)
                }

                if (prior != null) {
                    item {
                        SectionHeader("vs ${prior.period.label}")
                    }
                    item {
                        ReportComparisonSection(
                            current = summary,
                            previous = prior
                        )
                    }
                }

                val top = summary.byCategory.take(5)
                if (top.isNotEmpty()) {
                    item { SectionHeader("Top Categories") }
                    items(top) { (name, amount) ->
                        ListItem(
                            headlineContent = { Text(name) },
                            trailingContent = {
                                Text(
                                    formatCurrency(amount.toDouble()),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        )
                        HorizontalDivider()
                    }
                    item {
                        Text(
                            "${summary.transactionCount} transactions in ${df.format(period.start)}. The Reports tab has the full breakdown.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
        } ?: Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
            CircularProgressIndicator()
        }
    }
}
