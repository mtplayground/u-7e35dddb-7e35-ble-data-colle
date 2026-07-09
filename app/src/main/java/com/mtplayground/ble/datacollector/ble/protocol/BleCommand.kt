package com.mtplayground.ble.datacollector.ble.protocol

class BleCommand internal constructor(
    bytes: ByteArray,
) {
    val bytes: ByteArray = bytes.copyOf()

    fun copyBytes(): ByteArray = bytes.copyOf()
}
