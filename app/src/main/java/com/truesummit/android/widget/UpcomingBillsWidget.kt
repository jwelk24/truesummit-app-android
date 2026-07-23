package com.truesummit.android.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.appwidget.*
import androidx.glance.layout.*
import androidx.glance.text.*
import com.truesummit.android.ui.transactions.formatCurrency
import java.text.SimpleDateFormat
import java.util.*

class UpcomingBillsWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = TrueSummitSnapshot.build(context)
        provideContent {
            UpcomingBillsWidgetContent(snapshot)
        }
    }

    @Composable
    private fun UpcomingBillsWidgetContent(snapshot: TrueSummitSnapshot) {
        val df = SimpleDateFormat("MMM d", Locale.getDefault())

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.surface)
                .padding(16.dp)
        ) {
            Text(
                text = "Upcoming Bills",
                style = TextStyle(fontSize = 12.sp, color = GlanceTheme.colors.onSurfaceVariant)
            )
            
            Spacer(GlanceModifier.height(8.dp))
            
            if (snapshot.upcomingBills.isEmpty()) {
                Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No bills due in 30 days.",
                        style = TextStyle(fontSize = 12.sp, color = GlanceTheme.colors.onSurfaceVariant)
                    )
                }
            } else {
                snapshot.upcomingBills.forEach { bill ->
                    Row(
                        modifier = GlanceModifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = GlanceModifier.defaultWeight()) {
                            Text(
                                text = bill.name,
                                style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            )
                            Text(
                                text = df.format(bill.date),
                                style = TextStyle(fontSize = 10.sp, color = GlanceTheme.colors.onSurfaceVariant)
                            )
                        }
                        Text(
                            text = formatCurrency(bill.amount.abs().toDouble()),
                            style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        }
    }
}

class UpcomingBillsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = UpcomingBillsWidget()
}
