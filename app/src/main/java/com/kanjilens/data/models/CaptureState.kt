package com.kanjilens.data.models

data class WordEntry(
    val surface: String,
    val reading: String,
    val meaning: String,
    val jlptLevel: String?,
)

data class AnalysisResult(
    val originalText: String,
    val words: List<WordEntry>,
)

sealed class CaptureState {
    data object Idle : CaptureState()
    data object Capturing : CaptureState()
    data object Processing : CaptureState()
    data class DictionarySuccess(val result: AnalysisResult) : CaptureState()
    data class Error(val message: String) : CaptureState()
}
