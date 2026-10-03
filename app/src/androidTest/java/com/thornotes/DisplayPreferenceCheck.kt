package com.thornotes

import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display

// The private presentation must never steal the app or button from a real screen.
fun checkDisplayPreference(context: Context) {
    val manager = context.getSystemService(DisplayManager::class.java)
    val before = context.preferredDisplayId()
    val privateDisplay = checkNotNull(manager.createVirtualDisplay(
        "ThorNotes private display check", 320, 240, 160, null,
        DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION or
            DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY,
    ))
    try {
        check(context.preferredDisplayId() == before)
        check(context.preferredDisplayId() != privateDisplay.display.displayId)
        if (manager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
                .none { it.displayId != privateDisplay.display.displayId }) {
            check(context.preferredDisplayId() == Display.DEFAULT_DISPLAY)
        }
    } finally {
        privateDisplay.release()
    }
}
