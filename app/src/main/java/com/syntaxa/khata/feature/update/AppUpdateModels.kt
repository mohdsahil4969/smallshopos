package com.syntaxa.khata.feature.update

import org.json.JSONObject

enum class UpdateStatus {
    IDLE,
    CHECKING,
    AVAILABLE,
    UP_TO_DATE,
    DOWNLOADING,
    VERIFYING,
    READY_TO_INSTALL,
    PERMISSION_REQUIRED,
    ERROR
}

data class AppUpdateInfo(
    val version: String,
    val buildNumber: Int,
    val minimumSupportedVersion: String = "1.0.0",
    val title: String,
    val releaseNotes: String,
    val downloadUrl: String,
    val fileSizeBytes: Long,
    val isMandatory: Boolean = false,
    val sha256: String,
    val publishedAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val MAX_ALLOWED_FILE_SIZE_BYTES = 60 * 1024 * 1024L // 60 MB safety cap

        fun fromJson(json: JSONObject): AppUpdateInfo {
            val rawVersion = json.optString("latestVersion", json.optString("version", "")).trim()
            if (rawVersion.isBlank()) {
                throw IllegalArgumentException("Manifest is missing required 'latestVersion' or 'version' field")
            }

            val rawBuildNumber = json.optInt("versionCode", json.optInt("build_number", 0))
            if (rawBuildNumber <= 0) {
                throw IllegalArgumentException("Manifest has invalid or missing 'versionCode' / 'build_number' (must be > 0)")
            }

            val minVersion = json.optString("minimumSupportedVersion", "1.0.0").trim()
            val notes = json.optString(
                "releaseNotes",
                json.optString("release_notes", "Performance improvements and bug fixes.")
            )
            val title = json.optString("title", "Syntaxa Khata v$rawVersion Available")

            var chosenUrl = json.optString("download_url", "").trim()
            var chosenSha = if (json.has("sha256")) json.optString("sha256").trim() else ""
            var chosenSize = json.optLong("file_size_bytes", 0L)

            // Android-specific block in manifest if present
            if (json.has("android") && !json.isNull("android")) {
                val androidObj = json.getJSONObject("android")
                chosenUrl = androidObj.optString("url", chosenUrl).trim()
                chosenSha = if (androidObj.has("sha256")) androidObj.optString("sha256").trim() else chosenSha
                chosenSize = androidObj.optLong("sizeBytes", chosenSize)
            }

            if (chosenUrl.isBlank() || !chosenUrl.startsWith("https://", ignoreCase = true)) {
                throw IllegalArgumentException("Manifest must provide a secure HTTPS download URL")
            }

            val normalizedSha = chosenSha.lowercase()
            if (!normalizedSha.matches(Regex("^[a-f0-9]{64}$"))) {
                throw IllegalArgumentException("Manifest must provide a valid 64-character hex SHA-256 checksum")
            }

            if (chosenSize > MAX_ALLOWED_FILE_SIZE_BYTES) {
                throw IllegalArgumentException("Declared file size ($chosenSize bytes) exceeds maximum allowable limit (${MAX_ALLOWED_FILE_SIZE_BYTES / (1024 * 1024)} MB)")
            }

            val isForced = json.optBoolean("is_mandatory", false)

            return AppUpdateInfo(
                version = rawVersion,
                buildNumber = rawBuildNumber,
                minimumSupportedVersion = minVersion,
                title = title,
                releaseNotes = notes,
                downloadUrl = chosenUrl,
                fileSizeBytes = chosenSize,
                isMandatory = isForced,
                sha256 = normalizedSha,
                publishedAt = json.optLong("releaseDate", System.currentTimeMillis())
            )
        }
    }
}

data class AppUpdateState(
    val status: UpdateStatus = UpdateStatus.IDLE,
    val progress: Float = 0f,
    val message: String = "",
    val updateInfo: AppUpdateInfo? = null,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val speedMbPerSec: Double = 0.0,
    val etaSeconds: Int = 0,
    val error: String? = null
)
