package com.truesummit.android.ui.theme

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object ThemeManager {
    private const val PREFS_NAME = "truesummit_theme_prefs"
    private const val KEY_ACCENT_COLOR = "accent_color"
    private const val KEY_BACKGROUND_COLOR = "background_color"
    private const val KEY_ROW_BACKGROUND_COLOR = "row_background_color"

    private val _accentColor = MutableStateFlow<Color?>(null)
    val accentColor: StateFlow<Color?> = _accentColor

    private val _backgroundColor = MutableStateFlow<Color?>(null)
    val backgroundColor: StateFlow<Color?> = _backgroundColor

    private val _rowBackgroundColor = MutableStateFlow<Color?>(null)
    val rowBackgroundColor: StateFlow<Color?> = _rowBackgroundColor

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        val accent = prefs.getInt(KEY_ACCENT_COLOR, 0)
        if (accent != 0) _accentColor.value = Color(accent)

        val bg = prefs.getInt(KEY_BACKGROUND_COLOR, 0)
        if (bg != 0) _backgroundColor.value = Color(bg)

        val rowBg = prefs.getInt(KEY_ROW_BACKGROUND_COLOR, 0)
        if (rowBg != 0) _rowBackgroundColor.value = Color(rowBg)
    }

    fun setAccentColor(context: Context, color: Color?) {
        _accentColor.value = color
        saveColor(context, KEY_ACCENT_COLOR, color)
    }

    fun setBackgroundColor(context: Context, color: Color?) {
        _backgroundColor.value = color
        saveColor(context, KEY_BACKGROUND_COLOR, color)
    }

    fun setRowBackgroundColor(context: Context, color: Color?) {
        _rowBackgroundColor.value = color
        saveColor(context, KEY_ROW_BACKGROUND_COLOR, color)
    }

    private fun saveColor(context: Context, key: String, color: Color?) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (color == null) {
            prefs.edit().remove(key).apply()
        } else {
            prefs.edit().putInt(key, color.toArgb()).apply()
        }
    }
}
