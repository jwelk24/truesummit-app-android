package com.truesummit.android.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.truesummit.android.ui.onboarding.OnboardingState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onSyncAccount: () -> Unit,
    onSettleUp: () -> Unit,
    onCategoryRules: () -> Unit,
    onSmartAlerts: () -> Unit,
    onSubscriptions: () -> Unit,
    onCustomizeAppearance: () -> Unit,
    onFeatureGuide: () -> Unit = {},
    onPrivacyData: () -> Unit = {}
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Settings") }) }
    ) { padding ->
        var displayName by remember { mutableStateOf(OnboardingState.userDisplayName) }
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            item { SettingsSectionHeader("Profile") }
            item {
                ListItem(
                    headlineContent = {
                        OutlinedTextField(
                            value = displayName,
                            onValueChange = { displayName = it; OnboardingState.userDisplayName = it },
                            label = { Text("Your Name") },
                            placeholder = { Text("How should Summit greet you?") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    leadingContent = { Icon(Icons.Default.Person, contentDescription = null) }
                )
            }
            item { SettingsSectionHeader("Account") }
            item {
                SettingsRow("Sync & Account", Icons.Default.Cloud, onSyncAccount)
            }
            item {
                SettingsRow("Shared Expenses", Icons.Default.People, onSettleUp)
            }
            item { SettingsSectionHeader("Automation") }
            item {
                SettingsRow("Transaction Rules", Icons.Default.AutoFixHigh, onCategoryRules)
            }
            item {
                SettingsRow("Smart Alerts", Icons.Default.NotificationsActive, onSmartAlerts)
            }
            item {
                SettingsRow("Subscriptions", Icons.Default.Repeat, onSubscriptions)
            }
            item { SettingsSectionHeader("Appearance") }
            item {
                SettingsRow("Customize Tabs & Colors", Icons.Default.Palette, onCustomizeAppearance)
            }
            item { SettingsSectionHeader("Privacy") }
            item {
                SettingsRow("Privacy & Data", Icons.Default.Lock, onPrivacyData)
            }
            item { SettingsSectionHeader("Help") }
            item {
                SettingsRow("Feature Guide", Icons.Default.Map, onFeatureGuide)
            }
        }
    }
}

@Composable
internal fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 20.dp, end = 16.dp, bottom = 4.dp)
    )
}

@Composable
private fun SettingsRow(title: String, icon: ImageVector, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) },
        modifier = Modifier.clickable { onClick() }
    )
    HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
}
