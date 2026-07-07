package com.mtplayground.ble.datacollector.storage

import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class FileNamingTest {
    @Test
    fun buildSessionFileNameUsesDeviceNameTimestampAndTxtExtension() {
        val startedAtMillis = Instant.parse("2026-07-07T14:25:30Z").toEpochMilli()

        val fileName = FileLogger.buildSessionFileName(
            deviceName = "x_skiing",
            startedAtMillis = startedAtMillis,
            zoneId = ZoneOffset.UTC,
        )

        assertEquals("x_skiing_20260707_142530.txt", fileName)
    }

    @Test
    fun buildSessionFileNameSanitizesUnsafeDeviceNameCharacters() {
        val startedAtMillis = Instant.parse("2026-07-07T14:25:30Z").toEpochMilli()

        val fileName = FileLogger.buildSessionFileName(
            deviceName = " CM/alpha:01 ",
            startedAtMillis = startedAtMillis,
            zoneId = ZoneOffset.UTC,
        )

        assertEquals("CM_alpha_01_20260707_142530.txt", fileName)
    }
}
