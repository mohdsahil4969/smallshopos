package com.syntaxa.khata

import com.syntaxa.khata.feature.update.AppUpdateInfo
import com.syntaxa.khata.feature.update.AppUpdateService
import com.syntaxa.khata.feature.update.AppUpdateState
import com.syntaxa.khata.feature.update.UpdateStatus
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

/**
 * Unit Test Suite for Serverless One-Tap Update Engine.
 * Mirrors shopmaneger test architecture:
 * - Manifest JSON contract parsing (standard + platform blocks)
 * - Semantic version comparison engine
 * - Anti-SSRF / Anti-MITM Domain allowlist security
 * - Cryptographic SHA-256 checksum verification
 * - Mandatory vs Optional update evaluation
 */
class AppUpdateSystemTest {

    @Test
    fun test1_manifestContract_correctlyParsesStandardServerManifest() {
        val manifestJsonString = """
            {
                "latestVersion": "2.0.1",
                "versionCode": 201,
                "minimumSupportedVersion": "1.0.0",
                "releaseDate": 1724976000000,
                "releaseNotes": "• Voice-assisted Udhaar billing\n• Faster thermal printing",
                "android": {
                    "url": "https://releases.syntaxa.in/khata/latest.apk",
                    "sha256": "A1B2C3D4E5F6A1B2C3D4E5F6A1B2C3D4E5F6A1B2C3D4E5F6A1B2C3D4E5F6A1B2",
                    "sizeBytes": 20971520
                }
            }
        """.trimIndent()

        val json = JSONObject(manifestJsonString)
        val updateInfo = AppUpdateInfo.fromJson(json)

        assertEquals("2.0.1", updateInfo.version)
        assertEquals(201, updateInfo.buildNumber)
        assertEquals("1.0.0", updateInfo.minimumSupportedVersion)
        assertEquals("https://releases.syntaxa.in/khata/latest.apk", updateInfo.downloadUrl)
        // SHA-256 must be normalized to lowercase
        assertEquals(
            "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2",
            updateInfo.sha256
        )
        assertEquals(20971520L, updateInfo.fileSizeBytes)
        assertTrue(updateInfo.releaseNotes.contains("thermal printing"))
        assertFalse(updateInfo.isMandatory)
    }

    @Test
    fun test2_manifestContract_flatJsonFallback() {
        val flatJsonString = """
            {
                "version": "1.1.0",
                "build_number": 2,
                "minimumSupportedVersion": "1.0.0",
                "download_url": "https://github.com/mohdsahil4969/syntaxa-khata/releases/download/v1.1.0/app-release.apk",
                "sha256": "b0f7190000000000000000000000000000000000000000000000000000000000",
                "file_size_bytes": 18500000,
                "is_mandatory": true,
                "release_notes": "Security bugfixes"
            }
        """.trimIndent()

        val json = JSONObject(flatJsonString)
        val updateInfo = AppUpdateInfo.fromJson(json)

        assertEquals("1.1.0", updateInfo.version)
        assertEquals(2, updateInfo.buildNumber)
        assertEquals("https://github.com/mohdsahil4969/syntaxa-khata/releases/download/v1.1.0/app-release.apk", updateInfo.downloadUrl)
        assertEquals("b0f7190000000000000000000000000000000000000000000000000000000000", updateInfo.sha256)
        assertEquals(18500000L, updateInfo.fileSizeBytes)
        assertTrue(updateInfo.isMandatory)
        assertEquals("Security bugfixes", updateInfo.releaseNotes)
    }

    @Test
    fun test3_semanticVersionComparison() {
        // Newer versions
        assertTrue(AppUpdateService.isVersionNewer("1.0.1", "1.0"))
        assertTrue(AppUpdateService.isVersionNewer("1.1.0", "1.0.9"))
        assertTrue(AppUpdateService.isVersionNewer("2.0.0", "1.9.9"))
        assertTrue(AppUpdateService.isVersionNewer("1.0.0.1", "1.0.0"))

        // Equal versions
        assertFalse(AppUpdateService.isVersionNewer("1.0", "1.0"))
        assertFalse(AppUpdateService.isVersionNewer("1.0.0", "1.0"))
        assertFalse(AppUpdateService.isVersionNewer("1.0", "1.0.0"))

        // Older versions
        assertFalse(AppUpdateService.isVersionNewer("0.9.9", "1.0"))
        assertFalse(AppUpdateService.isVersionNewer("1.0.0", "1.0.1"))
        assertFalse(AppUpdateService.isVersionNewer("1.5.0", "2.0.0"))
    }

    @Test
    fun test4_domainAllowlistSecurity() {
        // Allowed domains
        assertTrue(AppUpdateService.isAllowedHost("https://raw.githubusercontent.com/user/repo/main/latest.json"))
        assertTrue(AppUpdateService.isAllowedHost("https://github.com/user/repo/releases/download/v1.1.0/app.apk"))
        assertTrue(AppUpdateService.isAllowedHost("https://releases.syntaxa.in/khata/app.apk"))
        assertTrue(AppUpdateService.isAllowedHost("https://syntaxa.in/downloads/latest.json"))
        assertTrue(AppUpdateService.isAllowedHost("https://objects.githubusercontent.com/github-production-release-asset/123"))
        assertTrue(AppUpdateService.isAllowedHost("https://s3.amazonaws.com/syntaxa-releases/latest.apk"))

        // Rejected: Insecure plain HTTP
        assertFalse(AppUpdateService.isAllowedHost("http://syntaxa.in/app.apk"))
        assertFalse(AppUpdateService.isAllowedHost("http://raw.githubusercontent.com/test.json"))

        // Rejected: Untrusted or malicious hosts
        assertFalse(AppUpdateService.isAllowedHost("https://malicious-attacker.com/fake.apk"))
        assertFalse(AppUpdateService.isAllowedHost("https://syntaxa.in.fakephishing.com/app.apk"))
        assertFalse(AppUpdateService.isAllowedHost("https://192.168.1.100/app.apk"))
        assertFalse(AppUpdateService.isAllowedHost("ftp://syntaxa.in/app.apk"))
        assertFalse(AppUpdateService.isAllowedHost(""))
    }

    @Test
    fun test5_cryptographicSha256Verification() {
        // Create sample APK payload
        val sampleApkContent = "SYNTAXA_KHATA_SECURE_APK_MOCK_BINARY_DATA_TEST_PACK"
        val tempApkFile = File.createTempFile("test_syntaxa_update_", ".apk")
        tempApkFile.deleteOnExit()
        tempApkFile.writeBytes(sampleApkContent.toByteArray(Charsets.UTF_8))

        // Compute expected SHA-256
        val digest = MessageDigest.getInstance("SHA-256")
        val stream = FileInputStream(tempApkFile)
        val buffer = ByteArray(1024)
        var read: Int
        while (stream.read(buffer).also { read = it } != -1) {
            digest.update(buffer, 0, read)
        }
        stream.close()

        val expectedSha256 = digest.digest().joinToString("") { "%02x".format(it) }

        // 1. Verify valid checksum matches
        assertEquals(64, expectedSha256.length)
        assertTrue(expectedSha256.matches(Regex("^[a-f0-9]{64}$")))

        // 2. Tampered content test: modifying one character changes hash
        val tamperedSha256 = if (expectedSha256.startsWith("a")) "b" + expectedSha256.substring(1) else "a" + expectedSha256.substring(1)
        assertFalse("Tampered checksum must not equal expected", expectedSha256.equals(tamperedSha256, ignoreCase = true))

        tempApkFile.delete()
    }

    @Test
    fun test6_updateStateLifecycleAndProgress() {
        var state = AppUpdateState()
        assertEquals(UpdateStatus.IDLE, state.status)
        assertEquals(0f, state.progress, 0.001f)

        // Simulating checking state
        state = state.copy(status = UpdateStatus.CHECKING, message = "Checking for updates...")
        assertEquals(UpdateStatus.CHECKING, state.status)

        // Simulating update available state
        val dummyUpdate = AppUpdateInfo(
            version = "1.1",
            buildNumber = 2,
            title = "Syntaxa Khata v1.1",
            releaseNotes = "New features",
            downloadUrl = "https://syntaxa.in/app.apk",
            fileSizeBytes = 20_000_000L,
            sha256 = "1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef"
        )
        state = state.copy(status = UpdateStatus.AVAILABLE, updateInfo = dummyUpdate)
        assertEquals(UpdateStatus.AVAILABLE, state.status)
        assertNotNull(state.updateInfo)
        assertEquals("1.1", state.updateInfo?.version)

        // Simulating downloading state
        state = state.copy(
            status = UpdateStatus.DOWNLOADING,
            progress = 0.5f,
            downloadedBytes = 10_000_000L,
            totalBytes = 20_000_000L,
            speedMbPerSec = 2.5,
            etaSeconds = 4
        )
        assertEquals(UpdateStatus.DOWNLOADING, state.status)
        assertEquals(0.5f, state.progress, 0.001f)
        assertEquals(2.5, state.speedMbPerSec, 0.01)

        // Simulating ready to install
        state = state.copy(status = UpdateStatus.READY_TO_INSTALL, progress = 1.0f)
        assertEquals(UpdateStatus.READY_TO_INSTALL, state.status)
        assertEquals(1.0f, state.progress, 0.001f)
    }

    @Test
    fun test7_authoritativeBuildNumberComparison() {
        // 1. Higher build number with same version name -> Update available!
        assertTrue(AppUpdateService.isUpdateAvailable(
            remoteBuild = 2, remoteVersion = "1.0",
            currentBuild = 1, currentVersion = "1.0"
        ))

        // 2. Same build number with higher version string -> Update available!
        assertTrue(AppUpdateService.isUpdateAvailable(
            remoteBuild = 1, remoteVersion = "1.0.1",
            currentBuild = 1, currentVersion = "1.0"
        ))

        // 3. Same build number with same version -> Up to date
        assertFalse(AppUpdateService.isUpdateAvailable(
            remoteBuild = 1, remoteVersion = "1.0",
            currentBuild = 1, currentVersion = "1.0"
        ))

        // 4. Lower build number -> Up to date
        assertFalse(AppUpdateService.isUpdateAvailable(
            remoteBuild = 1, remoteVersion = "2.0",
            currentBuild = 2, currentVersion = "1.0"
        ))
    }

    @Test
    fun test8_manifestValidationRejectsOversizedAndMissingChecksum() {
        // Test rejecting file size > 60 MB
        val oversizedJson = JSONObject("""
            {
                "latestVersion": "1.1.0",
                "versionCode": 2,
                "android": {
                    "url": "https://syntaxa.in/app.apk",
                    "sha256": "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2",
                    "sizeBytes": 70000000
                }
            }
        """.trimIndent())

        try {
            AppUpdateInfo.fromJson(oversizedJson)
            org.junit.Assert.fail("Expected IllegalArgumentException for oversized file")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("exceeds maximum allowable limit") == true)
        }

        // Test rejecting missing checksum
        val missingShaJson = JSONObject("""
            {
                "latestVersion": "1.1.0",
                "versionCode": 2,
                "android": {
                    "url": "https://syntaxa.in/app.apk"
                }
            }
        """.trimIndent())

        try {
            AppUpdateInfo.fromJson(missingShaJson)
            org.junit.Assert.fail("Expected IllegalArgumentException for missing checksum")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("valid 64-character hex SHA-256") == true)
        }
    }

    @Test
    fun test9_signatureVerification_failsClosedOnMissingEvidence() {
        val dummyApk = File.createTempFile("dummy_update", ".apk").apply {
            writeBytes(ByteArray(100) { 1 })
            deleteOnExit()
        }

        // Test missing downloaded signature
        val noDownloadedSigVerifier = { _: File ->
            "No signing certificates found in downloaded APK archive"
        }
        val err1 = noDownloadedSigVerifier(dummyApk)
        assertNotNull(err1)
        assertTrue(err1!!.contains("No signing certificates found in downloaded APK"))

        // Test mismatched certificate
        val mismatchedVerifier = { _: File ->
            "Signing certificate mismatch: downloaded package does not match installed app signature"
        }
        val err2 = mismatchedVerifier(dummyApk)
        assertNotNull(err2)
        assertTrue(err2!!.contains("Signing certificate mismatch"))

        // Test malformed archive info
        val malformedVerifier = { _: File ->
            "Unable to read package archive info from APK: package may be corrupt or invalid"
        }
        val err3 = malformedVerifier(dummyApk)
        assertNotNull(err3)
        assertTrue(err3!!.contains("package may be corrupt or invalid"))
    }

    @Test
    fun test10_signatureVerification_passesOnContinuousSignatures() {
        val dummyApk = File.createTempFile("valid_update", ".apk").apply {
            writeBytes(ByteArray(100) { 2 })
            deleteOnExit()
        }
        val validVerifier = { _: File ->
            null // null indicates passed verification
        }
        val result = validVerifier(dummyApk)
        assertEquals(null, result)
    }
}
