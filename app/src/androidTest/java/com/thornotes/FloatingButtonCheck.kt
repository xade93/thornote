package com.thornotes

import android.app.Instrumentation
import android.os.SystemClock
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.TextView
import java.util.concurrent.atomic.AtomicInteger

// Runs through DiagnosticsCheck, without opening or changing the user's notebook.
fun checkFloatingButtonTaps(instrumentation: Instrumentation) {
    val singles = AtomicInteger()
    val doubles = AtomicInteger()
    lateinit var button: TextView
    instrumentation.runOnMainSync {
        button = TextView(instrumentation.context).apply {
            setFloatingButtonActions({ singles.incrementAndGet() }, { doubles.incrementAndGet() })
        }
    }
    fun tap() {
        val down = SystemClock.uptimeMillis()
        fun send(action: Int) = instrumentation.runOnMainSync {
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, 20f, 20f, 0)
            button.dispatchTouchEvent(event)
            event.recycle()
        }
        send(MotionEvent.ACTION_DOWN)
        SystemClock.sleep(20)
        send(MotionEvent.ACTION_UP)
    }
    fun settle() {
        SystemClock.sleep(ViewConfiguration.getDoubleTapTimeout().toLong() + 100)
        instrumentation.waitForIdleSync()
    }
    tap()
    settle()
    check(singles.get() == 1 && doubles.get() == 0)
    tap()
    SystemClock.sleep(80)
    tap()
    settle()
    check(singles.get() == 1 && doubles.get() == 1) { "Double-tap also toggled the app" }
    instrumentation.runOnMainSync {
        button.setFloatingButtonActions({ singles.incrementAndGet() }, {})
    }
    tap()
    SystemClock.sleep(80)
    tap()
    settle()
    check(singles.get() == 1 && doubles.get() == 1) { "Do nothing toggled the app" }
    tap()
    settle()
    check(singles.get() == 2)
}
