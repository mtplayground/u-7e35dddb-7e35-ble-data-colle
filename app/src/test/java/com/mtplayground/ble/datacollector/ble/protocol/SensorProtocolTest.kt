package com.mtplayground.ble.datacollector.ble.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SensorProtocolTest {
    @Test
    fun initializationCommandsUseExactSensorBytes() {
        assertCommandBytes(
            expected = bytes(0xBE, 0xBB, 0x02, 0x00, 0x01),
            actual = SensorProtocol.buildSixAxisInitializationCommand(),
        )
        assertCommandBytes(
            expected = bytes(0xBE, 0xBB, 0x02, 0x00, 0x02),
            actual = SensorProtocol.buildMagnetometerInitializationCommand(),
        )
        assertCommandBytes(
            expected = bytes(0xBE, 0xBB, 0x02, 0x00, 0x03),
            actual = SensorProtocol.buildBarometerInitializationCommand(),
        )
    }

    @Test
    fun dataSourceCommandsUseExactLeftAndRightBytes() {
        assertCommandBytes(
            expected = bytes(0xBE, 0xBB, 0x02, 0x01, 0x01),
            actual = SensorProtocol.buildSetDataSourceCommand(SensorSide.Left),
        )
        assertCommandBytes(
            expected = bytes(0xBE, 0xBB, 0x02, 0x01, 0x02),
            actual = SensorProtocol.buildSetDataSourceCommand(SensorSide.Right),
        )
    }

    @Test
    fun collectionAndStatusCommandsUseExactBytes() {
        assertCommandBytes(
            expected = bytes(0xBE, 0xBB, 0x02, 0x03, 0x01),
            actual = SensorProtocol.buildStartCollectionCommand(),
        )
        assertCommandBytes(
            expected = bytes(0xBE, 0xBB, 0x02, 0x03, 0x02),
            actual = SensorProtocol.buildStopCollectionCommand(),
        )
        assertCommandBytes(
            expected = bytes(0xBE, 0xBB, 0x02, 0x02, 0x01),
            actual = SensorProtocol.buildQuerySensorInitStatusCommand(SensorSide.Left),
        )
        assertCommandBytes(
            expected = bytes(0xBE, 0xBB, 0x02, 0x02, 0x02),
            actual = SensorProtocol.buildQuerySensorInitStatusCommand(SensorSide.Right),
        )
    }

    @Test
    fun sensorSideMapsCommandValuesAndResponseFlags() {
        assertEquals(0x01.toByte(), SensorSide.Left.commandValue)
        assertEquals(0x41.toByte(), SensorSide.Left.responseFlag)
        assertEquals(0x02.toByte(), SensorSide.Right.commandValue)
        assertEquals(0x42.toByte(), SensorSide.Right.responseFlag)
    }

    @Test
    fun initializationSuccessMatchersAcceptOnlyExactResponseBytes() {
        assertTrue(SensorProtocol.matchesInitializationSuccess(SensorInitState.SixAxis, bytes(0xBB, 0xBE, 0x02, 0x00, 0x11)))
        assertTrue(SensorProtocol.matchesInitializationSuccess(SensorInitState.Magnetometer, bytes(0xBB, 0xBE, 0x02, 0x00, 0x21)))
        assertTrue(SensorProtocol.matchesInitializationSuccess(SensorInitState.Barometer, bytes(0xBB, 0xBE, 0x02, 0x00, 0x31)))

        assertFalse(SensorProtocol.matchesInitializationSuccess(SensorInitState.SixAxis, bytes(0xBB, 0xBE, 0x02, 0x00, 0x21)))
        assertFalse(SensorProtocol.matchesInitializationSuccess(SensorInitState.SixAxis, bytes(0xBB, 0xBE, 0x02, 0x00, 0x11, 0x00)))
        assertFalse(SensorProtocol.matchesInitializationSuccess(SensorInitState.SixAxis, bytes(0xBE, 0xBB, 0x02, 0x00, 0x11)))
    }

    @Test
    fun dataSourceSetMatchersAcceptOnlyExactResponseBytes() {
        assertTrue(SensorProtocol.matchesDataSourceSet(SensorSide.Left, bytes(0xBB, 0xBE, 0x06, 0x05, 0x41)))
        assertTrue(SensorProtocol.matchesDataSourceSet(SensorSide.Right, bytes(0xBB, 0xBE, 0x06, 0x05, 0x42)))

        assertFalse(SensorProtocol.matchesDataSourceSet(SensorSide.Left, bytes(0xBB, 0xBE, 0x06, 0x05, 0x42)))
        assertFalse(SensorProtocol.matchesDataSourceSet(SensorSide.Right, bytes(0xBB, 0xBE, 0x06, 0x05, 0x41)))
        assertFalse(SensorProtocol.matchesDataSourceSet(SensorSide.Left, bytes(0xBB, 0xBE, 0x02, 0x01, 0x41)))
    }

    @Test
    fun sensorInitializationFailureMatcherAcceptsExactUninitializedSensorError() {
        assertTrue(SensorProtocol.matchesSensorInitializationFailure(bytes(0xBB, 0xBE, 0x02, 0x00, 0x00)))
        assertFalse(SensorProtocol.matchesSensorInitializationFailure(bytes(0xBB, 0xBE, 0x02, 0x00, 0x01)))
        assertFalse(SensorProtocol.matchesSensorInitializationFailure(bytes(0xBB, 0xBE, 0x02, 0x00, 0x00, 0x00)))
    }

    @Test
    fun commandResponseMatcherIdentifiesKnownResponsesAndIgnoresDataFrames() {
        val sixAxis = SensorProtocol.matchCommandResponse(bytes(0xBB, 0xBE, 0x02, 0x00, 0x11))
        assertTrue(sixAxis is BleCommandResponse.SensorInitializationSucceeded)
        val sixAxisResponse = sixAxis as BleCommandResponse.SensorInitializationSucceeded
        assertEquals(SensorInitState.SixAxis, sixAxisResponse.sensor)
        assertArrayEquals(bytes(0xBB, 0xBE, 0x02, 0x00, 0x11), sixAxisResponse.bytes)

        val leftSource = SensorProtocol.matchCommandResponse(bytes(0xBB, 0xBE, 0x06, 0x05, 0x41))
        assertTrue(leftSource is BleCommandResponse.DataSourceSet)
        val leftSourceResponse = leftSource as BleCommandResponse.DataSourceSet
        assertEquals(SensorSide.Left, leftSourceResponse.side)
        assertArrayEquals(bytes(0xBB, 0xBE, 0x06, 0x05, 0x41), leftSourceResponse.bytes)

        assertEquals(
            BleCommandResponse.SensorInitializationFailed,
            SensorProtocol.matchCommandResponse(bytes(0xBB, 0xBE, 0x02, 0x00, 0x00)),
        )
        assertNull(SensorProtocol.matchCommandResponse(bytes(0xBE, 0xBB, 0x42, 0xAD, 0xBA, 0xFF)))
    }

    @Test
    fun bleCommandDefensivelyCopiesCommandBytes() {
        val command = SensorProtocol.buildStartCollectionCommand()
        val firstCopy = command.copyBytes()
        firstCopy[0] = 0x00

        assertArrayEquals(bytes(0xBE, 0xBB, 0x02, 0x03, 0x01), command.bytes)
        assertArrayEquals(bytes(0xBE, 0xBB, 0x02, 0x03, 0x01), command.copyBytes())
    }

    private fun assertCommandBytes(expected: ByteArray, actual: BleCommand) {
        assertArrayEquals(expected, actual.bytes)
        assertArrayEquals(expected, actual.copyBytes())
    }

    private fun bytes(vararg values: Int): ByteArray = values.map { value -> value.toByte() }.toByteArray()
}
