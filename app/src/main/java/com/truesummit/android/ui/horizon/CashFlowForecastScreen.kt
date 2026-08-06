package com.truesummit.android.ui.horizon

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.m3.style.m3ChartStyle
import com.patrykandpatrick.vico.compose.style.ProvideChartStyle
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.line.lineChart
import com.patrykandpatrick.vico.core.entry.entryModelOf
import com.truesummit.android.service.ForecastPoint
import com.truesummit.android.ui.horizon.viewmodel.HorizonViewModel
import com.truesummit.android.ui.transactions.formatCurrency
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CashFlowForecastScreen(
    onBack: () -> Unit,
    viewModel: HorizonViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Forecast") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            val forecast = uiState.forecastResult
            if (forecast != null) {
                item {
                    ForecastChart(forecast.points)
                }

                item {
                    SectionHeader("Key Milestones")
                }

                item {
                    MilestoneRow("Today", forecast.startingBalance)
                }

                listOf(30, 60, 90).forEach { days ->
                    val balance = forecast.getBalance(days)
                    if (balance != null) {
                        item {
                            MilestoneRow("In $days days", balance)
                        }
                    }
                }

                forecast.lowest?.let {
                    item {
                        val df = SimpleDateFormat("MMM d", Locale.getDefault())
                        MilestoneRow("Lowest (${df.format(it.date)})", it.balance, isWarning = it.balance < java.math.BigDecimal.ZERO)
                    }
                }
            }
        }
    }
}

@Composable
fun ForecastChart(points: List<ForecastPoint>) {
    val chartEntryModel = entryModelOf(*(points.map { it.balance.toFloat() }.toTypedArray()))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(250.dp)
            .padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        // Vico's default axis colors don't follow the Compose theme and vanish
        // against the dark card; m3ChartStyle derives them from MaterialTheme.
        ProvideChartStyle(m3ChartStyle()) {
            Box(modifier = Modifier.padding(16.dp)) {
                Chart(
                    chart = lineChart(),
                    model = chartEntryModel,
                    startAxis = rememberStartAxis(
                        valueFormatter = { value, _ -> formatCurrency(value.toDouble()) }
                    ),
                    bottomAxis = rememberBottomAxis(),
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
fun MilestoneRow(label: String, balance: java.math.BigDecimal, isWarning: Boolean = false) {
    ListItem(
        headlineContent = { Text(label) },
        trailingContent = {
            Text(
                text = formatCurrency(balance.toDouble()),
                style = MaterialTheme.typography.titleMedium,
                color = if (isWarning || balance < java.math.BigDecimal.ZERO) Color.Red else MaterialTheme.colorScheme.onSurface
            )
        }
    )
}
