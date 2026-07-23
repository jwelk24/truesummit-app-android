package com.truesummit.android.ui.transactions

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.truesummit.android.data.AppDatabase
import com.truesummit.android.data.entity.AccountEntity
import com.truesummit.android.data.entity.CategoryEntity
import com.truesummit.android.data.entity.TransactionEntity
import com.truesummit.android.service.ReceiptScanner
import com.truesummit.android.service.ScannedReceipt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.util.*

enum class ScanPhase { PICK_PHOTO, SCANNING, REVIEW }

data class LineItemDraft(
    val id: UUID = UUID.randomUUID(),
    var name: String,
    var amount: BigDecimal,
    var categoryId: UUID?
)

class ReceiptScannerViewModel(application: Application) : AndroidViewModel(application) {
    private val db = Room.databaseBuilder(
        application,
        AppDatabase::class.java, "truesummit-db"
    ).addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4).build()

    private val _phase = MutableStateFlow(ScanPhase.PICK_PHOTO)
    val phase: StateFlow<ScanPhase> = _phase

    private val _merchant = MutableStateFlow("")
    val merchant: MutableStateFlow<String> = _merchant

    private val _transactionDate = MutableStateFlow(Date())
    val transactionDate: MutableStateFlow<Date> = _transactionDate

    private val _selectedAccountId = MutableStateFlow<UUID?>(null)
    val selectedAccountId: MutableStateFlow<UUID?> = _selectedAccountId

    private val _lineItems = MutableStateFlow<List<LineItemDraft>>(emptyList())
    val lineItems: StateFlow<List<LineItemDraft>> = _lineItems

    val accounts: StateFlow<List<AccountEntity>> = db.accountDao().getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categories: StateFlow<List<CategoryEntity>> = db.categoryDao().getCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun scanReceipt(bitmap: Bitmap) {
        viewModelScope.launch {
            _phase.value = ScanPhase.SCANNING
            try {
                val result = ReceiptScanner.scan(bitmap)
                applyDraft(result)
                _phase.value = ScanPhase.REVIEW
            } catch (e: Exception) {
                _phase.value = ScanPhase.PICK_PHOTO
                // Handle error
            }
        }
    }

    private fun applyDraft(draft: ScannedReceipt) {
        _merchant.value = draft.merchant ?: ""
        _transactionDate.value = draft.date ?: Date()
        
        val items = mutableListOf<LineItemDraft>()
        draft.amount?.let { amt ->
            items.add(LineItemDraft(name = "Total", amount = amt, categoryId = null))
        }
        
        _lineItems.value = items
        
        viewModelScope.launch {
            val allAccounts = accounts.first()
            _selectedAccountId.value = allAccounts.firstOrNull { it.type.isAsset }?.id ?: allAccounts.firstOrNull()?.id
        }
    }

    fun save(onSuccess: () -> Unit) {
        viewModelScope.launch {
            val accountId = _selectedAccountId.value ?: return@launch
            val total = _lineItems.value.fold(BigDecimal.ZERO) { acc, item -> acc.add(item.amount) }
            
            val transaction = TransactionEntity(
                date = _transactionDate.value,
                amount = total.negate(),
                merchant = _merchant.value,
                cleared = false,
                flagColor = null,
                accountId = accountId,
                categoryId = null,
                pfcPrimary = null,
                memo = "Scanned Receipt"
            )
            db.transactionDao().insert(transaction)
            
            onSuccess()
        }
    }
}
