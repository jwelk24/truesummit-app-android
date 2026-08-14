package com.truesummit.android.ui.tax

import android.app.Application
import android.content.Intent
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
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.room.Room
import com.truesummit.android.billing.PremiumManager
import com.truesummit.android.billing.SubscriptionTier
import com.truesummit.android.data.AppDatabase
import com.truesummit.android.data.entity.CategoryEntity
import com.truesummit.android.data.entity.TransactionEntity
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.math.BigDecimal
import java.text.DateFormat
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

data class TaxLine(val categoryName: String, val total: BigDecimal, val count: Int)
data class TaxItem(val date: Date, val merchant: String, val categoryName: String, val amount: BigDecimal, val memo: String?)

data class TaxSummary(
    val year: Int,
    val lines: List<TaxLine>,
    val items: List<TaxItem>,
    val grossIncome: BigDecimal
) {
    val total: BigDecimal get() = lines.fold(BigDecimal.ZERO) { acc, l -> acc.add(l.total) }
}

object TaxSettingsStore {
    private const val KEY = "tax.categoryIDs"
    private val suggestedKeywords = listOf(
        "charity", "donat", "tithe", "medical", "health", "doctor", "dental",
        "pharmacy", "childcare", "child care", "daycare", "tuition", "education",
        "student loan", "business", "mortgage interest", "property tax"
    )

    fun categoryIDs(context: android.content.Context): Set<UUID> {
        val raw = context.getSharedPreferences("tax", 0).getStringSet(KEY, null) ?: return emptySet()
        return raw.mapNotNull { it.toUUID() }.toSet()
    }

    fun setCategoryIDs(context: android.content.Context, ids: Set<UUID>) {
        context.getSharedPreferences("tax", 0).edit()
            .putStringSet(KEY, ids.map { it.toString() }.toSet()).apply()
    }

    fun hasConfigured(context: android.content.Context): Boolean =
        context.getSharedPreferences("tax", 0).contains(KEY)

    fun isSuggested(name: String): Boolean {
        val lower = name.lowercase()
        return suggestedKeywords.any { lower.contains(it) }
    }

    private fun String.toUUID(): UUID? = try { UUID.fromString(this) } catch (e: Exception) { null }
}

object TaxPackBuilder {
    fun build(
        transactions: List<TransactionEntity>,
        categories: Map<UUID, CategoryEntity>,
        categoryIDs: Set<UUID>,
        year: Int,
        now: Date = Date()
    ): TaxSummary {
        val cal = Calendar.getInstance()
        cal.set(year, 0, 1, 0, 0, 0); cal.set(Calendar.MILLISECOND, 0)
        val start = cal.time
        cal.set(year + 1, 0, 1, 0, 0, 0)
        val end = minOf(cal.time, now)

        val inYear = transactions.filter { it.date >= start && it.date < end }
        val items = mutableListOf<TaxItem>()

        for (tx in inYear) {
            if (tx.amount >= BigDecimal.ZERO) continue
            val catId = tx.categoryId ?: continue
            if (!categoryIDs.contains(catId)) continue
            val catName = categories[catId]?.name ?: continue
            items.add(TaxItem(tx.date, tx.merchant, catName, tx.amount.abs(), tx.memo))
        }

        val lines = items.groupBy { it.categoryName }
            .map { (name, rows) -> TaxLine(name, rows.fold(BigDecimal.ZERO) { acc, i -> acc.add(i.amount) }, rows.size) }
            .sortedByDescending { it.total }

        val grossIncome = inYear
            .filter { it.amount > BigDecimal.ZERO }
            .fold(BigDecimal.ZERO) { acc, tx -> acc.add(tx.amount) }

        return TaxSummary(year, lines, items.sortedWith(compareBy({ it.categoryName }, { it.date })), grossIncome)
    }

    fun writeCSV(summary: TaxSummary, context: android.content.Context): File? {
        val df = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val sb = StringBuilder("category,date,merchant,amount,memo\n")
        for (item in summary.items) {
            sb.append("${csvEscape(item.categoryName)},${df.format(item.date)},${csvEscape(item.merchant)},${item.amount.toPlainString()},${csvEscape(item.memo ?: "")}\n")
        }
        sb.append("\nSUMMARY ${summary.year},,,,\n")
        for (line in summary.lines) {
            sb.append("${csvEscape(line.categoryName)},,,${line.total.toPlainString()},${line.count} transactions\n")
        }
        sb.append("TOTAL,,,${summary.total.toPlainString()},\n")
        sb.append("GROSS INCOME,,,${summary.grossIncome.toPlainString()},\n")

        return try {
            val file = File(context.cacheDir, "truesummit-tax-pack-${summary.year}.csv")
            file.writeText(sb.toString())
            file
        } catch (e: Exception) { null }
    }

    private fun csvEscape(s: String): String {
        return if (s.contains(",") || s.contains("\"") || s.contains("\n"))
            "\"${s.replace("\"", "\"\"")}\""
        else s
    }
}

class TaxPackViewModel(application: Application) : AndroidViewModel(application) {
    private val db = Room.databaseBuilder(application, AppDatabase::class.java, "truesummit-db")
        .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4).build()
    private val app = application

    var year by mutableIntStateOf(Calendar.getInstance().get(Calendar.YEAR))
    var selectedIDs by mutableStateOf<Set<UUID>>(emptySet())
    var categories by mutableStateOf<List<CategoryEntity>>(emptyList())
    var summary by mutableStateOf(TaxSummary(year, emptyList(), emptyList(), BigDecimal.ZERO))
    var initialized by mutableStateOf(false)

    init {
        viewModelScope.launch {
            combine(db.transactionDao().getAll(), db.categoryDao().getCategories()) { txs, cats ->
                categories = cats
                if (!initialized) {
                    selectedIDs = if (TaxSettingsStore.hasConfigured(app)) {
                        TaxSettingsStore.categoryIDs(app)
                    } else {
                        val seeded = cats.filter { TaxSettingsStore.isSuggested(it.name) }.map { it.id }.toSet()
                        TaxSettingsStore.setCategoryIDs(app, seeded)
                        seeded
                    }
                    initialized = true
                }
                val catMap = cats.associateBy { it.id }
                summary = TaxPackBuilder.build(txs, catMap, selectedIDs, year)
            }.collect()
        }
    }

    fun toggleCategory(id: UUID) {
        selectedIDs = if (selectedIDs.contains(id)) selectedIDs - id else selectedIDs + id
        TaxSettingsStore.setCategoryIDs(app, selectedIDs)
        rebuildSummary()
    }

    fun changeYear(y: Int) {
        year = y
        rebuildSummary()
    }

    private fun rebuildSummary() {
        viewModelScope.launch {
            val txs = db.transactionDao().getAll().first()
            val cats = db.categoryDao().getCategories().first()
            summary = TaxPackBuilder.build(txs, cats.associateBy { it.id }, selectedIDs, year)
        }
    }

    fun exportCSV(context: android.content.Context): File? = TaxPackBuilder.writeCSV(summary, context)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaxPackScreen(
    onBack: () -> Unit,
    onUpgrade: () -> Unit,
    viewModel: TaxPackViewModel = viewModel()
) {
    val context = LocalContext.current
    val currentTier by PremiumManager.currentTier.collectAsState()
    val canExport = currentTier == SubscriptionTier.PREMIUM
    val thisYear = Calendar.getInstance().get(Calendar.YEAR)
    var showYearMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tax Pack") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    Box {
                        TextButton(onClick = { showYearMenu = true }) {
                            Text("${viewModel.year}", fontWeight = FontWeight.SemiBold)
                        }
                        DropdownMenu(expanded = showYearMenu, onDismissRequest = { showYearMenu = false }) {
                            listOf(thisYear, thisYear - 1).forEach { y ->
                                DropdownMenuItem(
                                    text = {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            if (y == viewModel.year) Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Text("$y")
                                        }
                                    },
                                    onClick = { viewModel.changeYear(y); showYearMenu = false }
                                )
                            }
                        }
                    }
                }
            )
        }
    ) { padding ->
        val summary = viewModel.summary

        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Summary section
            item {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${viewModel.year} Summary", style = MaterialTheme.typography.titleSmall)

                        if (summary.lines.isEmpty()) {
                            Text("No spending in the selected categories for ${viewModel.year} yet.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            summary.lines.forEach { line ->
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Column {
                                        Text(line.categoryName, style = MaterialTheme.typography.bodyMedium)
                                        Text("${line.count} transaction${if (line.count == 1) "" else "s"}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Text(currencyWhole(line.total), fontWeight = FontWeight.SemiBold)
                                }
                            }
                            HorizontalDivider()
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Total", fontWeight = FontWeight.SemiBold)
                                Text(currencyWhole(summary.total), fontWeight = FontWeight.SemiBold)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Gross income", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(currencyWhole(summary.grossIncome), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Text("Not tax advice — verify with your tax professional.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // Export section
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Export", style = MaterialTheme.typography.titleSmall)
                        if (canExport) {
                            Button(
                                onClick = {
                                    val file = viewModel.exportCSV(context)
                                    if (file != null) {
                                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                        val intent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/csv"
                                            putExtra(Intent.EXTRA_STREAM, uri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(intent, "Share Tax Pack"))
                                    }
                                },
                                enabled = summary.items.isNotEmpty(),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.GridOn, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Export CSV for Your Accountant")
                            }
                        } else {
                            OutlinedButton(onClick = onUpgrade, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.Lock, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Export (Premium)")
                            }
                        }
                        Text("Lists every matching transaction plus per-category totals.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // Category picker
            item {
                Text("Tax-Relevant Categories", style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp))
            }
            items(viewModel.categories) { cat ->
                ListItem(
                    headlineContent = { Text(cat.name) },
                    leadingContent = {
                        Checkbox(
                            checked = viewModel.selectedIDs.contains(cat.id),
                            onCheckedChange = { viewModel.toggleCategory(cat.id) }
                        )
                    },
                    modifier = androidx.compose.ui.Modifier.let { it }
                )
            }
            item {
                Text("Pick the categories that matter at tax time — donations, medical, childcare, business expenses. TrueSummit pre-selects likely ones by name; your picks are remembered for next year.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

private fun currencyWhole(d: BigDecimal): String {
    val fmt = NumberFormat.getCurrencyInstance(Locale.US)
    fmt.maximumFractionDigits = 0
    return fmt.format(d)
}
