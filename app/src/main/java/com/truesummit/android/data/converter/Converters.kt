package com.truesummit.android.data.converter

import androidx.room.TypeConverter
import com.truesummit.android.data.model.AccountType
import com.truesummit.android.data.model.GoalType
import com.truesummit.android.data.model.ScheduledKind
import java.math.BigDecimal
import java.util.Date
import java.util.UUID

class Converters {
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }

    @TypeConverter
    fun fromUUID(value: String?): UUID? {
        return value?.let { UUID.fromString(it) }
    }

    @TypeConverter
    fun uuidToString(uuid: UUID?): String? {
        return uuid?.toString()
    }

    @TypeConverter
    fun fromBigDecimal(value: String?): BigDecimal? {
        return value?.let { BigDecimal(it) }
    }

    @TypeConverter
    fun bigDecimalToString(bigDecimal: BigDecimal?): String? {
        return bigDecimal?.toPlainString()
    }

    @TypeConverter
    fun fromAccountType(value: String?): AccountType? {
        return value?.let { enumValueOf<AccountType>(it) }
    }

    @TypeConverter
    fun accountTypeToString(type: AccountType?): String? {
        return type?.name
    }

    @TypeConverter
    fun fromGoalType(value: String?): GoalType? {
        return value?.let { enumValueOf<GoalType>(it) }
    }

    @TypeConverter
    fun goalTypeToString(type: GoalType?): String? {
        return type?.name
    }

    @TypeConverter
    fun fromScheduledKind(value: String?): ScheduledKind? {
        return value?.let { enumValueOf<ScheduledKind>(it) }
    }

    @TypeConverter
    fun scheduledKindToString(kind: ScheduledKind?): String? {
        return kind?.name
    }
}
