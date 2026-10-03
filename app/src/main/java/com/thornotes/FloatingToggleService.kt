package com.thornotes

import android.app.Service
import android.annotation.SuppressLint
import android.content.Intent
import android.hardware.display.DisplayManager
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.IBinder
import android.provider.Settings
import android.view.Display
import android.view.Gravity
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import com.thornotes.capture.CaptureDebugLog
import com.thornotes.capture.ScreenCaptureService
import com.thornotes.data.models.AppSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FloatingToggleService : Service() {

    private var windowManager: WindowManager? = null
    private var toggleView: TextView? = null
    private var overlayDisplayId = Display.DEFAULT_DISPLAY
    private var appHidden = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var capturing = false
    private val app get() = application as ThorNotesApp

    override fun onCreate() {
        super.onCreate()
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }

        val displayId = intent?.getIntExtra(EXTRA_DISPLAY_ID, Display.DEFAULT_DISPLAY)
            ?: Display.DEFAULT_DISPLAY
        ensureToggleView(displayId)

        if (intent?.action == ACTION_APP_VISIBLE) {
            appHidden = false
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        removeToggleView()
        super.onDestroy()
    }

    private fun ensureToggleView(displayId: Int) {
        if (toggleView != null && overlayDisplayId == displayId) return

        removeToggleView()
        overlayDisplayId = displayId

        val displayContext = displayContext(displayId)
        val density = displayContext.resources.displayMetrics.density
        val size = (42 * density).toInt()
        val margin = (10 * density).toInt()
        val radius = 10 * density
        val manager = displayContext.getSystemService(WINDOW_SERVICE) as WindowManager

        val view = TextView(displayContext).apply {
            text = "T"
            contentDescription = "ThorNotes floating button"
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = radius
                setColor(0xCC1A1A2E.toInt())
                setStroke((1.5f * density).toInt(), 0xFFE91E63.toInt())
            }
            setFloatingButtonActions(
                onSingleTap = { if (!capturing) toggleAppVisibility() },
                onDoubleTap = ::captureTopScreen,
            )
        }

        val params = WindowManager.LayoutParams(
            size,
            size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = margin
            y = margin
        }

        manager.addView(view, params)
        windowManager = manager
        toggleView = view
    }

    private fun removeToggleView() {
        toggleView?.let { view ->
            windowManager?.removeView(view)
        }
        toggleView = null
        windowManager = null
    }

    private fun displayContext(displayId: Int) = (
        getSystemService(DISPLAY_SERVICE) as DisplayManager
    ).getDisplay(displayId)?.let { display ->
        createDisplayContext(display)
    } ?: this

    private fun serviceIntent() = Intent(this, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
            Intent.FLAG_ACTIVITY_SINGLE_TOP
    }

    private fun toggleAppVisibility() {
        if (appHidden) {
            startActivity(serviceIntent())
            appHidden = false
        } else {
            sendBroadcast(Intent(MainActivity.ACTION_HIDE_APP).setPackage(packageName))
            appHidden = true
        }
    }

    private fun captureTopScreen() {
        if (capturing || app.settings.floatingDoubleTapAction.value != AppSettings.FLOATING_ACTION_TOP) return
        capturing = true
        scope.launch {
            val view = toggleView
            try {
                check(ScreenCaptureService.isReady) {
                    "Enable ThorNotes in Android Accessibility settings, then try again."
                }
                showFeedback("…", Color.WHITE, "Capturing top screen")
                if (overlayDisplayId == Display.DEFAULT_DISPLAY) {
                    view?.alpha = 0f
                    delay(150) // Let the top-screen capture update without the button.
                }
                val bitmap = ScreenCaptureService.captureScreen() ?: error("Couldn’t capture. Wait a moment and try again.")
                try {
                    withContext(Dispatchers.IO) { app.notebook.addScreenshot(bitmap) }
                } finally {
                    bitmap.recycle()
                }
                showFeedback("✓", 0xFF66BB6A.toInt(), "Screenshot saved")
                CaptureDebugLog.append(this@FloatingToggleService, "floating_capture_saved")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                showFeedback("!", 0xFFEF5350.toInt(), "Screenshot failed")
                Toast.makeText(this@FloatingToggleService,
                    if (!ScreenCaptureService.isReady) {
                        "Enable ThorNotes in Android Accessibility settings."
                    } else {
                        "Couldn’t save screenshot. Try again."
                    }, Toast.LENGTH_LONG).show()
                CaptureDebugLog.append(this@FloatingToggleService, "floating_capture_failed error=${error.javaClass.simpleName}")
            } finally {
                view?.alpha = 1f
            }
            try {
                delay(1_000)
            } finally {
                showFeedback("T", Color.WHITE, "ThorNotes floating button")
                capturing = false
            }
        }
    }

    private fun showFeedback(label: String, color: Int, description: String) {
        toggleView?.apply {
            text = label
            setTextColor(color)
            contentDescription = description
        }
    }

    companion object {
        const val ACTION_APP_VISIBLE = "com.thornotes.ACTION_APP_VISIBLE"
        const val EXTRA_DISPLAY_ID = "com.thornotes.EXTRA_DISPLAY_ID"
    }
}

@SuppressLint("ClickableViewAccessibility") // Confirmed single taps go through performClick().
internal fun TextView.setFloatingButtonActions(onSingleTap: () -> Unit, onDoubleTap: () -> Unit) {
    setOnClickListener { onSingleTap() }
    val detector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(event: MotionEvent) = true
        override fun onSingleTapConfirmed(event: MotionEvent): Boolean {
            performClick()
            return true
        }
        override fun onDoubleTap(event: MotionEvent) = true
        override fun onDoubleTapEvent(event: MotionEvent): Boolean {
            if (event.actionMasked == MotionEvent.ACTION_UP) onDoubleTap()
            return true
        }
    })
    setOnTouchListener { _, event ->
        detector.onTouchEvent(event)
        true
    }
    addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(view: View) = Unit
        override fun onViewDetachedFromWindow(view: View) {
            val cancel = MotionEvent.obtain(0, 0, MotionEvent.ACTION_CANCEL, 0f, 0f, 0)
            detector.onTouchEvent(cancel)
            cancel.recycle()
        }
    })
}
