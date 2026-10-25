package com.syntaxa.khata.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.syntaxa.khata.data.local.entity.CustomerEntity
import com.syntaxa.khata.domain.model.CustomerWithBalance
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomerDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCustomer(customer: CustomerEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restoreAllCustomers(customers: List<CustomerEntity>)

    @Query("DELETE FROM customers")
    suspend fun deleteAllCustomers()

    @Update
    suspend fun updateCustomer(customer: CustomerEntity)

    @Query("SELECT * FROM customers WHERE id = :id LIMIT 1")
    suspend fun getCustomerById(id: Long): CustomerEntity?

    @Query("SELECT * FROM customers WHERE id = :id LIMIT 1")
    fun observeCustomerById(id: Long): Flow<CustomerEntity?>

    @Query("""
        SELECT c.id, c.name, c.phone, c.notes, c.createdAt, c.archivedAt,
            COALESCE(SUM(CASE 
                WHEN t.status = 'ACTIVE' AND t.type = 'UDHAAR_GIVEN' THEN t.amountPaise 
                WHEN t.status = 'ACTIVE' AND t.type = 'PAYMENT_RECEIVED' THEN -t.amountPaise 
                ELSE 0 END), 0) AS balancePaise,
            MAX(t.occurredAt) AS lastTransactionAt,
            COUNT(t.id) AS transactionCount,
            c.dueDate
        FROM customers c
        LEFT JOIN financial_transactions t ON c.id = t.customerId
        WHERE c.archivedAt IS NULL
        GROUP BY c.id
        ORDER BY c.name COLLATE NOCASE ASC
    """)
    fun observeActiveCustomersWithBalance(): Flow<List<CustomerWithBalance>>

    @Query("""
        SELECT c.id, c.name, c.phone, c.notes, c.createdAt, c.archivedAt,
            COALESCE(SUM(CASE 
                WHEN t.status = 'ACTIVE' AND t.type = 'UDHAAR_GIVEN' THEN t.amountPaise 
                WHEN t.status = 'ACTIVE' AND t.type = 'PAYMENT_RECEIVED' THEN -t.amountPaise 
                ELSE 0 END), 0) AS balancePaise,
            MAX(t.occurredAt) AS lastTransactionAt,
            COUNT(t.id) AS transactionCount,
            c.dueDate
        FROM customers c
        LEFT JOIN financial_transactions t ON c.id = t.customerId
        WHERE c.archivedAt IS NULL AND (c.name LIKE '%' || :query || '%' OR c.phone LIKE '%' || :query || '%')
        GROUP BY c.id
        ORDER BY c.name COLLATE NOCASE ASC
    """)
    fun searchActiveCustomersWithBalance(query: String): Flow<List<CustomerWithBalance>>

    @Query("""
        SELECT c.id, c.name, c.phone, c.notes, c.createdAt, c.archivedAt,
            COALESCE(SUM(CASE 
                WHEN t.status = 'ACTIVE' AND t.type = 'UDHAAR_GIVEN' THEN t.amountPaise 
                WHEN t.status = 'ACTIVE' AND t.type = 'PAYMENT_RECEIVED' THEN -t.amountPaise 
                ELSE 0 END), 0) AS balancePaise,
            MAX(t.occurredAt) AS lastTransactionAt,
            COUNT(t.id) AS transactionCount,
            c.dueDate
        FROM customers c
        LEFT JOIN financial_transactions t ON c.id = t.customerId
        WHERE c.id = :id
        GROUP BY c.id
    """)
    fun observeCustomerWithBalance(id: Long): Flow<CustomerWithBalance?>

    @Query("""
        SELECT c.id, c.name, c.phone, c.notes, c.createdAt, c.archivedAt,
            COALESCE(SUM(CASE 
                WHEN t.status = 'ACTIVE' AND t.type = 'UDHAAR_GIVEN' THEN t.amountPaise 
                WHEN t.status = 'ACTIVE' AND t.type = 'PAYMENT_RECEIVED' THEN -t.amountPaise 
                ELSE 0 END), 0) AS balancePaise,
            MAX(t.occurredAt) AS lastTransactionAt,
            COUNT(t.id) AS transactionCount,
            c.dueDate
        FROM customers c
        LEFT JOIN financial_transactions t ON c.id = t.customerId
        WHERE c.id = :id
        GROUP BY c.id
    """)
    suspend fun getCustomerWithBalance(id: Long): CustomerWithBalance?

    @Query("""
        SELECT c.id, c.name, c.phone, c.notes, c.createdAt, c.archivedAt,
            COALESCE(SUM(CASE 
                WHEN t.status = 'ACTIVE' AND t.type = 'UDHAAR_GIVEN' THEN t.amountPaise 
                WHEN t.status = 'ACTIVE' AND t.type = 'PAYMENT_RECEIVED' THEN -t.amountPaise 
                ELSE 0 END), 0) AS balancePaise,
            MAX(t.occurredAt) AS lastTransactionAt,
            COUNT(t.id) AS transactionCount,
            c.dueDate
        FROM customers c
        LEFT JOIN financial_transactions t ON c.id = t.customerId
        WHERE c.archivedAt IS NOT NULL
        GROUP BY c.id
        ORDER BY c.archivedAt DESC
    """)
    fun observeArchivedCustomersWithBalance(): Flow<List<CustomerWithBalance>>

    @Query("SELECT COUNT(*) FROM customers WHERE phone = :phone AND (:excludeId IS NULL OR id != :excludeId)")
    suspend fun countByPhone(phone: String, excludeId: Long? = null): Int

    @Query("UPDATE customers SET archivedAt = :archivedAt, updatedAt = :updatedAt WHERE id = :customerId")
    suspend fun setCustomerArchived(customerId: Long, archivedAt: Long?, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE customers SET dueDate = :dueDate, updatedAt = :updatedAt WHERE id = :customerId")
    suspend fun updateCustomerDueDate(customerId: Long, dueDate: Long?, updatedAt: Long = System.currentTimeMillis())

    @Query("""
        SELECT COALESCE(SUM(CASE 
            WHEN t.status = 'ACTIVE' AND t.type = 'UDHAAR_GIVEN' THEN t.amountPaise 
            WHEN t.status = 'ACTIVE' AND t.type = 'PAYMENT_RECEIVED' THEN -t.amountPaise 
            ELSE 0 END), 0)
        FROM financial_transactions t
        WHERE t.status = 'ACTIVE' AND t.customerId IS NOT NULL
    """)
    fun observeTotalMarketUdhaar(): Flow<Long>

    @Query("SELECT * FROM customers")
    suspend fun getAllCustomersRaw(): List<CustomerEntity>
}
