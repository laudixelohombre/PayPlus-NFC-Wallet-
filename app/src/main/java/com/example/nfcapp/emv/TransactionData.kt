package com.example.nfcapp.emv

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Data model representing an EMV payment transaction.
 *
 * This class stores all the information related to a payment transaction,
 * including cryptogram data, transaction amount, merchant info, etc.
 * It is used both for the EMV processing and for storing transaction history.
 */
@Entity(tableName = "transactions")
data class TransactionData(
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0,

    // Card reference
    var cardId: Long = 0,

    // Transaction basics
    var timestamp: Long = System.currentTimeMillis(),
    var amount: String = "0",
    var currencyCode: String = "840", // Default USD
    var countryCode: String = "840", // Default USA
    var transactionType: String = "00", // Default purchase

    // EMV specific data
    var aid: String = "", // Application Identifier
    var terminalId: String = "",
    var merchantId: String = "",
    var merchantName: String = "",
    var merchantCity: String = "",

    // Cryptogram data
    var cryptogram: String = "", // ARQC/TC value
    var cryptogramType: String = "", // ARQC, TC, or AAC
    var unpredictableNumber: String = "", // Random challenge from terminal

    // Transaction results
    var approved: Boolean = false,
    var responseCode: String = "",
    var authorizationCode: String = "",

    // Additional data for display
    var formattedAmount: String = ""
) {
    /**
     * Format the transaction date for display
     */
    fun getFormattedDate(): String {
        val date = Date(timestamp)
        val formatter = SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault())
        return formatter.format(date)
    }

    /**
     * Format the transaction amount for display
     */
    fun getFormattedAmount(): String {
        if (formattedAmount.isNotEmpty()) {
            return formattedAmount
        }

        // If not already formatted, use basic formatting
        var formattedValue = amount
        if (formattedValue.length > 2) {
            // Add decimal point before last two digits (cents)
            formattedValue = formattedValue.substring(0, formattedValue.length - 2) +
                    "." +
                    formattedValue.substring(formattedValue.length - 2)
        } else if (formattedValue.length == 1) {
            formattedValue = "0.0$formattedValue"
        } else if (formattedValue.length == 2) {
            formattedValue = "0.$formattedValue"
        }

        // Add currency symbol based on currency code
        val currencySymbol = when (currencyCode) {
            "840" -> "$" // USD
            "978" -> "€" // EUR
            "826" -> "£" // GBP
            "392" -> "¥" // JPY
            else -> "$" // Default to USD
        }

        return "$currencySymbol$formattedValue"
    }

    /**
     * Get transaction status text
     */
    fun getStatusText(): String {
        return if (approved) "Approved" else "Declined"
    }

    /**
     * Get a summary of the transaction for display
     */
    fun getSummary(): String {
        return "$merchantName • ${getFormattedAmount()} • ${getStatusText()}"
    }
}