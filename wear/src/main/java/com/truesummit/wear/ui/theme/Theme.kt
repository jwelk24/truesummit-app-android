package com.truesummit.wear.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Colors

val Teal = Color(0xFF4ECDC4)
val Amber = Color(0xFFF7B731)
val Rose = Color(0xFFFF6B6B)
val Slate = Color(0xFF1C2333)
val Slate2 = Color(0xFF252E42)
val Green = Color(0xFF10B981)
val Red = Color(0xFFEF4444)

private val WearColorPalette = Colors(
    primary = Teal,
    primaryVariant = Color(0xFF3AB8B0),
    secondary = Amber,
    background = Slate,
    surface = Slate2,
    error = Rose,
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onBackground = Color.White,
    onSurface = Color.White,
    onError = Color.White
)

@Composable
fun TrueSummitWearTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colors = WearColorPalette,
        content = content
    )
}
