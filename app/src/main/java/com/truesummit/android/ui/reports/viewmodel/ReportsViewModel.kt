package com.truesummit.android.ui.reports.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.truesummit.android.data.AppDatabase
import com.truesummit.android.data.entity.TransactionEntity
import com.truesummit.android.service.PDFExporter
import com.truesummit.android.service.ReportCompareMode
import com.truesummit.android.service.ReportRange
import com.truesummit.android.service.ReportSummary
import com.truesummit.android.service.ReportsService
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.util.*
import java.io.File

data class CategorySpending(
    val categoryName: String,
    val amount: BigDecimal
)

data class MonthlyFlow(
    val monthLabel: String,
    val income: BigDecimal,
    val spending: BigDecimal
)

data class ReportsUiState(
    val currentMonthSpending: List<CategorySpending> = emptyList(),
    val sixMonthFlow: List<MonthlyFlow> = emptyList(),
    val currentSummary: ReportSummary? = null,
    val compareSummary: ReportSummary? = null,
    val compareMode: ReportCompareMode = ReportCompareMode.OFF,
    val periodTransactions: List<TransactionEntity> = emptyList(),
    val categoryNames: Map<UUID, String> = emptyMap(),
    val allTags: List<String> = emptyList(),
    val selectedTag: String? = null
)

class ReportsViewModel(application: Application) : AndroidViewModel(application) {
    private val db = Room.databaseBuilder(
        application,
        AppDatabase::class.java, "truesummit-db"
    ).addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4).build()

    private val _compareMode = MutableStateFlow(ReportCompareMode.OFF)
    private val _selectedTag = MutableStateFlow<String?>(null)

    fun setCompareMode(mode: ReportCompareMode) { _compareMode.value = mode }
    fun selectTag(tag: String?) { _selectedTag.value = if (_selectedTag.value == tag) null else tag }

    val uiState: StateFlow<ReportsUiState> = combine(
        db.transactionDao().getAll(),
        db.categoryDao().getCategories(),
        combine(_compareMode, _selectedTag) { m, t -> Pair(m, t) }
    ) { transactions, categories, (compareMode, selectedTag) ->
        val calendar = Calendar.getInstance()
        val currentYear = calendar.get(Calendar.YEAR)
        val currentMonth = calendar.get(Calendar.MONTH) + 1

        // All tags across all transactions
        val allTags = transactions.flatMap { it.tagList() }.distinct().sorted()

        // Current Month Spending — filtered by tag if one is selected
        val categoryMap = categories.associateBy { it.id }
        val tagFilteredTxs = if (selectedTag != null)
            transactions.filter { it.tagList().contains(selectedTag) }
        else transactions
        val currentMonthTxs = tagFilteredTxs.filter {
            calendar.time = it.date
            calendar.get(Calendar.YEAR) == currentYear && (calendar.get(Calendar.MONTH) + 1) == currentMonth && it.amount < BigDecimal.ZERO
        }

        val spendingByCat = currentMonthTxs.groupBy { it.categoryId }
            .map { (catId, txs) ->
                val name = categoryMap[catId]?.name ?: "Uncategorized"
                CategorySpending(name, txs.fold(BigDecimal.ZERO) { acc, tx -> acc.add(tx.amount.abs()) })
            }.sortedByDescending { it.amount }

        // 6-Month Flow — also filtered by tag
        val sixMonthFlow = mutableListOf<MonthlyFlow>()
        for (i in 5 downTo 0) {
            val cal = Calendar.getInstance()
            cal.add(Calendar.MONTH, -i)
            val year = cal.get(Calendar.YEAR)
            val month = cal.get(Calendar.MONTH) + 1

            val monthTxs = tagFilteredTxs.filter {
                calendar.time = it.date
                calendar.get(Calendar.YEAR) == year && (calendar.get(Calendar.MONTH) + 1) == month
            }

            val income = monthTxs.filter { it.amount > BigDecimal.ZERO }.fold(BigDecimal.ZERO) { acc, tx -> acc.add(tx.amount) }
            val spending = monthTxs.filter { it.amount < BigDecimal.ZERO }.fold(BigDecimal.ZERO) { acc, tx -> acc.add(tx.amount.abs()) }

            val label = cal.getDisplayName(Calendar.MONTH, Calendar.SHORT, Locale.getDefault()) ?: ""
            sixMonthFlow.add(MonthlyFlow(label, income, spending))
        }

        // Current period summary for comparison section
        val categoryNames = categories.associate { it.id to it.name }
        val now = Calendar.getInstance()
        val thisMonthPeriod = ReportsService.resolvePeriod(ReportRange.THIS_MONTH, null, null)
        val currentSummary = ReportsService.buildSummary(transactions, thisMonthPeriod, categoryNames)
        val comparePeriod = thisMonthPeriod.comparisonPeriod(compareMode)
        val compareSummary = comparePeriod?.let {
            ReportsService.buildSummary(transactions, it, categoryNames)
        }

        val periodStart = thisMonthPeriod.start
        val periodEnd = thisMonthPeriod.end
        val periodTxs = transactions.filter { it.date >= periodStart && it.date <= periodEnd }

        ReportsUiState(
            currentMonthSpending = spendingByCat,
            sixMonthFlow = sixMonthFlow,
            currentSummary = currentSummary,
            compareSummary = compareSummary,
            compareMode = compareMode,
            periodTransactions = periodTxs,
            categoryNames = categoryNames,
            allTags = allTags,
            selectedTag = selectedTag
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ReportsUiState())

    fun exportCSV(range: ReportRange, customStart: Date?, customEnd: Date?, onResult: (File?) -> Unit) {
        viewModelScope.launch {
            val period = ReportsService.resolvePeriod(range, customStart, customEnd)
            val txs = db.transactionDao().getAll().first()
            val file = ReportsService.exportToCSV(getApplication(), txs, period)
            onResult(file)
        }
    }

    fun exportPDF(range: ReportRange, customStart: Date?, customEnd: Date?, onResult: (File?) -> Unit) {
        viewModelScope.launch {
            val period = ReportsService.resolvePeriod(range, customStart, customEnd)
            val txs = db.transactionDao().getAll().first()
            val categories = db.categoryDao().getCategories().first().associate { it.id to it.name }
            val summary = ReportsService.buildSummary(txs, period, categories)
            val file = PDFExporter.exportReportToPDF(getApplication(), summary)
            onResult(file)
        }
    }
}
