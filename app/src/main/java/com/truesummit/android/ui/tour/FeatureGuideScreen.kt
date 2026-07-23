package com.truesummit.android.ui.tour

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeatureGuideScreen(
    onBack: () -> Unit,
    onStartTour: () -> Unit,
    onNavigateToTab: (String) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Feature Guide") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            item {
                ListItem(
                    headlineContent = { Text("Replay Guided Tour") },
                    leadingContent = {
                        Icon(Icons.Default.PlayArrow, contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary)
                    },
                    supportingContent = { Text("Walks through each tab, one at a time.") },
                    modifier = Modifier.clickable { onStartTour() }
                )
                HorizontalDivider()
            }

            tourStops.forEach { stop ->
                item {
                    Text(
                        stop.tabTitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, top = 20.dp, end = 16.dp, bottom = 4.dp)
                    )
                }
                stop.features.forEach { feature ->
                    item {
                        ListItem(
                            headlineContent = { Text(feature.title) },
                            supportingContent = { Text(feature.detail) }
                        )
                    }
                }
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { onNavigateToTab(stop.route) }) {
                            Text("Show Me")
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}
