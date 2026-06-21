package com.thornotes.data

import android.content.Context
import android.graphics.Bitmap
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.thornotes.data.models.NotebookEntry
import com.thornotes.data.models.NotebookEntryType
import com.thornotes.data.models.NotebookPageInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class NotebookRepository(private val context: Context) {

    private data class StoredPage(
        val id: String,
        val name: String,
        val createdAt: Long,
        val updatedAt: Long,
    )

    private data class ImportedStoredPage(
        val id: String?,
        val name: String?,
        val createdAt: Long?,
        val updatedAt: Long?,
    )

    private data class ImportedNotebookEntry(
        val id: String?,
        val pageId: String?,
        val type: String?,
        val createdAt: Long?,
        val text: String?,
        val imagePath: String?,
        val isStarred: Boolean?,
        val isPinned: Boolean?,
    )

    private val gson = Gson()
    private val notebookDir = File(context.filesDir, "notebook")
    private val metadataFile = File(notebookDir, "pages.json")
    private val legacyEntriesFile = File(notebookDir, "entries.json")
    private val preferences = context.getSharedPreferences("notebook", Context.MODE_PRIVATE)

    private var storedPages: List<StoredPage> = emptyList()
    private var allEntries: List<NotebookEntry> = emptyList()

    data class BackupImportResult(
        val pagesImported: Int,
        val pagesSkipped: Int,
    )

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
        val lastPageId = preferences.getString(KEY_LAST_PAGE_ID, null)
        _currentPageId.value = storedPages.firstOrNull { it.id == lastPageId }?.id ?: storedPages.first().id
        refresh()
    }

    @Synchronized
    fun selectPage(pageId: String) {
        if (storedPages.any { it.id == pageId }) {
            _currentPageId.value = pageId
            saveLastPageId(pageId)
            refresh()
        }
    }

    @Synchronized
    fun createPage(name: String): String {
        val cleanName = name.trim().ifBlank { "Untitled" }
        val page = createStoredPage(cleanName)
        storedPages = listOf(page) + storedPages
        _currentPageId.value = page.id
        saveLastPageId(page.id)
        savePages()
        refresh()
        return page.id
    }

    @Synchronized
    fun renamePage(pageId: String, name: String) {
        val cleanName = name.trim().ifBlank { "Untitled" }
        val now = System.currentTimeMillis()
        var renamed = false
        storedPages = storedPages.map { page ->
            if (page.id == pageId) {
                renamed = true
                page.copy(name = cleanName, updatedAt = now)
            } else {
                page
            }
        }
        if (renamed) {
            savePages()
            refresh()
        }
    }

    @Synchronized
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
        saveLastPageId(_currentPageId.value)
        savePages()
        refresh()
    }

    @Synchronized
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
                imagePath = imageFile.toNotebookRelativePath(),
            )
        )
    }

    fun resolveImagePath(imagePath: String?): String? {
        return imagePath?.let { imageFile(it).absolutePath }
    }

    @Synchronized
    fun exportBackup(output: OutputStream) {
        ZipOutputStream(BufferedOutputStream(output)).use { zip ->
            zip.addDirectory("files/")
            zip.addFileTree(notebookDir, "files/notebook")

            val sharedPrefsDir = File(context.applicationInfo.dataDir, "shared_prefs")
            if (sharedPrefsDir.exists()) {
                zip.addFileTree(sharedPrefsDir, "shared_prefs")
            }
        }
    }

    @Synchronized
    fun importBackup(input: InputStream): BackupImportResult {
        val tempDir = File(context.cacheDir, "notebook-import-${UUID.randomUUID()}")
        val importedPageIds = mutableListOf<String>()
        return try {
            tempDir.mkdirs()
            extractZip(input, tempDir)

            val backupNotebookDir = listOf(
                File(tempDir, "files/notebook"),
                File(tempDir, "notebook"),
            ).firstOrNull { File(it, "pages.json").exists() }
                ?: throw IllegalArgumentException("No files/notebook/pages.json found in backup.")

            val type = object : TypeToken<List<ImportedStoredPage>>() {}.type
            val importedPages: List<ImportedStoredPage> =
                gson.fromJson(File(backupNotebookDir, "pages.json").readText(), type) ?: emptyList()
            val backupPages = importedPages.mapNotNull { page ->
                val id = page.id?.takeIf { it.isNotBlank() }
                val name = page.name?.takeIf { it.isNotBlank() }
                if (id == null || name == null) {
                    null
                } else {
                    StoredPage(
                        id = id,
                        name = name,
                        createdAt = page.createdAt ?: 0L,
                        updatedAt = page.updatedAt ?: page.createdAt ?: 0L,
                    )
                }
            }

            var imported = 0
            var skipped = 0
            val usedIds = storedPages.map { it.id }.toMutableSet()
            val mergedPages = storedPages.toMutableList()

            for (page in backupPages) {
                val sourcePageDir = File(backupNotebookDir, "pages/${page.id}")
                if (!sourcePageDir.exists()) {
                    skipped += 1
                    continue
                }

                if (usedIds.contains(page.id) || pageDir(page.id).exists()) {
                    skipped += 1
                    continue
                }

                val targetPageId = page.id
                val targetPageDir = pageDir(targetPageId)
                copyDirectory(sourcePageDir, targetPageDir)
                importedPageIds += targetPageId
                normalizeImportedEntries(targetPageDir, page.id, targetPageId)

                mergedPages += page
                usedIds += targetPageId
                imported += 1
            }

            storedPages = mergedPages
            savePages()
            refresh()
            BackupImportResult(pagesImported = imported, pagesSkipped = skipped)
        } catch (exception: Exception) {
            importedPageIds.forEach { pageDir(it).deleteRecursively() }
            throw exception
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Synchronized
    fun addTextChunk(text: String): NotebookEntry {
        val pageId = ensureCurrentPageId()
        return addEntry(
            NotebookEntry(
                id = UUID.randomUUID().toString(),
                pageId = pageId,
                type = NotebookEntryType.TEXT_CHUNK,
                createdAt = System.currentTimeMillis(),
                text = text,
            )
        )
    }

    @Synchronized
    fun updateText(id: String, text: String) {
        allEntries = allEntries.map { entry ->
            if (entry.id == id) entry.copy(text = text) else entry
        }
        saveCurrentEntries()
        touchCurrentPage()
        refresh()
    }

    @Synchronized
    fun toggleStar(id: String) {
        var changed = false
        allEntries = allEntries.map { entry ->
            if (entry.id == id) {
                changed = true
                entry.copy(isStarred = !entry.isStarred)
            } else {
                entry
            }
        }
        if (changed) {
            saveCurrentEntries()
            touchCurrentPage()
            refresh()
        }
    }

    @Synchronized
    fun togglePin(id: String) {
        var changed = false
        allEntries = allEntries.map { entry ->
            if (entry.id == id) {
                changed = true
                entry.copy(isPinned = !entry.isPinned)
            } else {
                entry
            }
        }
        if (changed) {
            saveCurrentEntries()
            touchCurrentPage()
            refresh()
        }
    }

    @Synchronized
    fun deleteEntry(id: String) {
        val entry = allEntries.firstOrNull { it.id == id } ?: return
        entry.imagePath?.let { path ->
            val imageFile = imageFile(path)
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
            saveLastPageId(_currentPageId.value)
        }
        return _currentPageId.value
    }

    private fun saveLastPageId(pageId: String) {
        preferences.edit().putString(KEY_LAST_PAGE_ID, pageId).apply()
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
        atomicWriteText(metadataFile, gson.toJson(storedPages))
    }

    private fun loadEntries(pageId: String): List<NotebookEntry> {
        val file = entriesFile(pageId)
        if (!file.exists()) return emptyList()
        return try {
            val type = object : TypeToken<List<NotebookEntry>>() {}.type
            val entries: List<NotebookEntry> = gson.fromJson(file.readText(), type) ?: emptyList()
            entries.map { entry ->
                val withPage = if (entry.pageId.isBlank()) entry.copy(pageId = pageId) else entry
                withPage.copy(imagePath = withPage.imagePath?.toStoredImagePath())
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

    private fun imageFile(path: String): File {
        val file = File(path)
        return if (file.isAbsolute) file else File(notebookDir, path)
    }

    private fun String.toStoredImagePath(): String {
        val file = File(this)
        return if (file.isAbsolute) file.toNotebookRelativePath() else this
    }

    private fun File.toNotebookRelativePath(): String {
        val basePath = notebookDir.absolutePath.trimEnd(File.separatorChar) + File.separator
        return if (absolutePath.startsWith(basePath)) {
            absolutePath.removePrefix(basePath)
        } else {
            absolutePath
        }
    }

    private fun entriesFile(pageId: String): File {
        val dir = pageDir(pageId).apply { mkdirs() }
        return File(dir, "entries.json")
    }

    private fun copyDirectory(source: File, target: File) {
        if (source.isDirectory) {
            target.mkdirs()
            source.listFiles()?.forEach { child ->
                copyDirectory(child, File(target, child.name))
            }
        } else {
            target.parentFile?.mkdirs()
            source.copyTo(target, overwrite = false)
        }
    }

    private fun normalizeImportedEntries(pageDir: File, oldPageId: String, newPageId: String) {
        val file = File(pageDir, "entries.json")
        if (!file.exists()) return
        val type = object : TypeToken<List<ImportedNotebookEntry>>() {}.type
        val entries: List<ImportedNotebookEntry> = gson.fromJson(file.readText(), type) ?: emptyList()
        val updated = entries.mapNotNull { entry ->
            val id = entry.id?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val imagePath = if (oldPageId == newPageId) {
                entry.imagePath
            } else {
                entry.imagePath?.replace(
                    oldValue = "pages/$oldPageId/",
                    newValue = "pages/$newPageId/",
                    ignoreCase = false,
                )
            }
            val entryType = when (entry.type) {
                NotebookEntryType.SCREENSHOT.name -> NotebookEntryType.SCREENSHOT
                NotebookEntryType.TEXT_CHUNK.name, "OCR_TEXT" -> NotebookEntryType.TEXT_CHUNK
                else -> if (imagePath.isNullOrBlank()) {
                    NotebookEntryType.TEXT_CHUNK
                } else {
                    NotebookEntryType.SCREENSHOT
                }
            }

            NotebookEntry(
                id = id,
                pageId = newPageId,
                type = entryType,
                createdAt = entry.createdAt ?: 0L,
                text = entry.text.orEmpty(),
                imagePath = imagePath,
                isStarred = entry.isStarred ?: false,
                isPinned = entry.isPinned ?: false,
            )
        }
        atomicWriteText(file, gson.toJson(updated))
    }

    private fun atomicWriteText(file: File, text: String) {
        file.parentFile?.mkdirs()
        val tempFile = File(file.parentFile, "${file.name}.tmp-${UUID.randomUUID()}")
        FileOutputStream(tempFile).use { output ->
            output.write(text.encodeToByteArray())
            output.fd.sync()
        }
        if (!tempFile.renameTo(file)) {
            tempFile.copyTo(file, overwrite = true)
            tempFile.delete()
        }
    }

    private fun directorySize(file: File): Long {
        if (!file.exists()) return 0L
        if (file.isFile) return file.length()
        return file.listFiles()?.sumOf { directorySize(it) } ?: 0L
    }

    private fun ZipOutputStream.addFileTree(file: File, zipPath: String) {
        val cleanPath = zipPath.trim('/')
        if (file.isDirectory) {
            addDirectory("$cleanPath/")
            file.listFiles()
                ?.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name })
                ?.forEach { child ->
                    addFileTree(child, "$cleanPath/${child.name}")
                }
        } else if (file.isFile) {
            putNextEntry(ZipEntry(cleanPath))
            file.inputStream().use { input ->
                input.copyTo(this)
            }
            closeEntry()
        }
    }

    private fun ZipOutputStream.addDirectory(zipPath: String) {
        val cleanPath = zipPath.trim('/').let { "$it/" }
        putNextEntry(ZipEntry(cleanPath))
        closeEntry()
    }

    private fun extractZip(input: InputStream, destination: File) {
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val entryName = entry.name
                    ?: throw IllegalArgumentException("Zip entry has no path.")
                val target = safeZipTarget(destination, entryName)
                if (entry.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile?.mkdirs()
                    target.outputStream().use { output ->
                        zip.copyTo(output)
                    }
                }
                zip.closeEntry()
            }
        }
    }

    private fun safeZipTarget(destination: File, path: String): File {
        require(path.isNotBlank() && !path.startsWith("/") && !path.contains("..")) {
            "Unsafe zip path: $path"
        }
        val target = File(destination, path).canonicalFile
        val base = destination.canonicalFile
        require(target.path == base.path || target.path.startsWith(base.path + File.separator)) {
            "Zip path escapes destination: $path"
        }
        return target
    }

    private companion object {
        const val KEY_LAST_PAGE_ID = "last_page_id"
    }
}
