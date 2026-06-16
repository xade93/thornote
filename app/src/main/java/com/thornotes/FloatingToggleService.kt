package com.thornotes

import android.app.Service
import android.content.Intent
import android.hardware.display.DisplayManager
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.IBinder
import android.provider.Settings
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.TextView

class FloatingToggleService : Service() {

    private var windowManager: WindowManager? = null
    private var toggleView: View? = null
    private var overlayDisplayId = Display.DEFAULT_DISPLAY
    private var appHidden = false

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
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = radius
                setColor(0xCC1A1A2E.toInt())
                setStroke((1.5f * density).toInt(), 0xFFE91E63.toInt())
            }
            setOnClickListener { toggleAppVisibility() }
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

    companion object {
        const val ACTION_APP_VISIBLE = "com.thornotes.ACTION_APP_VISIBLE"
        const val EXTRA_DISPLAY_ID = "com.thornotes.EXTRA_DISPLAY_ID"
    }
}
