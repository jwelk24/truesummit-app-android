package com.truesummit.android.ui.budget.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.truesummit.android.data.AppDatabase
import com.truesummit.android.data.entity.CategoryEntity
import com.truesummit.android.data.entity.CategoryGroupEntity
import com.truesummit.android.service.BudgetEngine
import com.truesummit.android.service.GoalForecast
import com.truesummit.android.ui.budget.CategoryBarColor
import com.truesummit.android.ui.budget.CategoryTileData
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.util.*

data class BudgetUiState(
    val groups: List<CategoryGroupEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val availableToBudget: BigDecimal = BigDecimal.ZERO,
    val selectedDate: Date = Date(),
    val allocations: Map<UUID, BigDecimal> = emptyMap(),
    val activity: Map<UUID, BigDecimal> = emptyMap(),
    val ageOfMoney: Int? = null,
    val transactionCount: Int = 0,
    val hasPlaidConnection: Boolean = false,
    val savingsRate: Double = 0.5,
    val netWorthTrend: Double = 0.5,
    val insight: String = "",
    val categoryTiles: List<CategoryTileData> = emptyList(),
    val categoryTilesById: Map<UUID, CategoryTileData> = emptyMap(),
    val projectedSpend: Map<UUID, BigDecimal> = emptyMap()
)

class BudgetViewModel(application: Application) : AndroidViewModel(application) {
    private val db = Room.databaseBuilder(
        application,
        AppDatabase::class.java, "truesummit-db"
    ).addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4).build()
    
    private val engine = BudgetEngine(application)

    private val _selectedYear = MutableStateFlow(Calendar.getInstance().get(Calendar.YEAR))
    private val _selectedMonth = MutableStateFlow(Calendar.getInstance().get(Calendar.MONTH) + 1)

    private val _yearMonth = combine(_selectedYear, _selectedMonth) { y, m -> Pair(y, m) }

    val uiState: StateFlow<BudgetUiState> = combine(
        db.categoryDao().getGroups(),
        db.categoryDao().getCategories(),
        db.transactionDao().getAll(),
        db.plaidLinkDao().getAccountLinkCountFlow(),
        _yearMonth
    ) { groups, categories, transactions, plaidCount, yearMonth ->
        val (year, month) = yearMonth
        val budgetMonth = engine.ensureMonth(year, month)
        val allocs = db.budgetDao().getAllocationsForMonth(budgetMonth.id).first()
        val allocationMap = mutableMapOf<UUID, BigDecimal>()
        allocs.forEach { alloc ->
            alloc.categoryId?.let { allocationMap[it] = alloc.amount }
        }
        
        val activityMap = categories.associate { cat ->
            cat.id to engine.activity(cat, year, month)
        }

        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, 1)
        }

        val thisMonthTxs = transactions.filter {
            val txCal = Calendar.getInstance().apply { time = it.date }
            txCal.get(Calendar.YEAR) == year && txCal.get(Calendar.MONTH) + 1 == month
        }
        val income = thisMonthTxs.filter { it.amount > BigDecimal.ZERO }.sumOf { it.amount }
        val expenses = thisMonthTxs.filter { it.amount < BigDecimal.ZERO }.sumOf { it.amount.abs() }
        val savingsRate = if (income > BigDecimal.ZERO)
            ((income - expenses).toDouble() / income.toDouble()).coerceIn(0.0, 1.0)
        else 0.5
        val available = engine.availableToBudget(transactions, budgetMonth, year, month)
        val totalAssigned = allocationMap.values.fold(BigDecimal.ZERO, BigDecimal::add)
        val totalActivity = activityMap.values.fold(BigDecimal.ZERO, BigDecimal::add)
        val totalSpent = totalActivity.abs()
        val insight = buildInsight(available, totalAssigned, totalSpent, income)

        val projectedSpendMap = categories.associate { cat ->
            val catActivity = activityMap[cat.id] ?: BigDecimal.ZERO
            cat.id to (engine.projectedMonthlySpend(catActivity, year, month) ?: BigDecimal.ZERO)
        }.filter { (_, v) -> v > BigDecimal.ZERO }

        val allTiles = categories.mapIndexed { idx, cat ->
            val catAssigned = allocationMap[cat.id] ?: BigDecimal.ZERO
            val catActivity = activityMap[cat.id] ?: BigDecimal.ZERO
            val catSpent = catActivity.abs()
            val availableNow = catAssigned + catActivity
            val goal = db.goalDao().getGoalForCategory(cat.id)
            val avgMonthly = engine.averageAssigned(cat, 3, year, month)
            val goalPace = goal?.let {
                GoalForecast.pace(
                    goal = it,
                    assignedThisMonth = catAssigned,
                    availableNow = availableNow,
                    avgMonthlyAssigned = avgMonthly,
                    currentYear = year,
                    currentMonth = month
                )
            }
            CategoryTileData(
                id = cat.id,
                name = cat.name,
                spent = catSpent,
                budget = catAssigned,
                index = idx,
                customColor = CategoryBarColor.effectiveColor(getApplication(), cat.id, cat.name),
                goalPace = goalPace,
                projectedSpend = projectedSpendMap[cat.id]
            )
        }
        val tiles = allTiles.filter { it.budget > BigDecimal.ZERO }.take(8)

        BudgetUiState(
            groups = groups,
            categories = categories,
            availableToBudget = available,
            selectedDate = cal.time,
            allocations = allocationMap,
            activity = activityMap,
            ageOfMoney = BudgetEngine.ageOfMoneyDays(transactions),
            transactionCount = transactions.size,
            hasPlaidConnection = plaidCount > 0,
            savingsRate = savingsRate,
            netWorthTrend = 0.5,
            insight = insight,
            categoryTiles = tiles,
            categoryTilesById = allTiles.associateBy { it.id },
            projectedSpend = projectedSpendMap
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BudgetUiState())

    fun changeMonth(delta: Int) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, _selectedYear.value)
            set(Calendar.MONTH, _selectedMonth.value - 1)
            add(Calendar.MONTH, delta)
        }
        _selectedYear.value = cal.get(Calendar.YEAR)
        _selectedMonth.value = cal.get(Calendar.MONTH) + 1
    }

    fun setAssigned(categoryId: UUID, amount: BigDecimal) {
        viewModelScope.launch {
            val month = engine.ensureMonth(_selectedYear.value, _selectedMonth.value)
            val category = db.categoryDao().getCategories().first().find { it.id == categoryId }
            if (category != null) {
                engine.setAssigned(amount, category, month)
            }
        }
    }

    private fun buildInsight(
        available: BigDecimal,
        assigned: BigDecimal,
        spent: BigDecimal,
        income: BigDecimal
    ): String {
        return when {
            available > BigDecimal.ZERO -> "You have ${formatCurrencySimple(available)} left to assign. Give every dollar a job."
            available < BigDecimal.ZERO -> "You're over-assigned by ${formatCurrencySimple(available.abs())}. Pull some budget back."
            assigned > BigDecimal.ZERO -> "Every dollar is assigned. Your budget is perfectly balanced."
            income > BigDecimal.ZERO -> "Great start — keep logging transactions to build your picture."
            else -> "Add your first transactions to get personalized insights."
        }
    }

    private fun formatCurrencySimple(amount: BigDecimal): String {
        return "$${"%,.0f".format(amount.toDouble())}"
    }

    fun autoAssign() {
        viewModelScope.launch {
            val txs = db.transactionDao().getAll().first()
            val cats = db.categoryDao().getCategories().first()
            val month = engine.ensureMonth(_selectedYear.value, _selectedMonth.value)
            engine.autoAssignAvailable(txs, cats, month)
        }
    }
}
