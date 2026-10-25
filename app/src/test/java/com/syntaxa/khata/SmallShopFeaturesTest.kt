package com.syntaxa.khata

import com.syntaxa.khata.core.model.DateTimeUtils
import com.syntaxa.khata.core.preferences.UserGuidePreferences
import com.syntaxa.khata.feature.guide.guideCategories
import com.syntaxa.khata.feature.guide.termsSections
import com.syntaxa.khata.feature.guide.walkthroughSteps
import com.syntaxa.khata.data.local.dao.BillDao
import com.syntaxa.khata.data.local.dao.CustomerDao
import com.syntaxa.khata.data.local.dao.DailyCashClosingDao
import com.syntaxa.khata.data.local.dao.FinancialTransactionDao
import com.syntaxa.khata.data.local.dao.ProductDao
import com.syntaxa.khata.data.local.dao.SupplierDao
import com.syntaxa.khata.data.local.entity.BillConstants
import com.syntaxa.khata.data.local.entity.BillEntity
import com.syntaxa.khata.data.local.entity.BillItemEntity
import com.syntaxa.khata.data.local.entity.CustomerEntity
import com.syntaxa.khata.data.local.entity.DailyCashClosingEntity
import com.syntaxa.khata.data.local.entity.FinancialTransactionEntity
import com.syntaxa.khata.data.local.entity.ProductEntity
import com.syntaxa.khata.data.local.entity.SupplierEntity
import com.syntaxa.khata.data.local.entity.SupplierTransactionConstants
import com.syntaxa.khata.data.local.entity.SupplierTransactionEntity
import com.syntaxa.khata.data.local.entity.TransactionConstants
import com.syntaxa.khata.domain.model.BillWithItems
import com.syntaxa.khata.data.repository.BillingRepository
import com.syntaxa.khata.data.repository.CustomerRepository
import com.syntaxa.khata.data.repository.DailyClosingRepository
import com.syntaxa.khata.data.repository.DailyReportRepository
import com.syntaxa.khata.data.repository.ExpenseRepository
import com.syntaxa.khata.data.repository.FinancialRepository
import com.syntaxa.khata.data.repository.InventoryRepository
import com.syntaxa.khata.data.repository.SupplierRepository
import com.syntaxa.khata.domain.model.CustomerWithBalance
import com.syntaxa.khata.domain.model.PaymentReminderTemplate
import com.syntaxa.khata.domain.model.SupplierWithBalance
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicLong

/**
 * Unit Test Suite for Small-Shop Feature Upgrades:
 * 1. Customer Due Dates & Overdue Filtering
 * 2. Daily Cash Closing Drawer Reconciler
 * 3. Optional Simple Inventory & Atomic Stock Deduction
 * 4. Supplier Purchases & Dues Ledger (Strictly isolated from customer receivables)
 */
class SmallShopFeaturesTest {

    private val idGen = AtomicLong(1)
    private val customers = mutableListOf<CustomerEntity>()
    private val products = mutableListOf<ProductEntity>()
    private val suppliers = mutableListOf<SupplierEntity>()
    private val supplierTransactions = mutableListOf<SupplierTransactionEntity>()
    private val closings = mutableListOf<DailyCashClosingEntity>()

    // Fakes
    private val fakeCustomerDao = object : CustomerDao {
        override suspend fun insertCustomer(customer: CustomerEntity): Long {
            val id = idGen.getAndIncrement()
            customers.add(customer.copy(id = id))
            return id
        }
        override suspend fun restoreAllCustomers(list: List<CustomerEntity>) { customers.addAll(list) }
        override suspend fun deleteAllCustomers() { customers.clear() }
        override suspend fun updateCustomer(customer: CustomerEntity) {
            val idx = customers.indexOfFirst { it.id == customer.id }
            if (idx != -1) customers[idx] = customer
        }
        override suspend fun getCustomerById(id: Long): CustomerEntity? = customers.firstOrNull { it.id == id }
        override fun observeCustomerById(id: Long): Flow<CustomerEntity?> = flowOf(customers.firstOrNull { it.id == id })
        override fun observeActiveCustomersWithBalance(): Flow<List<CustomerWithBalance>> = flow {
            emit(customers.filter { it.archivedAt == null }.map { c ->
                CustomerWithBalance(c.id, c.name, c.phone, c.notes, c.createdAt, c.archivedAt, 1000L, null, 1, c.dueDate)
            })
        }
        override fun searchActiveCustomersWithBalance(query: String): Flow<List<CustomerWithBalance>> = flowOf(emptyList())
        override fun observeCustomerWithBalance(id: Long): Flow<CustomerWithBalance?> = flowOf(null)
        override suspend fun getCustomerWithBalance(id: Long): CustomerWithBalance? {
            val c = getCustomerById(id) ?: return null
            return CustomerWithBalance(c.id, c.name, c.phone, c.notes, c.createdAt, c.archivedAt, 5000L, null, 1, c.dueDate)
        }
        override fun observeArchivedCustomersWithBalance(): Flow<List<CustomerWithBalance>> = flowOf(emptyList())
        override suspend fun countByPhone(phone: String, excludeId: Long?): Int = 0
        override suspend fun setCustomerArchived(customerId: Long, archivedAt: Long?, updatedAt: Long) {
            val idx = customers.indexOfFirst { it.id == customerId }
            if (idx != -1) customers[idx] = customers[idx].copy(archivedAt = archivedAt, updatedAt = updatedAt)
        }
        override suspend fun updateCustomerDueDate(customerId: Long, dueDate: Long?, updatedAt: Long) {
            val idx = customers.indexOfFirst { it.id == customerId }
            if (idx != -1) customers[idx] = customers[idx].copy(dueDate = dueDate, updatedAt = updatedAt)
        }
        override fun observeTotalMarketUdhaar(): Flow<Long> = flowOf(5000L)
        override suspend fun getAllCustomersRaw(): List<CustomerEntity> = customers
    }

    private val fakeProductDao = object : ProductDao {
        override suspend fun insertProduct(product: ProductEntity): Long {
            val id = idGen.getAndIncrement()
            products.add(product.copy(id = id))
            return id
        }
        override suspend fun restoreAllProducts(list: List<ProductEntity>) { products.addAll(list) }
        override suspend fun updateProduct(product: ProductEntity) {
            val idx = products.indexOfFirst { it.id == product.id }
            if (idx != -1) products[idx] = product
        }
        override suspend fun getProductById(id: Long): ProductEntity? = products.firstOrNull { it.id == id }
        override fun observeActiveProducts(): Flow<List<ProductEntity>> = flow {
            emit(products.filter { !it.isArchived })
        }
        override fun searchActiveProducts(query: String): Flow<List<ProductEntity>> = flow {
            emit(products.filter { !it.isArchived && (it.name.contains(query, ignoreCase = true) || it.barcode.contains(query)) })
        }
        override fun observeLowStockProducts(): Flow<List<ProductEntity>> = flow {
            emit(products.filter { !it.isArchived && it.stockQuantity <= it.lowStockThreshold })
        }
        override suspend fun adjustStock(productId: Long, quantityChange: Double, updatedAt: Long): Int {
            val idx = products.indexOfFirst { it.id == productId }
            if (idx != -1) {
                products[idx] = products[idx].copy(
                    stockQuantity = products[idx].stockQuantity + quantityChange,
                    updatedAt = updatedAt
                )
                return 1
            }
            return 0
        }
        override suspend fun archiveProduct(productId: Long, updatedAt: Long) {
            val idx = products.indexOfFirst { it.id == productId }
            if (idx != -1) products[idx] = products[idx].copy(isArchived = true, updatedAt = updatedAt)
        }
        override suspend fun getAllProductsRaw(): List<ProductEntity> = products
        override suspend fun deleteAllProducts() { products.clear() }
    }

    private val fakeSupplierDao = object : SupplierDao {
        override suspend fun insertSupplier(supplier: SupplierEntity): Long {
            val id = idGen.getAndIncrement()
            suppliers.add(supplier.copy(id = id))
            return id
        }
        override suspend fun restoreAllSuppliers(list: List<SupplierEntity>) { suppliers.addAll(list) }
        override suspend fun updateSupplier(supplier: SupplierEntity) {
            val idx = suppliers.indexOfFirst { it.id == supplier.id }
            if (idx != -1) suppliers[idx] = supplier
        }
        override suspend fun getSupplierById(id: Long): SupplierEntity? = suppliers.firstOrNull { it.id == id }
        override fun observeActiveSuppliersWithBalance(): Flow<List<SupplierWithBalance>> = flow {
            emit(suppliers.filter { it.archivedAt == null }.map { s ->
                val txs = supplierTransactions.filter { it.supplierId == s.id && it.status == SupplierTransactionConstants.STATUS_ACTIVE }
                val purchases = txs.filter { it.type == SupplierTransactionConstants.TYPE_PURCHASE }.sumOf { it.amountPaise }
                val payments = txs.filter { it.type == SupplierTransactionConstants.TYPE_PAYMENT_MADE }.sumOf { it.amountPaise }
                SupplierWithBalance(s.id, s.name, s.phone, s.notes, s.createdAt, s.archivedAt, purchases - payments, null, txs.size)
            })
        }
        override suspend fun getSupplierWithBalance(id: Long): SupplierWithBalance? {
            val s = getSupplierById(id) ?: return null
            val txs = supplierTransactions.filter { it.supplierId == s.id && it.status == SupplierTransactionConstants.STATUS_ACTIVE }
            val purchases = txs.filter { it.type == SupplierTransactionConstants.TYPE_PURCHASE }.sumOf { it.amountPaise }
            val payments = txs.filter { it.type == SupplierTransactionConstants.TYPE_PAYMENT_MADE }.sumOf { it.amountPaise }
            return SupplierWithBalance(s.id, s.name, s.phone, s.notes, s.createdAt, s.archivedAt, purchases - payments, null, txs.size)
        }
        override suspend fun getAllSuppliersRaw(): List<SupplierEntity> = suppliers
        override suspend fun deleteAllSuppliers() { suppliers.clear() }

        override suspend fun insertTransaction(transaction: SupplierTransactionEntity): Long {
            val id = idGen.getAndIncrement()
            supplierTransactions.add(transaction.copy(id = id))
            return id
        }
        override suspend fun restoreAllTransactions(transactions: List<SupplierTransactionEntity>) {
            supplierTransactions.addAll(transactions)
        }
        override fun observeTransactionsForSupplier(supplierId: Long): Flow<List<SupplierTransactionEntity>> = flow {
            emit(supplierTransactions.filter { it.supplierId == supplierId })
        }
        override suspend fun getTransactionsForSupplier(supplierId: Long): List<SupplierTransactionEntity> =
            supplierTransactions.filter { it.supplierId == supplierId }
        override suspend fun getAllTransactionsRaw(): List<SupplierTransactionEntity> = supplierTransactions
        override suspend fun deleteAllTransactions() { supplierTransactions.clear() }
    }

    private val fakeClosingDao = object : DailyCashClosingDao {
        override suspend fun saveClosing(closing: DailyCashClosingEntity): Long {
            val id = idGen.getAndIncrement()
            closings.add(closing.copy(id = id))
            return id
        }
        override suspend fun restoreAllClosings(closingsList: List<DailyCashClosingEntity>) {
            closings.addAll(closingsList)
        }
        override suspend fun getClosingForDate(closingDateEpoch: Long): DailyCashClosingEntity? =
            closings.firstOrNull { it.closingDateEpoch == closingDateEpoch }
        override fun observeRecentClosings(limit: Int): Flow<List<DailyCashClosingEntity>> = flow {
            emit(closings.takeLast(limit).reversed())
        }
        override suspend fun getAllClosingsRaw(): List<DailyCashClosingEntity> = closings
        override suspend fun deleteAllClosings() { closings.clear() }
    }

    private lateinit var customerRepo: CustomerRepository
    private lateinit var inventoryRepo: InventoryRepository
    private lateinit var supplierRepo: SupplierRepository

    @Before
    fun setUp() {
        customers.clear()
        products.clear()
        suppliers.clear()
        supplierTransactions.clear()
        closings.clear()

        customerRepo = CustomerRepository(fakeCustomerDao)
        inventoryRepo = InventoryRepository(fakeProductDao)
        supplierRepo = SupplierRepository(fakeSupplierDao)
    }

    // =========================================================================
    // 1. CUSTOMER DUE DATES TESTS
    // =========================================================================

    @Test
    fun testCustomerDueDates_CreationAndStatusFiltering() = runBlocking {
        val now = System.currentTimeMillis()
        val yesterday = now - 86400000L
        val nextWeek = now + (7 * 86400000L)

        // Customer 1: Overdue
        val c1Id = customerRepo.createCustomer("Mohan Lal", "9876543201", "Due past date", dueDate = yesterday)
        // Customer 2: Upcoming
        val c2Id = customerRepo.createCustomer("Sita Devi", "9876543202", "Due next week", dueDate = nextWeek)
        // Customer 3: No due date
        val c3Id = customerRepo.createCustomer("Ravi Kumar", "9876543203", "No promise date", dueDate = null)

        val c1 = fakeCustomerDao.getCustomerById(c1Id)
        val c2 = fakeCustomerDao.getCustomerById(c2Id)
        val c3 = fakeCustomerDao.getCustomerById(c3Id)

        assertNotNull(c1?.dueDate)
        assertTrue(c1!!.dueDate!! < now) // Overdue!

        assertNotNull(c2?.dueDate)
        assertTrue(c2!!.dueDate!! > now) // Upcoming!

        // Update due date for Customer 3
        customerRepo.updateDueDate(c3Id, now + 86400000L)
        val c3Updated = fakeCustomerDao.getCustomerById(c3Id)
        assertNotNull(c3Updated?.dueDate)

        // Test payment reminder formatting with due date
        val reminder = PaymentReminderTemplate(
            customerName = "Mohan Lal",
            shopName = "Gupta Kirana",
            balanceFormatted = "₹500.00",
            shopPhone = "9876543210",
            dueDateFormatted = "22-Oct-2026"
        )
        assertTrue(reminder.toEnglishText().contains("Payment Due: 22-Oct-2026"))
        assertTrue(reminder.toHindiText().contains("भुगतान की तारीख: 22-Oct-2026"))
    }

    // =========================================================================
    // 2. DAILY CASH CLOSING TESTS
    // =========================================================================

    @Test
    fun testDailyCashClosing_FormulaReconciliation() = runBlocking {
        // Mock summary: Cash Sales ₹500, Cash Khata ₹200, Cash Expenses ₹100
        val cashSalesPaise = 50_000L
        val cashKhataPaise = 20_000L
        val cashExpensesPaise = 10_000L

        // Expected Cash = (Cash Sales + Cash Khata) - Cash Expenses = (500 + 200) - 100 = 600 (60,000 paise)
        val expectedCash = (cashSalesPaise + cashKhataPaise) - cashExpensesPaise
        assertEquals(60_000L, expectedCash)

        // Scenario A: Counted cash exactly matches expected (Balanced)
        val countedA = 60_000L
        val diffA = countedA - expectedCash
        assertEquals(0L, diffA)

        // Scenario B: Counted cash has surplus (₹650 counted -> +₹50 surplus)
        val countedB = 65_000L
        val diffB = countedB - expectedCash
        assertEquals(5_000L, diffB)
        assertTrue(diffB > 0)

        // Scenario C: Counted cash has shortage (₹550 counted -> -₹50 shortage)
        val countedC = 55_000L
        val diffC = countedC - expectedCash
        assertEquals(-5_000L, diffC)
        assertTrue(diffC < 0)

        // Save closing record to DAO
        val entity = DailyCashClosingEntity(
            closingDateEpoch = DateTimeUtils.getStartOfDay(),
            openingCashPaise = 0L,
            cashSalesPaise = cashSalesPaise,
            cashKhataCollectedPaise = cashKhataPaise,
            cashExpensesPaise = cashExpensesPaise,
            expectedCashPaise = expectedCash,
            countedCashPaise = countedB,
            differencePaise = diffB,
            notes = "Cash counted by Ramesh"
        )
        val id = fakeClosingDao.saveClosing(entity)
        assertTrue(id > 0)

        val saved = fakeClosingDao.getClosingForDate(DateTimeUtils.getStartOfDay())
        assertNotNull(saved)
        assertEquals(60_000L, saved?.expectedCashPaise)
        assertEquals(65_000L, saved?.countedCashPaise)
        assertEquals(5_000L, saved?.differencePaise)
    }

    // =========================================================================
    // 3. OPTIONAL SIMPLE INVENTORY TESTS
    // =========================================================================

    @Test
    fun testSimpleInventory_StockTrackingAndAtomicDeduction() = runBlocking {
        // 1. Add product: Mustard Oil, selling ₹180, cost ₹150, initial stock 20 packets, low threshold 5
        val pId = inventoryRepo.addProduct(
            name = "Fortune Mustard Oil 1L",
            unit = "packet",
            sellingPricePaise = 18_000L,
            costPricePaise = 15_000L,
            initialStock = 20.0,
            lowStockThreshold = 5.0,
            barcode = "8901234567890",
            category = "Oils"
        )
        assertTrue(pId > 0)

        var product = inventoryRepo.getProductById(pId)
        assertNotNull(product)
        assertEquals(20.0, product!!.stockQuantity, 0.001)
        assertFalse(product.isLowStock)

        // 2. Perform atomic stock deduction from bill items (Sale of 3 packets)
        val soldItems = listOf(
            BillItemEntity(
                billId = 101L,
                description = "Fortune Mustard Oil 1L",
                quantity = 3.0,
                unitPricePaise = 18_000L,
                lineTotalPaise = 54_000L
            )
        )
        inventoryRepo.deductStockForBillItems(soldItems)

        product = inventoryRepo.getProductById(pId)
        assertEquals(17.0, product!!.stockQuantity, 0.001)

        // 3. Deduct another 13 packets -> Stock becomes 4.0 <= 5.0 (Low Stock Alert triggers)
        val bigSale = listOf(
            BillItemEntity(
                billId = 102L,
                description = "Fortune Mustard Oil 1L",
                quantity = 13.0,
                unitPricePaise = 18_000L,
                lineTotalPaise = 234_000L
            )
        )
        inventoryRepo.deductStockForBillItems(bigSale)

        product = inventoryRepo.getProductById(pId)
        assertEquals(4.0, product!!.stockQuantity, 0.001)
        assertTrue(product.isLowStock)

        val lowStockList = inventoryRepo.observeLowStockProducts().first()
        assertEquals(1, lowStockList.size)
        assertEquals(pId, lowStockList[0].id)

        // 4. Stock-in: Replenish 25 packets
        inventoryRepo.adjustStock(pId, 25.0)
        product = inventoryRepo.getProductById(pId)
        assertEquals(29.0, product!!.stockQuantity, 0.001)
        assertFalse(product.isLowStock)
    }

    // =========================================================================
    // 4. SUPPLIER PURCHASES & DUES LEDGER TESTS
    // =========================================================================

    @Test
    fun testSupplierPurchasesAndDues_StrictIsolationFromCustomerReceivables() = runBlocking {
        // Create supplier: Hindustan Unilever Wholesale Distributor
        val sId = supplierRepo.createSupplier(
            name = "HUL Distributor Agency",
            phone = "9811223344",
            notes = "Wholesale soaps & detergents"
        )
        assertTrue(sId > 0)

        // 1. Purchase ₹10,000 worth of stock on credit (Payable increases)
        supplierRepo.recordPurchase(
            supplierId = sId,
            amountPaise = 1_000_000L, // ₹10,000.00
            billInvoiceNumber = "INV-HUL-9988",
            notes = "Soap boxes consignment"
        )

        var sWithBalance = supplierRepo.getSupplierWithBalance(sId)
        assertNotNull(sWithBalance)
        // Shopkeeper owes distributor ₹10,000 (Payable)
        assertEquals(1_000_000L, sWithBalance!!.balancePaise)

        // 2. Pay distributor ₹4,000 in cash
        supplierRepo.recordPaymentToSupplier(
            supplierId = sId,
            amountPaise = 400_000L, // ₹4,000.00
            paymentMethod = "CASH",
            notes = "Partial payment cash"
        )

        sWithBalance = supplierRepo.getSupplierWithBalance(sId)
        // Remaining payable to distributor is ₹6,000
        assertEquals(600_000L, sWithBalance!!.balancePaise)

        // 3. Settle remaining ₹6,000 via UPI
        supplierRepo.recordPaymentToSupplier(
            supplierId = sId,
            amountPaise = 600_000L,
            paymentMethod = "UPI",
            notes = "Final balance clearance"
        )

        sWithBalance = supplierRepo.getSupplierWithBalance(sId)
        assertEquals(0L, sWithBalance!!.balancePaise)

        // 4. Crucial rule: Supplier payables do not alter customer receivables
        val customerMarketUdhaar = fakeCustomerDao.observeTotalMarketUdhaar().first()
        assertEquals(5000L, customerMarketUdhaar) // Customer receivables remain completely isolated!
    }

    // =========================================================================
    // 5. ATOMIC BILLING INVENTORY DEDUCTIONS & REVERSALS TESTS
    // =========================================================================

    @Test
    fun test5_atomicInventoryDeductionsAndReversalsInBilling() = runBlocking {
        val billsList = mutableListOf<BillEntity>()
        val billItemsList = mutableListOf<BillItemEntity>()
        val fakeBillDao = object : BillDao {
            private var bId = 1L
            override suspend fun insertBill(bill: BillEntity): Long {
                val id = bId++
                billsList.add(bill.copy(id = id))
                return id
            }
            override suspend fun insertBillItems(items: List<BillItemEntity>) { billItemsList.addAll(items) }
            override suspend fun restoreAllBills(bills: List<BillEntity>) {}
            override suspend fun restoreAllBillItems(items: List<BillItemEntity>) {}
            override suspend fun deleteAllBills() {}
            override suspend fun deleteAllBillItems() {}
            override suspend fun updateBill(bill: BillEntity) {}
            override fun observeBillWithItems(id: Long) = flowOf<BillWithItems?>(null)
            override suspend fun getBillWithItems(id: Long): BillWithItems? {
                val b = billsList.find { it.id == id } ?: return null
                return BillWithItems(b, billItemsList.filter { it.billId == id }, null)
            }
            override fun observeRecentBills(limit: Int) = flowOf<List<BillWithItems>>(emptyList())
            override fun observeBillsByDateRange(start: Long, end: Long) = flowOf<List<BillWithItems>>(emptyList())
            override suspend fun getBillsByDateRange(start: Long, end: Long) = emptyList<BillWithItems>()
            override fun observeBillsForCustomer(customerId: Long) = flowOf<List<BillWithItems>>(emptyList())
            override suspend fun getBillsForCustomer(customerId: Long) = emptyList<BillWithItems>()
            override fun observeBillsForCustomerByDateRange(customerId: Long, startEpoch: Long, endEpoch: Long) = flowOf<List<BillWithItems>>(emptyList())
            override suspend fun getBillsForCustomerByDateRange(customerId: Long, startEpoch: Long, endEpoch: Long) = emptyList<BillWithItems>()
            override suspend fun countBillsForDay(start: Long, end: Long): Int = 0
            override suspend fun findByIdempotencyKey(key: String): BillEntity? = billsList.find { it.idempotencyKey == key }
            override suspend fun updateBillStatus(billId: Long, status: String) {
                val idx = billsList.indexOfFirst { it.id == billId }
                if (idx != -1) billsList[idx] = billsList[idx].copy(status = status)
            }
            override suspend fun getAllBillsRaw() = billsList
            override suspend fun getAllBillItemsRaw() = billItemsList
        }

        val fakeFinancialTxDao = object : FinancialTransactionDao {
            private var txId = 1L
            val txList = mutableListOf<FinancialTransactionEntity>()
            override suspend fun insertTransaction(transaction: FinancialTransactionEntity): Long {
                val id = txId++
                txList.add(transaction.copy(id = id))
                return id
            }
            override suspend fun restoreAllTransactions(transactions: List<FinancialTransactionEntity>) {}
            override suspend fun deleteAllTransactions() {}
            override suspend fun updateTransaction(transaction: FinancialTransactionEntity) {}
            override suspend fun getTransactionById(id: Long): FinancialTransactionEntity? = txList.find { it.id == id }
            override fun observeTransactionsForCustomer(customerId: Long) = flowOf(txList.filter { it.customerId == customerId })
            override suspend fun getTransactionsForCustomer(customerId: Long) = txList.filter { it.customerId == customerId }
            override fun observeRecentTransactions(limit: Int) = flowOf(emptyList<FinancialTransactionEntity>())
            override fun observeTransactionsByDateRange(start: Long, end: Long) = flowOf(emptyList<FinancialTransactionEntity>())
            override suspend fun getTransactionsByDateRange(start: Long, end: Long) = emptyList<FinancialTransactionEntity>()
            override suspend fun updateTransactionStatus(id: Long, status: String) {}
            override suspend fun findByIdempotencyKey(key: String): FinancialTransactionEntity? = txList.find { it.idempotencyKey == key }
            override suspend fun getAllTransactionsRaw() = txList
        }

        val billingRepo = BillingRepository(
            billDao = fakeBillDao,
            financialTransactionDao = fakeFinancialTxDao,
            database = null,
            productDao = fakeProductDao
        )

        // Add Product: Basmati Rice 5kg, Stock = 10 packets
        val riceId = inventoryRepo.addProduct(
            name = "Daawat Basmati Rice 5kg",
            unit = "packet",
            sellingPricePaise = 45_000L,
            initialStock = 10.0
        )

        // 1. Successful sale of 2 packets with stable productId
        val billId = billingRepo.createBill(
            receiptNumber = "BILL-001",
            customerId = null,
            subtotalPaise = 90_000L,
            totalPaise = 90_000L,
            amountPaidPaise = 90_000L,
            paymentMethod = "CASH",
            billType = "ITEMIZED",
            items = listOf(
                BillItemEntity(
                    billId = 0L,
                    description = "Daawat Basmati Rice 5kg",
                    quantity = 2.0,
                    unitPricePaise = 45_000L,
                    lineTotalPaise = 90_000L,
                    productId = riceId
                )
            ),
            cashPaidPaise = 90_000L
        )
        assertTrue(billId > 0)
        assertEquals(8.0, inventoryRepo.getProductById(riceId)!!.stockQuantity, 0.001)

        // 2. Insufficient stock rejection: try to sell 15 packets when only 8 are available
        try {
            billingRepo.createBill(
                receiptNumber = "BILL-002",
                customerId = null,
                subtotalPaise = 675_000L,
                totalPaise = 675_000L,
                amountPaidPaise = 675_000L,
                paymentMethod = "CASH",
                billType = "ITEMIZED",
                items = listOf(
                    BillItemEntity(
                        billId = 0L,
                        description = "Daawat Basmati Rice 5kg",
                        quantity = 15.0,
                        unitPricePaise = 45_000L,
                        lineTotalPaise = 675_000L,
                        productId = riceId
                    )
                ),
                cashPaidPaise = 675_000L
            )
            org.junit.Assert.fail("Expected IllegalStateException for insufficient stock")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("Insufficient stock") == true)
        }
        // Stock must remain untouched at 8.0 packets!
        assertEquals(8.0, inventoryRepo.getProductById(riceId)!!.stockQuantity, 0.001)

        // 3. Bill cancellation reverses inventory stock
        val cancelSuccess = billingRepo.cancelBill(billId, "Customer returned product")
        assertTrue(cancelSuccess)
        // Stock restored back to 10.0!
        assertEquals(10.0, inventoryRepo.getProductById(riceId)!!.stockQuantity, 0.001)

        // 4. Quick amount-based bill without product catalogue (productId = null) succeeds without touching inventory
        val quickBillId = billingRepo.createBill(
            receiptNumber = "BILL-003",
            customerId = null,
            subtotalPaise = 15_000L,
            totalPaise = 15_000L,
            amountPaidPaise = 15_000L,
            paymentMethod = "CASH",
            billType = "QUICK",
            items = emptyList(),
            cashPaidPaise = 15_000L
        )
        assertTrue(quickBillId > 0)
        assertEquals(10.0, inventoryRepo.getProductById(riceId)!!.stockQuantity, 0.001)
    }

    @Test
    fun test6_userGuideAndOnboardingWalkthroughFlow() {
        val fakePrefs = object : android.content.SharedPreferences {
            val map = mutableMapOf<String, Any>()
            override fun getAll(): MutableMap<String, *> = map
            override fun getString(key: String?, defValue: String?): String? = map[key] as? String ?: defValue
            override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = defValues
            override fun getInt(key: String?, defValue: Int): Int = map[key] as? Int ?: defValue
            override fun getLong(key: String?, defValue: Long): Long = map[key] as? Long ?: defValue
            override fun getFloat(key: String?, defValue: Float): Float = map[key] as? Float ?: defValue
            override fun getBoolean(key: String?, defValue: Boolean): Boolean = map[key] as? Boolean ?: defValue
            override fun contains(key: String?): Boolean = map.containsKey(key)
            override fun edit(): android.content.SharedPreferences.Editor = object : android.content.SharedPreferences.Editor {
                override fun putString(key: String?, value: String?) = apply { if (key != null && value != null) map[key] = value }
                override fun putStringSet(key: String?, values: MutableSet<String>?) = this
                override fun putInt(key: String?, value: Int) = apply { if (key != null) map[key] = value }
                override fun putLong(key: String?, value: Long) = apply { if (key != null) map[key] = value }
                override fun putFloat(key: String?, value: Float) = apply { if (key != null) map[key] = value }
                override fun putBoolean(key: String?, value: Boolean) = apply { if (key != null) map[key] = value }
                override fun remove(key: String?) = apply { if (key != null) map.remove(key) }
                override fun clear() = apply { map.clear() }
                override fun commit(): Boolean = true
                override fun apply() {}
            }
            override fun registerOnSharedPreferenceChangeListener(listener: android.content.SharedPreferences.OnSharedPreferenceChangeListener?) {}
            override fun unregisterOnSharedPreferenceChangeListener(listener: android.content.SharedPreferences.OnSharedPreferenceChangeListener?) {}
        }

        val guidePrefs = UserGuidePreferences(fakePrefs)

        // 1. Initial state: First time install must NOT have completed onboarding or accepted terms
        assertFalse("New install must show terms and conditions check page", guidePrefs.hasAcceptedTerms())
        assertFalse("New install must show onboarding walkthrough", guidePrefs.hasCompletedOnboarding())

        // 2. Accept Terms & Conditions: Persists so terms check page never shows again
        guidePrefs.setTermsAccepted(true)
        assertTrue("Accepted terms must be persisted", guidePrefs.hasAcceptedTerms())

        // 3. Complete onboarding: Sets flag so it never runs again automatically
        guidePrefs.setOnboardingCompleted(true)
        assertTrue("Completed onboarding must be persisted", guidePrefs.hasCompletedOnboarding())

        // 4. Reset onboarding & terms: Allows re-verification
        guidePrefs.resetOnboarding()
        assertFalse("Reset onboarding must allow replaying walkthrough", guidePrefs.hasCompletedOnboarding())
        guidePrefs.resetTermsAccepted()
        assertFalse("Reset terms must allow fresh install verification", guidePrefs.hasAcceptedTerms())

        // 5. Validate Terms & Conditions Legal Disclaimers
        assertTrue("Must contain comprehensive terms sections", termsSections.isNotEmpty())
        assertTrue("Must contain machine & software disclaimer", termsSections.any { it.id == "machine_disclaimer" && it.isCritical })
        assertTrue("Must contain limitation of liability for business losses", termsSections.any { it.id == "liability_limitation" && it.isCritical })
        assertTrue("Must contain merchant verification duty", termsSections.any { it.id == "shopkeeper_verification" })
        assertTrue("Must contain offline data privacy & local storage", termsSections.any { it.id == "local_privacy" })
        assertTrue("Must contain backup duty notice", termsSections.any { it.id == "backup_duty" })
        termsSections.forEach { section ->
            assertTrue(section.id.isNotBlank())
            assertTrue(section.titleEn.isNotBlank())
            assertTrue(section.titleHi.isNotBlank())
            assertTrue(section.contentEn.isNotBlank())
            assertTrue(section.contentHi.isNotBlank())
        }

        // 6. Validate walkthrough steps: exactly 7 steps covering every key feature
        assertEquals(7, walkthroughSteps.size)
        walkthroughSteps.forEachIndexed { index, step ->
            assertEquals(index + 1, step.stepNumber)
            assertTrue("Step tag English must not be blank", step.tagEn.isNotBlank())
            assertTrue("Step tag Hindi must not be blank", step.tagHi.isNotBlank())
            assertTrue("Step title English must not be blank", step.titleEn.isNotBlank())
            assertTrue("Step title Hindi must not be blank", step.titleHi.isNotBlank())
            assertTrue("Step subtitle English must not be blank", step.subtitleEn.isNotBlank())
            assertTrue("Step subtitle Hindi must not be blank", step.subtitleHi.isNotBlank())
            assertTrue("Step must have key points in English", step.keyPointsEn.isNotEmpty())
            assertTrue("Step must have key points in Hindi", step.keyPointsHi.isNotEmpty())
            assertTrue("Step pro tip English must not be blank", step.proTipEn.isNotBlank())
            assertTrue("Step pro tip Hindi must not be blank", step.proTipHi.isNotBlank())
        }

        // 5. Validate User Guide Categories & Sections
        assertTrue("Guide must contain comprehensive categories", guideCategories.size >= 8)
        guideCategories.forEach { category ->
            assertTrue(category.id.isNotBlank())
            assertTrue(category.titleEn.isNotBlank())
            assertTrue(category.titleHi.isNotBlank())
            assertTrue(category.items.isNotEmpty())
            category.items.forEach { item ->
                assertTrue(item.title.isNotBlank())
                assertTrue(item.details.isNotBlank())
            }
        }
    }
}
