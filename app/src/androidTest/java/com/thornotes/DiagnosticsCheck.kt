package com.thornotes

import android.app.Activity
import android.app.Instrumentation
import android.os.Bundle
import com.thornotes.capture.CaptureDebugLog
import java.io.ByteArrayOutputStream

// Install the debug and androidTest APKs, then run:
// adb shell am instrument -w com.thornotes.test/com.thornotes.DiagnosticsCheck
class DiagnosticsCheck : Instrumentation() {
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        start()
    }

    override fun onStart() {
        // Use the test package so the user's diagnostics and preferences are untouched.
        val result = runCatching {
            CaptureDebugLog.clear(context)
            CaptureDebugLog.append(context, false, "disabled_event")
            check(!CaptureDebugLog.file(context).exists())
            CaptureDebugLog.append(context, true, "write_ok page=test")
            val output = ByteArrayOutputStream()
            CaptureDebugLog.export(context, output)
            check(output.toString("UTF-8").contains("write_ok page=test"))
            repeat(180) { CaptureDebugLog.append(context, true, "x".repeat(1024)) }
            CaptureDebugLog.append(context, true, "latest_event")
            check(CaptureDebugLog.file(context).length() <= 128 * 1024)
            check(CaptureDebugLog.file(context).readText().endsWith("latest_event\n"))
            CaptureDebugLog.clear(context)
            check(!CaptureDebugLog.file(context).exists())
        }
        finish(if (result.isSuccess) Activity.RESULT_OK else Activity.RESULT_CANCELED,
            Bundle().apply { putString("stream", result.exceptionOrNull()?.stackTraceToString() ?: "Diagnostics checks passed\n") })
    }
}
