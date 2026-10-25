package com.syntaxa.khata.feature.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.syntaxa.khata.BuildConfig
import com.syntaxa.khata.feature.backup.BackupManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Hardened Serverless One-Tap In-App Update Engine for Syntaxa Khata.
 * 
 * Security & Reliability Safeguards:
 * 1. Authoritative versionCode (build number) comparison + semantic versioning.
 * 2. Strict domain allowlist with manual HTTPS redirect validation (anti-SSRF / anti-MITM).
 * 3. Enforced connection & read timeouts, bounded maximum file size (60MB).
 * 4. Automated pre-update database snapshot with verification (aborts if backup fails).
 * 5. Mandatory 64-character SHA-256 cryptographic checksum verification.
 * 6. Package archive identity & signing-certificate continuity verification.
 * 7. Clean purge of failed/partial downloads on any error or cancellation.
 * 8. Resilient Android unknown-source package installation permission flow.
 */
class AppUpdateService(private val context: Context) {

    companion object {
        val CURRENT_VERSION: String
            get() = try {
                BuildConfig.VERSION_NAME
            } catch (_: Throwable) {
                "1.0"
            }

        val CURRENT_BUILD_NUMBER: Int
            get() = try {
                BuildConfig.VERSION_CODE
            } catch (_: Throwable) {
                1
            }

        const val DEFAULT_UPDATE_MANIFEST_URL =
            "https://raw.githubusercontent.com/mohdsahil4969/smallshopos/main/latest.json"

        const val MAX_ALLOWED_APK_BYTES = 60 * 1024 * 1024L // 60 MB

        val ALLOWED_MANIFEST_HOSTS = listOf(
            "syntaxa.in",
            "github.com",
            "githubusercontent.com",
            "raw.githubusercontent.com",
            "objects.githubusercontent.com",
            "release-assets.githubusercontent.com",
            "github-releases.githubusercontent.com",
            "amazonaws.com",
            "releases.syntaxa.in"
        )

        const val PREF_AUTO_CHECK_KEY = "auto_check_updates_enabled"
        const val PREF_LAST_CHECK_KEY = "last_update_check_time"

        fun isAllowedHost(urlString: String): Boolean {
            return try {
                val uri = java.net.URI(urlString)
                if (uri.scheme?.lowercase() != "https") return false
                val host = uri.host?.lowercase() ?: return false
                ALLOWED_MANIFEST_HOSTS.any { allowed -> host == allowed || host.endsWith(".$allowed") }
            } catch (_: Exception) {
                false
            }
        }

        fun isVersionNewer(remote: String, current: String): Boolean {
            return try {
                val remoteParts = remote.split('.').map { it.trim().toIntOrNull() ?: 0 }
                val currentParts = current.split('.').map { it.trim().toIntOrNull() ?: 0 }
                val maxLen = maxOf(remoteParts.size, currentParts.size)

                for (i in 0 until maxLen) {
                    val r = if (i < remoteParts.size) remoteParts[i] else 0
                    val c = if (i < currentParts.size) currentParts[i] else 0
                    if (r > c) return true
                    if (r < c) return false
                }
                false
            } catch (_: Exception) {
                false
            }
        }

        /**
         * Authoritative comparison: checks build number first, then version string.
         */
        fun isUpdateAvailable(
            remoteBuild: Int,
            remoteVersion: String,
            currentBuild: Int,
            currentVersion: String
        ): Boolean {
            if (remoteBuild > currentBuild) return true
            if (remoteBuild == currentBuild) {
                return isVersionNewer(remoteVersion, currentVersion)
            }
            return false
        }
    }

    private val prefs = context.getSharedPreferences("syntaxa_update_prefs", Context.MODE_PRIVATE)

    private val _updateState = MutableStateFlow(AppUpdateState())
    val updateState: StateFlow<AppUpdateState> = _updateState.asStateFlow()

    private var pendingApkFile: File? = null

    fun isAutoCheckEnabled(): Boolean {
        return prefs.getBoolean(PREF_AUTO_CHECK_KEY, true)
    }

    fun setAutoCheckEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(PREF_AUTO_CHECK_KEY, enabled).apply()
    }

    fun dismissUpdate() {
        _updateState.value = AppUpdateState(status = UpdateStatus.IDLE)
    }

    /**
     * Checks remote serverless manifest for available updates over HTTPS.
     */
    suspend fun checkForUpdate(
        customManifestUrl: String? = null,
        isManual: Boolean = false
    ): AppUpdateInfo? = withContext(Dispatchers.IO) {
        _updateState.value = AppUpdateState(
            status = UpdateStatus.CHECKING,
            message = "Checking for updates..."
        )

        try {
            val urlString = customManifestUrl ?: DEFAULT_UPDATE_MANIFEST_URL

            if (!isAllowedHost(urlString)) {
                val err = "Update check blocked: URL not in security allowlist ($urlString)"
                _updateState.value = AppUpdateState(
                    status = if (isManual) UpdateStatus.ERROR else UpdateStatus.IDLE,
                    message = if (isManual) "Update server could not be verified." else "",
                    error = err
                )
                return@withContext null
            }

            val url = URL(urlString)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 10000
                readTimeout = 12000
                requestMethod = "GET"
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "SyntaxaKhata-AppUpdate/${CURRENT_VERSION}")
            }

            val responseCode = connection.responseCode
            prefs.edit().putLong(PREF_LAST_CHECK_KEY, System.currentTimeMillis()).apply()

            if (responseCode == HttpURLConnection.HTTP_OK) {
                val jsonText = connection.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
                connection.disconnect()

                val root = JSONObject(jsonText)
                var info = AppUpdateInfo.fromJson(root)

                // Enforce mandatory update policy if current version is below minimumSupportedVersion
                if (isVersionNewer(info.minimumSupportedVersion, CURRENT_VERSION)) {
                    info = info.copy(isMandatory = true)
                }

                val hasUpdate = isUpdateAvailable(
                    remoteBuild = info.buildNumber,
                    remoteVersion = info.version,
                    currentBuild = CURRENT_BUILD_NUMBER,
                    currentVersion = CURRENT_VERSION
                )

                if (hasUpdate) {
                    _updateState.value = AppUpdateState(
                        status = UpdateStatus.AVAILABLE,
                        message = "Syntaxa Khata update v${info.version} (Build ${info.buildNumber}) is available!",
                        updateInfo = info
                    )
                    return@withContext info
                } else {
                    _updateState.value = AppUpdateState(
                        status = UpdateStatus.UP_TO_DATE,
                        message = "You're on the latest version (v${CURRENT_VERSION})."
                    )
                    return@withContext null
                }
            } else {
                connection.disconnect()
                _updateState.value = AppUpdateState(
                    status = if (isManual) UpdateStatus.ERROR else UpdateStatus.IDLE,
                    message = if (isManual) "Update check failed: Server returned HTTP $responseCode" else "",
                    error = "HTTP response $responseCode"
                )
                return@withContext null
            }
        } catch (e: Exception) {
            _updateState.value = AppUpdateState(
                status = if (isManual) UpdateStatus.ERROR else UpdateStatus.IDLE,
                message = if (isManual) "Unable to check for updates: ${e.localizedMessage ?: "Network error"}" else "",
                error = e.localizedMessage
            )
            return@withContext null
        }
    }

    var customApkVerifier: ((File) -> String?)? = null

    private fun getOrCreateSafetyKey(): ByteArray {
        val prefs = context.getSharedPreferences("syntaxa_security_pref", Context.MODE_PRIVATE)
        val existingHex = prefs.getString("safety_backup_key", null)
        if (existingHex != null && existingHex.length == 64) {
            val key = ByteArray(32)
            for (i in 0 until 32) {
                key[i] = ((Character.digit(existingHex[i * 2], 16) shl 4) + Character.digit(existingHex[i * 2 + 1], 16)).toByte()
            }
            return key
        }
        val newKey = ByteArray(32)
        SecureRandom().nextBytes(newKey)
        val hex = newKey.joinToString("") { "%02x".format(it) }
        prefs.edit().putString("safety_backup_key", hex).apply()
        return newKey
    }

    /**
     * Creates an automated authenticated AES-256-GCM safety backup of user data prior to package replacement.
     * Retains the last 3 pre-update backups and purges older ones to avoid storage bloat.
     * Requires immediate recoverability verification before returning success.
     */
    suspend fun createPreUpdateSafetyBackup(backupManager: BackupManager): File? = withContext(Dispatchers.IO) {
        try {
            val backupDir = File(context.filesDir, "pre_update_backups")
            if (!backupDir.exists()) {
                backupDir.mkdirs()
            }
            val timestamp = System.currentTimeMillis()
            val backupFile = File(backupDir, "pre_update_safety_backup_$timestamp.enc")
            val safetyKey = getOrCreateSafetyKey()

            val success = FileOutputStream(backupFile).use { out ->
                backupManager.exportSafetyBackup(out, safetyKey)
            }

            if (!success || !backupFile.exists() || backupFile.length() == 0L) {
                backupFile.delete()
                return@withContext null
            }

            // Verify recoverability of safety backup
            val verified = FileInputStream(backupFile).use { inStream ->
                backupManager.verifySafetyBackup(inStream, safetyKey)
            }
            if (!verified) {
                backupFile.delete()
                return@withContext null
            }

            // Prune older pre-update backups, keeping only the 3 most recent
            val existingBackups = backupDir.listFiles { f -> f.name.startsWith("pre_update_safety_backup_") && f.name.endsWith(".enc") }
            if (existingBackups != null && existingBackups.size > 3) {
                existingBackups.sortedByDescending { it.lastModified() }
                    .drop(3)
                    .forEach { it.delete() }
            }

            backupFile
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Executes the one-tap update workflow with complete security safeguards.
     */
    suspend fun executeOneClickUpdate(
        updateInfo: AppUpdateInfo,
        backupManager: BackupManager
    ): Boolean = withContext(Dispatchers.IO) {
        var downloadedApkFile: File? = null
        try {
            // Step 1: Automated Safety Backup (Mandatory check)
            _updateState.value = _updateState.value.copy(
                status = UpdateStatus.DOWNLOADING,
                progress = 0.05f,
                message = "Creating database safety backup..."
            )
            val backup = createPreUpdateSafetyBackup(backupManager)
            if (backup == null || !backup.exists() || backup.length() == 0L) {
                throw IllegalStateException("Pre-update safety backup failed. Update cancelled to protect shop records.")
            }

            // Step 2: Validate Download Host & Scheme
            if (updateInfo.downloadUrl.isBlank()) {
                throw IllegalArgumentException("Missing download URL in update manifest")
            }
            if (!isAllowedHost(updateInfo.downloadUrl)) {
                throw SecurityException("Download URL host is not in security allowlist: ${updateInfo.downloadUrl}")
            }

            // Step 3: Validate Mandatory 64-char Hex SHA-256 Checksum
            val expectedSha256 = updateInfo.sha256.trim().lowercase()
            if (!expectedSha256.matches(Regex("^[a-f0-9]{64}$"))) {
                throw IllegalArgumentException("Mandatory 64-character SHA-256 checksum missing or invalid in update manifest")
            }

            // Step 4: Stream Download over HTTPS with Redirection Validation
            val updatesDir = File(context.cacheDir, "updates")
            if (!updatesDir.exists()) updatesDir.mkdirs()
            val apkFile = File(updatesDir, "SyntaxaKhata_${updateInfo.version}_build${updateInfo.buildNumber}.apk")
            downloadedApkFile = apkFile
            if (apkFile.exists()) apkFile.delete()

            _updateState.value = _updateState.value.copy(
                status = UpdateStatus.DOWNLOADING,
                progress = 0.1f,
                message = "Connecting to secure update server..."
            )

            var currentUrl = updateInfo.downloadUrl
            var connection: HttpURLConnection? = null
            var inputStream: InputStream? = null

            // Follow up to 5 HTTPS redirects securely
            for (redirect in 0 until 5) {
                if (!isAllowedHost(currentUrl)) {
                    throw SecurityException("Update redirected to unauthorized host: $currentUrl")
                }
                connection = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = false
                    connectTimeout = 12000
                    readTimeout = 15000
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "SyntaxaKhata-AppUpdate/${CURRENT_VERSION}")
                }

                val code = connection.responseCode
                if (code == HttpURLConnection.HTTP_MOVED_PERM ||
                    code == HttpURLConnection.HTTP_MOVED_TEMP ||
                    code == 307 || code == 308
                ) {
                    val location = connection.getHeaderField("Location") ?: break
                    connection.disconnect()
                    currentUrl = location
                    continue
                }
                if (code == HttpURLConnection.HTTP_OK) {
                    inputStream = connection.inputStream
                    break
                } else {
                    connection.disconnect()
                    throw IllegalStateException("Server returned HTTP $code while downloading update")
                }
            }

            if (inputStream == null || connection == null) {
                throw IllegalStateException("Failed to establish secure download connection")
            }

            val reportedTotalBytes = if (connection.contentLengthLong > 0) connection.contentLengthLong else updateInfo.fileSizeBytes
            if (reportedTotalBytes > MAX_ALLOWED_APK_BYTES) {
                connection.disconnect()
                throw IllegalArgumentException("Reported update size ($reportedTotalBytes bytes) exceeds security limit of ${MAX_ALLOWED_APK_BYTES / (1024 * 1024)} MB")
            }

            var receivedBytes = 0L
            val buffer = ByteArray(8192)
            val startTime = System.currentTimeMillis()
            var lastUpdateMs = 0L

            FileOutputStream(apkFile).use { fileOut ->
                var read: Int
                while (inputStream.read(buffer).also { read = it } != -1) {
                    fileOut.write(buffer, 0, read)
                    receivedBytes += read

                    if (receivedBytes > MAX_ALLOWED_APK_BYTES) {
                        throw IllegalStateException("Downloaded data exceeded maximum allowed size of ${MAX_ALLOWED_APK_BYTES / (1024 * 1024)} MB")
                    }

                    val now = System.currentTimeMillis()
                    if (now - lastUpdateMs > 150 || receivedBytes == reportedTotalBytes) {
                        lastUpdateMs = now
                        val elapsedSec = (now - startTime) / 1000.0
                        val speedBytesSec = if (elapsedSec > 0) receivedBytes / elapsedSec else 0.0
                        val speedMb = speedBytesSec / (1024 * 1024)
                        val remainingBytes = maxOf(0L, reportedTotalBytes - receivedBytes)
                        val etaSec = if (speedBytesSec > 0) (remainingBytes / speedBytesSec).toInt() else 0
                        val progressFraction = if (reportedTotalBytes > 0) (receivedBytes.toFloat() / reportedTotalBytes).coerceIn(0f, 1f) else 0.5f

                        val mbDownloaded = "%.1f".format(receivedBytes / (1024.0 * 1024.0))
                        val mbTotal = "%.1f".format(reportedTotalBytes / (1024.0 * 1024.0))
                        val speedFormatted = "%.1f".format(speedMb)
                        val etaText = if (etaSec > 0) " • ~${etaSec}s left" else ""

                        _updateState.value = _updateState.value.copy(
                            status = UpdateStatus.DOWNLOADING,
                            progress = 0.1f + (progressFraction * 0.70f),
                            message = "Downloading: $mbDownloaded MB / $mbTotal MB (${(progressFraction * 100).toInt()}%) • $speedFormatted MB/s$etaText",
                            downloadedBytes = receivedBytes,
                            totalBytes = reportedTotalBytes,
                            speedMbPerSec = speedMb,
                            etaSeconds = etaSec
                        )
                    }
                }
                fileOut.flush()
            }
            connection.disconnect()

            // Step 5: Cryptographic SHA-256 Verification
            _updateState.value = _updateState.value.copy(
                status = UpdateStatus.VERIFYING,
                progress = 0.85f,
                message = "Verifying cryptographic SHA-256 checksum..."
            )

            val digest = MessageDigest.getInstance("SHA-256")
            apkFile.inputStream().use { stream ->
                val buf = ByteArray(8192)
                var r: Int
                while (stream.read(buf).also { r = it } != -1) {
                    digest.update(buf, 0, r)
                }
            }
            val actualSha256 = digest.digest().joinToString("") { "%02x".format(it) }.lowercase()

            if (actualSha256 != expectedSha256) {
                apkFile.delete()
                _updateState.value = _updateState.value.copy(
                    status = UpdateStatus.ERROR,
                    message = "Update verification failed: corrupted package checksum.",
                    error = "Checksum mismatch (expected: $expectedSha256, actual: $actualSha256)"
                )
                return@withContext false
            }

            // Step 6: APK Identity & Signing-Certificate Continuity Verification
            _updateState.value = _updateState.value.copy(
                status = UpdateStatus.VERIFYING,
                progress = 0.95f,
                message = "Verifying package signature and identity..."
            )

            val verificationError = verifyApkPackageAndSignature(apkFile)
            if (verificationError != null) {
                apkFile.delete()
                _updateState.value = _updateState.value.copy(
                    status = UpdateStatus.ERROR,
                    message = "Package security check failed: $verificationError",
                    error = verificationError
                )
                return@withContext false
            }

            pendingApkFile = apkFile

            // Step 7: Android Unknown-Source Package Installation Permission Check
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    _updateState.value = _updateState.value.copy(
                        status = UpdateStatus.PERMISSION_REQUIRED,
                        progress = 1.0f,
                        message = "Installation permission required. Please allow installing apps from this source in Settings."
                    )
                    requestInstallPermission()
                    return@withContext true
                }
            }

            // Step 8: Native Android PackageInstaller Trigger
            _updateState.value = _updateState.value.copy(
                status = UpdateStatus.READY_TO_INSTALL,
                progress = 1.0f,
                message = "Update verified! Starting Android installer..."
            )

            launchNativeApkInstaller(apkFile)
            true
        } catch (e: Exception) {
            downloadedApkFile?.delete()
            _updateState.value = _updateState.value.copy(
                status = UpdateStatus.ERROR,
                message = "Update failed: ${e.localizedMessage}",
                error = e.localizedMessage
            )
            false
        }
    }

    /**
     * Inspects downloaded APK package name and signing certificates against currently installed app.
     * Fails closed: returns an error description string if signature evidence is missing, mismatched, or invalid.
     */
    fun verifyApkPackageAndSignature(apkFile: File): String? {
        val verifier = customApkVerifier
        if (verifier != null) {
            return verifier(apkFile)
        }

        return try {
            val pm = context.packageManager
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageManager.GET_SIGNING_CERTIFICATES
            } else {
                @Suppress("DEPRECATION")
                PackageManager.GET_SIGNATURES
            }

            val archiveInfo = pm.getPackageArchiveInfo(apkFile.absolutePath, flags)
                ?: return "Unable to read package archive info from APK: package may be corrupt or invalid"

            if (archiveInfo.packageName != context.packageName) {
                return "Package name mismatch: expected ${context.packageName}, found ${archiveInfo.packageName}"
            }

            val installedInfo = try {
                pm.getPackageInfo(context.packageName, flags)
            } catch (e: Exception) {
                return "Unable to access installed package signature: verification failed closed"
            }

            val downloadedSignatures = extractSignatures(archiveInfo)
            val installedSignatures = extractSignatures(installedInfo)

            if (downloadedSignatures.isEmpty()) {
                return "No signing certificates found in downloaded APK archive"
            }
            if (installedSignatures.isEmpty()) {
                return "No signing certificates found in installed application"
            }

            val matches = downloadedSignatures.any { ds ->
                installedSignatures.any { isig -> ds.contentEquals(isig) }
            }
            if (!matches) {
                return "Signing certificate mismatch: downloaded package does not match installed app signature"
            }

            null
        } catch (e: Exception) {
            "Package verification error: ${e.localizedMessage}"
        }
    }

    @Suppress("DEPRECATION")
    private fun extractSignatures(info: PackageInfo): List<ByteArray> {
        val result = mutableListOf<ByteArray>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = info.signingInfo
            if (signingInfo != null) {
                if (signingInfo.hasMultipleSigners()) {
                    signingInfo.apkContentsSigners?.forEach { result.add(it.toByteArray()) }
                } else {
                    signingInfo.signingCertificateHistory?.forEach { result.add(it.toByteArray()) }
                }
            }
        }
        if (result.isEmpty()) {
            info.signatures?.forEach { result.add(it.toByteArray()) }
        }
        return result
    }

    fun requestInstallPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manageIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(manageIntent)
        }
    }

    /**
     * Resumes APK installation after user grants permission in Android Settings.
     */
    fun resumeInstallAfterPermission(): Boolean {
        val apk = pendingApkFile ?: return false
        if (!apk.exists()) return false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                return false
            }
        }

        launchNativeApkInstaller(apk)
        return true
    }

    /**
     * Triggers Android's native package installer via FileProvider.
     */
    fun launchNativeApkInstaller(apkFile: File) {
        if (!apkFile.exists()) return

        val contentUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )

        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        context.startActivity(installIntent)
    }
}
