package com.thornotes

import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.thornotes.analysis.EnglishDictionaryLookup
import com.thornotes.capture.ScreenCaptureManager
import com.thornotes.data.NotebookRepository
import com.thornotes.data.models.AppSettings
import com.thornotes.data.models.CaptureState
import com.thornotes.ocr.TextRecognizer
import com.thornotes.ui.screens.CropScreen
import com.thornotes.ui.screens.HelpScreen
import com.thornotes.ui.screens.MainScreen
import com.thornotes.ui.screens.SettingsScreen
import com.thornotes.ui.theme.ThorNotesTheme

class MainActivity : ComponentActivity() {

    lateinit var captureManager: ScreenCaptureManager
    lateinit var textRecognizer: TextRecognizer
    lateinit var dictionary: EnglishDictionaryLookup
    lateinit var settings: AppSettings
    lateinit var notebook: NotebookRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    finishAndRemoveTask()
                }
            },
        )
        captureManager = ScreenCaptureManager(this)
        textRecognizer = TextRecognizer()
        dictionary = EnglishDictionaryLookup(this)
        settings = AppSettings(this)
        notebook = NotebookRepository(this)
        enableEdgeToEdge()
        setContent {
            ThorNotesTheme {
                var currentScreen by remember { mutableStateOf("main") }
                var captureState by remember { mutableStateOf<CaptureState>(CaptureState.Idle) }
                var cropScreenshot by remember { mutableStateOf<Bitmap?>(null) }

                when (currentScreen) {
                    "settings" -> SettingsScreen(
                        settings = settings,
                        onBack = { currentScreen = "main" },
                    )
                    "help" -> HelpScreen(
                        onBack = { currentScreen = "main" },
                    )
                    "crop" -> {
                        val bmp = cropScreenshot
                        if (bmp != null) {
                            CropScreen(
                                screenshot = bmp,
                                settings = settings,
                                onSave = { currentScreen = "main" },
                                onCancel = { currentScreen = "main" },
                            )
                        } else {
                            currentScreen = "main"
                        }
                    }
                    else -> MainScreen(
                        captureManager = captureManager,
                        textRecognizer = textRecognizer,
                        dictionary = dictionary,
                        settings = settings,
                        notebook = notebook,
                        captureState = captureState,
                        onCaptureStateChange = { captureState = it },
                        onSettingsClick = { currentScreen = "settings" },
                        onCropClick = { bitmap ->
                            cropScreenshot = bitmap
                            currentScreen = "crop"
                        },
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        captureManager.release()
        textRecognizer.close()
        dictionary.close()
    }
}
