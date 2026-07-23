package com.truesummit.android.ui.subscriptions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.truesummit.android.billing.PremiumFeature
import com.truesummit.android.billing.PremiumManager
import com.truesummit.android.billing.SubscriptionTier
import com.truesummit.android.service.DetectedSubscription
import com.truesummit.android.ui.auth.LockedFeatureCard
import com.truesummit.android.ui.transactions.formatCurrency
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionsScreen(
    onBack: () -> Unit,
    onUpgrade: () -> Unit,
    viewModel: SubscriptionsViewModel = viewModel()
) {
    val detected by viewModel.detected.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val addedNotice by viewModel.addedNotice.collectAsStateWithLifecycle()
    val currentTier by PremiumManager.currentTier.collectAsStateWithLifecycle()

    var showIgnoredDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.rescan()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Subscriptions") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (currentTier == SubscriptionTier.PREMIUM) {
                        var showMenu by remember { mutableStateOf(false) }
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Re-scan") },
                                onClick = {
                                    viewModel.rescan()
                                    showMenu = false
                                },
                                leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Show Ignored…") },
                                onClick = {
                                    showIgnoredDialog = true
                                    showMenu = false
                                },
                                leadingIcon = { Icon(Icons.Default.VisibilityOff, contentDescription = null) }
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (currentTier != SubscriptionTier.PREMIUM) {
            Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                LockedFeatureCard(
                    feature = PremiumFeature.SUBSCRIPTION_TRACKER,
                    onUpgrade = onUpgrade
                )
            }
        } else {
            Box(modifier = Modifier.padding(padding).fillMaxSize()) {
                if (isScanning) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                } else if (detected.isEmpty()) {
                    EmptyState(onRescan = { viewModel.rescan() })
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        if (addedNotice != null) {
                            item {
                                Snackbar(
                                    modifier = Modifier.padding(16.dp),
                                    action = {
                                        TextButton(onClick = { viewModel.clearNotice() }) {
                                            Text("Dismiss")
                                        }
                                    }
                                ) {
                                    Text(addedNotice!!)
                                }
                            }
                        }

                        items(detected, key = { it.id }) { sub ->
                            SubscriptionRow(
                                sub = sub,
                                onSchedule = { viewModel.schedule(sub) },
                                onIgnore = { viewModel.ignore(sub) }
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }

    if (showIgnoredDialog) {
        IgnoredMerchantsDialog(
            onDismiss = { showIgnoredDialog = false },
            viewModel = viewModel
        )
    }
}

@Composable
fun SubscriptionRow(
    sub: DetectedSubscription,
    onSchedule: () -> Unit,
    onIgnore: () -> Unit
) {
    val df = SimpleDateFormat("MMM d", Locale.getDefault())
    
    ListItem(
        headlineContent = { Text(sub.merchant) },
        supportingContent = {
            Column {
                Text("${sub.cadence.displayName} · ${sub.occurrences.size} charges")
                Text("Next: ${df.format(sub.predictedNextDate)}", style = MaterialTheme.typography.bodySmall)
            }
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                Text(formatCurrency(sub.typicalAmount.toDouble()), style = MaterialTheme.typography.titleMedium)
                Row {
                    IconButton(onClick = onSchedule) {
                        Icon(Icons.Default.Add, contentDescription = "Schedule", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = onIgnore) {
                        Icon(Icons.Default.VisibilityOff, contentDescription = "Ignore", tint = MaterialTheme.colorScheme.secondary)
                    }
                }
            }
        }
    )
}

@Composable
fun EmptyState(onRescan: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Repeat, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f))
        Spacer(modifier = Modifier.height(16.dp))
        Text("No Subscriptions Detected", style = MaterialTheme.typography.titleMedium)
        Text(
            "Summit surfaces recurring charges once you have a few months of data.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onRescan) {
            Text("Scan Again")
        }
    }
}

@Composable
fun IgnoredMerchantsDialog(
    onDismiss: () -> Unit,
    viewModel: SubscriptionsViewModel
) {
    val ignored = viewModel.getIgnoredMerchants()
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ignored Merchants") },
        text = {
            if (ignored.isEmpty()) {
                Text("No ignored merchants.")
            } else {
                LazyColumn {
                    items(ignored) { merchant ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(merchant)
                            TextButton(onClick = { viewModel.restore(merchant) }) {
                                Text("Restore")
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}
