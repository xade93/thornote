package com.thornotes

import android.app.Activity
import android.app.Instrumentation
import android.content.ComponentName
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import com.thornotes.capture.ScreenCaptureService
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

// Enable ThorNotes in Accessibility settings, leave an unprotected screen visible, then run:
// adb shell am instrument -w com.thornotes.test/com.thornotes.CaptureCheck
// Captures are recycled without saving to the notebook.
class CaptureCheck : Instrumentation() {
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        start()
    }

    override fun onStart() {
        val result = runCatching {
            val component = ComponentName(targetContext, ScreenCaptureService::class.java)
            val info = targetContext.packageManager.getServiceInfo(component, PackageManager.GET_META_DATA)
            check(info.permission == "android.permission.BIND_ACCESSIBILITY_SERVICE")
            check(info.exported)
            val parser = info.loadXmlMetaData(targetContext.packageManager, "android.accessibilityservice")
            parser.use {
                while (it.next() != org.xmlpull.v1.XmlPullParser.START_TAG) Unit
                check(it.getAttributeBooleanValue("http://schemas.android.com/apk/res/android", "canTakeScreenshot", false))
                check(!it.getAttributeBooleanValue("http://schemas.android.com/apk/res/android", "canRetrieveWindowContent", false))
            }
            runBlocking {
                repeat(50) { if (!ScreenCaptureService.isReady) delay(100) }
                check(ScreenCaptureService.isReady) { "Enable ThorNotes in Accessibility settings before running CaptureCheck" }
                repeat(2) {
                    delay(500) // Exercise captures inside the former one-second cooldown.
                    val bitmap = checkNotNull(ScreenCaptureService.captureScreen()) { "Capture failed" }
                    try {
                        check(bitmap.width > 0 && bitmap.height > 0)
                        check(bitmap.config == Bitmap.Config.ARGB_8888)
                        bitmap.getPixel(0, 0) // Software pixels must be readable by OCR and crop.
                    } finally {
                        bitmap.recycle()
                    }
                }
            }
        }
        finish(if (result.isSuccess) Activity.RESULT_OK else Activity.RESULT_CANCELED,
            Bundle().apply { putString("stream", result.exceptionOrNull()?.stackTraceToString() ?: "Accessibility capture checks passed\n") })
    }
}
