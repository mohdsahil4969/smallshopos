package com.syntaxa.khata

import com.syntaxa.khata.data.local.dao.CustomerDao
import com.syntaxa.khata.data.local.dao.FinancialTransactionDao
import com.syntaxa.khata.data.local.entity.CustomerEntity
import com.syntaxa.khata.data.local.entity.FinancialTransactionEntity
import com.syntaxa.khata.data.local.entity.TransactionConstants
import com.syntaxa.khata.data.repository.FinancialRepository
import com.syntaxa.khata.domain.model.CustomerWithBalance
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicLong

class FinancialLedgerTest {

    // In-memory fake DAOs for fast and hermetic financial testing
    private val transactions = mutableListOf<FinancialTransactionEntity>()
    private val idCounter = AtomicLong(1)

    private val fakeTransactionDao = object : FinancialTransactionDao {
        override suspend fun insertTransaction(transaction: FinancialTransactionEntity): Long {
            val id = if (transaction.id == 0L) idCounter.getAndIncrement() else transaction.id
            val saved = transaction.copy(id = id)
            transactions.add(saved)
            return id
        }

        override suspend fun updateTransaction(transaction: FinancialTransactionEntity) {
            val idx = transactions.indexOfFirst { it.id == transaction.id }
            if (idx != -1) transactions[idx] = transaction
        }

        override suspend fun getTransactionById(id: Long): FinancialTransactionEntity? {
            return transactions.firstOrNull { it.id == id }
        }

        override fun observeTransactionsForCustomer(customerId: Long): Flow<List<FinancialTransactionEntity>> {
            return flowOf(transactions.filter { it.customerId == customerId })
        }

        override suspend fun getTransactionsForCustomer(customerId: Long): List<FinancialTransactionEntity> {
            return transactions.filter { it.customerId == customerId }
        }

        override fun observeRecentTransactions(limit: Int): Flow<List<FinancialTransactionEntity>> {
            return flowOf(transactions.takeLast(limit).reversed())
        }

        override fun observeTransactionsByDateRange(startEpoch: Long, endEpoch: Long): Flow<List<FinancialTransactionEntity>> {
            return flowOf(transactions.filter { it.occurredAt in startEpoch..endEpoch })
        }

        override suspend fun getTransactionsByDateRange(startEpoch: Long, endEpoch: Long): List<FinancialTransactionEntity> {
            return transactions.filter { it.occurredAt in startEpoch..endEpoch }
        }

        override suspend fun updateTransactionStatus(id: Long, status: String) {
            val idx = transactions.indexOfFirst { it.id == id }
            if (idx != -1) transactions[idx] = transactions[idx].copy(status = status)
        }

        override suspend fun findByIdempotencyKey(idempotencyKey: String): FinancialTransactionEntity? {
            return transactions.firstOrNull { it.idempotencyKey == idempotencyKey }
        }

        override suspend fun restoreAllTransactions(transactions: List<FinancialTransactionEntity>) {
            this@FinancialLedgerTest.transactions.addAll(transactions)
        }

        override suspend fun deleteAllTransactions() {
            transactions.clear()
        }

        override suspend fun getAllTransactionsRaw(): List<FinancialTransactionEntity> = transactions
    }

    private val fakeCustomer = CustomerEntity(id = 1L, name = "Ramesh Kumar", phone = "9876543210")
    private val fakeCustomerDao = object : CustomerDao {
        override suspend fun insertCustomer(customer: CustomerEntity): Long = 1L
        override suspend fun restoreAllCustomers(customers: List<CustomerEntity>) {}
        override suspend fun deleteAllCustomers() {}
        override suspend fun updateCustomer(customer: CustomerEntity) {}
        override suspend fun getCustomerById(id: Long): CustomerEntity? = fakeCustomer
        override fun observeCustomerById(id: Long): Flow<CustomerEntity?> = flowOf(fakeCustomer)
        override fun observeActiveCustomersWithBalance(): Flow<List<CustomerWithBalance>> = flowOf(emptyList())
        override fun searchActiveCustomersWithBalance(query: String): Flow<List<CustomerWithBalance>> = flowOf(emptyList())
        override fun observeCustomerWithBalance(id: Long): Flow<CustomerWithBalance?> = flowOf(null)
        override suspend fun getCustomerWithBalance(id: Long): CustomerWithBalance? = null
        override fun observeArchivedCustomersWithBalance(): Flow<List<CustomerWithBalance>> = flowOf(emptyList())
        override suspend fun countByPhone(phone: String, excludeId: Long?): Int = 0
        override suspend fun setCustomerArchived(customerId: Long, archivedAt: Long?, updatedAt: Long) {}
        override suspend fun updateCustomerDueDate(customerId: Long, dueDate: Long?, updatedAt: Long) {}
        override fun observeTotalMarketUdhaar(): Flow<Long> = flowOf(0L)
        override suspend fun getAllCustomersRaw(): List<CustomerEntity> = listOf(fakeCustomer)
    }

    private lateinit var financialRepository: FinancialRepository

    @Before
    fun setUp() {
        transactions.clear()
        financialRepository = FinancialRepository(fakeTransactionDao, fakeCustomerDao)
    }

    @Test
    fun testUdhaarAndPartialPaymentBalance_Scenario1() = runBlocking {
        // 1. Give ₹1000 Udhaar (100,000 paise)
        financialRepository.recordUdhaar(customerId = 1L, amountPaise = 100_000L, description = "Rice and Oil")

        var statement = financialRepository.getCustomerStatement(1L)
        assertEquals(100_000L, statement.currentBalancePaise)

        // 2. Receive ₹400 payment (40,000 paise)
        financialRepository.recordPayment(customerId = 1L, amountPaise = 40_000L, paymentMethod = TransactionConstants.METHOD_CASH)

        statement = financialRepository.getCustomerStatement(1L)
        assertEquals(60_000L, statement.currentBalancePaise)

        // 3. Receive remaining ₹600 payment (60,000 paise)
        financialRepository.recordPayment(customerId = 1L, amountPaise = 60_000L, paymentMethod = TransactionConstants.METHOD_UPI)

        statement = financialRepository.getCustomerStatement(1L)
        assertEquals(0L, statement.currentBalancePaise)
    }

    @Test
    fun testIdempotencyPreventsDoubleSubmission() = runBlocking {
        val key = "unique-key-123"
        val id1 = financialRepository.recordUdhaar(customerId = 1L, amountPaise = 50_000L, idempotencyKey = key)
        val id2 = financialRepository.recordUdhaar(customerId = 1L, amountPaise = 50_000L, idempotencyKey = key)

        assertEquals(id1, id2)
        assertEquals(1, transactions.size)
        val statement = financialRepository.getCustomerStatement(1L)
        assertEquals(50_000L, statement.currentBalancePaise)
    }

    @Test
    fun testTransactionReversalPreservesAuditTrail() = runBlocking {
        // Record ₹500 Udhaar
        val txId = financialRepository.recordUdhaar(customerId = 1L, amountPaise = 50_000L)
        var statement = financialRepository.getCustomerStatement(1L)
        assertEquals(50_000L, statement.currentBalancePaise)

        // Reverse it
        financialRepository.reverseTransaction(txId, "Entered by mistake")

        statement = financialRepository.getCustomerStatement(1L)
        // Active balance becomes 0
        assertEquals(0L, statement.currentBalancePaise)
        // Original transaction is REVERSED, plus reversal entry added
        assertEquals(TransactionConstants.STATUS_REVERSED, transactions[0].status)
        assertEquals(2, transactions.size)
    }
}
