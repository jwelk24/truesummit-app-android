package com.truesummit.android.ui.transactions.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.truesummit.android.data.AppDatabase
import com.truesummit.android.data.entity.CategoryEntity
import com.truesummit.android.data.entity.TransactionEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.util.Calendar
import java.util.Date
import java.util.UUID

data class MonthMetrics(
    val monthLabel: String,
    val income: BigDecimal,
    val spent: BigDecimal,
    val net: BigDecimal,
    val count: Int
)

class TransactionsViewModel(application: Application) : AndroidViewModel(application) {
    private val db = Room.databaseBuilder(
        application,
        AppDatabase::class.java, "truesummit-db"
    ).addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4).build()

    val transactions: StateFlow<List<TransactionEntity>> = db.transactionDao().getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categoriesById: StateFlow<Map<UUID, CategoryEntity>> = db.categoryDao().getCategories()
        .map { categories -> categories.associateBy { it.id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val monthMetrics: StateFlow<MonthMetrics> = transactions
        .map { txs -> computeMonthMetrics(txs) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), computeMonthMetrics(emptyList()))

    private fun computeMonthMetrics(all: List<TransactionEntity>): MonthMetrics {
        val cal = Calendar.getInstance()
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH)
        val monthly = all.filter {
            val c = Calendar.getInstance().apply { time = it.date }
            c.get(Calendar.YEAR) == year && c.get(Calendar.MONTH) == month
        }
        val income = monthly.filter { it.amount > BigDecimal.ZERO }.fold(BigDecimal.ZERO) { a, t -> a + t.amount }
        val spent = monthly.filter { it.amount < BigDecimal.ZERO }.fold(BigDecimal.ZERO) { a, t -> a + t.amount }.abs()
        val label = java.text.SimpleDateFormat("MMMM", java.util.Locale.getDefault()).format(Date())
        return MonthMetrics(
            monthLabel = label,
            income = income,
            spent = spent,
            net = income - spent,
            count = monthly.size
        )
    }

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            // Trigger Supabase/Plaid sync
            _isRefreshing.value = false
        }
    }

    fun deleteTransaction(transaction: TransactionEntity) {
        viewModelScope.launch {
            db.transactionDao().delete(transaction)
        }
    }
}
