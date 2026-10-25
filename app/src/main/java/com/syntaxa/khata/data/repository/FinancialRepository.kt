package com.syntaxa.khata.data.repository

import com.syntaxa.khata.data.local.dao.CustomerDao
import com.syntaxa.khata.data.local.dao.FinancialTransactionDao
import com.syntaxa.khata.data.local.entity.FinancialTransactionEntity
import com.syntaxa.khata.data.local.entity.TransactionConstants
import com.syntaxa.khata.domain.model.CustomerStatement
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class FinancialRepository(
    private val transactionDao: FinancialTransactionDao,
    private val customerDao: CustomerDao
) {
    fun observeTransactionsForCustomer(customerId: Long): Flow<List<FinancialTransactionEntity>> =
        transactionDao.observeTransactionsForCustomer(customerId)

    fun observeRecentTransactions(limit: Int = 10): Flow<List<FinancialTransactionEntity>> =
        transactionDao.observeRecentTransactions(limit)

    fun observeTransactionsByDateRange(startEpoch: Long, endEpoch: Long): Flow<List<FinancialTransactionEntity>> =
        transactionDao.observeTransactionsByDateRange(startEpoch, endEpoch)

    suspend fun getTransactionsByDateRange(startEpoch: Long, endEpoch: Long): List<FinancialTransactionEntity> =
        transactionDao.getTransactionsByDateRange(startEpoch, endEpoch)

    suspend fun recordUdhaar(
        customerId: Long,
        amountPaise: Long,
        description: String = "",
        occurredAt: Long = System.currentTimeMillis(),
        idempotencyKey: String = UUID.randomUUID().toString(),
        relatedBillId: Long? = null
    ): Long {
        require(amountPaise > 0) { "Udhaar amount must be greater than zero" }
        // Verify idempotency to prevent double taps
        val existing = transactionDao.findByIdempotencyKey(idempotencyKey)
        if (existing != null) return existing.id

        val tx = FinancialTransactionEntity(
            type = TransactionConstants.TYPE_UDHAAR_GIVEN,
            customerId = customerId,
            amountPaise = amountPaise,
            paymentMethod = TransactionConstants.METHOD_CREDIT_LEDGER,
            description = description.trim(),
            occurredAt = occurredAt,
            createdAt = System.currentTimeMillis(),
            relatedBillId = relatedBillId,
            idempotencyKey = idempotencyKey,
            status = TransactionConstants.STATUS_ACTIVE
        )
        return transactionDao.insertTransaction(tx)
    }

    suspend fun recordPayment(
        customerId: Long,
        amountPaise: Long,
        paymentMethod: String = TransactionConstants.METHOD_CASH,
        description: String = "",
        occurredAt: Long = System.currentTimeMillis(),
        idempotencyKey: String = UUID.randomUUID().toString()
    ): Long {
        require(amountPaise > 0) { "Payment amount must be greater than zero" }
        val existing = transactionDao.findByIdempotencyKey(idempotencyKey)
        if (existing != null) return existing.id

        val tx = FinancialTransactionEntity(
            type = TransactionConstants.TYPE_PAYMENT_RECEIVED,
            customerId = customerId,
            amountPaise = amountPaise,
            paymentMethod = paymentMethod,
            description = description.trim(),
            occurredAt = occurredAt,
            createdAt = System.currentTimeMillis(),
            idempotencyKey = idempotencyKey,
            status = TransactionConstants.STATUS_ACTIVE
        )
        return transactionDao.insertTransaction(tx)
    }

    suspend fun reverseTransaction(transactionId: Long, reason: String = ""): Long {
        return transactionDao.executeReversal(transactionId, reason.trim())
    }

    suspend fun getCustomerStatement(
        customerId: Long,
        startEpoch: Long = 0L,
        endEpoch: Long = Long.MAX_VALUE
    ): CustomerStatement {
        val customer = customerDao.getCustomerById(customerId)
            ?: throw IllegalArgumentException("Customer not found with id $customerId")
        val allTransactions = transactionDao.getTransactionsForCustomer(customerId)

        var openingBalance = 0L
        var totalUdhaarInPeriod = 0L
        var totalPaidInPeriod = 0L
        val periodTransactions = mutableListOf<FinancialTransactionEntity>()

        for (tx in allTransactions) {
            if (tx.status == TransactionConstants.STATUS_ACTIVE) {
                if (tx.occurredAt < startEpoch) {
                    when (tx.type) {
                        TransactionConstants.TYPE_UDHAAR_GIVEN -> openingBalance += tx.amountPaise
                        TransactionConstants.TYPE_PAYMENT_RECEIVED -> openingBalance -= tx.amountPaise
                    }
                } else if (tx.occurredAt in startEpoch..endEpoch) {
                    periodTransactions.add(tx)
                    when (tx.type) {
                        TransactionConstants.TYPE_UDHAAR_GIVEN -> totalUdhaarInPeriod += tx.amountPaise
                        TransactionConstants.TYPE_PAYMENT_RECEIVED -> totalPaidInPeriod += tx.amountPaise
                    }
                }
            } else if (tx.occurredAt in startEpoch..endEpoch) {
                periodTransactions.add(tx)
            }
        }

        var totalActiveUdhaar = 0L
        var totalActivePaid = 0L
        for (tx in allTransactions) {
            if (tx.status == TransactionConstants.STATUS_ACTIVE) {
                when (tx.type) {
                    TransactionConstants.TYPE_UDHAAR_GIVEN -> totalActiveUdhaar += tx.amountPaise
                    TransactionConstants.TYPE_PAYMENT_RECEIVED -> totalActivePaid += tx.amountPaise
                }
            }
        }
        val currentBalance = totalActiveUdhaar - totalActivePaid
        val closingBalance = openingBalance + totalUdhaarInPeriod - totalPaidInPeriod

        return CustomerStatement(
            customer = customer,
            currentBalancePaise = currentBalance,
            transactions = periodTransactions,
            totalUdhaarPaise = totalUdhaarInPeriod,
            totalPaidPaise = totalPaidInPeriod,
            startDate = if (startEpoch > 0L) startEpoch else null,
            endDate = if (endEpoch < Long.MAX_VALUE) endEpoch else null,
            openingBalancePaise = openingBalance,
            closingBalancePaise = closingBalance
        )
    }

    suspend fun getAllTransactionsRaw(): List<FinancialTransactionEntity> =
        transactionDao.getAllTransactionsRaw()
}
