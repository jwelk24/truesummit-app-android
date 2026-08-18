package com.truesummit.android.ui

import com.truesummit.android.ui.networth.PlaidConnectionsViewModel
import com.plaid.link.configuration.LinkTokenConfiguration
import com.plaid.link.result.LinkSuccess
import com.plaid.link.OpenPlaidLink
import androidx.lifecycle.viewmodel.compose.viewModel
import com.truesummit.android.ui.transactions.viewmodel.TransactionsViewModel
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.truesummit.android.service.SyncService
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.truesummit.android.ui.budget.BudgetScreen
import com.truesummit.android.ui.horizon.CashFlowForecastScreen
import com.truesummit.android.ui.horizon.HorizonScreen
import com.truesummit.android.ui.insights.AIInsightsScreen
import com.truesummit.android.ui.more.MoreScreen
import com.truesummit.android.ui.navigation.Screen
import com.truesummit.android.ui.navigation.TabOrderManager
import com.truesummit.android.ui.navigation.bottomNavRoutes
import com.truesummit.android.ui.networth.NetWorthScreen
import com.truesummit.android.ui.networth.PlaidConnectionsScreen
import com.truesummit.android.ui.reports.ReportsScreen
import com.truesummit.android.ui.settings.CustomizeAppearanceScreen
import com.truesummit.android.ui.transactions.TransactionsScreen
import com.truesummit.android.ui.transactions.ReceiptScannerScreen
import com.truesummit.android.ui.transactions.editor.TransactionEditorScreen
import java.util.UUID

import com.truesummit.android.ui.alerts.SmartAlertsScreen
import com.truesummit.android.ui.billing.PaywallScreen
import com.truesummit.android.ui.budget.PaycheckPlanScreen
import com.truesummit.android.ui.calendar.BillCalendarScreen
import com.truesummit.android.ui.challenges.ChallengesScreen
import com.truesummit.android.ui.review.WeeklyReviewScreen
import com.truesummit.android.ui.rules.CategoryRulesScreen
import com.truesummit.android.ui.rules.RuleEditorScreen
import com.truesummit.android.ui.subscriptions.SubscriptionsScreen
import com.truesummit.android.ui.transactions.RefundTrackerScreen
import com.truesummit.android.ui.whatif.WhatIfScreen
import com.truesummit.android.ui.peaks.PeaksScreen
import com.truesummit.android.ui.wrapped.WrappedScreen
import com.truesummit.android.ui.coach.CoachScreen
import com.truesummit.android.ui.savetospend.SafeToSpendScreen
import com.truesummit.android.ui.health.FinancialHealthScreen
import com.truesummit.android.ui.budget.BudgetDraftScreen
import com.truesummit.android.ui.debt.DebtPayoffScreen
import com.truesummit.android.ui.settleup.SettleUpScreen
import com.truesummit.android.ui.tax.TaxPackScreen
import com.truesummit.android.ui.settings.PrivacyDataScreen
import com.truesummit.android.ui.settings.SettingsScreen
import com.truesummit.android.ui.inbox.ReviewInboxScreen
import com.truesummit.android.ui.onboarding.OnboardingState
import com.truesummit.android.ui.onboarding.OnboardingWelcomeScreen
import com.truesummit.android.ui.reports.MonthRecapScreen
import com.truesummit.android.ui.tour.FeatureGuideScreen
import com.truesummit.android.ui.tour.FeatureTourCard
import com.truesummit.android.ui.tour.FeatureTourState
import com.truesummit.android.ui.tour.tourStops
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val navController = rememberNavController()

    // Both activity-result launchers below are registered here, not inside the
    // screens that use them. Leaving the app — to the system file picker, or to
    // Plaid Link and on to Messages for an SMS code — can recreate
    // MainActivity, and the NavHost then restarts on its first tab. A launcher
    // registered inside a screen's composition no longer exists when the result
    // arrives, so it is dropped with no error at all. MainScreen is always
    // composed, so it re-registers and receives the result either way.
    //
    // For Plaid that failure is expensive: the bank links successfully, a Plaid
    // connection is consumed, and the app never learns the item exists.
    val plaidViewModel: PlaidConnectionsViewModel = viewModel()
    val pendingLinkToken by plaidViewModel.pendingLinkToken.collectAsState()
    val plaidLauncher = rememberLauncherForActivityResult(OpenPlaidLink()) { result ->
        if (result is LinkSuccess) {
            plaidViewModel.onLinkSuccess(
                publicToken = result.publicToken,
                institutionName = result.metadata.institution?.name
            )
        }
    }
    LaunchedEffect(pendingLinkToken) {
        val token = pendingLinkToken ?: return@LaunchedEffect
        plaidViewModel.onLinkTokenConsumed()
        plaidLauncher.launch(LinkTokenConfiguration.Builder().token(token).build())
    }

    val csvViewModel: TransactionsViewModel = viewModel()
    val csvImportMessage by csvViewModel.importMessage.collectAsState()
    val csvPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { csvViewModel.importCsv(it) } }

    val tourActive by FeatureTourState.isActive.collectAsState()
    val tourStop by FeatureTourState.currentStop.collectAsState()
    var showOnboarding by remember { mutableStateOf(!OnboardingState.hasCompletedWelcome) }

    val tabOrder by TabOrderManager.order.collectAsState()
    val primaryTabs = tabOrder.take(TabOrderManager.PRIMARY_TAB_COUNT)
    val overflowTabs = tabOrder.drop(TabOrderManager.PRIMARY_TAB_COUNT)
    val barRoutes = bottomNavRoutes(primaryTabs)

    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        topBar = {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = navBackStackEntry?.destination?.route
            val isMainTab = currentRoute in barRoutes
            if (isMainTab) {
                TopAppBar(
                    title = {},
                    actions = {
                        IconButton(onClick = { navController.navigate(Screen.Settings.route) }) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                )
            }
        },
        bottomBar = {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentDestination = navBackStackEntry?.destination
            val hideBottomBar = currentDestination?.route?.startsWith(Screen.TransactionEditor.route) == true || 
                               currentDestination?.route == Screen.Paywall.route ||
                               currentDestination?.route?.startsWith(Screen.CategoryRules.route) == true ||
                               currentDestination?.route?.startsWith(Screen.RuleEditor.route) == true
            if (!hideBottomBar) {
                Column {
                    SyncIndicator()
                    NavigationBar {
                        (primaryTabs + Screen.More).forEach { screen ->
                            NavigationBarItem(
                                icon = { Icon(screen.icon, contentDescription = null) },
                                label = {
                                    // One line only: NavigationBarItem centers
                                    // icon+label together, so a wrapped label
                                    // shifts its icon up out of line with the
                                    // other tabs. 11sp is what keeps the
                                    // longest label ("Transactions") inside its
                                    // slot without ellipsizing.
                                    Text(
                                        screen.title,
                                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                                onClick = {
                                    navController.navigate(screen.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Budget.route,
            modifier = Modifier.padding(innerPadding),
            enterTransition = { fadeIn(animationSpec = tween(300)) + slideInHorizontally(animationSpec = tween(300)) { it / 10 } },
            exitTransition = { fadeOut(animationSpec = tween(300)) + slideOutHorizontally(animationSpec = tween(300)) { -it / 10 } },
            popEnterTransition = { fadeIn(animationSpec = tween(300)) + slideInHorizontally(animationSpec = tween(300)) { -it / 10 } },
            popExitTransition = { fadeOut(animationSpec = tween(300)) + slideOutHorizontally(animationSpec = tween(300)) { it / 10 } }
        ) {
            composable(Screen.Budget.route) {
                BudgetScreen(
                    onPaycheckPlan = { navController.navigate(Screen.PaycheckPlan.route) },
                    onBudgetDraft = { navController.navigate(Screen.BudgetDraft.route) },
                    onDebtPayoff = { navController.navigate(Screen.DebtPayoff.route) },
                    onAddTransaction = { navController.navigate(Screen.TransactionEditor.route) },
                    onGoToNetWorth = { navController.navigate(Screen.NetWorth.route) },
                    onConnectBank = { navController.navigate(Screen.PlaidConnections.route) },
                    onTakeTour = { FeatureTourState.start() },
                    onOpenSettings = { navController.navigate(Screen.Settings.route) }
                )
            }
            composable(Screen.Transactions.route) {
                TransactionsScreen(
                    onAddTransaction = { navController.navigate(Screen.TransactionEditor.route) },
                    onEditTransaction = { txId -> navController.navigate("${Screen.TransactionEditor.route}/$txId") },
                    onScanReceipt = { navController.navigate(Screen.ReceiptScanner.route) },
                    onUpgrade = { navController.navigate(Screen.Paywall.route) },
                    onRefundTracker = { navController.navigate(Screen.RefundTracker.route) },
                    onReviewInbox = { navController.navigate(Screen.ReviewInbox.route) },
                    onImportCsv = {
                        csvPicker.launch(
                            arrayOf(
                                "text/csv",
                                "text/comma-separated-values",
                                "text/plain",
                                "application/csv",
                                "*/*"
                            )
                        )
                    }
                )
            }
            composable(Screen.ReceiptScanner.route) {
                ReceiptScannerScreen(onDismiss = { navController.popBackStack() })
            }
            composable(Screen.NetWorth.route) { 
                NetWorthScreen(onManageConnections = { navController.navigate(Screen.PlaidConnections.route) }) 
            }
            composable(Screen.PlaidConnections.route) {
                PlaidConnectionsScreen(
                    onBack = { navController.popBackStack() },
                    onAddBank = { /* TODO */ },
                    onUpgrade = { navController.navigate(Screen.Paywall.route) },
                    onSettleUp = { navController.navigate(Screen.SettleUp.route) },
                    viewModel = plaidViewModel
                )
            }
            composable(Screen.Horizon.route) {
                HorizonScreen(
                    onShowForecast = { navController.navigate(Screen.CashFlowForecast.route) },
                    onWhatIf = { navController.navigate(Screen.WhatIf.route) },
                    onBillCalendar = { navController.navigate(Screen.BillCalendar.route) }
                )
            }
            composable(Screen.CashFlowForecast.route) {
                CashFlowForecastScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.Peaks.route) {
                PeaksScreen(onNavigateToCategory = { /* TODO: open category detail */ })
            }
            composable(Screen.Reports.route) {
                ReportsScreen(
                    onBack = { navController.popBackStack() },
                    onTaxPack = { navController.navigate(Screen.TaxPack.route) }
                )
            }
            composable(Screen.More.route) {
                MoreScreen(
                    overflowTabs = overflowTabs,
                    onTab = { screen -> navController.navigate(screen.route) },
                    onCoach = { navController.navigate(Screen.Coach.route) },
                    onSafeToSpend = { navController.navigate(Screen.SafeToSpend.route) },
                    onFinancialHealth = { navController.navigate(Screen.FinancialHealth.route) },
                    onWeeklyReview = { navController.navigate(Screen.WeeklyReview.route) },
                    onWrapped = { navController.navigate(Screen.Wrapped.route) },
                    onChallenges = { navController.navigate(Screen.Challenges.route) },
                    onMonthRecap = { navController.navigate(Screen.MonthRecap.route) },
                    onSettings = { navController.navigate(Screen.Settings.route) }
                )
            }
            composable(Screen.Insights.route) {
                AIInsightsScreen(
                    onUpgrade = { navController.navigate(Screen.Paywall.route) },
                    onWeeklyReview = { navController.navigate(Screen.WeeklyReview.route) },
                    onWrapped = { navController.navigate(Screen.Wrapped.route) },
                    onChallenges = { navController.navigate(Screen.Challenges.route) },
                    onCoach = { navController.navigate(Screen.Coach.route) },
                    onSafeToSpend = { navController.navigate(Screen.SafeToSpend.route) },
                    onFinancialHealth = { navController.navigate(Screen.FinancialHealth.route) },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.CategoryRules.route) {
                CategoryRulesScreen(
                    onBack = { navController.popBackStack() },
                    onAddRule = { navController.navigate(Screen.RuleEditor.route) },
                    onEditRule = { ruleId -> navController.navigate("${Screen.RuleEditor.route}?ruleId=$ruleId") },
                    onUpgrade = { navController.navigate(Screen.Paywall.route) }
                )
            }
            composable(Screen.SmartAlerts.route) {
                SmartAlertsScreen(
                    onBack = { navController.popBackStack() },
                    onUpgrade = { navController.navigate(Screen.Paywall.route) }
                )
            }
            composable(Screen.Subscriptions.route) {
                SubscriptionsScreen(
                    onBack = { navController.popBackStack() },
                    onUpgrade = { navController.navigate(Screen.Paywall.route) }
                )
            }
            composable(Screen.CustomizeAppearance.route) {
                CustomizeAppearanceScreen(onBack = { navController.popBackStack() })
            }
            composable(
                route = "${Screen.RuleEditor.route}?ruleId={ruleId}&seedMerchant={seedMerchant}&seedCategoryId={seedCategoryId}",
                arguments = listOf(
                    navArgument("ruleId") { type = NavType.StringType; nullable = true },
                    navArgument("seedMerchant") { type = NavType.StringType; nullable = true },
                    navArgument("seedCategoryId") { type = NavType.StringType; nullable = true }
                )
            ) { backStackEntry ->
                val ruleId = backStackEntry.arguments?.getString("ruleId")?.let { UUID.fromString(it) }
                val seedMerchant = backStackEntry.arguments?.getString("seedMerchant")
                val seedCategoryId = backStackEntry.arguments?.getString("seedCategoryId")?.let { UUID.fromString(it) }
                RuleEditorScreen(
                    ruleId = ruleId,
                    seedMerchant = seedMerchant,
                    seedCategoryId = seedCategoryId,
                    onDismiss = { navController.popBackStack() }
                )
            }
            composable(Screen.Paywall.route) {
                PaywallScreen(onDismiss = { navController.popBackStack() })
            }
            composable(Screen.PaycheckPlan.route) {
                PaycheckPlanScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.Challenges.route) {
                ChallengesScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.WeeklyReview.route) {
                WeeklyReviewScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.Wrapped.route) {
                WrappedScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.BillCalendar.route) {
                BillCalendarScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.WhatIf.route) {
                WhatIfScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.RefundTracker.route) {
                RefundTrackerScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.Coach.route) {
                CoachScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.SafeToSpend.route) {
                SafeToSpendScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.FinancialHealth.route) {
                FinancialHealthScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.BudgetDraft.route) {
                BudgetDraftScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.DebtPayoff.route) {
                DebtPayoffScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.SettleUp.route) {
                SettleUpScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.TaxPack.route) {
                TaxPackScreen(
                    onBack = { navController.popBackStack() },
                    onUpgrade = { navController.navigate(Screen.Paywall.route) }
                )
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    onSyncAccount = { navController.navigate(Screen.PlaidConnections.route) },
                    onCategoryRules = { navController.navigate(Screen.CategoryRules.route) },
                    onSmartAlerts = { navController.navigate(Screen.SmartAlerts.route) },
                    onSubscriptions = { navController.navigate(Screen.Subscriptions.route) },
                    onCustomizeAppearance = { navController.navigate(Screen.CustomizeAppearance.route) },
                    onFeatureGuide = { navController.navigate(Screen.FeatureGuide.route) },
                    onPrivacyData = { navController.navigate(Screen.PrivacyData.route) },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.PrivacyData.route) {
                PrivacyDataScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.ReviewInbox.route) {
                ReviewInboxScreen(
                    onBack = { navController.popBackStack() },
                    onEditTransaction = { txId -> navController.navigate("${Screen.TransactionEditor.route}/$txId") }
                )
            }
            composable(Screen.FeatureGuide.route) {
                FeatureGuideScreen(
                    onBack = { navController.popBackStack() },
                    onStartTour = {
                        navController.popBackStack()
                        FeatureTourState.start()
                    },
                    onNavigateToTab = { route -> navController.navigate(route) }
                )
            }
            composable(Screen.MonthRecap.route) {
                MonthRecapScreen(onBack = { navController.popBackStack() })
            }
            composable(
                route = "${Screen.TransactionEditor.route}/{transactionId}",
                arguments = listOf(navArgument("transactionId") { type = NavType.StringType; nullable = true })
            ) { backStackEntry ->
                val transactionId = backStackEntry.arguments?.getString("transactionId")?.let { UUID.fromString(it) }
                TransactionEditorScreen(
                    transactionId = transactionId,
                    onDismiss = { navController.popBackStack() },
                    onCreateRule = { merchant, categoryId ->
                        navController.navigate("${Screen.RuleEditor.route}?seedMerchant=$merchant&seedCategoryId=$categoryId")
                    }
                ) 
            }
            composable(Screen.TransactionEditor.route) { 
                TransactionEditorScreen(
                    onDismiss = { navController.popBackStack() },
                    onCreateRule = { merchant, categoryId ->
                        navController.navigate("${Screen.RuleEditor.route}?seedMerchant=$merchant&seedCategoryId=$categoryId")
                    }
                )
            }
        }
    } // end Scaffold

    if (tourActive && tourStop < tourStops.size) {
        val stop = tourStops[tourStop]
        LaunchedEffect(tourStop) {
            navController.navigate(stop.route) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(modifier = Modifier.padding(16.dp)) {
                FeatureTourCard(
                    index = tourStop,
                    onAdvance = { next -> FeatureTourState.advance(next) },
                    onFinish = { FeatureTourState.finish() },
                    onClose = { FeatureTourState.close() }
                )
            }
        }
    }

    if (showOnboarding) {
        Box(modifier = Modifier.fillMaxSize()) {
            OnboardingWelcomeScreen(
                onFinish = {
                    OnboardingState.hasCompletedWelcome = true
                    showOnboarding = false
                },
                onConnectBank = {
                    OnboardingState.hasCompletedWelcome = true
                    showOnboarding = false
                    navController.navigate(Screen.PlaidConnections.route)
                }
            )
        }
    }
    } // end outer Box

    // Shown from here for the same reason the launcher lives here: the import
    // can finish after the Transactions screen has been torn down.
    csvImportMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { csvViewModel.dismissImportMessage() },
            title = { Text("Import Result") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { csvViewModel.dismissImportMessage() }) { Text("OK") }
            }
        )
    }
}

@Composable
fun SyncIndicator() {
    val isSyncing by SyncService.isSyncing.collectAsStateWithLifecycle()
    AnimatedVisibility(
        visible = isSyncing,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut()
    ) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }
}
