package com.example.nfcapp.emv

import com.example.nfcapp.util.HexUtil

/**
 * Represents an APDU (Application Protocol Data Unit) response.
 *
 * APDU Response Structure:
 * - Response data (variable length)
 * - SW1 (1 byte): Status Word 1
 * - SW2 (1 byte): Status Word 2
 *
 * The Status Words (SW1-SW2) together indicate the result of the command.
 * Common Status Words:
 * - 9000: Success
 * - 6A82: File or application not found
 * - 6A83: Record not found
 * - 6700: Wrong length
 * - 6E00: Class not supported
 */
class ApduResponse(
    private val responseData: ByteArray = ByteArray(0),
    private val statusWords: ByteArray = byteArrayOf(0x90.toByte(), 0x00.toByte())
) {

    companion object {
        // Common status words
        val SW_SUCCESS = byteArrayOf(0x90.toByte(), 0x00.toByte())
        val SW_FILE_NOT_FOUND = byteArrayOf(0x6A.toByte(), 0x82.toByte())
        val SW_RECORD_NOT_FOUND = byteArrayOf(0x6A.toByte(), 0x83.toByte())
        val SW_WRONG_LENGTH = byteArrayOf(0x67.toByte(), 0x00.toByte())
        val SW_CLASS_NOT_SUPPORTED = byteArrayOf(0x6E.toByte(), 0x00.toByte())
        val SW_INS_NOT_SUPPORTED = byteArrayOf(0x6D.toByte(), 0x00.toByte())
        val SW_CONDITIONS_NOT_SATISFIED = byteArrayOf(0x69.toByte(), 0x85.toByte())
        val SW_SECURITY_NOT_SATISFIED = byteArrayOf(0x69.toByte(), 0x82.toByte())
        val SW_INCORRECT_P1P2 = byteArrayOf(0x6A.toByte(), 0x86.toByte())
    }

    /**
     * Convert the APDU response to a byte array
     */
    fun toByteArray(): ByteArray {
        val result = ByteArray(responseData.size + 2)

        // Copy response data
        responseData.copyInto(result)

        // Add status words
        result[responseData.size] = statusWords[0]
        result[responseData.size + 1] = statusWords[1]

        return result
    }

    /**
     * Get response data without status words
     */
    fun getData(): ByteArray {
        return responseData.clone()
    }

    /**
     * Get status words
     */
    fun getStatusWords(): ByteArray {
        return statusWords.clone()
    }

    /**
     * Check if the response indicates success
     */
    fun isSuccess(): Boolean {
        return statusWords[0] == SW_SUCCESS[0] && statusWords[1] == SW_SUCCESS[1]
    }

    override fun toString(): String {
        val sb = StringBuilder()
        sb.append("APDU Response: ")

        if (responseData.isNotEmpty()) {
            sb.append("Data=${HexUtil.toHexString(responseData)} ")
        }

        sb.append("SW=${HexUtil.toHexString(statusWords)}")

        return sb.toString()
    }
}