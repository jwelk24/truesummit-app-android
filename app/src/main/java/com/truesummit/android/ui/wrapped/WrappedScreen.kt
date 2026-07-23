package com.truesummit.android.ui.wrapped

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import com.truesummit.android.service.WrappedService
import com.truesummit.android.service.WrappedStats
import com.truesummit.android.ui.transactions.formatCurrency
import kotlinx.coroutines.flow.*
import java.util.*

class WrappedViewModel(application: Application) : AndroidViewModel(application) {
    private val db = Room.databaseBuilder(application, AppDatabase::class.java, "truesummit-db")
        .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4).build()

    val year = Calendar.getInstance().get(Calendar.YEAR)

    val stats: StateFlow<WrappedStats?> = db.transactionDao().getAll()
        .map { WrappedService.compute(it, year) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun WrappedScreen(onBack: () -> Unit) {
    val vm: WrappedViewModel = viewModel()
    val stats by vm.stats.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("${vm.year} Wrapped") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        val s = stats
        if (s == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        val pages = buildPages(s, vm.year)
        val pagerState = rememberPagerState(pageCount = { pages.size })

        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
                pages[page]()
            }
            // Page dots
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                repeat(pages.size) { i ->
                    val selected = pagerState.currentPage == i
                    Surface(
                        modifier = Modifier.padding(horizontal = 4.dp).size(if (selected) 10.dp else 6.dp),
                        shape = MaterialTheme.shapes.small,
                        color = if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    ) {}
                }
            }
        }
    }
}

@Composable
private fun buildPages(stats: WrappedStats, year: Int): List<@Composable () -> Unit> {
    return listOf(
        { WrappedPage("${year} at a Glance", Icons.Default.CalendarMonth) {
            StatItem("Total Spent", formatCurrency(stats.totalSpent.toDouble()))
            StatItem("Total Earned", formatCurrency(stats.totalIncome.toDouble()))
            StatItem("No-Spend Days", "${stats.noSpendDays}")
        }},
        { WrappedPage("Top Categories", Icons.Default.Category) {
            stats.topCategories.take(5).forEach { (name, amount) ->
                StatItem(name, formatCurrency(amount.toDouble()))
            }
        }},
        { WrappedPage("Favourite Merchant", Icons.Default.Store) {
            val m = stats.topMerchant
            if (m != null) {
                StatItem("Merchant", m.first)
                StatItem("Visits", "${m.second}")
                StatItem("Total Spent", formatCurrency(m.third.toDouble()))
            } else {
                Text("Not enough data", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }},
        { WrappedPage("Biggest Purchase", Icons.Default.ShoppingBag) {
            val b = stats.biggestPurchase
            if (b != null) {
                StatItem("Merchant", b.first)
                StatItem("Amount", formatCurrency(b.second.toDouble()))
            } else {
                Text("No large purchases found", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }},
        { WrappedPage("Streaks & Habits", Icons.Default.EmojiEvents) {
            StatItem("Longest no-spend streak", "${stats.longestNoSpendStreak} days")
            StatItem("Busiest month", stats.busiestMonth?.first ?: "—")
            StatItem("No-spend days", "${stats.noSpendDays}")
        }}
    )
}

@Composable
private fun WrappedPage(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        content()
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}
