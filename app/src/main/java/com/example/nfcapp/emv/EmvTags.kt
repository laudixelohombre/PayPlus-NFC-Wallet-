package com.example.nfcapp.emv

/**
 * Constants for EMV (Europay, Mastercard, Visa) tag identifiers.
 *
 * These tags are used in the TLV (Tag-Length-Value) data format
 * for communication between EMV cards and terminals during
 * payment transactions.
 */
object EmvTags {
    // File Control Information (FCI) Tags
    val FCI_TEMPLATE = byteArrayOf(0x6F.toByte())
    val DF_NAME = byteArrayOf(0x84.toByte())
    val FCI_PROPRIETARY_TEMPLATE = byteArrayOf(0xA5.toByte())
    val FCI_ISSUER_DISCRETIONARY_DATA = byteArrayOf(0xBF.toByte(), 0x0C.toByte())

    // Application Specific Tags
    val APPLICATION_LABEL = byteArrayOf(0x50.toByte())
    val APPLICATION_PRIORITY_INDICATOR = byteArrayOf(0x87.toByte())
    val APPLICATION_PREFERRED_NAME = byteArrayOf(0x9F.toByte(), 0x12.toByte())
    val APPLICATION_EFFECTIVE_DATE = byteArrayOf(0x5F.toByte(), 0x25.toByte())
    val APPLICATION_EXPIRATION_DATE = byteArrayOf(0x5F.toByte(), 0x24.toByte())
    val APPLICATION_USAGE_CONTROL = byteArrayOf(0x9F.toByte(), 0x07.toByte())
    val APPLICATION_VERSION_NUMBER = byteArrayOf(0x9F.toByte(), 0x08.toByte())
    val APPLICATION_CURRENCY_CODE = byteArrayOf(0x9F.toByte(), 0x42.toByte())
    val APPLICATION_CURRENCY_EXPONENT = byteArrayOf(0x9F.toByte(), 0x44.toByte())
    val APPLICATION_INTERCHANGE_PROFILE = byteArrayOf(0x82.toByte())
    val AIP = byteArrayOf(0x82.toByte()) // Same as APPLICATION_INTERCHANGE_PROFILE

    // Record Template Tags
    val RECORD_TEMPLATE = byteArrayOf(0x70.toByte())
    val RESPONSE_MESSAGE_TEMPLATE_1 = byteArrayOf(0x80.toByte())
    val RESPONSE_MESSAGE_TEMPLATE_2 = byteArrayOf(0x77.toByte())

    // Cardholder Verification Method (CVM) Tags
    val CVM_LIST = byteArrayOf(0x8E.toByte())
    val CVM_RESULTS = byteArrayOf(0x9F.toByte(), 0x34.toByte())

    // Processing Options Tags
    val PDOL = byteArrayOf(0x9F.toByte(), 0x38.toByte())
    val CDOL1 = byteArrayOf(0x8C.toByte())
    val CDOL2 = byteArrayOf(0x8D.toByte())
    val AFL = byteArrayOf(0x94.toByte())

    // Card Data Tags
    val PAN = byteArrayOf(0x5A.toByte())
    val TRACK2_EQUIVALENT_DATA = byteArrayOf(0x57.toByte())
    val PAN_SEQUENCE_NUMBER = byteArrayOf(0x5F.toByte(), 0x34.toByte())
    val CARDHOLDER_NAME = byteArrayOf(0x5F.toByte(), 0x20.toByte())
    val EXPIRY_DATE = byteArrayOf(0x5F.toByte(), 0x24.toByte())
    val SERVICE_CODE = byteArrayOf(0x5F.toByte(), 0x30.toByte())

    // Cryptogram Tags
    val APPLICATION_CRYPTOGRAM = byteArrayOf(0x9F.toByte(), 0x26.toByte())
    val CRYPTOGRAM_INFORMATION_DATA = byteArrayOf(0x9F.toByte(), 0x27.toByte())
    val TRANSACTION_CERTIFICATE_HASH_VALUE = byteArrayOf(0x98.toByte())
    val ISSUER_APPLICATION_DATA = byteArrayOf(0x9F.toByte(), 0x10.toByte())
    val APPLICATION_TRANSACTION_COUNTER = byteArrayOf(0x9F.toByte(), 0x36.toByte())
    val ATC = byteArrayOf(0x9F.toByte(), 0x36.toByte()) // Same as APPLICATION_TRANSACTION_COUNTER
    val APPLICATION_CRYPTOGRAM_INFO_DATA = byteArrayOf(0x9F.toByte(), 0x27.toByte())

    // Transaction Processing Tags
    val TERMINAL_VERIFICATION_RESULTS = byteArrayOf(0x95.toByte())
    val TVR = byteArrayOf(0x95.toByte()) // Same as TERMINAL_VERIFICATION_RESULTS
    val TRANSACTION_STATUS_INFORMATION = byteArrayOf(0x9B.toByte())
    val TSI = byteArrayOf(0x9B.toByte()) // Same as TRANSACTION_STATUS_INFORMATION
    val TRANSACTION_DATE = byteArrayOf(0x9A.toByte())
    val TRANSACTION_TYPE = byteArrayOf(0x9C.toByte())
    val AMOUNT_AUTHORIZED = byteArrayOf(0x9F.toByte(), 0x02.toByte())
    val TRANSACTION_CURRENCY_CODE = byteArrayOf(0x5F.toByte(), 0x2A.toByte())
    val TERMINAL_COUNTRY_CODE = byteArrayOf(0x9F.toByte(), 0x1A.toByte())
    val TERMINAL_CAPABILITIES = byteArrayOf(0x9F.toByte(), 0x33.toByte())
    val TERMINAL_TYPE = byteArrayOf(0x9F.toByte(), 0x35.toByte())
    val UNPREDICTABLE_NUMBER = byteArrayOf(0x9F.toByte(), 0x37.toByte())
    val TERMINAL_TRANSACTION_QUALIFIERS = byteArrayOf(0x9F.toByte(), 0x66.toByte())
    val TTQ = byteArrayOf(0x9F.toByte(), 0x66.toByte()) // Same as TERMINAL_TRANSACTION_QUALIFIERS

    // Risk Management Tags
    val CARD_RISK_MANAGEMENT_DATA_1 = byteArrayOf(0x9F.toByte(), 0x6C.toByte())
    val CARD_RISK_MANAGEMENT_DATA_2 = byteArrayOf(0x9F.toByte(), 0x6D.toByte())

    /**
     * Converts a 2-byte tag to a readable hex string
     */
    fun tagToHexString(tag: ByteArray): String {
        val hexChars = CharArray(tag.size * 2)
        for (i in tag.indices) {
            val v = tag[i].toInt() and 0xFF
            hexChars[i * 2] = "0123456789ABCDEF"[v ushr 4]
            hexChars[i * 2 + 1] = "0123456789ABCDEF"[v and 0x0F]
        }
        return String(hexChars)
    }
}