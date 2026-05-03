package com.thornotes.ocr

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PaddleOcrEngine(context: Context) {

    companion object {
        private const val TAG = "ThorNotes"
    }

    val assets = PaddleOcrAssets(context.applicationContext)
    private val native = runCatching { PaddleOcrNative() }
        .onFailure { Log.w(TAG, "Paddle OCR native library unavailable", it) }
        .getOrNull()
    private var initialized = false

    suspend fun recognize(bitmap: Bitmap): String? = withContext(Dispatchers.Default) {
        val native = native ?: return@withContext null
        if (!assets.isReady()) return@withContext null
        if (!initialized) {
            initialized = native.init(assets)
            if (!initialized) {
                Log.w(TAG, "Paddle OCR failed to initialize")
                return@withContext null
            }
        }
        native.recognize(bitmap)
    }

    fun close() {
        native?.release()
        initialized = false
    }
}
