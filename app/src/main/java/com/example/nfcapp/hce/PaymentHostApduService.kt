package com.example.nfcapp.hce

import android.content.Intent
import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import android.util.Log
import com.example.nfcapp.data.repository.CardRepository
import com.example.nfcapp.data.repository.TransactionRepository
import com.example.nfcapp.emv.ApduCommand
import com.example.nfcapp.emv.ApduResponse
import com.example.nfcapp.emv.EmvCryptogramGenerator
import com.example.nfcapp.emv.EmvTags
import com.example.nfcapp.emv.TransactionData
import com.example.nfcapp.network.PaymentApi
import com.example.nfcapp.util.BiometricHelper
import com.example.nfcapp.util.HexUtil
import com.example.nfcapp.util.SharedPrefsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Host-based Card Emulation service for EMV payment processing.
 * Handles APDU commands from payment terminals and generates appropriate responses
 * including EMV cryptograms (TC, ARQC) and manages the transaction flow.
 */
class PaymentHostApduService : HostApduService() {

    private val TAG = "PaymentHostApduService"

    // Command status codes
    private val STATUS_SUCCESS = byteArrayOf(0x90.toByte(), 0x00.toByte())
    private val STATUS_FAILED = byteArrayOf(0x6A.toByte(), 0x82.toByte())
    private val STATUS_WRONG_LENGTH = byteArrayOf(0x67.toByte(), 0x00.toByte())
    private val STATUS_WRONG_CLA = byteArrayOf(0x6E.toByte(), 0x00.toByte())

    // Current transaction data
    private var currentTransaction: TransactionData? = null

    // EMV Select Command APDU header
    private val SELECT_COMMAND = byteArrayOf(
        0x00.toByte(), // CLA
        0xA4.toByte(), // INS
        0x04.toByte(), // P1
        0x00.toByte()  // P2
    )

    // GET PROCESSING OPTIONS (GPO) command header
    private val GPO_COMMAND = byteArrayOf(
        0x80.toByte(), // CLA
        0xA8.toByte(), // INS
        0x00.toByte(), // P1
        0x00.toByte()  // P2
    )

    // READ RECORD command header
    private val READ_RECORD_COMMAND = byteArrayOf(
        0x00.toByte(), // CLA
        0xB2.toByte(), // INS
        // P1 and P2 vary based on record
    )

    // GENERATE AC command header (for ARQC, TC, etc.)
    private val GENERATE_AC_COMMAND = byteArrayOf(
        0x80.toByte(), // CLA
        0xAE.toByte(), // INS
        // P1 depends on cryptogram type
        0x00.toByte()  // P2
    )

    // Verification method indicator bits
    private val VERIFY_NONE = 0x00.toByte()
    private val VERIFY_BIOMETRIC = 0x02.toByte()

    // Selected AID (Application Identifier)
    private var selectedAid: ByteArray? = null

    // Active card data
    private var currentCardId: Long = -1

    // Indicates if force approval is enabled
    private var forceApprovalEnabled = false

    private lateinit var cardRepository: CardRepository
    private lateinit var transactionRepository: TransactionRepository
    private lateinit var cryptogramGenerator: EmvCryptogramGenerator
    private lateinit var prefsManager: SharedPrefsManager
    private lateinit var biometricHelper: BiometricHelper
    private lateinit var paymentApi: PaymentApi

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "PaymentHostApduService created")

        // Initialize repositories and helpers
        cardRepository = CardRepository.getInstance(this)
        transactionRepository = TransactionRepository.getInstance(this)
        cryptogramGenerator = EmvCryptogramGenerator()
        prefsManager = SharedPrefsManager.getInstance(this)
        biometricHelper = BiometricHelper(this)
        paymentApi = PaymentApi.getInstance()

        // Get current card ID and force approval setting
        currentCardId = prefsManager.getActiveCardId()
        forceApprovalEnabled = prefsManager.isForceApprovalEnabled()
    }

    override fun processCommandApdu(commandApdu: ByteArray, extras: Bundle?): ByteArray {
        Log.d(TAG, "Received APDU command: ${HexUtil.toHexString(commandApdu)}")

        if (commandApdu.size < 4) {
            Log.e(TAG, "Command APDU too short")
            return STATUS_WRONG_LENGTH
        }

        val command = ApduCommand(commandApdu)

        return when {
            // SELECT command (selecting payment application)
            isSelectCommand(command) -> processSelectCommand(command)

            // GET PROCESSING OPTIONS command
            isGpoCommand(command) -> processGpoCommand(command)

            // READ RECORD command
            isReadRecordCommand(command) -> processReadRecordCommand(command)

            // GENERATE AC command (for cryptogram)
            isGenerateACCommand(command) -> processGenerateACCommand(command)

            // Unknown command
            else -> {
                Log.w(TAG, "Unknown command: ${HexUtil.toHexString(commandApdu)}")
                STATUS_WRONG_CLA
            }
        }
    }

    /**
     * Handle SELECT command which identifies the payment application to use
     */
    private fun processSelectCommand(command: ApduCommand): ByteArray {
        Log.d(TAG, "Processing SELECT command")

        // Extract AID from command data
        val aidData = command.data
        if (aidData == null || aidData.isEmpty()) {
            Log.e(TAG, "No AID provided in SELECT command")
            return STATUS_FAILED
        }

        Log.d(TAG, "Selected AID: ${HexUtil.toHexString(aidData)}")
        selectedAid = aidData

        // Check if we have a card with matching network
        val activeCard = runBlocking {
            cardRepository.getCardById(currentCardId)
        }

        if (activeCard == null) {
            Log.e(TAG, "No active card available")
            return STATUS_FAILED
        }

        // Start a new transaction
        currentTransaction = TransactionData(
            cardId = currentCardId,
            timestamp = System.currentTimeMillis(),
            aid = HexUtil.toHexString(aidData)
        )

        // Generate File Control Information (FCI) response
        val fciTemplate = generateFciTemplate(activeCard)

        // Return FCI template with success status
        return ApduResponse(fciTemplate, STATUS_SUCCESS).toByteArray()
    }

    /**
     * Handle GET PROCESSING OPTIONS command
     */
    private fun processGpoCommand(command: ApduCommand): ByteArray {
        Log.d(TAG, "Processing GPO command")

        val pdol = command.data
        if (pdol == null || pdol.isEmpty()) {
            Log.e(TAG, "No PDOL provided in GPO command")
            return STATUS_FAILED
        }

        // Parse PDOL data to extract transaction details
        extractTransactionDetails(pdol)

        // Generate Application Interchange Profile (AIP) and Application File Locator (AFL)
        val responseData = generateProcessingOptions()

        // Return GPO response with success status
        return ApduResponse(responseData, STATUS_SUCCESS).toByteArray()
    }

    /**
     * Handle READ RECORD command which requests specific card data
     */
    private fun processReadRecordCommand(command: ApduCommand): ByteArray {
        val p1 = command.p1.toInt() and 0xFF
        val p2 = command.p2.toInt() and 0xFF

        Log.d(TAG, "Processing READ RECORD command: P1=$p1, P2=$p2")

        // The record number is in P1, and the SFI (Short File Identifier) in the 5 high bits of P2
        val recordNumber = p1
        val sfi = (p2 shr 3) and 0x1F

        // Generate appropriate record data based on SFI and record number
        val recordData = generateRecordData(sfi, recordNumber)

        return if (recordData.isEmpty()) {
            STATUS_FAILED
        } else {
            ApduResponse(recordData, STATUS_SUCCESS).toByteArray()
        }
    }

    /**
     * Handle GENERATE AC command which requests an Application Cryptogram
     */
    private fun processGenerateACCommand(command: ApduCommand): ByteArray {
        val p1 = command.p1.toInt() and 0xFF
        val cdol = command.data

        Log.d(TAG, "Processing GENERATE AC command: P1=$p1")

        if (cdol == null || cdol.isEmpty()) {
            Log.e(TAG, "No CDOL provided in GENERATE AC command")
            return STATUS_FAILED
        }

        // Extract cryptogram type from P1
        val cryptogramType = getCryptogramType(p1)
        Log.d(TAG, "Requested cryptogram type: $cryptogramType")

        // Extract additional transaction data from CDOL
        extractCdolData(cdol)

        // Set verification method - biometric or none
        val verificationMethod = if (biometricHelper.isAuthenticationRequired()) {
            VERIFY_BIOMETRIC
        } else {
            VERIFY_NONE
        }

        // Generate the requested cryptogram
        val cryptogramData = generateCryptogram(cryptogramType, verificationMethod)

        // Process transaction with payment network
        processTransaction(cryptogramType)

        return ApduResponse(cryptogramData, STATUS_SUCCESS).toByteArray()
    }

    /**
     * Process the transaction with the payment network
     */
    private fun processTransaction(cryptogramType: String) {
        currentTransaction?.let { transaction ->
            // Only process if this is a TC (Transaction Certificate) request
            if (cryptogramType == "TC") {
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        // Request authorization from payment network
                        val response = if (forceApprovalEnabled) {
                            // Force approval if enabled
                            paymentApi.forceApproveTransaction(transaction)
                        } else {
                            // Normal authorization flow
                            paymentApi.authorizeTransaction(transaction)
                        }

                        // Update transaction with response
                        transaction.approved = response.approved
                        transaction.responseCode = response.responseCode
                        transaction.authorizationCode = response.authorizationCode

                        // Save transaction to history
                        transactionRepository.insertTransaction(transaction)

                        // Broadcast transaction result
                        val intent = Intent(ACTION_TRANSACTION_COMPLETED).apply {
                            putExtra(EXTRA_TRANSACTION_ID, transaction.id)
                            putExtra(EXTRA_TRANSACTION_APPROVED, transaction.approved)
                        }
                        sendBroadcast(intent)

                    } catch (e: Exception) {
                        Log.e(TAG, "Transaction processing error: ${e.message}")
                        transaction.approved = false
                        transaction.responseCode = "Z3" // Communication error

                        // Still save failed transaction
                        transactionRepository.insertTransaction(transaction)

                        // Broadcast transaction failure
                        val intent = Intent(ACTION_TRANSACTION_COMPLETED).apply {
                            putExtra(EXTRA_TRANSACTION_ID, transaction.id)
                            putExtra(EXTRA_TRANSACTION_APPROVED, false)
                        }
                        sendBroadcast(intent)
                    }
                }
            }
        }
    }

    /**
     * Generate FCI (File Control Information) template response data
     */
    private fun generateFciTemplate(card: Card): ByteArray {
        // This would typically include things like:
        // - DF Name (AID)
        // - Application Label
        // - Application Priority Indicator
        // - FCI Proprietary Template
        // - PDOL (Processing Options Data Object List)
        // This is a simplified implementation

        val buffer = ByteArrayBuilder()

        // Start with FCI Template tag
        buffer.addTag(EmvTags.FCI_TEMPLATE, true)

        // Add DF Name (AID)
        buffer.addTag(EmvTags.DF_NAME, selectedAid!!)

        // Add Application Label
        val appLabel = "PayPlus".toByteArray()
        buffer.addTag(EmvTags.APPLICATION_LABEL, appLabel)

        // Add FCI Proprietary Template
        buffer.addTag(EmvTags.FCI_PROPRIETARY_TEMPLATE, true)

        // Add PDOL
        val pdol = generatePdol()
        buffer.addTag(EmvTags.PDOL, pdol)

        // Close FCI Proprietary Template
        buffer.closeTag()

        // Close FCI Template
        buffer.closeTag()

        return buffer.toByteArray()
    }

    /**
     * Generate PDOL (Processing Options Data Object List)
     */
    private fun generatePdol(): ByteArray {
        // Simplified PDOL that requests:
        // - Terminal Transaction Qualifiers (TTQ)
        // - Amount, Authorized
        // - Transaction Currency Code
        // - Terminal Country Code

        val buffer = ByteArrayBuilder()

        // Add TTQ tag
        buffer.addTagAndLength(EmvTags.TTQ, 4)

        // Add Amount, Authorized tag
        buffer.addTagAndLength(EmvTags.AMOUNT_AUTHORIZED, 6)

        // Add Transaction Currency Code tag
        buffer.addTagAndLength(EmvTags.TRANSACTION_CURRENCY_CODE, 2)

        // Add Terminal Country Code tag
        buffer.addTagAndLength(EmvTags.TERMINAL_COUNTRY_CODE, 2)

        return buffer.toByteArray()
    }

    /**
     * Extract transaction details from PDOL response data
     */
    private fun extractTransactionDetails(pdolData: ByteArray) {
        // Parse the PDOL response data to extract transaction details
        // Simplified implementation - would normally parse TLV data

        if (pdolData.size >= 14) { // Minimum size based on our PDOL
            var offset = 0

            // Extract TTQ (first 4 bytes)
            val ttq = ByteArray(4)
            System.arraycopy(pdolData, offset, ttq, 0, 4)
            offset += 4

            // Extract Amount (next 6 bytes)
            val amountBytes = ByteArray(6)
            System.arraycopy(pdolData, offset, amountBytes, 0, 6)
            val amount = extractNumericFromBcd(amountBytes)
            offset += 6

            // Extract Currency Code (next 2 bytes)
            val currencyCodeBytes = ByteArray(2)
            System.arraycopy(pdolData, offset, currencyCodeBytes, 0, 2)
            val currencyCode = extractNumericFromBcd(currencyCodeBytes)
            offset += 2

            // Extract Country Code (next 2 bytes)
            val countryCodeBytes = ByteArray(2)
            System.arraycopy(pdolData, offset, countryCodeBytes, 0, 2)
            val countryCode = extractNumericFromBcd(countryCodeBytes)

            // Update transaction data
            currentTransaction?.apply {
                this.amount = amount
                this.currencyCode = currencyCode
                this.countryCode = countryCode
            }

            Log.d(
                TAG,
                "Transaction details - Amount: $amount, Currency: $currencyCode, Country: $countryCode"
            )
        }
    }

    /**
     * Extract numeric value from BCD (Binary Coded Decimal) format
     */
    private fun extractNumericFromBcd(bcdBytes: ByteArray): String {
        val sb = StringBuilder()
        for (b in bcdBytes) {
            sb.append((b.toInt() shr 4) and 0x0F)
            sb.append(b.toInt() and 0x0F)
        }
        return sb.toString().trimStart('0')
    }

    /**
     * Generate processing options response (AIP and AFL)
     */
    private fun generateProcessingOptions(): ByteArray {
        val buffer = ByteArrayBuilder()

        // Format 1: Response Message Template 1
        buffer.addTag(EmvTags.RESPONSE_MESSAGE_TEMPLATE_1, true)

        // Add Application Interchange Profile (AIP)
        // Indicates supported features like:
        // - SDA, DDA, CDA support
        // - Terminal risk management
        // - Cardholder verification methods
        // - Issuer authentication
        val aip = byteArrayOf(0x5F.toByte(), 0x00.toByte()) // Simplified AIP
        buffer.addTag(EmvTags.AIP, aip)

        // Add Application File Locator (AFL)
        // Indicates records and files to be read
        val afl = byteArrayOf(
            0x08.toByte(), 0x01.toByte(), 0x01.toByte(), 0x00.toByte(),
            0x10.toByte(), 0x01.toByte(), 0x02.toByte(), 0x01.toByte()
        )
        buffer.addTag(EmvTags.AFL, afl)

        // Close Response Message Template
        buffer.closeTag()

        return buffer.toByteArray()
    }

    /**
     * Generate record data for READ RECORD command
     */
    private fun generateRecordData(sfi: Int, recordNumber: Int): ByteArray {
        Log.d(TAG, "Generating record data for SFI=$sfi, Record=$recordNumber")

        // Get card data
        val card = runBlocking {
            cardRepository.getCardById(currentCardId)
        } ?: return ByteArray(0)

        val buffer = ByteArrayBuilder()

        // Start with Record Template
        buffer.addTag(EmvTags.RECORD_TEMPLATE, true)

        when {
            // SFI 1 - Card Data
            sfi == 1 && recordNumber == 1 -> {
                // PAN (Primary Account Number)
                val panBytes = HexUtil.toByteArray(card.cardNumber.replace(" ", ""))
                buffer.addTag(EmvTags.PAN, panBytes)

                // Card Expiration Date (YYMM format)
                val expiryParts = card.expiryDate.split("/")
                if (expiryParts.size == 2) {
                    val mm = expiryParts[0]
                    val yy = expiryParts[1]
                    val expiryBytes = HexUtil.toByteArray("$yy$mm")
                    buffer.addTag(EmvTags.EXPIRY_DATE, expiryBytes)
                }

                // Cardholder Name
                val nameBytes = card.cardholderName.toByteArray()
                buffer.addTag(EmvTags.CARDHOLDER_NAME, nameBytes)
            }

            // SFI 2 - Additional Data
            sfi == 2 && recordNumber == 1 -> {
                // Track 2 Equivalent Data (simplified)
                val track2Data = generateTrack2Data(card)
                buffer.addTag(EmvTags.TRACK2_EQUIVALENT_DATA, track2Data)

                // Application Transaction Counter (ATC)
                val atc = byteArrayOf(0x00.toByte(), 0x01.toByte())
                buffer.addTag(EmvTags.ATC, atc)
            }

            // SFI 2 - Cryptogram Information
            sfi == 2 && recordNumber == 2 -> {
                // Card Risk Management Data
                val crmData = byteArrayOf(0x1F.toByte(), 0x03.toByte())
                buffer.addTag(EmvTags.CARD_RISK_MANAGEMENT_DATA_1, crmData)

                // Application Cryptogram Information Data
                val aciData = byteArrayOf(0x60.toByte(), 0x04.toByte())
                buffer.addTag(EmvTags.APPLICATION_CRYPTOGRAM_INFO_DATA, aciData)
            }

            else -> {
                Log.e(TAG, "Unknown SFI/Record combination")
                return ByteArray(0)
            }
        }

        // Close Record Template
        buffer.closeTag()

        return buffer.toByteArray()
    }

    /**
     * Generate Track 2 Equivalent Data
     */
    private fun generateTrack2Data(card: Card): ByteArray {
        // Format: PAN + Separator + Expiry Date (YYMM) + Service Code + Discretionary Data
        val panDigits = card.cardNumber.replace(" ", "")
        val expiryParts = card.expiryDate.split("/")

        if (expiryParts.size != 2) {
            return ByteArray(0)
        }

        val mm = expiryParts[0]
        val yy = expiryParts[1]

        val serviceCode = "101" // Standard service code

        val track2String = "$panDigits=${yy}${mm}${serviceCode}0000000000"

        // Convert to packed BCD format
        return HexUtil.toByteArray(track2String.replace("=", "D"))
    }

    /**
     * Extract data from CDOL (Card Risk Management Data Object List)
     */
    private fun extractCdolData(cdolData: ByteArray) {
        // Parse CDOL data to extract additional transaction details
        // Simplified implementation - would normally parse TLV data

        if (cdolData.size >= 8) {
            // Transaction type (1 byte)
            val transactionType = cdolData[0].toInt() and 0xFF

            // Unpredictable Number (4 bytes)
            val unpredictableNumber = ByteArray(4)
            System.arraycopy(cdolData, 4, unpredictableNumber, 0, 4)

            // Update transaction data
            currentTransaction?.apply {
                this.transactionType = transactionType.toString()
                this.unpredictableNumber = HexUtil.toHexString(unpredictableNumber)
            }

            Log.d(
                TAG,
                "CDOL data - Transaction Type: $transactionType, UN: ${
                    HexUtil.toHexString(unpredictableNumber)
                }"
            )
        }
    }

    /**
     * Determine the cryptogram type from P1 parameter
     */
    private fun getCryptogramType(p1: Int): String {
        // Bits 7-6 of P1 indicate the cryptogram type
        return when ((p1 shr 6) and 0x03) {
            0 -> "AAC" // Application Authentication Cryptogram (declined)
            1 -> "TC"  // Transaction Certificate (approved)
            2 -> "ARQC" // Authorization Request Cryptogram (online authorization)
            else -> "RFU" // Reserved for future use
        }
    }

    /**
     * Generate EMV cryptogram based on type and verification method
     */
    private fun generateCryptogram(cryptogramType: String, verificationMethod: Byte): ByteArray {
        Log.d(TAG, "Generating $cryptogramType with verification method: $verificationMethod")

        val buffer = ByteArrayBuilder()

        // Start with response template
        buffer.addTag(EmvTags.RESPONSE_MESSAGE_TEMPLATE_2, true)

        // Card data
        val card = runBlocking {
            cardRepository.getCardById(currentCardId)
        } ?: return STATUS_FAILED

        // Transaction data
        val transaction = currentTransaction ?: return STATUS_FAILED

        // Add Transaction Status Information (TSI)
        val tsi = byteArrayOf(0x60.toByte(), 0x00.toByte())
        buffer.addTag(EmvTags.TSI, tsi)

        // Add Transaction Certificate (TC) Hash
        val tcHash = cryptogramGenerator.generateHash(card, transaction)
        buffer.addTag(EmvTags.TRANSACTION_CERTIFICATE_HASH_VALUE, tcHash)

        // Add Application Transaction Counter (ATC)
        val atc = byteArrayOf(0x00.toByte(), 0x02.toByte())
        buffer.addTag(EmvTags.ATC, atc)

        // Add Cryptogram Information Data (CID)
        // First byte: Cryptogram type and verification results
        // Bits 7-6: Cryptogram type (00=AAC, 01=TC, 10=ARQC)
        // Bits 5-4: CVM results (00=No CVM, 01=PIN, 10=Signature, 11=Biometric)
        var cid = 0x00.toByte()

        when (cryptogramType) {
            "AAC" -> cid = cid or 0x00.toByte()
            "TC" -> cid = cid or 0x40.toByte()
            "ARQC" -> cid = cid or 0x80.toByte()
        }

        // Add verification method
        cid = cid or (verificationMethod and 0x0F)

        buffer.addTag(EmvTags.CRYPTOGRAM_INFORMATION_DATA, byteArrayOf(cid))

        // Add Application Cryptogram (AC)
        val cryptogram = cryptogramGenerator.generateCryptogram(
            card,
            transaction,
            atc,
            cryptogramType,
            false // Not last cryptogram in sequence
        )
        buffer.addTag(EmvTags.APPLICATION_CRYPTOGRAM, cryptogram)

        // Store cryptogram in transaction
        transaction.cryptogram = HexUtil.toHexString(cryptogram)
        transaction.cryptogramType = cryptogramType

        // Add Issuer Application Data (IAD)
        val iad = cryptogramGenerator.generateIssuerApplicationData(card, cryptogramType)
        buffer.addTag(EmvTags.ISSUER_APPLICATION_DATA, iad)

        // Close response template
        buffer.closeTag()

        return buffer.toByteArray()
    }

    /**
     * Check if command is a SELECT command
     */
    private fun isSelectCommand(command: ApduCommand): Boolean {
        return command.ins == SELECT_COMMAND[1] &&
                command.cla == SELECT_COMMAND[0] &&
                command.p1 == SELECT_COMMAND[2] &&
                command.p2 == SELECT_COMMAND[3]
    }

    /**
     * Check if command is a GET PROCESSING OPTIONS command
     */
    private fun isGpoCommand(command: ApduCommand): Boolean {
        return command.ins == GPO_COMMAND[1] &&
                command.cla == GPO_COMMAND[0] &&
                command.p1 == GPO_COMMAND[2] &&
                command.p2 == GPO_COMMAND[3]
    }

    /**
     * Check if command is a READ RECORD command
     */
    private fun isReadRecordCommand(command: ApduCommand): Boolean {
        return command.ins == READ_RECORD_COMMAND[1] &&
                command.cla == READ_RECORD_COMMAND[0]
    }

    /**
     * Check if command is a GENERATE AC command
     */
    private fun isGenerateACCommand(command: ApduCommand): Boolean {
        return command.ins == GENERATE_AC_COMMAND[1] &&
                command.cla == GENERATE_AC_COMMAND[0] &&
                command.p2 == GENERATE_AC_COMMAND[3]
    }

    override fun onDeactivated(reason: Int) {
        val reasonStr = when (reason) {
            DEACTIVATION_LINK_LOSS -> "Link Lost"
            DEACTIVATION_DESELECTED -> "Deselected"
            else -> "Unknown reason"
        }
        Log.d(TAG, "Deactivated: $reasonStr")

        // Reset transaction data
        currentTransaction = null
        selectedAid = null

        // Broadcast deactivation intent if needed
        sendBroadcast(Intent(ACTION_APDU_SERVICE_DEACTIVATED))
    }

    companion object {
        // Broadcast actions
        const val ACTION_APDU_SERVICE_DEACTIVATED =
            "com.example.nfcapp.action.APDU_SERVICE_DEACTIVATED"
        const val ACTION_TRANSACTION_COMPLETED = "com.example.nfcapp.action.TRANSACTION_COMPLETED"

        // Transaction extras
        const val EXTRA_TRANSACTION_ID = "transaction_id"
        const val EXTRA_TRANSACTION_APPROVED = "transaction_approved"

        // Helper function for coroutines
        private fun <T> runBlocking(block: suspend () -> T): T {
            var result: T? = null
            var exception: Throwable? = null

            val job = CoroutineScope(Dispatchers.IO).launch {
                try {
                    result = block()
                } catch (e: Throwable) {
                    exception = e
                }
            }

            while (!job.isCompleted) {
                Thread.sleep(10)
            }

            exception?.let { throw it }

            @Suppress("UNCHECKED_CAST")
            return result as T
        }
    }
}

/**
 * Helper class for building TLV (Tag-Length-Value) data
 */
class ByteArrayBuilder {
    private val buffer = mutableListOf<Byte>()
    private val tagStack = mutableListOf<Int>()
    private val lengthLocationStack = mutableListOf<Int>()

    fun addTag(tag: ByteArray, isConstructed: Boolean = false) {
        // Add tag bytes
        for (b in tag) {
            buffer.add(b)
        }

        if (isConstructed) {
            // For constructed tags, save its position and reserve space for length
            tagStack.add(buffer.size - tag.size)
            lengthLocationStack.add(buffer.size)
            buffer.add(0) // Placeholder for length
        }
    }

    fun addTag(tag: ByteArray, value: ByteArray) {
        // Add tag bytes
        for (b in tag) {
            buffer.add(b)
        }

        // Add length byte
        buffer.add(value.size.toByte())

        // Add value bytes
        for (b in value) {
            buffer.add(b)
        }
    }

    fun addTagAndLength(tag: ByteArray, length: Int) {
        // Add tag bytes
        for (b in tag) {
            buffer.add(b)
        }

        // Add length byte
        buffer.add(length.toByte())
    }

    fun closeTag() {
        if (tagStack.isEmpty() || lengthLocationStack.isEmpty()) {
            return
        }

        // Get the length location for this tag
        val lengthLocation = lengthLocationStack.removeAt(lengthLocationStack.size - 1)
        val tagLocation = tagStack.removeAt(tagStack.size - 1)

        // Calculate length of this tag's value
        val length = buffer.size - lengthLocation - 1

        // Update the length byte
        buffer[lengthLocation] = length.toByte()
    }

    fun toByteArray(): ByteArray {
        val result = ByteArray(buffer.size)
        for (i in buffer.indices) {
            result[i] = buffer[i]
        }
        return result
    }
}