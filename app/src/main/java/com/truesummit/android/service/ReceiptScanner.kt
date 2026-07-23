package com.truesummit.android.service

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.util.*
import java.util.regex.Pattern

data class ScannedReceipt(
    val merchant: String?,
    val amount: BigDecimal?,
    val date: Date?
)

object ReceiptScanner {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun scan(context: Context, uri: Uri): ScannedReceipt = withContext(Dispatchers.IO) {
        val bitmap = MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        scan(bitmap)
    }

    suspend fun scan(bitmap: Bitmap): ScannedReceipt = withContext(Dispatchers.IO) {
        val image = InputImage.fromBitmap(bitmap, 0)
        try {
            val result = Tasks.await(recognizer.process(image))
            val text = result.text
            parse(text)
        } catch (e: Exception) {
            ScannedReceipt(null, null, null)
        }
    }

    private fun parse(text: String): ScannedReceipt {
        val lines = text.split("\n")
        val merchant = lines.firstOrNull()?.trim()

        val amountRegex = Pattern.compile("(?i)(total|sum|amount)[:\\s]*[\\$]?\\s*([\\d.,]+)")
        var amount: BigDecimal? = null
        val matcher = amountRegex.matcher(text)
        if (matcher.find()) {
            val str = matcher.group(2)?.replace(",", "")
            amount = str?.let { try { BigDecimal(it) } catch(e: Exception) { null } }
        }

        return ScannedReceipt(merchant, amount, Date())
    }
}
