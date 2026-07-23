package com.truesummit.android.ui.budget

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.truesummit.android.ui.transactions.formatCurrency
import java.math.BigDecimal

@Composable
fun SpendingPacePill(
    projected: BigDecimal,
    budget: BigDecimal,
    modifier: Modifier = Modifier
) {
    val ratio = if (budget > BigDecimal.ZERO) projected.toDouble() / budget.toDouble() else 0.0
    val tint: Color = when {
        ratio >= 1.0 -> Color(0xFFFF6B6B)
        ratio >= 0.85 -> Color(0xFFF7B731)
        else -> Color(0xFF4ECDC4)
    }
    val label = "Pace ${formatCurrency(projected.toDouble())}/mo"

    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = tint.copy(alpha = 0.15f),
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .border(width = 0.5.dp, color = tint.copy(alpha = 0.3f), shape = CircleShape)
                .padding(horizontal = 7.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(
                Icons.Default.Speed,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(10.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 10.sp
                ),
                color = tint,
                maxLines = 1
            )
        }
    }
}
