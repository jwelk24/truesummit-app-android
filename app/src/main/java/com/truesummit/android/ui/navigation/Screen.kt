package com.truesummit.android.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Budget : Screen("budget", "Budget", Icons.Default.TableChart)
    object Transactions : Screen("transactions", "Transactions", Icons.Default.CreditCard)
    object NetWorth : Screen("networth", "Net Worth", Icons.Default.ShowChart)
    object Horizon : Screen("horizon", "Horizon", Icons.Default.Terrain)
    object Reports : Screen("reports", "Reports", Icons.Default.PieChart)
    object Insights : Screen("insights", "Insights", Icons.Default.AutoAwesome)
    object TransactionEditor : Screen("transaction_editor", "New Transaction", Icons.Default.Add)
    object PlaidConnections : Screen("plaid_connections", "Plaid Connections", Icons.Default.Link)
    object ReceiptScanner : Screen("receipt_scanner", "Scan Receipt", Icons.Default.DocumentScanner)
    object Paywall : Screen("paywall", "Upgrade", Icons.Default.Star)
    object CategoryRules : Screen("category_rules", "Rules", Icons.Default.AutoFixHigh)
    object SmartAlerts : Screen("smart_alerts", "Smart Alerts", Icons.Default.NotificationsActive)
    object Subscriptions : Screen("subscriptions", "Subscriptions", Icons.Default.Repeat)
    object RuleEditor : Screen("rule_editor", "Edit Rule", Icons.Default.Edit)
    object CustomizeAppearance : Screen("customize_appearance", "Appearance", Icons.Default.Palette)
    object PaycheckPlan : Screen("paycheck_plan", "Paycheck Plan", Icons.Default.Payments)
    object Challenges : Screen("challenges", "Challenges", Icons.Default.EmojiEvents)
    object WeeklyReview : Screen("weekly_review", "Weekly Review", Icons.Default.CheckCircle)
    object Wrapped : Screen("wrapped", "Wrapped", Icons.Default.AutoAwesome)
    object BillCalendar : Screen("bill_calendar", "Bill Calendar", Icons.Default.CalendarMonth)
    object WhatIf : Screen("what_if", "What If?", Icons.Default.TrendingUp)
    object RefundTracker : Screen("refund_tracker", "Refund Tracker", Icons.Default.AssignmentReturn)
    object Coach : Screen("coach", "Financial Coach", Icons.Default.Psychology)
    object SafeToSpend : Screen("safe_to_spend", "Safe to Spend", Icons.Default.AttachMoney)
    object FinancialHealth : Screen("financial_health", "Financial Health", Icons.Default.Favorite)
    object BudgetDraft : Screen("budget_draft", "Draft Budget", Icons.Default.AutoFixHigh)
    object DebtPayoff : Screen("debt_payoff", "Debt Payoff", Icons.Default.AccountBalance)
    object SettleUp : Screen("settle_up", "Settle Up", Icons.Default.People)
    object TaxPack : Screen("tax_pack", "Tax Pack", Icons.Default.Receipt)
    object CashFlowForecast : Screen("forecast", "Cash Flow Forecast", Icons.Default.WaterfallChart)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)
    object ReviewInbox : Screen("review_inbox", "Review", Icons.Default.Inbox)
    object FeatureGuide : Screen("feature_guide", "Feature Guide", Icons.Default.Map)
    object MonthRecap : Screen("month_recap", "Month Recap", Icons.Default.CalendarMonth)
    object PrivacyData : Screen("privacy_data", "Privacy & Data", Icons.Default.Lock)
    object Peaks : Screen("peaks", "Your Peaks", Icons.Default.Flag)
    object More : Screen("more", "More", Icons.Default.MoreHoriz)
}

/**
 * Tabs the user can reorder, in default order. Mirrors iOS, which shows the
 * first handful in the bar and puts the rest behind "More" — six bar slots is
 * the ceiling here, since at seven the labels wrap.
 */
val reorderableTabs = listOf(
    Screen.Budget,
    Screen.Transactions,
    Screen.NetWorth,
    Screen.Horizon,
    Screen.Peaks,
    Screen.Reports,
    Screen.Insights
)

/** Routes that own a bottom-bar slot, used to decide when to show the shared top bar. */
fun bottomNavRoutes(primaryTabs: List<Screen>): Set<String> =
    primaryTabs.map { it.route }.toSet() + Screen.More.route
