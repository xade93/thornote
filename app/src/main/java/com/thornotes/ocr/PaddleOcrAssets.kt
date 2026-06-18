package com.thornotes.ocr

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.net.URL
import java.util.zip.GZIPInputStream

class PaddleOcrAssets(private val context: Context) {

    data class Status(
        val ready: Boolean = false,
        val downloading: Boolean = false,
        val message: String = "",
        val installedBytes: Long = 0L,
    )

    companion object {
        private const val MODEL_BASE = "https://paddlelite-demo.bj.bcebos.com/paddle-x/ocr/models"
        private const val DET_ARCHIVE = "$MODEL_BASE/PP-OCRv5_mobile_det.tar.gz"
        private const val REC_ARCHIVE = "$MODEL_BASE/PP-OCRv5_mobile_rec.tar.gz"
        private const val CLS_ARCHIVE = "$MODEL_BASE/PP-LCNet_x0_25_textline_ori.tar.gz"

        const val DET_MODEL = "PP-OCRv5_mobile_det.nb"
        const val REC_MODEL = "PP-OCRv5_mobile_rec.nb"
        const val CLS_MODEL = "PP-LCNet_x0_25_textline_ori.nb"
        const val CONFIG = "config.txt"
        const val LABELS = "ppocr_keys_ocrv5.txt"

        private const val DET_MODEL_SIZE = 5_001_214L
        private const val REC_MODEL_SIZE = 16_718_470L
        private const val CLS_MODEL_SIZE = 1_054_222L
    }

    private val modelDir = File(context.filesDir, "paddle_ocr/models")
    private val supportDir = File(context.filesDir, "paddle_ocr/support")
    private val installMutex = Mutex()

    private val _status = MutableStateFlow(currentStatus())
    val status: StateFlow<Status> = _status

    val detModelPath: String get() = File(modelDir, DET_MODEL).absolutePath
    val recModelPath: String get() = File(modelDir, REC_MODEL).absolutePath
    val clsModelPath: String get() = File(modelDir, CLS_MODEL).absolutePath
    val configPath: String get() = File(supportDir, CONFIG).absolutePath
    val labelsPath: String get() = File(supportDir, LABELS).absolutePath

    fun refresh() {
        _status.value = currentStatus()
    }

    suspend fun ensureDownloaded(): Boolean = withContext(Dispatchers.IO) {
        installMutex.withLock {
            if (isReady()) {
                _status.value = currentStatus()
                return@withLock true
            }

            _status.value = Status(downloading = true, message = "Downloading PP-OCRv5")
            try {
                modelDir.deleteRecursively()
                modelDir.mkdirs()
                supportDir.mkdirs()
                copySupportAsset(CONFIG)
                copySupportAsset(LABELS)
                downloadArchive(DET_ARCHIVE, modelDir)
                downloadArchive(REC_ARCHIVE, modelDir)
                downloadArchive(CLS_ARCHIVE, modelDir)
                if (!hasValidModels()) {
                    modelDir.deleteRecursively()
                    _status.value = Status(message = "PP-OCRv5 download incomplete")
                    return@withLock false
                }
                _status.value = currentStatus()
                isReady()
            } catch (e: Exception) {
                modelDir.deleteRecursively()
                _status.value = Status(message = e.message ?: "Download failed")
                false
            }
        }
    }

    fun isReady(): Boolean =
        hasValidModels() &&
            File(configPath).isFile &&
            File(labelsPath).isFile

    private fun hasValidModels(): Boolean =
        File(detModelPath).length() == DET_MODEL_SIZE &&
            File(recModelPath).length() == REC_MODEL_SIZE &&
            File(clsModelPath).length() == CLS_MODEL_SIZE

    suspend fun uninstall() = withContext(Dispatchers.IO) {
        installMutex.withLock {
            File(context.filesDir, "paddle_ocr").deleteRecursively()
            _status.value = currentStatus()
        }
    }

    private fun currentStatus(): Status {
        val ready = isReady()
        val size = File(context.filesDir, "paddle_ocr").sizeBytes()
        return Status(
            ready = ready,
            installedBytes = size,
            message = if (ready) "PP-OCRv5 ready (${size.formatBytes()})" else "PP-OCRv5 not downloaded",
        )
    }

    private fun copySupportAsset(name: String) {
        val target = File(supportDir, name)
        if (target.isFile) return
        context.assets.open("paddle_ocr/$name").use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
    }

    private fun downloadArchive(url: String, destination: File) {
        URL(url).openStream().use { stream ->
            GZIPInputStream(stream).use { gzip ->
                extractTar(gzip, destination)
            }
        }
    }

    private fun extractTar(input: InputStream, destination: File) {
        val header = ByteArray(512)
        while (true) {
            if (!input.readFully(header)) return
            if (header.all { it == 0.toByte() }) return

            val name = header.nameField()
            val size = header.sizeField()
            val type = header[156].toInt().toChar()
            val outputFile = File(destination, name.substringAfterLast('/'))

            if (type == '0' || type == '\u0000') {
                outputFile.outputStream().use { output ->
                    input.copyExactTo(output, size)
                }
            } else {
                input.skipExact(size)
            }

            val padding = (512 - (size % 512)) % 512
            input.skipExact(padding)
        }
    }

    private fun ByteArray.nameField(): String =
        copyOfRange(0, 100)
            .takeWhile { it != 0.toByte() }
            .toByteArray()
            .toString(Charsets.UTF_8)

    private fun ByteArray.sizeField(): Long {
        val raw = copyOfRange(124, 136)
            .takeWhile { it != 0.toByte() && it != ' '.code.toByte() }
            .toByteArray()
            .toString(Charsets.US_ASCII)
            .trim()
        return raw.ifBlank { "0" }.toLong(8)
    }

    private fun InputStream.readFully(buffer: ByteArray): Boolean {
        var offset = 0
        while (offset < buffer.size) {
            val read = read(buffer, offset, buffer.size - offset)
            if (read == -1) return offset == 0
            offset += read
        }
        return true
    }

    private fun InputStream.copyExactTo(output: java.io.OutputStream, bytes: Long) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var remaining = bytes
        while (remaining > 0) {
            val read = read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            if (read == -1) break
            output.write(buffer, 0, read)
            remaining -= read
        }
    }

    private fun InputStream.skipExact(bytes: Long) {
        var remaining = bytes
        while (remaining > 0) {
            val skipped = skip(remaining)
            if (skipped <= 0) {
                if (read() == -1) return
                remaining -= 1
            } else {
                remaining -= skipped
            }
        }
    }

    private fun File.sizeBytes(): Long {
        if (!exists()) return 0L
        if (isFile) return length()
        return listFiles()?.sumOf { it.sizeBytes() } ?: 0L
    }

    private fun Long.formatBytes(): String {
        val mb = this / (1024.0 * 1024.0)
        return if (mb >= 1.0) "%.1f MB".format(mb) else "$this B"
    }
}
