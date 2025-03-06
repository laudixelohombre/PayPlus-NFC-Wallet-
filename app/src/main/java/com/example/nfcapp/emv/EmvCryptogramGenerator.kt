package com.example.nfcapp.emv

import android.os.Parcel
import android.os.Parcelable
import android.util.Log
import com.example.nfcapp.data.model.Card
import com.example.nfcapp.util.HexUtil
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Generates EMV cryptograms for payment transactions.
 *
 * This class simulates the secure element functionality of an EMV card
 * by generating cryptograms (ARQC, TC) that can be used in payment transactions.
 * It implements dynamic cryptogram generation as required by EMV payment specifications.
 */
class EmvCryptogramGenerator() : Parcelable {
    private val TAG = "EmvCryptogramGenerator"
    private val random = SecureRandom()

    // Cryptogram versions
    private val VERSION_10 = "10"
    private val VERSION_11 = "11"

    // Cipher algorithms
    private val AES_ALGORITHM = "AES"
    private val HMAC_SHA256_ALGORITHM = "HmacSHA256"
    private val TRIPLE_DES_ALGORITHM = "DESede/ECB/NoPadding"

    constructor(parcel: Parcel) : this() {
    }

    /**
     * Generate a transaction cryptogram (ARQC, TC, or AAC)
     *
     * @param card The card data
     * @param transaction The transaction data
     * @param atc The application transaction counter
     * @param cryptogramType The type of cryptogram to generate (ARQC, TC, AAC)
     * @param isLastCryptogram Whether this is the last cryptogram in the transaction
     * @return The generated cryptogram as a byte array
     */
    fun generateCryptogram(
        card: Card,
        transaction: TransactionData,
        atc: ByteArray,
        cryptogramType: String,
        isLastCryptogram: Boolean
    ): ByteArray {
        // Log cryptogram generation
        Log.d(TAG, "Generating $cryptogramType for transaction: ${transaction.id}")

        // Get or derive the application cryptogram key
        val cryptogramKey = getCryptogramKey(card)

        // Generate the cryptogram input data
        val inputData = generateCryptogramInputData(card, transaction, atc, cryptogramType)
        Log.d(TAG, "Cryptogram input data: ${HexUtil.toHexString(inputData)}")

        // Generate the cryptogram using AES
        val cryptogram = generateAESCryptogram(cryptogramKey, inputData)
        Log.d(TAG, "Generated cryptogram: ${HexUtil.toHexString(cryptogram)}")

        // Update the ATC if this is the last cryptogram in the transaction
        if (isLastCryptogram) {
            card.applicationTransactionCounter = bytesToInt(atc)
        }

        return cryptogram
    }

    /**
     * Generate a hash value for Transaction Certificate
     */
    fun generateHash(card: Card, transaction: TransactionData): ByteArray {
        // Generate transaction data for hashing
        val transactionData = generateTransactionDataForHashing(card, transaction)

        // Use SHA-256 to create a hash
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(transactionData)

        // Return first 8 bytes of the hash
        val result = ByteArray(8)
        System.arraycopy(hash, 0, result, 0, 8)

        return result
    }

    /**
     * Generate Issuer Application Data (IAD)
     */
    fun generateIssuerApplicationData(card: Card, cryptogramType: String): ByteArray {
        // Format: <Version><CVR><Counter><Other data>
        // This is a simplified implementation

        val version = HexUtil.toByteArray(VERSION_11)
        val cvr = generateCVR(cryptogramType)
        val derivationCounter = byteArrayOf(0x00, 0x01)

        // Additional proprietary data
        val additionalData = ByteArray(8)
        random.nextBytes(additionalData)

        return HexUtil.concatenate(version, cvr, derivationCounter, additionalData)
    }

    /**
     * Generate Card Verification Results (CVR)
     */
    private fun generateCVR(cryptogramType: String): ByteArray {
        // CVR format depends on card type and issuer
        // This is a simplified implementation
        val cvr = ByteArray(4)

        // First byte indicates cryptogram type
        cvr[0] = when (cryptogramType) {
            "ARQC" -> 0x03
            "TC" -> 0x40
            "AAC" -> 0x00
            else -> 0x00
        }.toByte()

        // Other bytes for card verification methods, PIN verification, etc.
        cvr[1] = 0x00 // No offline PIN
        cvr[2] = 0x00 // No offline PIN retry
        cvr[3] = 0x00 // No PIN bypass

        return cvr
    }

    /**
     * Get the cryptogram key for this card
     */
    private fun getCryptogramKey(card: Card): ByteArray {
        // In a real implementation, this would use the card's master key and derivation data
        // For this demo, we'll generate a "secure" key based on card data

        if (card.applicationCryptogramKey.isNotEmpty()) {
            // Use existing key if available
            return HexUtil.toByteArray(card.applicationCryptogramKey)
        }

        // Generate a key based on card data
        val cardData = "${card.cardNumber}${card.expiryDate}${card.cvv}"
        val keyMaterial = MessageDigest.getInstance("SHA-256").digest(cardData.toByteArray())

        // Take first 16 bytes for AES-128 key
        val key = ByteArray(16)
        System.arraycopy(keyMaterial, 0, key, 0, 16)

        // Store the key for future use
        card.applicationCryptogramKey = HexUtil.toHexString(key)

        return key
    }

    /**
     * Generate the input data for cryptogram calculation
     */
    private fun generateCryptogramInputData(
        card: Card,
        transaction: TransactionData,
        atc: ByteArray,
        cryptogramType: String
    ): ByteArray {
        // Format for input data (simplified):
        // - 8 bytes: Primary Account Number (PAN) hash
        // - 2 bytes: Application Transaction Counter (ATC)
        // - 6 bytes: Amount
        // - 2 bytes: Currency Code
        // - 2 bytes: Country Code
        // - 1 byte: Cryptogram Type
        // - 4 bytes: Unpredictable Number

        val buffer = ByteArray(25)
        var offset = 0

        // PAN hash (8 bytes)
        val panHash = generatePanHash(card.cardNumber)
        System.arraycopy(panHash, 0, buffer, offset, 8)
        offset += 8

        // ATC (2 bytes)
        System.arraycopy(atc, 0, buffer, offset, 2)
        offset += 2

        // Amount (6 bytes, padded with zeros)
        val amount = transaction.amount.padStart(12, '0')
        val amountBytes = HexUtil.toByteArray(amount)
        System.arraycopy(amountBytes, 0, buffer, offset, 6)
        offset += 6

        // Currency Code (2 bytes)
        val currencyCode = transaction.currencyCode.padStart(4, '0')
        val currencyBytes = HexUtil.toByteArray(currencyCode)
        System.arraycopy(currencyBytes, 0, buffer, offset, 2)
        offset += 2

        // Country Code (2 bytes)
        val countryCode = transaction.countryCode.padStart(4, '0')
        val countryBytes = HexUtil.toByteArray(countryCode)
        System.arraycopy(countryBytes, 0, buffer, offset, 2)
        offset += 2

        // Cryptogram Type (1 byte)
        buffer[offset] = when (cryptogramType) {
            "ARQC" -> 0x80.toByte()
            "TC" -> 0x40.toByte()
            "AAC" -> 0x00.toByte()
            else -> 0x00.toByte()
        }
        offset += 1

        // Unpredictable Number (4 bytes)
        val unpredictableNumberBytes = if (transaction.unpredictableNumber.isNotEmpty()) {
            HexUtil.toByteArray(transaction.unpredictableNumber)
        } else {
            // Generate a random UN if not provided
            val randomBytes = ByteArray(4)
            random.nextBytes(randomBytes)
            randomBytes
        }
        System.arraycopy(unpredictableNumberBytes, 0, buffer, offset, 4)

        return buffer
    }

    /**
     * Generate transaction data for hashing (TC)
     */
    private fun generateTransactionDataForHashing(
        card: Card,
        transaction: TransactionData
    ): ByteArray {
        // Format for transaction data (simplified):
        // - 8 bytes: PAN hash
        // - 6 bytes: Amount
        // - 2 bytes: Currency Code
        // - 4 bytes: Date/Time
        // - 4 bytes: Transaction Counter

        val buffer = ByteArray(24)
        var offset = 0

        // PAN hash (8 bytes)
        val panHash = generatePanHash(card.cardNumber)
        System.arraycopy(panHash, 0, buffer, offset, 8)
        offset += 8

        // Amount (6 bytes, padded with zeros)
        val amount = transaction.amount.padStart(12, '0')
        val amountBytes = HexUtil.toByteArray(amount)
        System.arraycopy(amountBytes, 0, buffer, offset, 6)
        offset += 6

        // Currency Code (2 bytes)
        val currencyCode = transaction.currencyCode.padStart(4, '0')
        val currencyBytes = HexUtil.toByteArray(currencyCode)
        System.arraycopy(currencyBytes, 0, buffer, offset, 2)
        offset += 2

        // Date/Time (4 bytes)
        val timestamp = (transaction.timestamp / 1000).toInt()
        buffer[offset] = (timestamp shr 24).toByte()
        buffer[offset + 1] = (timestamp shr 16).toByte()
        buffer[offset + 2] = (timestamp shr 8).toByte()
        buffer[offset + 3] = timestamp.toByte()
        offset += 4

        // Transaction Counter (4 bytes)
        val counter = card.applicationTransactionCounter
        buffer[offset] = (counter shr 24).toByte()
        buffer[offset + 1] = (counter shr 16).toByte()
        buffer[offset + 2] = (counter shr 8).toByte()
        buffer[offset + 3] = counter.toByte()

        return buffer
    }

    /**
     * Generate a hash of the PAN for use in cryptogram calculation
     */
    private fun generatePanHash(pan: String): ByteArray {
        // Remove spaces from PAN
        val cleanPan = pan.replace(" ", "")

        // Create a SHA-256 hash of the PAN
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(cleanPan.toByteArray())

        // Use the first 8 bytes of the hash
        val result = ByteArray(8)
        System.arraycopy(hash, 0, result, 0, 8)

        return result
    }

    /**
     * Generate an AES-based cryptogram
     */
    private fun generateAESCryptogram(key: ByteArray, data: ByteArray): ByteArray {
        try {
            // Create an AES cipher
            val cipher = Cipher.getInstance("AES/ECB/NoPadding")
            val secretKey = SecretKeySpec(key, AES_ALGORITHM)

            // Initialize for encryption
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)

            // Encrypt the data
            val encrypted = cipher.doFinal(data)

            // Take the first 8 bytes of the result as the cryptogram
            val result = ByteArray(8)
            System.arraycopy(encrypted, 0, result, 0, 8)

            return result
        } catch (e: Exception) {
            Log.e(TAG, "Error generating AES cryptogram: ${e.message}")

            // Fallback to HMAC if AES fails
            return generateHMACCryptogram(key, data)
        }
    }

    /**
     * Generate an HMAC-based cryptogram (fallback method)
     */
    private fun generateHMACCryptogram(key: ByteArray, data: ByteArray): ByteArray {
        try {
            // Create an HMAC-SHA256 instance
            val hmac = Mac.getInstance(HMAC_SHA256_ALGORITHM)
            val secretKey = SecretKeySpec(key, HMAC_SHA256_ALGORITHM)

            // Initialize with the key
            hmac.init(secretKey)

            // Calculate HMAC
            val hmacResult = hmac.doFinal(data)

            // Take the first 8 bytes as the cryptogram
            val result = ByteArray(8)
            System.arraycopy(hmacResult, 0, result, 0, 8)

            return result
        } catch (e: Exception) {
            Log.e(TAG, "Error generating HMAC cryptogram: ${e.message}")

            // Emergency fallback - never should reach here in production
            val fallback = ByteArray(8)
            random.nextBytes(fallback)
            return fallback
        }
    }

    /**
     * Convert a 2-byte array to an integer
     */
    private fun bytesToInt(bytes: ByteArray): Int {
        if (bytes.size < 2) return 0
        return ((bytes[0].toInt() and 0xFF) shl 8) or (bytes[1].toInt() and 0xFF)
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {

    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<EmvCryptogramGenerator> {
        override fun createFromParcel(parcel: Parcel): EmvCryptogramGenerator {
            return EmvCryptogramGenerator(parcel)
        }

        override fun newArray(size: Int): Array<EmvCryptogramGenerator?> {
            return arrayOfNulls(size)
        }
    }
}
