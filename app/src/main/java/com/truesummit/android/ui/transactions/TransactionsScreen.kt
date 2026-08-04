package com.truesummit.android.ui.transactions

import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.truesummit.android.data.entity.TransactionEntity
import com.truesummit.android.service.MerchantCleaner
import com.truesummit.android.service.MerchantLogoService
import com.truesummit.android.ui.budget.CategoryBarColor
import com.truesummit.android.ui.transactions.editor.flags
import com.truesummit.android.ui.transactions.viewmodel.MonthMetrics
import com.truesummit.android.ui.transactions.viewmodel.TransactionsViewModel
import androidx.compose.ui.platform.LocalContext
import java.math.BigDecimal
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

import com.truesummit.android.billing.PremiumManager
import com.truesummit.android.billing.SubscriptionTier

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TransactionsScreen(
    onAddTransaction: () -> Unit,
    onEditTransaction: (UUID) -> Unit,
    onScanReceipt: () -> Unit,
    onUpgrade: () -> Unit,
    onRefundTracker: () -> Unit,
    onReviewInbox: () -> Unit = {},
    viewModel: TransactionsViewModel = viewModel()
) {
    val transactions by viewModel.transactions.collectAsState()
    val categoriesById by viewModel.categoriesById.collectAsState()
    val monthMetrics by viewModel.monthMetrics.collectAsState()
    val reviewCount = remember(transactions) {
        com.truesummit.android.ui.inbox.ReviewQueue.pending(transactions).size
    }
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    
    val pullToRefreshState = rememberPullToRefreshState()
    if (pullToRefreshState.isRefreshing) {
        LaunchedEffect(true) {
            viewModel.refresh()
            pullToRefreshState.endRefresh()
        }
    }

    var showAddMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Transactions") },
                actions = {
                    IconButton(onClick = { showAddMenu = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Actions")
                    }
                    DropdownMenu(
                        expanded = showAddMenu,
                        onDismissRequest = { showAddMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("New Transaction") },
                            onClick = { onAddTransaction(); showAddMenu = false },
                            leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text(if (PremiumManager.canScanReceipts()) "Scan Receipt…" else "Scan Receipt (Premium)…") },
                            onClick = {
                                showAddMenu = false
                                if (PremiumManager.canScanReceipts()) onScanReceipt() else onUpgrade()
                            },
                            leadingIcon = { Icon(Icons.Default.DocumentScanner, contentDescription = null) }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Refund Tracker") },
                            onClick = { onRefundTracker(); showAddMenu = false },
                            leadingIcon = { Icon(Icons.Default.AssignmentReturn, contentDescription = null) }
                        )
                        if (reviewCount > 0) {
                            DropdownMenuItem(
                                text = { Text("Review Inbox ($reviewCount)") },
                                onClick = { onReviewInbox(); showAddMenu = false },
                                leadingIcon = { Icon(Icons.Default.Inbox, contentDescription = null) }
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize().nestedScroll(pullToRefreshState.nestedScrollConnection)) {
            if (transactions.isEmpty() && !isRefreshing) {
                EmptyStateView(
                    icon = Icons.Default.CreditCard,
                    message = "No transactions yet.",
                    actionLabel = "Add Manually",
                    onAction = onAddTransaction
                )
            } else {
                val rowContext = LocalContext.current
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    item {
                        TransactionsHeroCard(metrics = monthMetrics)
                    }
                    if (reviewCount > 0) {
                        item {
                            ReviewInboxBanner(count = reviewCount, onClick = onReviewInbox)
                        }
                    }
                    items(transactions, key = { it.id }) { transaction ->
                        val category = transaction.categoryId?.let { categoriesById[it] }
                        val categoryColor = category?.let { CategoryBarColor.effectiveColor(rowContext, it.id, it.name) }
                            ?: MaterialTheme.colorScheme.outline
                        SwipeToDeleteRow(
                            onDelete = { viewModel.deleteTransaction(transaction) },
                            modifier = Modifier.animateItemPlacement()
                        ) {
                            TransactionRow(
                                transaction = transaction,
                                categoryName = category?.name,
                                categoryColor = categoryColor,
                                modifier = Modifier.clickable { onEditTransaction(transaction.id) }
                            )
                        }
                        HorizontalDivider()
                    }
                }
            }
            
            PullToRefreshContainer(
                state = pullToRefreshState,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeToDeleteRow(
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = {
            if (it == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                true
            } else false
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        backgroundContent = {
            val color = when (dismissState.dismissDirection) {
                SwipeToDismissBoxValue.EndToStart -> Color.Red
                else -> Color.Transparent
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                if (dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.White)
                }
            }
        },
        enableDismissFromStartToEnd = false,
        content = {
            content()
        }
    )
}

@Composable
fun EmptyStateView(
    icon: ImageVector,
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f))
        Spacer(modifier = Modifier.height(16.dp))
        Text(message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.secondary)
        if (actionLabel != null && onAction != null) {
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onAction) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
fun TransactionRow(
    transaction: TransactionEntity,
    categoryName: String?,
    categoryColor: Color,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val displayMerchant = if (MerchantCleaner.isEnabled(context))
        MerchantCleaner.clean(transaction.merchant) else transaction.merchant
    val logosEnabled = MerchantLogoService.isEnabled(context)
    val ringColor = transaction.flagColor?.let { name -> flags.firstOrNull { it.first == name }?.second }
    val tags = transaction.tagList()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (logosEnabled) {
            MerchantLogoView(merchant = transaction.merchant, fallbackColor = categoryColor, ringColor = ringColor)
        } else {
            CategoryDot(color = categoryColor, ringColor = ringColor, size = 12.dp)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(displayMerchant, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "${SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(transaction.date)} · ${categoryName ?: "Uncategorized"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (tags.isNotEmpty() || transaction.awaitingRefund || transaction.refundsTransactionId != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    when {
                        transaction.awaitingRefund -> TagChip("Refund due", Color(0xFFF59E0B))
                        transaction.refundsTransactionId != null -> TagChip("Refund", Color(0xFF10B981))
                    }
                    tags.take(3).forEach { tag -> TagChip("#$tag", MaterialTheme.colorScheme.primary) }
                    if (tags.size > 3) {
                        Text(
                            "+${tags.size - 3}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.CenterVertically)
                        )
                    }
                }
            }
        }
        val amountColor = if (transaction.amount < BigDecimal.ZERO) MaterialTheme.colorScheme.onSurface else Color(0xFF10B981)
        Text(
            text = formatCurrency(transaction.amount.toDouble()),
            color = amountColor,
            style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace)
        )
    }
}

@Composable
private fun TagChip(text: String, tint: Color) {
    Surface(
        shape = CircleShape,
        color = tint.copy(alpha = 0.12f)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
            color = tint,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
        )
    }
}

@Composable
fun TransactionsHeroCard(metrics: MonthMetrics) {
    val netIsPositive = metrics.net >= BigDecimal.ZERO
    val netColor = when {
        metrics.net == BigDecimal.ZERO -> MaterialTheme.colorScheme.onSurfaceVariant
        netIsPositive -> Color(0xFF10B981)
        else -> Color(0xFFEF4444)
    }
    val spendFraction = when {
        metrics.income > BigDecimal.ZERO -> (metrics.spent.toDouble() / metrics.income.toDouble()).coerceIn(0.0, 1.0)
        metrics.spent > BigDecimal.ZERO -> 1.0
        else -> 0.0
    }
    val meterColor = when {
        spendFraction >= 1.0 -> Color(0xFFEF4444)
        spendFraction > 0.85 -> Color(0xFFF59E0B)
        else -> MaterialTheme.colorScheme.primary
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.CreditCard,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    metrics.monthLabel.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.6.sp, fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.weight(1f))
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)) {
                    Text(
                        "${metrics.count} tx",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    if (netIsPositive) "Net This Month" else "Net Loss This Month",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    formatCurrency(metrics.net.abs().toDouble()),
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = netColor
                )
            }

            LinearProgressIndicator(
                progress = { spendFraction.toFloat() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape),
                color = meterColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Row(modifier = Modifier.fillMaxWidth()) {
                TransactionsMiniStat("Income", formatCurrency(metrics.income.toDouble()), Color(0xFF10B981), Modifier.weight(1f))
                Box(modifier = Modifier.width(1.dp).height(28.dp).background(MaterialTheme.colorScheme.outlineVariant))
                TransactionsMiniStat("Spent", formatCurrency(metrics.spent.toDouble()), Color(0xFFEF4444), Modifier.weight(1f).padding(start = 12.dp))
                Box(modifier = Modifier.width(1.dp).height(28.dp).background(MaterialTheme.colorScheme.outlineVariant))
                TransactionsMiniStat("Net", formatCurrency(metrics.net.abs().toDouble()), netColor, Modifier.weight(1f).padding(start = 12.dp))
            }
        }
    }
}

@Composable
private fun TransactionsMiniStat(label: String, value: String, tint: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 0.4.sp
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

fun formatCurrency(amount: Double): String {
    val format = NumberFormat.getCurrencyInstance(Locale.US)
    return format.format(amount)
}

@Composable
fun ReviewInboxBanner(count: Int, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Inbox, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(
                    "$count transaction${if (count == 1) "" else "s"} to review",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}
