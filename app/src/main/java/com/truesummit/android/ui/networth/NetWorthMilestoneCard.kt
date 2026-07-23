package com.truesummit.android.ui.networth

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.truesummit.android.service.NetWorthMilestone
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.*

@Composable
fun NetWorthMilestoneCard(milestone: NetWorthMilestone) {
    val monthly = milestone.monthlyChange
    val monthlyStr = (if (monthly >= BigDecimal.ZERO) "+" else "") + currencyWhole(monthly) + "/mo"

    val subtitle = if (milestone.etaDate != null) {
        val cal = Calendar.getInstance(); cal.time = milestone.etaDate
        val month = cal.getDisplayName(Calendar.MONTH, Calendar.SHORT, Locale.getDefault()) ?: ""
        val year = cal.get(Calendar.YEAR)
        "$monthlyStr — on track for ${currencyWhole(milestone.target)} by $month $year."
    } else if (monthly > BigDecimal.ZERO) {
        "$monthlyStr — keep it up to reach your next milestone."
    } else {
        "Grow your net worth to project a date."
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Flag, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Text("Next Milestone", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                }
                SuggestionChip(
                    onClick = {},
                    label = {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.TrackChanges, contentDescription = null, modifier = Modifier.size(14.dp))
                            Text(currencyWhole(milestone.target), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                )
            }
            LinearProgressIndicator(
                progress = { milestone.progress.toFloat() },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color = Color(0xFF10B981)
            )
            Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun currencyWhole(d: BigDecimal): String {
    val fmt = NumberFormat.getCurrencyInstance(Locale.US)
    fmt.maximumFractionDigits = 0
    return fmt.format(d)
}
