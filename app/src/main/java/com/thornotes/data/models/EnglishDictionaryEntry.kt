package com.thornotes.data.models

data class EnglishDictionaryEntry(
    val word: String,
    val partOfSpeech: String,
    val definition: String,
    val examples: List<String>,
    val synonyms: List<String>,
)
