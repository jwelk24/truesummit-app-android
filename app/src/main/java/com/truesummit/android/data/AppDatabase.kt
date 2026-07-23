package com.truesummit.android.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.truesummit.android.data.converter.Converters
import com.truesummit.android.data.dao.*
import com.truesummit.android.data.entity.*

@Database(
    entities = [
        AccountEntity::class,
        CategoryGroupEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
        TransactionSplitEntity::class,
        GoalEntity::class,
        ScheduledItemEntity::class,
        BudgetMonthEntity::class,
        BudgetAllocationEntity::class,
        BalanceSnapshotEntity::class,
        InvestmentHoldingEntity::class,
        InvestmentTransactionEntity::class,
        LiabilityEntity::class,
        SoftDeleteTombstoneEntity::class,
        PlaidAccountLinkEntity::class,
        PlaidTransactionLinkEntity::class,
        CategoryRuleEntity::class,
        TransactionAttachmentEntity::class
    ],
    version = 4,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun netWorthDao(): NetWorthDao
    abstract fun scheduledItemDao(): ScheduledItemDao
    abstract fun plaidLinkDao(): PlaidLinkDao
    abstract fun budgetDao(): BudgetDao
    abstract fun goalDao(): GoalDao
    abstract fun categoryRuleDao(): CategoryRuleDao
    abstract fun investmentDao(): InvestmentDao
    abstract fun liabilityDao(): LiabilityDao
    abstract fun softDeleteTombstoneDao(): SoftDeleteTombstoneDao
    abstract fun transactionAttachmentDao(): TransactionAttachmentDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE transactions ADD COLUMN tags TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE category_rules ADD COLUMN renameTo TEXT")
                database.execSQL("ALTER TABLE category_rules ADD COLUMN addTags TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE transactions ADD COLUMN awaitingRefund INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE transactions ADD COLUMN refundsTransactionId TEXT")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS transaction_attachments (" +
                        "id TEXT NOT NULL PRIMARY KEY, " +
                        "transactionId TEXT NOT NULL, " +
                        "imageData BLOB NOT NULL, " +
                        "createdAt INTEGER NOT NULL" +
                    ")"
                )
            }
        }
    }
}
