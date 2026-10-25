# Syntaxa Khata — Database Migration & Backup Integrity Report

## Executive Summary

Syntaxa Khata uses an offline-first **Room Database (`KhataDatabase`)** built on SQLite. All monetary figures are stored as 64-bit integer paise (`Long`) to prevent floating-point rounding errors. 

As the application evolved to support hardened security and small-shop retail workflows (Customer Due Dates, Simple Inventory with stable product IDs, Supplier Payables, and Daily Cash Drawer Reconciliations with Opening Cash), the database schema transitioned cleanly across three versions without financial data loss or destructive recreation:
- **Schema 1**: Initial baseline schema (Customers, Bills, Bill Items, Financial Transactions, Expenses, App Settings).
- **Schema 2**: Idempotency keys, PIN attempt throttling, encrypted backup metadata, and transaction reversal audit trails.
- **Schema 3**: Customer Due Dates (`dueDate`), Bill Item Product Linkage (`productId`), Simple Inventory (`products`), Supplier Accounts & Transactions (`suppliers`, `supplier_transactions`), and Daily Cash Closings with Opening Cash (`daily_cash_closings`).

Encrypted backups are protected using **AES-256-GCM** with authenticated associated data (AAD), bounded **PBKDF2WithHmacSHA256** key derivation, per-backup cryptographic salts, unique 96-bit IVs, and automated pre-restore schema and post-restore ledger invariant verification across both customer receivables and supplier payables.

In addition, automated pre-update safety snapshots have been upgraded from unencrypted plaintext to authenticated **AES-256-GCM** encrypted backups with mandatory immediate recoverability checks and a 3-version retention pruning policy.

---

## 1. Room Schema Evolution

### 1.1 Schema Overview & Version Map

| Schema Version | Core Additions | Destructive Rebuild? | Backward Compatible |
| :--- | :--- | :--- | :--- |
| **v1** | Customers, Bills, Items, Transactions, Expenses, Settings | Baseline | N/A |
| **v2** | Idempotency keys (`idempotencyKey`), Split payment columns, PIN lockout table, Reversals | No (`MIGRATION_1_2`) | Yes |
| **v3** | `dueDate` on `customers` & `financial_transactions`, `productId` on `bill_items`, `products` table, `suppliers` & `supplier_transactions`, `daily_cash_closings` with `openingCashPaise` | No (`MIGRATION_2_3`) | Yes |

```
   ┌─────────────────────────────────────────────────────────────┐
   │                       Schema v1                             │
   │  customers, bills, bill_items, financial_transactions,       │
   │  expenses, app_settings                                     │
   └──────────────────────────────┬──────────────────────────────┘
                                  │  MIGRATION_1_2
                                  ▼
   ┌─────────────────────────────────────────────────────────────┐
   │                       Schema v2                             │
   │  + bills.cashPaidPaise, upiPaidPaise, otherPaidPaise        │
   │  + bills.idempotencyKey, pin_attempts, indices              │
   └──────────────────────────────┬──────────────────────────────┘
                                  │  MIGRATION_2_3
                                  ▼
   ┌─────────────────────────────────────────────────────────────┐
   │                       Schema v3                             │
   │  + customers.dueDate (INTEGER NULL)                         │
   │  + financial_transactions.dueDate (INTEGER NULL)            │
   │  + bill_items.productId (INTEGER NULL)                      │
   │  + products (id, name, barcode, sellingPrice, stockQty...)  │
   │  + suppliers (id, name, phone, notes...)                    │
   │  + supplier_transactions (id, supplierId, amountPaise...)   │
   │  + daily_cash_closings (openingCash, sales, expected...)    │
   └─────────────────────────────────────────────────────────────┘
```

---

## 2. Migration DDL & Integration Testing

### 2.1 `MIGRATION_1_2` Execution Script

```kotlin
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE bills ADD COLUMN cashPaidPaise INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE bills ADD COLUMN upiPaidPaise INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE bills ADD COLUMN otherPaidPaise INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE bills ADD COLUMN idempotencyKey TEXT NOT NULL DEFAULT ''")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_bills_idempotencyKey ON bills(idempotencyKey)")

        db.execSQL("UPDATE bills SET cashPaidPaise = amountPaidPaise WHERE paymentMethod = 'CASH'")
        db.execSQL("UPDATE bills SET upiPaidPaise = amountPaidPaise WHERE paymentMethod = 'UPI'")
        db.execSQL("UPDATE bills SET otherPaidPaise = amountPaidPaise WHERE paymentMethod NOT IN ('CASH', 'UPI')")
    }
}
```

### 2.2 `MIGRATION_2_3` Execution Script

```kotlin
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 1. Due Dates & Bill Item Product Linkage
        db.execSQL("ALTER TABLE customers ADD COLUMN dueDate INTEGER DEFAULT NULL")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_customers_dueDate ON customers(dueDate)")
        db.execSQL("ALTER TABLE financial_transactions ADD COLUMN dueDate INTEGER DEFAULT NULL")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_financial_transactions_dueDate ON financial_transactions(dueDate)")
        db.execSQL("ALTER TABLE bill_items ADD COLUMN productId INTEGER DEFAULT NULL")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_bill_items_productId ON bill_items(productId)")

        // 2. Simple Inventory Products Table
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS products (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL,
                unit TEXT NOT NULL DEFAULT 'pcs',
                sellingPricePaise INTEGER NOT NULL DEFAULT 0,
                costPricePaise INTEGER NOT NULL DEFAULT 0,
                stockQuantity REAL NOT NULL DEFAULT 0.0,
                lowStockThreshold REAL NOT NULL DEFAULT 5.0,
                barcode TEXT NOT NULL DEFAULT '',
                category TEXT NOT NULL DEFAULT '',
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL,
                isArchived INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_products_name ON products(name)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_products_barcode ON products(barcode)")

        // 3. Suppliers Table
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS suppliers (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL,
                phone TEXT NOT NULL DEFAULT '',
                notes TEXT NOT NULL DEFAULT '',
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL,
                archivedAt INTEGER DEFAULT NULL
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_suppliers_name ON suppliers(name)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_suppliers_phone ON suppliers(phone)")

        // 4. Supplier Transactions Table
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS supplier_transactions (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                supplierId INTEGER NOT NULL,
                type TEXT NOT NULL,
                amountPaise INTEGER NOT NULL,
                paymentMethod TEXT NOT NULL,
                billInvoiceNumber TEXT NOT NULL DEFAULT '',
                notes TEXT NOT NULL DEFAULT '',
                occurredAt INTEGER NOT NULL,
                createdAt INTEGER NOT NULL,
                reversalOfTransactionId INTEGER DEFAULT NULL,
                status TEXT NOT NULL DEFAULT 'ACTIVE',
                idempotencyKey TEXT NOT NULL DEFAULT '',
                FOREIGN KEY(supplierId) REFERENCES suppliers(id) ON DELETE RESTRICT
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_supplier_transactions_supplierId ON supplier_transactions(supplierId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_supplier_transactions_occurredAt ON supplier_transactions(occurredAt)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_supplier_transactions_type ON supplier_transactions(type)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_supplier_transactions_idempotencyKey ON supplier_transactions(idempotencyKey)")

        // 5. Daily Cash Closing Drawer Table
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS daily_cash_closings (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                closingDateEpoch INTEGER NOT NULL,
                openingCashPaise INTEGER NOT NULL DEFAULT 0,
                cashSalesPaise INTEGER NOT NULL,
                cashKhataCollectedPaise INTEGER NOT NULL,
                cashExpensesPaise INTEGER NOT NULL,
                expectedCashPaise INTEGER NOT NULL,
                countedCashPaise INTEGER NOT NULL,
                differencePaise INTEGER NOT NULL,
                notes TEXT NOT NULL DEFAULT '',
                closedAt INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_daily_cash_closings_closingDateEpoch ON daily_cash_closings(closingDateEpoch)")
    }
}
```

Both migrations are tested on in-memory SQLite instances in `RoomMigrationIntegrityTest.kt` to ensure schema integrity and index creations execute without failure.

---

## 3. Encrypted Backup Architecture & Format Versioning

### 3.1 Format Specification (`BackupV2`)

Encrypted backups outputted by Syntaxa Khata use the `.skbak` binary-structured envelope.

```
+-------------------------------------------------------------------------+
|                              MAGIC HEADER                               |
|                     0x53 0x4B 0x48 0x41 ("SYNTAXA_KHATA_BACKUP")        |
+--------------------+--------------------+-------------------------------+
| Backup Version (v2)| App Version Code   | Schema Version (3)            |
+--------------------+--------------------+-------------------------------+
| Salt (16 bytes)    | IV / Nonce (12 B)  | PBKDF2 Iterations (100,000)   |
+--------------------+--------------------+-------------------------------+
| Payload Length     | AAD (App ID + Version + Schema + Timestamp)        |
+--------------------+--------------------+-------------------------------+
|                                                                         |
|                AES-256-GCM Encrypted Payload Body                       |
|        (JSON: Profile, Customers, Products, Suppliers, Closings...)     |
|                                                                         |
+-------------------------------------------------------------------------+
|                       128-bit GCM Auth Tag                              |
+-------------------------------------------------------------------------+
```

### 3.2 Automated Pre-Update Safety Backups

Before any update is installed, `AppUpdateService` executes `BackupManager.exportSafetyBackup`:
1. **Authenticated Envelope**: Uses `SYNTAXA_KHATA_SAFETY_BACKUP` format with AES-256-GCM.
2. **Device-Bound Key**: Uses a 256-bit CSPRNG safety key stored exclusively in private application preferences (`MODE_PRIVATE`), never logged or transmitted.
3. **Mandatory Recoverability Check**: Immediately runs `BackupManager.verifySafetyBackup` on the newly written file. If decryption or structural JSON validation fails, the backup file is deleted and the in-app update is immediately aborted.
4. **Retention Policy**: Only the 3 most recent safety backups are retained; older files are pruned automatically.

---

## 4. Post-Restore Structural & Financial Invariant Verification

When a backup is decrypted, `BackupManager` executes a multi-stage validation pipeline **before** committing data to the live SQLite database:

```
[Decrypted Payload JSON]
         │
         ▼
[Stage 1: JSON Schema & Field Type Check]
         │  (Validates JSON structure, required keys, positive IDs)
         ▼
[Stage 2: Target Schema Compatibility & Backward Compatibility]
         │  (Defaults missing products/suppliers to empty lists)
         ▼
[Stage 3: Customer Ledger Invariant Check]
         │  For every customer:
         │    Σ(UDHAAR_GIVEN) - Σ(PAYMENT_RECEIVED) == customer.balancePaise
         ▼
[Stage 4: Supplier Ledger Invariant Check]
         │  For every supplier:
         │    Σ(PURCHASE) - Σ(PAYMENT_MADE) == supplier.balancePaise
         ▼
[Stage 5: Bill Item Sum Invariant Check]
         │  For every bill:
         │    Σ(bill_items.lineTotalPaise) == bill.subtotalPaise
         ▼
[Stage 6: Atomic SQLite Transaction Commit]
         │  database.withTransaction { ... }
         ▼
[Restore Succeeded / Rollback on Any Invariant Discrepancy]
```

### 4.1 Invariant Rules Enforced

1. **Zero Orphaned Records**: Every `bill_item` must reference a valid `billId`. Every `financial_transaction` must reference an existing `customerId`. Every `supplier_transaction` must reference an existing `supplierId`.
2. **Customer Balance Invariant**:
   $$\text{Customer Balance} = \sum_{\text{Tx}} (\text{Udhaar Given}) - \sum_{\text{Tx}} (\text{Payments Received})$$
3. **Supplier Balance Invariant**:
   $$\text{Supplier Balance} = \sum_{\text{Tx}} (\text{Purchases}) - \sum_{\text{Tx}} (\text{Payments Made})$$
4. **Bill Arithmetic Invariant**:
   $$\sum_{i=1}^n (\text{quantity}_i \times \text{unitPricePaise}_i) = \text{Bill.subtotalPaise}$$
   $$\text{Total Bill} = \max(0, \text{Subtotal} - \text{Discount})$$
5. **Daily Cash Drawer Invariant**:
   $$\text{Expected Cash} = \text{Opening Cash} + (\text{Cash Sales} + \text{Udhaar Cash Collected}) - \text{Cash Expenses}$$
   $$\text{Difference} = \text{Counted Cash} - \text{Expected Cash}$$
