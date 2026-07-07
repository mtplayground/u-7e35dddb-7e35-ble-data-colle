package com.mtplayground.ble.datacollector.ble

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NameFilterTest {
    @Test
    fun matchesNameFilterAcceptsConfiguredPrefixes() {
        assertTrue(BleManager.matchesNameFilter("CM-1001"))
        assertTrue(BleManager.matchesNameFilter("CM-"))
        assertTrue(BleManager.matchesNameFilter("x_skiing"))
        assertTrue(BleManager.matchesNameFilter("x_skiing-alpha"))
    }

    @Test
    fun matchesNameFilterRejectsNamesWithoutConfiguredPrefixes() {
        assertFalse(BleManager.matchesNameFilter("ACM-1001"))
        assertFalse(BleManager.matchesNameFilter("cm-1001"))
        assertFalse(BleManager.matchesNameFilter("skiing-x_skiing"))
        assertFalse(BleManager.matchesNameFilter(""))
    }
}
