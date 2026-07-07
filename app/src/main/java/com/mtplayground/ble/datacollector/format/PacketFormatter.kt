package com.mtplayground.ble.datacollector.format

import com.mtplayground.ble.datacollector.ble.model.BlePacket
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object PacketFormatter {
    private val TimestampFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("HH:mm:ss.SSS", Locale.US)

    fun format(packet: BlePacket, zoneId: ZoneId = ZoneId.systemDefault()): String =
        formatRecord(
            deviceName = packet.deviceName,
            macAddress = packet.macAddress,
            rawBytes = packet.rawBytes,
            receivedAtMillis = packet.receivedAtMillis,
            zoneId = zoneId,
        )

    fun formatRecord(
        deviceName: String,
        macAddress: String,
        rawBytes: ByteArray,
        receivedAtMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): String {
        val timestamp = formatTimestamp(receivedAtMillis = receivedAtMillis, zoneId = zoneId)
        val hexBytes = HexUtils.bytesToHex(rawBytes)

        return "[$timestamp] 数据 -- $deviceName=$macAddress\nHEX: $hexBytes"
    }

    fun formatTimestamp(receivedAtMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): String =
        Instant.ofEpochMilli(receivedAtMillis)
            .atZone(zoneId)
            .format(TimestampFormatter)
}
