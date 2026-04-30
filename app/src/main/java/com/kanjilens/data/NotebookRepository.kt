package com.kanjilens.data

import android.content.Context
import android.graphics.Bitmap
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.kanjilens.data.models.NotebookEntry
import com.kanjilens.data.models.NotebookEntryType
import com.kanjilens.data.models.NotebookPageInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.util.UUID

class NotebookRepository(private val context: Context) {

    private data class StoredPage(
        val id: String,
        val name: String,
        val createdAt: Long,
        val updatedAt: Long,
    )

    private val gson = Gson()
    private val notebookDir = File(context.filesDir, "notebook")
    private val metadataFile = File(notebookDir, "pages.json")
    private val legacyEntriesFile = File(notebookDir, "entries.json")

    private var storedPages: List<StoredPage> = emptyList()
    private var allEntries: List<NotebookEntry> = emptyList()

    private val _pages = MutableStateFlow<List<NotebookPageInfo>>(emptyList())
    val pages: StateFlow<List<NotebookPageInfo>> = _pages

    private val _currentPageId = MutableStateFlow("")
    val currentPageId: StateFlow<String> = _currentPageId

    private val _entries = MutableStateFlow<List<NotebookEntry>>(emptyList())
    val entries: StateFlow<List<NotebookEntry>> = _entries

    init {
        notebookDir.mkdirs()
        storedPages = loadPages()
        if (storedPages.isEmpty()) {
            storedPages = listOf(createStoredPage("Default"))
        }
        importLegacyEntriesIfNeeded()
        _currentPageId.value = storedPages.first().id
        refresh()
    }

    fun selectPage(pageId: String) {
        if (storedPages.any { it.id == pageId }) {
            _currentPageId.value = pageId
            refresh()
        }
    }

    fun createPage(name: String): String {
        val cleanName = name.trim().ifBlank { "Untitled" }
        val page = createStoredPage(cleanName)
        storedPages = listOf(page) + storedPages
        _currentPageId.value = page.id
        savePages()
        refresh()
        return page.id
    }

    fun deletePage(pageId: String) {
        val page = pageDir(pageId)
        if (page.exists()) page.deleteRecursively()
        storedPages = storedPages.filterNot { it.id == pageId }
        if (storedPages.isEmpty()) {
            storedPages = listOf(createStoredPage("Default"))
        }
        if (_currentPageId.value == pageId) {
            _currentPageId.value = storedPages.first().id
        }
        savePages()
        refresh()
    }

    fun addScreenshot(bitmap: Bitmap): NotebookEntry {
        val pageId = ensureCurrentPageId()
        val id = UUID.randomUUID().toString()
        val imageDir = File(pageDir(pageId), "images").apply { mkdirs() }
        val imageFile = File(imageDir, "$id.jpg")
        imageFile.outputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, output)
        }
        return addEntry(
            NotebookEntry(
                id = id,
                pageId = pageId,
                type = NotebookEntryType.SCREENSHOT,
                createdAt = System.currentTimeMillis(),
                imagePath = imageFile.absolutePath,
            )
        )
    }

    fun addOcrText(text: String): NotebookEntry {
        val pageId = ensureCurrentPageId()
        return addEntry(
            NotebookEntry(
                id = UUID.randomUUID().toString(),
                pageId = pageId,
                type = NotebookEntryType.OCR_TEXT,
                createdAt = System.currentTimeMillis(),
                text = text,
            )
        )
    }

    fun updateText(id: String, text: String) {
        allEntries = allEntries.map { entry ->
            if (entry.id == id) entry.copy(text = text) else entry
        }
        saveCurrentEntries()
        touchCurrentPage()
        refresh()
    }

    fun deleteEntry(id: String) {
        val entry = allEntries.firstOrNull { it.id == id } ?: return
        entry.imagePath?.let { path ->
            val imageFile = File(path)
            if (imageFile.exists()) imageFile.delete()
        }
        allEntries = allEntries.filterNot { it.id == id }
        saveCurrentEntries()
        touchCurrentPage()
        refresh()
    }

    private fun addEntry(entry: NotebookEntry): NotebookEntry {
        allEntries = listOf(entry) + allEntries
        saveCurrentEntries()
        touchCurrentPage()
        refresh()
        return entry
    }

    private fun refresh() {
        val currentPageId = ensureCurrentPageId()
        allEntries = loadEntries(currentPageId)
        _entries.value = allEntries
        _pages.value = storedPages.map { page ->
            val entries = loadEntries(page.id)
            NotebookPageInfo(
                id = page.id,
                name = page.name,
                createdAt = page.createdAt,
                updatedAt = page.updatedAt,
                sizeBytes = directorySize(pageDir(page.id)),
                entryCount = entries.size,
            )
        }
    }

    private fun ensureCurrentPageId(): String {
        if (_currentPageId.value.isBlank() || storedPages.none { it.id == _currentPageId.value }) {
            _currentPageId.value = storedPages.first().id
        }
        return _currentPageId.value
    }

    private fun createStoredPage(name: String): StoredPage {
        val now = System.currentTimeMillis()
        val page = StoredPage(
            id = UUID.randomUUID().toString(),
            name = name,
            createdAt = now,
            updatedAt = now,
        )
        pageDir(page.id).mkdirs()
        return page
    }

    private fun touchCurrentPage() {
        val now = System.currentTimeMillis()
        val currentId = ensureCurrentPageId()
        storedPages = storedPages.map { page ->
            if (page.id == currentId) page.copy(updatedAt = now) else page
        }
        savePages()
    }

    private fun loadPages(): List<StoredPage> {
        if (!metadataFile.exists()) return emptyList()
        return try {
            val type = object : TypeToken<List<StoredPage>>() {}.type
            gson.fromJson(metadataFile.readText(), type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun savePages() {
        metadataFile.writeText(gson.toJson(storedPages))
    }

    private fun loadEntries(pageId: String): List<NotebookEntry> {
        val file = entriesFile(pageId)
        if (!file.exists()) return emptyList()
        return try {
            val type = object : TypeToken<List<NotebookEntry>>() {}.type
            val entries: List<NotebookEntry> = gson.fromJson(file.readText(), type) ?: emptyList()
            entries.map { entry ->
                if (entry.pageId.isBlank()) entry.copy(pageId = pageId) else entry
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveCurrentEntries() {
        val pageId = ensureCurrentPageId()
        entriesFile(pageId).writeText(gson.toJson(allEntries))
    }

    private fun importLegacyEntriesIfNeeded() {
        if (!legacyEntriesFile.exists()) return
        val pageId = storedPages.first().id
        val targetFile = entriesFile(pageId)
        if (targetFile.exists()) {
            legacyEntriesFile.delete()
            return
        }
        try {
            val type = object : TypeToken<List<NotebookEntry>>() {}.type
            val legacyEntries: List<NotebookEntry> =
                gson.fromJson(legacyEntriesFile.readText(), type) ?: emptyList()
            targetFile.writeText(gson.toJson(legacyEntries.map { it.copy(pageId = pageId) }))
            legacyEntriesFile.delete()
        } catch (_: Exception) {
            legacyEntriesFile.delete()
        }
    }

    private fun pageDir(pageId: String): File = File(notebookDir, "pages/$pageId")

    private fun entriesFile(pageId: String): File {
        val dir = pageDir(pageId).apply { mkdirs() }
        return File(dir, "entries.json")
    }

    private fun directorySize(file: File): Long {
        if (!file.exists()) return 0L
        if (file.isFile) return file.length()
        return file.listFiles()?.sumOf { directorySize(it) } ?: 0L
    }
}
