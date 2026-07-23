package com.truesummit.android.ui.budget

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.truesummit.android.ui.theme.SummitColors
import java.util.UUID

object CategoryBarColor {
    private const val PREFS = "truesummit_prefs"
    private const val KEY = "categoryBarColorByID"

    val palette = listOf(
        "Teal" to 0xFF4ECDC4, "Amber" to 0xFFF7B731, "Rose" to 0xFFFF6B6B,
        "Lavender" to 0xFF9B8EC4, "Green" to 0xFF34C759, "Mint" to 0xFF66D4CF,
        "Cyan" to 0xFF64D2FF, "Blue" to 0xFF0A84FF, "Indigo" to 0xFF5E5CE6,
        "Purple" to 0xFFBF5AF2, "Pink" to 0xFFFF375F, "Orange" to 0xFFFF9F0A,
    )

    private fun Context.prefMap(): MutableMap<String, Int> {
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY, "") ?: ""
        if (raw.isEmpty()) return mutableMapOf()
        return raw.split(",").mapNotNull {
            val parts = it.split("=")
            if (parts.size == 2) parts[0] to parts[1].toIntOrNull() else null
        }.filter { it.second != null }.associate { it.first to it.second!! }.toMutableMap()
    }

    private fun Context.savePrefMap(map: Map<String, Int>) {
        val raw = map.entries.joinToString(",") { "${it.key}=${it.value}" }
        getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, raw).apply()
    }

    fun getColor(context: Context, id: UUID): Color? {
        val argb = context.prefMap()[id.toString()] ?: return null
        return Color(argb)
    }

    fun setColor(context: Context, id: UUID, color: Color?) {
        val map = context.prefMap()
        if (color == null) map.remove(id.toString())
        else map[id.toString()] = color.toArgb()
        context.savePrefMap(map)
    }

    fun effectiveColor(context: Context, id: UUID, categoryName: String): Color {
        return getColor(context, id) ?: nameHashColor(categoryName)
    }

    private fun nameHashColor(name: String): Color {
        val hash = name.hashCode()
        return SummitColors.accent(kotlin.math.abs(hash) % 4)
    }
}
