package com.example.nfcapp.emv

import com.example.nfcapp.util.HexUtil

/**
 * Represents an APDU (Application Protocol Data Unit) command.
 *
 * APDU Command Structure:
 * - CLA (1 byte): Class byte
 * - INS (1 byte): Instruction byte
 * - P1 (1 byte): Parameter 1
 * - P2 (1 byte): Parameter 2
 * - Lc (0-3 bytes): Length of command data (optional)
 * - Command data (variable length)
 * - Le (0-3 bytes): Expected length of response (optional)
 */
class ApduCommand(private val commandApdu: ByteArray) {

    // Command header fields
    val cla: Byte
    val ins: Byte
    val p1: Byte
    val p2: Byte

    // Command data
    val data: ByteArray?

    // Expected response length
    val le: Int

    init {
        // Minimum APDU command is 4 bytes (header only)
        if (commandApdu.size < 4) {
            throw IllegalArgumentException("APDU command too short: ${commandApdu.size} bytes")
        }

        // Parse header
        cla = commandApdu[0]
        ins = commandApdu[1]
        p1 = commandApdu[2]
        p2 = commandApdu[3]

        // Parse body based on command length
        when {
            // Case 1: No data field and no Le field
            commandApdu.size == 4 -> {
                data = null
                le = 0
            }

            // Case 2S: No data field, short Le field (1 byte)
            commandApdu.size == 5 -> {
                data = null
                le = commandApdu[4].toInt() and 0xFF
            }

            // Case 3S: Data field present, no Le field
            commandApdu.size >= 6 -> {
                val lc = commandApdu[4].toInt() and 0xFF

                // Check if we have enough bytes for the data field
                if (commandApdu.size < 5 + lc) {
                    throw IllegalArgumentException("APDU command too short for specified Lc")
                }

                // Extract data field
                if (lc > 0) {
                    data = ByteArray(lc)
                    System.arraycopy(commandApdu, 5, data, 0, lc)
                } else {
                    data = ByteArray(0)
                }

                // Check if Le field is present
                le = if (commandApdu.size == 5 + lc + 1) {
                    commandApdu[5 + lc].toInt() and 0xFF
                } else {
                    0
                }
            }

            else -> {
                data = null
                le = 0
            }
        }
    }

    /**
     * Get the full APDU command
     */
    fun getBytes(): ByteArray {
        return commandApdu.clone()
    }

    override fun toString(): String {
        val sb = StringBuilder()
        sb.append("APDU Command: ")
        sb.append("CLA=${HexUtil.byteToHex(cla)} ")
        sb.append("INS=${HexUtil.byteToHex(ins)} ")
        sb.append("P1=${HexUtil.byteToHex(p1)} ")
        sb.append("P2=${HexUtil.byteToHex(p2)}")

        data?.let {
            sb.append(" Data=${HexUtil.toHexString(it)}")
        }

        if (le > 0) {
            sb.append(" Le=$le")
        }

        return sb.toString()
    }
}