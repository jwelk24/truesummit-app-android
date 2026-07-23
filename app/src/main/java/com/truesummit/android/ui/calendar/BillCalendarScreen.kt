package com.truesummit.android.ui.calendar

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.room.Room
import com.truesummit.android.data.AppDatabase
import com.truesummit.android.service.BillCalendarService
import com.truesummit.android.service.BillOccurrence
import com.truesummit.android.ui.transactions.formatCurrency
import kotlinx.coroutines.flow.*
import java.math.BigDecimal
import java.util.*

data class BillCalendarUiState(
    val monthAnchor: Date = Date(),
    val dayMap: Map<String, List<BillOccurrence>> = emptyMap()
)

fun dayKey(d: Date): String {
    val cal = Calendar.getInstance().apply { time = d }
    return "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.MONTH)}-${cal.get(Calendar.DAY_OF_MONTH)}"
}

class BillCalendarViewModel(application: Application) : AndroidViewModel(application) {
    private val db = Room.databaseBuilder(application, AppDatabase::class.java, "truesummit-db")
        .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4).build()

    private val _anchor = MutableStateFlow(
        Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1) }.time
    )

    val uiState: StateFlow<BillCalendarUiState> = combine(
        db.scheduledItemDao().getAll(),
        _anchor
    ) { scheduled, anchor ->
        val raw = BillCalendarService.occurrencesByDay(scheduled, anchor)
        val stringKeyed = raw.entries.associate { (date, list) -> dayKey(date) to list }
        BillCalendarUiState(monthAnchor = anchor, dayMap = stringKeyed)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BillCalendarUiState())

    fun prevMonth() { shiftMonth(-1) }
    fun nextMonth() { shiftMonth(1) }

    private fun shiftMonth(delta: Int) {
        val cal = Calendar.getInstance().apply { time = _anchor.value }
        cal.add(Calendar.MONTH, delta)
        cal.set(Calendar.DAY_OF_MONTH, 1)
        _anchor.value = cal.time
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillCalendarScreen(onBack: () -> Unit) {
    val vm: BillCalendarViewModel = viewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()
    var selectedKey by remember { mutableStateOf<String?>(null) }

    val monthFmt = java.text.SimpleDateFormat("MMMM yyyy", Locale.getDefault())
    val anchor = state.monthAnchor
    val cal = Calendar.getInstance().apply { time = anchor }
    val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val firstDayOfWeek = cal.apply { set(Calendar.DAY_OF_MONTH, 1) }.get(Calendar.DAY_OF_WEEK) - 1

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bill Calendar") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            // Month navigation
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { vm.prevMonth() }) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "Previous month")
                }
                Text(monthFmt.format(anchor), style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = { vm.nextMonth() }) {
                    Icon(Icons.Default.ChevronRight, contentDescription = "Next month")
                }
            }

            // Day headers
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                listOf("S", "M", "T", "W", "T", "F", "S").forEach { d ->
                    Text(d, modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(7),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                userScrollEnabled = false
            ) {
                // Leading blanks
                items(firstDayOfWeek) { Box(Modifier.aspectRatio(1f)) }
                // Days
                items(daysInMonth) { dayIndex ->
                    val dayOfMonth = dayIndex + 1
                    val calDay = Calendar.getInstance().apply {
                        time = anchor
                        set(Calendar.DAY_OF_MONTH, dayOfMonth)
                    }
                    val key = dayKey(calDay.time)
                    val occurrences = state.dayMap[key] ?: emptyList()
                    val isSelected = key == selectedKey
                    val today = dayKey(Date()) == key

                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .padding(2.dp)
                            .background(
                                when {
                                    isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                    today -> MaterialTheme.colorScheme.secondaryContainer
                                    else -> androidx.compose.ui.graphics.Color.Transparent
                                },
                                MaterialTheme.shapes.small
                            )
                            .clickable { selectedKey = if (isSelected) null else key },
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "$dayOfMonth",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (today) FontWeight.Bold else FontWeight.Normal
                            )
                            if (occurrences.isNotEmpty()) {
                                Box(
                                    modifier = Modifier.size(6.dp).background(
                                        if (occurrences.any { !it.isIncome }) MaterialTheme.colorScheme.error
                                        else MaterialTheme.colorScheme.primary,
                                        shape = MaterialTheme.shapes.small
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // Detail panel
            selectedKey?.let { key ->
                val items = state.dayMap[key] ?: emptyList()
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Scheduled", style = MaterialTheme.typography.titleSmall)
                    items.forEach { occ ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(occ.item.name, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                formatCurrency(occ.item.amount.abs().toDouble()),
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (occ.isIncome) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}
