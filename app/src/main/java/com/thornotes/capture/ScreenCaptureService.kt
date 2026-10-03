package com.thornotes.capture

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Bitmap
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeoutOrNull

class ScreenCaptureService : AccessibilityService() {
    companion object {
        @Volatile
        private var instance: ScreenCaptureService? = null
        val isReady: Boolean get() = instance != null

        suspend fun captureScreen(): Bitmap? = instance?.capture()
    }

    private val captureMutex = Mutex()

    override fun onServiceConnected() {
        instance = this
        CaptureDebugLog.append(this, "accessibility_connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        if (instance === this) instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private suspend fun capture(): Bitmap? {
        if (!captureMutex.tryLock()) return null
        try {
            CaptureDebugLog.append(this, "capture_started")
            return withTimeoutOrNull(3_000) {
                suspendCancellableCoroutine { continuation ->
                    // Also release a delivered bitmap if cancellation wins before the caller resumes.
                    fun complete(bitmap: Bitmap?) {
                        continuation.resume(bitmap) { bitmap?.recycle() }
                    }
                    try {
                        takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
                            override fun onSuccess(result: ScreenshotResult) {
                                val bitmap = try {
                                    result.hardwareBuffer.use { buffer ->
                                        if (!continuation.isActive) return@use null
                                        val hardwareBitmap = Bitmap.wrapHardwareBuffer(buffer, result.colorSpace)
                                        try {
                                            hardwareBitmap?.copy(Bitmap.Config.ARGB_8888, false)
                                        } finally {
                                            hardwareBitmap?.recycle()
                                        }
                                    }
                                } catch (error: Exception) {
                                    CaptureDebugLog.append(this@ScreenCaptureService, "capture_conversion_failed error=${error.javaClass.simpleName}")
                                    null
                                }
                                CaptureDebugLog.append(this@ScreenCaptureService, "capture_complete success=${bitmap != null}")
                                complete(bitmap)
                            }

                            override fun onFailure(errorCode: Int) {
                                CaptureDebugLog.append(this@ScreenCaptureService, "capture_failed code=$errorCode")
                                complete(null)
                            }
                        })
                    } catch (error: Exception) {
                        CaptureDebugLog.append(this@ScreenCaptureService, "capture_failed error=${error.javaClass.simpleName}")
                        complete(null)
                    }
                }
            }
        } finally {
            captureMutex.unlock()
        }
    }
}
