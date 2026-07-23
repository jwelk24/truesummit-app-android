package com.truesummit.android.ui.insights

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.truesummit.android.data.AppDatabase
import com.truesummit.android.service.AIInsightsService
import com.truesummit.android.service.MoneyQueryService
import com.truesummit.android.service.WeeklyDigest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.*

class AIInsightsViewModel(application: Application) : AndroidViewModel(application) {
    private val db = Room.databaseBuilder(
        application,
        AppDatabase::class.java, "truesummit-db"
    ).addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4).build()
    
    private val aiService = AIInsightsService(application)

    private val _digest = MutableStateFlow<WeeklyDigest?>(null)
    val digest: StateFlow<WeeklyDigest?> = _digest

    private val _isGeneratingDigest = MutableStateFlow(false)
    val isGeneratingDigest: StateFlow<Boolean> = _isGeneratingDigest

    private val _isCategorizing = MutableStateFlow(false)
    val isCategorizing: StateFlow<Boolean> = _isCategorizing

    private val _categorizeResult = MutableStateFlow<String?>(null)
    val categorizeResult: StateFlow<String?> = _categorizeResult

    private val _queryResult = MutableStateFlow<String?>(null)
    val queryResult: StateFlow<String?> = _queryResult
    private val _isQuerying = MutableStateFlow(false)
    val isQuerying: StateFlow<Boolean> = _isQuerying

    fun generateDigest() {
        viewModelScope.launch {
            _isGeneratingDigest.value = true
            try {
                val transactions = db.transactionDao().getAll().first()
                val result = aiService.generateWeeklySummary(transactions)
                _digest.value = result
            } catch (e: Exception) {
                // Handle error
            } finally {
                _isGeneratingDigest.value = false
            }
        }
    }

    fun askQuery(question: String) {
        viewModelScope.launch {
            _isQuerying.value = true
            _queryResult.value = null
            try {
                val transactions = db.transactionDao().getAll().first()
                val categories = db.categoryDao().getCategories().first()
                val categoryNames: Map<UUID, String> = categories.associate { it.id to it.name }
                val query = MoneyQueryService.parse(question)
                val result = MoneyQueryService.execute(query, transactions, categoryNames)
                _queryResult.value = result.answer
            } catch (e: Exception) {
                _queryResult.value = "Couldn't process that — try rephrasing."
            } finally {
                _isQuerying.value = false
            }
        }
    }

    fun runSmartCategorize() {
        viewModelScope.launch {
            _isCategorizing.value = true
            _categorizeResult.value = null
            try {
                val transactions = db.transactionDao().getAll().first().filter { it.categoryId == null }
                val categories = db.categoryDao().getCategories().first()
                var updatedCount = 0
                
                transactions.forEach { tx ->
                    val suggestion = aiService.suggestCategory(tx, categories)
                    if (suggestion != null && suggestion.confidence > 0.5) {
                        val category = categories.find { it.id.toString() == suggestion.categoryId }
                        if (category != null) {
                            db.transactionDao().update(tx.copy(categoryId = category.id))
                            updatedCount++
                        }
                    }
                }
                _categorizeResult.value = if (updatedCount == 0) {
                    "Nothing to categorize — every transaction already has a category."
                } else {
                    "Categorized $updatedCount transaction${if (updatedCount == 1) "" else "s"}."
                }
            } catch (e: Exception) {
                _categorizeResult.value = "Error: ${e.localizedMessage}"
            } finally {
                _isCategorizing.value = false
            }
        }
    }
}
