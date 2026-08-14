package com.truesummit.android.ui.transactions

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptScannerScreen(
    onDismiss: () -> Unit,
    viewModel: ReceiptScannerViewModel = viewModel()
) {
    val phase by viewModel.phase.collectAsState()
    val context = LocalContext.current

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val bitmap = if (Build.VERSION.SDK_INT < 28) {
                MediaStore.Images.Media.getBitmap(context.contentResolver, it)
            } else {
                val source = ImageDecoder.createSource(context.contentResolver, it)
                ImageDecoder.decodeBitmap(source)
            }
            viewModel.scanReceipt(bitmap)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scan Receipt") },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (phase == ScanPhase.REVIEW) {
                        Button(onClick = { viewModel.save(onDismiss) }) {
                            Text("Save")
                        }
                    }
                }
            )
        }
    ) { padding ->
        when (phase) {
            ScanPhase.PICK_PHOTO -> PickPhotoView(padding) { launcher.launch("image/*") }
            ScanPhase.SCANNING -> ScanningView(padding)
            ScanPhase.REVIEW -> ReviewView(padding, viewModel)
        }
    }
}

@Composable
fun PickPhotoView(padding: PaddingValues, onPick: () -> Unit) {
    Column(
        modifier = Modifier.padding(padding).fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.DocumentScanner,
            contentDescription = null,
            modifier = Modifier.size(100.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Photograph a receipt and TrueSummit will extract the merchant and totals.",
            modifier = Modifier.padding(32.dp),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Button(onClick = onPick) {
            Icon(Icons.Default.PhotoLibrary, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Choose Receipt Photo")
        }
    }
}

@Composable
fun ScanningView(padding: PaddingValues) {
    Column(
        modifier = Modifier.padding(padding).fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(16.dp))
        Text("Reading the receipt...", style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun ReviewView(padding: PaddingValues, viewModel: ReceiptScannerViewModel) {
    val merchant by viewModel.merchant.collectAsState()
    val lineItems by viewModel.lineItems.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    val selectedAccountId by viewModel.selectedAccountId.collectAsState()

    LazyColumn(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
        item {
            OutlinedTextField(
                value = merchant,
                onValueChange = { viewModel.merchant.value = it },
                label = { Text("Merchant") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            Text("Account", style = MaterialTheme.typography.labelLarge)
            accounts.forEach { account ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = selectedAccountId == account.id,
                        onClick = { viewModel.selectedAccountId.value = account.id }
                    )
                    Text(account.name)
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text("Line Items", style = MaterialTheme.typography.titleMedium)
        }
        
        items(lineItems) { item ->
            ListItem(
                headlineContent = { Text(item.name) },
                trailingContent = { Text(formatCurrency(item.amount.toDouble())) }
            )
        }
    }
}
