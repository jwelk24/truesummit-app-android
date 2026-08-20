package com.truesummit.android.ui.challenges

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.truesummit.android.data.AppDatabase
import com.truesummit.android.service.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Date

data class ChallengesUiState(
    val activeProgress: List<ChallengeProgress> = emptyList(),
    val suggestions: List<Challenge> = emptyList(),
    val completedIds: Set<String> = emptySet()
)

class ChallengesViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getInstance(application)

    init {
        ChallengeStore.init(application)
    }

    private val _refresh = MutableStateFlow(0)

    val uiState: StateFlow<ChallengesUiState> = combine(
        db.transactionDao().getAll(),
        _refresh
    ) { transactions, _ ->
        val active = ChallengeStore.activeChallenges()
        ChallengesUiState(
            activeProgress = active.map { ChallengeEngine.progress(it, transactions) },
            suggestions = ChallengeEngine.suggestions(transactions).filter { s ->
                active.none { it.id == s.id }
            },
            completedIds = ChallengeStore.completedIds()
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ChallengesUiState())

    fun startChallenge(challenge: Challenge) {
        ChallengeStore.startChallenge(challenge)
        _refresh.value++
    }

    fun removeChallenge(id: String) {
        ChallengeStore.removeChallenge(id)
        _refresh.value++
    }

    fun checkCompletions(progressList: List<ChallengeProgress>) {
        progressList.filter { it.isComplete }.forEach { p ->
            if (p.challenge.id !in ChallengeStore.completedIds()) {
                ChallengeStore.markCompleted(p.challenge.id)
                _refresh.value++
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChallengesScreen(onBack: () -> Unit) {
    val vm: ChallengesViewModel = viewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.activeProgress) {
        vm.checkCompletions(state.activeProgress)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Challenges") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.activeProgress.isNotEmpty()) {
                item {
                    Text("Active", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                items(state.activeProgress) { progress ->
                    ActiveChallengeCard(progress, onRemove = { vm.removeChallenge(progress.challenge.id) })
                }
            }

            if (state.suggestions.isNotEmpty()) {
                item {
                    Text("Suggested", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                items(state.suggestions) { challenge ->
                    SuggestedChallengeCard(challenge, onStart = { vm.startChallenge(challenge) })
                }
            }

            if (state.completedIds.isNotEmpty()) {
                item {
                    Text("Completed (${state.completedIds.size})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun ActiveChallengeCard(progress: ChallengeProgress, onRemove: () -> Unit) {
    val fraction = (progress.current / progress.challenge.goal).toFloat().coerceIn(0f, 1f)
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(progress.challenge.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                    Text(progress.challenge.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (progress.isComplete) {
                    Icon(Icons.Default.CheckCircle, contentDescription = "Complete",
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                } else {
                    IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Remove",
                            modifier = Modifier.size(16.dp))
                    }
                }
            }
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth(),
                color = if (progress.isComplete) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.secondary
            )
            Text("${String.format("%.1f", progress.current)} / ${String.format("%.1f", progress.challenge.goal)} • ${progress.challenge.durationDays}d challenge",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SuggestedChallengeCard(challenge: Challenge, onStart: () -> Unit) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(challenge.title, style = MaterialTheme.typography.titleSmall)
                Text(challenge.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${challenge.durationDays}d", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(8.dp))
            FilledTonalButton(onClick = onStart) { Text("Start") }
        }
    }
}
