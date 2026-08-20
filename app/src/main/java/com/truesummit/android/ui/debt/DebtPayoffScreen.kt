package com.truesummit.android.ui.debt

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.truesummit.android.data.AppDatabase
import com.truesummit.android.data.model.AccountType
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.*

enum class PayoffStrategy(val title: String, val subtitle: String) {
    AVALANCHE("Avalanche", "Highest interest rate first — pays the least total interest."),
    SNOWBALL("Snowball", "Smallest balance first — clears individual debts soonest.")
}

data class DebtInput(
    val id: UUID,
    var name: String,
    var balance: BigDecimal,
    var aprPercent: BigDecimal,
    var minimumPayment: BigDecimal
)

data class PayoffOrderEntry(val id: UUID, val name: String, val monthsToPayoff: Int, val interestPaid: BigDecimal)

data class PayoffSchedulePoint(val id: Int, val monthOffset: Int, val remaining: BigDecimal)

data class DebtPayoffResult(
    val months: Int,
    val totalInterest: BigDecimal,
    val totalPaid: BigDecimal,
    val order: List<PayoffOrderEntry>,
    val schedule: List<PayoffSchedulePoint>,
    val insufficient: Boolean
)

object DebtPayoffEngine {
    fun plan(debts: List<DebtInput>, strategy: PayoffStrategy, extraMonthly: BigDecimal): DebtPayoffResult {
        data class Working(
            val id: UUID, val name: String, var balance: Double,
            val monthlyRate: Double, val minimum: Double,
            var interestPaid: Double = 0.0, var paidMonth: Int? = null
        )

        val working = debts.mapNotNull { d ->
            val balance = d.balance.toDouble()
            if (balance <= 0.005) return@mapNotNull null
            val rate = d.aprPercent.toDouble() / 100.0 / 12.0
            val min = maxOf(0.0, d.minimumPayment.toDouble())
            Working(d.id, d.name, balance, rate, min)
        }.toMutableList()

        if (working.isEmpty()) return DebtPayoffResult(0, BigDecimal.ZERO, BigDecimal.ZERO, emptyList(), emptyList(), false)

        val totalMin = working.sumOf { it.minimum }
        val extra = maxOf(0.0, extraMonthly.toDouble())
        val pool = totalMin + extra

        fun priorityOrder(): List<Int> {
            val owing = working.indices.filter { working[it].balance > 0.005 }
            return when (strategy) {
                PayoffStrategy.AVALANCHE -> owing.sortedByDescending { working[it].monthlyRate }
                PayoffStrategy.SNOWBALL -> owing.sortedBy { working[it].balance }
            }
        }

        val startRemaining = working.sumOf { it.balance }
        val schedule = mutableListOf(PayoffSchedulePoint(0, 0, BigDecimal.valueOf(startRemaining)))
        var month = 0
        var totalInterest = 0.0
        var totalPaid = 0.0
        var insufficient = false

        while (working.any { it.balance > 0.005 }) {
            month++
            if (month > 600) { insufficient = true; break }

            var interestThisMonth = 0.0
            for (i in working.indices) {
                if (working[i].balance <= 0.005) continue
                val interest = working[i].balance * working[i].monthlyRate
                working[i].balance += interest
                working[i].interestPaid += interest
                interestThisMonth += interest
            }
            if (pool <= interestThisMonth + 0.001) { insufficient = true; break }

            var available = pool
            for (i in working.indices) {
                if (working[i].balance <= 0.005) continue
                val pay = minOf(working[i].minimum, working[i].balance, available)
                working[i].balance -= pay; available -= pay; totalPaid += pay
            }
            for (idx in priorityOrder()) {
                if (available <= 0.005) break
                if (working[idx].balance <= 0.005) continue
                val pay = minOf(available, working[idx].balance)
                working[idx].balance -= pay; available -= pay; totalPaid += pay
            }
            totalInterest += interestThisMonth

            for (i in working.indices) {
                if (working[i].paidMonth == null && working[i].balance <= 0.005) {
                    working[i].balance = 0.0; working[i].paidMonth = month
                }
            }
            val remaining = working.sumOf { maxOf(0.0, it.balance) }
            schedule.add(PayoffSchedulePoint(month, month, BigDecimal.valueOf(remaining)))
        }

        val order = working.sortedBy { it.paidMonth ?: Int.MAX_VALUE }.map {
            PayoffOrderEntry(it.id, it.name, it.paidMonth ?: month, BigDecimal.valueOf(it.interestPaid))
        }

        return DebtPayoffResult(
            months = month,
            totalInterest = BigDecimal.valueOf(totalInterest),
            totalPaid = BigDecimal.valueOf(totalPaid),
            order = order,
            schedule = schedule,
            insufficient = insufficient
        )
    }
}

class DebtPayoffViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getInstance(application)

    var strategy by mutableStateOf(PayoffStrategy.AVALANCHE)
    var extraText by mutableStateOf("")
    var debts by mutableStateOf<List<DebtInput>>(emptyList())
    var seeded by mutableStateOf(false)

    val extra: BigDecimal get() = extraText.toBigDecimalOrNull() ?: BigDecimal.ZERO

    val result: DebtPayoffResult
        get() = DebtPayoffEngine.plan(debts, strategy, extra)

    init {
        viewModelScope.launch {
            combine(db.accountDao().getAll(), db.liabilityDao().getAll()) { accounts, liabilities ->
                if (seeded) return@combine
                val liabByAccount = liabilities.associateBy { it.accountId }
                debts = accounts
                    .filter { it.type == AccountType.CREDIT_CARD || it.type == AccountType.LOAN }
                    .mapNotNull { account ->
                        val balance = if (account.balance < BigDecimal.ZERO) account.balance.negate() else account.balance
                        if (balance <= BigDecimal.ZERO) return@mapNotNull null
                        val liab = liabByAccount[account.id]
                        val minPay = liab?.minimumPayment ?: defaultMinimum(balance)
                        DebtInput(
                            id = account.id,
                            name = account.name,
                            balance = balance,
                            aprPercent = liab?.interestRatePercentage ?: BigDecimal.ZERO,
                            minimumPayment = minPay
                        )
                    }
                    .sortedBy { it.balance }
                seeded = true
            }.collect()
        }
    }

    fun updateDebt(index: Int, newDebt: DebtInput) {
        val list = debts.toMutableList()
        list[index] = newDebt
        debts = list
    }

    private fun defaultMinimum(balance: BigDecimal): BigDecimal {
        val twoPct = balance.multiply(BigDecimal("0.02"))
        return twoPct.max(BigDecimal("25"))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebtPayoffScreen(
    onBack: () -> Unit,
    viewModel: DebtPayoffViewModel = viewModel()
) {
    val result = viewModel.result

    val debtFreeDate: Date? = if (result.months > 0 && !result.insufficient) {
        val cal = Calendar.getInstance()
        cal.add(Calendar.MONTH, result.months)
        cal.time
    } else null

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Debt Payoff") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (viewModel.debts.isEmpty()) {
            Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.CreditCard, contentDescription = null, modifier = Modifier.size(48.dp))
                    Text("No Debts to Plan", style = MaterialTheme.typography.titleMedium)
                    Text("Add a credit card or loan account and TrueSummit will build a payoff plan.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 32.dp))
                }
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Strategy
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Method", style = MaterialTheme.typography.titleSmall)
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            PayoffStrategy.values().forEachIndexed { index, s ->
                                SegmentedButton(
                                    selected = viewModel.strategy == s,
                                    onClick = { viewModel.strategy = s },
                                    shape = SegmentedButtonDefaults.itemShape(index, 2),
                                    label = { Text(s.title) }
                                )
                            }
                        }
                        Text(viewModel.strategy.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // Extra payment
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Extra per month", style = MaterialTheme.typography.bodyMedium)
                            Text("Rolled onto the next debt as each is cleared.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        OutlinedTextField(
                            value = viewModel.extraText,
                            onValueChange = { viewModel.extraText = it },
                            modifier = Modifier.width(100.dp),
                            placeholder = { Text("$0") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                        )
                    }
                }
            }

            // Summary
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Plan", style = MaterialTheme.typography.titleSmall)
                        if (result.insufficient) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.Warning, contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error)
                                Text("Your minimums plus extra don't cover the interest yet. Add more to start making progress.",
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        } else {
                            if (debtFreeDate != null) {
                                val cal = Calendar.getInstance(); cal.time = debtFreeDate
                                val label = "${cal.getDisplayName(Calendar.MONTH, Calendar.SHORT, Locale.getDefault())} ${cal.get(Calendar.YEAR)}"
                                SummaryRow("Debt-free by", label)
                            }
                            SummaryRow("Time to payoff", monthsLabel(result.months))
                            SummaryRow("Total interest", currencyWhole(result.totalInterest))
                            SummaryRow("Total paid", currencyWhole(result.totalPaid))
                        }
                    }
                }
            }

            // Debts
            item {
                Text("Debts", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            }
            itemsIndexed(viewModel.debts) { index, debt ->
                var balanceText by remember(debt.id) { mutableStateOf(debt.balance.toPlainString()) }
                var aprText by remember(debt.id) { mutableStateOf(debt.aprPercent.toPlainString()) }
                var minText by remember(debt.id) { mutableStateOf(debt.minimumPayment.toPlainString()) }

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(debt.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            LabeledDebtField("Balance", balanceText, modifier = Modifier.weight(1f)) { v ->
                                balanceText = v
                                v.toBigDecimalOrNull()?.let { viewModel.updateDebt(index, debt.copy(balance = it)) }
                            }
                            LabeledDebtField("APR %", aprText, modifier = Modifier.weight(1f)) { v ->
                                aprText = v
                                v.toBigDecimalOrNull()?.let { viewModel.updateDebt(index, debt.copy(aprPercent = it)) }
                            }
                            LabeledDebtField("Min", minText, modifier = Modifier.weight(1f)) { v ->
                                minText = v
                                v.toBigDecimalOrNull()?.let { viewModel.updateDebt(index, debt.copy(minimumPayment = it)) }
                            }
                        }
                    }
                }
            }

            // Payoff order
            if (!result.insufficient && result.order.isNotEmpty()) {
                item {
                    Text("Payoff Order", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                }
                itemsIndexed(result.order) { index, entry ->
                    ListItem(
                        headlineContent = { Text(entry.name) },
                        supportingContent = {
                            Text("Paid off in ${monthsLabel(entry.monthsToPayoff)} · ${currencyWhole(entry.interestPaid)} interest",
                                style = MaterialTheme.typography.bodySmall)
                        },
                        leadingContent = {
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                    Text("${index + 1}", style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun LabeledDebtField(label: String, value: String, modifier: Modifier, onValueChange: (String) -> Unit) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private fun monthsLabel(months: Int): String {
    if (months <= 0) return "—"
    val years = months / 12; val rem = months % 12
    return when {
        years == 0 -> "$rem mo"
        rem == 0 -> "$years yr"
        else -> "$years yr $rem mo"
    }
}

private fun currencyWhole(d: BigDecimal): String {
    val fmt = NumberFormat.getCurrencyInstance(Locale.US)
    fmt.maximumFractionDigits = 0
    return fmt.format(d)
}
