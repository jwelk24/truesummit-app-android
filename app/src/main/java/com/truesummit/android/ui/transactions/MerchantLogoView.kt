package com.truesummit.android.ui.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.truesummit.android.service.MerchantLogoService

/**
 * Shows a merchant logo fetched via [MerchantLogoService] when merchant logos
 * are enabled. Falls back to a colored dot on load error or no URL.
 */
@Composable
fun MerchantLogoView(
    merchant: String,
    fallbackColor: Color,
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
    ringColor: Color? = null
) {
    val url = MerchantLogoService.logoUrl(merchant)
    if (url != null) {
        SubcomposeAsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(6.dp)),
            error = { CategoryDot(fallbackColor, size = size, ringColor = ringColor) },
            loading = { CategoryDot(fallbackColor, size = size, ringColor = ringColor) }
        )
    } else {
        CategoryDot(fallbackColor, modifier = modifier, size = size, ringColor = ringColor)
    }
}

@Composable
fun CategoryDot(
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 12.dp,
    ringColor: Color? = null
) {
    Box(
        modifier = modifier.size(size + 6.dp),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(Brush.verticalGradient(listOf(color, color.copy(alpha = 0.7f))))
        )
        if (ringColor != null) {
            Box(
                modifier = Modifier
                    .size(size + 4.dp)
                    .clip(CircleShape)
                    .border(1.5.dp, ringColor, CircleShape)
            )
        }
    }
}
