package com.mtplayground.ble.datacollector.ble.protocol

object SensorProtocol {
    fun buildInitializeCommand(sensor: SensorInitState): BleCommand = BleCommand(
        bytes = commandBytes(
            CommandGroup,
            InitializeCommand,
            sensor.commandValue,
        ),
    )

    fun buildSixAxisInitializationCommand(): BleCommand =
        buildInitializeCommand(SensorInitState.SixAxis)

    fun buildMagnetometerInitializationCommand(): BleCommand =
        buildInitializeCommand(SensorInitState.Magnetometer)

    fun buildBarometerInitializationCommand(): BleCommand =
        buildInitializeCommand(SensorInitState.Barometer)

    fun buildSetDataSourceCommand(side: SensorSide): BleCommand = BleCommand(
        bytes = commandBytes(
            CommandGroup,
            SetDataSourceCommand,
            side.commandValue,
        ),
    )

    fun buildStartCollectionCommand(): BleCommand = BleCommand(
        bytes = commandBytes(
            CommandGroup,
            CollectionCommand,
            StartCollectionValue,
        ),
    )

    fun buildStopCollectionCommand(): BleCommand = BleCommand(
        bytes = commandBytes(
            CommandGroup,
            CollectionCommand,
            StopCollectionValue,
        ),
    )

    fun buildQuerySensorInitStatusCommand(side: SensorSide): BleCommand = BleCommand(
        bytes = commandBytes(
            CommandGroup,
            QuerySensorInitStatusCommand,
            side.commandValue,
        ),
    )

    fun matchesInitializationSuccess(sensor: SensorInitState, packet: ByteArray): Boolean =
        packet.contentEquals(initializationSuccessBytes(sensor))

    fun matchesSixAxisInitializationSuccess(packet: ByteArray): Boolean =
        matchesInitializationSuccess(SensorInitState.SixAxis, packet)

    fun matchesMagnetometerInitializationSuccess(packet: ByteArray): Boolean =
        matchesInitializationSuccess(SensorInitState.Magnetometer, packet)

    fun matchesBarometerInitializationSuccess(packet: ByteArray): Boolean =
        matchesInitializationSuccess(SensorInitState.Barometer, packet)

    fun matchesDataSourceSet(side: SensorSide, packet: ByteArray): Boolean =
        packet.contentEquals(dataSourceSetBytes(side))

    fun matchesSensorInitializationFailure(packet: ByteArray): Boolean =
        packet.contentEquals(SensorInitializationFailureBytes)

    fun matchCommandResponse(packet: ByteArray): BleCommandResponse? {
        SensorInitState.entries.firstOrNull { sensor ->
            matchesInitializationSuccess(sensor, packet)
        }?.let { sensor ->
            return BleCommandResponse.SensorInitializationSucceeded(
                sensor = sensor,
                bytes = packet.copyOf(),
            )
        }

        SensorSide.entries.firstOrNull { side ->
            matchesDataSourceSet(side, packet)
        }?.let { side ->
            return BleCommandResponse.DataSourceSet(
                side = side,
                bytes = packet.copyOf(),
            )
        }

        return if (matchesSensorInitializationFailure(packet)) {
            BleCommandResponse.SensorInitializationFailed
        } else {
            null
        }
    }

    private fun initializationSuccessBytes(sensor: SensorInitState): ByteArray = responseBytes(
        CommandGroup,
        InitializeCommand,
        sensor.successFlag,
    )

    private fun dataSourceSetBytes(side: SensorSide): ByteArray = responseBytes(
        DataSourceSetResponseGroup,
        DataSourceSetResponseCommand,
        side.responseFlag,
    )

    private fun commandBytes(group: Byte, command: Byte, value: Byte): ByteArray = byteArrayOf(
        CommandHeaderFirst,
        CommandHeaderSecond,
        group,
        command,
        value,
    )

    private fun responseBytes(group: Byte, command: Byte, value: Byte): ByteArray = byteArrayOf(
        ResponseHeaderFirst,
        ResponseHeaderSecond,
        group,
        command,
        value,
    )

    private val CommandHeaderFirst = 0xBE.toByte()
    private val CommandHeaderSecond = 0xBB.toByte()
    private val ResponseHeaderFirst = 0xBB.toByte()
    private val ResponseHeaderSecond = 0xBE.toByte()
    private const val CommandGroup: Byte = 0x02
    private const val InitializeCommand: Byte = 0x00
    private const val SetDataSourceCommand: Byte = 0x01
    private const val QuerySensorInitStatusCommand: Byte = 0x02
    private const val CollectionCommand: Byte = 0x03
    private const val StartCollectionValue: Byte = 0x01
    private const val StopCollectionValue: Byte = 0x02
    private const val DataSourceSetResponseGroup: Byte = 0x06
    private const val DataSourceSetResponseCommand: Byte = 0x05
    private val SensorInitializationFailureBytes = byteArrayOf(
        ResponseHeaderFirst,
        ResponseHeaderSecond,
        CommandGroup,
        InitializeCommand,
        0x00,
    )
}
