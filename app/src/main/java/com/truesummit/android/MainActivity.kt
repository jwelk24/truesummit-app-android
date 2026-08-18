package com.truesummit.android

import android.os.Bundle
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.truesummit.android.ui.MainScreen
import com.truesummit.android.ui.auth.AuthScreen
import com.truesummit.android.service.HouseholdService
import com.truesummit.android.service.RealtimeService
import com.truesummit.android.ui.theme.TrueSummitTheme
import com.truesummit.android.service.SupabaseService
import androidx.lifecycle.compose.collectAsStateWithLifecycle

import com.truesummit.android.service.SpendingTodayManager
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

import com.truesummit.android.ui.navigation.TabOrderManager
import com.truesummit.android.ui.theme.ThemeManager
import com.truesummit.android.service.EngagementNudgesService
import com.truesummit.android.ui.onboarding.OnboardingState

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ThemeManager.init(this)
        TabOrderManager.init(this)
        OnboardingState.init(this)
        EngagementNudgesService.init(this)
        requestNotificationPermission()
        SpendingTodayManager.startOrUpdate(this)

        setContent {
            TrueSummitTheme {
                val isAuthenticated by SupabaseService.isAuthenticated.collectAsStateWithLifecycle()
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Household load and realtime used to be driven from
                    // AuthScreen, which only renders while signed *out* — so
                    // neither ever ran for a signed-in user. They are session
                    // concerns, not screen concerns, so they live here.
                    val household by HouseholdService.currentHousehold.collectAsStateWithLifecycle()
                    LaunchedEffect(isAuthenticated) {
                        if (isAuthenticated) HouseholdService.refresh() else RealtimeService.stop()
                    }
                    LaunchedEffect(household) {
                        household?.id?.let { RealtimeService.start(this@MainActivity, it) }
                    }

                    if (isAuthenticated) {
                        MainScreen()
                    } else {
                        AuthScreen()
                    }
                }
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            }
        }
    }
}
