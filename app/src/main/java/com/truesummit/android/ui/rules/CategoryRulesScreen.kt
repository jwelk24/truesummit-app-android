package com.truesummit.android.ui.rules

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.truesummit.android.billing.PremiumManager
import com.truesummit.android.billing.SubscriptionTier
import com.truesummit.android.data.entity.CategoryRuleEntity
import com.truesummit.android.ui.auth.LockedFeatureCard
import com.truesummit.android.billing.PremiumFeature
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryRulesScreen(
    onBack: () -> Unit,
    onAddRule: () -> Unit,
    onEditRule: (UUID) -> Unit,
    onUpgrade: () -> Unit,
    viewModel: CategoryRulesViewModel = viewModel()
) {
    val rules by viewModel.rules.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val backfillMessage by viewModel.backfillMessage.collectAsStateWithLifecycle()
    val currentTier by PremiumManager.currentTier.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Auto-Categorization") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (currentTier == SubscriptionTier.PREMIUM) {
                        IconButton(onClick = onAddRule) {
                            Icon(Icons.Default.Add, contentDescription = "Add Rule")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (currentTier != SubscriptionTier.PREMIUM) {
            Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                LockedFeatureCard(
                    feature = PremiumFeature.AUTO_RULES,
                    onUpgrade = onUpgrade
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
            ) {
                if (backfillMessage != null) {
                    item {
                        Snackbar(
                            modifier = Modifier.padding(16.dp),
                            action = {
                                TextButton(onClick = { viewModel.clearBackfillMessage() }) {
                                    Text("Dismiss")
                                }
                            }
                        ) {
                            Text(backfillMessage!!)
                        }
                    }
                }

                if (rules.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("No rules yet.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.secondary)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Create rules to automatically categorize your transactions.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(onClick = onAddRule) {
                                Text("Add Your First Rule")
                            }
                        }
                    }
                } else {
                    item {
                        Text(
                            text = "Rules",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(16.dp)
                        )
                    }

                    items(rules, key = { it.id }) { rule ->
                        val categoryName = categories.find { it.id == rule.categoryId }?.name ?: "(no category)"
                        RuleRow(
                            rule = rule,
                            categoryName = categoryName,
                            onClick = { onEditRule(rule.id) }
                        )
                        HorizontalDivider()
                    }

                    item {
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = { viewModel.runBackfill() },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                        ) {
                            Icon(Icons.Default.AutoFixHigh, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Apply to Uncategorized Now")
                        }
                        Text(
                            text = "Rules run automatically on new transactions. This re-runs them across existing uncategorized history.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(16.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RuleRow(
    rule: CategoryRuleEntity,
    categoryName: String,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = {
            Text("${rule.matchField} ${rule.matchKind} \"${rule.pattern}\"")
        },
        supportingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("→ $categoryName")
                if (rule.timesApplied > 0) {
                    Text(" · ${rule.timesApplied} hits", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                }
            }
        },
        trailingContent = {
            Text("p${rule.priority}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
        },
        leadingContent = {
            Icon(
                if (rule.enabled) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (rule.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
            )
        },
        modifier = Modifier.clickable { onClick() }
    )
}
