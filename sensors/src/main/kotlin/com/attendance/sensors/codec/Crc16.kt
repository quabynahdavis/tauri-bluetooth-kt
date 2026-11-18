package com.attendance.sensors.codec

/**
 * CRC-16/CCITT-FALSE: poly 0x1021, init 0xFFFF, refin/refout false, xorout 0x0000.
 */
object Crc16 {
    fun ccittFalse(data: ByteArray): Int {
        var crc = 0xFFFF
        for (value in data) {
            crc = crc xor ((value.toInt() and 0xFF) shl 8)
            repeat(8) {
                crc = if ((crc and 0x8000) != 0) {
                    (crc shl 1) xor 0x1021
                } else {
                    crc shl 1
                }
                crc = crc and 0xFFFF
            }
        }
        return crc
    }
}
