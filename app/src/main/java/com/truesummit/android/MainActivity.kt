package com.truesummit.android

import android.os.Bundle
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.truesummit.android.ui.MainScreen
import com.truesummit.android.ui.auth.AuthScreen
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
import com.truesummit.android.service.WearSyncService
import com.truesummit.android.ui.onboarding.OnboardingState
import androidx.lifecycle.lifecycleScope
import androidx.room.Room
import com.truesummit.android.data.AppDatabase
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ThemeManager.init(this)
        TabOrderManager.init(this)
        OnboardingState.init(this)
        EngagementNudgesService.init(this)
        requestNotificationPermission()
        SpendingTodayManager.startOrUpdate(this)

        // Push a fresh snapshot to paired Wear OS watches
        lifecycleScope.launch {
            runCatching {
                val db = Room.databaseBuilder(applicationContext, AppDatabase::class.java, "truesummit-db")
                    .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4)
                    .build()
                WearSyncService(applicationContext, db).pushSnapshot()
            }
        }

        setContent {
            TrueSummitTheme {
                val isAuthenticated by SupabaseService.isAuthenticated.collectAsStateWithLifecycle()
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (isAuthenticated) {
                        MainScreen()
                    } else {
                        // In a real app, we'd handle onUpgrade differently here, 
                        // but MainScreen handles its own navigation.
                        AuthScreen(onUpgrade = { /* Navigation handled in MainScreen */ })
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
