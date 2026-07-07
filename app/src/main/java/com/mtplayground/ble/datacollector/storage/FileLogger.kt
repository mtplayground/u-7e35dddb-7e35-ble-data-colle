package com.mtplayground.ble.datacollector.storage

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import com.mtplayground.ble.datacollector.core.Config
import java.io.BufferedWriter
import java.io.IOException
import java.io.OutputStreamWriter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class LogSession(
    val uri: Uri,
    val displayName: String,
    val startedAtMillis: Long,
)

sealed interface FileLoggerResult {
    data object Success : FileLoggerResult

    data class Failure(
        val message: String,
        val throwable: Throwable? = null,
    ) : FileLoggerResult
}

class FileLogger(
    context: Context,
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) {
    private val appContext = context.applicationContext
    private val contentResolver: ContentResolver = appContext.contentResolver
    private var activeSession: ActiveLogSession? = null

    val currentSession: LogSession?
        get() = activeSession?.logSession

    fun start(deviceName: String): FileLoggerResult {
        stop()

        val startedAtMillis = nowMillis()
        val displayName = buildSessionFileName(
            deviceName = deviceName,
            startedAtMillis = startedAtMillis,
            zoneId = zoneId,
        )
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, Config.sessionMimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        val uri = try {
            contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        } catch (exception: RuntimeException) {
            return FileLoggerResult.Failure("Unable to create the log file.", exception)
        } ?: return FileLoggerResult.Failure("Unable to create the log file.")

        val writer = try {
            contentResolver.openOutputStream(uri, "w")?.let { outputStream ->
                BufferedWriter(OutputStreamWriter(outputStream, Charsets.UTF_8))
            }
        } catch (exception: IOException) {
            deleteUriQuietly(uri)
            return FileLoggerResult.Failure("Unable to open the log file.", exception)
        } catch (exception: RuntimeException) {
            deleteUriQuietly(uri)
            return FileLoggerResult.Failure("Unable to open the log file.", exception)
        } ?: run {
            deleteUriQuietly(uri)
            return FileLoggerResult.Failure("Unable to open the log file.")
        }

        activeSession = ActiveLogSession(
            logSession = LogSession(
                uri = uri,
                displayName = displayName,
                startedAtMillis = startedAtMillis,
            ),
            writer = writer,
        )
        return FileLoggerResult.Success
    }

    fun appendRecord(record: String): FileLoggerResult {
        val session = activeSession
            ?: return FileLoggerResult.Failure("No active log session.")

        return try {
            session.writer.write(record)
            session.writer.newLine()
            session.writer.flush()
            FileLoggerResult.Success
        } catch (exception: Exception) {
            val uri = session.logSession.uri
            closeWriterQuietly(session.writer)
            activeSession = null
            finalizeUriQuietly(uri)
            FileLoggerResult.Failure("Unable to write the log record.", exception)
        }
    }

    fun stop(): FileLoggerResult {
        val session = activeSession ?: return FileLoggerResult.Success
        activeSession = null

        val closeFailure = try {
            session.writer.flush()
            session.writer.close()
            null
        } catch (exception: Exception) {
            exception
        }
        finalizeUriQuietly(session.logSession.uri)

        return if (closeFailure == null) {
            FileLoggerResult.Success
        } else {
            FileLoggerResult.Failure("Unable to close the log file.", closeFailure)
        }
    }

    private fun finalizeUriQuietly(uri: Uri) {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.IS_PENDING, 0)
        }
        try {
            contentResolver.update(uri, values, null, null)
        } catch (_: RuntimeException) {
            // The file may already be visible or removed; callers receive write/open failures above.
        }
    }

    private fun deleteUriQuietly(uri: Uri) {
        try {
            contentResolver.delete(uri, null, null)
        } catch (_: RuntimeException) {
            // Best-effort cleanup for failed MediaStore creation/opening.
        }
    }

    private fun closeWriterQuietly(writer: BufferedWriter) {
        try {
            writer.close()
        } catch (_: Exception) {
            // The original write failure is the useful error to surface.
        }
    }

    private data class ActiveLogSession(
        val logSession: LogSession,
        val writer: BufferedWriter,
    )

    companion object {
        private val FileTimestampFormatter: DateTimeFormatter =
            DateTimeFormatter.ofPattern(Config.sessionTimestampPattern, Locale.US)

        fun buildSessionFileName(
            deviceName: String,
            startedAtMillis: Long,
            zoneId: ZoneId = ZoneId.systemDefault(),
        ): String {
            val safeDeviceName = sanitizeFileNamePart(deviceName)
            val timestamp = Instant.ofEpochMilli(startedAtMillis)
                .atZone(zoneId)
                .format(FileTimestampFormatter)

            return "$safeDeviceName$FileNameSeparator$timestamp.${Config.sessionFileExtension}"
        }

        fun sanitizeFileNamePart(value: String): String =
            value.trim()
                .replace(UnsafeFileNameCharacters, FileNameSeparator)
                .replace(RepeatedSeparators, FileNameSeparator)
                .trim(FileNameSeparator.first())
                .ifBlank { "device" }

        private const val FileNameSeparator = "_"
        private val UnsafeFileNameCharacters = Regex("[^A-Za-z0-9._-]+")
        private val RepeatedSeparators = Regex("_+")
    }
}
