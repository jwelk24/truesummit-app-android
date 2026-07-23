package com.truesummit.android.ui.transactions.editor

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.truesummit.android.data.AppDatabase
import com.truesummit.android.data.entity.AccountEntity
import com.truesummit.android.data.entity.CategoryEntity
import com.truesummit.android.data.entity.TransactionAttachmentEntity
import com.truesummit.android.data.entity.TransactionEntity
import com.truesummit.android.service.RuleEngine
import com.truesummit.android.service.SpendingTodayManager
import com.truesummit.android.data.entity.TransactionSplitEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.util.*

data class TransactionSplitDraft(
    val id: UUID = UUID.randomUUID(),
    var amount: String = "",
    var categoryId: UUID? = null,
    var memo: String = ""
)

class TransactionEditorViewModel(application: Application) : AndroidViewModel(application) {
    private val db = Room.databaseBuilder(
        application,
        AppDatabase::class.java, "truesummit-db"
    ).addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4).build()

    val accounts: StateFlow<List<AccountEntity>> = db.accountDao().getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categories: StateFlow<List<CategoryEntity>> = db.categoryDao().getCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _editingTransaction = MutableStateFlow<TransactionEntity?>(null)
    val editingTransaction: StateFlow<TransactionEntity?> = _editingTransaction

    private val _splits = MutableStateFlow<List<TransactionSplitDraft>>(emptyList())
    val splits: StateFlow<List<TransactionSplitDraft>> = _splits

    private val _pendingImages = MutableStateFlow<List<ByteArray>>(emptyList())
    val pendingImages: StateFlow<List<ByteArray>> = _pendingImages

    private val _removedAttachmentIds = MutableStateFlow<Set<UUID>>(emptySet())
    val removedAttachmentIds: StateFlow<Set<UUID>> = _removedAttachmentIds

    fun existingAttachments(transactionId: UUID): Flow<List<TransactionAttachmentEntity>> =
        db.transactionAttachmentDao().getForTransaction(transactionId)

    fun addPendingImage(bytes: ByteArray) {
        _pendingImages.value = _pendingImages.value + bytes
    }

    fun removePendingImage(index: Int) {
        val current = _pendingImages.value.toMutableList()
        if (index in current.indices) {
            current.removeAt(index)
            _pendingImages.value = current
        }
    }

    fun removeExistingAttachment(id: UUID) {
        _removedAttachmentIds.value = _removedAttachmentIds.value + id
    }

    fun loadTransaction(id: UUID) {
        viewModelScope.launch {
            val transaction = db.transactionDao().getById(id)
            _editingTransaction.value = transaction
            if (transaction != null) {
                val splits = db.transactionDao().getSplitsForTransaction(id)
                _splits.value = splits.map { 
                    TransactionSplitDraft(
                        id = it.id,
                        amount = it.amount.abs().toPlainString(),
                        categoryId = it.categoryId,
                        memo = it.memo ?: ""
                    )
                }
            }
        }
    }

    fun addSplit() {
        _splits.value = _splits.value + TransactionSplitDraft()
    }

    fun removeSplit(index: Int) {
        val current = _splits.value.toMutableList()
        if (index in current.indices) {
            current.removeAt(index)
            _splits.value = current
        }
    }

    fun updateSplit(index: Int, draft: TransactionSplitDraft) {
        val current = _splits.value.toMutableList()
        if (index in current.indices) {
            current[index] = draft
            _splits.value = current
        }
    }

    fun clearSplits() {
        _splits.value = emptyList()
    }

    fun saveTransaction(
        merchant: String,
        amount: String,
        isInflow: Boolean,
        date: Date,
        accountId: UUID?,
        categoryId: UUID?,
        memo: String?,
        cleared: Boolean,
        flagColor: String?,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            val magnitude = BigDecimal(amount.ifBlank { "0" })
            val signedAmount = if (isInflow) magnitude else magnitude.negate()
            
            val transactionId = _editingTransaction.value?.id ?: UUID.randomUUID()
            
            var finalCategoryId = if (_splits.value.isEmpty()) categoryId else null
            
            val tempTx = TransactionEntity(
                id = transactionId,
                merchant = merchant,
                amount = signedAmount,
                date = date,
                accountId = accountId,
                categoryId = finalCategoryId,
                pfcPrimary = null,
                memo = memo,
                cleared = cleared,
                flagColor = flagColor
            )

            if (finalCategoryId == null && _splits.value.isEmpty()) {
                val rules = db.categoryRuleDao().getEnabledRules()
                finalCategoryId = RuleEngine.applyRules(rules, tempTx, db)
            }

            val transaction = tempTx.copy(categoryId = finalCategoryId)
            
            db.transactionDao().insert(transaction)
            
            db.transactionDao().deleteSplitsForTransaction(transactionId)
            if (_splits.value.isNotEmpty()) {
                val splitEntities = _splits.value.map { draft ->
                    val splitMagnitude = BigDecimal(draft.amount.ifBlank { "0" })
                    val splitSignedAmount = if (isInflow) splitMagnitude else splitMagnitude.negate()
                    TransactionSplitEntity(
                        id = draft.id,
                        amount = splitSignedAmount,
                        memo = draft.memo.ifBlank { null },
                        transactionId = transactionId,
                        categoryId = draft.categoryId
                    )
                }
                db.transactionDao().insertSplits(splitEntities)
            }

            for (id in _removedAttachmentIds.value) {
                db.transactionAttachmentDao().deleteById(id)
            }
            for (bytes in _pendingImages.value) {
                db.transactionAttachmentDao().insert(
                    TransactionAttachmentEntity(transactionId = transactionId, imageData = bytes)
                )
            }
            _pendingImages.value = emptyList()
            _removedAttachmentIds.value = emptySet()

            SpendingTodayManager.startOrUpdate(getApplication())
            onSuccess()
        }
    }
}
