package com.truesummit.android.service

import android.content.Context

/**
 * Tidies raw bank/card merchant descriptors for display — entirely on-device,
 * no network. Non-destructive: only used for presentation; stored merchant
 * strings are never changed so search and sync stay intact.
 */
object MerchantCleaner {

    private const val PREFS = "truesummit_prefs"
    private const val KEY_ENABLED = "merchantCleaner.enabled"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, enabled).apply()

    private val prefixes = listOf(
        "SQ *", "SQ*", "TST* ", "TST*", "SP *", "SP* ", "PY *", "IN *",
        "PAYPAL *", "PP*", "GOOGLE *", "GOOGLE*", "APLPAY ", "POS ",
        "PURCHASE ", "DEBIT ", "CREDIT ", "CHECKCARD "
    )

    private val brandFixes = mapOf(
        "amzn mktp" to "Amazon", "amzn" to "Amazon", "amazon mktpl" to "Amazon", "amazon" to "Amazon",
        "wm supercenter" to "Walmart", "walmart" to "Walmart", "wal mart" to "Walmart",
        "dd doordash" to "DoorDash", "doordash" to "DoorDash",
        "uber eats" to "Uber Eats", "ubereats" to "Uber Eats", "uber" to "Uber",
        "nflx" to "Netflix", "netflix" to "Netflix",
        "sq" to "Square"
    )

    fun clean(raw: String): String {
        var s = raw.trim()
        if (s.isEmpty()) return raw

        // 1. Strip a leading processor prefix
        val upper = s.uppercase()
        for (prefix in prefixes) {
            if (upper.startsWith(prefix.uppercase())) {
                s = s.drop(prefix.length).trim()
                break
            }
        }

        // 2. Drop store-number / long numeric tokens ("#1234", "0001234")
        val tokens = s.split(" ").filter { token ->
            if (token.startsWith("#")) return@filter false
            val digitCount = token.count { it.isDigit() }
            if (digitCount >= 4 && digitCount == token.length) return@filter false
            true
        }
        s = tokens.joinToString(" ")

        // 3. Collapse whitespace
        s = s.replace(Regex("\\s+"), " ").trim()
        if (s.isEmpty()) return raw

        // 4. Known-brand normalization (prefix match on lowercased result)
        val key = s.lowercase()
        for ((needle, brand) in brandFixes) {
            if (key == needle || key.startsWith("$needle ")) return brand
        }

        // 5. Title-case shouty all-caps descriptors
        val letters = s.filter { it.isLetter() }
        val uppercase = letters.count { it.isUpperCase() }
        if (letters.isNotEmpty() && uppercase.toDouble() / letters.length > 0.7) {
            s = s.split(" ").joinToString(" ") { word ->
                word.lowercase().replaceFirstChar { it.uppercase() }
            }
        }

        return s.ifEmpty { raw }
    }
}
