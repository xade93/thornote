package com.thornotes

import android.app.Application
import com.thornotes.data.NotebookRepository
import com.thornotes.data.models.AppSettings

class ThorNotesApp : Application() {
    val settings by lazy { AppSettings(this) }
    // The editor and floating captures must share one writer and the same current page.
    val notebook by lazy { NotebookRepository(this) }
}
