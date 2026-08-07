package com.truesummit.android.ui.insights

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.truesummit.android.billing.PremiumManager
import com.truesummit.android.billing.SubscriptionTier
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
    onBack: (() -> Unit)? = null,
    viewModel: AIInsightsViewModel = viewModel()
) {
    val digest by viewModel.digest.collectAsState()
    val isGeneratingDigest by viewModel.isGeneratingDigest.collectAsState()
    val isCategorizing by viewModel.isCategorizing.collectAsState()
    val categorizeResult by viewModel.categorizeResult.collectAsState()
    val queryResult by viewModel.queryResult.collectAsState()
    val isQuerying by viewModel.isQuerying.collectAsState()
    val anomalies by viewModel.anomalies.collectAsState()
    val isDetectingAnomalies by viewModel.isDetectingAnomalies.collectAsState()
    val savingsSuggestions by viewModel.savingsSuggestions.collectAsState()
    val isLoadingSuggestions by viewModel.isLoadingSuggestions.collectAsState()
    val chatHistory by viewModel.chatHistory.collectAsState()
    val isCoachTyping by viewModel.isCoachTyping.collectAsState()

    val currentTier by PremiumManager.currentTier.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Insights") },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (currentTier != SubscriptionTier.PREMIUM) {
            LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    InsightsHeroCard(digestHeadline = null, isPremium = false)
                }
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
                    InsightsHeroCard(digestHeadline = digest?.headline, isPremium = true)
                }
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

                item {
                    AnomalyCard(
                        anomalies = anomalies,
                        isLoading = isDetectingAnomalies,
                        onRun = { viewModel.detectAnomalies() }
                    )
                }

                item {
                    SavingsSuggestionsCard(
                        suggestions = savingsSuggestions,
                        isLoading = isLoadingSuggestions,
                        onLoad = { viewModel.loadSavingsSuggestions() }
                    )
                }

                item {
                    CoachChatCard(
                        chatHistory = chatHistory,
                        isTyping = isCoachTyping,
                        onSend = { viewModel.sendCoachMessage(it) }
                    )
                }
            }
        }
    }
}

@Composable
fun InsightsHeroCard(digestHeadline: String?, isPremium: Boolean) {
    val statusText = if (isPremium) "Active" else "Premium"
    val statusColor = if (isPremium) Color(0xFF10B981) else Color(0xFFF59E0B)
    val statusIcon = if (isPremium) Icons.Default.CheckCircle else Icons.Default.Lock

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "ON-DEVICE AI",
                    style = MaterialTheme.typography.labelSmall.copy(
                        letterSpacing = 0.6.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.weight(1f))
                Surface(shape = CircleShape, color = statusColor.copy(alpha = 0.15f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Icon(statusIcon, contentDescription = null, tint = statusColor, modifier = Modifier.size(11.dp))
                        Text(
                            statusText,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = statusColor
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    if (digestHeadline == null) "On-Device Insights" else "Latest Digest",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    digestHeadline ?: "Private summaries that never leave your device.",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(modifier = Modifier.fillMaxWidth()) {
                InsightsTrait(Icons.Default.Lock, "On-device", Modifier.weight(1f))
                InsightsTrait(Icons.Default.Shield, "Private", Modifier.weight(1f))
                InsightsTrait(Icons.Default.AllInclusive, "Free", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun InsightsTrait(icon: ImageVector, label: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(12.dp)
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Text("Weekly Digest", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.AutoFixHigh, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Text("Smart Categorize", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
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

// ── Anomaly detection card ────────────────────────────────────────────────────

@Composable
fun AnomalyCard(
    anomalies: List<com.truesummit.android.service.AnomalyResult>,
    isLoading: Boolean,
    onRun: () -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Warning, contentDescription = null,
                    tint = Color(0xFFF59E0B), modifier = Modifier.size(20.dp))
                Text("Anomaly Detection", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Text("Gemini scans your recent transactions for unusual charges or price changes.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            if (anomalies.isEmpty() && !isLoading) {
                Button(onClick = onRun, modifier = Modifier.fillMaxWidth()) {
                    Text("Scan for Anomalies")
                }
            } else if (isLoading) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp))
                    Text("Scanning transactions…", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                anomalies.forEach { anomaly ->
                    val severityColor = when (anomaly.severity) {
                        "high" -> Color(0xFFEF4444)
                        "medium" -> Color(0xFFF59E0B)
                        else -> Color(0xFF6B7280)
                    }
                    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Surface(shape = CircleShape, color = severityColor.copy(alpha = 0.15f)) {
                                Icon(Icons.Default.ErrorOutline, contentDescription = null,
                                    tint = severityColor, modifier = Modifier.padding(6.dp).size(14.dp))
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${anomaly.merchant} · \$" + "%.2f".format(anomaly.amount),
                                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Text(anomaly.reason, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                TextButton(onClick = onRun, modifier = Modifier.align(Alignment.End)) { Text("Rescan") }
            }
        }
    }
}

// ── Savings suggestions card ──────────────────────────────────────────────────

@Composable
fun SavingsSuggestionsCard(
    suggestions: List<com.truesummit.android.service.SavingsSuggestion>,
    isLoading: Boolean,
    onLoad: () -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Lightbulb, contentDescription = null,
                    tint = Color(0xFF4ECDC4), modifier = Modifier.size(20.dp))
                Text("Savings Suggestions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Text("Gemini finds realistic ways to save based on your actual spending habits.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            if (suggestions.isEmpty() && !isLoading) {
                Button(onClick = onLoad, modifier = Modifier.fillMaxWidth()) {
                    Text("Get Suggestions")
                }
            } else if (isLoading) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp))
                    Text("Analyzing your spending…", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                suggestions.forEach { s ->
                    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text(s.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
                                    modifier = Modifier.weight(1f))
                                Text("+\$" + "%.0f".format(s.estimatedMonthlySavings) + "/mo",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color(0xFF10B981), fontWeight = FontWeight.Bold)
                            }
                            Text(s.detail, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                TextButton(onClick = onLoad, modifier = Modifier.align(Alignment.End)) { Text("Refresh") }
            }
        }
    }
}

// ── Budget coach chat card ────────────────────────────────────────────────────

@Composable
fun CoachChatCard(
    chatHistory: List<com.truesummit.android.ui.insights.ChatMessage>,
    isTyping: Boolean,
    onSend: (String) -> Unit
) {
    var input by remember { mutableStateOf("") }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Psychology, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Text("Budget Coach", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Text("Ask Gemini anything about your finances — personalized advice based on your real data.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            // Chat bubbles
            if (chatHistory.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    chatHistory.takeLast(6).forEach { msg ->
                        val bubbleColor = if (msg.isUser)
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        else
                            MaterialTheme.colorScheme.surfaceVariant
                        val align = if (msg.isUser) Alignment.End else Alignment.Start
                        Box(modifier = Modifier.fillMaxWidth()) {
                            Surface(
                                shape = RoundedCornerShape(
                                    topStart = 12.dp, topEnd = 12.dp,
                                    bottomStart = if (msg.isUser) 12.dp else 4.dp,
                                    bottomEnd = if (msg.isUser) 4.dp else 12.dp
                                ),
                                color = bubbleColor,
                                modifier = Modifier.align(if (msg.isUser) Alignment.CenterEnd else Alignment.CenterStart)
                                    .widthIn(max = 280.dp)
                            ) {
                                Text(msg.text, style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp))
                            }
                        }
                    }
                    if (isTyping) {
                        Text("Coach is thinking…", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // Input
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = { Text("Ask your coach…") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        if (input.isNotBlank()) { onSend(input); input = "" }
                    })
                )
                IconButton(
                    onClick = { if (input.isNotBlank()) { onSend(input); input = "" } },
                    enabled = input.isNotBlank() && !isTyping
                ) {
                    if (isTyping) CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    else Icon(Icons.Default.Send, contentDescription = "Send")
                }
            }
        }
    }
}
