# SMALL-SHOP FEATURE ROADMAP

**Product:** Syntaxa Khata  
**Target User:** Indian Kirana Stores, General Provision Merchants, and Small Retailers  
**Philosophy:** Offline-first, fast counter operation, exact integer math, zero cloud lock-in, zero unnecessary complexity.

---

## 1. STRATEGIC ARCHITECTURE GOALS

1. **Zero Data Loss Guarantee:** Every financial action (credit given, payment collected, bill created, inventory deduction) must be atomic and reversible with an immutable audit log.
2. **Speed at the Counter:** Standard transactions must take fewer than 3 taps.
3. **True Offline Independence:** No feature essential to everyday business operations requires an internet connection.
4. **Resilient Portability:** Backups must be self-contained, validated, encrypted with authenticated AES-256-GCM, and verifiable upon restore.

---

## 2. ROADMAP PHASES

### PHASE 1 (Current Release — v1.0.0 Production Hardening) — 100% COMPLETE
- [x] **Core Khata & Udhaar:** Integer paise math, customer ledger, credit & payment records, reversal entries.
- [x] **Sales & Billing:** Quick total mode, itemized mode, split payments (Cash/UPI/Credit), receipt generation.
- [x] **Receipt Sharing & ESC/POS Printing:** Native vector PDF receipts (58mm/80mm), WhatsApp sharing, direct Bluetooth printing.
- [x] **Local Security & App Lock:** Biometric unlock, salted PBKDF2 PIN hashing with persistent exponential lockout throttling.
- [x] **Encrypted Backup & Portability:** AES-256-GCM authenticated encryption, SHA-256 integrity, bounds validation.
- [x] **Comprehensive Schema v3 Backup & Restore:** Complete export and restore for all 10 entities (Customers, Transactions, Bills, Bill Items, Expenses, Shop Settings, Products, Suppliers, Supplier Transactions, Daily Cash Closings) with older v1/v2 format backward compatibility.
- [x] **Hardened Serverless Updates:** Dynamic `BuildConfig` binding, fail-closed package identity and signature continuity checks, `REQUEST_INSTALL_PACKAGES` permission management, strict AES-256-GCM pre-update safety backup with immediate recoverability verification.
- [x] **Post-Restore Invariant Checks:** Comprehensive validation of customer balances, supplier balances, bill counts, and transaction relationships before transaction commit.
- [x] **Customer Due Dates:** Optional payment due date on customer accounts and Udhaar entries with Overdue, Due Today, and Upcoming filters.
- [x] **Daily Cash Drawer Closing:** Formal reconciliation workflow with opening cash formula: $\text{Expected Cash} = \text{Opening Cash} + (\text{Cash Sales} + \text{Udhaar Cash Recovered}) - \text{Cash Expenses}$, comparing against `Counted Cash`.
- [x] **Optional Simple Inventory:** Lightweight product master (Item, unit, selling price, cost price, current stock, low stock alert) with atomic Room transaction deduction during billing, cancellation restoration, and dedicated InventoryScreen UI.
- [x] **Supplier Purchases & Dues:** Dedicated supplier directory, purchase/payment ledger, and isolated payables balance statement with dedicated SupplierScreen UI.

### PHASE 2 (Near-Term Upgrades — v1.1.0)
- **Barcode & QR Scanner via Camera:** Native Android CameraX barcode scanning for packaged goods to auto-populate the itemized cart.
- **Supplier Payment Reminders:** Outbound WhatsApp/SMS payment confirmations when settling distributor accounts.
- **Multi-Rate Discounting & Tax Presets:** Simple percentage-based tax tags (e.g., 0%, 5%, 12%, 18%) for shops that voluntarily register under GST composition scheme.
- **Periodic Scheduled Safety Backups:** Automated daily local database snapshot to app storage on device idle or app close.

### PHASE 3 (Deferred / Enterprise Only — v2.0+)
- **Multi-Counter LAN Sync:** P2P Wi-Fi local network synchronization between multiple billing phones inside the same shop without external cloud servers.
- **Desktop Companion App:** Local USB or LAN pairing with a Windows PC for large desktop receipt printing and bulk item CSV imports.

---

## 3. STRICT NON-GOALS (EXCLUDED PERMANENTLY)
- Cloud-hosted mandatory accounts or mandatory phone OTP logins.
- Predatory micro-lending or buy-now-pay-later (BNPL) third-party financial products.
- Monetization via third-party advertising SDKs or tracking pixels.
- Automated mass messaging or robocalling that annoys shop customers.
- Heavy AI/LLM models that slow down basic 3GB/4GB RAM Android devices.
