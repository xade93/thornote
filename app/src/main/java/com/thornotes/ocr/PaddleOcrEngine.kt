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
    private val nativeLock = Any()

    suspend fun recognize(bitmap: Bitmap): String? = withContext(Dispatchers.Default) {
        synchronized(nativeLock) {
            val native = native ?: return@synchronized null
            if (!assets.isReady()) return@synchronized null
            if (!initialized) {
                initialized = native.init(assets)
                if (!initialized) {
                    Log.w(TAG, "Paddle OCR failed to initialize")
                    return@synchronized null
                }
            }
            native.recognize(bitmap)
        }
    }

    fun close() {
        synchronized(nativeLock) {
            native?.release()
            initialized = false
        }
    }
}
