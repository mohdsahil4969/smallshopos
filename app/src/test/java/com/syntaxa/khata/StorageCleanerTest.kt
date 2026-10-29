package com.syntaxa.khata

import com.syntaxa.khata.core.util.StorageCleanerManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class StorageCleanerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testFormatSize_returnsCorrectUnits() {
        assertEquals("0 KB", StorageCleanerManager.formatSize(0L))
        assertEquals("0 KB", StorageCleanerManager.formatSize(-10L))
        assertEquals("500 B", StorageCleanerManager.formatSize(500L))
        assertEquals("2 KB", StorageCleanerManager.formatSize(2048L))
        assertEquals("4.0 MB", StorageCleanerManager.formatSize(4 * 1024 * 1024L))
        assertEquals("4.2 MB", StorageCleanerManager.formatSize((4.2 * 1024 * 1024).toLong()))
    }

    @Test
    fun testCleanCacheDirectory_deletesTemporaryFilesAndPreservesOtherDirectories() {
        val mockCacheDir = tempFolder.newFolder("cache")
        val mockDataDir = tempFolder.newFolder("databases")

        // Populate mock cache directories
        val updatesDir = File(mockCacheDir, "updates").apply { mkdirs() }
        val apkFile = File(updatesDir, "SyntaxaKhata_2.0.1.apk").apply {
            writeBytes(ByteArray(1024 * 100)) // 100 KB
        }

        val receiptsDir = File(mockCacheDir, "receipts").apply { mkdirs() }
        val receiptPdf = File(receiptsDir, "receipt_101.pdf").apply {
            writeBytes(ByteArray(1024 * 50)) // 50 KB
        }

        val reportsDir = File(mockCacheDir, "reports").apply { mkdirs() }
        val reportCsv = File(reportsDir, "monthly_report.csv").apply {
            writeBytes(ByteArray(1024 * 20)) // 20 KB
        }

        val rootTempFile = File(mockCacheDir, "temp_render.tmp").apply {
            writeBytes(ByteArray(1024 * 10)) // 10 KB
        }

        // Mock database file that must NEVER be touched
        val dbFile = File(mockDataDir, "syntaxa_khata.db").apply {
            writeText("IMPORTANT_CUSTOMER_KHATA_DATA")
        }

        val totalCacheBefore = StorageCleanerManager.getDirectorySize(mockCacheDir)
        assertTrue(totalCacheBefore >= 180 * 1024L)

        // Execute cleanup
        val freedBytes = StorageCleanerManager.cleanCacheDirectory(mockCacheDir)

        // Verify temporary cache files are gone
        assertFalse("Update APK must be deleted", apkFile.exists())
        assertFalse("Temporary receipt PDF must be deleted", receiptPdf.exists())
        assertFalse("Temporary CSV report must be deleted", reportCsv.exists())
        assertFalse("Root temp file must be deleted", rootTempFile.exists())

        // Verify freed bytes matches
        assertEquals(totalCacheBefore, freedBytes)
        assertEquals(0L, StorageCleanerManager.getDirectorySize(mockCacheDir))

        // Verify customer khata database is completely safe and intact
        assertTrue("Customer database file must remain untouched", dbFile.exists())
        assertEquals("IMPORTANT_CUSTOMER_KHATA_DATA", dbFile.readText())
    }

    @Test
    fun testAutoCleanCacheDirectory_removesOldFilesAndPreservesRecentFiles() {
        val mockCacheDir = tempFolder.newFolder("auto_clean_cache")
        val now = System.currentTimeMillis()

        // 1. Residual update APK from prior install - must always be removed
        val updatesDir = File(mockCacheDir, "updates").apply { mkdirs() }
        val residualApk = File(updatesDir, "old_update.apk").apply {
            writeText("apk content")
        }

        // 2. Receipt created 48 hours ago - must be cleaned
        val receiptsDir = File(mockCacheDir, "receipts").apply { mkdirs() }
        val staleReceipt = File(receiptsDir, "old_bill.pdf").apply {
            writeText("stale bill")
            setLastModified(now - (48L * 60L * 60L * 1000L))
        }

        // 3. Receipt created 1 hour ago - must be preserved for user sharing
        val recentReceipt = File(receiptsDir, "recent_bill.pdf").apply {
            writeText("recent bill")
            setLastModified(now - (1L * 60L * 60L * 1000L))
        }

        StorageCleanerManager.autoCleanCacheDirectory(mockCacheDir, currentTimeMillis = now)

        assertFalse("Residual APK should be removed on startup", residualApk.exists())
        assertFalse("Stale receipt older than 24h should be auto-cleaned", staleReceipt.exists())
        assertTrue("Fresh receipt younger than 24h should be kept for sharing", recentReceipt.exists())
    }
}
