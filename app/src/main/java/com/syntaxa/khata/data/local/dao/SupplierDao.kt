package com.syntaxa.khata.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.syntaxa.khata.data.local.entity.SupplierEntity
import com.syntaxa.khata.data.local.entity.SupplierTransactionEntity
import com.syntaxa.khata.domain.model.SupplierWithBalance
import kotlinx.coroutines.flow.Flow

@Dao
interface SupplierDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSupplier(supplier: SupplierEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restoreAllSuppliers(suppliers: List<SupplierEntity>)

    @Update
    suspend fun updateSupplier(supplier: SupplierEntity)

    @Query("SELECT * FROM suppliers WHERE id = :id LIMIT 1")
    suspend fun getSupplierById(id: Long): SupplierEntity?

    @Query("""
        SELECT s.id, s.name, s.phone, s.notes, s.createdAt, s.archivedAt,
            COALESCE(SUM(CASE 
                WHEN t.status = 'ACTIVE' AND t.type = 'PURCHASE' THEN t.amountPaise 
                WHEN t.status = 'ACTIVE' AND t.type = 'PAYMENT_MADE' THEN -t.amountPaise 
                ELSE 0 END), 0) AS balancePaise,
            MAX(t.occurredAt) AS lastTransactionAt,
            COUNT(t.id) AS transactionCount
        FROM suppliers s
        LEFT JOIN supplier_transactions t ON s.id = t.supplierId
        WHERE s.archivedAt IS NULL
        GROUP BY s.id
        ORDER BY s.name COLLATE NOCASE ASC
    """)
    fun observeActiveSuppliersWithBalance(): Flow<List<SupplierWithBalance>>

    @Query("""
        SELECT s.id, s.name, s.phone, s.notes, s.createdAt, s.archivedAt,
            COALESCE(SUM(CASE 
                WHEN t.status = 'ACTIVE' AND t.type = 'PURCHASE' THEN t.amountPaise 
                WHEN t.status = 'ACTIVE' AND t.type = 'PAYMENT_MADE' THEN -t.amountPaise 
                ELSE 0 END), 0) AS balancePaise,
            MAX(t.occurredAt) AS lastTransactionAt,
            COUNT(t.id) AS transactionCount
        FROM suppliers s
        LEFT JOIN supplier_transactions t ON s.id = t.supplierId
        WHERE s.id = :id
        GROUP BY s.id
    """)
    suspend fun getSupplierWithBalance(id: Long): SupplierWithBalance?

    @Query("SELECT * FROM suppliers")
    suspend fun getAllSuppliersRaw(): List<SupplierEntity>

    @Query("DELETE FROM suppliers")
    suspend fun deleteAllSuppliers()

    // Supplier Transactions
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTransaction(transaction: SupplierTransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restoreAllTransactions(transactions: List<SupplierTransactionEntity>)

    @Query("SELECT * FROM supplier_transactions WHERE supplierId = :supplierId ORDER BY occurredAt DESC")
    fun observeTransactionsForSupplier(supplierId: Long): Flow<List<SupplierTransactionEntity>>

    @Query("SELECT * FROM supplier_transactions WHERE supplierId = :supplierId ORDER BY occurredAt DESC")
    suspend fun getTransactionsForSupplier(supplierId: Long): List<SupplierTransactionEntity>

    @Query("SELECT * FROM supplier_transactions")
    suspend fun getAllTransactionsRaw(): List<SupplierTransactionEntity>

    @Query("DELETE FROM supplier_transactions")
    suspend fun deleteAllTransactions()
}
