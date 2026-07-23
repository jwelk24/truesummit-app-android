package com.truesummit.android.ui.networth

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.plaid.link.OpenPlaidLink
import com.plaid.link.configuration.LinkTokenConfiguration
import com.plaid.link.result.LinkSuccess
import com.truesummit.android.billing.PremiumManager
import com.truesummit.android.service.StoredPlaidItem
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaidConnectionsScreen(
    onBack: () -> Unit,
    onAddBank: () -> Unit,
    onUpgrade: () -> Unit,
    viewModel: PlaidConnectionsViewModel = viewModel()
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val syncingItemId by viewModel.syncingItemId.collectAsStateWithLifecycle()
    val currentTier by PremiumManager.currentTier.collectAsStateWithLifecycle()
    val pendingLinkToken by viewModel.pendingLinkToken.collectAsStateWithLifecycle()
    val isLinkLoading by viewModel.isLinkLoading.collectAsStateWithLifecycle()
    val linkError by viewModel.linkError.collectAsStateWithLifecycle()

    val plaidLauncher = rememberLauncherForActivityResult(OpenPlaidLink()) { result ->
        when (result) {
            is LinkSuccess -> viewModel.onLinkSuccess(
                publicToken = result.publicToken,
                institutionName = result.metadata.institution?.name
            )
            else -> { /* user exited without linking */ }
        }
    }

    LaunchedEffect(pendingLinkToken) {
        val token = pendingLinkToken ?: return@LaunchedEffect
        val config = LinkTokenConfiguration.Builder().token(token).build()
        viewModel.onLinkTokenConsumed()
        plaidLauncher.launch(config)
    }

    linkError?.let { error ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissLinkError() },
            title = { Text("Connection Error") },
            text = { Text(error) },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissLinkError() }) { Text("OK") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Plaid Connections") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            item {
                SectionHeader("Linked Items")
            }

            if (items.isEmpty()) {
                item {
                    Text(
                        "No banks linked yet.",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            } else {
                items(items) { item ->
                    PlaidItemRow(
                        item = item,
                        isSyncing = syncingItemId == item.itemId,
                        onSync = { viewModel.syncItem(item) },
                        onUnlink = { viewModel.unlinkItem(item.itemId) }
                    )
                    HorizontalDivider()
                }
            }
            
            item {
                Spacer(modifier = Modifier.height(24.dp))
                val maxItems = PremiumManager.getMaxPlaidItems()
                val isAtCap = items.size >= maxItems
                
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (isAtCap) {
                        Text(
                            text = "${currentTier.displayName} is limited to $maxItems bank links.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = onUpgrade, modifier = Modifier.fillMaxWidth()) {
                            Text("Upgrade to Increase Limit")
                        }
                    } else {
                        Button(
                            onClick = { viewModel.requestLink() },
                            enabled = !isLinkLoading,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isLinkLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Connecting…")
                            } else {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Link New Bank")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PlaidItemRow(
    item: StoredPlaidItem,
    isSyncing: Boolean,
    onSync: () -> Unit,
    onUnlink: () -> Unit
) {
    val df = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    val dateStr = df.format(Date(item.linkedAt))

    ListItem(
        headlineContent = { Text(item.institutionName ?: item.itemId) },
        supportingContent = { Text("Linked $dateStr") },
        trailingContent = {
            Row {
                if (isSyncing) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                } else {
                    IconButton(onClick = { onSync() }) {
                        Icon(Icons.Default.Sync, contentDescription = "Sync")
                    }
                }
                IconButton(onClick = onUnlink) {
                    Icon(Icons.Default.Delete, contentDescription = "Unlink")
                }
            }
        }
    )
}
