package com.mtplayground.ble.datacollector.core

import com.mtplayground.ble.datacollector.BuildConfig

object Config {
    val bleNameFilterPrefixes: List<String> = listOf(
        BuildConfig.BLE_NAME_FILTER_PREFIX_CM,
        BuildConfig.BLE_NAME_FILTER_PREFIX_SKIING,
    )

    const val requestedMtu: Int = BuildConfig.REQUESTED_MTU

    const val sessionFilePrefix: String = BuildConfig.SESSION_FILE_PREFIX
    const val sessionFileExtension: String = BuildConfig.SESSION_FILE_EXTENSION
    const val sessionTimestampPattern: String = BuildConfig.SESSION_TIMESTAMP_PATTERN
    const val sessionMimeType: String = BuildConfig.SESSION_MIME_TYPE
}
