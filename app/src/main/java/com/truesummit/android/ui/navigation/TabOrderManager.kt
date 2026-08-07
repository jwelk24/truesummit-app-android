package com.truesummit.android.ui.navigation

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Persists the user's tab order, mirroring iOS's `tabOrder` AppStorage key.
 * The first [PRIMARY_TAB_COUNT] entries get their own slot in the bottom bar;
 * the rest are listed under "More".
 */
object TabOrderManager {
    private const val PREFS_NAME = "truesummit_theme_prefs"
    private const val KEY_TAB_ORDER = "tab_order"

    /**
     * Bottom-bar slots reserved for real tabs; the last slot is always More,
     * so the bar shows this many + 1. Four keeps every label on one line —
     * at five, "Transactions" wraps and its icon drifts up out of alignment
     * with the single-line tabs.
     */
    const val PRIMARY_TAB_COUNT = 4

    private val _order = MutableStateFlow(reorderableTabs)
    val order: StateFlow<List<Screen>> = _order

    private var prefs: android.content.SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _order.value = read()
    }

    /**
     * Saved order, reconciled against [reorderableTabs]: unknown routes are
     * dropped and tabs added in a later app version are appended, so a stale
     * saved value can never hide a tab outright.
     */
    private fun read(): List<Screen> {
        val raw = prefs?.getString(KEY_TAB_ORDER, null) ?: return reorderableTabs
        val byRoute = reorderableTabs.associateBy { it.route }
        val saved = raw.split(",").mapNotNull { byRoute[it] }
        val missing = reorderableTabs.filter { it !in saved }
        return saved + missing
    }

    fun setOrder(order: List<Screen>) {
        _order.value = order
        prefs?.edit()?.putString(KEY_TAB_ORDER, order.joinToString(",") { it.route })?.apply()
    }

    /** Moves the tab at [index] one slot toward the front. */
    fun moveUp(index: Int) {
        if (index <= 0) return
        setOrder(_order.value.toMutableList().apply { add(index - 1, removeAt(index)) })
    }

    /** Moves the tab at [index] one slot toward the back. */
    fun moveDown(index: Int) {
        val current = _order.value
        if (index >= current.lastIndex) return
        setOrder(current.toMutableList().apply { add(index + 1, removeAt(index)) })
    }

    fun reset() {
        _order.value = reorderableTabs
        prefs?.edit()?.remove(KEY_TAB_ORDER)?.apply()
    }
}
