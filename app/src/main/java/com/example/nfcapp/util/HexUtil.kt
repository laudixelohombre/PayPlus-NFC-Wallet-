package com.example.nfcapp.util

/**
 * Utility class for converting between byte arrays and hexadecimal strings.
 * Used extensively for EMV data processing, APDU commands, and cryptogram generation.
 */
object HexUtil {
    private val HEX_CHARS = "0123456789ABCDEF".toCharArray()

    /**
     * Convert a byte array to a hexadecimal string
     *
     * @param bytes The byte array to convert
     * @return The hexadecimal string representation
     */
    fun toHexString(bytes: ByteArray): String {
        val result = StringBuilder(bytes.size * 2)
        for (byte in bytes) {
            val i = byte.toInt() and 0xFF
            result.append(HEX_CHARS[i shr 4])
            result.append(HEX_CHARS[i and 0x0F])
        }
        return result.toString()
    }

    /**
     * Convert a single byte to a hexadecimal string
     *
     * @param byte The byte to convert
     * @return The hexadecimal string representation
     */
    fun byteToHex(byte: Byte): String {
        val i = byte.toInt() and 0xFF
        return "${HEX_CHARS[i shr 4]}${HEX_CHARS[i and 0x0F]}"
    }

    /**
     * Convert a hexadecimal string to a byte array
     *
     * @param hexString The hexadecimal string to convert
     * @return The byte array representation
     */
    fun toByteArray(hexString: String): ByteArray {
        // Remove any spaces or other formatting characters
        val cleanHexString = hexString.replace(" ", "").replace("-", "").uppercase()

        // Check for valid input
        if (cleanHexString.isEmpty() || cleanHexString.length % 2 != 0) {
            throw IllegalArgumentException("Invalid hexadecimal string")
        }

        val result = ByteArray(cleanHexString.length / 2)

        for (i in result.indices) {
            val index = i * 2
            val firstNibble = Character.digit(cleanHexString[index], 16)
            val secondNibble = Character.digit(cleanHexString[index + 1], 16)

            if (firstNibble == -1 || secondNibble == -1) {
                throw IllegalArgumentException("Invalid hexadecimal string")
            }

            result[i] = ((firstNibble shl 4) + secondNibble).toByte()
        }

        return result
    }

    /**
     * Format a hexadecimal string with spaces between bytes for better readability
     *
     * @param hexString The hexadecimal string to format
     * @return The formatted hexadecimal string
     */
    fun formatHexString(hexString: String): String {
        val cleanHexString = hexString.replace(" ", "").uppercase()
        if (cleanHexString.isEmpty() || cleanHexString.length % 2 != 0) {
            return hexString // Return original if invalid
        }

        val result = StringBuilder()
        for (i in 0 until cleanHexString.length step 2) {
            if (i > 0) {
                result.append(" ")
            }
            result.append(cleanHexString.substring(i, i + 2))
        }

        return result.toString()
    }

    /**
     * Concatenate multiple byte arrays
     *
     * @param arrays The byte arrays to concatenate
     * @return The concatenated byte array
     */
    fun concatenate(vararg arrays: ByteArray): ByteArray {
        var totalLength = 0
        for (array in arrays) {
            totalLength += array.size
        }

        val result = ByteArray(totalLength)
        var currentIndex = 0

        for (array in arrays) {
            System.arraycopy(array, 0, result, currentIndex, array.size)
            currentIndex += array.size
        }

        return result
    }

    /**
     * XOR two byte arrays together
     *
     * @param array1 First byte array
     * @param array2 Second byte array
     * @return XOR result
     */
    fun xor(array1: ByteArray, array2: ByteArray): ByteArray {
        if (array1.size != array2.size) {
            throw IllegalArgumentException("Arrays must be of the same length")
        }

        val result = ByteArray(array1.size)
        for (i in array1.indices) {
            result[i] = (array1[i].toInt() xor array2[i].toInt()).toByte()
        }

        return result
    }
}