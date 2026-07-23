package com.truesummit.android.ui.budget

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import com.truesummit.android.ui.theme.SummitColors

@Composable
fun TrueSummitMountainView(
    savingsRate: Double,
    budgetUsed: Double,
    netWorthTrend: Double,
    modifier: Modifier = Modifier
) {
    val animSavings by animateFloatAsState(
        targetValue = savingsRate.coerceIn(0.0, 1.0).toFloat(),
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 120f),
        label = "savings"
    )
    val animBudget by animateFloatAsState(
        targetValue = budgetUsed.coerceIn(0.0, 1.0).toFloat(),
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 120f),
        label = "budget"
    )
    val animTrend by animateFloatAsState(
        targetValue = netWorthTrend.coerceIn(0.0, 1.0).toFloat(),
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 120f),
        label = "trend"
    )

    Canvas(modifier = modifier.fillMaxWidth().height(210.dp)) {
        drawMountainScene(animSavings, animBudget, animTrend)
    }
}

private fun DrawScope.drawMountainScene(savings: Float, budget: Float, trend: Float) {
    val w = size.width
    val h = size.height
    val cx = w / 2f

    val tipY = lerp(h * 0.52f, h * 0.08f, trend)
    val base = h

    val glowColor = atmosphereColor(budget)
    val glowAlpha = 0.10f + budget * 0.30f
    val snowDepth = lerp(0.02f, 0.45f, savings)

    // 1. Sky
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(Color(0xFF0E1525), Color(0xFF1A2540), Color(0xFF2A3D60)),
            startY = 0f,
            endY = h
        )
    )

    // 2. Atmosphere glow
    drawRect(brush = Brush.verticalGradient(listOf(glowColor.copy(alpha = glowAlpha), glowColor.copy(alpha = glowAlpha))))

    // 3. Stars
    val stars = listOf(
        Triple(0.06f * w, 0.06f * h, 0.55f),
        Triple(0.15f * w, 0.04f * h, 0.40f),
        Triple(0.24f * w, 0.09f * h, 0.50f),
        Triple(0.36f * w, 0.05f * h, 0.35f),
        Triple(0.61f * w, 0.04f * h, 0.55f),
        Triple(0.75f * w, 0.09f * h, 0.38f),
        Triple(0.86f * w, 0.03f * h, 0.48f),
        Triple(0.94f * w, 0.10f * h, 0.40f),
        Triple(0.10f * w, 0.18f * h, 0.28f),
        Triple(0.90f * w, 0.20f * h, 0.25f),
    )
    for ((sx, sy, alpha) in stars) {
        drawCircle(color = Color.White.copy(alpha = alpha.toFloat()), radius = 1.2f, center = Offset(sx, sy))
    }

    // 4. Far ridgeline
    val r1pts = listOf(
        -0.01f to 0.78f, 0.06f to 0.64f, 0.12f to 0.69f, 0.18f to 0.60f,
        0.23f to 0.66f, 0.28f to 0.56f, 0.35f to 0.62f, 0.40f to 0.50f,
        0.46f to 0.56f, 0.50f to 0.46f, 0.54f to 0.41f, 0.59f to 0.47f,
        0.64f to 0.42f, 0.70f to 0.50f, 0.73f to 0.44f, 0.78f to 0.53f,
        0.83f to 0.46f, 0.89f to 0.55f, 0.94f to 0.48f, 1.01f to 0.56f,
        1.01f to 1.0f, -0.01f to 1.0f
    )
    drawFilledRidge(r1pts, w, h, Color(0xFF2E4070).copy(alpha = 0.50f), Color(0xFF1A2540).copy(alpha = 0f))

    // 5. Mid ridgeline
    val r2pts = listOf(
        -0.01f to 0.88f, 0.04f to 0.77f, 0.09f to 0.82f, 0.14f to 0.72f,
        0.21f to 0.78f, 0.28f to 0.66f, 0.35f to 0.73f, 0.41f to 0.61f,
        0.47f to 0.69f, 0.51f to 0.56f, 0.54f to 0.51f, 0.58f to 0.57f,
        0.62f to 0.52f, 0.68f to 0.60f, 0.74f to 0.51f, 0.80f to 0.59f,
        0.86f to 0.52f, 0.91f to 0.62f, 0.97f to 0.56f, 1.01f to 0.60f,
        1.01f to 1.0f, -0.01f to 1.0f
    )
    drawFilledRidge(r2pts, w, h, Color(0xFF243560).copy(alpha = 0.75f), SummitColors.Slate.copy(alpha = 0.20f))

    // 6. Mountain left face (lit)
    val leftFace = Path().apply {
        moveTo(-5f, base)
        lineTo(cx, tipY)
        lineTo(cx, base)
        close()
    }
    drawPath(leftFace, brush = Brush.linearGradient(
        colors = listOf(Color(0xFF1E2D4A), Color(0xFF141C2E)),
        start = Offset(w * 0.15f, 0f),
        end = Offset(w * 0.6f, h)
    ))

    // 7. Mountain right face (shadow)
    val rightFace = Path().apply {
        moveTo(w + 5f, base)
        lineTo(cx, tipY)
        lineTo(cx, base)
        close()
    }
    drawPath(rightFace, brush = Brush.linearGradient(
        colors = listOf(Color(0xFF0F1828), Color(0xFF0A1018)),
        start = Offset(w * 0.85f, 0f),
        end = Offset(w * 0.3f, h)
    ))

    // 8. Snow cap
    if (snowDepth > 0.02f) {
        val totalH = base - tipY
        val snowY = tipY + totalH * snowDepth
        val leftEdgeX = cx - (cx + 5f) * (snowY - tipY) / totalH
        val rightEdgeX = cx + (w - cx + 5f) * (snowY - tipY) / totalH
        val leftW = cx - leftEdgeX
        val rightW = rightEdgeX - cx

        // Left snow (lit face)
        val snowL = Path().apply {
            moveTo(cx, tipY)
            lineTo(leftEdgeX, snowY)
            cubicTo(
                leftEdgeX + leftW * 0.10f, snowY + leftW * 0.06f,
                leftEdgeX + leftW * 0.22f, snowY + leftW * 0.10f,
                leftEdgeX + leftW * 0.30f, snowY + leftW * 0.07f
            )
            cubicTo(
                leftEdgeX + leftW * 0.38f, snowY + leftW * 0.04f,
                leftEdgeX + leftW * 0.45f, snowY + leftW * 0.09f,
                leftEdgeX + leftW * 0.55f, snowY + leftW * 0.06f
            )
            cubicTo(
                leftEdgeX + leftW * 0.65f, snowY + leftW * 0.03f,
                leftEdgeX + leftW * 0.72f, snowY + leftW * 0.08f,
                leftEdgeX + leftW * 0.80f, snowY + leftW * 0.04f
            )
            cubicTo(
                leftEdgeX + leftW * 0.90f, snowY + leftW * 0.02f,
                leftEdgeX + leftW * 0.96f, snowY + leftW * 0.05f,
                cx, snowY + leftW * 0.03f
            )
            lineTo(cx, tipY)
            close()
        }
        drawPath(snowL, brush = Brush.linearGradient(
            colorStops = arrayOf(
                0.0f to Color(0xFFEEF4FF),
                0.6f to Color(0xFFC8DAF5).copy(alpha = 0.85f),
                1.0f to Color(0xFFA0BCE8).copy(alpha = 0f)
            ),
            start = Offset(cx, tipY),
            end = Offset(cx, snowY + leftW * 0.05f)
        ))

        // Right snow (shadow face)
        val snowR = Path().apply {
            moveTo(cx, tipY)
            lineTo(rightEdgeX, snowY)
            cubicTo(
                rightEdgeX - rightW * 0.10f, snowY + rightW * 0.06f,
                rightEdgeX - rightW * 0.22f, snowY + rightW * 0.10f,
                rightEdgeX - rightW * 0.30f, snowY + rightW * 0.07f
            )
            cubicTo(
                rightEdgeX - rightW * 0.38f, snowY + rightW * 0.04f,
                rightEdgeX - rightW * 0.45f, snowY + rightW * 0.09f,
                rightEdgeX - rightW * 0.55f, snowY + rightW * 0.06f
            )
            cubicTo(
                rightEdgeX - rightW * 0.65f, snowY + rightW * 0.03f,
                rightEdgeX - rightW * 0.72f, snowY + rightW * 0.08f,
                rightEdgeX - rightW * 0.80f, snowY + rightW * 0.04f
            )
            cubicTo(
                rightEdgeX - rightW * 0.90f, snowY + rightW * 0.02f,
                rightEdgeX - rightW * 0.96f, snowY + rightW * 0.05f,
                cx, snowY + rightW * 0.03f
            )
            lineTo(cx, tipY)
            close()
        }
        drawPath(snowR, brush = Brush.linearGradient(
            colorStops = arrayOf(
                0.0f to Color(0xFF7FA8D8).copy(alpha = 0.70f),
                1.0f to Color(0xFF4A7AB5).copy(alpha = 0f)
            ),
            start = Offset(cx, tipY),
            end = Offset(cx, snowY)
        ))

        // Tip highlight
        val tipHighlight = Path().apply {
            moveTo(cx - 3f, tipY + 10f)
            lineTo(cx, tipY)
            lineTo(cx + 3f, tipY + 10f)
            cubicTo(cx + 1f, tipY + 7f, cx - 1f, tipY + 7f, cx - 3f, tipY + 10f)
            close()
        }
        drawPath(tipHighlight, color = Color.White.copy(alpha = 0.95f))
    }

    // 9. Base mist
    drawRect(brush = Brush.verticalGradient(
        colors = listOf(SummitColors.Slate.copy(alpha = 0f), SummitColors.Slate),
        startY = 0f,
        endY = h
    ))
}

private fun DrawScope.drawFilledRidge(
    pts: List<Pair<Float, Float>>,
    w: Float,
    h: Float,
    topColor: Color,
    bottomColor: Color
) {
    val path = Path().apply {
        moveTo(pts[0].first * w, pts[0].second * h)
        for (pt in pts.drop(1)) lineTo(pt.first * w, pt.second * h)
        close()
    }
    drawPath(path, brush = Brush.verticalGradient(
        colors = listOf(topColor, bottomColor),
        startY = 0f,
        endY = h
    ))
}

private fun atmosphereColor(budgetUsed: Float): Color {
    val t = budgetUsed.coerceIn(0f, 1f)
    return if (t < 0.5f) lerp(SummitColors.Teal, SummitColors.Amber, t * 2f)
    else lerp(SummitColors.Amber, SummitColors.Rose, (t - 0.5f) * 2f)
}

private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t.coerceIn(0f, 1f)
