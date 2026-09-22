package com.thornotes

import android.content.Context
import android.content.ContextWrapper
import com.thornotes.data.NotebookRepository
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

// Called by DiagnosticsCheck; all files and preferences belong to the test package.
fun checkArchives(context: Context) {
    val key = "archive-check-${UUID.randomUUID()}"
    val root = File(context.cacheDir, key).apply { mkdirs() }
    val isolated = object : ContextWrapper(context) {
        override fun getFilesDir() = root
        override fun getSharedPreferences(name: String, mode: Int) =
            context.getSharedPreferences("$key-$name", mode)
    }
    val metadata = File(root, "notebook/pages.json")
    fun seed(id: String, created: Long) =
        """{"id":"$id","name":"$id","createdAt":$created,"updatedAt":99}"""
    fun zipPage(id: String, archived: Boolean?): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("files/notebook/pages.json"))
            val page = seed(id, 1).dropLast(1) + (archived?.let { ",\"isArchived\":$it" } ?: "") + "}"
            zip.write("[$page]".toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("files/notebook/pages/$id/entries.json"))
            zip.write("[]".toByteArray())
            zip.closeEntry()
        }
        return output.toByteArray()
    }
    try {
        metadata.parentFile!!.mkdirs()
        // Deliberately nonchronological: restoration must preserve stored order.
        metadata.writeText("[${seed("a", 10)},${seed("b", 30)},${seed("c", 20)}]")
        val entries = File(root, "notebook/pages/b/entries.json")
        entries.parentFile!!.mkdirs()
        entries.writeText("""[{"id":"text","pageId":"b","type":"TEXT_CHUNK","createdAt":7,"text":"Existing notes","isStarred":true},{"id":"photo","pageId":"b","type":"SCREENSHOT","createdAt":8,"text":"","imagePath":"pages/b/images/photo.jpg"}]""")
        val photo = File(root, "notebook/pages/b/images/photo.jpg")
        photo.parentFile!!.mkdirs()
        photo.writeBytes(byteArrayOf(1, 2, 3, 4))
        val originalEntries = entries.readBytes()
        val originalPhoto = photo.readBytes()
        var repository = NotebookRepository(isolated)
        check(repository.pages.value.none { it.isArchived })
        repository.selectPage("b")
        val originalEntryValues = repository.entries.value
        val original = repository.pages.value
        repository.setPageArchived("b", true)
        check(repository.currentPageId.value == "a")
        check(repository.pages.value == original.map { if (it.id == "b") it.copy(isArchived = true) else it })
        repository.selectPage("b")
        check(repository.currentPageId.value == "a")
        isolated.getSharedPreferences("notebook", 0).edit().putString("last_page_id", "b").commit()
        repository = NotebookRepository(isolated)
        check(repository.currentPageId.value == "a")
        repository.setPageArchived("b", false)
        check(repository.pages.value == original)
        check(repository.currentPageId.value == "a")
        check(entries.readBytes().contentEquals(originalEntries))
        check(photo.readBytes().contentEquals(originalPhoto))

        // Force the metadata write to fail without damaging the existing metadata.
        val blocker = File(metadata.path + ".new").apply { mkdirs() }
        File(blocker, "block").writeText("block")
        val beforeFailure = metadata.readBytes()
        check(runCatching { repository.setPageArchived("a", true) }.isFailure)
        check(repository.pages.value == original)
        check(metadata.readBytes().contentEquals(beforeFailure))
        blocker.deleteRecursively()

        repository.setPageArchived("b", true)
        repository.setPageArchived("c", true)
        repository.deletePage("a")
        check(repository.pages.value.single { !it.isArchived }.name == "Default")
        val defaultId = repository.currentPageId.value
        repository.setPageArchived(defaultId, true)
        check(repository.currentPageId.value != defaultId)
        check(repository.addTextChunk("New capture").pageId == repository.currentPageId.value)
        val bitmap = android.graphics.Bitmap.createBitmap(1, 1, android.graphics.Bitmap.Config.ARGB_8888)
        try {
            check(repository.addScreenshot(bitmap).pageId == repository.currentPageId.value)
        } finally {
            bitmap.recycle()
        }
        check(entries.readBytes().contentEquals(originalEntries))
        check(photo.readBytes().contentEquals(originalPhoto))

        repository.importBackup(ByteArrayInputStream(zipPage("old-import", null)))
        check(!repository.pages.value.first { it.id == "old-import" }.isArchived)
        repository.importBackup(ByteArrayInputStream(zipPage("archived-import", true)))
        check(repository.pages.value.first { it.id == "archived-import" }.isArchived)
        val expected = repository.pages.value
        val backup = ByteArrayOutputStream()
        repository.exportBackup(backup)
        check(repository.importBackup(ByteArrayInputStream(backup.toByteArray())).pagesImported == 0)
        check(repository.pages.value == expected)
        File(root, "notebook").deleteRecursively()
        isolated.getSharedPreferences("notebook", 0).edit().clear().commit()
        repository = NotebookRepository(isolated)
        repository.importBackup(ByteArrayInputStream(backup.toByteArray()))
        // Import already normalizes entry JSON, so compare metadata and entry values,
        // allowing serialized file sizes to change while photo bytes stay identical.
        check(repository.pages.value.filter { it.id in expected.map { page -> page.id } }
            .map { it.copy(sizeBytes = 0) } == expected.map { it.copy(sizeBytes = 0) })
        repository.setPageArchived("b", false)
        repository.selectPage("b")
        check(repository.entries.value == originalEntryValues)
        check(photo.readBytes().contentEquals(originalPhoto))
    } finally {
        root.deleteRecursively()
        context.deleteSharedPreferences("$key-notebook")
    }
}
