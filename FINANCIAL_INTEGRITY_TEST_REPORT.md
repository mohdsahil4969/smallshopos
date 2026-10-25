# FINANCIAL INTEGRITY TEST REPORT

**Application:** Syntaxa Khata v1.0 (`com.syntaxa.khata`)  
**Scope:** Room Database Entities, DAOs, Repositories, Math Model, Ledger Operations, Inventory Atomicity, Supplier Accounts, and Daily Reports  
**Auditor:** Senior Financial Systems Engineer & Android Security Auditor  
**Date:** 2026-10-24

---

## 1. MATHEMATICAL ARCHITECTURE & INVARIANTS

### 1.1 Strict 64-Bit Integer Paise Representation
- In Syntaxa Khata, all monetary representations (`amountPaise`, `subtotalPaise`, `discountPaise`, `totalPaise`, `cashPaidPaise`, `upiPaidPaise`, `otherPaidPaise`, `balancePaise`, `openingCashPaise`, `countedCashPaise`, `expectedCashPaise`) are stored in SQLite and Kotlin as `Long` (64-bit signed integers).
- 1 Rupee = 100 Paise (e.g. ₹1,250.75 is represented strictly as `125075L`).
- Binary floating-point types (`Double`, `Float`) are strictly prohibited in the financial domain and repository layers, eliminating IEEE 754 rounding errors, precision drift, and fraction truncation.
- Conversion to display strings is isolated exclusively to `CurrencyFormatter.formatPaise(paise: Long): String`.

### 1.2 Atomic Inventory Deductions & Cancellation Reversals
- **Stable Product Linkage:** Bill items reference items via a stable foreign key `productId: Long?`. Catalog products are no longer matched by volatile text descriptions.
- **Transactional Stock Boundary:** Stock deductions are executed inside the exact same Room database transaction boundary as the bill insertion and Udhaar ledger posting.
- **Fail-Safe Stock Checks:** If available stock is less than the requested quantity, or if a product is archived, `BillingRepository.createBill()` throws an `IllegalStateException` prior to insertion, and SQLite rolls back the entire operation. No orphaned bills or phantom credit entries can be generated.
- **Cancellation Restorations:** Invoking `cancelBill(billId)` marks the bill `CANCELLED`, reverses any active Udhaar transaction in the customer's ledger, and restores deducted product stock quantities atomically.
- **Quick Bill Compatibility:** Quick amount-based bills without a product catalog (`productId = null`) bypass inventory validation and succeed directly.

### 1.3 Strict Supplier Accounts & Payables Segregation
- **Receivables vs Payables:** Supplier accounts and debts (`supplier_transactions`) are stored in isolated tables with their own transaction history.
- **Zero Contamination:** Vendor credit purchases and debt settlements never mix with customer receivables (`totalMarketUdhaarOwedPaise`) or daily retail sales revenue.

### 1.4 Daily Cash Drawer Reconciliation Formula
- Physical drawer cash reconciliation incorporates Opening Cash, Cash Sales, Old Udhaar Cash Collected, and Cash Expenses:
  $$\text{Expected Cash} = \text{Opening Cash} + (\text{Cash Sales} + \text{Udhaar Cash Collected}) - \text{Cash Expenses}$$
  $$\text{Difference} = \text{Counted Cash} - \text{Expected Cash} \quad (\text{Surplus } > 0, \text{Shortage } < 0, \text{Balanced } = 0)$$
- Direct UPI payments go directly into the merchant's bank account and are excluded from the physical cash drawer to prevent double counting.

---

## 2. VERIFIED EXECUTION OF TEST SCENARIOS

| Scenario # | Description | Execution Details & Test Reference | Outcome |
| :--- | :--- | :--- | :--- |
| **1** | Create a ₹500 credit sale | Bill created with `totalPaise = 50000L`, `amountPaidPaise = 0L`. Linked customer ledger entry `TYPE_UDHAAR_GIVEN` generated atomically. | **PASS** |
| **2** | Record a ₹200 payment | Customer payment recorded with `amountPaise = 20000L` (`METHOD_CASH`). | **PASS** |
| **3** | Verify ₹300 remains outstanding | Statement balance calculated: `50000 - 20000 = 30000L` (₹300.00). | **PASS** |
| **4** | Record remaining ₹300 later | Second payment recorded with `amountPaise = 30000L` (`METHOD_UPI`). | **PASS** |
| **5** | Verify outstanding balance becomes zero | Statement balance evaluated: `50000 - 20000 - 30000 = 0L` (₹0.00). | **PASS** |
| **6** | Collections reflect payments without repeating sale | `getDailySummary()` evaluated: `totalSalesPaise = 50000L` (₹500), `cashKhata = 20000L` (₹200), `upiKhata = 30000L` (₹300). Collections do not inflate sales figures. | **PASS** |
| **7** | Cash, UPI, and Split payments | Bill with ₹300 Cash + ₹200 UPI verified against `DailyShopSummary.cashSalesPaise` and `upiSalesPaise`. | **PASS** |
| **8** | Bill cancellation & refunds | `BillingRepository.cancelBill(billId)` marks bill `CANCELLED` and reverses linked active ledger Udhaar entries. | **PASS** |
| **9** | Repeated save taps & duplicate submission | Identical `idempotencyKey` resubmitted to `createBill()` and `recordPayment()` returns existing record ID with zero duplicated rows. | **PASS** |
| **10** | Malformed & negative amounts | Negative amounts (`-5000L`) and zero amounts (`0L`) rejected with `IllegalArgumentException`. | **PASS** |
| **11** | Large amounts & paise precision | Verified ₹9,999,999.99 (`999999999L`) balance and exact deduction of a single paisa (`1L`). | **PASS** |
| **12** | Atomic inventory deduction | Bill with item `productId` atomically deducts stock quantity from product inventory. | **PASS** |
| **13** | Insufficient stock rollback | Attempting to sell more stock than available aborts and rolls back without creating bill or updating inventory. | **PASS** |
| **14** | Bill cancellation stock reversal | Cancelling bill restores product stock quantities in inventory. | **PASS** |
| **15** | Supplier payables isolation | Supplier credit purchases and payments do not contaminate customer Udhaar or retail sales. | **PASS** |
| **16** | Daily drawer reconciliation with opening cash | Validates expected drawer formula with opening cash, cash sales, old udhaar collected, and cash expenses. | **PASS** |

---

## 3. AUDIT CONCLUSION
The financial engine of Syntaxa Khata adheres to banking-grade offline accounting standards:
- 0 floating-point vulnerabilities.
- Complete idempotency and double-spend protection.
- Transactional atomicity guaranteed by SQLite transactions.
