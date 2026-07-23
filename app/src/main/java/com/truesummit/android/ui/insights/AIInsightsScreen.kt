package com.truesummit.android.ui.insights

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.truesummit.android.billing.PremiumManager
import com.truesummit.android.service.ChallengeStore
import com.truesummit.android.ui.transactions.EmptyStateView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIInsightsScreen(
    onUpgrade: () -> Unit,
    onWeeklyReview: () -> Unit,
    onWrapped: () -> Unit,
    onChallenges: () -> Unit,
    onCoach: () -> Unit = {},
    onSafeToSpend: () -> Unit = {},
    onFinancialHealth: () -> Unit = {},
    viewModel: AIInsightsViewModel = viewModel()
) {
    val digest by viewModel.digest.collectAsState()
    val isGeneratingDigest by viewModel.isGeneratingDigest.collectAsState()
    val isCategorizing by viewModel.isCategorizing.collectAsState()
    val categorizeResult by viewModel.categorizeResult.collectAsState()
    val queryResult by viewModel.queryResult.collectAsState()
    val isQuerying by viewModel.isQuerying.collectAsState()
    
    val currentTier by PremiumManager.currentTier.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Insights") }) }
    ) { padding ->
        if (currentTier != com.truesummit.android.billing.SubscriptionTier.PREMIUM) {
            LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    AskYourMoneyCard(
                        result = queryResult,
                        isLoading = isQuerying,
                        onAsk = { viewModel.askQuery(it) }
                    )
                }
                item {
                    CheckInsSection(
                        onWeeklyReview = onWeeklyReview,
                        onWrapped = onWrapped,
                        onChallenges = onChallenges,
                        onCoach = onCoach,
                        onSafeToSpend = onSafeToSpend,
                        onFinancialHealth = onFinancialHealth
                    )
                }
                item {
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Text("AI Insights", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            }
                            Text("Unlock Ask Your Money, weekly digests, and smart categorization with a Premium subscription.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Button(onClick = onUpgrade, modifier = Modifier.fillMaxWidth()) { Text("View Plans") }
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    AskYourMoneyCard(
                        result = queryResult,
                        isLoading = isQuerying,
                        onAsk = { viewModel.askQuery(it) }
                    )
                }
                item {
                    CheckInsSection(
                        onWeeklyReview = onWeeklyReview,
                        onWrapped = onWrapped,
                        onChallenges = onChallenges,
                        onCoach = onCoach,
                        onSafeToSpend = onSafeToSpend,
                        onFinancialHealth = onFinancialHealth
                    )
                }

                item {
                    WeeklyDigestCard(
                        digest = digest,
                        isLoading = isGeneratingDigest,
                        onGenerate = { viewModel.generateDigest() }
                    )
                }

                item {
                    SmartCategorizeCard(
                        isLoading = isCategorizing,
                        result = categorizeResult,
                        onRun = { viewModel.runSmartCategorize() }
                    )
                }
            }
        }
    }
}

@Composable
fun AskYourMoneyCard(
    result: String?,
    isLoading: Boolean,
    onAsk: (String) -> Unit
) {
    var text by remember { mutableStateOf("") }

    val suggestions = listOf(
        "How much did I spend on groceries this month?",
        "What was my income last month?",
        "How many transactions this month?",
        "Average spending this year?"
    )

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.QuestionAnswer, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Text("Ask Your Money", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Text(
                "Ask a plain-English question about your spending or income — answered on your device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("e.g. How much did I spend on food?") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                trailingIcon = {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    } else {
                        IconButton(onClick = { if (text.isNotBlank()) onAsk(text) }) {
                            Icon(Icons.Default.Send, contentDescription = "Ask")
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (text.isNotBlank()) onAsk(text) })
            )
            if (result != null) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        result,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            if (result == null && !isLoading) {
                Text(
                    "Try:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                suggestions.forEach { s ->
                    Text(
                        "\"$s\"",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { text = s }
                    )
                }
            }
        }
    }
}

@Composable
fun WeeklyDigestCard(
    digest: com.truesummit.android.service.WeeklyDigest?,
    isLoading: Boolean,
    onGenerate: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Weekly Digest", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            
            if (isLoading) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Summarizing your week...", style = MaterialTheme.typography.bodyMedium)
                }
            } else if (digest != null) {
                Text(digest.headline, style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(8.dp))
                digest.bullets.forEach { bullet ->
                    Text("• $bullet", style = MaterialTheme.typography.bodyMedium)
                }
                if (digest.suggestion.isNotBlank()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Row {
                        Icon(Icons.Default.Lightbulb, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(digest.suggestion, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            } else {
                Text(
                    "Get a plain-English summary of your spending over the last 7 days.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = onGenerate,
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (digest == null) "Generate Weekly Digest" else "Regenerate")
            }
        }
    }
}

@Composable
fun SmartCategorizeCard(
    isLoading: Boolean,
    result: String?,
    onRun: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Smart Categorize", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            if (result != null) {
                Text(result, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
            } else {
                Text(
                    "Let AI assign a category to every transaction that's currently uncategorized.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = onRun,
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Icon(Icons.Default.AutoFixHigh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Categorize Uncategorized")
                }
            }
        }
    }
}

@Composable
fun CheckInsSection(
    onWeeklyReview: () -> Unit,
    onWrapped: () -> Unit,
    onChallenges: () -> Unit,
    onCoach: () -> Unit = {},
    onSafeToSpend: () -> Unit = {},
    onFinancialHealth: () -> Unit = {}
) {
    val wins = ChallengeStore.completedIds().size

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Icon(Icons.Default.CalendarToday, contentDescription = null,
                    modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                Text("Check-Ins",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            CheckInRow(
                icon = Icons.Default.Checklist,
                label = "Weekly Review",
                badge = null,
                onClick = onWeeklyReview
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
            CheckInRow(
                icon = Icons.Default.AutoAwesome,
                label = "Summit Wrapped",
                badge = null,
                onClick = onWrapped
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
            CheckInRow(
                icon = Icons.Default.EmojiEvents,
                label = "Challenges",
                badge = if (wins > 0) "$wins" else null,
                onClick = onChallenges
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
            CheckInRow(
                icon = Icons.Default.Psychology,
                label = "Financial Coach",
                badge = null,
                onClick = onCoach
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
            CheckInRow(
                icon = Icons.Default.AttachMoney,
                label = "Safe to Spend",
                badge = null,
                onClick = onSafeToSpend
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
            CheckInRow(
                icon = Icons.Default.Favorite,
                label = "Financial Health",
                badge = null,
                onClick = onFinancialHealth
            )

            Text(
                "A 3-minute weekly tidy-up, your year in review, and spending challenges.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun CheckInRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    badge: String?,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null,
            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        if (badge != null) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.tertiaryContainer
            ) {
                Text(
                    badge,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
            Spacer(Modifier.width(4.dp))
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null,
            modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
