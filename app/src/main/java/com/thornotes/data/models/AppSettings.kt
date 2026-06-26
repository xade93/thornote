package com.thornotes.data.models

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class AppSettings(context: Context) {

    companion object {
        private const val PREFS_NAME = "thornotes_prefs"
        private const val KEY_TEXT_SIZE = "text_size"
        private const val KEY_CROP_LEFT = "crop_left"
        private const val KEY_CROP_TOP = "crop_top"
        private const val KEY_CROP_RIGHT = "crop_right"
        private const val KEY_CROP_BOTTOM = "crop_bottom"
        private const val KEY_CROP_ENABLED = "crop_enabled"
        private const val KEY_OCR_LANGUAGE = "ocr_language"
        private const val KEY_FLOATING_TOGGLE_ENABLED = "floating_toggle_enabled"
        private const val KEY_WELCOME_SEEN = "welcome_seen"
        private const val KEY_CAPTURE_DEBUG_LOG_ENABLED = "capture_debug_log_enabled"
        private const val KEY_THEME_COLOR = "theme_color"

        const val TEXT_SIZE_SMALL = 0
        const val TEXT_SIZE_MEDIUM = 1
        const val TEXT_SIZE_LARGE = 2

        const val OCR_LANGUAGE_CHINESE_ENGLISH = 0
        const val OCR_LANGUAGE_ENGLISH = 1
        const val OCR_LANGUAGE_CHINESE = 2
        const val OCR_LANGUAGE_JAPANESE = 3
        const val OCR_LANGUAGE_ALL = 4

        const val THEME_COLOR_PINK = 0
        const val THEME_COLOR_AMBER = 1
        const val THEME_COLOR_TEAL = 2
        const val THEME_COLOR_VIOLET = 3
        const val THEME_COLOR_RED = 4
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _textSize = MutableStateFlow(prefs.getInt(KEY_TEXT_SIZE, TEXT_SIZE_MEDIUM))
    val textSize: StateFlow<Int> = _textSize

    private val _cropEnabled = MutableStateFlow(prefs.getBoolean(KEY_CROP_ENABLED, false))
    val cropEnabled: StateFlow<Boolean> = _cropEnabled

    private val _ocrLanguage = MutableStateFlow(
        prefs.getInt(KEY_OCR_LANGUAGE, OCR_LANGUAGE_CHINESE_ENGLISH)
    )
    val ocrLanguage: StateFlow<Int> = _ocrLanguage

    private val _floatingToggleEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_FLOATING_TOGGLE_ENABLED, false)
    )
    val floatingToggleEnabled: StateFlow<Boolean> = _floatingToggleEnabled

    private val _welcomeSeen = MutableStateFlow(
        prefs.getBoolean(KEY_WELCOME_SEEN, false)
    )
    val welcomeSeen: StateFlow<Boolean> = _welcomeSeen

    private val _captureDebugLogEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_CAPTURE_DEBUG_LOG_ENABLED, false)
    )
    val captureDebugLogEnabled: StateFlow<Boolean> = _captureDebugLogEnabled

    private val _themeColor = MutableStateFlow(
        prefs.getInt(KEY_THEME_COLOR, THEME_COLOR_PINK)
    )
    val themeColor: StateFlow<Int> = _themeColor

    private val _cropLeft = MutableStateFlow(prefs.getFloat(KEY_CROP_LEFT, 0f))
    private val _cropTop = MutableStateFlow(prefs.getFloat(KEY_CROP_TOP, 0f))
    private val _cropRight = MutableStateFlow(prefs.getFloat(KEY_CROP_RIGHT, 1f))
    private val _cropBottom = MutableStateFlow(prefs.getFloat(KEY_CROP_BOTTOM, 1f))

    data class CropRegion(val left: Float, val top: Float, val right: Float, val bottom: Float)

    val cropRegion: CropRegion
        get() = CropRegion(_cropLeft.value, _cropTop.value, _cropRight.value, _cropBottom.value)

    fun setTextSize(size: Int) {
        _textSize.value = size
        prefs.edit().putInt(KEY_TEXT_SIZE, size).apply()
    }

    fun setOcrLanguage(language: Int) {
        _ocrLanguage.value = language
        prefs.edit().putInt(KEY_OCR_LANGUAGE, language).apply()
    }

    fun setCropRegion(left: Float, top: Float, right: Float, bottom: Float) {
        _cropEnabled.value = true
        _cropLeft.value = left
        _cropTop.value = top
        _cropRight.value = right
        _cropBottom.value = bottom
        prefs.edit()
            .putBoolean(KEY_CROP_ENABLED, true)
            .putFloat(KEY_CROP_LEFT, left)
            .putFloat(KEY_CROP_TOP, top)
            .putFloat(KEY_CROP_RIGHT, right)
            .putFloat(KEY_CROP_BOTTOM, bottom)
            .apply()
    }

    fun clearCropRegion() {
        _cropEnabled.value = false
        prefs.edit().putBoolean(KEY_CROP_ENABLED, false).apply()
    }

    fun setFloatingToggleEnabled(enabled: Boolean) {
        _floatingToggleEnabled.value = enabled
        prefs.edit().putBoolean(KEY_FLOATING_TOGGLE_ENABLED, enabled).apply()
    }

    fun setCaptureDebugLogEnabled(enabled: Boolean) {
        _captureDebugLogEnabled.value = enabled
        prefs.edit().putBoolean(KEY_CAPTURE_DEBUG_LOG_ENABLED, enabled).apply()
    }

    fun setThemeColor(themeColor: Int) {
        _themeColor.value = themeColor
        prefs.edit().putInt(KEY_THEME_COLOR, themeColor).apply()
    }

    fun markWelcomeSeen() {
        _welcomeSeen.value = true
        prefs.edit().putBoolean(KEY_WELCOME_SEEN, true).apply()
    }

    fun resetWelcomeSeen() {
        _welcomeSeen.value = false
        prefs.edit().putBoolean(KEY_WELCOME_SEEN, false).apply()
    }
}
