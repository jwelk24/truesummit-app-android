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
import com.truesummit.android.service.AuthService
import com.truesummit.android.service.GoogleSignInService
import kotlinx.coroutines.launch

import com.truesummit.android.billing.PremiumFeature

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen() {
    Scaffold(
        topBar = { TopAppBar(title = { Text("TrueSummit Sync") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            SignInForm()
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
    val context = LocalContext.current

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

        // One button covers both tabs: Google returns the same identity whether
        // or not the account already exists, so there is nothing to choose.
        if (mode != AuthMode.FORGOT_PASSWORD) {
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(
                onClick = {
                    scope.launch {
                        isWorking = true
                        errorMessage = null
                        infoMessage = null
                        try {
                            GoogleSignInService.signIn(context)
                        } catch (e: GoogleSignInService.Cancelled) {
                            // Dismissing the sheet is not a failure.
                        } catch (e: Exception) {
                            errorMessage = e.localizedMessage ?: "Google sign-in failed"
                        } finally {
                            isWorking = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isWorking
            ) {
                Text("Continue with Google")
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
