package com.mtplayground.ble.datacollector.ble.protocol

sealed interface BleCommandResponse {
    val bytes: ByteArray

    data class SensorInitializationSucceeded(
        val sensor: SensorInitState,
        override val bytes: ByteArray,
    ) : BleCommandResponse

    data class DataSourceSet(
        val side: SensorSide,
        override val bytes: ByteArray,
    ) : BleCommandResponse

    data object SensorInitializationFailed : BleCommandResponse {
        override val bytes: ByteArray = byteArrayOf(
            0xBB.toByte(),
            0xBE.toByte(),
            0x02,
            0x00,
            0x00,
        )
    }
}
