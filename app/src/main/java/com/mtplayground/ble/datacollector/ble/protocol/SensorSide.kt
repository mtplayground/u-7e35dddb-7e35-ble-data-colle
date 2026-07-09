package com.mtplayground.ble.datacollector.ble.protocol

enum class SensorSide(
    val commandValue: Byte,
    val responseFlag: Byte,
) {
    Left(
        commandValue = 0x01,
        responseFlag = 0x41,
    ),
    Right(
        commandValue = 0x02,
        responseFlag = 0x42,
    ),
}
