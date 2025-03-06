package com.example.nfcapp.data.model

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import java.util.Date

/**
 * Data model for a payment card.
 *
 * This class stores all information related to a payment card including
 * card number, expiry date, CVV, and card network. Card data is used for
 * both storage in the Room database and for card emulation during payment.
 */
@Entity(tableName = "cards")
data class Card @JvmOverloads constructor(
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0,

    // Card Details
    var cardNumber: String = "",
    var expiryDate: String = "", // Format: MM/YY
    var cvv: String = "",
    var cardholderName: String = "",

    // Card Metadata
    var issuerName: String = "",
    var cardNetwork: String = "", // VISA, MASTERCARD, etc.
    var cardType: String = "", // CREDIT, DEBIT
    var lastUsed: Long = 0,
    var lastTransaction: Long = 0,
    var isDefault: Boolean = false,
    var isEnabled: Boolean = true,

    // EMV Specific Data
    var pan: String = "", // Primary Account Number (same as card number without spaces)
    var panSequence: String = "01", // PAN sequence number
    var applicationTransactionCounter: Int = 0, // ATC counter
    var issuerApplicationData: String = "", // IAD for EMV
    var applicationCryptogramKey: String = "", // Derived key for cryptogram generation

    // Timestamps
    var dateAdded: Long = Date().time,
    var dateModified: Long = Date().time
) {
    /**
     * Get the last 4 digits of the card number
     */
    fun getLastFourDigits(): String {
        if (cardNumber.length < 4) return cardNumber
        return cardNumber.takeLast(4)
    }

    /**
     * Get masked card number (e.g., **** **** **** 1234)
     */
    fun getMaskedCardNumber(): String {
        if (cardNumber.isBlank()) return ""

        val lastFour = getLastFourDigits()
        val builder = StringBuilder()

        // Determine appropriate masking based on card length
        when {
            cardNumber.length == 16 -> {
                // Standard 16-digit card (VISA, MC, etc.)
                builder.append("**** **** **** $lastFour")
            }

            cardNumber.length == 15 -> {
                // AMEX
                builder.append("**** ****** $lastFour")
            }

            else -> {
                // For other card types
                val maskedPart = "*".repeat(cardNumber.length - 4)
                builder.append("$maskedPart$lastFour")
            }
        }

        return builder.toString()
    }

    /**
     * Get card network icon resource ID
     */
    fun getCardNetworkIcon(): Int {
        // Determine card network based on BIN (first digits of card)
        return when {
            cardNumber.startsWith("4") -> {
                // R.drawable.ic_visa
                0 // Placeholder, replace with actual resource ID
            }

            cardNumber.startsWith("5") -> {
                // R.drawable.ic_mastercard
                0 // Placeholder, replace with actual resource ID
            }

            cardNumber.startsWith("3") -> {
                // AMEX
                0 // Placeholder, replace with actual resource ID
            }

            cardNumber.startsWith("6") -> {
                // Discover
                0 // Placeholder, replace with actual resource ID
            }

            else -> {
                // Generic card
                0 // Placeholder, replace with actual resource ID
            }
        }
    }

    /**
     * Get color for card background based on network
     */
    fun getCardColor(): Int {
        // Return different colors based on card network
        return when {
            cardNumber.startsWith("4") -> {
                // VISA blue
                0xFF1A1F71.toInt()
            }

            cardNumber.startsWith("5") -> {
                // Mastercard red
                0xFFFF5F00.toInt()
            }

            cardNumber.startsWith("3") -> {
                // AMEX green
                0xFF006FCF.toInt()
            }

            cardNumber.startsWith("6") -> {
                // Discover orange
                0xFFFF6600.toInt()
            }

            else -> {
                // Default color
                0xFF2D2D2D.toInt()
            }
        }
    }

    /**
     * Get formatted expiry date (MM/YY)
     */
    fun getFormattedExpiryDate(): String {
        return expiryDate
    }

    /**
     * Determine if the card has expired
     */
    fun isExpired(): Boolean {
        if (expiryDate.length != 5) return false // Invalid format

        try {
            val parts = expiryDate.split("/")
            val expiryMonth = parts[0].toInt()
            val expiryYear = 2000 + parts[1].toInt() // Convert YY to YYYY

            val calendar = java.util.Calendar.getInstance()
            val currentMonth =
                calendar.get(java.util.Calendar.MONTH) + 1 // Calendar months are 0-based
            val currentYear = calendar.get(java.util.Calendar.YEAR)

            return (currentYear > expiryYear) ||
                    (currentYear == expiryYear && currentMonth > expiryMonth)
        } catch (e: Exception) {
            return false
        }
    }

    /**
     * Format the card number with spaces for display
     */
    fun getFormattedCardNumber(): String {
        if (cardNumber.isBlank()) return ""

        val cleaned = cardNumber.replace(" ", "")
        val result = StringBuilder()

        for (i in cleaned.indices) {
            if (i > 0 && i % 4 == 0) {
                result.append(" ")
            }
            result.append(cleaned[i])
        }

        return result.toString()
    }

    /**
     * Increment and get the next Application Transaction Counter value
     * This is used for generating unique cryptograms for each transaction
     */
    fun getNextATC(): Int {
        applicationTransactionCounter++
        return applicationTransactionCounter
    }

    /**
     * Validate the card data
     */
    fun isValid(): Boolean {
        // Card number must be 15-19 digits
        if (cardNumber.replace(" ", "").length !in 15..19) return false

        // Expiry date must be in MM/YY format
        if (!expiryDate.matches(Regex("^(0[1-9]|1[0-2])/[0-9]{2}$"))) return false

        // Check if card has expired
        if (isExpired()) return false

        // CVV must be 3-4 digits
        if (cvv.length !in 3..4) return false

        return true
    }

    /**
     * Check if this is a valid EMV card (has all required data for emulation)
     */
    fun isValidForEmulation(): Boolean {
        return isValid() && !isExpired() && isEnabled
    }
}