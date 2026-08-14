package com.truesummit.android.ui.billing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.truesummit.android.billing.PremiumManager
import com.truesummit.android.billing.SubscriptionTier
import com.truesummit.android.billing.SubscriptionPeriod

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaywallScreen(onDismiss: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Upgrade TrueSummit") },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Choose Your Peak",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                "Unlock the full power of automated budgeting.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(top = 8.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            TierCard(
                tier = SubscriptionTier.PRO,
                features = listOf(
                    "Up to 5 Bank Links via Plaid",
                    "30-Day Cash-Flow Forecast",
                    "12-Month Historical Data",
                    "Real-time Cloud Sync"
                ),
                onSelect = { period ->
                    PremiumManager.setTier(SubscriptionTier.PRO)
                    PremiumManager.setPeriod(period)
                    onDismiss()
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            TierCard(
                tier = SubscriptionTier.PREMIUM,
                features = listOf(
                    "Up to 20 Bank Links",
                    "Full 365-Day Horizon",
                    "AI Smart Categorization",
                    "AI Weekly Summaries",
                    "AI Receipt Scanning",
                    "Family Household Sharing",
                    "Custom Category Rules",
                    "Smart Spending Alerts"
                ),
                isHighlighted = true,
                onSelect = { period ->
                    PremiumManager.setTier(SubscriptionTier.PREMIUM)
                    PremiumManager.setPeriod(period)
                    onDismiss()
                }
            )

            Spacer(modifier = Modifier.height(24.dp))
            
            TextButton(onClick = { /* Restore Purchases logic */ }) {
                Text("Restore Purchases")
            }
            Text(
                "Subscriptions auto-renew until cancelled. Manage in Play Store settings.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
        }
    }
}

@Composable
fun TierCard(
    tier: SubscriptionTier,
    features: List<String>,
    isHighlighted: Boolean = false,
    onSelect: (SubscriptionPeriod) -> Unit
) {
    val monthlyPrice = PremiumManager.getMonthlyPriceLabel(tier)
    val yearlyPrice = PremiumManager.getYearlyPriceLabel(tier)
    val savings = PremiumManager.getYearlySavingsPercent(tier)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isHighlighted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        ),
        border = if (isHighlighted) CardDefaults.outlinedCardBorder() else null
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            if (isHighlighted) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Star, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    Text(" RECOMMENDED", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }
            Text("TrueSummit ${tier.displayName}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            
            Spacer(modifier = Modifier.height(16.dp))
            
            features.forEach { feature ->
                Row(modifier = Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFF10B981))
                    Text(feature, modifier = Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodySmall)
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Monthly Button
            Button(
                onClick = { onSelect(SubscriptionPeriod.MONTHLY) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isHighlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                )
            ) {
                Text("Monthly: $monthlyPrice")
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Yearly Button with Savings Badge
            OutlinedButton(
                onClick = { onSelect(SubscriptionPeriod.YEARLY) },
                modifier = Modifier.fillMaxWidth(),
                border = ButtonDefaults.outlinedButtonBorder.copy(width = 1.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Yearly: $yearlyPrice")
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = Color(0xFF10B981),
                        shape = MaterialTheme.shapes.extraSmall
                    ) {
                        Text(
                            "SAVE $savings%",
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
