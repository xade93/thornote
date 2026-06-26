package com.thornotes.capture

import android.content.Context
import android.os.SystemClock
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CaptureDebugLog {
    private const val FILE_NAME = "capture_debug.log"
    private const val MAX_BYTES = 128 * 1024
    private const val KEEP_BYTES = 96 * 1024
    private val lock = Any()
    private val timestampFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    fun file(context: Context): File {
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        return File(dir, FILE_NAME)
    }

    fun append(context: Context, enabled: Boolean, event: String) {
        if (!enabled) return
        synchronized(lock) {
            val logFile = file(context)
            runCatching {
                logFile.parentFile?.mkdirs()
                logFile.appendText("${timestamp()} +${SystemClock.elapsedRealtime()}ms $event\n")
                trimIfNeeded(logFile)
            }
        }
    }

    private fun timestamp(): String = synchronized(timestampFormat) {
        timestampFormat.format(Date())
    }

    private fun trimIfNeeded(logFile: File) {
        if (logFile.length() <= MAX_BYTES) return
        val bytes = logFile.readBytes()
        val start = (bytes.size - KEEP_BYTES).coerceAtLeast(0)
        val newline = firstNewlineAtOrAfter(bytes, start)
        val trimmed = if (newline >= 0 && newline + 1 < bytes.size) {
            bytes.copyOfRange(newline + 1, bytes.size)
        } else {
            bytes.copyOfRange(start, bytes.size)
        }
        logFile.writeBytes(trimmed)
    }

    private fun firstNewlineAtOrAfter(bytes: ByteArray, start: Int): Int {
        for (index in start until bytes.size) {
            if (bytes[index] == '\n'.code.toByte()) return index
        }
        return -1
    }
}
