package com.truesummit.android.ui.networth.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.truesummit.android.billing.PremiumManager
import com.truesummit.android.billing.SubscriptionTier
import com.truesummit.android.data.AppDatabase
import com.truesummit.android.data.entity.AccountEntity
import com.truesummit.android.data.entity.BalanceSnapshotEntity
import com.truesummit.android.data.entity.InvestmentHoldingEntity
import com.truesummit.android.service.NetWorthMilestone
import com.truesummit.android.service.NetWorthProjectorService
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.*


enum class NetWorthTimeRange(val label: String, val days: Int?) {
    MONTH_1("1M", 30),
    MONTH_3("3M", 90),
    MONTH_6("6M", 180),
    YEAR_1("1Y", 365),
    ALL("ALL", null)
}

data class NetWorthUiState(
    val netWorth: BigDecimal = BigDecimal.ZERO,
    val totalAssets: BigDecimal = BigDecimal.ZERO,
    val totalLiabilities: BigDecimal = BigDecimal.ZERO,
    val assets: List<AccountEntity> = emptyList(),
    val liabilities: List<AccountEntity> = emptyList(),
    val holdings: List<InvestmentHoldingEntity> = emptyList(),
    val timeRange: NetWorthTimeRange = NetWorthTimeRange.MONTH_3,
    val chartPoints: List<BigDecimal> = emptyList(),
    val currentTier: SubscriptionTier = SubscriptionTier.NONE,
    val milestone: NetWorthMilestone? = null,
    val delta: BigDecimal? = null,
    val deltaPercent: Double? = null
)

class NetWorthViewModel(application: Application) : AndroidViewModel(application) {
    private val db = Room.databaseBuilder(
        application,
        AppDatabase::class.java, "truesummit-db"
    ).addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4).build()

    private val _timeRange = MutableStateFlow(NetWorthTimeRange.MONTH_3)

    val uiState: StateFlow<NetWorthUiState> = combine(
        db.accountDao().getAll(),
        db.investmentDao().getAllHoldings(),
        db.netWorthDao().getAllSnapshots(),
        combine(PremiumManager.currentTier, _timeRange) { tier, range -> tier to range }
    ) { accounts, holdings, snapshots, (tier, range) ->
        val assets = accounts.filter { it.type.isAsset }
        val liabilities = accounts.filter { !it.type.isAsset }

        val totalAssets = assets.fold(BigDecimal.ZERO) { acc, a -> acc.add(a.balance) }
        val totalLiabs = liabilities.fold(BigDecimal.ZERO) { acc, l -> acc.add(l.balance.abs()) }

        // Build net-worth history from balance snapshots, bucketed by day.
        val cutoff: Date? = range.days?.let { days ->
            Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -days) }.time
        }
        val filtered = if (cutoff != null) snapshots.filter { it.date.after(cutoff) } else snapshots
        val assetIds = assets.map { it.id }.toSet()
        val liabIds = liabilities.map { it.id }.toSet()

        val byDay = filtered.groupBy { snap ->
            val cal = Calendar.getInstance().apply { time = snap.date }
            Triple(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH))
        }
        val chartPoints = byDay.keys
            .sortedWith(compareBy({ it.first }, { it.second }, { it.third }))
            .map { key ->
            val daySnaps = byDay[key] ?: emptyList()
            val latestPerAccount = daySnaps.groupBy { it.accountId }
                .mapValues { (_, v) -> v.maxByOrNull { it.date }!! }
            val dayAssets = latestPerAccount.entries.filter { it.key in assetIds }
                .fold(BigDecimal.ZERO) { acc, e -> acc.add(e.value.balance) }
            val dayLiabs = latestPerAccount.entries.filter { it.key in liabIds }
                .fold(BigDecimal.ZERO) { acc, e -> acc.add(e.value.balance.abs()) }
            dayAssets.subtract(dayLiabs)
        }

        val netWorth = totalAssets.subtract(totalLiabs)
        // Estimate monthly change from the last 30 days of chart points
        val monthlyChange = if (chartPoints.size >= 2) {
            val recent = chartPoints.takeLast(30)
            recent.last().subtract(recent.first()).divide(BigDecimal(recent.size).max(BigDecimal.ONE), 2, RoundingMode.HALF_UP)
        } else BigDecimal.ZERO
        val milestone = if (netWorth > BigDecimal.ZERO || monthlyChange > BigDecimal.ZERO)
            NetWorthProjectorService.project(netWorth, monthlyChange)
        else null

        // Delta vs the start of the selected range, mirroring iOS's "vs Xd ago" chip.
        val delta = chartPoints.firstOrNull()?.let { past -> netWorth.subtract(past) }
        val deltaPercent = delta?.let { d ->
            val pastValue = chartPoints.first().abs()
            if (pastValue > BigDecimal("0.01")) d.toDouble() / pastValue.toDouble() * 100.0 else null
        }

        NetWorthUiState(
            netWorth = netWorth,
            totalAssets = totalAssets,
            totalLiabilities = totalLiabs,
            assets = assets,
            liabilities = liabilities,
            holdings = holdings,
            timeRange = range,
            chartPoints = chartPoints,
            currentTier = tier,
            milestone = milestone,
            delta = delta,
            deltaPercent = deltaPercent
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NetWorthUiState())

    fun setTimeRange(range: NetWorthTimeRange) {
        _timeRange.value = range
    }
}
