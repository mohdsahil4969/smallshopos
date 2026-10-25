# COMPETITOR FEATURE GAP MATRIX

**Application:** Syntaxa Khata v1.0  
**Benchmarked Competitors:** Khatabook, Vyapar, OkCredit, myBillBook, and Syntaxa Shop OS  
**Evaluation Criteria:** Alignment with offline-first Indian kirana shopkeeper requirements, privacy, speed, and mathematical integrity.

---

## FEATURE COMPARISON MATRIX

| Feature | Competitor Evidence | Syntaxa Status (v1.0) | Shopkeeper Benefit | Complexity | Decision | Audit Status |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Digital Customer Udhaar Ledger** | Khatabook / OkCredit core feature. Tracks credit given and payments. | **Implemented & Verified** (Paise model, audit reversals, atomic Room queries). | Fast, zero-math tracking of customer balances while customers wait at counter. | Medium | **Retained** | Solid, zero floating-point error, authoritative ledger. |
| **Customer Payment Reminders** | Khatabook / OkCredit 1-tap WhatsApp message with balance and UPI ID. | **Implemented & Verified** (English & Hindi templates, direct WhatsApp intent, due date included). | Polite payment recovery without confrontation. | Low | **Retained & Enhanced** | Formatted due dates and overdue notices in reminder text. |
| **Customer Due Dates & Filters** | Khatabook / OkCredit feature allowing due date per customer with Overdue / Due Today filter. | **Implemented & Verified** (Room schema v3, DueDateFilter pills, customer profile date picker). | Immediate visibility into who promised to pay today or who is critically overdue. | Low | **Delivered** | Highly requested by small retailers; integrated cleanly with customer list. |
| **Quick & Itemized Billing** | Vyapar / myBillBook / Syntaxa Shop OS core feature. Fast POS billing with split payments. | **Implemented & Verified** (Quick total and itemized modes, discount, cash/UPI/credit split, atomic stock deduction). | Eliminates paper bills; issues instant professional receipts; maintains inventory. | Medium | **Retained & Hardened** | Fully functional with idempotency and transaction safety. |
| **Daily Cash Drawer Closing** | Syntaxa Shop OS `cash_book` & Vyapar daily cash reconciler. Reconciles physical cash against expected. | **Implemented & Verified** (Opening Cash + Cash Sales + Udhaar Cash - Expenses formula, direct-to-bank UPI isolation). | Detects register cash shortages/surpluses before locking up the shop at night. | Low | **Delivered** | Vital everyday discipline for retail store owners; prevents employee theft or counting errors. |
| **Optional Simple Inventory** | Vyapar, myBillBook, Syntaxa Shop OS item master with stock tracking and low-stock alerts. | **Implemented & Verified** (ProductEntity, InventoryScreen, Low Stock filter chip, atomic Room transaction deduction, cancel restoration). | Tracks stock for packaged items (oil, sugar, flour) without forcing manual entry for quick sales. | Medium | **Delivered** | Kept strictly optional so quick-billing never requires a product catalog. |
| **Supplier Purchases & Dues** | Khatabook supplier ledger, Vyapar purchase vouchers. Tracks distributor credit payables. | **Implemented & Verified** (SupplierEntity, SupplierTransactionEntity, SupplierScreen, isolated payables balance). | Keeps money owed to wholesale distributors separate from customer credit receivables. | Medium | **Delivered** | Prevents mixing payables with receivables, preserving clean books. |
| **Serverless One-Tap Update Engine** | Syntaxa Shop OS GitHub/CDN static manifest updater with checksum. | **Implemented & Verified** (Fail-closed signature/package check, HTTPS host allowlist, SHA-256 validation, AES-256-GCM safety backup). | Painless app updates without Google Play Store lock-in or proprietary update servers. | Medium | **Retained & Hardened** | Full integrity verification and pre-update snapshot recoverability check. |
| **Bluetooth Thermal Receipt Printing** | Vyapar, Syntaxa Shop OS direct ESC/POS printing (58mm/80mm). | **Implemented & Verified** (RFCOMM SPP native socket driver). | Prints instant physical paper receipts for customers who demand printed paper slips. | Low | **Retained** | Works offline with standard mini Bluetooth thermal printers. |
| **Encrypted Local Backup & Portability** | Khatabook (cloud only), Vyapar (local/drive backup). | **Implemented & Verified** (AES-256-GCM authenticated encryption, PBKDF2, full schema v3 coverage, v1/v2 backward compatibility, balance invariants). | Total ownership of business records; restores across phones without server dependencies. | Medium | **Retained & Hardened** | Deep post-restore invariant verification for Customers and Suppliers. |
| **Cloud Sync & Web Dashboard** | Khatabook, Vyapar, myBillBook multi-device cloud synchronization. | **Excluded by Design** | None for single-counter kirana shops; increases data leak risk and monthly fees. | High | **Rejected** | Violates offline-first privacy mandate; exposes financial records to cloud breaches. |
| **Automated Server Bulk SMS** | Khatabook / OkCredit automated background SMS gateway. | **Excluded by Design** | Automated reminders, but incurs SMS fees and invades customer privacy. | High | **Rejected** | Native Android Sharesheet / WhatsApp direct intent gives shopkeeper 100% control with zero cost. |
| **Complex Double-Entry Accounting & GST E-Way** | Vyapar, myBillBook full balance sheets, journal entries, and E-Way bills. | **Excluded by Design** | Overwhelms small kirana shopkeepers with confusing accounting jargon. | High | **Rejected** | Small shops need speed and simplicity, not certified accountant tooling. |
| **In-App Lending & Micro-Loans** | Khatabook merchant loan offers and third-party NBFC integration. | **Excluded by Design** | Predatory lending risk, high regulatory overhead. | High | **Rejected** | Strictly prohibited by product safety charter. |
| **AI Agents & LLM Voice Inference** | Syntaxa Shop OS on-device Qwen3 LLM & Whisper ASR. | **Excluded by Design** | Voice billing is exciting but adds heavy binary bloat (>100MB) unsuitable for entry-level phones. | High | **Rejected** | Keeps APK lean, fast, and responsive on modest Android phones. |

---

## SUMMARY OF VERIFIED ADVANTAGES OVER COMPETITORS

1. **100% Offline Independence**: Unlike Khatabook and OkCredit, which require internet connectivity and cloud account synchronization, Syntaxa Khata functions entirely locally on-device.
2. **Deterministic Integer Math**: Guaranteed zero floating-point accumulation errors by using 64-bit integer paise throughout all billing, transaction, and reporting layers.
3. **Data Privacy & Zero Cloud Lock-In**: Merchant financial data is encrypted on the device with AES-256-GCM and never uploaded to corporate telemetry or cloud databases.
4. **Serverless Update Resilience**: Independent of app store gatekeepers while maintaining military-grade cryptographic integrity (fail-closed signature verification, SHA-256 checksum, pre-update authenticated safety backup).
