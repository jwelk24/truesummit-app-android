package com.truesummit.android.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = DarkSummitPrimary,
    secondary = DarkSummitSecondary,
    tertiary = DarkSummitTertiary,
    background = DarkSummitBackground,
    onBackground = SummitColors.Ice,
    surface = DarkSummitSurface,
    onSurface = SummitColors.Ice,
    surfaceVariant = DarkSummitSurfaceVariant,
    onSurfaceVariant = DarkSummitOnSurfaceVariant,
    outline = DarkSummitOutline,
    outlineVariant = DarkSummitOutlineVariant,
    error = SummitColors.Rose,
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = SummitPrimary,
    secondary = SummitSecondary,
    tertiary = SummitTertiary,
    background = SummitBackground,
    surface = SummitSurface
)

@Composable
fun TrueSummitTheme(
    // Summit's signature look is the dark slate palette; matching iOS,
    // system light mode is intentionally not supported (Customize can
    // still recolor the accent).
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val customAccent by ThemeManager.accentColor.collectAsState()
    val customBackground by ThemeManager.backgroundColor.collectAsState()

    val baseColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val colorScheme = baseColorScheme.copy(
        primary = customAccent ?: baseColorScheme.primary,
        background = customBackground ?: baseColorScheme.background
    )

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
