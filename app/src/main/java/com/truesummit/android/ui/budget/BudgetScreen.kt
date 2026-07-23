package com.truesummit.android.ui.budget

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.truesummit.android.data.entity.CategoryEntity
import com.truesummit.android.service.BudgetEngine
import com.truesummit.android.ui.budget.viewmodel.BudgetViewModel
import com.truesummit.android.ui.onboarding.OnboardingState
import com.truesummit.android.ui.transactions.formatCurrency
import java.math.BigDecimal
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetScreen(
    onPaycheckPlan: () -> Unit,
    onBudgetDraft: () -> Unit = {},
    onDebtPayoff: () -> Unit = {},
    onSettleUp: () -> Unit = {},
    onTaxPack: () -> Unit = {},
    onAddTransaction: () -> Unit = {},
    onGoToNetWorth: () -> Unit = {},
    onConnectBank: () -> Unit = {},
    onTakeTour: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    isAuthenticated: Boolean = false,
    viewModel: BudgetViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showMenu by remember { mutableStateOf(false) }
    var selectedTileId by remember { mutableStateOf<java.util.UUID?>(null) }
    var selectedTileName by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Budget") },
                actions = {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Actions")
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Plan a Paycheck") },
                            onClick = { onPaycheckPlan(); showMenu = false },
                            leadingIcon = { Icon(Icons.Default.Payments, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Draft from History") },
                            onClick = { onBudgetDraft(); showMenu = false },
                            leadingIcon = { Icon(Icons.Default.AutoFixHigh, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Debt Payoff") },
                            onClick = { onDebtPayoff(); showMenu = false },
                            leadingIcon = { Icon(Icons.Default.AccountBalance, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Settle Up") },
                            onClick = { onSettleUp(); showMenu = false },
                            leadingIcon = { Icon(Icons.Default.People, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Tax Pack") },
                            onClick = { onTaxPack(); showMenu = false },
                            leadingIcon = { Icon(Icons.Default.Receipt, contentDescription = null) }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Auto-Assign to Goals") },
                            onClick = {
                                viewModel.autoAssign()
                                showMenu = false
                            },
                            leadingIcon = { Icon(Icons.Default.AutoAwesome, contentDescription = null) }
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            MonthNavigator(
                selectedDate = uiState.selectedDate,
                onPrevious = { viewModel.changeMonth(-1) },
                onNext = { viewModel.changeMonth(1) }
            )

            androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.fillMaxSize()) {
                item {
                    GettingStartedSection(
                        transactionCount = uiState.transactionCount,
                        hasPlaidConnection = uiState.hasPlaidConnection,
                        isAuthenticated = isAuthenticated,
                        onTakeTour = onTakeTour,
                        onGoToNetWorth = onGoToNetWorth,
                        onAddTransaction = onAddTransaction,
                        onConnectBank = onConnectBank,
                        onOpenSettings = onOpenSettings
                    )
                }
                item {
                    TrueSummitBudgetHero(
                        displayName = OnboardingState.userDisplayName,
                        assigned = uiState.allocations.values.fold(BigDecimal.ZERO, BigDecimal::add),
                        spent = uiState.activity.values.fold(BigDecimal.ZERO) { acc, v -> acc + v.abs() },
                        availableToBudget = uiState.availableToBudget,
                        savingsRate = uiState.savingsRate,
                        netWorthTrend = uiState.netWorthTrend,
                        tiles = uiState.categoryTiles,
                        insight = uiState.insight,
                        onInsightClick = { /* navigate to Insights tab handled by parent */ },
                        onTileClick = { id ->
                            val tile = uiState.categoryTiles.find { it.id == id }
                            if (tile != null) {
                                selectedTileName = tile.name
                                selectedTileId = id
                            }
                        }
                    )
                }
                uiState.groups.forEach { group ->
                    item {
                        GroupHeader(group.name)
                    }
                    items(uiState.categories.filter { it.groupId == group.id }) { category ->
                        val cal = java.util.Calendar.getInstance()
                        cal.time = uiState.selectedDate
                        CategoryRow(
                            category = category,
                            assigned = uiState.allocations[category.id] ?: BigDecimal.ZERO,
                            activity = uiState.activity[category.id] ?: BigDecimal.ZERO,
                            projectedSpend = uiState.projectedSpend[category.id],
                            onAssignedChange = { viewModel.setAssigned(category.id, it) }
                        )
                    }
                }
            }
        }
    }

    selectedTileId?.let { id ->
        val cal = java.util.Calendar.getInstance()
        cal.time = uiState.selectedDate
        CategoryDetailSheet(
            categoryId = id,
            categoryName = selectedTileName,
            year = cal.get(java.util.Calendar.YEAR),
            month = cal.get(java.util.Calendar.MONTH) + 1,
            onDismiss = { selectedTileId = null }
        )
    }
}

@Composable
fun MonthNavigator(
    selectedDate: Date,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    val sdf = remember { java.text.SimpleDateFormat("MMMM yyyy", Locale.getDefault()) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Previous")
        }
        Text(sdf.format(selectedDate), style = MaterialTheme.typography.titleMedium)
        IconButton(onClick = onNext) {
            Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Next")
        }
    }
}

@Composable
fun AvailableToBudget(amount: BigDecimal) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Available to Budget", style = MaterialTheme.typography.labelMedium)
            Text(formatCurrency(amount.toDouble()), style = MaterialTheme.typography.headlineMedium)
        }
    }
}

@Composable
fun GroupHeader(name: String) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
        Text(
            text = name,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.titleSmall
        )
    }
}

@Composable
fun CategoryRow(
    category: CategoryEntity,
    assigned: BigDecimal,
    activity: BigDecimal,
    projectedSpend: BigDecimal? = null,
    onAssignedChange: (BigDecimal) -> Unit
) {
    val available = assigned.add(activity)
    val rolloverEnabled = com.truesummit.android.service.BudgetRollover.isEnabled
    var showContextMenu by remember { mutableStateOf(false) }
    val isExcluded = com.truesummit.android.service.BudgetRollover.isExcluded(category.id)

    Box {
        ListItem(
            headlineContent = { Text(category.name) },
            supportingContent = {
                androidx.compose.foundation.layout.Column {
                    Text("Activity: ${formatCurrency(activity.toDouble())} · Available: ${formatCurrency(available.toDouble())}")
                    if (projectedSpend != null && assigned > BigDecimal.ZERO) {
                        SpendingPacePill(
                            projected = projectedSpend,
                            budget = assigned,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = assigned.toString(),
                        onValueChange = {
                            it.toBigDecimalOrNull()?.let { amt -> onAssignedChange(amt) }
                        },
                        modifier = Modifier.width(100.dp),
                        textStyle = MaterialTheme.typography.bodyMedium
                    )
                    if (rolloverEnabled) {
                        IconButton(onClick = { showContextMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Category options")
                        }
                    }
                }
            }
        )
        if (rolloverEnabled) {
            DropdownMenu(expanded = showContextMenu, onDismissRequest = { showContextMenu = false }) {
                DropdownMenuItem(
                    text = { Text(if (isExcluded) "Enable Rollover" else "Exclude from Rollover") },
                    onClick = {
                        com.truesummit.android.service.BudgetRollover.setExcluded(category.id, !isExcluded)
                        showContextMenu = false
                    },
                    leadingIcon = { Icon(Icons.Default.Repeat, contentDescription = null) }
                )
            }
        }
    }
}
