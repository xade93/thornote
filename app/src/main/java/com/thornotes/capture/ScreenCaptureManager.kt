package com.thornotes.capture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjection.Callback
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import com.thornotes.data.models.AppSettings
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

class ScreenCaptureManager(
    private val context: Context,
    private val settings: AppSettings,
) {

    companion object {
        private const val TAG = "ThorNotes"
        private const val CAPTURE_TIMEOUT_MS = 3_000L
        private const val CAPTURE_COOLDOWN_MS = 1_000L
        private const val PROJECTION_SETTLE_MS = 1_500L
    }

    @Volatile
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var captureWidth = 0
    private var captureHeight = 0
    private var captureDensity = 0
    private var pendingCapture: kotlinx.coroutines.CancellableContinuation<Bitmap?>? = null
    private var cachedFrame: Bitmap? = null
    private val captureThread = HandlerThread("ThorNotesScreenCapture").apply { start() }
    private val handler = Handler(captureThread.looper)
    private val captureMutex = Mutex()
    private var lastCaptureStartedAt = 0L
    private var projectionReceivedAt = 0L

    // Callback for when projection is ready
    private var onProjectionReady: (() -> Unit)? = null
    private val projectionCallback = object : Callback() {
        override fun onStop() {
            handler.post {
                cleanupFrameCaptureOnHandler()
                mediaProjection = null
            }
        }
    }

    val projectionManager: MediaProjectionManager =
        context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

    val isReady: Boolean
        get() = mediaProjection != null

    fun setProjection(projection: MediaProjection) {
        Log.d(TAG, "MediaProjection received")
        captureLog("projection_received replacingExisting=${mediaProjection != null}")
        mediaProjection?.unregisterCallback(projectionCallback)
        mediaProjection = projection
        projectionReceivedAt = SystemClock.elapsedRealtime()
        projection.registerCallback(projectionCallback, handler)
        handler.post {
            cleanupFrameCaptureOnHandler()
        }
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
        captureLog("capture_request ready=${mediaProjection != null}")
        if (!captureMutex.tryLock()) {
            Log.w(TAG, "Ignoring overlapping screen capture request")
            captureLog("capture_rejected reason=overlap")
            return null
        }

        return try {
            val now = SystemClock.elapsedRealtime()
            if (now - lastCaptureStartedAt < CAPTURE_COOLDOWN_MS) {
                Log.w(TAG, "Ignoring rapid screen capture request")
                captureLog("capture_rejected reason=cooldown elapsed=${now - lastCaptureStartedAt}")
                return null
            }
            lastCaptureStartedAt = now
            captureLog("capture_started")
            waitForProjectionToSettleIfNeeded()

            withTimeoutOrNull(CAPTURE_TIMEOUT_MS) {
                captureSingleFrame()
            } ?: run {
                Log.e(TAG, "Timed out waiting for screen capture")
                captureLog("capture_timeout")
                handler.post {
                    clearPendingCaptureOnHandler()
                }
                null
            }
        } finally {
            captureMutex.unlock()
        }
    }

    private suspend fun waitForProjectionToSettleIfNeeded() {
        if (mediaProjection == null || projectionReceivedAt == 0L || virtualDisplay != null) return

        val elapsed = SystemClock.elapsedRealtime() - projectionReceivedAt
        val remaining = PROJECTION_SETTLE_MS - elapsed
        if (remaining <= 0L) return

        captureLog("projection_settle_wait millis=$remaining elapsed=$elapsed")
        delay(remaining)
    }

    private suspend fun captureSingleFrame(): Bitmap? = suspendCancellableCoroutine { continuation ->
        handler.post {
            captureSingleFrameOnHandler(continuation)
        }
        continuation.invokeOnCancellation {
            handler.post {
                if (pendingCapture === continuation) {
                    clearPendingCaptureOnHandler()
                }
            }
        }
    }

    private fun captureSingleFrameOnHandler(continuation: kotlinx.coroutines.CancellableContinuation<Bitmap?>) {
        if (pendingCapture != null) {
            captureLog("capture_rejected_on_handler reason=pending_capture")
            if (continuation.isActive) continuation.resume(null)
            return
        }

        if (!ensureCaptureSessionOnHandler()) {
            captureLog("capture_failed_on_handler reason=session_unavailable")
            if (continuation.isActive) continuation.resume(null)
            return
        }

        val reader = imageReader
        if (reader != null) {
            val immediateBitmap = takeCachedFrameOnHandler()
                ?: acquireBitmapFromReaderOnHandler(reader)
            if (immediateBitmap != null) {
                captureLog("capture_complete source=immediate width=${immediateBitmap.width} height=${immediateBitmap.height}")
                if (continuation.isActive) continuation.resume(immediateBitmap)
                return
            }
        }

        captureLog("capture_pending waiting_for_image")
        pendingCapture = continuation
    }

    private fun ensureCaptureSessionOnHandler(): Boolean {
        val projection = mediaProjection
        if (projection == null) {
            Log.e(TAG, "captureScreen called but mediaProjection is null")
            captureLog("capture_session_unavailable reason=no_projection")
            return false
        }

        val metrics = getScreenMetrics()
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        if (
            virtualDisplay != null &&
            imageReader != null &&
            captureWidth == width &&
            captureHeight == height &&
            captureDensity == density
        ) {
            captureLog("capture_session_reused width=$width height=$height density=$density cached=${cachedFrame != null}")
            return true
        }

        cleanupFrameCaptureOnHandler()
        Log.d(TAG, "Creating capture session: ${width}x${height} @ ${density}dpi")
        captureLog(
            "capture_session_creating width=$width height=$height density=$density projectionAge=${SystemClock.elapsedRealtime() - projectionReceivedAt}"
        )

        val reader = try {
            ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 3)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create ImageReader", e)
            captureLog("image_reader_create_failed error=${e.javaClass.simpleName}")
            return false
        }
        imageReader = reader

        reader.setOnImageAvailableListener({ imgReader ->
            handleImageAvailableOnHandler(imgReader)
        }, handler)

        try {
            val display = projection.createVirtualDisplay(
                "ThorNotesCapture",
                width, height, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.surface,
                null, null
            )
            virtualDisplay = display
            captureWidth = width
            captureHeight = height
            captureDensity = density
            Log.d(TAG, "VirtualDisplay created")
            captureLog("virtual_display_created width=$width height=$height density=$density")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create VirtualDisplay", e)
            captureLog("virtual_display_create_failed error=${e.javaClass.simpleName}")
            cleanupFrameCaptureOnHandler()
            return false
        }
    }

    private fun handleImageAvailableOnHandler(reader: ImageReader) {
        val bitmap = acquireBitmapFromReaderOnHandler(reader)
        val continuation = pendingCapture
        if (continuation == null) {
            if (bitmap != null) {
                replaceCachedFrameOnHandler(bitmap)
            }
            return
        }

        if (bitmap == null) {
            completePendingCaptureOnHandler(null)
        } else {
            captureLog("capture_complete source=listener width=${bitmap.width} height=${bitmap.height}")
            completePendingCaptureOnHandler(bitmap)
        }
    }

    private fun acquireBitmapFromReaderOnHandler(reader: ImageReader): Bitmap? {
        val image = try {
            reader.acquireLatestImage()
        } catch (e: IllegalStateException) {
            return null
        }
        if (image == null) {
            Log.e(TAG, "acquireLatestImage returned null")
            captureLog("image_acquire_empty")
            return null
        }

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
            val bitmap = imageToBitmap(image, captureWidth, captureHeight)
            closeImage()
            return bitmap
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to convert screen capture", e)
            captureLog("image_convert_failed error=${e.javaClass.simpleName}")
            closeImage()
            return null
        }
    }

    private fun completePendingCaptureOnHandler(bitmap: Bitmap?) {
        val continuation = pendingCapture ?: return
        pendingCapture = null
        if (continuation.isActive) {
            continuation.resume(bitmap)
        } else {
            bitmap?.recycle()
        }
    }

    private fun clearPendingCaptureOnHandler() {
        if (pendingCapture != null) {
        }
        pendingCapture = null
    }

    private fun takeCachedFrameOnHandler(): Bitmap? {
        val bitmap = cachedFrame ?: return null
        cachedFrame = null
        return bitmap
    }

    private fun replaceCachedFrameOnHandler(bitmap: Bitmap) {
        cachedFrame?.recycle()
        cachedFrame = bitmap
    }

    fun release() {
        handler.post {
            cleanupFrameCaptureOnHandler()
            captureThread.quitSafely()
        }
        mediaProjection?.unregisterCallback(projectionCallback)
        mediaProjection?.stop()
        mediaProjection = null
    }

    private fun cleanupFrameCaptureOnHandler() {
        clearPendingCaptureOnHandler()
        if (virtualDisplay != null || imageReader != null) {
            captureLog("capture_session_cleanup display=${virtualDisplay != null} reader=${imageReader != null} cached=${cachedFrame != null}")
        }
        virtualDisplay?.release()
        imageReader?.setOnImageAvailableListener(null, null)
        imageReader?.close()
        cachedFrame?.recycle()
        cachedFrame = null
        virtualDisplay = null
        imageReader = null
        captureWidth = 0
        captureHeight = 0
        captureDensity = 0
    }

    private fun captureLog(event: String) {
        CaptureDebugLog.append(context, settings.captureDebugLogEnabled.value, event)
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
