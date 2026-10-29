package com.syntaxa.khata

import android.content.SharedPreferences
import com.syntaxa.khata.core.security.PinSecurityManager
import com.syntaxa.khata.core.security.PinVerifyResult
import com.syntaxa.khata.data.local.KhataDatabase
import com.syntaxa.khata.data.local.dao.BillDao
import com.syntaxa.khata.data.local.dao.CustomerDao
import com.syntaxa.khata.data.local.dao.DailyCashClosingDao
import com.syntaxa.khata.data.local.dao.ExpenseDao
import com.syntaxa.khata.data.local.dao.FinancialTransactionDao
import com.syntaxa.khata.data.local.dao.ProductDao
import com.syntaxa.khata.data.local.dao.ShopProfileDao
import com.syntaxa.khata.data.local.dao.SupplierDao
import com.syntaxa.khata.data.local.entity.BillConstants
import com.syntaxa.khata.data.local.entity.BillEntity
import com.syntaxa.khata.data.local.entity.BillItemEntity
import com.syntaxa.khata.data.local.entity.CustomerEntity
import com.syntaxa.khata.data.local.entity.DailyCashClosingEntity
import com.syntaxa.khata.data.local.entity.ExpenseConstants
import com.syntaxa.khata.data.local.entity.ExpenseEntity
import com.syntaxa.khata.data.local.entity.FinancialTransactionEntity
import com.syntaxa.khata.data.local.entity.ProductEntity
import com.syntaxa.khata.data.local.entity.ShopProfileEntity
import com.syntaxa.khata.data.local.entity.SupplierEntity
import com.syntaxa.khata.data.local.entity.SupplierTransactionEntity
import com.syntaxa.khata.data.local.entity.TransactionConstants
import com.syntaxa.khata.domain.model.BillWithItems
import com.syntaxa.khata.domain.model.CustomerWithBalance
import com.syntaxa.khata.domain.model.SupplierWithBalance
import com.syntaxa.khata.feature.backup.BackupManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.Base64

class BackupAndSecurityVerificationTest {

    // --- In-Memory SharedPreferences implementation for testing PinSecurityManager ---
    private class FakeSharedPreferences : SharedPreferences {
        val map = mutableMapOf<String, Any>()

        override fun getAll(): MutableMap<String, *> = HashMap(map)
        override fun getString(key: String?, defValue: String?): String? = map[key] as? String ?: defValue
        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
            (map[key] as? Set<*>)?.map { it.toString() }?.toMutableSet() ?: defValues
        override fun getInt(key: String?, defValue: Int): Int = map[key] as? Int ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = map[key] as? Long ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = map[key] as? Float ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = map[key] as? Boolean ?: defValue
        override fun contains(key: String?): Boolean = map.containsKey(key)
        override fun edit(): SharedPreferences.Editor = FakeEditor(map)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        class FakeEditor(private val backingMap: MutableMap<String, Any>) : SharedPreferences.Editor {
            private val tempMap = mutableMapOf<String, Any?>()
            private val removeSet = mutableSetOf<String>()
            private var clearAll = false

            override fun putString(key: String, value: String?): SharedPreferences.Editor {
                tempMap[key] = value
                removeSet.remove(key)
                return this
            }
            override fun putStringSet(key: String, values: MutableSet<String>?): SharedPreferences.Editor {
                tempMap[key] = values
                removeSet.remove(key)
                return this
            }
            override fun putInt(key: String, value: Int): SharedPreferences.Editor {
                tempMap[key] = value
                removeSet.remove(key)
                return this
            }
            override fun putLong(key: String, value: Long): SharedPreferences.Editor {
                tempMap[key] = value
                removeSet.remove(key)
                return this
            }
            override fun putFloat(key: String, value: Float): SharedPreferences.Editor {
                tempMap[key] = value
                removeSet.remove(key)
                return this
            }
            override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor {
                tempMap[key] = value
                removeSet.remove(key)
                return this
            }
            override fun remove(key: String): SharedPreferences.Editor {
                removeSet.add(key)
                tempMap.remove(key)
                return this
            }
            override fun clear(): SharedPreferences.Editor {
                clearAll = true
                return this
            }
            override fun commit(): Boolean {
                apply()
                return true
            }
            override fun apply() {
                if (clearAll) {
                    backingMap.clear()
                }
                for (key in removeSet) {
                    backingMap.remove(key)
                }
                for ((key, value) in tempMap) {
                    if (value != null) backingMap[key] = value
                    else backingMap.remove(key)
                }
            }
        }
    }

    // --- Test Double for KhataDatabaseSource to test BackupManager production class directly ---
    private class TestKhataDatabase(
        val customers: MutableList<CustomerEntity> = mutableListOf(),
        val bills: MutableList<BillEntity> = mutableListOf(),
        val billItems: MutableList<BillItemEntity> = mutableListOf(),
        val transactions: MutableList<FinancialTransactionEntity> = mutableListOf(),
        val expenses: MutableList<ExpenseEntity> = mutableListOf(),
        val products: MutableList<ProductEntity> = mutableListOf(),
        val suppliers: MutableList<SupplierEntity> = mutableListOf(),
        val supplierTransactions: MutableList<SupplierTransactionEntity> = mutableListOf(),
        val dailyCashClosings: MutableList<DailyCashClosingEntity> = mutableListOf(),
        var profile: ShopProfileEntity? = null,
        var customerDaoOverride: CustomerDao? = null
    ) : com.syntaxa.khata.data.local.KhataDatabaseSource {

        private val fakeShopProfileDao = object : ShopProfileDao {
            override suspend fun saveShopProfile(p: ShopProfileEntity) { profile = p }
            override fun observeShopProfile(): Flow<ShopProfileEntity?> = flowOf(profile)
            override suspend fun getShopProfile(): ShopProfileEntity? = profile
        }

        private val fakeCustomerDao = object : CustomerDao {
            override suspend fun insertCustomer(customer: CustomerEntity): Long {
                customers.add(customer)
                return customer.id
            }
            override suspend fun restoreAllCustomers(list: List<CustomerEntity>) {
                customers.addAll(list)
            }
            override suspend fun deleteAllCustomers() { customers.clear() }
            override suspend fun updateCustomer(customer: CustomerEntity) {}
            override suspend fun getCustomerById(id: Long): CustomerEntity? = customers.find { it.id == id }
            override fun observeCustomerById(id: Long): Flow<CustomerEntity?> = flowOf(customers.find { it.id == id })
            override fun observeActiveCustomersWithBalance(): Flow<List<CustomerWithBalance>> = flowOf(emptyList())
            override fun searchActiveCustomersWithBalance(query: String): Flow<List<CustomerWithBalance>> = flowOf(emptyList())
            override fun observeCustomerWithBalance(id: Long): Flow<CustomerWithBalance?> = flowOf(null)
            override suspend fun getCustomerWithBalance(id: Long): CustomerWithBalance? {
                val c = customers.find { it.id == id } ?: return null
                var bal = 0L
                for (t in transactions.filter { it.customerId == id && it.status == TransactionConstants.STATUS_ACTIVE }) {
                    if (t.type == TransactionConstants.TYPE_UDHAAR_GIVEN) bal += t.amountPaise
                    else if (t.type == TransactionConstants.TYPE_PAYMENT_RECEIVED) bal -= t.amountPaise
                }
                return CustomerWithBalance(c.id, c.name, c.phone, c.notes, c.createdAt, c.archivedAt, bal, null, 0, c.dueDate)
            }
            override fun observeArchivedCustomersWithBalance(): Flow<List<CustomerWithBalance>> = flowOf(emptyList())
            override suspend fun countByPhone(phone: String, excludeId: Long?): Int = 0
            override suspend fun setCustomerArchived(customerId: Long, archivedAt: Long?, updatedAt: Long) {}
            override suspend fun updateCustomerDueDate(customerId: Long, dueDate: Long?, updatedAt: Long) {}
            override fun observeTotalMarketUdhaar(): Flow<Long> = flowOf(0L)
            override suspend fun getAllCustomersRaw(): List<CustomerEntity> = customers.toList()
        }

        private val fakeBillDao = object : BillDao {
            override suspend fun insertBill(bill: BillEntity): Long {
                bills.add(bill)
                return bill.id
            }
            override suspend fun insertBillItems(items: List<BillItemEntity>) { billItems.addAll(items) }
            override suspend fun restoreAllBills(bList: List<BillEntity>) { bills.addAll(bList) }
            override suspend fun restoreAllBillItems(items: List<BillItemEntity>) { billItems.addAll(items) }
            override suspend fun deleteAllBills() { bills.clear() }
            override suspend fun deleteAllBillItems() { billItems.clear() }
            override suspend fun updateBill(bill: BillEntity) {}
            override fun observeBillWithItems(id: Long): Flow<BillWithItems?> = flowOf(null)
            override suspend fun getBillWithItems(id: Long): BillWithItems? = null
            override fun observeRecentBills(limit: Int): Flow<List<BillWithItems>> = flowOf(emptyList())
            override fun observeBillsByDateRange(startEpoch: Long, endEpoch: Long): Flow<List<BillWithItems>> = flowOf(emptyList())
            override suspend fun getBillsByDateRange(startEpoch: Long, endEpoch: Long): List<BillWithItems> = emptyList()
            override fun observeBillsForCustomer(customerId: Long): Flow<List<BillWithItems>> = flowOf(emptyList())
            override suspend fun getBillsForCustomer(customerId: Long): List<BillWithItems> = emptyList()
            override fun observeBillsForCustomerByDateRange(customerId: Long, startEpoch: Long, endEpoch: Long): Flow<List<BillWithItems>> = flowOf(emptyList())
            override suspend fun getBillsForCustomerByDateRange(customerId: Long, startEpoch: Long, endEpoch: Long): List<BillWithItems> = emptyList()
            override suspend fun countBillsForDay(startOfDay: Long, endOfDay: Long): Int = 0
            override suspend fun findByIdempotencyKey(idempotencyKey: String): BillEntity? = bills.find { it.idempotencyKey == idempotencyKey }
            override suspend fun updateBillStatus(billId: Long, status: String) {}
            override suspend fun getAllBillsRaw(): List<BillEntity> = bills.toList()
            override suspend fun getAllBillItemsRaw(): List<BillItemEntity> = billItems.toList()
        }

        private val fakeFinancialTransactionDao = object : FinancialTransactionDao {
            override suspend fun insertTransaction(transaction: FinancialTransactionEntity): Long {
                transactions.add(transaction)
                return transaction.id
            }
            override suspend fun restoreAllTransactions(tList: List<FinancialTransactionEntity>) { transactions.addAll(tList) }
            override suspend fun deleteAllTransactions() { transactions.clear() }
            override suspend fun updateTransaction(transaction: FinancialTransactionEntity) {}
            override suspend fun getTransactionById(id: Long): FinancialTransactionEntity? = transactions.find { it.id == id }
            override fun observeTransactionsForCustomer(customerId: Long): Flow<List<FinancialTransactionEntity>> = flowOf(emptyList())
            override suspend fun getTransactionsForCustomer(customerId: Long): List<FinancialTransactionEntity> = emptyList()
            override fun observeRecentTransactions(limit: Int): Flow<List<FinancialTransactionEntity>> = flowOf(emptyList())
            override fun observeTransactionsByDateRange(startEpoch: Long, endEpoch: Long): Flow<List<FinancialTransactionEntity>> = flowOf(emptyList())
            override suspend fun getTransactionsByDateRange(startEpoch: Long, endEpoch: Long): List<FinancialTransactionEntity> = emptyList()
            override suspend fun updateTransactionStatus(id: Long, status: String) {}
            override suspend fun findByIdempotencyKey(idempotencyKey: String): FinancialTransactionEntity? = transactions.find { it.idempotencyKey == idempotencyKey }
            override suspend fun getAllTransactionsRaw(): List<FinancialTransactionEntity> = transactions.toList()
        }

        private val fakeExpenseDao = object : ExpenseDao {
            override suspend fun insertExpense(expense: ExpenseEntity): Long {
                expenses.add(expense)
                return expense.id
            }
            override suspend fun restoreAllExpenses(eList: List<ExpenseEntity>) { expenses.addAll(eList) }
            override suspend fun deleteAllExpenses() { expenses.clear() }
            override suspend fun updateExpense(expense: ExpenseEntity) {}
            override fun observeActiveExpenses(status: String): Flow<List<ExpenseEntity>> = flowOf(emptyList())
            override fun observeExpensesByDateRange(startEpoch: Long, endEpoch: Long): Flow<List<ExpenseEntity>> = flowOf(emptyList())
            override suspend fun getExpensesByDateRange(startEpoch: Long, endEpoch: Long): List<ExpenseEntity> = emptyList()
            override suspend fun updateExpenseStatus(id: Long, status: String) {}
            override suspend fun getExpenseById(id: Long): ExpenseEntity? = expenses.find { it.id == id }
            override suspend fun getAllExpensesRaw(): List<ExpenseEntity> = expenses.toList()
        }

        override fun shopProfileDao(): ShopProfileDao = fakeShopProfileDao
        override fun customerDao(): CustomerDao = customerDaoOverride ?: fakeCustomerDao
        override fun financialTransactionDao(): FinancialTransactionDao = fakeFinancialTransactionDao
        override fun billDao(): BillDao = fakeBillDao
        override fun expenseDao(): ExpenseDao = fakeExpenseDao

        override fun productDao(): ProductDao = object : ProductDao {
            override suspend fun insertProduct(product: ProductEntity): Long {
                products.add(product)
                return product.id
            }
            override suspend fun restoreAllProducts(pList: List<ProductEntity>) { products.addAll(pList) }
            override suspend fun updateProduct(product: ProductEntity) {
                val idx = products.indexOfFirst { it.id == product.id }
                if (idx != -1) products[idx] = product
            }
            override suspend fun getProductById(id: Long): ProductEntity? = products.find { it.id == id }
            override suspend fun getProductByName(name: String): ProductEntity? = products.find { it.name.equals(name, ignoreCase = true) }
            override fun observeActiveProducts(): Flow<List<ProductEntity>> = flowOf(products.filter { !it.isArchived })
            override fun searchActiveProducts(query: String): Flow<List<ProductEntity>> = flowOf(products.filter { !it.isArchived && it.name.contains(query, true) })
            override fun observeLowStockProducts(): Flow<List<ProductEntity>> = flowOf(products.filter { !it.isArchived && it.isLowStock })
            override suspend fun adjustStock(productId: Long, quantityChange: Double, updatedAt: Long): Int {
                val idx = products.indexOfFirst { it.id == productId }
                if (idx != -1) {
                    val p = products[idx]
                    products[idx] = p.copy(stockQuantity = p.stockQuantity + quantityChange, updatedAt = updatedAt)
                    return 1
                }
                return 0
            }
            override suspend fun archiveProduct(productId: Long, updatedAt: Long) {
                val idx = products.indexOfFirst { it.id == productId }
                if (idx != -1) products[idx] = products[idx].copy(isArchived = true, updatedAt = updatedAt)
            }
            override suspend fun getAllProductsRaw(): List<ProductEntity> = products.toList()
            override suspend fun deleteAllProducts() { products.clear() }
        }

        override fun supplierDao(): SupplierDao = object : SupplierDao {
            override suspend fun insertSupplier(supplier: SupplierEntity): Long {
                suppliers.add(supplier)
                return supplier.id
            }
            override suspend fun restoreAllSuppliers(sList: List<SupplierEntity>) { suppliers.addAll(sList) }
            override suspend fun updateSupplier(supplier: SupplierEntity) {
                val idx = suppliers.indexOfFirst { it.id == supplier.id }
                if (idx != -1) suppliers[idx] = supplier
            }
            override suspend fun getSupplierById(id: Long): SupplierEntity? = suppliers.find { it.id == id }
            override fun observeActiveSuppliersWithBalance(): Flow<List<SupplierWithBalance>> = flowOf(emptyList())
            override suspend fun getSupplierWithBalance(id: Long): SupplierWithBalance? {
                val s = suppliers.find { it.id == id } ?: return null
                var bal = 0L
                for (st in supplierTransactions.filter { it.supplierId == id && it.status == "ACTIVE" }) {
                    if (st.type == "PURCHASE") bal += st.amountPaise
                    else if (st.type == "PAYMENT_MADE") bal -= st.amountPaise
                }
                return SupplierWithBalance(s.id, s.name, s.phone, s.notes, s.createdAt, s.archivedAt, bal, null, supplierTransactions.size)
            }
            override suspend fun getAllSuppliersRaw(): List<SupplierEntity> = suppliers.toList()
            override suspend fun deleteAllSuppliers() { suppliers.clear() }
            override suspend fun insertTransaction(transaction: SupplierTransactionEntity): Long {
                supplierTransactions.add(transaction)
                return transaction.id
            }
            override suspend fun restoreAllTransactions(stList: List<SupplierTransactionEntity>) { supplierTransactions.addAll(stList) }
            override fun observeTransactionsForSupplier(supplierId: Long): Flow<List<SupplierTransactionEntity>> = flowOf(supplierTransactions.filter { it.supplierId == supplierId })
            override suspend fun getTransactionsForSupplier(supplierId: Long): List<SupplierTransactionEntity> = supplierTransactions.filter { it.supplierId == supplierId }
            override suspend fun getAllTransactionsRaw(): List<SupplierTransactionEntity> = supplierTransactions.toList()
            override suspend fun deleteAllTransactions() { supplierTransactions.clear() }
        }

        override fun dailyCashClosingDao(): DailyCashClosingDao = object : DailyCashClosingDao {
            override suspend fun saveClosing(closing: DailyCashClosingEntity): Long {
                dailyCashClosings.add(closing)
                return closing.id
            }
            override suspend fun restoreAllClosings(closings: List<DailyCashClosingEntity>) { dailyCashClosings.addAll(closings) }
            override suspend fun getClosingForDate(closingDateEpoch: Long): DailyCashClosingEntity? = dailyCashClosings.find { it.closingDateEpoch == closingDateEpoch }
            override fun observeRecentClosings(limit: Int): Flow<List<DailyCashClosingEntity>> = flowOf(dailyCashClosings.take(limit))
            override suspend fun getAllClosingsRaw(): List<DailyCashClosingEntity> = dailyCashClosings.toList()
            override suspend fun deleteAllClosings() { dailyCashClosings.clear() }
        }
    }

    // =========================================================================
    // 1. PIN SECURITY MANAGER PRODUCTION CLASS TESTS
    // =========================================================================

    @Test
    fun testPinSecurityManager_SetVerifyAndLockoutThrottling() {
        val prefs = FakeSharedPreferences()
        val pinManager = PinSecurityManager(prefs)

        assertFalse("App lock should not be enabled initially", pinManager.isAppLockEnabled())

        // 1. Set PIN "4321"
        pinManager.setPin("4321")
        assertTrue("App lock should be enabled after setting PIN", pinManager.isAppLockEnabled())

        // 2. Verify correct PIN
        val successResult = pinManager.verifyPinWithThrottling("4321")
        assertTrue("Correct PIN must succeed", successResult is PinVerifyResult.Success)

        // 3. Four incorrect attempts -> should return Incorrect with decreasing attempts
        for (i in 1..4) {
            val result = pinManager.verifyPinWithThrottling("9999")
            assertTrue("Attempt $i should be Incorrect", result is PinVerifyResult.Incorrect)
            val incorrect = result as PinVerifyResult.Incorrect
            assertEquals(5 - i, incorrect.attemptsLeftBeforeLockout)
        }

        // 4. Fifth incorrect attempt -> should trigger LockedOut(30 seconds)
        val fifthResult = pinManager.verifyPinWithThrottling("9999")
        assertTrue("5th attempt must trigger lockout", fifthResult is PinVerifyResult.LockedOut)
        val lockout = fifthResult as PinVerifyResult.LockedOut
        assertTrue("Lockout duration should be ~30 seconds", lockout.remainingSeconds in 25..30)

        // 5. While locked out, even the correct PIN should be rejected immediately
        val lockedResult = pinManager.verifyPinWithThrottling("4321")
        assertTrue("Attempt during lockout must be rejected", lockedResult is PinVerifyResult.LockedOut)

        // 6. Test persistent lockout across new PinSecurityManager instance (simulating app process restart)
        val restartedPinManager = PinSecurityManager(prefs)
        assertTrue("Lockout remaining seconds should persist across restarts", restartedPinManager.getLockoutRemainingSeconds() > 0)
        val restartVerifyResult = restartedPinManager.verifyPinWithThrottling("4321")
        assertTrue("Restarted manager must honor persistent lockout", restartVerifyResult is PinVerifyResult.LockedOut)
    }

    @Test
    fun testPinSecurityManager_LegacyV1Sha256AutoMigrationToV2Pbkdf2() {
        val prefs = FakeSharedPreferences()
        // Simulate legacy v1 SHA-256 storage
        val salt = "abcd1234efgh5678"
        val md = java.security.MessageDigest.getInstance("SHA-256")
        val legacyHash = md.digest("$salt:1234".toByteArray(StandardCharsets.UTF_8)).joinToString("") { "%02x".format(it) }

        prefs.edit()
            .putString("pin_hash", legacyHash)
            .putString("pin_salt", salt)
            .putInt("pin_version", 1)
            .putBoolean("lock_enabled", true)
            .commit()

        val pinManager = PinSecurityManager(prefs)
        assertTrue(pinManager.isAppLockEnabled())

        // Verifying with correct PIN should succeed AND migrate to version 2
        val result = pinManager.verifyPinWithThrottling("1234")
        assertTrue("Legacy PIN verification must succeed", result is PinVerifyResult.Success)

        // Verify version was upgraded to 2 in preferences
        assertEquals("PIN version must be upgraded to 2", 2, prefs.getInt("pin_version", 0))
    }

    // =========================================================================
    // 2. BACKUP MANAGER PRODUCTION CLASS ENCRYPTION & RESTORE ROUNDTRIP
    // =========================================================================

    @Test
    fun testBackupManagerEncryptedRoundtrip_ExportValidateAndRestore() = runBlocking {
        val db = TestKhataDatabase()
        db.profile = ShopProfileEntity(1, "Sharma Kirana", "Main Market", "9876543210", "sharma@upi", "Thanks!")
        db.customers.add(CustomerEntity(10L, "Ramesh Kumar", "9800000001", "Regular buyer", 1000L, 1000L))
        db.bills.add(
            BillEntity(
                id = 100L,
                receiptNumber = "BILL-001",
                customerId = 10L,
                subtotalPaise = 50000L,
                discountPaise = 5000L,
                totalPaise = 45000L,
                amountPaidPaise = 45000L,
                paymentMethod = TransactionConstants.METHOD_CASH,
                billType = BillConstants.TYPE_QUICK,
                notes = "Diwali groceries",
                createdAt = 2000L,
                status = BillConstants.STATUS_COMPLETED,
                cashPaidPaise = 45000L,
                upiPaidPaise = 0L,
                otherPaidPaise = 0L,
                idempotencyKey = "BILL-100"
            )
        )
        db.billItems.add(
            BillItemEntity(
                id = 500L,
                billId = 100L,
                description = "Basmati Rice 5kg",
                quantity = 1.0,
                unitPricePaise = 50000L,
                lineTotalPaise = 50000L
            )
        )
        db.transactions.add(
            FinancialTransactionEntity(
                id = 200L,
                type = TransactionConstants.TYPE_UDHAAR_GIVEN,
                customerId = 10L,
                amountPaise = 15000L,
                paymentMethod = TransactionConstants.METHOD_CASH,
                description = "Udhaar for oil",
                occurredAt = 2500L,
                createdAt = 2500L,
                relatedBillId = null,
                idempotencyKey = "TX-200",
                status = TransactionConstants.STATUS_ACTIVE
            )
        )
        db.expenses.add(
            ExpenseEntity(
                id = 300L,
                amountPaise = 20000L,
                category = ExpenseConstants.CAT_RENT,
                description = "Shop Rent",
                paymentMethod = TransactionConstants.METHOD_CASH,
                occurredAt = 2600L,
                createdAt = 2600L,
                status = ExpenseConstants.STATUS_ACTIVE
            )
        )

        // Directly test BackupManager production class
        val backupManager = BackupManager(db)
        val validPassword = "StrongBackupPassword@2026"

        // 1. Export encrypted backup
        val outputStream = ByteArrayOutputStream()
        val exportSuccess = backupManager.exportEncryptedBackup(outputStream, validPassword)
        assertTrue("Export must succeed", exportSuccess)

        val backupBytes = outputStream.toByteArray()
        assertTrue("Exported file must contain bytes", backupBytes.isNotEmpty())

        // 2. Inspect backup without password
        val inspectResult = backupManager.inspectBackup(ByteArrayInputStream(backupBytes))
        assertTrue("Inspect result must be valid", inspectResult.isValid)
        assertTrue("Inspect result must report encrypted", inspectResult.isEncrypted)
        assertEquals(BackupManager.BACKUP_VERSION_2_ENCRYPTED, inspectResult.version)

        // 3. Validate and parse with correct password
        val (parseValidation, parsedData) = backupManager.validateAndParseBackup(ByteArrayInputStream(backupBytes), validPassword)
        assertTrue("Parse validation must be valid: ${parseValidation.errorMessage}", parseValidation.isValid)
        assertNotNull("Parsed data must not be null", parsedData)

        assertEquals("Sharma Kirana", parsedData!!.profile?.shopName)
        assertEquals(1, parsedData.customers.size)
        assertEquals("Ramesh Kumar", parsedData.customers[0].name)
        assertEquals(1, parsedData.bills.size)
        assertEquals("BILL-001", parsedData.bills[0].receiptNumber)
        assertEquals(1, parsedData.billItems.size)
        assertEquals("Basmati Rice 5kg", parsedData.billItems[0].description)
        assertEquals(1, parsedData.transactions.size)
        assertEquals(15000L, parsedData.transactions[0].amountPaise)
        assertEquals(1, parsedData.expenses.size)
        assertEquals(20000L, parsedData.expenses[0].amountPaise)

        // 4. Restore verified data into fresh database
        val targetDb = TestKhataDatabase()
        val targetBackupManager = BackupManager(targetDb, transactionRunner = { block -> block() })
        val restoreSuccess = targetBackupManager.restoreVerifiedData(parsedData)
        assertTrue("Restore must succeed", restoreSuccess)

        assertEquals(1, targetDb.customers.size)
        assertEquals(1, targetDb.bills.size)
        assertEquals(1, targetDb.billItems.size)
        assertEquals(1, targetDb.transactions.size)
        assertEquals(1, targetDb.expenses.size)
        assertEquals("Sharma Kirana", targetDb.profile?.shopName)
    }

    // =========================================================================
    // 3. CRYPTOGRAPHIC FAILURE AND REJECTION TESTS
    // =========================================================================

    @Test
    fun testBackupManagerRejectsWrongPassword() = runBlocking {
        val db = TestKhataDatabase()
        db.customers.add(CustomerEntity(1L, "Test", "", "", 0L, 0L))
        val backupManager = BackupManager(db)

        val outputStream = ByteArrayOutputStream()
        backupManager.exportEncryptedBackup(outputStream, "ValidMasterPassword#1234")
        val backupBytes = outputStream.toByteArray()

        val (validation, parsed) = backupManager.validateAndParseBackup(
            ByteArrayInputStream(backupBytes),
            "WrongMasterPassword#9999"
        )
        assertFalse("Decryption with wrong password must fail", validation.isValid)
        assertNull(parsed)
        assertTrue(
            "Error message should mention incorrect password or tampered backup",
            validation.errorMessage?.contains("Incorrect password", ignoreCase = true) == true
        )
    }

    @Test
    fun testBackupManagerEnforcesMinimumPasswordLength() = runBlocking {
        val db = TestKhataDatabase()
        val backupManager = BackupManager(db)

        // Password shorter than 12 characters must fail export
        try {
            backupManager.exportEncryptedBackup(ByteArrayOutputStream(), "short123")
            fail("Export must throw IllegalArgumentException for password < 12 characters")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("at least 12 characters") == true)
        }

        // Test validateAndParseBackup with short password
        val dummyEncryptedJson = JSONObject().apply {
            put("format", BackupManager.FORMAT_IDENTIFIER)
            put("version", BackupManager.BACKUP_VERSION_2_ENCRYPTED)
            put("encrypted", true)
            put("cipher", BackupManager.CIPHER_ALGORITHM)
            put("kdf", BackupManager.PBKDF2_ALGORITHM)
            put("iterations", 100_000)
            put("saltHex", "0123456789abcdef0123456789abcdef")
            put("ivHex", "0123456789abcdef01234567")
            put("createdAt", 1000L)
            put("ciphertextBase64", Base64.getEncoder().encodeToString(ByteArray(32)))
        }

        val (validation, _) = backupManager.validateAndParseBackup(
            ByteArrayInputStream(dummyEncryptedJson.toString().toByteArray(StandardCharsets.UTF_8)),
            "short"
        )
        assertFalse(validation.isValid)
        assertTrue(validation.errorMessage?.contains("at least 12 characters") == true)
    }

    @Test
    fun testBackupManagerRejectsTamperedCiphertext() = runBlocking {
        val db = TestKhataDatabase()
        db.customers.add(CustomerEntity(1L, "Customer A", "", "", 0L, 0L))
        val backupManager = BackupManager(db)

        val outputStream = ByteArrayOutputStream()
        backupManager.exportEncryptedBackup(outputStream, "ValidMasterPassword#1234")

        val root = JSONObject(outputStream.toString(StandardCharsets.UTF_8.name()))
        val originalCiphertext = Base64.getDecoder().decode(root.getString("ciphertextBase64"))

        // Tamper 1 bit of ciphertext
        originalCiphertext[0] = (originalCiphertext[0].toInt() xor 0x01).toByte()
        root.put("ciphertextBase64", Base64.getEncoder().encodeToString(originalCiphertext))

        val tamperedBytes = root.toString().toByteArray(StandardCharsets.UTF_8)
        val (validation, parsed) = backupManager.validateAndParseBackup(
            ByteArrayInputStream(tamperedBytes),
            "ValidMasterPassword#1234"
        )
        assertFalse("Tampered ciphertext must fail AEAD verification", validation.isValid)
        assertNull(parsed)
    }

    @Test
    fun testBackupManagerRejectsTamperedMetadataOrAAD() = runBlocking {
        val db = TestKhataDatabase()
        db.customers.add(CustomerEntity(1L, "Customer A", "", "", 0L, 0L))
        val backupManager = BackupManager(db)

        val outputStream = ByteArrayOutputStream()
        backupManager.exportEncryptedBackup(outputStream, "ValidMasterPassword#1234")

        val root = JSONObject(outputStream.toString(StandardCharsets.UTF_8.name()))
        // Tamper with createdAt timestamp which is part of authenticated additional data (AAD)
        root.put("createdAt", root.getLong("createdAt") + 1000L)

        val tamperedBytes = root.toString().toByteArray(StandardCharsets.UTF_8)
        val (validation, parsed) = backupManager.validateAndParseBackup(
            ByteArrayInputStream(tamperedBytes),
            "ValidMasterPassword#1234"
        )
        assertFalse("Tampered AAD must fail AEAD verification", validation.isValid)
        assertNull(parsed)
    }

    @Test
    fun testBackupManagerRejectsExcessiveOrInsufficientIterations() = runBlocking {
        val db = TestKhataDatabase()
        val backupManager = BackupManager(db)

        val baseJson = JSONObject().apply {
            put("format", BackupManager.FORMAT_IDENTIFIER)
            put("version", BackupManager.BACKUP_VERSION_2_ENCRYPTED)
            put("encrypted", true)
            put("cipher", BackupManager.CIPHER_ALGORITHM)
            put("kdf", BackupManager.PBKDF2_ALGORITHM)
            put("saltHex", "0123456789abcdef0123456789abcdef")
            put("ivHex", "0123456789abcdef01234567")
            put("createdAt", 1000L)
            put("ciphertextBase64", Base64.getEncoder().encodeToString(ByteArray(32)))
        }

        // Test iterations below 50,000
        baseJson.put("iterations", 49_999)
        val inspectLow = backupManager.inspectBackup(ByteArrayInputStream(baseJson.toString().toByteArray()))
        assertFalse(inspectLow.isValid)
        assertTrue(inspectLow.errorMessage?.contains("below security minimum") == true)

        // Test iterations above 500,000
        baseJson.put("iterations", 500_001)
        val inspectHigh = backupManager.inspectBackup(ByteArrayInputStream(baseJson.toString().toByteArray()))
        assertFalse(inspectHigh.isValid)
        assertTrue(inspectHigh.errorMessage?.contains("exceed safety maximum") == true)
    }

    @Test
    fun testBackupManagerRejectsMalformedSaltOrIV() = runBlocking {
        val db = TestKhataDatabase()
        val backupManager = BackupManager(db)

        val baseJson = JSONObject().apply {
            put("format", BackupManager.FORMAT_IDENTIFIER)
            put("version", BackupManager.BACKUP_VERSION_2_ENCRYPTED)
            put("encrypted", true)
            put("cipher", BackupManager.CIPHER_ALGORITHM)
            put("kdf", BackupManager.PBKDF2_ALGORITHM)
            put("iterations", 100_000)
            put("createdAt", 1000L)
            put("ciphertextBase64", Base64.getEncoder().encodeToString(ByteArray(32)))
        }

        // Malformed salt: length != 32 hex chars
        baseJson.put("saltHex", "short_salt")
        baseJson.put("ivHex", "0123456789abcdef01234567")
        val inspectSalt = backupManager.inspectBackup(ByteArrayInputStream(baseJson.toString().toByteArray()))
        assertFalse(inspectSalt.isValid)
        assertTrue(inspectSalt.errorMessage?.contains("Invalid salt length") == true)

        // Malformed IV: non-hex characters
        baseJson.put("saltHex", "0123456789abcdef0123456789abcdef")
        baseJson.put("ivHex", "0123456789abcdefZZZZZZZZ")
        val inspectIv = backupManager.inspectBackup(ByteArrayInputStream(baseJson.toString().toByteArray()))
        assertFalse(inspectIv.isValid)
        assertTrue(inspectIv.errorMessage?.contains("Invalid IV length or format") == true)
    }

    // =========================================================================
    // 4. CANDIDATE DATASET DEEP VALIDATION TESTS
    // =========================================================================

    private fun signPlaintextJson(root: JSONObject): JSONObject {
        val copy = JSONObject(root.toString())
        copy.remove("checksum")
        val md = java.security.MessageDigest.getInstance("SHA-256")
        val hash = md.digest(copy.toString().toByteArray(StandardCharsets.UTF_8)).joinToString("") { "%02x".format(it) }
        root.put("checksum", hash)
        return root
    }

    @Test
    fun testCandidateDatasetValidation_DuplicateCustomerAndReceiptNumbers() = runBlocking {
        val db = TestKhataDatabase()
        val backupManager = BackupManager(db)

        // Duplicate customer IDs
        val jsonDupCustomer = JSONObject().apply {
            put("format", BackupManager.FORMAT_IDENTIFIER)
            put("version", BackupManager.BACKUP_VERSION_1_PLAINTEXT)
            put("encrypted", false)
            put("customers", JSONArray().apply {
                put(JSONObject().apply { put("id", 1L); put("name", "Alice") })
                put(JSONObject().apply { put("id", 1L); put("name", "Alice Duplicate") })
            })
        }
        val (valCust, _) = backupManager.validateAndParseBackup(
            ByteArrayInputStream(signPlaintextJson(jsonDupCustomer).toString().toByteArray())
        )
        assertFalse("Duplicate customer IDs must be rejected", valCust.isValid)
        assertTrue(valCust.errorMessage?.contains("Duplicate customer ID") == true)

        // Duplicate bill receipt numbers
        val jsonDupReceipt = JSONObject().apply {
            put("format", BackupManager.FORMAT_IDENTIFIER)
            put("version", BackupManager.BACKUP_VERSION_1_PLAINTEXT)
            put("encrypted", false)
            put("bills", JSONArray().apply {
                put(JSONObject().apply {
                    put("id", 1L)
                    put("receiptNumber", "REC-001")
                    put("subtotalPaise", 1000L)
                    put("discountPaise", 0L)
                    put("totalPaise", 1000L)
                    put("amountPaidPaise", 1000L)
                })
                put(JSONObject().apply {
                    put("id", 2L)
                    put("receiptNumber", "REC-001") // Duplicate!
                    put("subtotalPaise", 2000L)
                    put("discountPaise", 0L)
                    put("totalPaise", 2000L)
                    put("amountPaidPaise", 2000L)
                })
            })
        }
        val (valReceipt, _) = backupManager.validateAndParseBackup(
            ByteArrayInputStream(signPlaintextJson(jsonDupReceipt).toString().toByteArray())
        )
        assertFalse("Duplicate receipt numbers must be rejected", valReceipt.isValid)
        assertTrue(valReceipt.errorMessage?.contains("Duplicate receipt number") == true)
    }

    @Test
    fun testCandidateDatasetValidation_BrokenForeignKeys() = runBlocking {
        val db = TestKhataDatabase()
        val backupManager = BackupManager(db)

        // Bill references non-existent customer
        val jsonBrokenBillCust = JSONObject().apply {
            put("format", BackupManager.FORMAT_IDENTIFIER)
            put("version", BackupManager.BACKUP_VERSION_1_PLAINTEXT)
            put("encrypted", false)
            put("bills", JSONArray().apply {
                put(JSONObject().apply {
                    put("id", 1L)
                    put("receiptNumber", "REC-001")
                    put("customerId", 9999L) // Non-existent!
                    put("subtotalPaise", 1000L)
                    put("discountPaise", 0L)
                    put("totalPaise", 1000L)
                    put("amountPaidPaise", 1000L)
                })
            })
        }
        val (valBillCust, _) = backupManager.validateAndParseBackup(
            ByteArrayInputStream(signPlaintextJson(jsonBrokenBillCust).toString().toByteArray())
        )
        assertFalse(valBillCust.isValid)
        assertTrue(valBillCust.errorMessage?.contains("references non-existent customer") == true)

        // BillItem references non-existent bill
        val jsonBrokenItem = JSONObject().apply {
            put("format", BackupManager.FORMAT_IDENTIFIER)
            put("version", BackupManager.BACKUP_VERSION_1_PLAINTEXT)
            put("encrypted", false)
            put("billItems", JSONArray().apply {
                put(JSONObject().apply {
                    put("id", 1L)
                    put("billId", 8888L) // Non-existent bill!
                    put("quantity", 1.0)
                    put("unitPricePaise", 500L)
                    put("lineTotalPaise", 500L)
                })
            })
        }
        val (valItem, _) = backupManager.validateAndParseBackup(
            ByteArrayInputStream(signPlaintextJson(jsonBrokenItem).toString().toByteArray())
        )
        assertFalse(valItem.isValid)
        assertTrue(valItem.errorMessage?.contains("references non-existent bill") == true)
    }

    @Test
    fun testCandidateDatasetValidation_BillAndLineItemArithmeticMismatch() = runBlocking {
        val db = TestKhataDatabase()
        val backupManager = BackupManager(db)

        // Line item lineTotal != quantity * unitPrice
        val jsonLineMathError = JSONObject().apply {
            put("format", BackupManager.FORMAT_IDENTIFIER)
            put("version", BackupManager.BACKUP_VERSION_1_PLAINTEXT)
            put("encrypted", false)
            put("bills", JSONArray().apply {
                put(JSONObject().apply {
                    put("id", 1L)
                    put("receiptNumber", "REC-001")
                    put("subtotalPaise", 1000L)
                    put("totalPaise", 1000L)
                    put("amountPaidPaise", 1000L)
                })
            })
            put("billItems", JSONArray().apply {
                put(JSONObject().apply {
                    put("id", 1L)
                    put("billId", 1L)
                    put("quantity", 2.0)
                    put("unitPricePaise", 500L)
                    put("lineTotalPaise", 999L) // Wrong! 2 * 500 == 1000
                })
            })
        }
        val (valLineMath, _) = backupManager.validateAndParseBackup(
            ByteArrayInputStream(signPlaintextJson(jsonLineMathError).toString().toByteArray())
        )
        assertFalse(valLineMath.isValid)
        assertTrue(valLineMath.errorMessage?.contains("line total") == true)

        // Line items sum != declared bill subtotal
        val jsonSubtotalMismatch = JSONObject().apply {
            put("format", BackupManager.FORMAT_IDENTIFIER)
            put("version", BackupManager.BACKUP_VERSION_1_PLAINTEXT)
            put("encrypted", false)
            put("bills", JSONArray().apply {
                put(JSONObject().apply {
                    put("id", 1L)
                    put("receiptNumber", "REC-001")
                    put("subtotalPaise", 5000L) // Declared 5000
                    put("totalPaise", 5000L)
                    put("amountPaidPaise", 5000L)
                })
            })
            put("billItems", JSONArray().apply {
                put(JSONObject().apply {
                    put("id", 1L)
                    put("billId", 1L)
                    put("quantity", 1.0)
                    put("unitPricePaise", 4000L)
                    put("lineTotalPaise", 4000L) // Sum is only 4000!
                })
            })
        }
        val (valSubtotal, _) = backupManager.validateAndParseBackup(
            ByteArrayInputStream(signPlaintextJson(jsonSubtotalMismatch).toString().toByteArray())
        )
        assertFalse(valSubtotal.isValid)
        assertTrue(valSubtotal.errorMessage?.contains("declared subtotal") == true)
    }

    @Test
    fun testCandidateDatasetValidation_ReversalIntegrity() = runBlocking {
        val db = TestKhataDatabase()
        val backupManager = BackupManager(db)

        // Multiple reversals for the same target transaction
        val jsonDuplicateReversal = JSONObject().apply {
            put("format", BackupManager.FORMAT_IDENTIFIER)
            put("version", BackupManager.BACKUP_VERSION_1_PLAINTEXT)
            put("encrypted", false)
            put("transactions", JSONArray().apply {
                put(JSONObject().apply {
                    put("id", 10L)
                    put("type", TransactionConstants.TYPE_UDHAAR_GIVEN)
                    put("amountPaise", 5000L)
                })
                put(JSONObject().apply {
                    put("id", 11L)
                    put("type", TransactionConstants.TYPE_REVERSAL)
                    put("reversalOfTransactionId", 10L)
                    put("amountPaise", 5000L)
                })
                put(JSONObject().apply {
                    put("id", 12L)
                    put("type", TransactionConstants.TYPE_REVERSAL)
                    put("reversalOfTransactionId", 10L) // Duplicate reversal!
                    put("amountPaise", 5000L)
                })
            })
        }
        val (valRev, _) = backupManager.validateAndParseBackup(
            ByteArrayInputStream(signPlaintextJson(jsonDuplicateReversal).toString().toByteArray())
        )
        assertFalse("Duplicate reversal must be rejected", valRev.isValid)
        assertTrue(valRev.errorMessage?.contains("Duplicate reversal") == true)
    }

    // =========================================================================
    // 5. UNENCRYPTED BACKUP CHECKSUM VERIFICATION & TAMPER DETECTION
    // =========================================================================

    @Test
    fun testUnencryptedBackupExportAndChecksumTamperDetection() = runBlocking {
        val db = TestKhataDatabase()
        db.customers.add(CustomerEntity(1L, "Plain Customer", "9999999999", "", 100L, 100L))
        val backupManager = BackupManager(db)

        val outStream = ByteArrayOutputStream()
        assertTrue(backupManager.exportUnencryptedBackup(outStream))
        val plainBytes = outStream.toByteArray()

        // 1. Valid export must inspect and parse cleanly
        val inspectResult = backupManager.inspectBackup(ByteArrayInputStream(plainBytes))
        assertTrue("Plaintext inspect must succeed: ${inspectResult.errorMessage}", inspectResult.isValid)
        assertFalse(inspectResult.isEncrypted)

        val (valResult, parsedData) = backupManager.validateAndParseBackup(ByteArrayInputStream(plainBytes))
        assertTrue(valResult.isValid)
        assertEquals(1, parsedData?.customers?.size)

        // 2. Tampering payload must trigger checksum mismatch
        val root = JSONObject(String(plainBytes, StandardCharsets.UTF_8))
        val customers = root.getJSONArray("customers")
        customers.getJSONObject(0).put("name", "Tampered Customer Name")

        val tamperedBytes = root.toString(2).toByteArray(StandardCharsets.UTF_8)
        val tamperedInspect = backupManager.inspectBackup(ByteArrayInputStream(tamperedBytes))
        assertFalse("Tampered unencrypted backup must fail checksum", tamperedInspect.isValid)
        assertTrue(tamperedInspect.errorMessage?.contains("checksum mismatch") == true)
    }

    // =========================================================================
    // 6. RESTORE TRANSACTION ROLLBACK SAFETY TEST
    // =========================================================================

    @Test
    fun testRestoreTransactionalRollbackSafety() = runBlocking {
        val db = TestKhataDatabase()
        // Existing state before restore
        db.customers.add(CustomerEntity(1L, "Original Customer", "9000000000", "", 0L, 0L))

        var transactionRollbackSimulated = false
        // Simulate a database failure during transaction execution
        val failingManager = BackupManager(
            database = db,
            transactionRunner = { block ->
                val snapshot = db.customers.toList()
                try {
                    block()
                } catch (e: Exception) {
                    // Simulate Room rollback
                    db.customers.clear()
                    db.customers.addAll(snapshot)
                    transactionRollbackSimulated = true
                    throw e
                }
            }
        )

        val failingData = com.syntaxa.khata.feature.backup.ParsedBackupData(
            profile = null,
            customers = listOf(
                CustomerEntity(10L, "New A", "", "", 0L, 0L),
                CustomerEntity(20L, "New B", "", "", 0L, 0L)
            ),
            transactions = emptyList(),
            bills = emptyList(),
            billItems = emptyList(),
            expenses = emptyList()
        )

        // Inject customerDao that throws on restore to trigger rollback
        val failingDb = TestKhataDatabase(customers = mutableListOf(CustomerEntity(1L, "Original Customer", "9000000000", "", 0L, 0L)))
        val standardCustomerDao = failingDb.customerDao()
        failingDb.customerDaoOverride = object : CustomerDao by standardCustomerDao {
            override suspend fun restoreAllCustomers(list: List<CustomerEntity>) {
                throw IllegalStateException("Simulated disk error or SQL constraint violation")
            }
        }

        val testRollbackManager = BackupManager(
            database = failingDb,
            transactionRunner = { block ->
                val snapshot = failingDb.customers.toList()
                try {
                    block()
                } catch (e: Exception) {
                    failingDb.customers.clear()
                    failingDb.customers.addAll(snapshot)
                    transactionRollbackSimulated = true
                    throw e
                }
            }
        )

        try {
            testRollbackManager.restoreVerifiedData(failingData)
            fail("Expected restore to throw exception on database failure")
        } catch (_: IllegalStateException) {
            // Expected
        }

        assertTrue("Transaction rollback must be triggered", transactionRollbackSimulated)
        assertEquals("Original customer must remain preserved", 1, failingDb.customers.size)
        assertEquals("Original Customer", failingDb.customers[0].name)
    }

    @Test
    fun test16_backupAndRestoreCompleteness_AllEntitiesAndInvariants() = runBlocking {
        val srcDb = TestKhataDatabase()
        srcDb.profile = ShopProfileEntity(1, "Sharma Kirana Store", "Station Road", "9876543210", "sharma@upi")
        srcDb.customers.add(CustomerEntity(1L, "Ramesh Kumar", "9876543210", "Milk daily", 1000L, 1000L, dueDate = 2000L))
        srcDb.products.add(ProductEntity(10L, "Aashirvaad Atta 5kg", "pkt", 22000L, 19000L, 15.0, 3.0, "890123"))
        srcDb.suppliers.add(SupplierEntity(20L, "Gupta Wholesalers", "9811122233", "Grain distributor"))
        srcDb.supplierTransactions.add(SupplierTransactionEntity(100L, 20L, "PURCHASE", 50000L, "CREDIT", "INV-99", "", 1000L, 1000L))
        srcDb.supplierTransactions.add(SupplierTransactionEntity(101L, 20L, "PAYMENT_MADE", 20000L, "CASH", "", "", 1500L, 1500L))
        // Expected supplier balance = 50000 - 20000 = 30000L

        srcDb.bills.add(BillEntity(200L, "BILL-001", 1L, 22000L, 0L, 22000L, 10000L, "CASH", "ITEMIZED", "", 1000L, BillConstants.STATUS_COMPLETED, 10000L, 0L, 0L))
        srcDb.billItems.add(BillItemEntity(300L, 200L, "Aashirvaad Atta 5kg", 1.0, 22000L, 22000L, productId = 10L))
        srcDb.transactions.add(FinancialTransactionEntity(400L, TransactionConstants.TYPE_UDHAAR_GIVEN, 1L, 12000L, "CREDIT", "", 1000L, 1000L, relatedBillId = 200L))
        srcDb.expenses.add(ExpenseEntity(500L, 5000L, "Tea & Snacks", "", "CASH", 1000L, 1000L))
        srcDb.dailyCashClosings.add(DailyCashClosingEntity(600L, 100000L, 5000L, 10000L, 0L, 5000L, 10000L, 10000L, 0L, "Balanced"))

        val backupManager = BackupManager(srcDb)
        val baos = ByteArrayOutputStream()
        val exportSuccess = backupManager.exportEncryptedBackup(baos, "StrongPassword#2026!")
        assertTrue("Encrypted export must succeed", exportSuccess)

        // Inspect
        val inspectRes = backupManager.inspectBackup(ByteArrayInputStream(baos.toByteArray()))
        assertTrue("Inspect must report valid encrypted backup", inspectRes.isValid && inspectRes.isEncrypted)

        // Parse & Validate
        val (valResult, parsedData) = backupManager.validateAndParseBackup(ByteArrayInputStream(baos.toByteArray()), "StrongPassword#2026!")
        assertTrue("Validation must pass: ${valResult.errorMessage}", valResult.isValid)
        assertNotNull(parsedData)
        assertEquals(1, parsedData!!.customers.size)
        assertEquals(1, parsedData.products.size)
        assertEquals(1, parsedData.suppliers.size)
        assertEquals(2, parsedData.supplierTransactions.size)
        assertEquals(1, parsedData.bills.size)
        assertEquals(1, parsedData.billItems.size)
        assertEquals(10L, parsedData.billItems[0].productId)
        assertEquals(1, parsedData.dailyCashClosings.size)

        // Restore into target DB
        val targetDb = TestKhataDatabase()
        val targetManager = BackupManager(targetDb)
        val restoreSuccess = targetManager.restoreVerifiedData(parsedData)
        assertTrue("Restore must succeed", restoreSuccess)

        assertEquals("Ramesh Kumar", targetDb.customers[0].name)
        assertEquals(2000L, targetDb.customers[0].dueDate)
        assertEquals("Aashirvaad Atta 5kg", targetDb.products[0].name)
        assertEquals(10L, targetDb.billItems[0].productId)
        assertEquals(30000L, targetDb.supplierDao().getSupplierWithBalance(20L)?.balancePaise)
        assertEquals(12000L, targetDb.customerDao().getCustomerWithBalance(1L)?.balancePaise)
    }

    @Test
    fun test17_backwardCompatibility_V1AndV2BackupsWithoutNewEntities() = runBlocking {
        // Construct older v2 backup JSON that lacks products, suppliers, closings
        val root = org.json.JSONObject().apply {
            put("format", BackupManager.FORMAT_IDENTIFIER)
            put("version", BackupManager.BACKUP_VERSION_1_PLAINTEXT)
            put("encrypted", false)
            put("createdAt", 100000L)
            put("customers", org.json.JSONArray().apply {
                put(org.json.JSONObject().apply {
                    put("id", 5L)
                    put("name", "Old Customer")
                })
            })
            put("bills", org.json.JSONArray())
            put("billItems", org.json.JSONArray())
            put("transactions", org.json.JSONArray())
            put("expenses", org.json.JSONArray())
        }
        val canonical = backupManagerCanonicalJsonString(root)
        val checksum = backupManagerCalculateSha256(canonical)
        root.put("checksum", checksum)

        val targetDb = TestKhataDatabase()
        val manager = BackupManager(targetDb)
        val (valRes, parsedData) = manager.validateAndParseBackup(ByteArrayInputStream(root.toString().toByteArray(Charsets.UTF_8)))
        assertTrue("Older format without new tables must validate successfully: ${valRes.errorMessage}", valRes.isValid)
        assertNotNull(parsedData)
        assertTrue("Products list defaults to empty", parsedData!!.products.isEmpty())
        assertTrue("Suppliers list defaults to empty", parsedData.suppliers.isEmpty())
        assertTrue("Daily closings defaults to empty", parsedData.dailyCashClosings.isEmpty())
        assertEquals(1, parsedData.customers.size)

        val restoreSuccess = manager.restoreVerifiedData(parsedData)
        assertTrue(restoreSuccess)
        assertEquals(1, targetDb.customers.size)
        assertEquals("Old Customer", targetDb.customers[0].name)
    }

    @Test
    fun test18_safetyBackup_ExportAndVerifyRecoverability() = runBlocking {
        val db = TestKhataDatabase()
        db.customers.add(CustomerEntity(1L, "Safety Cust", "9999999999", "", 0L, 0L))
        db.products.add(ProductEntity(2L, "Sugar 1kg", "kg", 4500L, 4000L, 50.0, 5.0, ""))

        val manager = BackupManager(db)
        val safetyKey = ByteArray(32) { (it + 1).toByte() }
        val baos = ByteArrayOutputStream()

        val exportOk = manager.exportSafetyBackup(baos, safetyKey)
        assertTrue("Safety backup export must succeed", exportOk)

        // Verify recoverability
        val verifyOk = manager.verifySafetyBackup(ByteArrayInputStream(baos.toByteArray()), safetyKey)
        assertTrue("Safety backup verification must succeed", verifyOk)

        // Test with corrupted data
        val corruptedBytes = baos.toByteArray()
        corruptedBytes[corruptedBytes.size - 5] = (corruptedBytes[corruptedBytes.size - 5] + 1).toByte()
        val verifyFails = manager.verifySafetyBackup(ByteArrayInputStream(corruptedBytes), safetyKey)
        assertFalse("Tampered safety backup must fail verification", verifyFails)
    }

    private fun backupManagerCanonicalJsonString(obj: Any): String {
        return when (obj) {
            is org.json.JSONObject -> {
                val keys = obj.keys().asSequence().sorted().toList()
                keys.joinToString(prefix = "{", postfix = "}", separator = ",") { k ->
                    "\"$k\":" + backupManagerCanonicalJsonString(obj.get(k))
                }
            }
            is org.json.JSONArray -> {
                (0 until obj.length()).joinToString(prefix = "[", postfix = "]", separator = ",") { i ->
                    backupManagerCanonicalJsonString(obj.get(i))
                }
            }
            is String -> org.json.JSONObject.quote(obj)
            else -> obj.toString()
        }
    }

    private fun backupManagerCalculateSha256(text: String): String {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(text.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
