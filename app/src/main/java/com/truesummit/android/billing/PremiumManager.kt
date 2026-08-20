package com.truesummit.android.billing

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class SubscriptionTier(val displayName: String) {
    NONE("None"),
    PRO("Pro"),
    PREMIUM("Premium")
}

enum class SubscriptionPeriod {
    MONTHLY,
    YEARLY
}

enum class PremiumFeature(val icon: String, val title: String, val description: String) {
    RECEIPT_SCANNING("doc.text.viewfinder", "Receipt Scanning", "Capture receipts with your camera and turn them into transactions."),
    HOUSEHOLD("person.3", "Family Sharing", "Share a single budget with your partner or family in real time."),
    AI_INSIGHTS("sparkles", "AI Insights", "Personalized analysis of your spending and savings patterns."),
    AUTO_RULES("wand.and.stars", "Auto-Categorization", "Automatically categorize transactions with custom rules."),
    SMART_ALERTS("bell.badge", "Smart Alerts", "Get notified about overspending and unusual charges."),
    SUBSCRIPTION_TRACKER("repeat.circle", "Subscription Tracker", "Surface every recurring charge so nothing slips by."),
    INVESTMENTS("chart.line.uptrend.xyaxis", "Investments", "Track holdings, performance, and dividends alongside your budget."),
    LIABILITIES("creditcard.trianglebadge.exclamationmark", "Liabilities", "See loan balances, APRs, and payoff projections in one place."),
    EXPORT_REPORTS("square.and.arrow.up", "Export Reports", "Export PDF or CSV reports for any date range."),
    UNLIMITED_HORIZON("infinity", "Unlimited Horizon", "Forecast cash flow as far as a year out."),
    UNLIMITED_BANK_LINKS("building.columns", "Unlimited Bank Links", "Connect all your bank, credit, and investment accounts — no limit.")
}

object PremiumManager {
    private val _currentTier = MutableStateFlow(SubscriptionTier.PRO)
    val currentTier: StateFlow<SubscriptionTier> = _currentTier

    private val _currentPeriod = MutableStateFlow(SubscriptionPeriod.MONTHLY)
    val currentPeriod: StateFlow<SubscriptionPeriod> = _currentPeriod

    fun setTier(tier: SubscriptionTier) {
        _currentTier.value = tier
    }

    fun setPeriod(period: SubscriptionPeriod) {
        _currentPeriod.value = period
    }

    // Pricing Labels
    fun getMonthlyPriceLabel(tier: SubscriptionTier): String = when (tier) {
        SubscriptionTier.PRO -> "$7.99/mo"
        SubscriptionTier.PREMIUM -> "$12.99/mo"
        else -> ""
    }

    fun getYearlyPriceLabel(tier: SubscriptionTier): String = when (tier) {
        SubscriptionTier.PRO -> "$69/yr"
        SubscriptionTier.PREMIUM -> "$99/yr"
        else -> ""
    }

    fun getYearlySavingsPercent(tier: SubscriptionTier): Int = when (tier) {
        SubscriptionTier.PRO -> 28
        SubscriptionTier.PREMIUM -> 36
        else -> 0
    }

    // Feature Gates
    fun canScanReceipts(): Boolean = _currentTier.value == SubscriptionTier.PREMIUM
    fun canUseHousehold(): Boolean = _currentTier.value == SubscriptionTier.PREMIUM
    fun canUseAIInsights(): Boolean = _currentTier.value == SubscriptionTier.PREMIUM
    fun canUseAutoRules(): Boolean = _currentTier.value == SubscriptionTier.PREMIUM
    fun canUseSmartAlerts(): Boolean = _currentTier.value == SubscriptionTier.PREMIUM
    fun canUseSubscriptionTracker(): Boolean = _currentTier.value == SubscriptionTier.PREMIUM
    fun canTrackInvestments(): Boolean = _currentTier.value == SubscriptionTier.PREMIUM
    fun canTrackLiabilities(): Boolean = _currentTier.value == SubscriptionTier.PREMIUM
    fun canExportReports(): Boolean = _currentTier.value == SubscriptionTier.PREMIUM

    // Numeric Caps
    fun getMaxPlaidItems(): Int = if (_currentTier.value == SubscriptionTier.PREMIUM) 100 else 15

    fun getMaxHorizonDays(): Int = if (_currentTier.value == SubscriptionTier.PREMIUM) 365 else 30

    fun getMaxHistoryMonths(): Int = if (_currentTier.value == SubscriptionTier.PREMIUM) Int.MAX_VALUE else 12
    
    fun tierRequired(feature: PremiumFeature): SubscriptionTier {
        return SubscriptionTier.PREMIUM
    }
}
