# SECURITY, PRIVACY & DEPENDENCY AUDIT

**Application:** Syntaxa Khata (`com.syntaxa.khata`)  
**Scope:** Android Manifest, Runtime Permissions, Cryptography, Data Protection, Network Security, and Dependencies  
**Auditor:** Application Security Reviewer & Systems Architect  
**Date:** 2026-10-24

---

## 1. ANDROID MANIFEST & PERMISSIONS AUDIT

### 1.1 Permission Minimization Principles
| Declared Permission | Purpose | Security Evaluation |
| :--- | :--- | :--- |
| `android.permission.BLUETOOTH` | Classic Bluetooth for ESC/POS printing (Android $\le 10$). | Low Risk. MaxSdkVersion capped at 30. |
| `android.permission.BLUETOOTH_ADMIN` | Discovery of paired printers (Android $\le 10$). | Low Risk. MaxSdkVersion capped at 30. |
| `android.permission.BLUETOOTH_CONNECT` | Connect to paired thermal printer (Android 12+). | Normal runtime permission. Isolated to printing service. |
| `android.permission.BLUETOOTH_SCAN` | Thermal printer scan with `usesPermissionFlags="neverForLocation"`. | **Zero Privacy Leak:** Flags ensure Android OS does not use Bluetooth for geo-location tracking. |
| `android.permission.USE_BIOMETRIC` | Fingerprint & Face unlock for app lock. | Handled via AndroidX `BiometricPrompt` with zero biometric data access by the application. |
| `android.permission.INTERNET` | Manifest check and APK download for updates. | Strictly restricted by application domain allowlist (`ALLOWED_MANIFEST_HOSTS`). |
| `android.permission.REQUEST_INSTALL_PACKAGES` | Triggering native Android PackageInstaller for in-app updates. | Protected by Android 8.0+ Unknown Sources Settings screen. |

### 1.2 Prohibited Dangerous Permissions (CONFIRMED ABSENT)
- ❌ **NO `READ_CONTACTS` / `WRITE_CONTACTS`**: Customer phone numbers are entered voluntarily by the merchant. The address book is never harvested.
- ❌ **NO `READ_SMS` / `SEND_SMS` / `RECEIVE_MMS`**: No SMS gateway permissions requested. Reminders use user-confirmed Android Sharesheet.
- ❌ **NO `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION`**: Zero geolocation tracking.
- ❌ **NO `READ_EXTERNAL_STORAGE` / `WRITE_EXTERNAL_STORAGE`**: File sharing strictly uses scoped internal cache via `FileProvider`.
- ❌ **NO `READ_PHONE_STATE` / `READ_CALL_LOG`**: Zero device fingerprinting or call tracking.

### 1.3 Component Exposure
- `MainActivity`: Single exported entry point (`android:exported="true"`) guarded by `MAIN`/`LAUNCHER` intent filters.
- `FileProvider`: Explicitly unexported (`android:exported="false"`, `android:grantUriPermissions="true"`). Temporary read grants issued only for explicit share intents.
- Zero exported broadcast receivers, services, or content providers.

---

## 2. CRYPTOGRAPHIC INTEGRITY & LOCAL STORAGE

### 2.1 Backup Encryption (`BackupManager.kt`)
- **Cipher Suite:** `AES/GCM/NoPadding` with 256-bit key length and 128-bit authentication tag.
- **Key Derivation:** `PBKDF2WithHmacSHA256` with 100,000 iterations (strictly bounded between 50,000 and 500,000).
- **Salt & IV:** Fresh 16-byte cryptographically secure random salt and fresh 12-byte random IV generated per backup operation via `SecureRandom`. Nonce reuse is mathematically impossible.
- **Password Hygiene:** Minimum 12-character passphrase enforced. Temporary `CharArray` buffers are wiped with null bytes (`\u0000`) in `finally` blocks.
- **Authenticated Additional Data (AAD):** Validates format version, iteration count, and metadata headers before attempting decryption, mitigating CCA attacks.
- **Full Entity Completeness:** Encrypts and restores all 10 Schema v3 entities: Customers (with due dates), Transactions, Bills, Bill Items (with product IDs), Expenses, Shop Settings, Products, Suppliers, Supplier Transactions, and Daily Cash Closings. Backward compatible with v1 and v2 backups.
- **Post-Restore Balance Invariants:** Mathematically verifies Customer balances against transactions and Supplier balances against supplier transactions before committing restore.

### 2.2 Pre-Update Authenticated Safety Backup
- **Encryption Scheme:** Authenticated AES-256-GCM encryption with 256-bit CSPRNG safety key stored in internal private preferences (`MODE_PRIVATE`).
- **Immediate Recoverability Verification:** Before any APK package installer intent is launched, the safety backup is decrypted and parsed in-memory (`verifySafetyBackup()`) to guarantee it is valid and fully recoverable.
- **Retention Policy:** Retains the 3 most recent verified safety backups; older safety backups are automatically purged.

### 2.3 App Lock PIN Throttling (`PinSecurityManager.kt`)
- **Hash Derivation:** Salted PBKDF2WithHmacSHA256 with 16-byte random salt.
- **Brute-Force Lockout:** Tracks failed attempts persistently in private preferences:
  - 3 failed attempts: 5-second forced delay.
  - 5 failed attempts: 30-second exponential lockout.
  - Lockout timestamps survive app restart and force-stop.

### 2.4 Local Database Security
- Android auto-backup is disabled: `android:allowBackup="false"`, `android:fullBackupContent="false"`. This prevents unauthorized ADB data extraction from unencrypted developer workstations.

---

## 3. IN-APP UPDATE SYSTEM SECURITY AUDIT

1. **Anti-SSRF & Anti-MITM Domain Allowlist:** Manifest URLs and APK download endpoints are validated against `ALLOWED_MANIFEST_HOSTS`. Cleartext HTTP is rejected unconditionally.
2. **Manual Redirect Inspection:** Auto-redirects are disabled (`instanceFollowRedirects = false`). Every 3xx hop is individually verified to ensure it remains on HTTPS and within the allowlist.
3. **Cryptographic SHA-256 Digest:** 64-character hex checksum verified over the downloaded stream. Incomplete or mismatched binaries are deleted immediately.
4. **Fail-Closed Package Identity & Signing Continuity:** 
   - Checks `archiveInfo.packageName == "com.syntaxa.khata"`.
   - Compares SHA-256 certificate fingerprints of the downloaded APK against the currently installed running application.
   - Fails closed: if package info cannot be read, if certificates cannot be extracted, or if hashes mismatch, the update is blocked immediately with a descriptive security error.

---

## 4. DEPENDENCY & THIRD-PARTY LIBRARY AUDIT

Inspection of `gradle/libs.versions.toml`:
- `androidx.core:core-ktx:1.15.0`: Core AndroidX extension. Verified clean.
- `androidx.lifecycle:*-ktx:2.8.7`: Architecture components. Verified clean.
- `androidx.compose.*:2024.11.00`: Native UI engine. Verified clean.
- `androidx.room:*:2.6.1`: Local SQLite abstraction. Verified clean.
- `androidx.biometric:biometric:1.1.0`: Native BiometricPrompt driver. Verified clean.
- `org.jetbrains.kotlinx:kotlinx-coroutines-*:1.9.0`: Structured concurrency. Verified clean.
- `org.json:json:20240303`: Standard JSON parser. Verified clean.

**Confirmed Absence of Unwanted SDKs:**
- 0 Google Firebase SDKs.
- 0 Analytics or telemetry SDKs (Adjust, AppsFlyer, Google Analytics).
- 0 Advertising networks (AdMob, Unity Ads, AppLovin).
- 0 Lending / credit scoring SDKs.

---

## 5. AUDIT CONCLUSION
The application meets high security and privacy standards for offline retail software:
- Zero data exfiltration pathways.
- Strict authenticated cryptography.
- Minimal permissions with zero background tracking.
- Pre-update safety backup guarantees data recoverability.
- Fail-closed binary verification blocks unauthorized code execution.
