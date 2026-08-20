package com.truesummit.android.ui.health

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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.truesummit.android.data.AppDatabase
import com.truesummit.android.service.FinancialHealthScore
import com.truesummit.android.service.FinancialHealthService
import com.truesummit.android.service.HealthPillar
import com.truesummit.android.service.HealthScorePoint
import kotlinx.coroutines.flow.*
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.*

class FinancialHealthViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getInstance(application)

    data class UiState(
        val score: FinancialHealthScore = FinancialHealthScore(emptyList(), false),
        val history: List<HealthScorePoint> = emptyList(),
        val delta: Int? = null
    )

    val uiState: StateFlow<UiState> = combine(
        db.transactionDao().getAll(),
        db.accountDao().getAll()
    ) { txs, accounts ->
        val score = FinancialHealthService.compute(txs, accounts)
        val history = FinancialHealthService.scoreHistory(txs, accounts)
        val delta = if (history.size >= 2)
            history[history.size - 1].score - history[history.size - 2].score
        else null
        UiState(score, history, delta)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UiState())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinancialHealthScreen(
    onBack: () -> Unit,
    viewModel: FinancialHealthViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val score = state.score

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Financial Health") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Score header
            item {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HealthScoreRing(score.total, tintForKey(score.tintKey), size = 80.dp.value)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    score.grade,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = tintForKey(score.tintKey)
                                )
                                state.delta?.let { d ->
                                    Text(
                                        if (d >= 0) "+$d" else "$d",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (d >= 0) Color(0xFF10B981) else Color(0xFFEF4444),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                            Text(
                                "Based on your last 3 months of activity.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Score trend
            if (state.history.size >= 2) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Score Trend (6 months)", style = MaterialTheme.typography.titleSmall)
                            ScoreTrendChart(state.history, tintForKey(score.tintKey))
                        }
                    }
                }
            }

            // Pillars section
            if (score.hasData) {
                item {
                    Text("How it's scored", style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp))
                }
                items(score.pillars) { pillar ->
                    PillarCard(pillar)
                }
                item {
                    Text(
                        "Savings rate (30), emergency fund (30), card debt (25), and subscription load (15) add up to 100. Everything is computed on your device — your score never leaves it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Icon(Icons.Default.Info, contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(8.dp))
                            Text("Needs income history", style = MaterialTheme.typography.titleSmall)
                            Text("Add income transactions to unlock your financial health score.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HealthScoreRing(score: Int, tint: Color, size: Float = 56f, lineWidth: Float = 6f) {
    Box(
        modifier = Modifier.size(size.dp),
        contentAlignment = Alignment.Center
    ) {
        val sweepAngle = (score / 100f) * 360f
        Canvas(modifier = Modifier.size(size.dp)) {
            drawArc(
                color = Color.Gray.copy(alpha = 0.15f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = lineWidth.dp.toPx()),
                size = Size(this.size.width - lineWidth.dp.toPx(), this.size.height - lineWidth.dp.toPx()),
                topLeft = Offset(lineWidth.dp.toPx() / 2, lineWidth.dp.toPx() / 2)
            )
            if (score > 0) {
                drawArc(
                    color = tint,
                    startAngle = -90f,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    style = Stroke(width = lineWidth.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round),
                    size = Size(this.size.width - lineWidth.dp.toPx(), this.size.height - lineWidth.dp.toPx()),
                    topLeft = Offset(lineWidth.dp.toPx() / 2, lineWidth.dp.toPx() / 2)
                )
            }
        }
        Text(
            "$score",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = tint
        )
    }
}

@Composable
private fun ScoreTrendChart(history: List<HealthScorePoint>, tint: Color) {
    val scores = history.map { it.score.toFloat() }
    val minScore = (scores.minOrNull() ?: 0f) - 10f
    val maxScore = (scores.maxOrNull() ?: 100f) + 10f
    val range = (maxScore - minScore).coerceAtLeast(1f)

    Canvas(modifier = Modifier.fillMaxWidth().height(80.dp)) {
        val w = size.width
        val h = size.height
        val step = if (scores.size > 1) w / (scores.size - 1) else w

        // Area fill
        val path = androidx.compose.ui.graphics.Path()
        scores.forEachIndexed { i, score ->
            val x = i * step
            val y = h - ((score - minScore) / range) * h
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.lineTo((scores.size - 1) * step, h)
        path.lineTo(0f, h)
        path.close()
        drawPath(path, color = tint.copy(alpha = 0.12f))

        // Line
        val linePath = androidx.compose.ui.graphics.Path()
        scores.forEachIndexed { i, score ->
            val x = i * step
            val y = h - ((score - minScore) / range) * h
            if (i == 0) linePath.moveTo(x, y) else linePath.lineTo(x, y)
        }
        drawPath(linePath, color = tint, style = Stroke(width = 2.dp.toPx()))

        // Points
        scores.forEachIndexed { i, score ->
            val x = i * step
            val y = h - ((score - minScore) / range) * h
            drawCircle(tint, radius = 4.dp.toPx(), center = Offset(x, y))
        }
    }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        history.forEach { point ->
            Text(point.label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PillarCard(pillar: HealthPillar) {
    val tint = tintForKey(pillar.tintKey)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(pillarIcon(pillar.icon), contentDescription = null,
                        tint = tint, modifier = Modifier.size(18.dp))
                    Text(pillar.name, style = MaterialTheme.typography.titleSmall)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(pillar.valueText, style = MaterialTheme.typography.bodySmall,
                        color = tint, fontWeight = FontWeight.SemiBold)
                    Text("${pillar.points}/${pillar.maxPoints}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            LinearProgressIndicator(
                progress = { pillar.fraction.toFloat() },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color = tint
            )
            Text(pillar.advice, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun tintForKey(key: String) = when (key) {
    "green" -> Color(0xFF10B981)
    "mint" -> Color(0xFF34D399)
    "orange" -> Color(0xFFF59E0B)
    else -> Color(0xFFEF4444)
}

private fun pillarIcon(name: String) = when (name) {
    "trending_up" -> Icons.Default.TrendingUp
    "shield" -> Icons.Default.Shield
    "credit_card" -> Icons.Default.CreditCard
    "repeat" -> Icons.Default.Repeat
    else -> Icons.Default.Info
}
