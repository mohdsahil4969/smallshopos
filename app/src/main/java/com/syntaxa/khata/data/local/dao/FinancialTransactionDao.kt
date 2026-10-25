package com.syntaxa.khata.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.syntaxa.khata.data.local.entity.FinancialTransactionEntity
import com.syntaxa.khata.data.local.entity.TransactionConstants
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Dao
interface FinancialTransactionDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTransaction(transaction: FinancialTransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restoreAllTransactions(transactions: List<FinancialTransactionEntity>)

    @Query("DELETE FROM financial_transactions")
    suspend fun deleteAllTransactions()

    @Update
    suspend fun updateTransaction(transaction: FinancialTransactionEntity)

    @Query("SELECT * FROM financial_transactions WHERE id = :id LIMIT 1")
    suspend fun getTransactionById(id: Long): FinancialTransactionEntity?

    @Query("SELECT * FROM financial_transactions WHERE customerId = :customerId ORDER BY occurredAt DESC, id DESC")
    fun observeTransactionsForCustomer(customerId: Long): Flow<List<FinancialTransactionEntity>>

    @Query("SELECT * FROM financial_transactions WHERE customerId = :customerId ORDER BY occurredAt DESC, id DESC")
    suspend fun getTransactionsForCustomer(customerId: Long): List<FinancialTransactionEntity>

    @Query("SELECT * FROM financial_transactions ORDER BY occurredAt DESC, id DESC LIMIT :limit")
    fun observeRecentTransactions(limit: Int = 10): Flow<List<FinancialTransactionEntity>>

    @Query("""
        SELECT * FROM financial_transactions 
        WHERE occurredAt BETWEEN :startEpoch AND :endEpoch 
        ORDER BY occurredAt DESC, id DESC
    """)
    fun observeTransactionsByDateRange(startEpoch: Long, endEpoch: Long): Flow<List<FinancialTransactionEntity>>

    @Query("""
        SELECT * FROM financial_transactions 
        WHERE occurredAt BETWEEN :startEpoch AND :endEpoch 
        ORDER BY occurredAt DESC, id DESC
    """)
    suspend fun getTransactionsByDateRange(startEpoch: Long, endEpoch: Long): List<FinancialTransactionEntity>

    @Query("UPDATE financial_transactions SET status = :status WHERE id = :id")
    suspend fun updateTransactionStatus(id: Long, status: String)

    @Query("SELECT * FROM financial_transactions WHERE idempotencyKey = :idempotencyKey LIMIT 1")
    suspend fun findByIdempotencyKey(idempotencyKey: String): FinancialTransactionEntity?

    @Query("SELECT * FROM financial_transactions")
    suspend fun getAllTransactionsRaw(): List<FinancialTransactionEntity>

    @Transaction
    suspend fun executeReversal(targetTransactionId: Long, reason: String): Long {
        val target = getTransactionById(targetTransactionId)
            ?: throw IllegalArgumentException("Transaction not found")

        if (target.status == TransactionConstants.STATUS_REVERSED) {
            throw IllegalStateException("Transaction is already reversed")
        }

        // 1. Mark target transaction as REVERSED
        updateTransactionStatus(target.id, TransactionConstants.STATUS_REVERSED)

        // 2. Create an auditable reversal ledger entry
        val reversalEntry = FinancialTransactionEntity(
            type = TransactionConstants.TYPE_REVERSAL,
            customerId = target.customerId,
            amountPaise = target.amountPaise,
            paymentMethod = target.paymentMethod,
            description = if (reason.isNotBlank()) "Reversal: $reason" else "Reversal of Transaction #${target.id}",
            occurredAt = System.currentTimeMillis(),
            reversalOfTransactionId = target.id,
            relatedBillId = target.relatedBillId,
            idempotencyKey = "REV-${UUID.randomUUID()}",
            status = TransactionConstants.STATUS_REVERSED
        )

        return insertTransaction(reversalEntry)
    }
}
