package com.truesummit.android.ui.alerts

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.truesummit.android.billing.PremiumManager
import com.truesummit.android.billing.SubscriptionTier
import com.truesummit.android.data.AppDatabase
import com.truesummit.android.data.model.AccountType
import com.truesummit.android.service.CashFlowForecaster
import com.truesummit.android.service.CashFlowForecasterUtils
import com.truesummit.android.service.SmartAlertsService
import com.truesummit.android.service.SubscriptionTracker
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.math.BigDecimal

data class PriceChangeInfo(
    val merchant: String,
    val oldAmount: BigDecimal,
    val newAmount: BigDecimal,
    val isIncrease: Boolean
)

data class SmartAlertsUiState(
    val isPremium: Boolean = false,
    val budgetThresholdsEnabled: Boolean = false,
    val unusualActivityEnabled: Boolean = false,
    val billRemindersEnabled: Boolean = false,
    val billLeadDays: Int = 3,
    val lowBalanceEnabled: Boolean = false,
    val lowBalanceThreshold: BigDecimal = BigDecimal("100"),
    val priceChangeEnabled: Boolean = false,
    val projectedLowDate: java.util.Date? = null,
    val projectedLowBalance: BigDecimal? = null,
    val priceChanges: List<PriceChangeInfo> = emptyList()
)

class SmartAlertsViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs: Context get() = getApplication()
    private val db = Room.databaseBuilder(application, AppDatabase::class.java, "truesummit-db")
        .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4).build()

    private val _uiState = MutableStateFlow(loadState())
    val uiState: StateFlow<SmartAlertsUiState> = combine(
        PremiumManager.currentTier,
        _uiState
    ) { tier, state ->
        state.copy(isPremium = tier == SubscriptionTier.PREMIUM)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), _uiState.value)

    init { loadLiveData() }

    private fun loadLiveData() {
        viewModelScope.launch {
            val accounts = db.accountDao().getAll().first()
            val scheduled = db.scheduledItemDao().getAll().first()
            val transactions = db.transactionDao().getAll().first()

            // Low-balance projection
            val threshold = SmartAlertsService.getLowBalanceThreshold(prefs)
            val spendable = CashFlowForecasterUtils.spendableBalance(accounts)
            val forecast = CashFlowForecaster(spendable, scheduled, 30).project()
            val dip = forecast.points.firstOrNull { it.balance < threshold }

            // Price changes
            val changes = SubscriptionTracker.detectPriceChanges(transactions).map {
                PriceChangeInfo(it.merchant, it.oldAmount, it.newAmount, it.isIncrease)
            }

            _uiState.value = _uiState.value.copy(
                projectedLowDate = dip?.date,
                projectedLowBalance = dip?.balance,
                priceChanges = changes
            )
        }
    }

    private fun loadState() = SmartAlertsUiState(
        budgetThresholdsEnabled = SmartAlertsService.isBudgetEnabled(prefs),
        unusualActivityEnabled = SmartAlertsService.isUnusualEnabled(prefs),
        billRemindersEnabled = SmartAlertsService.isBillRemindersEnabled(prefs),
        billLeadDays = SmartAlertsService.getBillReminderLeadDays(prefs),
        lowBalanceEnabled = SmartAlertsService.isLowBalanceEnabled(prefs),
        lowBalanceThreshold = SmartAlertsService.getLowBalanceThreshold(prefs),
        priceChangeEnabled = SmartAlertsService.isPriceChangeEnabled(prefs)
    )

    fun toggleBudgetThresholds() {
        val new = !_uiState.value.budgetThresholdsEnabled
        SmartAlertsService.setBudgetEnabled(prefs, new)
        _uiState.value = _uiState.value.copy(budgetThresholdsEnabled = new)
    }

    fun toggleUnusualActivity() {
        val new = !_uiState.value.unusualActivityEnabled
        SmartAlertsService.setUnusualEnabled(prefs, new)
        _uiState.value = _uiState.value.copy(unusualActivityEnabled = new)
    }

    fun toggleBillReminders() {
        val new = !_uiState.value.billRemindersEnabled
        SmartAlertsService.setBillRemindersEnabled(prefs, new)
        _uiState.value = _uiState.value.copy(billRemindersEnabled = new)
    }

    fun setBillLeadDays(days: Int) {
        SmartAlertsService.setBillReminderLeadDays(prefs, days)
        _uiState.value = _uiState.value.copy(billLeadDays = days)
    }

    fun toggleLowBalance() {
        val new = !_uiState.value.lowBalanceEnabled
        SmartAlertsService.setLowBalanceEnabled(prefs, new)
        _uiState.value = _uiState.value.copy(lowBalanceEnabled = new)
    }

    fun setLowBalanceThreshold(threshold: BigDecimal) {
        SmartAlertsService.setLowBalanceThreshold(prefs, threshold)
        _uiState.value = _uiState.value.copy(lowBalanceThreshold = threshold)
    }

    fun togglePriceChange() {
        val new = !_uiState.value.priceChangeEnabled
        SmartAlertsService.setPriceChangeEnabled(prefs, new)
        _uiState.value = _uiState.value.copy(priceChangeEnabled = new)
    }

    fun sendTestNotification() {
        viewModelScope.launch {
            SmartAlertsService.sendTestNotification(prefs)
        }
    }
}
