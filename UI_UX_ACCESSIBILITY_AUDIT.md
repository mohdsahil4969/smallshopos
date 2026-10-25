# UI/UX & ACCESSIBILITY AUDIT

**Target Application:** Syntaxa Khata v1.0  
**Target User Profile:** Non-technical Indian kirana shopkeepers, provision store operators, small retail merchants.  
**Auditor:** Senior Android UI/UX Designer & Accessibility Reviewer  
**Date:** 2026-10-24

---

## 1. DESIGN PHILOSOPHY & OBJECTIVES

Syntaxa Khata is engineered around real-world counter environments where:
1. **Speed is King:** Shopkeepers enter credit or payments while customers are waiting with groceries in hand. Tasks must take $\le 3$ taps.
2. **Plain Language over Accounting Jargon:** Avoid double-entry terminology (Debits, Credits, Asset Balances). Use intuitive merchant terms: **उधार दिया (Udhaar Given)**, **जमा मिला (Payment Received)**, **बाकी (Balance Due)**, **स्टॉक (Stock)**, **सप्लायर (Supplier)**.
3. **High-Contrast Touch Targets:** Big touch buttons ($\ge 48$ dp), large numeric keypad inputs, and clear color coding (Red for Udhaar Given / Money Owed, Green for Payments Received / Money Settled).

---

## 2. SCREEN-BY-SCREEN ISSUE AUDIT & RESOLUTION

### 2.1 Home Screen (`HomeScreen.kt`)
- **Key Actions:** 4 prominent quick-action tiles: "Give Udhaar", "Receive Payment", "Quick Bill", "Add Expense".
- **Metric Cards:** Today's Sales, Cash in Hand, UPI Collections, Expenses, and Total Market Udhaar.
- **Audit Findings:**
  - *Previous Defect:* Icon deprecation warnings on `ReceiptLong`, `MenuBook`, and `Undo`.
  - *Fix Applied:* Upgraded to `Icons.AutoMirrored.Filled.*` to support RTL/LTR layout mirroring automatically.
  - *Touch Target Review:* Quick Action tiles measure $72 \times 72$ dp, exceeding the 48 dp Android accessibility guideline.

### 2.2 Customer List & Due Dates (`CustomerListScreen.kt`)
- **Features:** Search bar, Active/Archived tabs, filter pills (All, Overdue, Due Today, Upcoming), avatar circle, and due date chips.
- **Audit Findings:**
  - *Previous Limitation:* Shopkeepers had no visual cues on which customers had promised to pay today or were overdue.
  - *Fix Applied:* Added interactive `FilterChip` row (Overdue in Red, Due Today in Amber, Upcoming in Blue) and added high-contrast due date status tags on each customer card.
  - *Accessibility:* Dynamic search field provides instant clear button (`Icons.Default.Clear`) with content descriptions.

### 2.3 Customer Profile (`CustomerProfileScreen.kt`)
- **Features:** Balance hero banner, phone quick-call, notes, Give Udhaar button (Red), Receive Payment button (Green), Reminder share, Statement PDF, and Set Due Date trigger.
- **Audit Findings:**
  - *Previous Limitation:* Due dates could not be set or modified directly from profile.
  - *Fix Applied:* Added quick preset due date picker (+3 Days, +7 Days, +15 Days, +30 Days, Clear Due Date) directly accessible with 1 tap.
  - *Destructive Action Safeguard:* Reversal entries require explicit user confirmation with reason input, preventing accidental double-reversals.

### 2.4 Quick & Itemized Billing (`BillingScreen.kt`)
- **Features:** Mode toggle (Quick Total vs Itemized), customer selector, split payment inputs (Cash/UPI/Other), discount, and instant Bluetooth print / WhatsApp share.
- **Audit Findings:**
  - *Previous Defect:* Credit sales without selected customer could risk data inconsistency; catalog stock deductions lacked atomicity.
  - *Fix Applied:* Enforced explicit validation requiring customer selection if unpaid balance exists; bound items to stable `productId` and moved stock deductions to atomic Room database transaction boundaries.
  - *Touch Target Review:* Number pad and quick amount pills (+₹10, +₹50, +₹100, +₹500) accelerate entry without full keyboard typing.

### 2.5 Inventory Management Screen (`InventoryScreen.kt`)
- **Features:** Add/edit/archive products, search bar, "Low Stock Only" filter chip, current quantity badges, and stock adjustment dialog with reason logging (Purchase, Damaged, Theft, Return, Audit).
- **Audit Findings:**
  - *Design Principle:* Kept completely optional for fast retail operations—shopkeepers can use quick amount billing without setting up any products.
  - *Touch Targets:* Adjust button provides instant numeric keypad with $\pm$ increments.

### 2.6 Supplier Management Screen (`SupplierScreen.kt`)
- **Features:** Wholesaler directory, outstanding payables card, Record Purchase dialog (Cash or Credit), Record Payment dialog, and detailed statement transaction history dialog.
- **Audit Findings:**
  - *Design Principle:* Payables remain strictly segregated from customer receivables and never pollute retail revenue.
  - *Usability:* Contact phone numbers trigger direct dialer intent with 1 tap.

### 2.7 5-Tab Records Navigation (`RecordsScreen.kt`)
- **Features:** Unified `ScrollableTabRow` with 5 clear tabs:
  1. **Bills:** Invoices, print receipt, share PDF, cancel bill with automatic stock restoration.
  2. **Expenses:** Daily shop expense vouchers by category.
  3. **Stock (Inventory):** Product catalog, low stock alerts, stock adjustments.
  4. **Suppliers:** Wholesaler dues, purchase invoices, payment entries.
  5. **Reports:** Net business overview, sales breakdown, CSV export, daily cash closing.

### 2.8 Daily Cash Closing Dialog (`DailyCashClosingDialog.kt`)
- **Features:** Step-by-step cash drawer reconciler:
  - Input field for **Opening Cash** (morning cash float).
  - Calculated Drawer Cash Expected: $\text{Opening} + (\text{Cash Sales} + \text{Udhaar Cash}) - \text{Cash Expenses}$.
  - Counted Cash input and instant difference badge (Matched / Shortage / Surplus).
  - Separate info card for **UPI / Online Collections** (clarifying that online payments go straight to the bank and do not sit in the cash drawer).

### 2.9 Settings & App Lock (`SettingsScreen.kt`, `AppLockScreen.kt`)
- **Features:** Shop Profile, ESC/POS Bluetooth Printer setup, 4-Digit PIN Security, Biometric toggle, Backup & Restore, and Software Updates.
- **Audit Findings:**
  - *Previous Defect:* Misleading "100% Data Safe" notice.
  - *Fix Applied:* Replaced with honest, professional description: "Automatic Safety Backup: Database snapshot is created before updating to preserve shop records."

---

## 3. LOCALIZATION & TEXT READABILITY

| Item | English String | Hindi String (हिन्दी) | Verified Fit & Overflow Check |
| :--- | :--- | :--- | :--- |
| **Give Udhaar** | Give Udhaar | उधार दें | PASS (Fits 48dp button) |
| **Receive Payment** | Receive Payment | जमा करें | PASS (Fits 48dp button) |
| **Due Today** | Due Today | आज देय | PASS (Compact badge) |
| **Overdue** | Overdue | समय सीमा समाप्त | PASS (Compact red badge) |
| **Daily Cash Closing** | Daily Cash Closing | दैनिक रोकड़ मिलान | PASS (Clean full-width card) |
| **Stock (Inventory)** | Stock | स्टॉक | PASS (Tab & Card label) |
| **Suppliers** | Suppliers | सप्लायर | PASS (Tab & Card label) |
| **Low Stock Alert** | Low Stock | कम स्टॉक | PASS (Filter Chip badge) |
| **App Lock** | Enter Security PIN | सुरक्षा पिन दर्ज करें | PASS (Clear numeric prompt) |

---

## 4. ACCESSIBILITY COMPLIANCE SUMMARY
- **Color Contrast Ratio:** Red (`KiranaDebitRed` #D32F2F) and Green (`KiranaCreditGreen` #2E7D32) achieve $> 4.5:1$ contrast against pure white and dark backgrounds.
- **Font Scaling:** All font sizes use `sp` units, correctly adapting to Android system font scaling up to 200%.
- **Screen Reader Support:** All actionable icons (`PersonAdd`, `Clear`, `ArrowBack`, `FileDownload`, `SystemUpdate`) have descriptive `contentDescription` attributes in English and Hindi.
