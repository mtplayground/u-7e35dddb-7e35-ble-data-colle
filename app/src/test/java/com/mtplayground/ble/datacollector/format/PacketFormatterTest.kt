package com.mtplayground.ble.datacollector.format

import com.mtplayground.ble.datacollector.ble.model.BlePacket
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class PacketFormatterTest {
    @Test
    fun formatTimestampUsesHourMinuteSecondAndMilliseconds() {
        val receivedAtMillis = Instant.parse("2026-07-07T15:44:22.726Z").toEpochMilli()

        assertEquals(
            "15:44:22.726",
            PacketFormatter.formatTimestamp(
                receivedAtMillis = receivedAtMillis,
                zoneId = ZoneOffset.UTC,
            ),
        )
    }

    @Test
    fun formatRecordReproducesExactTwoLineOutput() {
        val receivedAtMillis = Instant.parse("2026-07-07T15:44:22.726Z").toEpochMilli()
        val packet = BlePacket(
            deviceName = "x_skiing",
            macAddress = "3F:89:E5:1E:2A:EF",
            rawBytes = byteArrayOf(
                0xBE.toByte(),
                0xBB.toByte(),
                0x42,
                0xAD.toByte(),
                0xBA.toByte(),
                0xFF.toByte(),
                0x9B.toByte(),
                0x07,
                0xD8.toByte(),
                0xFF.toByte(),
                0xFE.toByte(),
                0xFF.toByte(),
                0x05,
                0x00,
                0xFF.toByte(),
                0xFF.toByte(),
                0x9B.toByte(),
            ),
            receivedAtMillis = receivedAtMillis,
        )
        val expected = "[15:44:22.726] 数据 -- x_skiing=3F:89:E5:1E:2A:EF\n" +
            "HEX: BE BB 42 AD BA FF 9B 07 D8 FF FE FF 05 00 FF FF 9B"

        val formatted = PacketFormatter.format(packet, ZoneOffset.UTC)

        assertEquals(expected, formatted)
        assertArrayEquals(
            expected.toByteArray(StandardCharsets.UTF_8),
            formatted.toByteArray(StandardCharsets.UTF_8),
        )
    }
}
