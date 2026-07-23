package com.truesummit.android.service

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Biometric / device-credential gate for the whole app.
 * Mirrors iOS AppLockService: enabled via SharedPrefs, locks on background,
 * unlocks via BiometricPrompt with device-credential fallback.
 */
object AppLockService {
    private const val PREFS_NAME = "truesummit_prefs"
    private const val KEY_ENABLED = "appLock.enabled"

    private var prefs: android.content.SharedPreferences? = null

    private val _isLocked = MutableStateFlow(false)
    val isLocked: StateFlow<Boolean> get() = _isLocked

    var isEnabled: Boolean
        get() = prefs?.getBoolean(KEY_ENABLED, false) ?: false
        set(value) {
            prefs?.edit()?.putBoolean(KEY_ENABLED, value)?.apply()
            if (!value) _isLocked.value = false
        }

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        // Cold launches start locked if enabled
        if (isEnabled) _isLocked.value = true
    }

    fun lockIfEnabled() {
        if (isEnabled) _isLocked.value = true
    }

    /** Returns true whether the device can authenticate at all. */
    fun isAuthAvailable(context: Context): Boolean {
        val bm = BiometricManager.from(context)
        return bm.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        ) == BiometricManager.BIOMETRIC_SUCCESS
    }

    /**
     * Shows the system auth sheet and returns true on success.
     * Must be called from a FragmentActivity context.
     */
    suspend fun authenticate(activity: FragmentActivity, reason: String): Boolean =
        suspendCancellableCoroutine { cont ->
            val executor = ContextCompat.getMainExecutor(activity)
            val callback = object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    _isLocked.value = false
                    if (cont.isActive) cont.resume(true)
                }
                override fun onAuthenticationFailed() {
                    // user failed but may retry — don't resolve yet
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (cont.isActive) cont.resume(false)
                }
            }

            val prompt = BiometricPrompt(activity, executor, callback)
            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock Summit")
                .setSubtitle(reason)
                .setAllowedAuthenticators(
                    BiometricManager.Authenticators.BIOMETRIC_STRONG or
                        BiometricManager.Authenticators.DEVICE_CREDENTIAL
                )
                .build()

            prompt.authenticate(info)
        }
}
