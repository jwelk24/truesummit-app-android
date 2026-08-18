package com.truesummit.android.ui.account

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.truesummit.android.billing.PremiumFeature
import com.truesummit.android.billing.PremiumManager
import com.truesummit.android.billing.SubscriptionTier
import com.truesummit.android.service.AuthService
import com.truesummit.android.service.HouseholdRole
import com.truesummit.android.service.HouseholdService
import com.truesummit.android.service.SupabaseService
import com.truesummit.android.ui.auth.LockedFeatureCard
import kotlinx.coroutines.launch

/**
 * Account and household state for the signed-in user. This lived inside
 * AuthScreen behind an `isAuthenticated` branch that could never be true —
 * AuthScreen is only composed when signed *out* — so none of it, including
 * Sign Out, was reachable. It belongs on Sync & Account instead.
 */
@Composable
fun AccountSection(onUpgrade: () -> Unit) {
    val email by SupabaseService.currentEmail.collectAsStateWithLifecycle()
    val household by HouseholdService.currentHousehold.collectAsStateWithLifecycle()
    val role by HouseholdService.currentRole.collectAsStateWithLifecycle()
    val isLoading by HouseholdService.isLoading.collectAsStateWithLifecycle()
    val currentTier by PremiumManager.currentTier.collectAsStateWithLifecycle()

    val scope = rememberCoroutineScope()
    var confirmSignOut by remember { mutableStateOf(false) }

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text("Sign out?") },
            text = { Text("Your data stays on this device. You'll need to sign in again to sync or share a budget.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmSignOut = false
                    scope.launch { AuthService.signOut() }
                }) {
                    Text("Sign Out", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmSignOut = false }) { Text("Cancel") }
            }
        )
    }

    Column(modifier = Modifier.padding(16.dp)) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Signed in", style = MaterialTheme.typography.labelLarge)
                Text(email ?: "—", style = MaterialTheme.typography.bodyLarge)

                Spacer(modifier = Modifier.height(16.dp))

                Text("Household", style = MaterialTheme.typography.labelLarge)
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                } else {
                    Text("Name: ${household?.name ?: "None"}", style = MaterialTheme.typography.bodyLarge)
                    Text("Role: ${role?.value ?: "—"}", style = MaterialTheme.typography.bodyLarge)
                }

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { scope.launch { HouseholdService.refresh() } },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Reload Household")
                }

                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { confirmSignOut = true },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Sign Out")
                }
            }
        }

        if (role == HouseholdRole.OWNER) {
            Spacer(modifier = Modifier.height(16.dp))
            if (currentTier == SubscriptionTier.PREMIUM) {
                InviteMemberCard()
            } else {
                LockedFeatureCard(feature = PremiumFeature.HOUSEHOLD, onUpgrade = onUpgrade)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        if (currentTier == SubscriptionTier.PREMIUM) {
            JoinHouseholdCard()
        } else {
            LockedFeatureCard(feature = PremiumFeature.HOUSEHOLD, onUpgrade = onUpgrade)
        }
    }
}

@Composable
private fun InviteMemberCard() {
    var generatedCode by remember { mutableStateOf<String?>(null) }
    var isBusy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Invite a member", style = MaterialTheme.typography.titleMedium)

            if (generatedCode != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text("Code: $generatedCode", style = MaterialTheme.typography.headlineSmall)
                Text("Share this code with someone you trust. Expires in 7 days.", style = MaterialTheme.typography.bodySmall)
            }

            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    scope.launch {
                        isBusy = true
                        try {
                            generatedCode = HouseholdService.createInvite()
                        } catch (e: Exception) {
                            // Handle error
                        } finally {
                            isBusy = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isBusy
            ) {
                if (isBusy) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                } else {
                    Text(if (generatedCode == null) "Generate Invite Code" else "Generate New Code")
                }
            }
        }
    }
}

@Composable
private fun JoinHouseholdCard() {
    var inviteCode by remember { mutableStateOf("") }
    var isBusy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Join a household", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = inviteCode,
                onValueChange = { inviteCode = it.uppercase() },
                label = { Text("Enter invite code") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    scope.launch {
                        isBusy = true
                        try {
                            HouseholdService.redeemInvite(inviteCode)
                            inviteCode = ""
                        } catch (e: Exception) {
                            // Handle error
                        } finally {
                            isBusy = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isBusy && inviteCode.isNotBlank()
            ) {
                if (isBusy) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                } else {
                    Text("Join")
                }
            }
        }
    }
}
