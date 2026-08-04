package com.truesummit.android.ui.peaks

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import com.truesummit.android.data.entity.CategoryEntity
import com.truesummit.android.data.entity.CategoryGroupEntity
import com.truesummit.android.data.entity.GoalEntity
import com.truesummit.android.data.model.GoalType
import com.truesummit.android.service.BudgetEngine
import com.truesummit.android.service.GoalForecast
import com.truesummit.android.service.GoalPace
import com.truesummit.android.ui.theme.SummitColors
import com.truesummit.android.ui.theme.summitCategoryEmoji
import com.truesummit.android.ui.transactions.formatCurrency
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.*

// ---------------------------------------------------------------------------
// ViewModel
// ---------------------------------------------------------------------------

data class PeakCardData(
    val goal: GoalEntity,
    val category: CategoryEntity?,
    val saved: BigDecimal,
    val assigned: BigDecimal,
    val pace: GoalPace,
    val fraction: Double
)

class PeaksViewModel(application: Application) : AndroidViewModel(application) {
    private val db = Room.databaseBuilder(application, AppDatabase::class.java, "truesummit-db")
        .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4)
        .build()

    private val engine = BudgetEngine(application)

    val cards = combine(
        db.goalDao().getAllGoals(),
        db.categoryDao().getCategories(),
    ) { goals, cats ->
        val cal = Calendar.getInstance()
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) + 1
        val budgetMonth = db.budgetDao().getMonth(year, month)

        goals.filter { it.categoryId != null }.map { goal ->
            val category = cats.find { it.id == goal.categoryId }
            val savedAmt = if (category != null)
                engine.available(category, budgetMonth, year, month).max(BigDecimal.ZERO)
            else BigDecimal.ZERO
            val assignedAmt = if (category != null)
                engine.assigned(category, budgetMonth)
            else BigDecimal.ZERO
            val allMonths = db.budgetDao().getAllMonths()
            val avg = if (allMonths.size >= 2) {
                val recent = allMonths.sortedByDescending { it.year * 100 + it.month }.take(3)
                recent.mapNotNull { m ->
                    val alloc = db.budgetDao().getAllocation(m.id, goal.categoryId!!)
                    alloc?.amount
                }.fold(BigDecimal.ZERO) { a, b -> a + b }
                    .divide(BigDecimal(recent.size.coerceAtLeast(1)), 2, java.math.RoundingMode.HALF_UP)
            } else BigDecimal.ZERO

            val pace = GoalForecast.pace(
                goal = goal,
                assignedThisMonth = assignedAmt,
                availableNow = savedAmt,
                avgMonthlyAssigned = avg,
                currentYear = year,
                currentMonth = month
            )
            val fraction = if (goal.targetAmount > BigDecimal.ZERO)
                (savedAmt.toDouble() / goal.targetAmount.toDouble()).coerceIn(0.0, 1.0)
            else 0.0

            PeakCardData(goal, category, savedAmt, assignedAmt, pace, fraction)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addPeak(name: String, targetAmount: BigDecimal, targetDate: Date?) {
        viewModelScope.launch {
            val groups = db.categoryDao().getGroupsList()
            val group = groups.firstOrNull {
                it.name.lowercase() == "goals" || it.name.lowercase() == "peaks"
            } ?: run {
                val newGroup = CategoryGroupEntity(
                    name = "Goals",
                    sort = (groups.maxOfOrNull { it.sort } ?: 0) + 1
                )
                db.categoryDao().insertGroup(newGroup)
                newGroup
            }
            val cats = db.categoryDao().getCategoriesList()
            val category = CategoryEntity(name = name, sort = cats.size, groupId = group.id, linkedAccountId = null)
            db.categoryDao().insertCategory(category)
            val type = if (targetDate != null) GoalType.BY_DATE_TARGET else GoalType.SAVINGS_TARGET
            db.goalDao().insert(GoalEntity(type = type, targetAmount = targetAmount, targetDate = targetDate, categoryId = category.id))
        }
    }
}

// ---------------------------------------------------------------------------
// Screen
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeaksScreen(
    onNavigateToCategory: (UUID) -> Unit = {}
) {
    val vm: PeaksViewModel = viewModel()
    val cards by vm.cards.collectAsState()
    var showAdd by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Your Peaks", fontFamily = FontFamily.Serif) },
                actions = {
                    IconButton(onClick = { showAdd = true }) {
                        Icon(Icons.Default.Add, contentDescription = "New Peak")
                    }
                }
            )
        },
        floatingActionButton = {
            if (cards.isNotEmpty()) {
                FloatingActionButton(onClick = { showAdd = true }) {
                    Icon(Icons.Default.Add, contentDescription = "New Peak")
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (cards.isEmpty()) {
                item { PeaksEmptyState(onAdd = { showAdd = true }) }
            } else {
                item {
                    Text(
                        "Every summit starts with a single step.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                itemsIndexed(cards) { index, card ->
                    PeakCard(
                        card = card,
                        accentColor = SummitColors.accent(index),
                        onClick = { card.category?.id?.let(onNavigateToCategory) }
                    )
                }
                item { Spacer(Modifier.height(72.dp)) }
            }
        }
    }

    if (showAdd) {
        AddPeakSheet(
            onDismiss = { showAdd = false },
            onSave = { name, amount, date ->
                vm.addPeak(name, amount, date)
                showAdd = false
            }
        )
    }
}

// ---------------------------------------------------------------------------
// PeakCard
// ---------------------------------------------------------------------------

@Composable
private fun PeakCard(card: PeakCardData, accentColor: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(SummitColors.Slate2)
            .clickable(onClick = onClick)
    ) {
        // Decorative shimmer circle
        Box(
            modifier = Modifier
                .size(130.dp)
                .align(Alignment.TopEnd)
                .offset(x = 35.dp, y = (-35).dp)
                .clip(RoundedCornerShape(65.dp))
                .background(accentColor.copy(alpha = 0.06f))
        )

        Column(modifier = Modifier.padding(20.dp)) {
            // Icon + badge row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                val emoji = summitCategoryEmoji(card.category?.name)
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(accentColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(emoji, fontSize = 22.sp)
                }
                Surface(
                    shape = RoundedCornerShape(50),
                    color = accentColor.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = badgeText(card.pace).uppercase(),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentColor,
                        letterSpacing = 0.6.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // Goal name
            Text(
                text = card.category?.name ?: "Goal",
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = goalDescription(card.goal),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 16.dp)
            )

            // Saved + ETA row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = formatCurrency(card.saved.toDouble()),
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "of ${formatCurrency(card.goal.targetAmount.toDouble())}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
                Text(
                    text = etaLabel(card),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = accentColor,
                    textAlign = TextAlign.End
                )
            }

            Spacer(Modifier.height(10.dp))

            // Progress bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(accentColor.copy(alpha = 0.15f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(card.fraction.toFloat().coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(3.dp))
                        .background(accentColor)
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Empty state
// ---------------------------------------------------------------------------

@Composable
private fun PeaksEmptyState(onAdd: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 60.dp, horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            Icons.Default.Flag,
            contentDescription = null,
            tint = SummitColors.Teal,
            modifier = Modifier.size(44.dp)
        )
        Text("No peaks yet", style = MaterialTheme.typography.titleMedium)
        Text(
            "Set a savings goal and watch your progress climb.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        OutlinedButton(onClick = onAdd) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Set a new peak")
        }
    }
}

// ---------------------------------------------------------------------------
// AddPeakSheet
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddPeakSheet(
    onDismiss: () -> Unit,
    onSave: (name: String, amount: BigDecimal, date: Date?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var useTargetDate by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var targetDate by remember {
        val cal = Calendar.getInstance().apply { add(Calendar.YEAR, 1) }
        mutableStateOf(cal.time)
    }

    val canSave = name.trim().isNotEmpty() && amountText.toBigDecimalOrNull()?.let { it > BigDecimal.ZERO } == true

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "New Peak",
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name your peak") },
                placeholder = { Text("e.g. Japan Trip, Down Payment…") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it },
                label = { Text("Target amount") },
                prefix = { Text("$") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Set a target date", style = MaterialTheme.typography.bodyMedium)
                Switch(checked = useTargetDate, onCheckedChange = { useTargetDate = it })
            }

            if (useTargetDate) {
                val dateLabel = SimpleDateFormat("MMM yyyy", Locale.getDefault()).format(targetDate)
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(dateLabel)
                }
            }

            Text(
                "A savings category will be created for this peak. Assign funds to it each month in the Budget tab to build progress.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }
                Button(
                    onClick = {
                        val amount = amountText.toBigDecimalOrNull() ?: return@Button
                        onSave(name.trim(), amount, if (useTargetDate) targetDate else null)
                    },
                    enabled = canSave,
                    modifier = Modifier.weight(1f)
                ) { Text("Add Peak") }
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = targetDate.time
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { targetDate = Date(it) }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

private fun badgeText(pace: GoalPace) = when (pace) {
    is GoalPace.Reached -> "Reached!"
    is GoalPace.OnTrack -> "On track"
    is GoalPace.Projecting -> "Saving"
    is GoalPace.Unfunded -> "New goal"
    is GoalPace.Behind -> "Behind"
    is GoalPace.ShortThisMonth -> "This month"
    is GoalPace.FundedThisMonth -> "On track"
    is GoalPace.NeedToStayOnTrack -> "Stay on track"
}

private fun goalDescription(goal: GoalEntity): String = when (goal.type) {
    GoalType.MONTHLY_AMOUNT -> "Monthly target"
    GoalType.SAVINGS_TARGET -> "Savings goal"
    GoalType.BY_DATE_TARGET -> {
        goal.targetDate?.let { SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(it) }
            ?: "Savings goal"
    }
}

private fun etaLabel(card: PeakCardData): String {
    card.goal.targetDate?.let {
        return "By " + SimpleDateFormat("MMM yyyy", Locale.getDefault()).format(it)
    }
    return when (val p = card.pace) {
        is GoalPace.Reached -> "Summit reached!"
        is GoalPace.Projecting -> "~${p.monthsToGoal} months away"
        is GoalPace.OnTrack -> if (p.monthsEarly > 0) "${p.monthsEarly}mo early" else "On track"
        is GoalPace.Behind -> "${p.monthsLate}mo late"
        is GoalPace.Unfunded -> "Just started"
        is GoalPace.ShortThisMonth -> "Needs this month"
        is GoalPace.FundedThisMonth -> "On track"
        is GoalPace.NeedToStayOnTrack -> "Add more"
    }
}
