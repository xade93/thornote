package com.kanjilens

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
import com.kanjilens.analysis.DictionaryLookup
import com.kanjilens.analysis.JapaneseTokenizer
import com.kanjilens.capture.ScreenCaptureManager
import com.kanjilens.data.NotebookRepository
import com.kanjilens.data.models.AppSettings
import com.kanjilens.data.models.CaptureState
import com.kanjilens.ocr.TextRecognizer
import com.kanjilens.ui.screens.CropScreen
import com.kanjilens.ui.screens.HelpScreen
import com.kanjilens.ui.screens.MainScreen
import com.kanjilens.ui.screens.SettingsScreen
import com.kanjilens.ui.theme.KanjiLensTheme

class MainActivity : ComponentActivity() {

    lateinit var captureManager: ScreenCaptureManager
    lateinit var textRecognizer: TextRecognizer
    lateinit var tokenizer: JapaneseTokenizer
    lateinit var dictionary: DictionaryLookup
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
        tokenizer = JapaneseTokenizer()
        dictionary = DictionaryLookup(this)
        settings = AppSettings(this)
        notebook = NotebookRepository(this)
        enableEdgeToEdge()
        setContent {
            KanjiLensTheme {
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
                        tokenizer = tokenizer,
                        dictionary = dictionary,
                        settings = settings,
                        notebook = notebook,
                        captureState = captureState,
                        onCaptureStateChange = { captureState = it },
                        onSettingsClick = { currentScreen = "settings" },
                        onHelpClick = { currentScreen = "help" },
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
    }
}
