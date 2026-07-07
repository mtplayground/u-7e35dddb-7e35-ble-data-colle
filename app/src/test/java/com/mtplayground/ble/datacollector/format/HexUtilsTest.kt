package com.mtplayground.ble.datacollector.format

import org.junit.Assert.assertEquals
import org.junit.Test

class HexUtilsTest {
    @Test
    fun bytesToHexFormatsUppercaseSpaceSeparatedBytes() {
        val bytes = byteArrayOf(
            0x00,
            0x0A,
            0x10,
            0x7F,
            0x80.toByte(),
            0xBE.toByte(),
            0xFF.toByte(),
        )

        assertEquals("00 0A 10 7F 80 BE FF", HexUtils.bytesToHex(bytes))
    }

    @Test
    fun bytesToHexReturnsEmptyStringForEmptyPacket() {
        assertEquals("", HexUtils.bytesToHex(ByteArray(0)))
    }
}
