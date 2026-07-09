package com.mtplayground.ble.datacollector.ble.protocol

enum class SensorInitState(
    val commandValue: Byte,
    val successFlag: Byte,
) {
    SixAxis(
        commandValue = 0x01,
        successFlag = 0x11,
    ),
    Magnetometer(
        commandValue = 0x02,
        successFlag = 0x21,
    ),
    Barometer(
        commandValue = 0x03,
        successFlag = 0x31,
    ),
}
