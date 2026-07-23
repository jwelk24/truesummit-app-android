package com.truesummit.android.ui.inbox

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.room.Room
import com.truesummit.android.data.AppDatabase
import com.truesummit.android.data.entity.CategoryEntity
import com.truesummit.android.data.entity.TransactionEntity
import com.truesummit.android.ui.transactions.formatCurrency
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

object ReviewQueue {
    fun needsReview(tx: TransactionEntity): Boolean =
        tx.categoryId == null &&
        tx.pfcPrimary != "TRANSFER_IN" && tx.pfcPrimary != "TRANSFER_OUT"

    fun pending(transactions: List<TransactionEntity>): List<TransactionEntity> =
        transactions.filter { needsReview(it) }.sortedByDescending { it.date }
}

class ReviewInboxViewModel(application: Application) : AndroidViewModel(application) {
    private val db = Room.databaseBuilder(
        application, AppDatabase::class.java, "truesummit-db"
    ).addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4).build()

    val categories: StateFlow<List<CategoryEntity>> = db.categoryDao().getCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pending: StateFlow<List<TransactionEntity>> = db.transactionDao().getAll()
        .map { ReviewQueue.pending(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun assignCategory(tx: TransactionEntity, category: CategoryEntity) {
        viewModelScope.launch {
            db.transactionDao().insert(tx.copy(categoryId = category.id))
        }
    }

    fun markTransfer(tx: TransactionEntity) {
        viewModelScope.launch {
            val pfcPrimary = if (tx.amount >= java.math.BigDecimal.ZERO) "TRANSFER_IN" else "TRANSFER_OUT"
            db.transactionDao().insert(tx.copy(pfcPrimary = pfcPrimary))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewInboxScreen(
    onBack: () -> Unit,
    onEditTransaction: (UUID) -> Unit,
    viewModel: ReviewInboxViewModel = viewModel()
) {
    val pending by viewModel.pending.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Review") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (pending.isEmpty()) {
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text("Inbox Zero", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Every transaction has a category.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
            ) {
                item {
                    Text(
                        "${pending.size} to review",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 4.dp)
                    )
                }
                items(pending, key = { it.id }) { tx ->
                    ReviewRow(
                        tx = tx,
                        categories = categories,
                        onAssign = { viewModel.assignCategory(tx, it) },
                        onEdit = { onEditTransaction(tx.id) },
                        onMarkTransfer = { viewModel.markTransfer(tx) }
                    )
                    HorizontalDivider()
                }
                item {
                    Text(
                        "Pick a category to clear an item, or tap ⇄ to mark as a transfer between your own accounts.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ReviewRow(
    tx: TransactionEntity,
    categories: List<CategoryEntity>,
    onAssign: (CategoryEntity) -> Unit,
    onEdit: () -> Unit,
    onMarkTransfer: () -> Unit
) {
    val df = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }
    val isInflow = tx.amount >= java.math.BigDecimal.ZERO
    var showCategoryMenu by remember { mutableStateOf(false) }

    ListItem(
        headlineContent = { Text(tx.merchant) },
        supportingContent = {
            Text(df.format(tx.date) + "  " + formatCurrency(tx.amount.abs().toDouble()))
        },
        trailingContent = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box {
                    IconButton(onClick = { showCategoryMenu = true }) {
                        Icon(
                            Icons.Default.FolderOpen,
                            contentDescription = "Categorize",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    DropdownMenu(
                        expanded = showCategoryMenu,
                        onDismissRequest = { showCategoryMenu = false }
                    ) {
                        categories.sortedBy { it.name }.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat.name) },
                                onClick = { onAssign(cat); showCategoryMenu = false }
                            )
                        }
                    }
                }
                TextButton(onClick = onMarkTransfer) { Text("⇄") }
            }
        },
        modifier = Modifier.clickable { onEdit() }
    )
}
