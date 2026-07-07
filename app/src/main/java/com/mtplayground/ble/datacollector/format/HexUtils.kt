package com.mtplayground.ble.datacollector.format

object HexUtils {
    private val UppercaseHexDigits = charArrayOf(
        '0',
        '1',
        '2',
        '3',
        '4',
        '5',
        '6',
        '7',
        '8',
        '9',
        'A',
        'B',
        'C',
        'D',
        'E',
        'F',
    )

    fun bytesToHex(bytes: ByteArray): String = buildString(bytes.size * 3) {
        bytes.forEachIndexed { index, byte ->
            if (index > 0) {
                append(' ')
            }

            val unsignedValue = byte.toInt() and 0xFF
            append(UppercaseHexDigits[unsignedValue ushr 4])
            append(UppercaseHexDigits[unsignedValue and 0x0F])
        }
    }
}
