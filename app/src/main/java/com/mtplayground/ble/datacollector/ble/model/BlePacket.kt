package com.mtplayground.ble.datacollector.ble.model

data class BlePacket(
    val deviceName: String,
    val macAddress: String,
    val rawBytes: ByteArray,
    val receivedAtMillis: Long,
)
