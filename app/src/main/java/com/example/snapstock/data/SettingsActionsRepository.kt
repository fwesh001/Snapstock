package com.example.snapstock.data

import android.content.Context
import android.graphics.BitmapFactory
import androidx.core.content.edit
import java.nio.charset.StandardCharsets
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val SNAPSHOT_DATABASE_NAME = "snapstock_database"
private const val BATCH_IMAGES_DIR = "batch_images"
private const val COLLECTION_IMAGES_DIR = "collection_images"
private const val SETTINGS_PREFS_NAME = "snapstock_prefs"
private const val FIRST_SCAN_TUTORIAL_KEY = "show_first_scan_tutorial"

sealed interface SettingsMaintenanceResult {
    data class Success(val message: String) : SettingsMaintenanceResult
    data class ExportReady(val file: File) : SettingsMaintenanceResult
    data class Error(val message: String) : SettingsMaintenanceResult
}

data class CleanupSummary(
    val deletedFiles: Int,
    val deletedCompletedTodos: Int
)

data class ResetSummary(
    val deletedFiles: Int,
    val deletedItems: Int,
    val deletedTodos: Int
)

data class ExportSummary(
    val file: File,
    val itemCount: Int,
    val imageCount: Int
)

class SettingsActionsRepository(private val context: Context) {
    private val database = AppDatabase.getDatabase(context)
    private val clothingDao = database.clothingItemDao()
    private val todoDao = database.todoEntryDao()
    private val appContext = context.applicationContext

    suspend fun compressStoredImages(quality: Int = 82): SettingsMaintenanceResult = withContext(Dispatchers.IO) {
        val files = listImageFiles()
        var processed = 0
        var failed = 0

        files.forEach { file ->
            val bitmap = BitmapFactory.decodeFile(file.absolutePath)
            if (bitmap == null) {
                failed += 1
                return@forEach
            }

            val tempFile = File(file.parentFile, "${file.name}.tmp")
            val written = runCatching {
                FileOutputStream(tempFile).use { output ->
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, quality, output)
                }
            }.getOrDefault(false)

            if (written && tempFile.exists()) {
                runCatching {
                    if (file.delete()) {
                        tempFile.renameTo(file)
                    } else {
                        tempFile.delete()
                    }
                }
                processed += 1
            } else {
                tempFile.delete()
                failed += 1
            }
        }

        SettingsMaintenanceResult.Success("Compressed $processed images${if (failed > 0) ", $failed failed" else ""}.")
    }

    suspend fun cleanDatabaseAndFiles(): SettingsMaintenanceResult = withContext(Dispatchers.IO) {
        val items = clothingDao.getAllItemsOnce()
        val completedTodos = todoDao.getAllTodosOnce().filter { it.completed }
        val referencedPaths = items.map { it.imagePath }.toSet()
        var deletedFiles = 0

        listImageFiles().forEach { file ->
            if (file.absolutePath !in referencedPaths && file.delete()) {
                deletedFiles += 1
            }
        }

        completedTodos.forEach { todo ->
            todoDao.deleteTodo(todo.id)
        }

        SettingsMaintenanceResult.Success(
            "Removed $deletedFiles orphan files and ${completedTodos.size} completed todos."
        )
    }

    suspend fun runFactoryReset(): SettingsMaintenanceResult = withContext(Dispatchers.IO) {
        val items = clothingDao.getAllItemsOnce()
        val todos = todoDao.getAllTodosOnce()
        val deletedFiles = purgeImageDirectories()

        database.clearAllTables()
        SettingsRepository(context).resetSettings()
        appContext.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
            .edit { clear() }

        SettingsMaintenanceResult.Success(
            "Factory reset complete: ${items.size} items, ${todos.size} todos, $deletedFiles files removed."
        )
    }

    suspend fun exportData(): SettingsMaintenanceResult = withContext(Dispatchers.IO) {
        val items = clothingDao.getAllItemsOnce()
        val exportDir = File(appContext.cacheDir, "exports").apply { mkdirs() }
        val exportFile = File(exportDir, "snapstock_export_${System.currentTimeMillis()}.zip")

        ZipOutputStream(FileOutputStream(exportFile)).use { zip ->
            zip.putNextEntry(ZipEntry("inventory.csv"))
            zip.write(buildCsv(items).toByteArray(StandardCharsets.UTF_8))
            zip.closeEntry()

            val seenImages = linkedSetOf<String>()
            items.forEach { item ->
                val file = File(item.imagePath)
                if (file.exists() && seenImages.add(file.absolutePath)) {
                    zip.putNextEntry(ZipEntry("images/${file.name}"))
                    file.inputStream().use { input ->
                        input.copyTo(zip)
                    }
                    zip.closeEntry()
                }
            }
        }

        SettingsMaintenanceResult.ExportReady(exportFile)
    }

    private fun buildCsv(items: List<ClothingItem>): String {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault())

        return buildString {
            appendLine("ID,Name,Price,Qty,Category,Date")
            items.forEach { item ->
                appendLine(
                    listOf(
                        item.id.toString(),
                        csv(item.name),
                        item.price.toString(),
                        item.quantity.toString(),
                        csv(item.category),
                        csv(formatter.format(Instant.ofEpochMilli(item.dateAdded)))
                    ).joinToString(",")
                )
            }
        }
    }

    private fun csv(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        return if (escaped.any { it == ',' || it == '"' || it.isWhitespace() }) {
            "\"$escaped\""
        } else {
            escaped
        }
    }

    private fun listImageFiles(): List<File> {
        return listOf(
            File(appContext.filesDir, BATCH_IMAGES_DIR),
            File(appContext.filesDir, COLLECTION_IMAGES_DIR)
        ).flatMap { dir ->
            dir.listFiles()?.filter { it.isFile } ?: emptyList()
        }.filter { it.extension.lowercase() in listOf("jpg", "jpeg", "png") }
    }

    private fun purgeImageDirectories(): Int {
        var deleted = 0
        listOf(
            File(appContext.filesDir, BATCH_IMAGES_DIR),
            File(appContext.filesDir, COLLECTION_IMAGES_DIR)
        ).forEach { dir ->
            dir.listFiles()?.forEach { file ->
                if (file.delete()) deleted += 1
            }
        }
        return deleted
    }
}
