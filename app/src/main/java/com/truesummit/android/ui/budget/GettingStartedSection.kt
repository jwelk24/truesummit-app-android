package com.truesummit.android.ui.budget

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import com.truesummit.android.ui.onboarding.OnboardingState

@Composable
fun GettingStartedSection(
    transactionCount: Int,
    hasPlaidConnection: Boolean,
    isAuthenticated: Boolean,
    onTakeTour: () -> Unit,
    onGoToNetWorth: () -> Unit,
    onAddTransaction: () -> Unit,
    onConnectBank: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    var dismissed by remember { mutableStateOf(OnboardingState.isChecklistDismissed) }
    var accountsVisited by remember { mutableStateOf(OnboardingState.hasVisitedAccounts) }
    var tourDone by remember { mutableStateOf(OnboardingState.hasTakenTour) }

    val hasLoggedTransaction = transactionCount > 3
    val notificationsEnabled = remember { NotificationManagerCompat.from(context).areNotificationsEnabled() }

    val steps = listOf(tourDone, accountsVisited, hasLoggedTransaction, hasPlaidConnection, notificationsEnabled, isAuthenticated)
    val doneCount = steps.count { it }
    val allDone = doneCount == steps.size

    if (dismissed || allDone) return

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Getting Started", style = MaterialTheme.typography.titleSmall)
                Text(
                    "$doneCount of ${steps.size} done",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                LinearProgressIndicator(
                    progress = { doneCount.toFloat() / steps.size },
                    modifier = Modifier.width(80.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = {
                    OnboardingState.isChecklistDismissed = true
                    dismissed = true
                }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Dismiss checklist", modifier = Modifier.size(16.dp))
                }
            }
        }
        HorizontalDivider()
        ChecklistRow(Icons.Default.Map, "Take the tour", "A guided look at what lives on each tab.", tourDone) {
            onTakeTour()
        }
        ChecklistRow(Icons.Default.AccountBalance, "Set your real balances", "Replace the sample accounts on the Net Worth tab.", accountsVisited) {
            OnboardingState.hasVisitedAccounts = true
            accountsVisited = true
            onGoToNetWorth()
        }
        ChecklistRow(Icons.Default.Add, "Log a transaction", "Add a purchase by hand to see your budget react.", hasLoggedTransaction) {
            onAddTransaction()
        }
        ChecklistRow(Icons.Default.Link, "Connect a bank", "Transactions import automatically once linked.", hasPlaidConnection) {
            onConnectBank()
        }
        ChecklistRow(Icons.Default.NotificationsActive, "Turn on reminders", "Bill reminders and weekly check-ins.", notificationsEnabled) {
            onOpenSettings()
        }
        ChecklistRow(Icons.Default.Cloud, "Back up & sync", "Sign in to protect your data and share with a partner.", isAuthenticated) {
            onOpenSettings()
        }
        HorizontalDivider()
    }
}

@Composable
private fun ChecklistRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    done: Boolean,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = {
            Text(
                title,
                textDecoration = if (done) TextDecoration.LineThrough else null,
                color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
            )
        },
        supportingContent = { Text(subtitle, style = MaterialTheme.typography.bodySmall) },
        leadingContent = {
            Icon(
                if (done) Icons.Default.CheckCircle else icon,
                contentDescription = null,
                tint = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingContent = if (!done) ({
            Icon(Icons.Default.ChevronRight, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }) else null,
        modifier = if (!done) Modifier.clickable { onClick() } else Modifier
    )
}
