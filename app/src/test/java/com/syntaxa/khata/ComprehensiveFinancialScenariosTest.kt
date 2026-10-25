package com.syntaxa.khata

import com.syntaxa.khata.core.model.DateTimeUtils
import com.syntaxa.khata.data.local.dao.BillDao
import com.syntaxa.khata.data.local.dao.CustomerDao
import com.syntaxa.khata.data.local.dao.ExpenseDao
import com.syntaxa.khata.data.local.dao.FinancialTransactionDao
import com.syntaxa.khata.data.local.entity.BillConstants
import com.syntaxa.khata.data.local.entity.BillEntity
import com.syntaxa.khata.data.local.entity.BillItemEntity
import com.syntaxa.khata.data.local.entity.CustomerEntity
import com.syntaxa.khata.data.local.entity.ExpenseConstants
import com.syntaxa.khata.data.local.entity.ExpenseEntity
import com.syntaxa.khata.data.local.entity.FinancialTransactionEntity
import com.syntaxa.khata.data.local.entity.TransactionConstants
import com.syntaxa.khata.data.repository.BillingRepository
import com.syntaxa.khata.data.repository.CustomerRepository
import com.syntaxa.khata.data.repository.DailyReportRepository
import com.syntaxa.khata.data.repository.ExpenseRepository
import com.syntaxa.khata.data.repository.FinancialRepository
import com.syntaxa.khata.domain.model.BillWithItems
import com.syntaxa.khata.domain.model.CustomerWithBalance
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

class ComprehensiveFinancialScenariosTest {

    private val customers = mutableListOf<CustomerEntity>()
    private val transactions = mutableListOf<FinancialTransactionEntity>()
    private val bills = mutableListOf<BillEntity>()
    private val billItems = mutableListOf<BillItemEntity>()
    private val expenses = mutableListOf<ExpenseEntity>()

    private val idGen = AtomicLong(1)

    private val fakeCustomerDao = object : CustomerDao {
        override suspend fun insertCustomer(customer: CustomerEntity): Long {
            val id = idGen.getAndIncrement()
            customers.add(customer.copy(id = id))
            return id
        }
        override suspend fun restoreAllCustomers(customers: List<CustomerEntity>) {
            this@ComprehensiveFinancialScenariosTest.customers.addAll(customers)
        }
        override suspend fun deleteAllCustomers() {
            customers.clear()
        }
        override suspend fun updateCustomer(customer: CustomerEntity) {
            val idx = customers.indexOfFirst { it.id == customer.id }
            if (idx != -1) customers[idx] = customer
        }
        override suspend fun getCustomerById(id: Long): CustomerEntity? = customers.firstOrNull { it.id == id }
        override fun observeCustomerById(id: Long): Flow<CustomerEntity?> = flow {
            emit(getCustomerById(id))
        }
        override fun observeActiveCustomersWithBalance(): Flow<List<CustomerWithBalance>> = flowOf(emptyList())
        override fun searchActiveCustomersWithBalance(query: String): Flow<List<CustomerWithBalance>> = flowOf(emptyList())
        override fun observeCustomerWithBalance(id: Long): Flow<CustomerWithBalance?> = flow {
            emit(getCustomerWithBalance(id))
        }
        override suspend fun getCustomerWithBalance(id: Long): CustomerWithBalance? {
            val c = getCustomerById(id) ?: return null
            var bal = 0L
            for (t in transactions.filter { it.customerId == id && it.status == TransactionConstants.STATUS_ACTIVE }) {
                if (t.type == TransactionConstants.TYPE_UDHAAR_GIVEN) bal += t.amountPaise
                else if (t.type == TransactionConstants.TYPE_PAYMENT_RECEIVED) bal -= t.amountPaise
            }
            return CustomerWithBalance(c.id, c.name, c.phone, c.notes, c.createdAt, c.archivedAt, bal, null, 0)
        }
        override fun observeArchivedCustomersWithBalance(): Flow<List<CustomerWithBalance>> = flowOf(emptyList())
        override suspend fun countByPhone(phone: String, excludeId: Long?): Int = customers.count { it.phone == phone && it.id != excludeId }
        override suspend fun setCustomerArchived(customerId: Long, archivedAt: Long?, updatedAt: Long) {
            val idx = customers.indexOfFirst { it.id == customerId }
            if (idx != -1) customers[idx] = customers[idx].copy(archivedAt = archivedAt, updatedAt = updatedAt)
        }
        override suspend fun updateCustomerDueDate(customerId: Long, dueDate: Long?, updatedAt: Long) {
            val idx = customers.indexOfFirst { it.id == customerId }
            if (idx != -1) customers[idx] = customers[idx].copy(dueDate = dueDate, updatedAt = updatedAt)
        }
        override fun observeTotalMarketUdhaar(): Flow<Long> = flow {
            var sum = 0L
            for (c in customers.filter { it.archivedAt == null }) {
                val b = getCustomerWithBalance(c.id)?.balancePaise ?: 0L
                if (b > 0) sum += b
            }
            emit(sum)
        }
        override suspend fun getAllCustomersRaw(): List<CustomerEntity> = customers
    }

    private val fakeTransactionDao = object : FinancialTransactionDao {
        override suspend fun insertTransaction(transaction: FinancialTransactionEntity): Long {
            val id = idGen.getAndIncrement()
            transactions.add(transaction.copy(id = id))
            return id
        }
        override suspend fun restoreAllTransactions(transactions: List<FinancialTransactionEntity>) {
            this@ComprehensiveFinancialScenariosTest.transactions.addAll(transactions)
        }
        override suspend fun deleteAllTransactions() {
            transactions.clear()
        }
        override suspend fun updateTransaction(transaction: FinancialTransactionEntity) {
            val idx = transactions.indexOfFirst { it.id == transaction.id }
            if (idx != -1) transactions[idx] = transaction
        }
        override suspend fun getTransactionById(id: Long): FinancialTransactionEntity? = transactions.firstOrNull { it.id == id }
        override fun observeTransactionsForCustomer(customerId: Long): Flow<List<FinancialTransactionEntity>> =
            flowOf(transactions.filter { it.customerId == customerId })
        override suspend fun getTransactionsForCustomer(customerId: Long): List<FinancialTransactionEntity> =
            transactions.filter { it.customerId == customerId }
        override fun observeRecentTransactions(limit: Int): Flow<List<FinancialTransactionEntity>> =
            flowOf(transactions.takeLast(limit).reversed())
        override fun observeTransactionsByDateRange(startEpoch: Long, endEpoch: Long): Flow<List<FinancialTransactionEntity>> =
            flowOf(transactions.filter { it.occurredAt in startEpoch..endEpoch })
        override suspend fun getTransactionsByDateRange(startEpoch: Long, endEpoch: Long): List<FinancialTransactionEntity> =
            transactions.filter { it.occurredAt in startEpoch..endEpoch }
        override suspend fun updateTransactionStatus(id: Long, status: String) {
            val idx = transactions.indexOfFirst { it.id == id }
            if (idx != -1) transactions[idx] = transactions[idx].copy(status = status)
        }
        override suspend fun findByIdempotencyKey(idempotencyKey: String): FinancialTransactionEntity? =
            transactions.firstOrNull { it.idempotencyKey == idempotencyKey }
        override suspend fun getAllTransactionsRaw(): List<FinancialTransactionEntity> = transactions
        override suspend fun executeReversal(targetTransactionId: Long, reason: String): Long {
            val target = getTransactionById(targetTransactionId)
                ?: throw IllegalArgumentException("Transaction not found")
            if (target.status == TransactionConstants.STATUS_REVERSED) {
                throw IllegalStateException("Transaction is already reversed")
            }
            updateTransactionStatus(target.id, TransactionConstants.STATUS_REVERSED)
            val rev = FinancialTransactionEntity(
                id = idGen.getAndIncrement(),
                type = TransactionConstants.TYPE_REVERSAL,
                customerId = target.customerId,
                amountPaise = target.amountPaise,
                paymentMethod = target.paymentMethod,
                description = "Reversal: $reason",
                occurredAt = System.currentTimeMillis(),
                createdAt = System.currentTimeMillis(),
                reversalOfTransactionId = target.id,
                relatedBillId = target.relatedBillId,
                idempotencyKey = "REV-${UUID.randomUUID()}",
                status = TransactionConstants.STATUS_REVERSED
            )
            transactions.add(rev)
            return rev.id
        }
    }

    private val fakeBillDao = object : BillDao {
        override suspend fun insertBill(bill: BillEntity): Long {
            val id = idGen.getAndIncrement()
            bills.add(bill.copy(id = id))
            return id
        }
        override suspend fun insertBillItems(items: List<BillItemEntity>) {
            billItems.addAll(items)
        }
        override suspend fun restoreAllBills(bills: List<BillEntity>) {
            this@ComprehensiveFinancialScenariosTest.bills.addAll(bills)
        }
        override suspend fun restoreAllBillItems(items: List<BillItemEntity>) {
            billItems.addAll(items)
        }
        override suspend fun deleteAllBills() {
            bills.clear()
        }
        override suspend fun deleteAllBillItems() {
            billItems.clear()
        }
        override suspend fun updateBill(bill: BillEntity) {
            val idx = bills.indexOfFirst { it.id == bill.id }
            if (idx != -1) bills[idx] = bill
        }
        override fun observeBillWithItems(id: Long): Flow<BillWithItems?> = flow {
            emit(getBillWithItems(id))
        }
        override suspend fun getBillWithItems(id: Long): BillWithItems? {
            val b = bills.firstOrNull { it.id == id } ?: return null
            val items = billItems.filter { it.billId == id }
            val c = customers.firstOrNull { it.id == b.customerId }
            return BillWithItems(b, items, c)
        }
        override fun observeRecentBills(limit: Int): Flow<List<BillWithItems>> = flowOf(emptyList())
        override fun observeBillsByDateRange(startEpoch: Long, endEpoch: Long): Flow<List<BillWithItems>> = flow {
            emit(bills.filter { it.createdAt in startEpoch..endEpoch }.mapNotNull { getBillWithItems(it.id) })
        }
        override suspend fun getBillsByDateRange(startEpoch: Long, endEpoch: Long): List<BillWithItems> =
            bills.filter { it.createdAt in startEpoch..endEpoch }.mapNotNull { getBillWithItems(it.id) }
        override fun observeBillsForCustomer(customerId: Long): Flow<List<BillWithItems>> = flow {
            emit(bills.filter { it.customerId == customerId }.mapNotNull { getBillWithItems(it.id) })
        }
        override suspend fun getBillsForCustomer(customerId: Long): List<BillWithItems> =
            bills.filter { it.customerId == customerId }.mapNotNull { getBillWithItems(it.id) }
        override fun observeBillsForCustomerByDateRange(customerId: Long, startEpoch: Long, endEpoch: Long): Flow<List<BillWithItems>> = flow {
            emit(bills.filter { it.customerId == customerId && it.createdAt in startEpoch..endEpoch }.mapNotNull { getBillWithItems(it.id) })
        }
        override suspend fun getBillsForCustomerByDateRange(customerId: Long, startEpoch: Long, endEpoch: Long): List<BillWithItems> =
            bills.filter { it.customerId == customerId && it.createdAt in startEpoch..endEpoch }.mapNotNull { getBillWithItems(it.id) }
        override suspend fun countBillsForDay(startOfDay: Long, endOfDay: Long): Int =
            bills.count { it.createdAt in startOfDay..endOfDay }
        override suspend fun updateBillStatus(billId: Long, status: String) {
            val idx = bills.indexOfFirst { it.id == billId }
            if (idx != -1) bills[idx] = bills[idx].copy(status = status)
        }
        override suspend fun findByIdempotencyKey(idempotencyKey: String): BillEntity? =
            bills.firstOrNull { it.idempotencyKey == idempotencyKey }
        override suspend fun getAllBillsRaw(): List<BillEntity> = bills
        override suspend fun getAllBillItemsRaw(): List<BillItemEntity> = billItems
        override suspend fun createBillWithItems(bill: BillEntity, items: List<BillItemEntity>): Long {
            val billId = insertBill(bill)
            if (items.isNotEmpty()) {
                val mapped = items.map { it.copy(billId = billId) }
                insertBillItems(mapped)
            }
            return billId
        }
    }

    private val fakeExpenseDao = object : ExpenseDao {
        override suspend fun insertExpense(expense: ExpenseEntity): Long {
            val id = idGen.getAndIncrement()
            expenses.add(expense.copy(id = id))
            return id
        }
        override suspend fun restoreAllExpenses(expenses: List<ExpenseEntity>) {
            this@ComprehensiveFinancialScenariosTest.expenses.addAll(expenses)
        }
        override suspend fun deleteAllExpenses() {
            expenses.clear()
        }
        override suspend fun updateExpense(expense: ExpenseEntity) {
            val idx = expenses.indexOfFirst { it.id == expense.id }
            if (idx != -1) expenses[idx] = expense
        }
        override fun observeActiveExpenses(status: String): Flow<List<ExpenseEntity>> =
            flowOf(expenses.filter { it.status == status })
        override fun observeExpensesByDateRange(startEpoch: Long, endEpoch: Long): Flow<List<ExpenseEntity>> =
            flowOf(expenses.filter { it.occurredAt in startEpoch..endEpoch })
        override suspend fun getExpensesByDateRange(startEpoch: Long, endEpoch: Long): List<ExpenseEntity> =
            expenses.filter { it.occurredAt in startEpoch..endEpoch }
        override suspend fun updateExpenseStatus(id: Long, status: String) {
            val idx = expenses.indexOfFirst { it.id == id }
            if (idx != -1) expenses[idx] = expenses[idx].copy(status = status)
        }
        override suspend fun getExpenseById(id: Long): ExpenseEntity? = expenses.firstOrNull { it.id == id }
        override suspend fun getAllExpensesRaw(): List<ExpenseEntity> = expenses
    }

    private lateinit var customerRepo: CustomerRepository
    private lateinit var financialRepo: FinancialRepository
    private lateinit var billingRepo: BillingRepository
    private lateinit var expenseRepo: ExpenseRepository
    private lateinit var dailyReportRepo: DailyReportRepository

    @Before
    fun setUp() {
        customers.clear()
        transactions.clear()
        bills.clear()
        billItems.clear()
        expenses.clear()

        customerRepo = CustomerRepository(fakeCustomerDao)
        financialRepo = FinancialRepository(fakeTransactionDao, fakeCustomerDao)
        billingRepo = BillingRepository(fakeBillDao, fakeTransactionDao, null)
        expenseRepo = ExpenseRepository(fakeExpenseDao)
        dailyReportRepo = DailyReportRepository(billingRepo, financialRepo, expenseRepo, fakeCustomerDao)
    }

    @Test
    fun testPRD_Scenario1_FullUdhaarAndRepaymentLifecycle() = runBlocking {
        // 1. Create a customer with ₹1,000 Udhaar.
        val customerId = customerRepo.createCustomer("Ramesh Kumar", "9876543210")
        financialRepo.recordUdhaar(customerId, 100_000L, "Initial Udhaar")

        var stmt = financialRepo.getCustomerStatement(customerId)
        assertEquals(100_000L, stmt.currentBalancePaise)

        // 2. Receive ₹400 and verify ₹600 remains.
        financialRepo.recordPayment(customerId, 40_000L, TransactionConstants.METHOD_CASH)
        stmt = financialRepo.getCustomerStatement(customerId)
        assertEquals(60_000L, stmt.currentBalancePaise)

        // 3. Receive ₹600 and verify balance becomes zero.
        financialRepo.recordPayment(customerId, 60_000L, TransactionConstants.METHOD_UPI)
        stmt = financialRepo.getCustomerStatement(customerId)
        assertEquals(0L, stmt.currentBalancePaise)
    }

    @Test
    fun testNegativeOrInvalidPaymentRejected() = runBlocking {
        val customerId = customerRepo.createCustomer("Mohan Lal", "9876543211")
        try {
            financialRepo.recordPayment(customerId, -5000L)
            fail("Expected IllegalArgumentException for negative payment")
        } catch (_: IllegalArgumentException) { }

        try {
            financialRepo.recordPayment(customerId, 0L)
            fail("Expected IllegalArgumentException for zero payment")
        } catch (_: IllegalArgumentException) { }

        assertEquals(0, transactions.size)
    }

    @Test
    fun testCreditSaleRequiresCustomerAndCannotSilentlyLoseUnpaidAmount() = runBlocking {
        // Attempting to create an unpaid sale without selecting a customer MUST fail
        try {
            billingRepo.createBill(
                receiptNumber = "BILL-001",
                customerId = null,
                subtotalPaise = 50_000L,
                totalPaise = 50_000L,
                amountPaidPaise = 30_000L, // 20,000 unpaid!
                paymentMethod = TransactionConstants.METHOD_CASH,
                billType = BillConstants.TYPE_QUICK,
                cashPaidPaise = 30_000L
            )
            fail("Expected exception: credit sales must require a customer")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Customer must be selected for credit") == true)
        }

        assertEquals(0, bills.size)
        assertEquals(0, transactions.size)
    }

    @Test
    fun testFullyPaidWalkInCustomerSaleAllowed() = runBlocking {
        val billId = billingRepo.createBill(
            receiptNumber = "BILL-WALKIN",
            customerId = null,
            subtotalPaise = 25_000L,
            totalPaise = 25_000L,
            amountPaidPaise = 25_000L,
            paymentMethod = TransactionConstants.METHOD_CASH,
            billType = BillConstants.TYPE_QUICK,
            cashPaidPaise = 25_000L
        )

        assertTrue(billId > 0)
        assertEquals(1, bills.size)
        assertEquals(0, transactions.size) // No Udhaar transaction created for walk-in fully paid
    }

    @Test
    fun testAtomicBillCreationWithCustomerUdhaar() = runBlocking {
        val customerId = customerRepo.createCustomer("Sunita Devi", "9876543212")

        val billId = billingRepo.createBill(
            receiptNumber = "BILL-500",
            customerId = customerId,
            subtotalPaise = 50_000L,
            totalPaise = 50_000L,
            amountPaidPaise = 30_000L,
            paymentMethod = TransactionConstants.METHOD_CASH,
            billType = BillConstants.TYPE_QUICK,
            cashPaidPaise = 30_000L
        )

        // Verify bill saved
        assertEquals(1, bills.size)
        assertEquals(billId, bills[0].id)

        // Verify customer Khata Udhaar atomically recorded
        assertEquals(1, transactions.size)
        val tx = transactions[0]
        assertEquals(TransactionConstants.TYPE_UDHAAR_GIVEN, tx.type)
        assertEquals(20_000L, tx.amountPaise)
        assertEquals(billId, tx.relatedBillId)
        assertEquals(customerId, tx.customerId)

        val stmt = financialRepo.getCustomerStatement(customerId)
        assertEquals(20_000L, stmt.currentBalancePaise)
    }

    @Test
    fun testBillIdempotencyPreventsDuplicateWrites() = runBlocking {
        val customerId = customerRepo.createCustomer("Amit Patel", "9876543213")
        val key = "IDEM-KEY-UNIQUE-123"

        val id1 = billingRepo.createBill(
            receiptNumber = "BILL-DUP-1",
            customerId = customerId,
            subtotalPaise = 10_000L,
            totalPaise = 10_000L,
            amountPaidPaise = 10_000L,
            paymentMethod = TransactionConstants.METHOD_CASH,
            billType = BillConstants.TYPE_QUICK,
            cashPaidPaise = 10_000L,
            idempotencyKey = key
        )

        // Resubmit with same key
        val id2 = billingRepo.createBill(
            receiptNumber = "BILL-DUP-1",
            customerId = customerId,
            subtotalPaise = 10_000L,
            totalPaise = 10_000L,
            amountPaidPaise = 10_000L,
            paymentMethod = TransactionConstants.METHOD_CASH,
            billType = BillConstants.TYPE_QUICK,
            cashPaidPaise = 10_000L,
            idempotencyKey = key
        )

        assertEquals(id1, id2)
        assertEquals(1, bills.size)
    }

    @Test
    fun testTransactionReversalAndDuplicateReversalPrevention() = runBlocking {
        val customerId = customerRepo.createCustomer("Kavita Sharma", "9876543214")
        val txId = financialRepo.recordUdhaar(customerId, 15_000L, "Initial Udhaar")

        var stmt = financialRepo.getCustomerStatement(customerId)
        assertEquals(15_000L, stmt.currentBalancePaise)

        // Reverse transaction
        val revId = financialRepo.reverseTransaction(txId, "Wrong entry entered")
        assertTrue(revId > 0)

        // Verify balance rolled back to 0
        stmt = financialRepo.getCustomerStatement(customerId)
        assertEquals(0L, stmt.currentBalancePaise)

        // Attempting to reverse the same transaction again must be rejected
        try {
            financialRepo.reverseTransaction(txId, "Second reversal attempt")
            fail("Expected IllegalStateException for duplicate reversal")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("already reversed") == true)
        }
    }

    @Test
    fun testBillCancellationReversesCustomerUdhaar() = runBlocking {
        val customerId = customerRepo.createCustomer("Deepak Verma", "9876543215")
        val billId = billingRepo.createBill(
            receiptNumber = "BILL-CANCEL-TEST",
            customerId = customerId,
            subtotalPaise = 40_000L,
            totalPaise = 40_000L,
            amountPaidPaise = 10_000L,
            paymentMethod = TransactionConstants.METHOD_CASH,
            billType = BillConstants.TYPE_QUICK,
            cashPaidPaise = 10_000L
        )

        assertEquals(30_000L, financialRepo.getCustomerStatement(customerId).currentBalancePaise)

        // Cancel bill
        billingRepo.cancelBill(billId, "Customer returned items")

        // Customer balance must return to 0
        assertEquals(0L, financialRepo.getCustomerStatement(customerId).currentBalancePaise)
        assertEquals(BillConstants.STATUS_CANCELLED, fakeBillDao.getBillWithItems(billId)?.bill?.status)
    }

    @Test
    fun testSplitPaymentAndDailyReportingCalculations() = runBlocking {
        // 1. Bill 1: ₹500 total, Split ₹300 Cash + ₹200 UPI fully paid
        billingRepo.createBill(
            receiptNumber = "BILL-SPLIT",
            customerId = null,
            subtotalPaise = 50_000L,
            totalPaise = 50_000L,
            amountPaidPaise = 50_000L,
            paymentMethod = "SPLIT",
            billType = BillConstants.TYPE_QUICK,
            cashPaidPaise = 30_000L,
            upiPaidPaise = 20_000L
        )

        // 2. Bill 2: ₹200 total, Paid via OTHER (Bank transfer / Cheque)
        billingRepo.createBill(
            receiptNumber = "BILL-OTHER",
            customerId = null,
            subtotalPaise = 20_000L,
            totalPaise = 20_000L,
            amountPaidPaise = 20_000L,
            paymentMethod = TransactionConstants.METHOD_OTHER,
            billType = BillConstants.TYPE_QUICK,
            otherPaidPaise = 20_000L
        )

        // 3. Cash expense of ₹100
        expenseRepo.recordExpense(
            amountPaise = 10_000L,
            category = ExpenseConstants.CAT_STOCK,
            description = "Milk stock",
            paymentMethod = TransactionConstants.METHOD_CASH
        )

        val summary = dailyReportRepo.getDailySummary()

        // Total sales = 500 + 200 = 700
        assertEquals(70_000L, summary.totalSalesPaise)
        // Cash sales = 300
        assertEquals(30_000L, summary.cashSalesPaise)
        // UPI sales = 200
        assertEquals(20_000L, summary.upiSalesPaise)
        // Other sales = 200 (NOT lumped into cash!)
        assertEquals(20_000L, summary.otherSalesPaise)

        // Total Cash collected = 300
        assertEquals(30_000L, summary.totalCashCollectedPaise)
        // Total UPI collected = 200
        assertEquals(20_000L, summary.totalUpiCollectedPaise)
        // Total Other collected = 200
        assertEquals(20_000L, summary.totalOtherCollectedPaise)

        // Cash expenses = 100
        assertEquals(10_000L, summary.cashExpensesPaise)
        // Net Cash Movement = Cash collected (300) - Cash expenses (100) = 200
        assertEquals(20_000L, summary.netCashMovementPaise)
    }

    @Test
    fun testFinancialScenarios1To6_ExactAuditSequence() = runBlocking {
        val customerId = customerRepo.createCustomer("Raju Kirana Customer", "9988776655")

        // 1. Create a ₹500 credit sale (unpaid)
        val billId = billingRepo.createBill(
            receiptNumber = "BILL-AUDIT-500",
            customerId = customerId,
            subtotalPaise = 50_000L, // ₹500.00
            totalPaise = 50_000L,
            amountPaidPaise = 0L,    // Fully credit
            paymentMethod = TransactionConstants.METHOD_CREDIT_LEDGER,
            billType = BillConstants.TYPE_QUICK
        )
        assertTrue(billId > 0)

        // 2. Record a ₹200 payment
        financialRepo.recordPayment(customerId, 20_000L, TransactionConstants.METHOD_CASH, "Partial payment 1")

        // 3. Verify ₹300 remains outstanding
        var statement = financialRepo.getCustomerStatement(customerId)
        assertEquals(30_000L, statement.currentBalancePaise) // ₹300.00

        // 4. Record the remaining ₹300 later
        financialRepo.recordPayment(customerId, 30_000L, TransactionConstants.METHOD_UPI, "Settling balance")

        // 5. Verify the outstanding balance becomes zero
        statement = financialRepo.getCustomerStatement(customerId)
        assertEquals(0L, statement.currentBalancePaise)

        // 6. Verify collections reflect payments rather than repeating the sale amount
        val summary = dailyReportRepo.getDailySummary()
        assertEquals(50_000L, summary.totalSalesPaise) // Sale is ₹500
        assertEquals(20_000L, summary.cashKhataCollectedPaise) // Cash Khata recovered is ₹200
        assertEquals(30_000L, summary.upiKhataCollectedPaise) // UPI Khata recovered is ₹300
        assertEquals(50_000L, summary.udhaarRecoveredTodayPaise) // Total recovered is ₹500
        assertEquals(50_000L, summary.totalCashCollectedPaise + summary.totalUpiCollectedPaise)
    }

    @Test
    fun testLargeAmountsAndPaisePrecision() = runBlocking {
        val customerId = customerRepo.createCustomer("Wholesale Buyer", "9123456780")

        // Test ₹9,999,999.99 (999,999,999 paise)
        val largeAmountPaise = 999_999_999L
        financialRepo.recordUdhaar(customerId, largeAmountPaise, "Large consignment")

        var stmt = financialRepo.getCustomerStatement(customerId)
        assertEquals(largeAmountPaise, stmt.currentBalancePaise)

        // Pay exactly 1 paisa
        financialRepo.recordPayment(customerId, 1L, TransactionConstants.METHOD_CASH, "1 paisa test")
        stmt = financialRepo.getCustomerStatement(customerId)
        assertEquals(largeAmountPaise - 1L, stmt.currentBalancePaise)
    }
}
