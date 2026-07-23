package com.truesummit.android.ui.transactions.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.truesummit.android.data.entity.TransactionAttachmentEntity
import kotlinx.coroutines.flow.flowOf
import androidx.lifecycle.viewmodel.compose.viewModel
import com.truesummit.android.billing.SubscriptionTier
import com.truesummit.android.billing.PremiumManager
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.*

val flags = listOf(
    "red" to Color(0xFFEF4444),
    "orange" to Color(0xFFF59E0B),
    "yellow" to Color(0xFFFBBF24),
    "green" to Color(0xFF10B981),
    "blue" to Color(0xFF3B82F6),
    "purple" to Color(0xFF8B5CF6)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionEditorScreen(
    transactionId: UUID? = null,
    onDismiss: () -> Unit,
    onCreateRule: (String, UUID) -> Unit,
    viewModel: TransactionEditorViewModel = viewModel()
) {
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val editingTransaction by viewModel.editingTransaction.collectAsStateWithLifecycle()
    val splits by viewModel.splits.collectAsStateWithLifecycle()
    val pendingImages by viewModel.pendingImages.collectAsStateWithLifecycle()
    val removedAttachmentIds by viewModel.removedAttachmentIds.collectAsStateWithLifecycle()

    val existingAttachments by (transactionId?.let { viewModel.existingAttachments(it) }
        ?: flowOf(emptyList<TransactionAttachmentEntity>())).collectAsStateWithLifecycle(emptyList())

    val currentTier by PremiumManager.currentTier.collectAsStateWithLifecycle()

    var merchant by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var isInflow by remember { mutableStateOf(false) }
    var selectedAccountId by remember { mutableStateOf<UUID?>(null) }
    var selectedCategoryId by remember { mutableStateOf<UUID?>(null) }
    var memo by remember { mutableStateOf("") }
    var cleared by remember { mutableStateOf(false) }
    var flagColor by remember { mutableStateOf<String?>(null) }
    var transactionDate by remember { mutableStateOf(Date()) }

    var showDatePicker by remember { mutableStateOf(false) }

    LaunchedEffect(transactionId) {
        transactionId?.let { viewModel.loadTransaction(it) }
    }

    LaunchedEffect(editingTransaction) {
        editingTransaction?.let { tx ->
            merchant = tx.merchant
            amount = tx.amount.abs().toPlainString()
            isInflow = tx.amount >= BigDecimal.ZERO
            selectedAccountId = tx.accountId
            selectedCategoryId = tx.categoryId
            memo = tx.memo ?: ""
            cleared = tx.cleared
            flagColor = tx.flagColor
            transactionDate = tx.date
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (transactionId == null) "New Transaction" else "Edit Transaction") },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Button(
                        onClick = {
                            viewModel.saveTransaction(
                                merchant = merchant,
                                amount = amount,
                                isInflow = isInflow,
                                date = transactionDate,
                                accountId = selectedAccountId,
                                categoryId = selectedCategoryId,
                                memo = memo.ifBlank { null },
                                cleared = cleared,
                                flagColor = flagColor,
                                onSuccess = onDismiss
                            )
                        },
                        enabled = merchant.isNotBlank() && amount.isNotBlank() && selectedAccountId != null
                    ) {
                        Text("Save")
                    }
                }
            )
        }
    ) { padding ->
        if (showDatePicker) {
            val datePickerState = rememberDatePickerState(initialSelectedDateMillis = transactionDate.time)
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        datePickerState.selectedDateMillis?.let { transactionDate = Date(it) }
                        showDatePicker = false
                    }) { Text("OK") }
                },
                dismissButton = {
                    TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
                }
            ) {
                DatePicker(state = datePickerState)
            }
        }

        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp)
        ) {
            item {
                Row(modifier = Modifier.fillMaxWidth()) {
                    FilterChip(
                        selected = !isInflow,
                        onClick = { isInflow = false },
                        label = { Text("Outflow") },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    FilterChip(
                        selected = isInflow,
                        onClick = { isInflow = true },
                        label = { Text("Inflow") },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(transactionDate),
                    onValueChange = { },
                    label = { Text("Date") },
                    readOnly = true,
                    trailingIcon = {
                        IconButton(onClick = { showDatePicker = true }) {
                            Icon(Icons.Default.DateRange, contentDescription = "Select Date")
                        }
                    },
                    modifier = Modifier.fillMaxWidth().clickable { showDatePicker = true }
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = merchant,
                    onValueChange = { merchant = it },
                    label = { Text("Merchant") },
                    modifier = Modifier.fillMaxWidth()
                )

                AnimatedVisibility(
                    visible = currentTier == SubscriptionTier.PREMIUM && 
                              merchant.isNotBlank() && 
                              selectedCategoryId != null && 
                              splits.isEmpty()
                ) {
                    TextButton(
                        onClick = { onCreateRule(merchant, selectedCategoryId!!) },
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Create Auto-Categorization Rule")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text("Account", style = MaterialTheme.typography.labelLarge)
                accounts.forEach { account ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedAccountId = account.id }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedAccountId == account.id,
                            onClick = { selectedAccountId = account.id }
                        )
                        Text(account.name, modifier = Modifier.padding(start = 8.dp))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (splits.isEmpty()) {
                    Text("Category", style = MaterialTheme.typography.labelLarge)
                    categories.forEach { category ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedCategoryId = category.id }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedCategoryId == category.id,
                                onClick = { selectedCategoryId = category.id }
                            )
                            Text(category.name, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                    
                    Button(onClick = { viewModel.addSplit() }, modifier = Modifier.padding(top = 8.dp)) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Split Across Categories")
                    }
                } else {
                    Text("Splits", style = MaterialTheme.typography.labelLarge)
                }
            }

            if (splits.isNotEmpty()) {
                itemsIndexed(splits) { index, split ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Split #${index + 1}", style = MaterialTheme.typography.titleSmall)
                                IconButton(onClick = { viewModel.removeSplit(index) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Remove Split", tint = Color.Red)
                                }
                            }
                            
                            OutlinedTextField(
                                value = split.amount,
                                onValueChange = { viewModel.updateSplit(index, split.copy(amount = it)) },
                                label = { Text("Amount") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.fillMaxWidth()
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Text("Category", style = MaterialTheme.typography.bodySmall)
                            categories.forEach { category ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.updateSplit(index, split.copy(categoryId = category.id)) }
                                        .padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = split.categoryId == category.id,
                                        onClick = { viewModel.updateSplit(index, split.copy(categoryId = category.id)) }
                                    )
                                    Text(category.name, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 4.dp))
                                }
                            }
                        }
                    }
                }
                
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        Button(onClick = { viewModel.addSplit() }) {
                            Text("Add Split")
                        }
                        TextButton(onClick = { viewModel.clearSplits() }, colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)) {
                            Text("Remove All Splits")
                        }
                    }
                }
            }

            item {
                TransactionAttachmentsSection(
                    existing = existingAttachments,
                    pendingImages = pendingImages,
                    removedIds = removedAttachmentIds,
                    onAddPending = { viewModel.addPendingImage(it) },
                    onRemovePending = { viewModel.removePendingImage(it) },
                    onRemoveExisting = { viewModel.removeExistingAttachment(it) }
                )
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = memo,
                    onValueChange = { memo = it },
                    label = { Text("Memo (Optional)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Cleared")
                    Switch(checked = cleared, onCheckedChange = { cleared = it })
                }

                Text("Flag", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(if (flagColor == null) Color.Gray else Color.Transparent, shape = MaterialTheme.shapes.small)
                            .clickable { flagColor = null },
                        contentAlignment = Alignment.Center
                    ) {
                        if (flagColor == null) Icon(Icons.Default.Add, contentDescription = "None", tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                    flags.forEach { (name, color) ->
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(color, shape = MaterialTheme.shapes.small)
                                .clickable { flagColor = name }
                        ) {
                            if (flagColor == name) {
                                Icon(Icons.Default.Check, contentDescription = "Selected", tint = Color.White, modifier = Modifier.size(16.dp).align(Alignment.Center))
                            }
                        }
                    }
                }
            }
        }
    }
}
