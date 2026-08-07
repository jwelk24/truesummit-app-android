package com.truesummit.wear.complication

import android.app.PendingIntent
import android.content.Intent
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceService
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import com.truesummit.wear.MainActivity
import com.truesummit.wear.SnapshotRepository
import com.truesummit.wear.WatchSnapshot
import java.text.NumberFormat
import java.util.Locale

/**
 * Shared plumbing for the watch-face complications. Subclasses describe one
 * metric; this class handles snapshot loading, tap targets, and the mapping
 * onto each complication type the watch face may ask for.
 */
abstract class SnapshotComplicationService : ComplicationDataSourceService() {

    /** Short label shown alongside the value, e.g. "Safe". */
    protected abstract val label: String

    /** Value text for a populated snapshot, e.g. "$142". */
    protected abstract fun value(snapshot: WatchSnapshot): String

    /** Longer form used by LONG_TEXT, defaults to the short value. */
    protected open fun longValue(snapshot: WatchSnapshot): String = value(snapshot)

    /** Fraction 0f..1f for RANGED_VALUE, or null if the metric isn't a ratio. */
    protected open fun fraction(snapshot: WatchSnapshot): Float? = null

    /** Sample shown in the complication picker. */
    protected abstract val previewValue: String

    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        build(type, previewValue, previewValue, 0.6f)

    override fun onComplicationRequest(
        request: ComplicationRequest,
        listener: ComplicationRequestListener
    ) {
        val snapshot = SnapshotRepository.load(this)
        val data = if (snapshot == null) {
            build(request.complicationType, "—", "Open phone", null)
        } else {
            build(
                request.complicationType,
                value(snapshot),
                longValue(snapshot),
                fraction(snapshot)
            )
        }
        listener.onComplicationData(data)
    }

    private fun tapAction(): PendingIntent =
        PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    private fun build(
        type: ComplicationType,
        short: String,
        long: String,
        fraction: Float?
    ): ComplicationData? {
        val contentDescription = PlainComplicationText.Builder("$label $short").build()
        return when (type) {
            ComplicationType.SHORT_TEXT ->
                ShortTextComplicationData.Builder(
                    text = PlainComplicationText.Builder(short.take(7)).build(),
                    contentDescription = contentDescription
                )
                    .setTitle(PlainComplicationText.Builder(label.take(7)).build())
                    .setTapAction(tapAction())
                    .build()

            ComplicationType.LONG_TEXT ->
                LongTextComplicationData.Builder(
                    text = PlainComplicationText.Builder(long).build(),
                    contentDescription = contentDescription
                )
                    .setTitle(PlainComplicationText.Builder(label).build())
                    .setTapAction(tapAction())
                    .build()

            ComplicationType.RANGED_VALUE -> {
                // Watch faces may offer RANGED_VALUE on a metric that has no
                // natural 0..1 range; fall back to a neutral full arc.
                val value = fraction ?: 1f
                RangedValueComplicationData.Builder(
                    value = value.coerceIn(0f, 1f),
                    min = 0f,
                    max = 1f,
                    contentDescription = contentDescription
                )
                    .setText(PlainComplicationText.Builder(short.take(7)).build())
                    .setTitle(PlainComplicationText.Builder(label.take(7)).build())
                    .setTapAction(tapAction())
                    .build()
            }

            else -> null
        }
    }

    companion object {
        fun currency(amount: Double): String {
            val fmt = NumberFormat.getCurrencyInstance(Locale.US)
            fmt.maximumFractionDigits = 0
            return fmt.format(amount)
        }
    }
}
