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

class NetWorthWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = TrueSummitSnapshot.build(context)
        provideContent {
            NetWorthWidgetContent(snapshot)
        }
    }

    @Composable
    private fun NetWorthWidgetContent(snapshot: TrueSummitSnapshot) {
        val nw = snapshot.netWorth
        val nwColor = if (nw >= BigDecimal.ZERO) Color(0xFF10B981) else Color(0xFFEF4444)

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.surface)
                .padding(16.dp),
            verticalAlignment = Alignment.Top,
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = "Net Worth",
                style = TextStyle(
                    fontSize = 12.sp,
                    color = GlanceTheme.colors.onSurfaceVariant
                )
            )
            Spacer(GlanceModifier.height(4.dp))
            Text(
                text = formatCurrency(nw.toDouble()),
                style = TextStyle(
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = ColorProvider(nwColor)
                )
            )
            Spacer(GlanceModifier.defaultWeight())
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(
                        text = "Assets",
                        style = TextStyle(fontSize = 10.sp, color = GlanceTheme.colors.onSurfaceVariant)
                    )
                    Text(
                        text = formatCurrency(snapshot.totalAssets.toDouble()),
                        style = TextStyle(fontSize = 12.sp, color = ColorProvider(Color(0xFF10B981)))
                    )
                }
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(
                        text = "Liabilities",
                        style = TextStyle(fontSize = 10.sp, color = GlanceTheme.colors.onSurfaceVariant)
                    )
                    Text(
                        text = formatCurrency(snapshot.totalLiabilities.toDouble()),
                        style = TextStyle(fontSize = 12.sp, color = ColorProvider(Color(0xFFEF4444)))
                    )
                }
            }
        }
    }
}

class NetWorthWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NetWorthWidget()
}
