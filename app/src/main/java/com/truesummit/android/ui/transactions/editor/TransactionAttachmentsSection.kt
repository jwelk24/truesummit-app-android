package com.truesummit.android.ui.transactions.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.truesummit.android.data.entity.TransactionAttachmentEntity
import java.io.ByteArrayOutputStream
import java.util.UUID

private fun processImage(context: Context, uri: Uri, maxDimension: Int = 1600): ByteArray? {
    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
    val largest = maxOf(bitmap.width, bitmap.height)
    val scaled = if (largest > maxDimension) {
        val scale = maxDimension.toFloat() / largest
        Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
    } else bitmap
    val out = ByteArrayOutputStream()
    scaled.compress(Bitmap.CompressFormat.JPEG, 70, out)
    return out.toByteArray()
}

@Composable
fun TransactionAttachmentsSection(
    existing: List<TransactionAttachmentEntity>,
    pendingImages: List<ByteArray>,
    removedIds: Set<UUID>,
    onAddPending: (ByteArray) -> Unit,
    onRemovePending: (Int) -> Unit,
    onRemoveExisting: (UUID) -> Unit
) {
    val context = LocalContext.current
    var viewingData by remember { mutableStateOf<ByteArray?>(null) }
    var viewingExistingId by remember { mutableStateOf<UUID?>(null) }
    var viewingPendingIndex by remember { mutableStateOf<Int?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        uris.take(5).forEach { uri ->
            processImage(context, uri)?.let { onAddPending(it) }
        }
    }

    val visibleExisting = existing.filter { !removedIds.contains(it.id) }

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text("Receipts", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(bottom = 8.dp))

        if (visibleExisting.isNotEmpty() || pendingImages.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                visibleExisting.forEach { attachment ->
                    val bmp = remember(attachment.id) {
                        BitmapFactory.decodeByteArray(attachment.imageData, 0, attachment.imageData.size)
                    }
                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Receipt",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    viewingData = attachment.imageData
                                    viewingExistingId = attachment.id
                                    viewingPendingIndex = null
                                }
                        )
                    }
                }
                pendingImages.forEachIndexed { index, bytes ->
                    val bmp = remember(index, bytes.size) {
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    }
                    if (bmp != null) {
                        Box {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "Pending receipt",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        viewingData = bytes
                                        viewingExistingId = null
                                        viewingPendingIndex = index
                                    }
                            )
                            Icon(
                                Icons.Default.Schedule,
                                contentDescription = "Pending",
                                modifier = Modifier
                                    .size(16.dp)
                                    .align(Alignment.TopEnd)
                                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(50))
                                    .padding(2.dp)
                            )
                        }
                    }
                }
            }
        }

        OutlinedButton(
            onClick = { picker.launch("image/*") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.AttachFile, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Add Receipt Photo")
        }

        Text(
            "Stored only on this device.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
    }

    viewingData?.let { data ->
        val bmp = remember(data.size) { BitmapFactory.decodeByteArray(data, 0, data.size) }
        if (bmp != null) {
            AlertDialog(
                onDismissRequest = { viewingData = null },
                title = { Text("Receipt") },
                text = {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Receipt",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)
                    )
                },
                confirmButton = {
                    TextButton(onClick = { viewingData = null }) { Text("Done") }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            viewingExistingId?.let { onRemoveExisting(it) }
                            viewingPendingIndex?.let { onRemovePending(it) }
                            viewingData = null
                        }
                    ) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
            )
        }
    }
}
