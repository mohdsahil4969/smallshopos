# SYNTAXA KHATA V1.0 — UPDATE SYSTEM AUDIT

**Date:** 2026-10-24  
**Auditor:** Senior Android Security & Systems QA Engineer  
**Component:** Serverless One-Tap Update Engine (`feature/update`)

---

## 1. ARCHITECTURAL OVERVIEW

The update system in Syntaxa Khata provides a completely serverless, zero-cloud-dependency mechanism to distribute and apply application updates directly to Android devices. It operates via static HTTPS manifests (hosted on GitHub Releases, S3, or a static CDN) without requiring dynamic backend servers, user account logins, or Google Play Services.

---

## 2. AUDIT OF SECURITY SAFEGUARDS

### 2.1 Manifest & Version Validation
- **Authoritative Version Comparison:**
  Updates compare `remote.buildNumber > currentBuildNumber` as the primary authoritative metric. If build numbers match, semantic dot-separated version strings are compared component-by-component (`isVersionNewer`). A release with identical `versionName` but higher `versionCode` is correctly detected.
- **Dynamic Gradle Integration:**
  Replaced hardcoded version constants with `BuildConfig.VERSION_NAME` and `BuildConfig.VERSION_CODE`, guaranteeing synchronization with `app/build.gradle.kts`.
- **Mandatory Policy (`minimumSupportedVersion`):**
  If `currentVersion` falls below `minimumSupportedVersion`, `isMandatory` is automatically forced to `true`, disabling dismissal and deferral buttons.
- **Strict Payload Parsing:**
  Rejects manifests missing `latestVersion`, `versionCode`, `url`, or `sha256`. Rejects unreasonable file sizes (`> 60 MB`).

### 2.2 Network & Download Security
- **Strict HTTPS Enforcement:**
  Both manifest URL and binary APK download URLs must use the `https://` scheme. Plain HTTP and other schemes (`ftp://`, `file://`) are strictly rejected.
- **Domain Allowlist & Anti-SSRF:**
  Hosts are validated against `ALLOWED_MANIFEST_HOSTS` (`syntaxa.in`, `github.com`, `raw.githubusercontent.com`, `objects.githubusercontent.com`, `release-assets.githubusercontent.com`, `amazonaws.com`). Subdomain spoofing (e.g. `syntaxa.in.attacker.com`) is rejected.
- **Manual Redirect Validation:**
  HTTP auto-redirects are disabled (`instanceFollowRedirects = false`). Every redirect (301, 302, 307, 308) is individually inspected to verify the destination URL is HTTPS and belongs to an allowed host.
- **Bounded Stream Processing & Timeouts:**
  Connection timeout is 10–12 seconds; read timeout is 12–15 seconds. Streams enforce a strict 60 MB ceiling. If downloaded bytes exceed the ceiling, the stream aborts and the file is purged.
- **Incomplete / Corrupt File Deletion:**
  Temporary APK files are stored in `context.cacheDir/updates/` and deleted immediately upon any exception, cancellation, or checksum mismatch.

### 2.3 Cryptographic Integrity & APK Identity Verification (Fail-Closed)
- **SHA-256 Checksum:**
  Calculates a 64-character lowercase hexadecimal SHA-256 digest over the downloaded binary stream. Mismatch immediately deletes the file.
- **Package Archive Inspection:**
  Invokes `PackageManager.getPackageArchiveInfo` on the downloaded file before launching the installer. Verifies `archiveInfo.packageName == "com.syntaxa.khata"`. Malformed archives fail closed immediately.
- **Fail-Closed Signing-Certificate Continuity:**
  Compares downloaded APK signatures against the currently running application's certificates (supporting `signingInfo` history on Android P+ for legitimate key rotation). Missing signature evidence, empty certificates, or mismatched signatures reject the APK immediately and fail closed without ever triggering installation.

### 2.4 Android PackageInstaller & Permission Flow
- **Unknown Sources Permission:**
  Checks `context.packageManager.canRequestPackageInstalls()` on Android 8.0+ (Oreo, API 26+). If not granted, sets state to `PERMISSION_REQUIRED` and directs the user to `Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES`.
- **Resilient Continuation:**
  Provides a "Continue Install" button in the dialog to re-verify permission upon returning from Settings without restarting the download.
- **FileProvider Security:**
  Shares APK using `androidx.core.content.FileProvider` (`content://com.syntaxa.khata.fileprovider`) with scoped read grants (`FLAG_GRANT_READ_URI_PERMISSION`). External storage permissions are completely avoided.

### 2.5 Pre-Update Database Preservation
- **Authenticated AES-256-GCM Safety Snapshot:**
  Before downloading or applying an APK, an authenticated AES-256-GCM encrypted snapshot is written to `context.filesDir/pre_update_backups/` using a device-bound 256-bit key. Plaintext snapshots have been eliminated.
- **Mandatory Recoverability Check:**
  Immediately decrypts and validates the safety backup via `BackupManager.verifySafetyBackup`. If decryption or structural JSON validation fails, the file is purged and the update is aborted immediately.
- **Safe Retention Policy:**
  Retains the 3 most recent pre-update snapshots and purges older ones to prevent internal storage exhaustion.

---

## 3. VERIFIED TEST RESULTS

The test suite in `AppUpdateSystemTest.kt` verifies:
1. `test1_manifestContract_correctlyParsesStandardServerManifest`: **PASS**
2. `test2_manifestContract_flatJsonFallback`: **PASS**
3. `test3_semanticVersionComparison`: **PASS**
4. `test4_domainAllowlistSecurity`: **PASS**
5. `test5_cryptographicSha256Verification`: **PASS**
6. `test6_updateStateLifecycleAndProgress`: **PASS**
7. `test7_authoritativeBuildNumberComparison`: **PASS**
8. `test8_manifestValidationRejectsOversizedAndMissingChecksum`: **PASS**
9. `test9_signatureVerification_failsClosedOnMissingEvidence`: **PASS**
10. `test10_signatureVerification_passesOnContinuousSignatures`: **PASS**
