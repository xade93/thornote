package com.thornotes.ocr

import android.graphics.Bitmap

class PaddleOcrNative {
    companion object {
        init {
            System.loadLibrary("c++_shared")
            System.loadLibrary("paddle_light_api_shared")
            System.loadLibrary("thornotes_paddle_ocr")
        }
    }

    private var handle: Long = 0

    fun init(assets: PaddleOcrAssets): Boolean {
        release()
        handle = nativeInit(
            assets.detModelPath,
            assets.clsModelPath,
            assets.recModelPath,
            assets.configPath,
            assets.labelsPath,
            Runtime.getRuntime().availableProcessors().coerceIn(2, 4),
            "LITE_POWER_HIGH",
        )
        return handle != 0L
    }

    fun recognize(bitmap: Bitmap): String? {
        if (handle == 0L) return null
        return nativeRecognizeBitmap(handle, bitmap).trim().ifBlank { null }
    }

    fun release() {
        if (handle != 0L) {
            nativeRelease(handle)
            handle = 0
        }
    }

    private external fun nativeInit(
        detModelPath: String,
        clsModelPath: String,
        recModelPath: String,
        configPath: String,
        labelPath: String,
        cpuThreadNum: Int,
        cpuPowerMode: String,
    ): Long

    private external fun nativeRelease(handle: Long)

    private external fun nativeRecognizeBitmap(handle: Long, bitmap: Bitmap): String
}
