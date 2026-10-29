package com.syntaxa.khata.core.util

import android.content.Context
import java.io.File
import java.util.Locale

/**
 * StorageCleanerManager ensures that downloaded update APKs, temporary shared PDF receipts,
 * and report caches never accumulate or waste storage on the merchant's device.
 */
object StorageCleanerManager {

    /**
     * Calculates total bytes consumed by temporary cache directories (updates, receipts, reports).
     */
    fun getCacheSizeBytes(context: Context): Long {
        return getDirectorySize(context.cacheDir)
    }

    /**
     * Formats bytes into human-readable text (e.g. "4.2 MB", "420 KB", "0 KB").
     */
    fun formatSize(bytes: Long): String {
        if (bytes <= 0L) return "0 KB"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        return when {
            mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
            kb >= 1.0 -> String.format(Locale.US, "%.0f KB", kb)
            else -> "$bytes B"
        }
    }

    /**
     * Cleans temporary files within the specified cache directory.
     * 1. Erases all downloaded update APKs from updates/
     * 2. Erases temporary shared PDF receipts from receipts/
     * 3. Erases temporary shared CSV reports from reports/
     * 4. Deletes root cache files
     *
     * Returns total bytes freed.
     */
    fun cleanCacheDirectory(cacheDir: File?): Long {
        if (cacheDir == null || !cacheDir.exists()) return 0L
        val initialSize = getDirectorySize(cacheDir)
        try {
            // 1. Clean downloaded update APKs
            val updatesDir = File(cacheDir, "updates")
            if (updatesDir.exists()) {
                updatesDir.listFiles()?.forEach { it.delete() }
            }

            // 2. Clean temporary PDF receipts
            val receiptsDir = File(cacheDir, "receipts")
            if (receiptsDir.exists()) {
                receiptsDir.listFiles()?.forEach { it.delete() }
            }

            // 3. Clean temporary CSV reports
            val reportsDir = File(cacheDir, "reports")
            if (reportsDir.exists()) {
                reportsDir.listFiles()?.forEach { it.delete() }
            }

            // 4. Clean root cacheDir files
            cacheDir.listFiles()?.forEach { file ->
                if (file.isFile) file.delete()
            }
        } catch (_: Exception) {}

        val freed = initialSize - getDirectorySize(cacheDir)
        return maxOf(0L, freed)
    }

    /**
     * Comprehensive storage cleaner:
     * Cleans internal cacheDir and externalCacheDir (if available).
     * Strictly preserves Room SQLite databases, preferences, and user khata records.
     * Returns total bytes freed.
     */
    fun cleanTemporaryStorage(context: Context): Long {
        var totalFreed = cleanCacheDirectory(context.cacheDir)
        try {
            context.externalCacheDir?.let { extCache ->
                totalFreed += cleanCacheDirectory(extCache)
            }
        } catch (_: Exception) {}
        return totalFreed
    }

    /**
     * Auto-cleaner core logic for a given cache directory:
     * Removes obsolete APK update files and temporary share receipts older than 24 hours.
     */
    fun autoCleanCacheDirectory(cacheDir: File?, currentTimeMillis: Long = System.currentTimeMillis()) {
        if (cacheDir == null || !cacheDir.exists()) return
        try {
            // Residual APK files from past updates are 100% obsolete once app is running
            val updatesDir = File(cacheDir, "updates")
            if (updatesDir.exists()) {
                updatesDir.listFiles()?.forEach { it.delete() }
            }

            // Temporary receipts older than 24h
            val receiptsDir = File(cacheDir, "receipts")
            if (receiptsDir.exists()) {
                val oneDayAgo = currentTimeMillis - (24L * 60L * 60L * 1000L)
                receiptsDir.listFiles()?.forEach { file ->
                    if (file.lastModified() < oneDayAgo) {
                        file.delete()
                    }
                }
            }
        } catch (_: Exception) {}
    }

    /**
     * Background auto-cleaner called on app startup:
     * Removes obsolete APK update files and temporary share receipts older than 24 hours.
     */
    fun autoCleanOnStartup(context: Context) {
        autoCleanCacheDirectory(context.cacheDir)
        try {
            context.externalCacheDir?.let { extCache ->
                autoCleanCacheDirectory(extCache)
            }
        } catch (_: Exception) {}
    }

    fun getDirectorySize(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0L
        var size = 0L
        val files = dir.listFiles() ?: return 0L
        for (file in files) {
            size += if (file.isDirectory) getDirectorySize(file) else file.length()
        }
        return size
    }
}
