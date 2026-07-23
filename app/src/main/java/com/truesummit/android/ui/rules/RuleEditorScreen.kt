package com.truesummit.android.ui.rules

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleEditorScreen(
    ruleId: UUID? = null,
    seedMerchant: String? = null,
    seedCategoryId: UUID? = null,
    onDismiss: () -> Unit,
    viewModel: CategoryRulesViewModel = viewModel()
) {
    val rules by viewModel.rules.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    
    val editingRule = remember(ruleId, rules) { rules.find { it.id == ruleId } }

    var matchField by remember { mutableStateOf("merchant") }
    var matchKind by remember { mutableStateOf("contains") }
    var pattern by remember { mutableStateOf(seedMerchant ?: "") }
    var priority by remember { mutableStateOf(100) }
    var enabled by remember { mutableStateOf(true) }
    var selectedCategoryId by remember { mutableStateOf(seedCategoryId) }
    var caseSensitive by remember { mutableStateOf(false) }

    LaunchedEffect(editingRule) {
        editingRule?.let { rule ->
            matchField = rule.matchField
            matchKind = rule.matchKind
            pattern = rule.pattern
            priority = rule.priority
            enabled = rule.enabled
            selectedCategoryId = rule.categoryId
            caseSensitive = rule.caseSensitive
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (ruleId == null) "New Rule" else "Edit Rule") },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Button(
                        onClick = {
                            viewModel.saveRule(
                                id = ruleId,
                                priority = priority,
                                matchField = matchField,
                                matchKind = matchKind,
                                pattern = pattern,
                                caseSensitive = caseSensitive,
                                enabled = enabled,
                                categoryId = selectedCategoryId
                            )
                            onDismiss()
                        },
                        enabled = pattern.isNotBlank() && selectedCategoryId != null
                    ) {
                        Text("Save")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text("Match", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            
            // Field Picker
            Text("Field", style = MaterialTheme.typography.labelMedium)
            Row(modifier = Modifier.fillMaxWidth()) {
                listOf("merchant" to "Merchant", "memo" to "Memo").forEach { (value, label) ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 16.dp)) {
                        RadioButton(selected = matchField == value, onClick = { matchField = value })
                        Text(label)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Operator Picker
            Text("Operator", style = MaterialTheme.typography.labelMedium)
            listOf("contains", "equals", "startsWith", "endsWith").forEach { op ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = matchKind == op, onClick = { matchKind = op })
                    Text(op.replaceFirstChar { it.uppercase() })
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = pattern,
                onValueChange = { pattern = it },
                label = { Text("Pattern") },
                modifier = Modifier.fillMaxWidth()
            )
            
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                Checkbox(checked = caseSensitive, onCheckedChange = { caseSensitive = it })
                Text("Case sensitive")
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text("Then Assign", style = MaterialTheme.typography.titleMedium)
            
            categories.forEach { category ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = selectedCategoryId == category.id, onClick = { selectedCategoryId = category.id })
                    Text(category.name)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text("Options", style = MaterialTheme.typography.titleMedium)
            
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("Priority")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { if (priority > 1) priority-- }) { 
                        Icon(Icons.Default.Remove, contentDescription = "Decrease") 
                    }
                    Text("$priority")
                    IconButton(onClick = { if (priority < 999) priority++ }) { 
                        Icon(Icons.Default.Add, contentDescription = "Increase") 
                    }
                }
            }
            
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("Enabled")
                Switch(checked = enabled, onCheckedChange = { enabled = it })
            }
            
            if (ruleId != null) {
                Spacer(modifier = Modifier.height(32.dp))
                Button(
                    onClick = {
                        editingRule?.let { viewModel.deleteRule(it) }
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete Rule")
                }
            }
        }
    }
}
