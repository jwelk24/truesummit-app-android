package com.truesummit.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.truesummit.wear.ui.TrueSummitWearApp
import com.truesummit.wear.ui.theme.TrueSummitWearTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Hydrate from disk so a cold start shows the last known numbers
        // instead of the "open your phone" placeholder.
        SnapshotRepository.load(this)

        setContent {
            TrueSummitWearTheme {
                val snapshot by SnapshotRepository.snapshot.collectAsState()
                TrueSummitWearApp(snapshot = snapshot)
            }
        }
    }
}
