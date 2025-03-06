package com.example.nfcapp.network

import android.util.Log
import com.example.nfcapp.emv.TransactionData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.random.Random

/**
 * Simulated Payment API for interacting with payment networks.
 *
 * This class simulates interaction with payment networks like Visa and Mastercard.
 * It provides methods for authorizing transactions and supports a "force approval"
 * mode for testing and demos.
 */
class PaymentApi private constructor() {

    private val TAG = "PaymentApi"

    // Network constants
    private val NETWORK_VISA = "VISA"
    private val NETWORK_MASTERCARD = "MASTERCARD"
    private val NETWORK_AMEX = "AMEX"
    private val NETWORK_DISCOVER = "DISCOVER"

    // Response codes
    private val RESP_APPROVED = "00"
    private val RESP_DECLINED_INSUFFICIENT_FUNDS = "51"
    private val RESP_DECLINED_EXPIRED_CARD = "54"
    private val RESP_DECLINED_SUSPECTED_FRAUD = "59"
    private val RESP_DECLINED_LOST_CARD = "41"
    private val RESP_DECLINED_STOLEN_CARD = "43"
    private val RESP_DECLINED_RESTRICTED_CARD = "62"
    private val RESP_ERROR_INVALID_TRANSACTION = "12"
    private val RESP_ERROR_PROCESSING = "96"
    private val RESP_ERROR_FORMAT = "30"

    /**
     * Response from payment network
     */
    data class PaymentResponse(
        val approved: Boolean,
        val responseCode: String,
        val responseMessage: String,
        val authorizationCode: String = "",
        val transactionId: String = UUID.randomUUID().toString(),
        val network: String = ""
    )

    /**
     * Authorize a transaction with the payment network
     *
     * @param transaction The transaction to authorize
     * @return PaymentResponse with authorization result
     */
    suspend fun authorizeTransaction(transaction: TransactionData): PaymentResponse =
        withContext(Dispatchers.IO) {
            Log.d(
                TAG,
                "Authorizing transaction: ${transaction.amount} ${
                    getNetworkForTransaction(transaction)
                }"
            )

            // Simulate network delay (300-1500ms)
            val networkDelay = Random.nextLong(300, 1500)
            delay(networkDelay)

            // Determine the result based on various factors
            val approved = shouldApproveTransaction(transaction)
            val responseCode = if (approved) RESP_APPROVED else getDeclineCode(transaction)
            val responseMessage = getResponseMessage(responseCode)
            val authCode = if (approved) generateAuthCode() else ""
            val network = getNetworkForTransaction(transaction)

            // Log the response
            Log.d(
                TAG,
                "Authorization result: approved=$approved, code=$responseCode, message=$responseMessage"
            )

            return@withContext PaymentResponse(
                approved = approved,
                responseCode = responseCode,
                responseMessage = responseMessage,
                authorizationCode = authCode,
                network = network
            )
        }

    /**
     * Force approve a transaction (bypasses normal authorization rules)
     *
     * @param transaction The transaction to force approve
     * @return PaymentResponse with forced approval
     */
    suspend fun forceApproveTransaction(transaction: TransactionData): PaymentResponse =
        withContext(Dispatchers.IO) {
            Log.d(TAG, "Force approving transaction: ${transaction.amount}")

            // Simulate a short network delay (200-500ms)
            val networkDelay = Random.nextLong(200, 500)
            delay(networkDelay)

            // Always approve with success code
            val network = getNetworkForTransaction(transaction)

            return@withContext PaymentResponse(
                approved = true,
                responseCode = RESP_APPROVED,
                responseMessage = "Approved (Forced)",
                authorizationCode = generateAuthCode(),
                network = network
            )
        }

    /**
     * Determine if a transaction should be approved based on various factors
     */
    private fun shouldApproveTransaction(transaction: TransactionData): Boolean {
        // Extract the amount as a numeric value
        val amount = transaction.amount.toDoubleOrNull() ?: 0.0

        // Higher amounts have a slightly lower approval rate
        val baseApprovalRate = when {
            amount < 1000 -> 95  // 95% for small amounts
            amount < 5000 -> 85  // 85% for medium amounts
            amount < 10000 -> 70 // 70% for larger amounts
            else -> 50           // 50% for very large amounts
        }

        // Random approval based on the rate
        return Random.nextInt(100) < baseApprovalRate
    }

    /**
     * Get a decline code for rejected transactions
     */
    private fun getDeclineCode(transaction: TransactionData): String {
        // Pick a random decline reason, weighted towards insufficient funds
        val random = Random.nextInt(100)

        return when {
            random < 50 -> RESP_DECLINED_INSUFFICIENT_FUNDS  // 50% of declines
            random < 70 -> RESP_DECLINED_SUSPECTED_FRAUD     // 20% of declines
            random < 80 -> RESP_DECLINED_EXPIRED_CARD        // 10% of declines
            random < 85 -> RESP_DECLINED_RESTRICTED_CARD     // 5% of declines
            random < 90 -> RESP_DECLINED_LOST_CARD           // 5% of declines
            random < 95 -> RESP_DECLINED_STOLEN_CARD         // 5% of declines
            else -> RESP_ERROR_PROCESSING                    // 5% of declines
        }
    }

    /**
     * Get a human-readable message for a response code
     */
    private fun getResponseMessage(responseCode: String): String {
        return when (responseCode) {
            RESP_APPROVED -> "Approved"
            RESP_DECLINED_INSUFFICIENT_FUNDS -> "Declined: Insufficient Funds"
            RESP_DECLINED_EXPIRED_CARD -> "Declined: Expired Card"
            RESP_DECLINED_SUSPECTED_FRAUD -> "Declined: Suspected Fraud"
            RESP_DECLINED_LOST_CARD -> "Declined: Lost Card"
            RESP_DECLINED_STOLEN_CARD -> "Declined: Stolen Card"
            RESP_DECLINED_RESTRICTED_CARD -> "Declined: Restricted Card"
            RESP_ERROR_INVALID_TRANSACTION -> "Error: Invalid Transaction"
            RESP_ERROR_PROCESSING -> "Error: Processing Error"
            RESP_ERROR_FORMAT -> "Error: Format Error"
            else -> "Unknown Response: $responseCode"
        }
    }

    /**
     * Generate a random authorization code
     */
    private fun generateAuthCode(): String {
        val charPool: List<Char> = ('A'..'Z') + ('0'..'9')
        return (1..6)
            .map { Random.nextInt(0, charPool.size) }
            .map(charPool::get)
            .joinToString("")
    }

    /**
     * Determine the payment network for a transaction
     */
    private fun getNetworkForTransaction(transaction: TransactionData): String {
        // Derive network from AID if available
        val aid = transaction.aid.uppercase()

        return when {
            aid.startsWith("A000000003") -> NETWORK_VISA
            aid.startsWith("A000000004") -> NETWORK_MASTERCARD
            aid.startsWith("A000000025") -> NETWORK_AMEX
            aid.startsWith("A000000152") -> NETWORK_DISCOVER
            else -> "UNKNOWN"
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: PaymentApi? = null

        fun getInstance(): PaymentApi {
            return INSTANCE ?: synchronized(this) {
                val instance = PaymentApi()
                INSTANCE = instance
                instance
            }
        }
    }
}