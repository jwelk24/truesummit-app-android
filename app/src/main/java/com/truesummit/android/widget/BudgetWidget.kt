package com.truesummit.android.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.appwidget.*
import androidx.glance.layout.*
import androidx.glance.text.*
import androidx.glance.unit.ColorProvider
import com.truesummit.android.ui.transactions.formatCurrency
import java.math.BigDecimal

class BudgetWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = TrueSummitSnapshot.build(context)
        provideContent {
            BudgetWidgetContent(snapshot)
        }
    }

    @Composable
    private fun BudgetWidgetContent(snapshot: TrueSummitSnapshot) {
        val remaining = snapshot.budgetRemaining
        val frac = snapshot.budgetUsedFraction
        val tint = when {
            frac > 0.9f -> Color(0xFFEF4444)
            frac > 0.7f -> Color(0xFFF59E0B)
            else -> Color(0xFF10B981)
        }

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.surface)
                .padding(16.dp)
        ) {
            Text(
                text = "Budget Left",
                style = TextStyle(fontSize = 12.sp, color = GlanceTheme.colors.onSurfaceVariant)
            )
            Text(
                text = formatCurrency(remaining.toDouble()),
                style = TextStyle(
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (remaining < BigDecimal.ZERO) ColorProvider(Color.Red) else GlanceTheme.colors.onSurface
                )
            )
            Text(
                text = snapshot.monthLabel,
                style = TextStyle(fontSize = 10.sp, color = GlanceTheme.colors.onSurfaceVariant)
            )
            
            Spacer(GlanceModifier.defaultWeight())
            
            LinearProgressIndicator(
                progress = frac,
                modifier = GlanceModifier.fillMaxWidth().height(8.dp),
                color = ColorProvider(tint)
            )
            
            Spacer(GlanceModifier.height(4.dp))
            
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                Text(
                    text = formatCurrency(snapshot.budgetSpent.toDouble()),
                    style = TextStyle(fontSize = 10.sp, color = GlanceTheme.colors.onSurfaceVariant)
                )
                Spacer(GlanceModifier.defaultWeight())
                Text(
                    text = "of ${formatCurrency(snapshot.budgetAssigned.toDouble())}",
                    style = TextStyle(fontSize = 10.sp, color = GlanceTheme.colors.onSurfaceVariant)
                )
            }
        }
    }
}

class BudgetWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BudgetWidget()
}
