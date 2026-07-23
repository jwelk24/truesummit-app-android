package com.truesummit.android.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.truesummit.android.service.AuthService
import com.truesummit.android.service.SupabaseService
import kotlinx.coroutines.launch

import com.truesummit.android.service.HouseholdService
import com.truesummit.android.service.HouseholdRole

import com.truesummit.android.service.RealtimeService
import com.truesummit.android.billing.PremiumManager
import com.truesummit.android.billing.SubscriptionTier
import com.truesummit.android.billing.PremiumFeature

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    onUpgrade: () -> Unit
) {
    val isAuthenticated by SupabaseService.isAuthenticated.collectAsStateWithLifecycle()
    val currentEmail by SupabaseService.currentEmail.collectAsStateWithLifecycle()
    
    val household by HouseholdService.currentHousehold.collectAsStateWithLifecycle()
    val role by HouseholdService.currentRole.collectAsStateWithLifecycle()
    val isLoading by HouseholdService.isLoading.collectAsStateWithLifecycle()
    
    val currentTier by PremiumManager.currentTier.collectAsStateWithLifecycle()
    
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(isAuthenticated) {
        if (isAuthenticated) {
            HouseholdService.refresh()
        } else {
            RealtimeService.stop()
        }
    }

    LaunchedEffect(household) {
        household?.id?.let {
            RealtimeService.start(context, it)
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Summit Sync") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isAuthenticated) {
                SignedInView(
                    email = currentEmail ?: "—",
                    householdName = household?.name,
                    role = role?.value,
                    isLoading = isLoading,
                    onReload = { scope.launch { HouseholdService.refresh() } }
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                if (role == HouseholdRole.OWNER) {
                    if (currentTier == SubscriptionTier.PREMIUM) {
                        InviteMemberCard()
                    } else {
                        LockedFeatureCard(
                            feature = PremiumFeature.HOUSEHOLD,
                            onUpgrade = onUpgrade
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
                
                if (currentTier == SubscriptionTier.PREMIUM) {
                    JoinHouseholdCard()
                } else {
                    LockedFeatureCard(
                        feature = PremiumFeature.HOUSEHOLD,
                        onUpgrade = onUpgrade
                    )
                }
                
            } else {
                SignInForm()
            }
        }
    }
}

@Composable
fun SignedInView(
    email: String,
    householdName: String?,
    role: String?,
    isLoading: Boolean,
    onReload: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Signed in", style = MaterialTheme.typography.labelLarge)
            Text("Email: $email", style = MaterialTheme.typography.bodyLarge)
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text("Household", style = MaterialTheme.typography.labelLarge)
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            } else {
                Text("Name: ${householdName ?: "None"}", style = MaterialTheme.typography.bodyLarge)
                Text("Role: ${role ?: "—"}", style = MaterialTheme.typography.bodyLarge)
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = onReload, modifier = Modifier.fillMaxWidth()) {
                Text("Reload Household")
            }

            Spacer(modifier = Modifier.height(16.dp))
            val scope = rememberCoroutineScope()
            Button(
                onClick = { scope.launch { AuthService.signOut() } },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Sign Out")
            }
        }
    }
}

@Composable
fun InviteMemberCard() {
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
fun JoinHouseholdCard() {
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

@Composable
fun SignInForm() {
    var mode by remember { mutableStateOf(AuthMode.SIGN_IN) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isWorking by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var infoMessage by remember { mutableStateOf<String?>(null) }
    
    val scope = rememberCoroutineScope()

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (mode != AuthMode.FORGOT_PASSWORD) {
            TabRow(selectedTabIndex = if (mode == AuthMode.SIGN_IN) 0 else 1) {
                Tab(selected = mode == AuthMode.SIGN_IN, onClick = { 
                    mode = AuthMode.SIGN_IN 
                    errorMessage = null
                    infoMessage = null
                }) {
                    Text("Sign In", modifier = Modifier.padding(16.dp))
                }
                Tab(selected = mode == AuthMode.SIGN_UP, onClick = { 
                    mode = AuthMode.SIGN_UP 
                    errorMessage = null
                    infoMessage = null
                }) {
                    Text("Sign Up", modifier = Modifier.padding(16.dp))
                }
            }
        } else {
            Text("Reset Password", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(16.dp))
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth()
        )
        
        if (mode != AuthMode.FORGOT_PASSWORD) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
        }

        errorMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
        }
        infoMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(top = 8.dp))
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                scope.launch {
                    isWorking = true
                    errorMessage = null
                    infoMessage = null
                    try {
                        when (mode) {
                            AuthMode.SIGN_IN -> {
                                AuthService.signIn(email, password)
                            }
                            AuthMode.SIGN_UP -> {
                                AuthService.signUp(email, password)
                                infoMessage = "Confirmation email sent! Please check your inbox and spam folder."
                            }
                            AuthMode.FORGOT_PASSWORD -> {
                                AuthService.sendPasswordReset(email)
                                infoMessage = "Password reset email sent!"
                                mode = AuthMode.SIGN_IN
                            }
                        }
                    } catch (e: Exception) {
                        errorMessage = e.localizedMessage ?: "An error occurred"
                    } finally {
                        isWorking = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isWorking && email.isNotEmpty() && (mode == AuthMode.FORGOT_PASSWORD || password.length >= 6)
        ) {
            if (isWorking) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Text(when (mode) {
                    AuthMode.SIGN_IN -> "Sign In"
                    AuthMode.SIGN_UP -> "Create Account"
                    AuthMode.FORGOT_PASSWORD -> "Send Reset Link"
                })
            }
        }

        if (mode == AuthMode.SIGN_IN) {
            TextButton(onClick = { 
                mode = AuthMode.FORGOT_PASSWORD 
                errorMessage = null
                infoMessage = null
            }) {
                Text("Forgot Password?")
            }
        } else {
            TextButton(onClick = { 
                mode = AuthMode.SIGN_IN 
                errorMessage = null
                infoMessage = null
            }) {
                Text("Back to Sign In")
            }
        }
    }
}

enum class AuthMode { SIGN_IN, SIGN_UP, FORGOT_PASSWORD }

@Composable
fun LockedFeatureCard(feature: PremiumFeature, onUpgrade: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(feature.title, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Requires a Premium subscription.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onUpgrade, modifier = Modifier.fillMaxWidth()) {
                Text("View Plans")
            }
        }
    }
}
