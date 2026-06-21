package com.thornotes

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.Log
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
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
import com.thornotes.ui.screens.WelcomeScreen
import com.thornotes.ui.theme.ThorNotesTheme

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "ThorNotes"
        const val ACTION_HIDE_APP = "com.thornotes.ACTION_HIDE_APP"
    }

    lateinit var captureManager: ScreenCaptureManager
    lateinit var textRecognizer: TextRecognizer
    lateinit var dictionary: EnglishDictionaryLookup
    lateinit var settings: AppSettings
    lateinit var notebook: NotebookRepository
    private var releasedInputFocus = false
    private val hideAppReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_HIDE_APP) {
                moveTaskToBack(true)
            }
        }
    }

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
        textRecognizer = TextRecognizer(this)
        dictionary = EnglishDictionaryLookup(this)
        settings = AppSettings(this)
        notebook = NotebookRepository(this)
        ContextCompat.registerReceiver(
            this,
            hideAppReceiver,
            IntentFilter(ACTION_HIDE_APP),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        enableEdgeToEdge()
        setContent {
            ThorNotesTheme {
                var currentScreen by remember { mutableStateOf("main") }
                var captureState by remember { mutableStateOf<CaptureState>(CaptureState.Idle) }
                var cropScreenshot by remember { mutableStateOf<Bitmap?>(null) }
                val floatingToggleEnabled by settings.floatingToggleEnabled.collectAsState()
                val welcomeSeen by settings.welcomeSeen.collectAsState()

                fun clearCropScreenshot() {
                    cropScreenshot?.recycle()
                    cropScreenshot = null
                }

                LaunchedEffect(welcomeSeen) {
                    if (!welcomeSeen && currentScreen == "main") {
                        currentScreen = "welcome"
                    }
                }

                LaunchedEffect(floatingToggleEnabled) {
                    syncFloatingToggleService()
                }

                when (currentScreen) {
                    "welcome" -> WelcomeScreen(
                        onDone = {
                            settings.markWelcomeSeen()
                            currentScreen = "main"
                        },
                    )
                    "settings" -> SettingsScreen(
                        settings = settings,
                        textRecognizer = textRecognizer,
                        notebook = notebook,
                        onBack = { currentScreen = "main" },
                        onShowWelcome = {
                            settings.resetWelcomeSeen()
                            currentScreen = "welcome"
                        },
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
                                onSave = {
                                    clearCropScreenshot()
                                    currentScreen = "main"
                                },
                                onCancel = {
                                    clearCropScreenshot()
                                    currentScreen = "main"
                                },
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
                        onRestoreGameFocus = ::restoreGameFocus,
                        onCropClick = { bitmap ->
                            clearCropScreenshot()
                            cropScreenshot = bitmap
                            currentScreen = "crop"
                        },
                    )
                }
            }
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (releasedInputFocus && ev.actionMasked == MotionEvent.ACTION_DOWN) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
            releasedInputFocus = false
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        syncFloatingToggleService()
    }

    override fun onStart() {
        super.onStart()
        if (settings.floatingToggleEnabled.value && Settings.canDrawOverlays(this)) {
            startService(
                floatingToggleServiceIntent(FloatingToggleService.ACTION_APP_VISIBLE),
            )
        }
    }

    private fun restoreGameFocus() {
        currentFocus?.clearFocus()
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        releasedInputFocus = true
        Handler(Looper.getMainLooper()).postDelayed({
            tapTopDisplay()
        }, 120L)
    }

    private fun tapTopDisplay() {
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)
        val x = metrics.widthPixels / 2
        val y = (metrics.heightPixels * 0.08f).toInt().coerceAtLeast(1)

        Thread {
            val commands = listOf(
                listOf("/system/bin/input", "-d", "0", "tap", x.toString(), y.toString()),
                listOf("/system/bin/input", "tap", x.toString(), y.toString()),
            )
            for (command in commands) {
                if (runInputCommand(command)) return@Thread
            }
            Log.w(TAG, "Unable to restore top display focus with input tap")
        }.start()
    }

    private fun runInputCommand(command: List<String>): Boolean {
        return try {
            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()
            val success = process.waitFor() == 0
            success
        } catch (e: Exception) {
            Log.w(TAG, "Input command failed: ${command.joinToString(" ")}", e)
            false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(hideAppReceiver)
        captureManager.release()
        textRecognizer.close()
        dictionary.close()
    }

    private fun syncFloatingToggleService() {
        val serviceIntent = floatingToggleServiceIntent()
        if (settings.floatingToggleEnabled.value && Settings.canDrawOverlays(this)) {
            startService(serviceIntent)
        } else {
            stopService(serviceIntent)
        }
    }

    @Suppress("DEPRECATION")
    private fun floatingToggleServiceIntent(actionName: String? = null): Intent {
        return Intent(this, FloatingToggleService::class.java).apply {
            action = actionName
            putExtra(FloatingToggleService.EXTRA_DISPLAY_ID, windowManager.defaultDisplay.displayId)
        }
    }
}
