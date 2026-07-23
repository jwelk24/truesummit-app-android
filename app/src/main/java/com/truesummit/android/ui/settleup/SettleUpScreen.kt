package com.truesummit.android.ui.settleup

import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DateFormat
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

data class SharedExpense(
    val id: UUID = UUID.randomUUID(),
    val title: String,
    val amount: BigDecimal,
    val date: Date,
    val paidByMe: Boolean,
    val payerShare: BigDecimal,
    val note: String?
)

data class SettlementRecord(
    val id: UUID = UUID.randomUUID(),
    val fromMe: Boolean,
    val amount: BigDecimal,
    val date: Date
)

object SettleUpStore {
    private const val PREFS = "settle_up"
    private lateinit var ctx: Context

    fun init(context: Context) { ctx = context.applicationContext }

    fun loadExpenses(): List<SharedExpense> {
        val raw = ctx.getSharedPreferences(PREFS, 0).getString("expenses", "[]") ?: "[]"
        val arr = JSONArray(raw)
        val result = mutableListOf<SharedExpense>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            result.add(SharedExpense(
                id = UUID.fromString(o.getString("id")),
                title = o.getString("title"),
                amount = BigDecimal(o.getString("amount")),
                date = Date(o.getLong("date")),
                paidByMe = o.getBoolean("paidByMe"),
                payerShare = BigDecimal(o.getString("payerShare")),
                note = if (o.has("note") && !o.isNull("note")) o.getString("note") else null
            ))
        }
        return result.sortedByDescending { it.date }
    }

    fun saveExpenses(list: List<SharedExpense>) {
        val arr = JSONArray()
        for (e in list) {
            val o = JSONObject()
            o.put("id", e.id.toString())
            o.put("title", e.title)
            o.put("amount", e.amount.toPlainString())
            o.put("date", e.date.time)
            o.put("paidByMe", e.paidByMe)
            o.put("payerShare", e.payerShare.toPlainString())
            if (e.note != null) o.put("note", e.note)
            arr.put(o)
        }
        ctx.getSharedPreferences(PREFS, 0).edit().putString("expenses", arr.toString()).apply()
    }

    fun loadSettlements(): List<SettlementRecord> {
        val raw = ctx.getSharedPreferences(PREFS, 0).getString("settlements", "[]") ?: "[]"
        val arr = JSONArray(raw)
        val result = mutableListOf<SettlementRecord>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            result.add(SettlementRecord(
                id = UUID.fromString(o.getString("id")),
                fromMe = o.getBoolean("fromMe"),
                amount = BigDecimal(o.getString("amount")),
                date = Date(o.getLong("date"))
            ))
        }
        return result.sortedByDescending { it.date }
    }

    fun saveSettlements(list: List<SettlementRecord>) {
        val arr = JSONArray()
        for (s in list) {
            val o = JSONObject()
            o.put("id", s.id.toString())
            o.put("fromMe", s.fromMe)
            o.put("amount", s.amount.toPlainString())
            o.put("date", s.date.time)
            arr.put(o)
        }
        ctx.getSharedPreferences(PREFS, 0).edit().putString("settlements", arr.toString()).apply()
    }

    fun partnerName(): String =
        ctx.getSharedPreferences(PREFS, 0).getString("partner_name", "Partner") ?: "Partner"

    fun setPartnerName(name: String) {
        ctx.getSharedPreferences(PREFS, 0).edit().putString("partner_name", name).apply()
    }

    /** Positive = partner owes you. Negative = you owe partner. */
    fun netBalance(expenses: List<SharedExpense>, settlements: List<SettlementRecord>): BigDecimal {
        var balance = BigDecimal.ZERO
        for (e in expenses) {
            val owedToPayer = e.amount.subtract(e.payerShare)
            if (e.paidByMe) balance = balance.add(owedToPayer)
            else balance = balance.subtract(owedToPayer)
        }
        for (s in settlements) {
            if (s.fromMe) balance = balance.add(s.amount)
            else balance = balance.subtract(s.amount)
        }
        return balance
    }
}

class SettleUpViewModel(application: Application) : AndroidViewModel(application) {
    init { SettleUpStore.init(application) }

    private val _refresh = MutableStateFlow(0)

    val expenses: List<SharedExpense>
        get() = SettleUpStore.loadExpenses()

    val settlements: List<SettlementRecord>
        get() = SettleUpStore.loadSettlements()

    val partnerName: String
        get() = SettleUpStore.partnerName()

    val balance: BigDecimal
        get() = SettleUpStore.netBalance(expenses, settlements)

    val refresh: StateFlow<Int> = _refresh.asStateFlow()

    fun addExpense(expense: SharedExpense) {
        val list = SettleUpStore.loadExpenses().toMutableList()
        list.add(0, expense)
        SettleUpStore.saveExpenses(list)
        _refresh.value++
    }

    fun settleUp() {
        val amt = balance.abs()
        if (amt <= BigDecimal.ZERO) return
        val record = SettlementRecord(fromMe = balance < BigDecimal.ZERO, amount = amt, date = Date())
        val list = SettleUpStore.loadSettlements().toMutableList()
        list.add(0, record)
        SettleUpStore.saveSettlements(list)
        _refresh.value++
    }

    fun setPartnerName(name: String) {
        SettleUpStore.setPartnerName(name)
        _refresh.value++
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettleUpScreen(
    onBack: () -> Unit,
    viewModel: SettleUpViewModel = viewModel()
) {
    val refresh by viewModel.refresh.collectAsState()
    var showAdd by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }

    val expenses = remember(refresh) { viewModel.expenses }
    val settlements = remember(refresh) { viewModel.settlements }
    val balance = remember(refresh) { viewModel.balance }
    val partnerName = remember(refresh) { viewModel.partnerName }

    val tint = when {
        balance > BigDecimal.ZERO -> Color(0xFF10B981)
        balance < BigDecimal.ZERO -> Color(0xFFF59E0B)
        else -> MaterialTheme.colorScheme.secondary
    }
    val balanceCaption = when {
        balance > BigDecimal.ZERO -> "$partnerName owes you"
        balance < BigDecimal.ZERO -> "You owe $partnerName"
        else -> "Settled up"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settle Up") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    IconButton(onClick = { showAdd = true }) { Icon(Icons.Default.Add, contentDescription = "Add expense") }
                    IconButton(onClick = { showRename = true }) { Icon(Icons.Default.Edit, contentDescription = "Set partner name") }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.People, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("You & $partnerName", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        }
                        Text(balanceCaption, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(currencyWhole(balance.abs()), style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold, color = tint)
                        if (balance != BigDecimal.ZERO) {
                            Button(onClick = { viewModel.settleUp() }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Settle Up")
                            }
                        } else {
                            Text("You're all settled up.", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            if (expenses.isEmpty() && settlements.isEmpty()) {
                item {
                    Text("No shared expenses yet. Tap + to add one.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp))
                }
            } else {
                item {
                    Text("Activity", style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary)
                }

                val activity = buildList {
                    expenses.forEach { e ->
                        add(Triple(e.date, e.title, if (e.paidByMe) "You paid" else "$partnerName paid") to e.amount)
                    }
                    settlements.forEach { s ->
                        add(Triple(s.date, "Settlement", if (s.fromMe) "You → $partnerName" else "$partnerName → You") to s.amount)
                    }
                }.sortedByDescending { it.first.first }

                items(activity) { (meta, amount) ->
                    val (date, title, who) = meta
                    val df = SimpleDateFormat("MMM d", Locale.getDefault())
                    ListItem(
                        headlineContent = { Text(title) },
                        supportingContent = { Text("$who · ${df.format(date)}") },
                        trailingContent = { Text(currencyWhole(amount), fontWeight = FontWeight.SemiBold) }
                    )
                }
            }
        }
    }

    if (showAdd) {
        AddSharedExpenseDialog(
            partnerName = partnerName,
            onAdd = { expense ->
                viewModel.addExpense(expense)
                showAdd = false
            },
            onDismiss = { showAdd = false }
        )
    }

    if (showRename) {
        RenamePartnerDialog(
            current = partnerName,
            onSave = { name ->
                viewModel.setPartnerName(name)
                showRename = false
            },
            onDismiss = { showRename = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddSharedExpenseDialog(
    partnerName: String,
    onAdd: (SharedExpense) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var paidByMe by remember { mutableStateOf(true) }
    var splitEvenly by remember { mutableStateOf(true) }
    var payerShareText by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    val amount = amountText.toBigDecimalOrNull() ?: BigDecimal.ZERO

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Shared Expense") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it },
                    label = { Text("What was it for?") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = amountText, onValueChange = { amountText = it },
                    label = { Text("Amount") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                Text("Paid by", style = MaterialTheme.typography.labelMedium)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(selected = paidByMe, onClick = { paidByMe = true },
                        shape = SegmentedButtonDefaults.itemShape(0, 2), label = { Text("You") })
                    SegmentedButton(selected = !paidByMe, onClick = { paidByMe = false },
                        shape = SegmentedButtonDefaults.itemShape(1, 2), label = { Text(partnerName) })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = splitEvenly, onCheckedChange = { splitEvenly = it })
                    Text("Split evenly (50/50)", style = MaterialTheme.typography.bodyMedium)
                }
                if (!splitEvenly) {
                    OutlinedTextField(value = payerShareText, onValueChange = { payerShareText = it },
                        label = { Text("Payer's share") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                }
                OutlinedTextField(value = note, onValueChange = { note = it },
                    label = { Text("Note (optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val payerShare = if (splitEvenly)
                        amount.divide(BigDecimal(2), 2, RoundingMode.HALF_UP)
                    else
                        payerShareText.toBigDecimalOrNull() ?: amount.divide(BigDecimal(2), 2, RoundingMode.HALF_UP)
                    onAdd(SharedExpense(
                        title = title.trim(),
                        amount = amount,
                        date = Date(),
                        paidByMe = paidByMe,
                        payerShare = payerShare,
                        note = note.ifBlank { null }
                    ))
                },
                enabled = amount > BigDecimal.ZERO && title.isNotBlank()
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun RenamePartnerDialog(current: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Partner's Name") },
        text = {
            OutlinedTextField(value = name, onValueChange = { name = it },
                label = { Text("Name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        },
        confirmButton = {
            TextButton(onClick = { onSave(name.ifBlank { "Partner" }) },
                enabled = name.isNotBlank()) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun currencyWhole(d: BigDecimal): String {
    val fmt = NumberFormat.getCurrencyInstance(Locale.US)
    fmt.maximumFractionDigits = 0
    return fmt.format(d)
}
