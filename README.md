# Syntaxa Khata (v1.0)
**Offline-First Daily Business Assistant for Small Indian Shops**

Designed specifically for small kirana shops, general stores, and retail merchants. Works completely offline with zero mandatory cloud dependencies, built on Android's modern native stack with Jetpack Compose, Room SQLite, and Clean Architecture.

---

## 📱 Quick Start: Install APK Directly

A pre-built, ready-to-install debug APK is included in this package:
- Location: `apk/SyntaxaKhata-v1.0-debug.apk`

### Steps to Install on Android Device:
1. Transfer `SyntaxaKhata-v1.0-debug.apk` to your Android phone (via WhatsApp, Google Drive, USB cable, or Quick Share).
2. Tap the APK file on your phone.
3. If prompted, allow "Install from Unknown Sources" in your phone settings.
4. Open **Syntaxa Khata** and start managing your shop!

---

## ✨ Key Features (100% PRD Compliant)

1. **Digital Khata & Udhaar Management:**
   - Add Udhaar (Credit given to customer).
   - Collect Payments (Cash, UPI, Other) with instant balance recalculation.
   - Authoritative ledger with immutable reversal transactions (never alters past financial truth).
   - Strict 64-bit integer paise arithmetic (no IEEE 754 floating-point rounding errors).

2. **Daily Sales & Billing:**
   - Quick Bill creation with multiple line items, quantity, rate, and discount.
   - Payment splitting (Cash, UPI, Customer Udhaar credit balance).

3. **Daily Expenses & Cash Flow:**
   - Categorized daily expense recording (Rent, Utilities, Stock, Staff, Tea/Snacks, Miscellaneous).
   - Today's summary: Total Sales, Cash in Hand, UPI Collections, Expenses, and Total Outstanding Udhaar.

4. **Offline PDF Receipts & Sharing:**
   - Android-native vector PDF generation (no internet required).
   - 4 Receipt types: Sale Bill, Payment Receipt, Udhaar Receipt, and Customer Account Statement.
   - 1-tap WhatsApp sharing and standard Android Sharesheet integration with secure FileProvider content URIs.

5. **Bluetooth Thermal Receipt Printing:**
   - Direct Bluetooth SPP ESC/POS thermal printing.
   - Configurable paper widths: **58mm** (32 columns) and **80mm** (48 columns).
   - Shop name, items, tax/discount, payment mode, and custom footer note.

6. **Customer Payment Reminders:**
   - Pre-formatted, polite WhatsApp/SMS payment reminder templates in English and Hindi.
   - Includes customer name, pending balance, and shop UPI ID.

7. **Security & App Lock:**
   - Biometric fingerprint/face unlock via AndroidX `BiometricPrompt`.
   - Salted SHA-256 PIN authentication with brute-force delay throttling.

8. **Offline Backup & Data Portability:**
   - Encrypted/checksum-verified JSON backup and restore with SHA-256 integrity verification.
   - CSV export of customers, ledger transactions, and expense records for Excel/Sheets.

9. **Serverless One-Tap Updates:**
   - 100% serverless static manifest update engine (`latest.json` hosted on GitHub / CDN).
   - Domain allowlist verification (anti-SSRF / anti-MITM protection).
   - Mandatory SHA-256 cryptographic checksum verification.
   - Automated pre-update safety backup of SQLite database prior to update installation.
   - In-app Material 3 update dialog with live download speed, ETA, and progress bar.
   - Native `PackageInstaller` launch via Android `FileProvider`.

10. **Bilingual Support:**
    - Fully localized in **English** and **Hindi (हिन्दी)**.

---

## 🛠️ Tech Stack & Architecture

- **Language:** Kotlin 2.0.21
- **UI Toolkit:** Jetpack Compose + Material 3 Design System
- **Database:** Room SQLite (Entities: `Customer`, `LedgerTransaction`, `Bill`, `BillItem`, `Expense`, `ShopProfile`, `AuditLog`)
- **Architecture:** MVVM + Clean Architecture + Repository Pattern
- **State Management:** Kotlin Coroutines + `StateFlow`
- **Preferences:** Jetpack DataStore (Preferences)
- **PDF Generation:** Native `android.graphics.pdf.PdfDocument`
- **Printing:** Native Android Bluetooth RFCOMM / ESC/POS command stream
- **Security:** AndroidX Biometric + Salted SHA-256 Hash

---

## 💻 Building from Source

### Prerequisites:
- Android Studio Ladybug / Meerkat / Koala or Antigravity IDE
- JDK 17 or JDK 21 (Android Studio JBR recommended)
- Android SDK 35 (compileSdk 35, minSdk 26)

### Gradle Commands:

**1. Run Unit Tests:**
```bash
./gradlew test
```

**2. Assemble Debug APK:**
```bash
./gradlew assembleDebug
```
The compiled APK will be located at `app/build/outputs/apk/debug/app-debug.apk`.

**3. Assemble Release APK:**
```bash
./gradlew assembleRelease
```

---

## 📂 Project Structure

```
smallshopos/
├── apk/
│   └── SyntaxaKhata-v1.0-debug.apk       # Pre-compiled ready-to-test APK
├── app/
│   ├── build.gradle.kts                  # App-level dependencies & plugins
│   ├── proguard-rules.pro                # Proguard rules
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/syntaxakhata/
│       │   │   ├── core/                 # CurrencyFormatter, DateUtils, AppLockManager
│       │   │   ├── data/                 # Room DB, DAOs, Entities, Repository impls
│       │   │   ├── domain/               # Domain Models, Repository interfaces
│       │   │   ├── feature/              # Compose UI: Home, Khata, Billing, Expenses, Reports, More
│       │   │   ├── printing/             # Bluetooth ESC/POS engine (58mm/80mm)
│       │   │   └── export/               # Native PDF & CSV generators, BackupManager
│       │   └── res/
│       │       ├── values/               # English strings, theme, colors
│       │       ├── values-hi/            # Hindi strings (हिन्दी)
│       │       └── xml/                  # FileProvider paths
│       └── test/                         # Unit tests (Room, Financial calculations, etc.)
├── gradle/wrapper/                       # Gradle wrapper binaries & properties
├── build.gradle.kts                      # Root Gradle build script
├── settings.gradle.kts                   # Project module settings
├── gradle.properties                     # JVM & AndroidX memory/daemon settings
└── README.md
```

---

## 🔒 Financial Integrity Guarantee

All money values in **Syntaxa Khata** are stored and calculated as 64-bit integer paise (1 Rupee = 100 Paise).
- `10050` = ₹100.50
- Zero floating-point rounding discrepancies.
- All balance updates are calculated dynamically or verified against ledger totals.
- Reversals create explicit offset entries (`REVERSAL` transaction type) linking back to the original transaction ID for clean, tamper-evident audit trails.
