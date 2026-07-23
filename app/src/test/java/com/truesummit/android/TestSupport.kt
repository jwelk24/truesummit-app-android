package com.truesummit.android

import com.truesummit.android.data.entity.TransactionEntity
import java.math.BigDecimal
import java.util.*

object TestSupport {

    /** Returns a deterministic Date at noon on the given Y/M/D so day-boundary math doesn't shift. */
    fun date(year: Int, month: Int, day: Int, hour: Int = 12): Date {
        val cal = Calendar.getInstance()
        cal.set(year, month - 1, day, hour, 0, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.time
    }

    fun tx(
        date: Date,
        amount: Double,
        merchant: String = "Test",
        categoryId: UUID? = null,
        pfcPrimary: String? = null,
        memo: String? = null,
        cleared: Boolean = false
    ) = TransactionEntity(
        id = UUID.randomUUID(),
        date = date,
        amount = BigDecimal(amount.toString()),
        merchant = merchant,
        memo = memo,
        cleared = cleared,
        flagColor = null,
        pfcPrimary = pfcPrimary,
        accountId = null,
        categoryId = categoryId
    )
}
