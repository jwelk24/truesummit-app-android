package com.truesummit.android.ui.budget

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.truesummit.android.service.GoalPace
import com.truesummit.android.ui.theme.SummitColors
import com.truesummit.android.ui.theme.summitCategoryEmoji
import com.truesummit.android.ui.transactions.formatCurrency
import java.math.BigDecimal
import java.util.*

data class CategoryTileData(
    val id: UUID,
    val name: String,
    val spent: BigDecimal,
    val budget: BigDecimal,
    val index: Int,
    val customColor: Color? = null,
    val goalPace: GoalPace? = null,
    val projectedSpend: BigDecimal? = null
)

@Composable
fun TrueSummitBudgetHero(
    displayName: String,
    assigned: BigDecimal,
    spent: BigDecimal,
    availableToBudget: BigDecimal,
    savingsRate: Double,
    netWorthTrend: Double,
    tiles: List<CategoryTileData>,
    insight: String,
    onInsightClick: () -> Unit = {},
    onTileClick: (UUID) -> Unit = {}
) {
    val usedFraction = if (assigned > BigDecimal.ZERO)
        (spent.toDouble() / assigned.toDouble()).coerceIn(0.0, 2.0)
    else 0.0

    val remaining = maxOf(assigned - spent, BigDecimal.ZERO)

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        // Greeting + title
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = greeting().uppercase(),
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            val titleText = displayName.trim().ifEmpty { "Budget" }
            Text(
                text = titleText,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold
                )
            )
        }

        // Mountain view
        TrueSummitMountainView(
            savingsRate = savingsRate,
            budgetUsed = usedFraction,
            netWorthTrend = netWorthTrend,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(24.dp))
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Available amount
        Column(
            modifier = Modifier.padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "AVAILABLE THIS MONTH",
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = 1.5.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "$",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 28.sp
                    ),
                    color = SummitColors.Teal
                )
                Text(
                    text = remaining.toLong().toString(),
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        fontFeatureSettings = "tnum"
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = "of ${formatCurrency(assigned.toDouble())} budget · ${formatCurrency(spent.toDouble())} spent",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Budget used bar
        Column(
            modifier = Modifier.padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row {
                Text("Budget used", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                Spacer(Modifier.weight(1f))
                Text("${(usedFraction * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold))
            }
            SummitGradientBar(fraction = usedFraction)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Left to assign banner
        LeftToAssignBanner(
            availableToBudget = availableToBudget,
            assigned = assigned,
            modifier = Modifier.padding(horizontal = 24.dp)
        )

        // Category tiles
        if (tiles.isNotEmpty()) {
            Text(
                text = "This Month",
                style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold),
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 12.dp)
            )
            // Non-lazy grid (we want it inside a ScrollView parent, not its own scrollable)
            val chunked = tiles.chunked(2)
            Column(
                modifier = Modifier.padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                for (row in chunked) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        for (tile in row) {
                            SummitCategoryTileCard(
                                tile = tile,
                                modifier = Modifier.weight(1f),
                                onClick = { onTileClick(tile.id) }
                            )
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }

        // Insight card
        if (insight.isNotEmpty()) {
            SummitInsightCard(
                text = insight,
                onClick = onInsightClick,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 18.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
fun SummitGradientBar(
    fraction: Double,
    height: Int = 8,
    tint: Color? = null,
    modifier: Modifier = Modifier
) {
    val clamped = fraction.coerceIn(0.0, 1.0).toFloat()
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height.dp)
    ) {
        val w = size.width
        val h = size.height
        val r = h / 2f
        // Track
        drawRoundRect(
            color = Color.White.copy(alpha = 0.08f),
            size = size,
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(r)
        )
        // Fill
        val fillWidth = w * clamped
        if (fillWidth > 0f) {
            val brush = if (tint != null)
                Brush.horizontalGradient(listOf(tint, tint))
            else
                Brush.horizontalGradient(listOf(SummitColors.Teal, SummitColors.Amber))
            drawRoundRect(
                brush = brush,
                size = Size(fillWidth, h),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(r)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SummitCategoryTileCard(
    tile: CategoryTileData,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    onLongClick: (() -> Unit)? = null
) {
    val accent = tile.customColor ?: SummitColors.accent(tile.index)
    val fraction = if (tile.budget > BigDecimal.ZERO)
        (tile.spent.toDouble() / tile.budget.toDouble()).coerceIn(0.0, 1.0)
    else 0.0
    val available = tile.budget - tile.spent

    // This card's background is a fixed dark navy regardless of the app's
    // light/dark theme (matching the iOS mockup), so its text must be
    // pinned to light-on-dark colors rather than the ambient onSurface,
    // which flips to near-black in light mode and disappears here.
    CompositionLocalProvider(LocalContentColor provides SummitColors.Ice) {
        Surface(
            modifier = modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
            shape = RoundedCornerShape(20.dp),
            color = SummitColors.Slate2,
            tonalElevation = 0.dp
        ) {
            Box {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(summitCategoryEmoji(tile.name), style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 4.dp))
                    Text(
                        text = tile.name.uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.8.sp),
                        color = SummitColors.Ice.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = formatCurrency(tile.spent.toDouble()),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold,
                            fontFeatureSettings = "tnum"
                        ),
                        color = SummitColors.Ice
                    )
                    Text(
                        text = "of ${formatCurrency(tile.budget.toDouble())}",
                        style = MaterialTheme.typography.labelSmall,
                        color = SummitColors.Ice.copy(alpha = 0.4f)
                    )
                    Spacer(Modifier.height(4.dp))
                    SummitGradientBar(fraction = fraction, height = 3, tint = accent)
                    Text(
                        text = if (available < BigDecimal.ZERO)
                            "${formatCurrency(-available.toDouble())} over"
                        else "${formatCurrency(available.toDouble())} left",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (available < BigDecimal.ZERO)
                            SummitColors.Rose
                        else SummitColors.Ice.copy(alpha = 0.4f),
                        modifier = Modifier.padding(top = 5.dp)
                    )
                    if (tile.goalPace != null) {
                        Spacer(Modifier.height(5.dp))
                        TrueSummitPacePill(pace = tile.goalPace)
                    } else if (tile.projectedSpend != null && tile.budget > BigDecimal.ZERO) {
                        Spacer(Modifier.height(5.dp))
                        SpendingPacePill(projected = tile.projectedSpend, budget = tile.budget)
                    }
                }
                // Accent bottom bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(accent, RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp))
                        .align(Alignment.BottomStart)
                )
            }
        }
    }
}

@Composable
private fun SummitInsightCard(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        color = Color.Transparent,
        tonalElevation = 0.dp
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        listOf(SummitColors.Teal.copy(alpha = 0.12f), SummitColors.Lavender.copy(alpha = 0.08f))
                    ),
                    RoundedCornerShape(20.dp)
                )
        ) {
            Row(
                modifier = Modifier.padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = SummitColors.Teal.copy(alpha = 0.15f),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("⛰️", style = MaterialTheme.typography.titleMedium)
                    }
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = "SUMMIT INSIGHT",
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
                        color = SummitColors.Teal
                    )
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                    )
                }
                Icon(Icons.Default.ChevronRight, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
            }
        }
    }
}

@Composable
private fun LeftToAssignBanner(
    availableToBudget: BigDecimal,
    assigned: BigDecimal,
    modifier: Modifier = Modifier
) {
    val state = when {
        availableToBudget > BigDecimal.ZERO -> BannerState.ToAssign(availableToBudget)
        availableToBudget < BigDecimal.ZERO -> BannerState.OverAssigned(-availableToBudget)
        assigned > BigDecimal.ZERO -> BannerState.Balanced
        else -> BannerState.Empty
    }

    val accent = when (state) {
        is BannerState.ToAssign, BannerState.Balanced -> SummitColors.Teal
        is BannerState.OverAssigned -> SummitColors.Rose
        BannerState.Empty -> SummitColors.Lavender
    }
    val icon = when (state) {
        is BannerState.ToAssign -> Icons.Default.MoveToInbox
        BannerState.Balanced -> Icons.Default.CheckCircle
        is BannerState.OverAssigned -> Icons.Default.Warning
        BannerState.Empty -> Icons.Default.Inbox
    }
    val headline = when (state) {
        BannerState.Balanced -> "Every dollar has a job"
        is BannerState.OverAssigned -> "Over-assigned"
        BannerState.Empty -> "Nothing to assign yet"
        is BannerState.ToAssign -> "Left to assign"
    }
    val subtitle = when (state) {
        is BannerState.ToAssign -> "Give each dollar a job in a category."
        BannerState.Balanced -> "Your whole budget is assigned — nicely done."
        is BannerState.OverAssigned -> "You've assigned more than you have. Pull some back."
        BannerState.Empty -> "Add income or assign from savings to get started."
    }
    val amount: BigDecimal? = when (state) {
        is BannerState.ToAssign -> state.amount
        is BannerState.OverAssigned -> state.amount
        else -> null
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(accent.copy(alpha = 0.10f), RoundedCornerShape(20.dp))
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = accent.copy(alpha = 0.15f),
            modifier = Modifier.size(44.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = accent)
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = headline.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.1.sp, fontWeight = FontWeight.Bold),
                color = accent
            )
            if (amount != null) {
                Text(
                    text = formatCurrency(amount.toDouble()),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

private sealed class BannerState {
    data class ToAssign(val amount: BigDecimal) : BannerState()
    object Balanced : BannerState()
    data class OverAssigned(val amount: BigDecimal) : BannerState()
    object Empty : BannerState()
}

private fun greeting(): String {
    return when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
        in 0..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        else -> "Good evening"
    }
}
