package com.mtplayground.ble.datacollector.ble.model

data class DiscoveredDevice(
    val name: String,
    val macAddress: String,
    val rssi: Int,
    val lastSeenMillis: Long,
)
