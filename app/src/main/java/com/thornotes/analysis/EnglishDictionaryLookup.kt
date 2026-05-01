package com.thornotes.analysis

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.thornotes.data.models.EnglishDictionaryEntry
import java.io.File

class EnglishDictionaryLookup(private val context: Context) {

    companion object {
        private const val ASSET_NAME = "english_dictionary.db"
        private const val DB_NAME = "english_dictionary.db"
        private const val MAX_RESULTS = 24
    }

    private val gson = Gson()
    private val listType = object : TypeToken<List<String>>() {}.type
    private val db: SQLiteDatabase by lazy {
        SQLiteDatabase.openDatabase(ensureDatabase(), null, SQLiteDatabase.OPEN_READONLY)
    }

    fun lookup(query: String): List<EnglishDictionaryEntry> {
        val candidates = candidateKeys(query)
        val results = mutableListOf<EnglishDictionaryEntry>()
        val seen = mutableSetOf<String>()

        for (candidate in candidates) {
            queryEntries(candidate).forEach { entry ->
                val key = "${entry.word}|${entry.partOfSpeech}|${entry.definition}"
                if (seen.add(key)) results.add(entry)
            }
            if (results.isNotEmpty()) break
        }

        return results.take(MAX_RESULTS)
    }

    fun close() {
        if (db.isOpen) db.close()
    }

    private fun queryEntries(lemmaKey: String): List<EnglishDictionaryEntry> {
        val entries = mutableListOf<EnglishDictionaryEntry>()
        db.rawQuery(
            """
            SELECT display_word, pos, definition, examples_json, synonyms_json
            FROM entries
            WHERE lemma_key = ?
            ORDER BY
                CASE pos
                    WHEN 'noun' THEN 0
                    WHEN 'verb' THEN 1
                    WHEN 'adjective' THEN 2
                    WHEN 'adverb' THEN 3
                    ELSE 4
                END,
                id
            LIMIT ?
            """.trimIndent(),
            arrayOf(lemmaKey, MAX_RESULTS.toString()),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                entries.add(
                    EnglishDictionaryEntry(
                        word = cursor.getString(0),
                        partOfSpeech = cursor.getString(1),
                        definition = cursor.getString(2),
                        examples = parseList(cursor.getString(3)),
                        synonyms = parseList(cursor.getString(4)).filterNot {
                            normalize(it) == lemmaKey
                        },
                    )
                )
            }
        }
        return entries
    }

    private fun candidateKeys(query: String): List<String> {
        val key = normalize(query)
        if (key.isBlank()) return emptyList()

        val candidates = linkedSetOf(key)
        queryExceptions(key).forEach { candidates.add(it) }

        if (key.endsWith("ies") && key.length > 3) {
            candidates.add(key.dropLast(3) + "y")
        }
        if (key.endsWith("ied") && key.length > 3) {
            candidates.add(key.dropLast(3) + "y")
        }
        if (key.endsWith("ing") && key.length > 4) {
            val stem = key.dropLast(3)
            candidates.add(stem)
            candidates.add(stem + "e")
            if (stem.length > 1 && stem.last() == stem[stem.length - 2]) {
                candidates.add(stem.dropLast(1))
            }
        }
        if (key.endsWith("ed") && key.length > 3) {
            val stem = key.dropLast(2)
            candidates.add(stem)
            candidates.add(stem + "e")
            if (stem.length > 1 && stem.last() == stem[stem.length - 2]) {
                candidates.add(stem.dropLast(1))
            }
        }
        if (key.endsWith("es") && key.length > 3) {
            candidates.add(key.dropLast(2))
        }
        if (key.endsWith("s") && key.length > 2) {
            candidates.add(key.dropLast(1))
        }

        return candidates.toList()
    }

    private fun queryExceptions(key: String): List<String> {
        val results = mutableListOf<String>()
        db.rawQuery(
            "SELECT lemma_key FROM exceptions WHERE inflected = ?",
            arrayOf(key),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                results.add(cursor.getString(0))
            }
        }
        return results
    }

    private fun parseList(value: String): List<String> {
        return try {
            gson.fromJson(value, listType) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun normalize(value: String): String =
        value.trim().lowercase().replace('_', ' ').replace(Regex("\\s+"), " ")

    private fun ensureDatabase(): String {
        val target = File(context.filesDir, DB_NAME)
        val assetSize = context.assets.open(ASSET_NAME).use { it.available() }
        if (!target.exists() || target.length() != assetSize.toLong()) {
            context.assets.open(ASSET_NAME).use { input ->
                target.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
        return target.absolutePath
    }
}
