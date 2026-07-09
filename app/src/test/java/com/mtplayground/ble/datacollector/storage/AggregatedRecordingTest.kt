package com.mtplayground.ble.datacollector.storage

import com.mtplayground.ble.datacollector.ble.model.BlePacket
import com.mtplayground.ble.datacollector.core.Config
import com.mtplayground.ble.datacollector.format.PacketFormatter
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class AggregatedRecordingTest {
    @Test
    fun aggregatedRecordsKeepReceiveOrderAndSourceDeviceIdentity() {
        val firstReceivedAt = Instant.parse("2026-07-07T15:44:22.726Z").toEpochMilli()
        val secondReceivedAt = Instant.parse("2026-07-07T15:44:22.900Z").toEpochMilli()
        val packets = listOf(
            BlePacket(
                deviceName = "x_skiing",
                macAddress = "3F:89:E5:1E:2A:EF",
                rawBytes = byteArrayOf(
                    0xBE.toByte(),
                    0xBB.toByte(),
                    0x42,
                    0xAD.toByte(),
                ),
                receivedAtMillis = firstReceivedAt,
            ),
            BlePacket(
                deviceName = "CM-1001",
                macAddress = "00:11:22:33:44:55",
                rawBytes = byteArrayOf(
                    0x9B.toByte(),
                    0x07,
                    0xD8.toByte(),
                    0xFF.toByte(),
                ),
                receivedAtMillis = secondReceivedAt,
            ),
        )

        val writtenRecords = packets.map { packet ->
            PacketFormatter.format(packet, ZoneOffset.UTC)
        }
        val singleSessionFileBody = writtenRecords.joinToString(separator = "\n")

        assertEquals(
            listOf(
                "[15:44:22.726] 数据 -- x_skiing=3F:89:E5:1E:2A:EF\n" +
                    "HEX: BE BB 42 AD",
                "[15:44:22.900] 数据 -- CM-1001=00:11:22:33:44:55\n" +
                    "HEX: 9B 07 D8 FF",
            ),
            writtenRecords,
        )
        assertEquals(
            "[15:44:22.726] 数据 -- x_skiing=3F:89:E5:1E:2A:EF\n" +
                "HEX: BE BB 42 AD\n" +
                "[15:44:22.900] 数据 -- CM-1001=00:11:22:33:44:55\n" +
                "HEX: 9B 07 D8 FF",
            singleSessionFileBody,
        )
    }

    @Test
    fun aggregatedSessionFileNameUsesConfiguredSessionPrefix() {
        val startedAtMillis = Instant.parse("2026-07-07T14:25:30Z").toEpochMilli()

        val fileName = FileLogger.buildSessionFileName(
            deviceName = Config.sessionFilePrefix,
            startedAtMillis = startedAtMillis,
            zoneId = ZoneOffset.UTC,
        )

        assertEquals("ble-session_20260707_142530.txt", fileName)
    }
}
