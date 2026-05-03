package com.thornotes.ocr

import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer as MlKitTextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.thornotes.data.models.AppSettings
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class TextRecognizer {

    companion object {
        private const val TAG = "ThorNotes"
    }

    private val latinRecognizer = TextRecognition.getClient(
        TextRecognizerOptions.DEFAULT_OPTIONS
    )
    private val chineseRecognizer = lazy {
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    }
    private val japaneseRecognizer = lazy {
        TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
    }

    suspend fun recognizeText(bitmap: Bitmap, language: Int): String? {
        val recognizers = when (language) {
            AppSettings.OCR_LANGUAGE_ENGLISH -> listOf(latinRecognizer)
            AppSettings.OCR_LANGUAGE_CHINESE -> listOf(chineseRecognizer.value)
            AppSettings.OCR_LANGUAGE_JAPANESE -> listOf(japaneseRecognizer.value)
            AppSettings.OCR_LANGUAGE_ALL -> listOf(chineseRecognizer.value, latinRecognizer, japaneseRecognizer.value)
            else -> listOf(chineseRecognizer.value, latinRecognizer)
        }
        val results = recognizers.mapNotNull { recognizer ->
            recognizeWith(recognizer, bitmap)
        }
        return mergeResults(results)
    }

    private suspend fun recognizeWith(recognizer: MlKitTextRecognizer, bitmap: Bitmap): String? = suspendCancellableCoroutine { continuation ->
        val image = InputImage.fromBitmap(bitmap, 0)

        Log.d(TAG, "OCR: Starting text recognition on ${bitmap.width}x${bitmap.height} image")

        recognizer.process(image)
            .addOnSuccessListener { result ->
                val text = result.text.trim()
                Log.d(TAG, "OCR: Recognized ${result.textBlocks.size} blocks, text length=${text.length}")
                if (text.isNotEmpty()) {
                    Log.d(TAG, "OCR: Text = $text")
                    continuation.resume(text)
                } else {
                    Log.d(TAG, "OCR: No text found")
                    continuation.resume(null)
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "OCR: Recognition failed", e)
                continuation.resume(null)
            }
    }

    suspend fun recognizeTextBlocks(bitmap: Bitmap): List<String>? = suspendCancellableCoroutine { continuation ->
        val image = InputImage.fromBitmap(bitmap, 0)

        latinRecognizer.process(image)
            .addOnSuccessListener { result ->
                val blocks = result.textBlocks
                    .map { it.text.trim() }
                    .filter { it.isNotEmpty() }
                if (blocks.isNotEmpty()) {
                    continuation.resume(blocks)
                } else {
                    continuation.resume(null)
                }
            }
            .addOnFailureListener {
                continuation.resume(null)
            }
    }

    private fun mergeResults(results: List<String>): String? {
        if (results.isEmpty()) return null
        val seen = linkedSetOf<String>()
        results.forEach { result ->
            result.lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .forEach { seen.add(it) }
        }
        return seen.joinToString("\n").ifBlank { null }
    }

    fun close() {
        latinRecognizer.close()
        if (chineseRecognizer.isInitialized()) chineseRecognizer.value.close()
        if (japaneseRecognizer.isInitialized()) japaneseRecognizer.value.close()
    }
}
