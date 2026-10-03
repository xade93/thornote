package com.thornotes

import android.app.ActivityManager
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.util.Log
import android.view.Display

internal fun Context.preferredDisplayId(): Int {
    val manager = getSystemService(DisplayManager::class.java)
    val intent = Intent(this, MainActivity::class.java)
    // ponytail: use Android's presentation-display order; add a picker if multiple secondary screens need support.
    return manager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
        .firstOrNull {
            it.isValid && it.state != Display.STATE_OFF &&
                it.displayId != Display.DEFAULT_DISPLAY &&
                it.flags and Display.FLAG_PRIVATE == 0 &&
                getSystemService(ActivityManager::class.java).isActivityStartAllowedOnDisplay(this, it.displayId, intent)
        }?.displayId ?: Display.DEFAULT_DISPLAY
}

internal fun Context.launchNotesOnDisplay(intent: Intent, displayId: Int): Boolean {
    return try {
        startActivity(intent, ActivityOptions.makeBasic().setLaunchDisplayId(displayId).toBundle())
        true
    } catch (error: RuntimeException) {
        // A display can disappear or reject the launch after discovery.
        Log.w("ThorNotes", "Unable to open notes on display $displayId", error)
        false
    }
}
