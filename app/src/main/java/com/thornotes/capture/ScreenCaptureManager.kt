package com.thornotes.capture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

class ScreenCaptureManager(private val context: Context) {

    companion object {
        private const val TAG = "ThorNotes"
        private const val CAPTURE_TIMEOUT_MS = 3_000L
        private const val CAPTURE_COOLDOWN_MS = 1_000L
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private val captureThread = HandlerThread("ThorNotesScreenCapture").apply { start() }
    private val handler = Handler(captureThread.looper)
    private val captureMutex = Mutex()
    private var lastCaptureStartedAt = 0L

    // Callback for when projection is ready
    private var onProjectionReady: (() -> Unit)? = null

    val projectionManager: MediaProjectionManager =
        context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

    val isReady: Boolean
        get() = mediaProjection != null

    fun setProjection(projection: MediaProjection) {
        Log.d(TAG, "MediaProjection received")
        mediaProjection = projection
        onProjectionReady?.invoke()
        onProjectionReady = null
    }

    fun awaitProjectionReady(callback: () -> Unit) {
        if (mediaProjection != null) {
            callback()
        } else {
            onProjectionReady = callback
        }
    }

    suspend fun captureScreen(): Bitmap? {
        if (!captureMutex.tryLock()) {
            Log.w(TAG, "Ignoring overlapping screen capture request")
            return null
        }

        return try {
            val now = SystemClock.elapsedRealtime()
            if (now - lastCaptureStartedAt < CAPTURE_COOLDOWN_MS) {
                Log.w(TAG, "Ignoring rapid screen capture request")
                return null
            }
            lastCaptureStartedAt = now

            withTimeoutOrNull(CAPTURE_TIMEOUT_MS) {
                captureSingleFrame()
            } ?: run {
                Log.e(TAG, "Timed out waiting for screen capture")
                cleanupFrameCapture()
                null
            }
        } finally {
            captureMutex.unlock()
        }
    }

    private suspend fun captureSingleFrame(): Bitmap? = suspendCancellableCoroutine { continuation ->
        val projection = mediaProjection
        if (projection == null) {
            Log.e(TAG, "captureScreen called but mediaProjection is null")
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }

        val metrics = getScreenMetrics()
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        Log.d(TAG, "Capturing screen: ${width}x${height} @ ${density}dpi")

        cleanupFrameCapture()

        // Create ImageReader for single frame capture
        val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 1)
        imageReader = reader

        var captured = false

        reader.setOnImageAvailableListener({ imgReader ->
            if (!captured) {
                captured = true
                val image = imgReader.acquireLatestImage()
                if (image != null) {
                    Log.d(TAG, "Image acquired: ${image.width}x${image.height}")
                    var imageClosed = false
                    fun closeImage() {
                        if (!imageClosed) {
                            runCatching { image.close() }
                                .onFailure { Log.w(TAG, "Failed to close captured image", it) }
                            imageClosed = true
                        }
                    }
                    try {
                        val bitmap = imageToBitmap(image, width, height)
                        closeImage()
                        cleanupFrameCapture()
                        if (continuation.isActive) continuation.resume(bitmap)
                    } catch (e: Throwable) {
                        Log.e(TAG, "Failed to convert screen capture", e)
                        closeImage()
                        cleanupFrameCapture()
                        if (continuation.isActive) continuation.resume(null)
                    }
                } else {
                    Log.e(TAG, "acquireLatestImage returned null")
                    cleanupFrameCapture()
                    if (continuation.isActive) continuation.resume(null)
                }
            }
        }, handler)

        // Create VirtualDisplay AFTER setting the listener
        try {
            val display = projection.createVirtualDisplay(
                "ThorNotesCapture",
                width, height, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.surface,
                null, null
            )
            virtualDisplay = display
            Log.d(TAG, "VirtualDisplay created")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create VirtualDisplay", e)
            cleanupFrameCapture()
            if (continuation.isActive) continuation.resume(null)
        }

        continuation.invokeOnCancellation {
            cleanupFrameCapture()
        }
    }

    fun release() {
        cleanupFrameCapture()
        mediaProjection?.stop()
        mediaProjection = null
        captureThread.quitSafely()
    }

    private fun cleanupFrameCapture() {
        virtualDisplay?.release()
        imageReader?.setOnImageAvailableListener(null, null)
        imageReader?.close()
        virtualDisplay = null
        imageReader = null
    }

    private fun imageToBitmap(image: android.media.Image, width: Int, height: Int): Bitmap {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * width

        val bitmapWidth = width + rowPadding / pixelStride
        val bitmap = Bitmap.createBitmap(bitmapWidth, height, Bitmap.Config.ARGB_8888)
        bitmap.copyPixelsFromBuffer(buffer)

        return if (bitmapWidth != width) {
            val cropped = Bitmap.createBitmap(bitmap, 0, 0, width, height)
            bitmap.recycle()
            cropped
        } else {
            bitmap
        }
    }

    private fun getScreenMetrics(): DisplayMetrics {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)
        return metrics
    }
}
