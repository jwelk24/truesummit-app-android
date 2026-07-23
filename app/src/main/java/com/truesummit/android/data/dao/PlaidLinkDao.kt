package com.truesummit.android.data.dao

import androidx.room.*
import com.truesummit.android.data.entity.PlaidAccountLinkEntity
import com.truesummit.android.data.entity.PlaidTransactionLinkEntity
import kotlinx.coroutines.flow.Flow
import java.util.*

@Dao
interface PlaidLinkDao {
    @Query("SELECT * FROM plaid_account_links WHERE plaidAccountId = :plaidAccountId")
    suspend fun getAccountLink(plaidAccountId: String): PlaidAccountLinkEntity?

    @Query("SELECT * FROM plaid_account_links WHERE accountModelId = :accountModelId")
    suspend fun getAccountLinkByModelId(accountModelId: UUID): PlaidAccountLinkEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccountLink(link: PlaidAccountLinkEntity)

    @Query("SELECT * FROM plaid_transaction_links WHERE plaidTransactionId = :plaidTransactionId")
    suspend fun getTransactionLink(plaidTransactionId: String): PlaidTransactionLinkEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactionLink(link: PlaidTransactionLinkEntity)

    @Query("DELETE FROM plaid_transaction_links WHERE plaidTransactionId = :plaidTransactionId")
    suspend fun deleteTransactionLink(plaidTransactionId: String)

    @Query("SELECT * FROM plaid_account_links")
    suspend fun getAllAccountLinks(): List<PlaidAccountLinkEntity>

    @Query("SELECT COUNT(*) FROM plaid_account_links")
    fun getAccountLinkCountFlow(): Flow<Int>

    @Query("SELECT * FROM plaid_transaction_links")
    suspend fun getAllTransactionLinks(): List<PlaidTransactionLinkEntity>
}
