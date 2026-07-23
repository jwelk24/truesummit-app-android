package com.truesummit.android.ui.rules

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.truesummit.android.data.AppDatabase
import com.truesummit.android.data.entity.CategoryEntity
import com.truesummit.android.data.entity.CategoryRuleEntity
import com.truesummit.android.service.RuleEngine
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*

class CategoryRulesViewModel(application: Application) : AndroidViewModel(application) {
    private val db = Room.databaseBuilder(
        application,
        AppDatabase::class.java, "truesummit-db"
    ).addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4).build()

    val rules: StateFlow<List<CategoryRuleEntity>> = db.categoryRuleDao().getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categories: StateFlow<List<CategoryEntity>> = db.categoryDao().getCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _backfillMessage = MutableStateFlow<String?>(null)
    val backfillMessage: StateFlow<String?> = _backfillMessage

    fun deleteRule(rule: CategoryRuleEntity) {
        viewModelScope.launch {
            db.categoryRuleDao().delete(rule)
        }
    }

    fun runBackfill() {
        viewModelScope.launch {
            val hits = RuleEngine.backfill(getApplication())
            _backfillMessage.value = if (hits == 0) {
                "No uncategorized transactions matched any rule."
            } else {
                "Categorized $hits transaction${if (hits == 1) "" else "s"}."
            }
        }
    }

    fun saveRule(
        id: UUID?,
        priority: Int,
        matchField: String,
        matchKind: String,
        pattern: String,
        caseSensitive: Boolean,
        enabled: Boolean,
        categoryId: UUID?
    ) {
        viewModelScope.launch {
            val rule = CategoryRuleEntity(
                id = id ?: UUID.randomUUID(),
                priority = priority,
                matchField = matchField,
                matchKind = matchKind,
                pattern = pattern,
                caseSensitive = caseSensitive,
                enabled = enabled,
                categoryId = categoryId
            )
            db.categoryRuleDao().insert(rule)
        }
    }

    fun clearBackfillMessage() {
        _backfillMessage.value = null
    }
}
