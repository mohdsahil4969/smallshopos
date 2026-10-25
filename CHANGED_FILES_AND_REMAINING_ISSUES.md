# Syntaxa Khata — Changed Files & Remaining Issues Report

## Executive Summary

This document enumerates all modified, newly created, and audited files in the **Syntaxa Khata** repository during the security hardening, one-tap update system overhaul, Room schema v3 migration, atomic inventory deduction, and small-shop feature upgrades. It also documents deferred items, known limitations, and release blockers.

All **67 unit tests** across all 8 test suites pass with **0 failures and 0 errors**.

---

## 1. Inventory of Changed & Newly Created Files

### 1.1 Build & Configuration Files
- [`app/build.gradle.kts`](file:///c:/Users/sahil/OneDrive/Desktop/smallshopos/app/build.gradle.kts)
  - Enabled `buildConfig = true` under `buildFeatures` to allow authoritative package and version inspection in production code (`BuildConfig.VERSION_CODE`, `BuildConfig.VERSION_NAME`, `BuildConfig.APPLICATION_ID`).

### 1.2 Update System Hardening
- [`app/src/main/java/com/syntaxa/khata/feature/update/AppUpdateModels.kt`](file:///c:/Users/sahil/OneDrive/Desktop/smallshopos/app/src/main/java/com/syntaxa/khata/feature/update/AppUpdateModels.kt)
  - Added strict JSON validation: required fields, non-empty HTTPS URLs, valid 64-char hex SHA-256 digests, size bounds (max 60 MB), non-negative build numbers.
  - Added `UpdateStatus.PERMISSION_REQUIRED` state for Android unknown app installation permissions.
- [`app/src/main/java/com/syntaxa/khata/feature/update/AppUpdateService.kt`](file:///c:/Users/sahil/OneDrive/Desktop/smallshopos/app/src/main/java/com/syntaxa/khata/feature/update/AppUpdateService.kt)
  - Tied update comparison directly to `BuildConfig.VERSION_CODE` rather than fragile string parsing.
  - Enforced HTTPS-only manifest and APK downloads with an explicit whitelist (`ALLOWED_MANIFEST_HOSTS`).
  - Added manual redirect inspection to prevent scheme downgrading or off-host redirection attacks.
  - Implemented 60MB hard download cap to prevent device storage exhaustion.
  - Added cryptographic SHA-256 verification of the downloaded binary.
  - Added **fail-closed pre-install APK verification** using Android `PackageManager.getPackageArchiveInfo()`: validates package ID (`com.syntaxa.khata`) and signing certificate continuity against the running app before triggering package installer. Fails closed with descriptive security error if signatures cannot be verified.
  - Added `customApkVerifier` property to enable isolated unit testing of package and certificate verification without full OS mocks.
  - Integrated automated **AES-256-GCM authenticated pre-update safety backup** with immediate recoverability verification check (`verifySafetyBackup`) before the update installation proceeds, complete with 3-version retention prune.
- [`app/src/main/java/com/syntaxa/khata/feature/update/AppUpdateViewModel.kt`](file:///c:/Users/sahil/OneDrive/Desktop/smallshopos/app/src/main/java/com/syntaxa/khata/feature/update/AppUpdateViewModel.kt)
  - Added permission request and install continuation methods for Compose UI.
- [`app/src/main/java/com/syntaxa/khata/feature/update/AppUpdateDialog.kt`](file:///c:/Users/sahil/OneDrive/Desktop/smallshopos/app/src/main/java/com/syntaxa/khata/feature/update/AppUpdateDialog.kt)
  - Replaced misleading "100% Data Safe" claims with accurate technical notices.
  - Handled `PERMISSION_REQUIRED` with a dedicated prompt and direct navigation to Android settings.
- [`app/src/main/res/values/strings.xml`](file:///c:/Users/sahil/OneDrive/Desktop/smallshopos/app/src/main/res/values/strings.xml) & [`values-hi/strings.xml`](file:///c:/Users/sahil/OneDrive/Desktop/smallshopos/app/src/main/res/values-hi/strings.xml)
  - Updated localization strings for backup snapshots, inventory, suppliers, cash closings, and update notices.

### 1.3 UI & Deprecation Modernization
- [`app/src/main/java/com/syntaxa/khata/core/ui/navigation/KhataNavigation.kt`](file:///c:/Users/sahil/OneDrive/Desktop/smallshopos/app/src/main/java/com/syntaxa/khata/core/ui/navigation/KhataNavigation.kt)
  - Migrated deprecated icons to `Icons.AutoMirrored.Filled.*`.
- [`app/src/main/java/com/syntaxa/khata/feature/home/HomeScreen.kt`](file:///c:/Users/sahil/OneDrive/Desktop/smallshopos/app/src/main/java/com/syntaxa/khata/feature/home/HomeScreen.kt)
  - Migrated `Icons.Filled.ArrowForward` and `Icons.Filled.ExitToApp` to auto-mirrored variants.
- [`app/src/main/java/com/syntaxa/khata/feature/records/RecordsScreen.kt`](file:///c:/Users/sahil/OneDrive/Desktop/smallshopos/app/src/main/java/com/syntaxa/khata/feature/records/RecordsScreen.kt)
  - Upgraded to `ScrollableTabRow` with 5 dedicated tabs: Bills, Expenses, Stock (Inventory), Suppliers, Reports.
- [`app/src/main/java/com/syntaxa/khata/feature/customers/CustomerDialogs.kt`](file:///c:/Users/sahil/OneDrive/Desktop/smallshopos/app/src/main/java/com/syntaxa/khata/feature/customers/CustomerDialogs.kt)
  - Fixed deprecated `Modifier.menuAnchor()` by supplying `MenuAnchorType.PrimaryNotEditable`.
- [`app/src/main/java/com/syntaxa/khata/MainActivity.kt`](file:///c:/Users/sahil/OneDrive/Desktop/smallshopos/app/src/main/java/com/syntaxa/khata/MainActivity.kt)
  - Injected `InventoryViewModel` and `SupplierViewModel` into `RecordsScreen`.

### 1.4 Database Schema v3 Entities & DAOs
- [`app/src/main/java/com/syntaxa/khata/data/local/entity/CustomerEntity.kt`](file:///c:/Users/sahil/OneDrive/Desktop/smallshopos/app/src/main/java/com/syntaxa/khata/data/local/entity/CustomerEntity.kt)
  - Added nullable `dueDate: Long? = null` column.
- [`app/src/main/java/com/syntaxa/khata/data/local/entity/FinancialTransactionEntity.kt`](file:///c:/Users/sahil/OneDrive/Desktop/smallshopos/app/src/main/java/com/syntaxa/khata/data/local/entity/FinancialTransactionEntity.kt)
  - Added nullable `dueDate: Long? = null` column.
- [`app/src/main/java/com/syntaxa/khata/data/local/entity/BillItemEntity.kt`](file:///c:/Users/sahil/OneDrive/Desktop/smallshopos/app/src/main/java/com/syntaxa/khata/data/local/entity/BillItemEntity.kt)
  - Added nullable `productId: Long? = null` with index `@Index(value = ["productId"])` for stable stock deductions.
- [`app/src/main/java/com/syntaxa/khata/data/local/entity/ProductEntity.kt`](file:///c:/Users/sahil/OneDrive/Desktop/smallshopos/app/src/main/java/com/syntaxa/khata/data/local/entity/ProductEntity.kt) *(New)*
  - Simple inventory entity for retail stock tracking (`salePricePaise`, `purchasePricePaise`, `stockQuantity`, `lowStockThreshold`, `barcode`, `isArchived`).
- [`app/src/main/java/com/syntaxa/khata/data/local/entity/SupplierEntity.kt`](file:///c:/Users/sahil/OneDrive/Desktop/smallshopos/app/src/main/java/com/syntaxa/khata/data/local/entity/SupplierEntity.kt) *(New)*
  - Wholesaler/vendor entity with isolated payables balance (`balancePaise`).
- [`app/src/main/java/com/syntaxa/khata/data/local/entity/SupplierTransactionEntity.kt`](file:///c:/Users/sahil/OneDrive/Desktop/smallshopos/app/src/main/java/com/syntaxa/khata/data/local/entity/SupplierTransactionEntity.kt) *(New)*
  - Supplier purchase and payment ledger entries (`PURCHASE_CREDIT`, `PAYMENT_MADE`, `PURCHASE_CASH`).
- [`app/src/main/java/com/syntaxa/khata/data/local/entity/DailyCashClosingEntity.kt`](file:///c:/Users/sahil/OneDrive/Desktop/smallshopos/app/src/main/java/com/syntaxa/khata/data/local/entity/DailyCashClosingEntity.kt) *(New)*
  - Daily physical cash drawer closing record with opening cash, expected cash, counted cash, difference, and notes.
- [`app/src/main/java/com/syntaxa/khata/data/local/dao/CustomerDao.kt`](file:///c:/Users/sahil/OneDrive/Desktop/smallshopos/app/src/main/java/com/syntaxa/khata/data/local/dao/CustomerDao.kt)
  - Updated queries to project `dueDate` and added `updateCustomerDueDate()`.
- [`app/src/main/java/com/syntaxa/khata/data/local/dao/ProductDao.kt`](file:///c:/Users/sahil/OneDrive/Desktop/smallshopos/app/src/main/java/com/syntaxa/khata/data/local/dao/ProductDao.kt) *(New)*
  - Product catalog and inventory operations with atomic stock reduction returning affected rows.
- [`app/src/main/java/com/syntaxa/khata/data/local/dao/SupplierDao.kt`](file:///c:/Users/sahil/OneDrive/Desktop/smallshopos/app/src/main/java/com/syntaxa/khata/data/local/dao/SupplierDao.kt) *(New)*
  - Supplier CRUD and transactional balance mutations.
- [`app/src/main/java/com/syntaxa/khata/data/local/dao/DailyCashClosingDao.kt`](file:///c:/Users/sahil/OneDrive/Desktop/smallshopos/app/src/main/java/com/syntaxa/khata/data/local/dao/DailyCashClosingDao.kt) *(New)*
  - Daily closing persistence, historical retrieval, and `restoreAllClosings()`.
- [`app/src/main/java/com/syntaxa/khata/data/local/KhataDatabase.kt`](file:///c:/Users/sahil/OneDrive/Desktop/smallshopos/app/src/main/java/com/syntaxa/khata/data/local/KhataDatabase.kt)
  - Incremented version from 2 to 3.
  - Implemented `MIGRATION_2_3` with `bill_items ADD COLUMN productId` and `daily_cash_closings` table with `openingCashPaise`.
  - Registered new DAOs and entities.

### 1.5 Repositories & Dependency Injection
- [`app/src/main/java/com/syntaxa/khata/data/repository/CustomerRepository.kt`](file:///c:/Users/sahil/OneDrive/Desktop/smallshopos/app/src/main/java/com/syntaxa/khata/data/repository/CustomerRepository.kt)
  - Added `updateDueDate(customerId, dueDate)`.
- [`app/src/main/java/com/syntaxa/khata/data/repository/BillingRepository.kt`](file:///c:/Users/sahil/OneDrive/Desktop/smallshopos/app/src/main/java/com/syntaxa/khata/data/repository/BillingRepository.kt)
  - Enforced atomic stock deduction inside `createBill()` Room transaction boundary.
  - Rejects bill creation if inventory is insufficient.
  - Supports quick amount-based bills without a catalog (`productId == null`).
  - Implemented inventory restoration on `cancelBill()`.
- [`app/src/main/java/com/syntaxa/khata/data/repository/InventoryRepository.kt`](file:///c:/Users/sahil/OneDrive/Desktop/smallshopos/app/src/main/java/com/syntaxa/khata/data/repository/InventoryRepository.kt) *(New)*
  - Repository for stock tracking, adjust stock with reasons, and stock deduction.
- [`app/src/main/java/com/syntaxa/khata/data/repository/SupplierRepository.kt`](file:///c:/Users/sahil/OneDrive/Desktop/smallshopos/app/src/main/java/com/syntaxa/khata/data/repository/SupplierRepository.kt) *(New)*
  - Repository for supplier accounts, purchases, payments, and payables ledger.
- [`app/src/main/java/com/syntaxa/khata/data/repository/DailyClosingRepository.kt`](file:///c:/Users/sahil/OneDrive/Desktop/smallshopos/app/src/main/java/com/syntaxa/khata/data/repository/DailyClosingRepository.kt) *(New)*
  - Cash drawer reconciliation with opening cash formula:
    $$\text{Expected Cash} = \text{Opening Cash} + (\text{Cash Sales} + \text{Udhaar Cash Collected}) - \text{Cash Expenses}$$
- [`app/src/main/java/com/syntaxa/khata/KhataAppContainer.kt`](file:///c:/Users/sahil/OneDrive/Desktop/smallshopos/app/src/main/java/com/syntaxa/khata/KhataAppContainer.kt)
  - Instantiated and injected `InventoryRepository`, `SupplierRepository`, `DailyClosingRepository`, and passed `ProductDao` to `BillingRepository`.

### 1.6 Feature Logic & UI Workflows
- [`app/src/main/java/com/syntaxa/khata/core/preferences/UserGuidePreferences.kt`](file:///c:/Users/sahil/OneDrive/Desktop/smallshopos/app/src/main/java/com/syntaxa/khata/core/preferences/UserGuidePreferences.kt) *(New)*
  - Manages persistent flags for first-time install walkthrough gating and replay triggers.
- [`app/src/main/java/com/syntaxa/khata/feature/guide/OnboardingWalkthroughScreen.kt`](file:///c:/Users/sahil/OneDrive/Desktop/smallshopos/app/src/main/java/com/syntaxa/khata/feature/guide/OnboardingWalkthroughScreen.kt) *(New)*
  - 7-step interactive first-time walkthrough explaining each core screen: Offline First, Home Actions, Customer Khata & Due Dates, Smart Billing & Inventory, Wholesale Suppliers, Daily Cash Closing, and App Security/Backups. Includes English/Hindi quick toggle, step indicators, and "Get Started" completion trigger.
- [`app/src/main/java/com/syntaxa/khata/feature/guide/UserGuideScreen.kt`](file:///c:/Users/sahil/OneDrive/Desktop/smallshopos/app/src/main/java/com/syntaxa/khata/feature/guide/UserGuideScreen.kt) *(New)*
  - Comprehensive, searchable in-app user guide manual with expandable categories, step-by-step procedures, pro tips, and FAQs. Accessible anytime from Home Screen and Settings.
- [`app/src/main/java/com/syntaxa/khata/feature/inventory/InventoryScreen.kt`](file:///c:/Users/sahil/OneDrive/Desktop/smallshopos/app/src/main/java/com/syntaxa/khata/feature/inventory/InventoryScreen.kt) *(New)* & [`InventoryViewModel.kt`](file:///c:/Users/sahil/OneDrive/Desktop/smallshopos/app/src/main/java/com/syntaxa/khata/feature/inventory/InventoryViewModel.kt) *(New)*
  - Minimal usable offline screen: Add/Edit/Archive products, search query, Low Stock filter chip, and Stock Adjustment dialog with reason logging.
- [`app/src/main/java/com/syntaxa/khata/feature/suppliers/SupplierScreen.kt`](file:///c:/Users/sahil/OneDrive/Desktop/smallshopos/app/src/main/java/com/syntaxa/khata/feature/suppliers/SupplierScreen.kt) *(New)* & [`SupplierViewModel.kt`](file:///c:/Users/sahil/OneDrive/Desktop/smallshopos/app/src/main/java/com/syntaxa/khata/feature/suppliers/SupplierViewModel.kt) *(New)*
  - Minimal usable offline screen: Add/Edit suppliers, Record Purchase (Credit/Cash), Record Payment, and view supplier transaction history / balance statement.
- [`app/src/main/java/com/syntaxa/khata/feature/customers/CustomerViewModel.kt`](file:///c:/Users/sahil/OneDrive/Desktop/smallshopos/app/src/main/java/com/syntaxa/khata/feature/customers/CustomerViewModel.kt)
  - Added `DueDateFilter` state (`ALL`, `OVERDUE`, `DUE_TODAY`, `UPCOMING`).
  - Added `updateCustomerDueDate()` handler.
- [`app/src/main/java/com/syntaxa/khata/feature/customers/CustomerListScreen.kt`](file:///c:/Users/sahil/OneDrive/Desktop/smallshopos/app/src/main/java/com/syntaxa/khata/feature/customers/CustomerListScreen.kt)
  - Added horizontal FilterChip row for Due Dates.
- [`app/src/main/java/com/syntaxa/khata/feature/customers/CustomerProfileScreen.kt`](file:///c:/Users/sahil/OneDrive/Desktop/smallshopos/app/src/main/java/com/syntaxa/khata/feature/customers/CustomerProfileScreen.kt)
  - Added due date banner and interactive date picker dialog for setting/updating customer collection targets.
- [`app/src/main/java/com/syntaxa/khata/feature/billing/BillingViewModel.kt`](file:///c:/Users/sahil/OneDrive/Desktop/smallshopos/app/src/main/java/com/syntaxa/khata/feature/billing/BillingViewModel.kt) & [`BillingScreen.kt`](file:///c:/Users/sahil/OneDrive/Desktop/smallshopos/app/src/main/java/com/syntaxa/khata/feature/billing/BillingScreen.kt)
  - Maintained `productId` on draft bill items and delegated atomic stock check/deduction entirely to transactional repository.
- [`app/src/main/java/com/syntaxa/khata/feature/reports/DailyCashClosingDialog.kt`](file:///c:/Users/sahil/OneDrive/Desktop/smallshopos/app/src/main/java/com/syntaxa/khata/feature/reports/DailyCashClosingDialog.kt)
  - Added Opening Cash input, step-by-step formula breakdown, and dedicated direct-to-bank UPI info card.
- [`app/src/main/java/com/syntaxa/khata/feature/reports/ReportsViewModel.kt`](file:///c:/Users/sahil/OneDrive/Desktop/smallshopos/app/src/main/java/com/syntaxa/khata/feature/reports/ReportsViewModel.kt) & [`ReportsScreen.kt`](file:///c:/Users/sahil/OneDrive/Desktop/smallshopos/app/src/main/java/com/syntaxa/khata/feature/reports/ReportsScreen.kt)
  - Added cash drawer closing state flow, opening cash integration, and dialog trigger.
- [`app/src/main/java/com/syntaxa/khata/feature/backup/BackupManager.kt`](file:///c:/Users/sahil/OneDrive/Desktop/smallshopos/app/src/main/java/com/syntaxa/khata/feature/backup/BackupManager.kt)
  - Extended backup JSON and encrypted payload to fully include Products, Suppliers, Supplier Transactions, and Daily Cash Closings.
  - Implemented backward compatibility for older v1 and v2 backups.
  - Added post-restore invariant checking for both Customer balances and Supplier balances.
  - Implemented `exportSafetyBackup()` and `verifySafetyBackup()` using AES-256-GCM.

### 1.7 Customer Ledger & Purchase History Module
- [`app/src/main/java/com/syntaxa/khata/domain/model/Models.kt`](file:///c:/Users/sahil/OneDrive/Desktop/smallshopos/app/src/main/java/com/syntaxa/khata/domain/model/Models.kt)
  - Added `CustomerFinancialSummary`, `CustomerLedgerEntry` sealed hierarchy (`BillPurchase`, `Payment`, `DirectUdhaar`, `Reversal`), and `LedgerDateFilter` enum (`ALL_TIME`, `LAST_30_DAYS`, `LAST_3_MONTHS`, `LAST_YEAR`, `CUSTOM`).
  - Added opening balance, closing balance, and date range parameters to `CustomerStatement`.
- [`app/src/main/java/com/syntaxa/khata/data/local/dao/BillDao.kt`](file:///c:/Users/sahil/OneDrive/Desktop/smallshopos/app/src/main/java/com/syntaxa/khata/data/local/dao/BillDao.kt) & [`BillingRepository.kt`](file:///c:/Users/sahil/OneDrive/Desktop/smallshopos/app/src/main/java/com/syntaxa/khata/data/repository/BillingRepository.kt)
  - Added `observeBillsForCustomer`, `getBillsForCustomer`, `observeBillsForCustomerByDateRange`, and `getBillsForCustomerByDateRange`.
- [`app/src/main/java/com/syntaxa/khata/data/repository/FinancialRepository.kt`](file:///c:/Users/sahil/OneDrive/Desktop/smallshopos/app/src/main/java/com/syntaxa/khata/data/repository/FinancialRepository.kt)
  - Extended `getCustomerStatement` with date filtering, opening balance calculation, period Udhaar/Paid accumulation, and closing balance computation.
- [`app/src/main/java/com/syntaxa/khata/feature/customers/CustomerViewModel.kt`](file:///c:/Users/sahil/OneDrive/Desktop/smallshopos/app/src/main/java/com/syntaxa/khata/feature/customers/CustomerViewModel.kt)
  - Integrated `customerLedgerEntries` flow, `customerFinancialSummary` flow with 4-figure accounting metrics, date filtering, and helper functions for bill PDF generation, thermal ESC/POS printing, and date-range statements.
- [`app/src/main/java/com/syntaxa/khata/feature/customers/CustomerProfileScreen.kt`](file:///c:/Users/sahil/OneDrive/Desktop/smallshopos/app/src/main/java/com/syntaxa/khata/feature/customers/CustomerProfileScreen.kt)
  - Completely redesigned screen inspired by Khatabook:
    - Customer header with phone, notes, promised due date picker, and prominent balance indicator.
    - 4-figure summary grid (Total Purchases, Payments Received, Current Outstanding Balance, Transaction Count) with interactive date filter chips.
    - Single chronological timeline with expandable bills revealing itemized line totals (`Paneer — 15 kg × ₹100 = ₹1,500`), discounts, and subtotal.
    - Multi-action bill strip: Full Bill Dialog, PDF Share, SMS, WhatsApp, and Bluetooth Thermal Printing (with friendly printer pairing guidance dialog).
    - Multi-channel customer action strip: WhatsApp statement, SMS (`smsto:`), Email (`mailto:` with PDF attached), PDF statement, and payment reminders.
- [`app/src/main/java/com/syntaxa/khata/feature/receipts/ShareHelper.kt`](file:///c:/Users/sahil/OneDrive/Desktop/smallshopos/app/src/main/java/com/syntaxa/khata/feature/receipts/ShareHelper.kt)
  - Added native SMS intent (`smsto:`) and Email intent (`mailto:`, `ACTION_SEND` with PDF attachment).
- [`app/src/main/java/com/syntaxa/khata/feature/receipts/PdfReceiptGenerator.kt`](file:///c:/Users/sahil/OneDrive/Desktop/smallshopos/app/src/main/java/com/syntaxa/khata/feature/receipts/PdfReceiptGenerator.kt)
  - Enhanced statement PDF layout with opening balance row, filtered date range period, and closing balance summary.
- [`app/src/test/java/com/syntaxa/khata/CustomerLedgerTest.kt`](file:///c:/Users/sahil/OneDrive/Desktop/smallshopos/app/src/test/java/com/syntaxa/khata/CustomerLedgerTest.kt) *(New, 15 tests)*
  - 15 comprehensive scenario tests verifying paid bills, credit bills, partial bills, manual credit, payments, reversals, mathematical consistency, date filter transitions, cancelled bills, zero double-counting, PDF/thermal outputs, SMS/WhatsApp texts, zero transactions, and 64-bit integer overflow safety.

### 1.8 Test Suites
- [`app/src/test/java/com/syntaxa/khata/CustomerLedgerTest.kt`](file:///c:/Users/sahil/OneDrive/Desktop/smallshopos/app/src/test/java/com/syntaxa/khata/CustomerLedgerTest.kt) (15 tests) *(New)*
  - Full suite covering all 15 customer ledger and purchase history scenarios.
- [`app/src/test/java/com/syntaxa/khata/ComprehensiveFinancialScenariosTest.kt`](file:///c:/Users/sahil/OneDrive/Desktop/smallshopos/app/src/test/java/com/syntaxa/khata/ComprehensiveFinancialScenariosTest.kt) (11 tests)
  - Tests 6 real-world kirana scenarios + paise boundary/precision test + isolated ledger tests.
- [`app/src/test/java/com/syntaxa/khata/AppUpdateSystemTest.kt`](file:///c:/Users/sahil/OneDrive/Desktop/smallshopos/app/src/test/java/com/syntaxa/khata/AppUpdateSystemTest.kt) (10 tests)
  - Added tests for fail-closed package & certificate verification and signature matching.
- [`app/src/test/java/com/syntaxa/khata/SmallShopFeaturesTest.kt`](file:///c:/Users/sahil/OneDrive/Desktop/smallshopos/app/src/test/java/com/syntaxa/khata/SmallShopFeaturesTest.kt) (5 tests)
  - Tests for Customer Due Dates, Daily Cash Drawer Reconciliation, Atomic Billing Inventory Deduction, Insufficient Stock Rollback, Cancellation Stock Restoration, and Supplier Payables isolation.
- [`app/src/test/java/com/syntaxa/khata/BackupAndSecurityVerificationTest.kt`](file:///c:/Users/sahil/OneDrive/Desktop/smallshopos/app/src/test/java/com/syntaxa/khata/BackupAndSecurityVerificationTest.kt) (18 tests)
  - Added tests for full entity backup/restore completeness, v1/v2 backward compatibility, and pre-update safety backup integrity verification.
- [`app/src/test/java/com/syntaxa/khata/RoomMigrationIntegrityTest.kt`](file:///c:/Users/sahil/OneDrive/Desktop/smallshopos/app/src/test/java/com/syntaxa/khata/RoomMigrationIntegrityTest.kt) (2 tests) *(New)*
  - Validates SQL DDL statements for `MIGRATION_1_2` and `MIGRATION_2_3`.
- [`app/src/test/java/com/syntaxa/khata/FinancialLedgerTest.kt`](file:///c:/Users/sahil/OneDrive/Desktop/smallshopos/app/src/test/java/com/syntaxa/khata/FinancialLedgerTest.kt) (3 tests)
  - Core Room DAO ledger aggregation queries and reversals.
- [`app/src/test/java/com/syntaxa/khata/CurrencyFormatterTest.kt`](file:///c:/Users/sahil/OneDrive/Desktop/smallshopos/app/src/test/java/com/syntaxa/khata/CurrencyFormatterTest.kt) (2 tests)
  - Indian numbering system and negative paise representations.

---

## 2. Deferred Items & Future Enhancements

The following capabilities were analyzed and intentionally deferred for subsequent minor updates (v1.1+):

1. **Camera/Barcode Scanner UI**:
   - *Status*: Deferred.
   - *Reason*: `ProductEntity` includes barcode field support, but bundling ML Kit or ZXing adds ~10MB to APK size. Kept simple for kirana store manual code / search typing.
2. **Thermal Bluetooth ESC/POS Print Custom Layout Designer**:
   - *Status*: Deferred.
   - *Reason*: Existing 58mm/80mm ESC/POS raw byte printer handles standard kirana receipts reliably. Custom drag-and-drop designer is unnecessary overhead for kirana shop owners.
3. **Multi-Store / Multi-Device Synchronization**:
   - *Status*: Excluded by product design.
   - *Reason*: Non-negotiable constraint: Syntaxa Khata is strictly offline-first. Multi-master cloud synchronization introduces conflict resolution complexities inappropriate for solo storekeepers.

---

## 3. Release Blockers Assessment

| Blocker Category | Status | Notes |
| :--- | :--- | :--- |
| **Financial Integrity** | **CLEAR** | All 6 financial audit scenarios pass with integer paise precision; atomic billing stock checks guarantee consistency; reversals are immutable. |
| **Database Migrations** | **CLEAR** | Schema v1 -> v2 -> v3 migrations are non-destructive and verified by DDL execution test suite. |
| **Update Security** | **CLEAR** | Fail-closed signature check, strict SHA-256 verification, HTTPS host whitelisting, size limits, and AES-256-GCM safety backup before update. |
| **Backup Completeness** | **CLEAR** | All 10 entities supported; backward compatible with v1/v2 backups; post-restore customer and supplier balance invariant checks pass. |
| **Inventory & Supplier UI** | **CLEAR** | Complete minimal usable offline screens wired into RecordsScreen tabs (Bills, Expenses, Stock, Suppliers, Reports). |
| **Deprecation Warnings** | **CLEAR** | Compose deprecated icons and menu anchors resolved. |
