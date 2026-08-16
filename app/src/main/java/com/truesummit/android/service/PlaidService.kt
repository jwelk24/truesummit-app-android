package com.truesummit.android.service

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*

interface PlaidApi {
    @POST("api/link/token/create")
    suspend fun createLinkToken(@Body body: Map<String, String>): LinkTokenResponse

    @POST("api/item/public_token/exchange")
    suspend fun exchangePublicToken(@Body body: Map<String, String>): ExchangeResponse

    @GET("api/accounts")
    suspend fun getAccounts(@Header("X-Plaid-Access-Token") accessToken: String): AccountsResponse

    @POST("api/transactions/sync")
    suspend fun syncTransactions(
        @Header("X-Plaid-Access-Token") accessToken: String,
        @Body body: SyncBody
    ): SyncResponse

    @GET("api/investments/holdings")
    suspend fun getHoldings(@Header("X-Plaid-Access-Token") accessToken: String): HoldingsResponse

    @GET("api/investments/transactions")
    suspend fun getInvestmentTransactions(@Header("X-Plaid-Access-Token") accessToken: String): InvestmentTransactionsResponse

    @GET("api/liabilities")
    suspend fun getLiabilities(@Header("X-Plaid-Access-Token") accessToken: String): LiabilitiesResponse
}

// hostedLinkUrl is null on Android: the native SDK branch creates no hosted
// link. Declaring it non-null was a lie Gson would not catch — it populates
// fields reflectively and would leave a null in a String, blowing up at the
// first read rather than at parse time.
data class LinkTokenResponse(val linkToken: String, val hostedLinkUrl: String?)
data class ExchangeResponse(val accessToken: String, val itemId: String)
data class AccountsResponse(val item: PlaidItem, val accounts: List<PlaidAccount>)
data class PlaidItem(val item_id: String, val institution_id: String?)
data class PlaidAccount(
    val account_id: String,
    val name: String,
    val official_name: String?,
    val mask: String?,
    val type: String,
    val subtype: String?,
    val balances: Balances
)
data class Balances(val available: Double?, val current: Double?, val iso_currency_code: String?)

data class SyncBody(val cursor: String?)
data class SyncResponse(
    val added: List<PlaidTransaction>,
    val modified: List<PlaidTransaction>,
    val removed: List<RemovedTransaction>,
    val nextCursor: String?
)
data class PlaidTransaction(
    val transaction_id: String,
    val account_id: String,
    val amount: Double,
    val date: String,
    val name: String,
    val merchant_name: String? = null,
    val pending: Boolean = false,
    val personal_finance_category: PlaidPfc? = null
)
data class PlaidPfc(val primary: String?, val detailed: String?)
data class RemovedTransaction(val transaction_id: String)

// Holdings
data class HoldingsResponse(
    val holdings: List<PlaidHolding>,
    val securities: List<PlaidSecurity>
)
data class PlaidHolding(
    val account_id: String,
    val security_id: String,
    val quantity: Double,
    val institution_price: Double,
    val institution_value: Double,
    val cost_basis: Double?,
    val iso_currency_code: String?,
    val institution_price_as_of: String?
)
data class PlaidSecurity(
    val security_id: String,
    val ticker_symbol: String?,
    val name: String?,
    val type: String?,
    val is_cash_equivalent: Boolean
)

// Investment Transactions
data class InvestmentTransactionsResponse(
    // Server sends camelCase here, unlike Plaid's own snake_case payloads.
    val investmentTransactions: List<PlaidInvestmentTransaction>,
    val securities: List<PlaidSecurity>
)
data class PlaidInvestmentTransaction(
    val investment_transaction_id: String,
    val account_id: String,
    val security_id: String?,
    val date: String,
    val name: String,
    val amount: Double,
    val fees: Double?,
    val quantity: Double?,
    val price: Double?,
    val type: String?,
    val subtype: String?,
    val iso_currency_code: String?
)

// Liabilities
data class LiabilitiesResponse(val liabilities: PlaidLiabilities)
data class PlaidLiabilities(
    val credit: List<PlaidCreditLiability>?,
    val mortgage: List<PlaidMortgageLiability>?,
    val student: List<PlaidStudentLiability>?
)
data class PlaidCreditLiability(
    val account_id: String?,
    val aprs: List<PlaidApr>?,
    val last_statement_balance: Double?,
    val last_statement_issue_date: String?,
    val minimum_payment_amount: Double?,
    val next_payment_due_date: String?,
    val last_payment_amount: Double?,
    val last_payment_date: String?
)
data class PlaidApr(val apr_type: String?, val apr_percentage: Double?)
data class PlaidMortgageLiability(
    val account_id: String?,
    val next_monthly_payment: Double?,
    val next_payment_due_date: String?,
    val last_payment_amount: Double?,
    val last_payment_date: String?,
    val interest_rate: PlaidInterestRate?,
    val origination_principal_amount: Double?,
    val origination_date: String?,
    val maturity_date: String?,
    val loan_type_description: String?,
    val loan_term: String?
)
data class PlaidInterestRate(val percentage: Double?, val type: String?)
data class PlaidStudentLiability(
    val account_id: String?,
    val last_statement_balance: Double?,
    val last_statement_issue_date: String?,
    val minimum_payment_amount: Double?,
    val next_payment_due_date: String?,
    val last_payment_amount: Double?,
    val last_payment_date: String?,
    val interest_rate_percentage: Double?,
    val origination_principal_amount: Double?,
    val origination_date: String?,
    val loan_name: String?
)

object PlaidService {
    // The Plaid Edge Function, shared with the iOS app — same Supabase
    // project, same deployment. Note the endpoint paths above are relative:
    // a leading slash would make Retrofit discard the /functions/v1/plaid
    // prefix and call the project root.
    private val BASE_URL = "${SupabaseService.FUNCTIONS_URL}/plaid/"

    val api: PlaidApi = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(PlaidApi::class.java)
}
