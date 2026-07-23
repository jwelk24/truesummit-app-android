package com.truesummit.android.service

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.truesummit.android.ui.transactions.formatCurrency
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object PDFExporter {

    fun exportReportToPDF(context: Context, summary: ReportSummary): File? {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(612, 792, 1).create() // Letter size
        val page = document.startPage(pageInfo)
        val canvas = page.canvas
        val paint = Paint()

        var y = 60f
        val xMargin = 40f

        // Title
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 24f
        paint.color = Color.BLACK
        canvas.drawText("Summit Report", xMargin, y, paint)
        y += 24f

        // Period
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 14f
        paint.color = Color.GRAY
        canvas.drawText(summary.period.label, xMargin, y, paint)
        y += 40f

        // Summary Stats
        val statWidth = (612 - (xMargin * 2) - 40) / 3
        drawStatBox(canvas, "Income", formatCurrency(summary.totalIncome.toDouble()), xMargin, y, statWidth, Color.parseColor("#10B981"))
        drawStatBox(canvas, "Spending", formatCurrency(summary.totalSpending.toDouble()), xMargin + statWidth + 20, y, statWidth, Color.parseColor("#EF4444"))
        val netColor = if (summary.netWorthChange >= java.math.BigDecimal.ZERO) Color.parseColor("#10B981") else Color.parseColor("#EF4444")
        drawStatBox(canvas, "Net Change", formatCurrency(summary.netWorthChange.toDouble()), xMargin + (statWidth + 20) * 2, y, statWidth, netColor)
        y += 80f

        // Category List Header
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 18f
        paint.color = Color.BLACK
        canvas.drawText("Spending by Category", xMargin, y, paint)
        y += 30f

        // Category Rows
        paint.textSize = 14f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        for (item in summary.byCategory.take(20)) {
            paint.color = Color.BLACK
            canvas.drawText(item.first, xMargin, y, paint)
            
            val amountStr = formatCurrency(item.second.toDouble())
            val amountWidth = paint.measureText(amountStr)
            canvas.drawText(amountStr, 612 - xMargin - amountWidth, y, paint)
            
            y += 10f
            paint.color = Color.LTGRAY
            paint.strokeWidth = 1f
            canvas.drawLine(xMargin, y, 612 - xMargin, y, paint)
            y += 24f
            
            if (y > 740f) break // Prevent overflow
        }

        // Footer
        y = 760f
        paint.color = Color.GRAY
        paint.textSize = 10f
        canvas.drawText("${summary.transactionCount} transactions", xMargin, y, paint)
        
        val genText = "Generated ${SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date())}"
        val genWidth = paint.measureText(genText)
        canvas.drawText(genText, 612 - xMargin - genWidth, y, paint)

        document.finishPage(page)

        val fileName = "truesummit_report_${System.currentTimeMillis()}.pdf"
        val file = File(context.cacheDir, fileName)
        
        return try {
            val outputStream = FileOutputStream(file)
            document.writeTo(outputStream)
            document.close()
            outputStream.close()
            file
        } catch (e: Exception) {
            document.close()
            null
        }
    }

    private fun drawStatBox(canvas: Canvas, label: String, value: String, x: Float, y: Float, width: Float, color: Int) {
        val paint = Paint()
        
        // Background
        paint.color = color
        paint.alpha = 20
        canvas.drawRoundRect(x, y, x + width, y + 60f, 8f, 8f, paint)
        
        // Label
        paint.alpha = 255
        paint.color = Color.GRAY
        paint.textSize = 10f
        canvas.drawText(label, x + 10, y + 20, paint)
        
        // Value
        paint.color = color
        paint.textSize = 16f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(value, x + 10, y + 45, paint)
    }
}
