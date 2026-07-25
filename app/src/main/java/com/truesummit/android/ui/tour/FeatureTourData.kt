package com.truesummit.android.ui.tour

import com.truesummit.android.ui.navigation.Screen

data class TourFeature(val icon: String, val title: String, val detail: String)

data class TourStop(
    val route: String,
    val tabTitle: String,
    val headline: String,
    val features: List<TourFeature>
)

val tourStops: List<TourStop> = listOf(
    TourStop(
        route = Screen.Budget.route,
        tabTitle = "Budget",
        headline = "Give every dollar a job.",
        features = listOf(
            TourFeature("banknote", "Safe to Spend", "The tile up top shows what's actually free to spend right now."),
            TourFeature("folder", "Categories & groups", "Assign money to each category; tap one to fund, edit, or set a goal."),
            TourFeature("more_horiz", "Actions menu", "Move money, draft a budget from history, plan a paycheck, or manage rollover.")
        )
    ),
    TourStop(
        route = Screen.Transactions.route,
        tabTitle = "Transactions",
        headline = "Every purchase, in one stream.",
        features = listOf(
            TourFeature("add_circle", "Quick add & import", "Log a purchase in seconds, or import CSVs from Mint, YNAB, and Monarch."),
            TourFeature("filter_list", "Search & filters", "Filter by type, account, category, amount, or date range."),
            TourFeature("inbox", "Review inbox", "Imported transactions that still need a category land here."),
            TourFeature("call_split", "Splits, refunds & rules", "Split across categories, track refunds, and turn any merchant into a rule.")
        )
    ),
    TourStop(
        route = Screen.NetWorth.route,
        tabTitle = "Net Worth",
        headline = "Your whole financial picture.",
        features = listOf(
            TourFeature("show_chart", "The big picture", "Accounts, investments, and debts rolled into one trend over time."),
            TourFeature("check_circle", "Reconcile", "Open any account and reconcile to keep balances honest."),
            TourFeature("trending_down", "Debt Payoff Plan", "Compare avalanche vs. snowball and see your debt-free date.")
        )
    ),
    TourStop(
        route = Screen.Horizon.route,
        tabTitle = "Horizon",
        headline = "See what's coming.",
        features = listOf(
            TourFeature("calendar_month", "Bill Calendar", "Upcoming bills and scheduled items, laid out on a calendar."),
            TourFeature("waterfall_chart", "Cash-flow forecast", "Project your balances weeks or months ahead."),
            TourFeature("device_hub", "What-If Simulator", "Test a raise, a big purchase, or new rent before it happens."),
            TourFeature("repeat", "Subscriptions", "Recurring charges are detected and tracked automatically.")
        )
    ),
    TourStop(
        route = Screen.Peaks.route,
        tabTitle = "Your Peaks",
        headline = "Climb toward your goals.",
        features = listOf(
            TourFeature("flag", "Savings goals", "Each peak is a savings goal with a target amount and optional deadline."),
            TourFeature("trending_up", "Live progress", "The progress bar fills as you assign money to the goal's category each month."),
            TourFeature("add_circle", "Add a peak", "Tap + to name a goal and set a target — a savings category is created automatically.")
        )
    ),
    TourStop(
        route = Screen.Reports.route,
        tabTitle = "Reports",
        headline = "Look back with clarity.",
        features = listOf(
            TourFeature("pie_chart", "Spending breakdowns", "Where the money went, by category — tap anything to drill down."),
            TourFeature("compare_arrows", "Comparisons", "This month vs. last month, last year, or any custom range."),
            TourFeature("upload", "Export & Tax Pack", "CSV and PDF exports, plus a year-end tax summary.")
        )
    ),
    TourStop(
        route = Screen.Settings.route,
        tabTitle = "Settings",
        headline = "Tune TrueSummit to you.",
        features = listOf(
            TourFeature("cloud", "Sync & Account", "Sign in to back up your data and share a budget with a partner."),
            TourFeature("auto_fix_high", "Rules & Smart Alerts", "Auto-categorize merchants and get bill reminders."),
            TourFeature("palette", "Make it yours", "Customize your appearance and notification preferences."),
            TourFeature("lock", "Privacy & Data", "See exactly what syncs, and export or erase everything.")
        )
    )
)
