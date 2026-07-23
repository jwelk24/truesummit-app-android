package com.truesummit.android.ui.reports

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.truesummit.android.service.ReportSummary
import com.truesummit.android.ui.transactions.formatCurrency
import java.math.BigDecimal
import java.math.RoundingMode

@Composable
fun ReportComparisonSection(current: ReportSummary, previous: ReportSummary) {
    // Category deltas: current spend - previous spend, largest absolute first
    data class Mover(val name: String, val delta: BigDecimal)
    val currentByCategory = current.byCategory.associate { it.first to it.second }
    val previousByCategory = previous.byCategory.associate { it.first to it.second }
    val allNames = (currentByCategory.keys + previousByCategory.keys).toSet()
    val movers = allNames
        .map { name ->
            val delta = (currentByCategory[name] ?: BigDecimal.ZERO)
                .subtract(previousByCategory[name] ?: BigDecimal.ZERO)
            Mover(name, delta)
        }
        .filter { it.delta.abs() >= BigDecimal.ONE }
        .sortedByDescending { it.delta.abs() }
        .take(4)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DeltaStatRow("Income", current.totalIncome, previous.totalIncome, increaseIsGood = true)
        DeltaStatRow("Spending", current.totalSpending, previous.totalSpending, increaseIsGood = false)
        DeltaStatRow("Net", current.netWorthChange, previous.netWorthChange, increaseIsGood = true)

        if (movers.isNotEmpty()) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            Text(
                "Biggest category changes",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            movers.forEach { mover ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(mover.name, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    val positive = mover.delta >= BigDecimal.ZERO
                    Text(
                        "${if (positive) "+" else "−"}${formatCurrency(mover.delta.abs().toDouble())}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (positive) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun DeltaStatRow(
    label: String,
    current: BigDecimal,
    previous: BigDecimal,
    increaseIsGood: Boolean
) {
    val delta = current.subtract(previous)
    val pctText: String? = if (previous.signum() != 0) {
        val pct = delta.toDouble() / previous.abs().toDouble() * 100
        String.format("%+.0f%%", pct)
    } else null

    val deltaColor = when {
        delta.signum() == 0 -> MaterialTheme.colorScheme.onSurfaceVariant
        (delta > BigDecimal.ZERO) == increaseIsGood -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.error
    }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(formatCurrency(current.toDouble()), style = MaterialTheme.typography.bodyMedium)
            Text(
                buildString {
                    append(if (delta >= BigDecimal.ZERO) "+" else "−")
                    append(formatCurrency(delta.abs().toDouble()))
                    if (pctText != null) append(" ($pctText)")
                },
                style = MaterialTheme.typography.labelSmall,
                color = deltaColor
            )
        }
    }
}
