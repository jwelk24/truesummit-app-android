package com.truesummit.android.ui.onboarding

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.room.Room
import com.truesummit.android.data.AppDatabase
import com.truesummit.android.data.entity.AccountEntity
import com.truesummit.android.data.entity.BudgetAllocationEntity
import com.truesummit.android.data.entity.BudgetMonthEntity
import com.truesummit.android.data.entity.CategoryEntity
import com.truesummit.android.data.entity.CategoryGroupEntity
import com.truesummit.android.data.entity.TransactionEntity
import com.truesummit.android.data.model.AccountType
import com.truesummit.android.service.BudgetEngine
import com.truesummit.android.ui.theme.SummitColors
import com.truesummit.android.ui.transactions.formatCurrency
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.util.*

// ---------------------------------------------------------------------------
// ViewModel
// ---------------------------------------------------------------------------

class OnboardingWizardViewModel(application: Application) : AndroidViewModel(application) {
    private val db by lazy {
        Room.databaseBuilder(application, AppDatabase::class.java, "truesummit-db")
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4)
            .build()
    }

    fun seedSampleData() {
        viewModelScope.launch {
            BudgetEngine(getApplication()).seedIfNeeded()
        }
    }

    fun commitSetup(
        displayName: String,
        accounts: List<DraftAccount>,
        chosenCategories: List<StarterCategory>,
        assignments: Map<UUID, BigDecimal>,
        onDone: () -> Unit
    ) {
        viewModelScope.launch {
            val cal = Calendar.getInstance()
            val year = cal.get(Calendar.YEAR)
            val month = cal.get(Calendar.MONTH) + 1

            for (draft in accounts) {
                val name = draft.name.trim()
                if (name.isEmpty()) continue
                val account = AccountEntity(name = name, type = draft.type, balance = draft.balance)
                db.accountDao().insert(account)
                if (draft.type.isBudgetableCash && draft.balance > BigDecimal.ZERO) {
                    db.transactionDao().insert(
                        TransactionEntity(
                            date = Date(),
                            amount = draft.balance,
                            merchant = "Starting Balance",
                            memo = null,
                            cleared = true,
                            flagColor = null,
                            pfcPrimary = "Income",
                            accountId = account.id,
                            categoryId = null
                        )
                    )
                }
            }

            val groupOrder = listOf(
                StarterCategory.NEEDS, StarterCategory.WANTS, StarterCategory.SAVINGS, StarterCategory.CUSTOM
            )
            var groupSort = 0
            val categoryIdMap = mutableMapOf<UUID, UUID>()
            for (groupName in groupOrder) {
                val items = chosenCategories.filter { it.group == groupName }
                if (items.isEmpty()) continue
                val group = CategoryGroupEntity(name = groupName, sort = groupSort++)
                db.categoryDao().insertGroup(group)
                items.forEachIndexed { i, starter ->
                    val cat = CategoryEntity(name = starter.name, sort = i, groupId = group.id, linkedAccountId = null)
                    db.categoryDao().insertCategory(cat)
                    categoryIdMap[starter.id] = cat.id
                }
            }

            var budgetMonth = db.budgetDao().getMonth(year, month)
            if (budgetMonth == null) {
                budgetMonth = BudgetMonthEntity(year = year, month = month, carryover = BigDecimal.ZERO)
                db.budgetDao().insertMonth(budgetMonth)
            }
            for ((starterId, amount) in assignments) {
                if (amount <= BigDecimal.ZERO) continue
                val catId = categoryIdMap[starterId] ?: continue
                db.budgetDao().insertAllocation(
                    BudgetAllocationEntity(amount = amount, categoryId = catId, monthId = budgetMonth.id)
                )
            }

            if (displayName.isNotBlank()) OnboardingState.userDisplayName = displayName
            OnboardingState.hasCompletedWelcome = true
            onDone()
        }
    }
}

// ---------------------------------------------------------------------------
// Draft models
// ---------------------------------------------------------------------------

data class DraftAccount(
    val id: UUID = UUID.randomUUID(),
    val name: String = "",
    val type: AccountType = AccountType.CHECKING,
    val balanceText: String = ""
) {
    val balance: BigDecimal get() = balanceText.toBigDecimalOrNull() ?: BigDecimal.ZERO
}

val AccountType.isBudgetableCash: Boolean
    get() = this == AccountType.CHECKING || this == AccountType.SAVINGS

data class StarterCategory(
    val id: UUID = UUID.randomUUID(),
    val name: String,
    val group: String
) {
    companion object {
        const val NEEDS = "Needs (Fixed Expenses)"
        const val WANTS = "Wants (Flexible Expenses)"
        const val SAVINGS = "Savings & Debt"
        const val CUSTOM = "My Categories"

        val all = listOf(
            StarterCategory(name = "Housing", group = NEEDS),
            StarterCategory(name = "Utilities", group = NEEDS),
            StarterCategory(name = "Groceries", group = NEEDS),
            StarterCategory(name = "Transportation", group = NEEDS),
            StarterCategory(name = "Insurance", group = NEEDS),
            StarterCategory(name = "Dining Out & Entertainment", group = WANTS),
            StarterCategory(name = "Subscriptions", group = WANTS),
            StarterCategory(name = "Personal Care & Clothing", group = WANTS),
            StarterCategory(name = "Vacation & Travel", group = WANTS),
            StarterCategory(name = "Gifts & Donations", group = WANTS),
            StarterCategory(name = "Debt Repayment", group = SAVINGS),
            StarterCategory(name = "Savings & Investments", group = SAVINGS),
        )
    }
}

// ---------------------------------------------------------------------------
// Screen
// ---------------------------------------------------------------------------

@Composable
fun OnboardingWelcomeScreen(
    onFinish: () -> Unit,
    onConnectBank: () -> Unit
) {
    val vm: OnboardingWizardViewModel = viewModel()
    var step by remember { mutableIntStateOf(0) }
    val lastStep = 5

    var displayName by remember { mutableStateOf(OnboardingState.userDisplayName) }
    var accounts by remember { mutableStateOf(listOf(DraftAccount())) }
    var chosenIds by remember { mutableStateOf(StarterCategory.all.map { it.id }.toSet()) }
    var customCategories by remember { mutableStateOf(listOf<StarterCategory>()) }
    var newCategoryName by remember { mutableStateOf("") }
    var assignments by remember { mutableStateOf(mapOf<UUID, BigDecimal>()) }

    val allCategories = StarterCategory.all + customCategories
    val chosenCategories = allCategories.filter { it.id in chosenIds }
    val readyToAssign = accounts
        .filter { it.type.isBudgetableCash && it.balance > BigDecimal.ZERO }
        .fold(BigDecimal.ZERO) { acc, a -> acc + a.balance }
    val assignedTotal = chosenCategories.fold(BigDecimal.ZERO) { acc, c -> acc + (assignments[c.id] ?: BigDecimal.ZERO) }
    val leftToAssign = readyToAssign - assignedTotal

    val canAdvance = when (step) {
        1 -> accounts.any { it.name.trim().isNotEmpty() }
        3 -> chosenIds.isNotEmpty()
        else -> true
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Progress header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (step > 0 && step <= lastStep) {
                TextButton(onClick = { step-- }) { Text("Back") }
            } else {
                Spacer(Modifier.width(64.dp))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                repeat(lastStep + 1) { i ->
                    Box(
                        modifier = Modifier
                            .height(6.dp)
                            .width(if (i == step) 18.dp else 6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (i == step) SummitColors.Teal else MaterialTheme.colorScheme.outlineVariant)
                    )
                }
            }

            if (step == 0) {
                TextButton(onClick = {
                    vm.seedSampleData()
                    OnboardingState.hasCompletedWelcome = true
                    onFinish()
                }) { Text("Skip", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                Spacer(Modifier.width(64.dp))
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (step) {
                0 -> WelcomeStep(name = displayName, onNameChange = { displayName = it })
                1 -> AccountsStep(accounts = accounts, readyToAssign = readyToAssign, onAccountsChange = { accounts = it })
                2 -> ReadyToAssignStep(readyToAssign = readyToAssign)
                3 -> CategoriesStep(
                    allCategories = allCategories,
                    chosenIds = chosenIds,
                    newCategoryName = newCategoryName,
                    onToggle = { id -> chosenIds = if (id in chosenIds) chosenIds - id else chosenIds + id },
                    onNewCategoryNameChange = { newCategoryName = it },
                    onAddCustom = {
                        val name = newCategoryName.trim()
                        if (name.isNotEmpty()) {
                            val cat = StarterCategory(name = name, group = StarterCategory.CUSTOM)
                            customCategories = customCategories + cat
                            chosenIds = chosenIds + cat.id
                            newCategoryName = ""
                        }
                    }
                )
                4 -> AssignStep(
                    chosenCategories = chosenCategories,
                    assignments = assignments,
                    leftToAssign = leftToAssign,
                    onAssign = { id, amount -> assignments = assignments + (id to amount) }
                )
                else -> DoneStep()
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (step == lastStep) {
                OutlinedButton(
                    onClick = {
                        vm.commitSetup(displayName, accounts, chosenCategories, assignments) { onConnectBank() }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.AccountBalance, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Connect a Bank")
                }
            }
            Button(
                onClick = {
                    if (step < lastStep) step++
                    else vm.commitSetup(displayName, accounts, chosenCategories, assignments) { onFinish() }
                },
                enabled = canAdvance,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(when (step) { 4 -> "Review Budget"; lastStep -> "Start Budgeting"; else -> "Continue" })
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Steps
// ---------------------------------------------------------------------------

@Composable
private fun WelcomeStep(name: String, onNameChange: (String) -> Unit) {
    WizardPage(icon = "⛰️", title = "Welcome to TrueSummit", subtitle = "Let's build your budget together — one step at a time.") {
        Text("What should we call you?", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        OutlinedTextField(value = name, onValueChange = onNameChange, placeholder = { Text("Your name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Text("We'll use it to greet you. You can change it anytime in Settings.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        WizardFeatureRow(Icons.Default.List, "Give every dollar a job", "Assign the money you have to categories so you always know what's safe to spend.")
        WizardFeatureRow(Icons.Default.TouchApp, "Built from your real numbers", "We'll set up your accounts and budget with your money — no sample data to clean up.")
        WizardFeatureRow(Icons.Default.Lock, "Private by default", "Everything stays on this device unless you sign in to sync.")
    }
}

@Composable
private fun AccountsStep(accounts: List<DraftAccount>, readyToAssign: BigDecimal, onAccountsChange: (List<DraftAccount>) -> Unit) {
    WizardPage(icon = "🏦", title = "Add Your Accounts", subtitle = "Start with the accounts you spend and save from. You can add more anytime.") {
        accounts.forEachIndexed { index, account ->
            DraftAccountRow(
                account = account,
                canRemove = accounts.size > 1,
                onRemove = { onAccountsChange(accounts.filter { it.id != account.id }) },
                onChange = { updated -> onAccountsChange(accounts.toMutableList().also { it[index] = updated }) }
            )
            Spacer(Modifier.height(8.dp))
        }
        TextButton(onClick = { onAccountsChange(accounts + DraftAccount()) }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.AddCircle, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Add another account")
        }
        if (readyToAssign > BigDecimal.ZERO) {
            Text("Cash on hand: ${formatCurrency(readyToAssign.toDouble())}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun ReadyToAssignStep(readyToAssign: BigDecimal) {
    WizardPage(icon = "📥", title = "Ready to Assign", subtitle = "This is the money in your cash accounts waiting for a job.") {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(formatCurrency(readyToAssign.toDouble()), fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 44.sp, color = SummitColors.Teal)
                Text(
                    if (readyToAssign > BigDecimal.ZERO) "Next, you'll give every dollar a job."
                    else "You can still set up categories and assign money as it comes in.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        WizardFeatureRow(Icons.Default.CheckCircle, "The goal is zero", "When every dollar is assigned, you'll see \"Every dollar has a job\" on your budget.")
        WizardFeatureRow(Icons.Default.SwapHoriz, "Nothing is locked in", "Move money between categories whenever your plans change.")
    }
}

@Composable
private fun CategoriesStep(
    allCategories: List<StarterCategory>,
    chosenIds: Set<UUID>,
    newCategoryName: String,
    onToggle: (UUID) -> Unit,
    onNewCategoryNameChange: (String) -> Unit,
    onAddCustom: () -> Unit
) {
    val groups = listOf(StarterCategory.NEEDS, StarterCategory.WANTS, StarterCategory.SAVINGS, StarterCategory.CUSTOM)
    WizardPage(icon = "📂", title = "Pick Your Categories", subtitle = "Keep the ones that fit your life. Uncheck what you don't need.") {
        groups.forEach { groupName ->
            val items = allCategories.filter { it.group == groupName }
            if (items.isNotEmpty()) {
                Text(groupName.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 1.sp, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                items.forEach { cat ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onToggle(cat.id) }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            if (cat.id in chosenIds) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                            contentDescription = null,
                            tint = if (cat.id in chosenIds) SummitColors.Teal else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(cat.name, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = newCategoryName, onValueChange = onNewCategoryNameChange, placeholder = { Text("Add a category") }, singleLine = true, modifier = Modifier.weight(1f))
            IconButton(onClick = onAddCustom, enabled = newCategoryName.trim().isNotEmpty()) {
                Icon(Icons.Default.AddCircle, contentDescription = "Add", tint = SummitColors.Teal)
            }
        }
    }
}

@Composable
private fun AssignStep(chosenCategories: List<StarterCategory>, assignments: Map<UUID, BigDecimal>, leftToAssign: BigDecimal, onAssign: (UUID, BigDecimal) -> Unit) {
    WizardPage(icon = "💵", title = "Give Every Dollar a Job", subtitle = "Assign your money across categories. It's fine to leave some for later.") {
        val bannerColor = if (leftToAssign < BigDecimal.ZERO) SummitColors.Rose else SummitColors.Teal
        val bannerLabel = when {
            leftToAssign > BigDecimal.ZERO -> "LEFT TO ASSIGN"
            leftToAssign < BigDecimal.ZERO -> "OVER-ASSIGNED"
            else -> "EVERY DOLLAR HAS A JOB"
        }
        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(bannerColor.copy(alpha = 0.1f)).border(1.dp, bannerColor.copy(alpha = 0.25f), RoundedCornerShape(20.dp)).padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (leftToAssign < BigDecimal.ZERO) Icons.Default.Warning else if (leftToAssign == BigDecimal.ZERO) Icons.Default.CheckCircle else Icons.Default.ArrowDownward,
                contentDescription = null, tint = bannerColor,
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(bannerColor.copy(alpha = 0.15f)).padding(10.dp)
            )
            Column {
                Text(bannerLabel, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = bannerColor, letterSpacing = 1.1.sp)
                if (leftToAssign != BigDecimal.ZERO) {
                    Text(formatCurrency(leftToAssign.abs().toDouble()), fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        chosenCategories.forEach { cat ->
            val current = assignments[cat.id] ?: BigDecimal.ZERO
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(cat.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = if (current == BigDecimal.ZERO) "" else current.toPlainString(),
                    onValueChange = { onAssign(cat.id, it.toBigDecimalOrNull() ?: BigDecimal.ZERO) },
                    prefix = { Text("$") }, placeholder = { Text("0") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.width(130.dp)
                )
            }
        }
    }
}

@Composable
private fun DoneStep() {
    WizardPage(icon = "🏁", title = "You're All Set", subtitle = "Your budget is ready. Here are a couple of ways to make it even better.") {
        WizardFeatureRow(Icons.Default.AccountBalance, "Connect your bank", "Transactions and balances import automatically once linked.")
        WizardFeatureRow(Icons.Default.Checklist, "A checklist has your back", "The Getting Started checklist on the Budget tab covers the rest.")
        WizardFeatureRow(Icons.Default.Flag, "Set your peaks", "Visit Your Peaks to create savings goals and track progress.")
    }
}

// ---------------------------------------------------------------------------
// Reusable components
// ---------------------------------------------------------------------------

@Composable
private fun WizardPage(icon: String, title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        Text(icon, fontSize = 56.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(4.dp))
        content()
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun WizardFeatureRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, detail: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
        Icon(icon, contentDescription = null, tint = SummitColors.Teal,
            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(SummitColors.Teal.copy(alpha = 0.1f)).padding(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DraftAccountRow(account: DraftAccount, canRemove: Boolean, onRemove: () -> Unit, onChange: (DraftAccount) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = account.name, onValueChange = { onChange(account.copy(name = it)) }, placeholder = { Text("Account name") }, singleLine = true, modifier = Modifier.weight(1f))
            if (canRemove) {
                IconButton(onClick = onRemove) {
                    Icon(Icons.Default.RemoveCircle, contentDescription = "Remove", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = Modifier.weight(1f)) {
                OutlinedTextField(
                    value = account.type.displayName, onValueChange = {}, readOnly = true,
                    label = { Text("Type") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier.menuAnchor()
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    AccountType.entries.forEach { type ->
                        DropdownMenuItem(text = { Text(type.displayName) }, onClick = { onChange(account.copy(type = type)); expanded = false })
                    }
                }
            }
            OutlinedTextField(
                value = account.balanceText, onValueChange = { onChange(account.copy(balanceText = it)) },
                label = { Text("Balance") }, prefix = { Text("$") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true, modifier = Modifier.weight(1f)
            )
        }
    }
}
