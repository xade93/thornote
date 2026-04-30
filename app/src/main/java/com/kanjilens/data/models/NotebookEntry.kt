package com.kanjilens.data.models

data class NotebookEntry(
    val id: String,
    val pageId: String = "",
    val type: NotebookEntryType,
    val createdAt: Long,
    val text: String = "",
    val imagePath: String? = null,
)

data class NotebookPageInfo(
    val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val sizeBytes: Long = 0L,
    val entryCount: Int = 0,
)

enum class NotebookEntryType {
    SCREENSHOT,
    OCR_TEXT,
}
