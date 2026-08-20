package com.truesummit.android.ui.settings

import com.truesummit.android.service.BudgetEngine
import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.truesummit.android.data.AppDatabase
import com.truesummit.android.service.MerchantLogoService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.*

private const val PREFS_PRIVACY = "truesummit_privacy"
private const val KEY_LOCAL_ONLY = "local_only_mode"

class PrivacyDataViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getInstance(application)

    private val prefs = application.getSharedPreferences(PREFS_PRIVACY, Context.MODE_PRIVATE)

    val localOnlyMode: StateFlow<Boolean> = MutableStateFlow(prefs.getBoolean(KEY_LOCAL_ONLY, false))

    fun setLocalOnly(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_LOCAL_ONLY, enabled).apply()
        (localOnlyMode as MutableStateFlow).value = enabled
    }

    /** Build a full JSON export of all user data. Returns the JSON string. */
    suspend fun buildExportJson(): String {
        val txs = db.transactionDao().getAll().first()
        val categories = db.categoryDao().getCategoriesList()
        val accounts = db.accountDao().getAll().first()
        val goals = db.goalDao().getAllGoals().first()
        val df = SimpleDateFormat("yyyy-MM-dd", Locale.US)

        val json = buildJsonObject {
            put("exportVersion", 1)
            put("exportDate", df.format(Date()))
            put("transactions", buildJsonArray {
                txs.forEach { tx ->
                    add(buildJsonObject {
                        put("id", tx.id.toString())
                        put("date", df.format(tx.date))
                        put("amount", tx.amount.toDouble())
                        put("merchant", tx.merchant)
                        put("memo", tx.memo ?: "")
                        put("cleared", tx.cleared)
                        put("categoryId", tx.categoryId?.toString() ?: "")
                        put("accountId", tx.accountId?.toString() ?: "")
                        put("tags", tx.tags)
                        put("pfcPrimary", tx.pfcPrimary ?: "")
                    })
                }
            })
            put("categories", buildJsonArray {
                categories.forEach { cat ->
                    add(buildJsonObject {
                        put("id", cat.id.toString())
                        put("name", cat.name)
                        put("groupId", cat.groupId?.toString() ?: "")
                    })
                }
            })
            put("accounts", buildJsonArray {
                accounts.forEach { acc ->
                    add(buildJsonObject {
                        put("id", acc.id.toString())
                        put("name", acc.name)
                        put("type", acc.type.name)
                        put("balance", acc.balance.toDouble())
                    })
                }
            })
            put("goals", buildJsonArray {
                goals.forEach { goal ->
                    add(buildJsonObject {
                        put("id", goal.id.toString())
                        put("categoryId", goal.categoryId.toString())
                        put("type", goal.type.name)
                        put("targetAmount", goal.targetAmount.toDouble())
                        put("targetDate", goal.targetDate?.let { df.format(it) } ?: "")
                    })
                }
            })
        }
        return Json { prettyPrint = true }.encodeToString(json)
    }

    val importResult: StateFlow<String?> = MutableStateFlow(null)

    fun importJson(jsonText: String) {
        viewModelScope.launch {
            try {
                val root = Json.parseToJsonElement(jsonText).jsonObject
                val df = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                var imported = 0

                root["transactions"]?.jsonArray?.forEach { el ->
                    val obj = el.jsonObject
                    val id = obj["id"]?.jsonPrimitive?.content?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: UUID.randomUUID()
                    val existing = db.transactionDao().getById(id)
                    if (existing == null) {
                        val date = obj["date"]?.jsonPrimitive?.content?.let { df.parse(it) } ?: Date()
                        val amount = obj["amount"]?.jsonPrimitive?.doubleOrNull?.let { BigDecimal.valueOf(it) } ?: BigDecimal.ZERO
                        val merchant = obj["merchant"]?.jsonPrimitive?.content ?: ""
                        val memo = obj["memo"]?.jsonPrimitive?.content?.ifBlank { null }
                        val cleared = obj["cleared"]?.jsonPrimitive?.content?.toBoolean() ?: false
                        val catId = obj["categoryId"]?.jsonPrimitive?.content?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                        val accId = obj["accountId"]?.jsonPrimitive?.content?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                        val tags = obj["tags"]?.jsonPrimitive?.content ?: ""
                        val pfc = obj["pfcPrimary"]?.jsonPrimitive?.content?.ifBlank { null }
                        db.transactionDao().insert(
                            com.truesummit.android.data.entity.TransactionEntity(
                                id = id, date = date, amount = amount, merchant = merchant,
                                memo = memo, cleared = cleared, flagColor = null,
                                pfcPrimary = pfc, accountId = accId, categoryId = catId, tags = tags
                            )
                        )
                        imported++
                    }
                }
                (importResult as MutableStateFlow).value = "Imported $imported transactions."
            } catch (e: Exception) {
                (importResult as MutableStateFlow).value = "Import failed: ${e.message}"
            }
        }
    }

    fun eraseCloudData() {
        viewModelScope.launch {
            // Remove Supabase household linkage so future syncs won't push data
            val syncPrefs = getApplication<Application>().getSharedPreferences("truesummit_sync", Context.MODE_PRIVATE)
            syncPrefs.edit().remove("household_id").remove("user_id").apply()
        }
    }

    private val _seedResult = MutableStateFlow<String?>(null)
    val seedResult: StateFlow<String?> = _seedResult
    fun dismissSeedResult() { _seedResult.value = null }

    /**
     * Fills an empty install with the six-month sample budget.
     *
     * The onboarding wizard offers this too, but only on its first step and
     * only below the fold — so once a user is past the wizard there was no way
     * back to it. Screenshot and demo runs need a trigger that does not depend
     * on onboarding state.
     */
    fun loadSampleData() {
        viewModelScope.launch {
            _seedResult.value = try {
                val engine = BudgetEngine(getApplication())
                engine.seedIfNeeded()
                "Sample data loaded. Pull down or reopen a tab to see it."
            } catch (e: Exception) {
                "Could not load sample data: ${e.message}"
            }
        }
    }

    private val _eraseLocalResult = MutableStateFlow<String?>(null)
    val eraseLocalResult: StateFlow<String?> = _eraseLocalResult
    fun dismissEraseLocalResult() { _eraseLocalResult.value = null }

    /**
     * Wipes every local record. Without this there was no way to remove the
     * sample data short of reinstalling, which is what the onboarding button
     * had to warn about.
     */
    fun eraseLocalData() {
        viewModelScope.launch {
            _eraseLocalResult.value = try {
                BudgetEngine(getApplication()).resetAllData(reseed = false)
                "All local accounts, transactions, budgets and goals have been deleted."
            } catch (e: Exception) {
                "Could not erase local data: ${e.message}"
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyDataScreen(
    onBack: () -> Unit,
    viewModel: PrivacyDataViewModel = viewModel()
) {
    val context = LocalContext.current
    val localOnly by viewModel.localOnlyMode.collectAsState()
    val importResult by viewModel.importResult.collectAsState()
    var showEraseDialog by remember { mutableStateOf(false) }
    var showEraseLocalDialog by remember { mutableStateOf(false) }
    val eraseLocalResult by viewModel.eraseLocalResult.collectAsState()
    val seedResult by viewModel.seedResult.collectAsState()
    var exportJson by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    var merchantLogosEnabled by remember { mutableStateOf(MerchantLogoService.isEnabled(context)) }
    var showLogoConsentDialog by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let {
            val json = exportJson ?: return@let
            context.contentResolver.openOutputStream(it)?.use { os -> os.write(json.toByteArray()) }
            exportJson = null
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            val text = context.contentResolver.openInputStream(it)?.bufferedReader()?.readText() ?: return@let
            viewModel.importJson(text)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Privacy & Data") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize()
        ) {
            item { SettingsSectionHeader("Privacy") }

            item {
                ListItem(
                    headlineContent = { Text("Local-Only Mode") },
                    supportingContent = { Text("Disable cloud sync — your data stays on this device only.") },
                    leadingContent = { Icon(Icons.Default.Lock, contentDescription = null) },
                    trailingContent = {
                        Switch(checked = localOnly, onCheckedChange = { viewModel.setLocalOnly(it) })
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
            }

            item { SettingsSectionHeader("Your Data") }

            item {
                ListItem(
                    headlineContent = { Text("Export All Data") },
                    supportingContent = { Text("Download a full JSON backup of your transactions, categories, and goals.") },
                    leadingContent = { Icon(Icons.Default.FileDownload, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth().clickable {
                        scope.launch {
                            val json = viewModel.buildExportJson()
                            exportJson = json
                            exportLauncher.launch("truesummit-export-${System.currentTimeMillis()}.json")
                        }
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
            }

            item {
                ListItem(
                    headlineContent = { Text("Import Data") },
                    supportingContent = { Text("Import a previously exported JSON file. Existing records are preserved (idempotent).") },
                    leadingContent = { Icon(Icons.Default.FileUpload, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth().clickable { importLauncher.launch(arrayOf("application/json")) }
                )
                if (importResult != null) {
                    Text(
                        importResult!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 72.dp, bottom = 8.dp)
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
            }

            item {
                ListItem(
                    headlineContent = { Text("Load Sample Data") },
                    supportingContent = { Text("Fills this device with a six-month example budget. Only runs on an empty install — erase local data first to re-run it.") },
                    leadingContent = { Icon(Icons.Default.Dataset, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth().clickable { viewModel.loadSampleData() }
                )
                if (seedResult != null) {
                    Text(
                        seedResult!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 72.dp, bottom = 8.dp)
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
            }

            item { SettingsSectionHeader("Merchant Logos") }

            item {
                ListItem(
                    headlineContent = { Text("Show merchant logos") },
                    supportingContent = { Text("Off by default. Sends merchant names to a logo service. See AI Features below for the other network feature.") },
                    leadingContent = { Icon(Icons.Default.Image, contentDescription = null) },
                    trailingContent = {
                        Switch(
                            checked = merchantLogosEnabled,
                            onCheckedChange = { newValue ->
                                if (newValue) {
                                    showLogoConsentDialog = true
                                } else {
                                    merchantLogosEnabled = false
                                    MerchantLogoService.setEnabled(context, false)
                                }
                            }
                        )
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
            }

            item { SettingsSectionHeader("AI Features") }

            item {
                ListItem(
                    headlineContent = { Text("How AI features use your data") },
                    supportingContent = {
                        Text(
                            "Insights, digests, categorization and the coach send the relevant " +
                            "transactions — merchant names, amounts, dates and categories — to " +
                            "Google Gemini through TrueSummit's server, which holds the API key. " +
                            "Requests require you to be signed in. Nothing is sent unless you " +
                            "start an AI feature, and account numbers and credentials are never " +
                            "included."
                        )
                    },
                    leadingContent = { Icon(Icons.Default.AutoAwesome, contentDescription = null) }
                )
                HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
            }

            item { SettingsSectionHeader("Cloud") }

            item {
                ListItem(
                    headlineContent = { Text("Erase Cloud Data") },
                    supportingContent = { Text("Unlinks this device from cloud sync. Your local data is not affected.") },
                    leadingContent = { Icon(Icons.Default.CloudOff, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                    modifier = Modifier.fillMaxWidth().clickable { showEraseDialog = true }
                )
                HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
            }

            item {
                ListItem(
                    headlineContent = { Text("Erase Local Data") },
                    supportingContent = { Text("Deletes every account, transaction, budget and goal on this device. Cannot be undone.") },
                    leadingContent = { Icon(Icons.Default.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                    modifier = Modifier.fillMaxWidth().clickable { showEraseLocalDialog = true }
                )
            }
        }
    }

    if (showLogoConsentDialog) {
        AlertDialog(
            onDismissRequest = { showLogoConsentDialog = false },
            title = { Text("Show merchant logos?") },
            text = { Text("To show logos, TrueSummit sends merchant names from your transactions to a logo service over the internet. Your budgets and balances are not included. You can turn this off anytime.") },
            confirmButton = {
                TextButton(onClick = {
                    merchantLogosEnabled = true
                    MerchantLogoService.setEnabled(context, true)
                    MerchantLogoService.markConsentShown(context)
                    showLogoConsentDialog = false
                }) { Text("Enable — I understand") }
            },
            dismissButton = {
                TextButton(onClick = {
                    merchantLogosEnabled = false
                    showLogoConsentDialog = false
                }) { Text("Cancel") }
            }
        )
    }

    if (showEraseDialog) {
        AlertDialog(
            onDismissRequest = { showEraseDialog = false },
            title = { Text("Erase Cloud Link?") },
            text = { Text("This removes the cloud sync link from this device. Your local data stays intact. You'll need to re-link to sync again in the future.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.eraseCloudData()
                        showEraseDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Erase Link") }
            },
            dismissButton = {
                TextButton(onClick = { showEraseDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showEraseLocalDialog) {
        AlertDialog(
            onDismissRequest = { showEraseLocalDialog = false },
            title = { Text("Erase Local Data?") },
            text = {
                Text(
                    "This permanently deletes every account, transaction, budget and goal " +
                    "stored on this device, including any sample data. It cannot be undone."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.eraseLocalData()
                        showEraseLocalDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Erase Everything") }
            },
            dismissButton = {
                TextButton(onClick = { showEraseLocalDialog = false }) { Text("Cancel") }
            }
        )
    }

    eraseLocalResult?.let { message ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissEraseLocalResult() },
            title = { Text("Erase Local Data") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissEraseLocalResult() }) { Text("OK") }
            }
        )
    }
}
