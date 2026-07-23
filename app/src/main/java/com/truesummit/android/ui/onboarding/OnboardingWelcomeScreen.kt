package com.truesummit.android.ui.onboarding

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingWelcomeScreen(
    onFinish: () -> Unit,
    onConnectBank: () -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()
    val isLastPage = pagerState.currentPage == 2
    var nameText by remember { mutableStateOf(OnboardingState.userDisplayName) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.End
        ) {
            if (!isLastPage) {
                TextButton(onClick = onFinish) {
                    Text("Skip", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f)
        ) { page ->
            when (page) {
                0 -> WelcomePage(
                    icon = Icons.Default.Terrain,
                    title = "Welcome to Summit",
                    subtitle = "Budget, net worth, and the road ahead — in one place.",
                    nameField = {
                        OutlinedTextField(
                            value = nameText,
                            onValueChange = { nameText = it; OnboardingState.userDisplayName = it },
                            label = { Text("What should we call you?") },
                            placeholder = { Text("Your name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    features = listOf(
                        Triple(Icons.Default.TableChart, "Give every dollar a job",
                            "Assign your money to categories so you always know what's safe to spend."),
                        Triple(Icons.Default.ShowChart, "Track your net worth",
                            "Accounts, investments, and debts in one picture that updates as you do."),
                        Triple(Icons.Default.CalendarMonth, "See what's coming",
                            "Upcoming bills, cash-flow forecasts, and goals on the Horizon tab.")
                    )
                )
                1 -> WelcomePage(
                    nameField = null,
                    icon = Icons.Default.TableChart,
                    title = "Your Starter Budget",
                    subtitle = "We set up common categories and sample accounts so you can look around.",
                    features = listOf(
                        Triple(Icons.Default.Layers, "The numbers are examples",
                            "The accounts, balances, and transactions you'll see are samples — swap in your real ones."),
                        Triple(Icons.Default.Edit, "Everything is editable",
                            "Rename categories, add your own, and delete anything you don't need."),
                        Triple(Icons.Default.Checklist, "A checklist guides you",
                            "The Getting Started checklist on the Budget tab walks you through making it yours.")
                    )
                )
                2 -> WelcomePage(
                    nameField = null,
                    icon = Icons.Default.AccountBalance,
                    title = "Bring In Your Money",
                    subtitle = "Connect accounts for automatic imports, or log things yourself.",
                    features = listOf(
                        Triple(Icons.Default.AccountBalance, "Connect your bank",
                            "Transactions and balances import automatically."),
                        Triple(Icons.Default.Add, "Or log it yourself",
                            "Quick-add transactions, scan receipts, or use widgets."),
                        Triple(Icons.Default.Lock, "Private by default",
                            "Your data stays on this device unless you sign in to back up and sync.")
                    )
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            repeat(3) { idx ->
                val selected = pagerState.currentPage == idx
                Surface(
                    modifier = Modifier
                        .padding(4.dp)
                        .size(if (selected) 8.dp else 6.dp),
                    shape = MaterialTheme.shapes.small,
                    color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                ) {}
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (isLastPage) {
                OutlinedButton(
                    onClick = { onConnectBank(); onFinish() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.AccountBalance, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Connect a Bank")
                }
            }
            Button(
                onClick = {
                    if (!isLastPage) scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    else onFinish()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isLastPage) "Start Budgeting" else "Continue")
            }
        }
    }
}

@Composable
private fun WelcomePage(
    icon: ImageVector,
    title: String,
    subtitle: String,
    features: List<Triple<ImageVector, String, String>>,
    nameField: (@Composable () -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center)
        if (nameField != null) {
            nameField()
        }
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            features.forEach { (featureIcon, featureTitle, featureDetail) ->
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Icon(featureIcon, contentDescription = null,
                        modifier = Modifier.size(24.dp).padding(top = 2.dp),
                        tint = MaterialTheme.colorScheme.primary)
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(featureTitle, style = MaterialTheme.typography.labelLarge)
                        Text(featureDetail, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
