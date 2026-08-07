package com.truesummit.wear.tile

import android.content.Intent
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material.CircularProgressIndicator
import androidx.wear.protolayout.material.Text
import androidx.wear.protolayout.material.Typography
import androidx.wear.protolayout.material.layouts.EdgeContentLayout
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.truesummit.wear.MainActivity
import com.truesummit.wear.SnapshotRepository
import com.truesummit.wear.WatchSnapshot
import java.text.NumberFormat
import java.util.Locale

private const val RESOURCES_VERSION = "1"

private const val TEAL = 0xFF4ECDC4.toInt()
private const val AMBER = 0xFFF7B731.toInt()
private const val RED = 0xFFEF4444.toInt()
private const val GREEN = 0xFF10B981.toInt()
private const val WHITE = 0xFFFFFFFF.toInt()
private const val MUTED = 0xFF9AA5B8.toInt()

/**
 * Swipe-from-watch-face tile: budget ring around Safe to Spend, with the
 * next bill underneath. Tapping anywhere opens the watch app.
 */
class TrueSummitTileService : TileService() {

    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest
    ): ListenableFuture<TileBuilders.Tile> {
        val snapshot = SnapshotRepository.load(this)
        val root = if (snapshot == null) emptyLayout() else contentLayout(snapshot, requestParams)

        val tile = TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            // Tiles are refreshed explicitly when the phone pushes new data;
            // this is a safety net if a push is missed.
            .setFreshnessIntervalMillis(30 * 60 * 1000L)
            .setTileTimeline(
                TimelineBuilders.Timeline.Builder()
                    .addTimelineEntry(
                        TimelineBuilders.TimelineEntry.Builder()
                            .setLayout(
                                LayoutElementBuilders.Layout.Builder()
                                    .setRoot(root)
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            .build()

        return Futures.immediateFuture(tile)
    }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest
    ): ListenableFuture<ResourceBuilders.Resources> =
        Futures.immediateFuture(
            ResourceBuilders.Resources.Builder()
                .setVersion(RESOURCES_VERSION)
                .build()
        )

    // ── Layouts ──────────────────────────────────────────────────────────────

    private fun contentLayout(
        snap: WatchSnapshot,
        params: RequestBuilders.TileRequest
    ): LayoutElementBuilders.LayoutElement {
        val used = snap.budgetUsedFraction.toFloat().coerceIn(0f, 1f)
        val ringColor = when {
            used > 0.9f -> RED
            used > 0.7f -> AMBER
            else -> GREEN
        }

        val safeText = snap.safeToSpendToday?.let { currency(it) } ?: "—"
        val safeColor = when {
            snap.safeToSpendToday == null -> MUTED
            snap.safeToSpendToday <= 0 -> AMBER
            else -> GREEN
        }

        val center = LayoutElementBuilders.Column.Builder()
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .addContent(
                Text.Builder(this, "SAFE TO SPEND")
                    .setTypography(Typography.TYPOGRAPHY_CAPTION3)
                    .setColor(argb(MUTED))
                    .build()
            )
            .addContent(
                Text.Builder(this, safeText)
                    .setTypography(Typography.TYPOGRAPHY_TITLE2)
                    .setColor(argb(safeColor))
                    .build()
            )
            .addContent(
                Text.Builder(this, "${currency(snap.budgetRemaining)} budget left")
                    .setTypography(Typography.TYPOGRAPHY_CAPTION3)
                    .setColor(argb(MUTED))
                    .build()
            )
            .build()

        val bill = snap.upcomingBill
        val footer = Text.Builder(
            this,
            if (bill == null) snap.monthLabel
            else "${bill.name} ${currency(kotlin.math.abs(bill.amount))} · ${dueLabel(bill.daysUntil)}"
        )
            .setTypography(Typography.TYPOGRAPHY_CAPTION3)
            .setColor(argb(if (bill == null) MUTED else WHITE))
            .setMaxLines(1)
            .build()

        val layout = EdgeContentLayout.Builder(params.deviceConfiguration)
            .setEdgeContent(
                CircularProgressIndicator.Builder()
                    .setProgress(used)
                    .setCircularProgressIndicatorColors(
                        androidx.wear.protolayout.material.ProgressIndicatorColors(
                            argb(ringColor),
                            argb(0x33FFFFFF)
                        )
                    )
                    .build()
            )
            .setContent(center)
            .setSecondaryLabelTextContent(footer)
            .build()

        return tappable(layout)
    }

    private fun emptyLayout(): LayoutElementBuilders.LayoutElement =
        tappable(
            LayoutElementBuilders.Column.Builder()
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                .addContent(
                    Text.Builder(this, "TrueSummit")
                        .setTypography(Typography.TYPOGRAPHY_TITLE3)
                        .setColor(argb(TEAL))
                        .build()
                )
                .addContent(
                    Text.Builder(this, "Open on your phone to sync")
                        .setTypography(Typography.TYPOGRAPHY_CAPTION2)
                        .setColor(argb(MUTED))
                        .setMaxLines(2)
                        .build()
                )
                .build()
        )

    /** Wraps a layout so tapping anywhere on the tile opens the watch app. */
    private fun tappable(
        content: LayoutElementBuilders.LayoutElement
    ): LayoutElementBuilders.LayoutElement =
        LayoutElementBuilders.Box.Builder()
            .setWidth(androidx.wear.protolayout.DimensionBuilders.expand())
            .setHeight(androidx.wear.protolayout.DimensionBuilders.expand())
            .setModifiers(openAppModifier())
            .addContent(content)
            .build()

    private fun openAppModifier(): ModifiersBuilders.Modifiers =
        ModifiersBuilders.Modifiers.Builder()
            .setClickable(
                ModifiersBuilders.Clickable.Builder()
                    .setId("open_app")
                    .setOnClick(
                        ActionBuilders.LaunchAction.Builder()
                            .setAndroidActivity(
                                ActionBuilders.AndroidActivity.Builder()
                                    .setPackageName(packageName)
                                    .setClassName(MainActivity::class.java.name)
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            .build()

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun currency(amount: Double): String {
        val fmt = NumberFormat.getCurrencyInstance(Locale.US)
        fmt.maximumFractionDigits = 0
        return fmt.format(amount)
    }

    private fun dueLabel(days: Int): String = when {
        days <= 0 -> "today"
        days == 1 -> "tomorrow"
        else -> "in ${days}d"
    }
}
