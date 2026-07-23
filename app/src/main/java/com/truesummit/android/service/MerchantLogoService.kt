package com.truesummit.android.service

import android.content.Context

/**
 * Builds a logo URL for a merchant name. Uses Google's favicon CDN — the only
 * Summit feature that sends user-derived data over the network. Off by default;
 * gated behind explicit user consent in PrivacyDataScreen.
 */
object MerchantLogoService {

    private const val PREFS = "truesummit_prefs"
    private const val KEY_ENABLED = "merchantLogos.enabled"
    private const val KEY_CONSENT = "merchantLogos.consentShown"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_ENABLED, enabled).apply()

    fun wasConsentShown(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_CONSENT, false)

    fun markConsentShown(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_CONSENT, true).apply()

    /** Returns the favicon URL for a merchant, or null if the name is too short to be useful. */
    fun logoUrl(merchant: String): String? {
        val cleaned = MerchantCleaner.clean(merchant)
        val compact = cleaned.lowercase()
            .filter { it.isLetterOrDigit() }
        if (compact.length < 2) return null
        return "https://www.google.com/s2/favicons?sz=128&domain=$compact.com"
    }
}
