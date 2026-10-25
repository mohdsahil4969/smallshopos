package com.syntaxa.khata.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.syntaxa.khata.data.local.entity.BillConstants
import com.syntaxa.khata.data.local.entity.BillEntity
import com.syntaxa.khata.data.local.entity.BillItemEntity
import com.syntaxa.khata.domain.model.BillWithItems
import kotlinx.coroutines.flow.Flow

@Dao
interface BillDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertBill(bill: BillEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertBillItems(items: List<BillItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restoreAllBills(bills: List<BillEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restoreAllBillItems(items: List<BillItemEntity>)

    @Query("DELETE FROM bills")
    suspend fun deleteAllBills()

    @Query("DELETE FROM bill_items")
    suspend fun deleteAllBillItems()

    @Update
    suspend fun updateBill(bill: BillEntity)

    @Transaction
    @Query("SELECT * FROM bills WHERE id = :id LIMIT 1")
    fun observeBillWithItems(id: Long): Flow<BillWithItems?>

    @Transaction
    @Query("SELECT * FROM bills WHERE id = :id LIMIT 1")
    suspend fun getBillWithItems(id: Long): BillWithItems?

    @Transaction
    @Query("SELECT * FROM bills ORDER BY createdAt DESC, id DESC LIMIT :limit")
    fun observeRecentBills(limit: Int = 10): Flow<List<BillWithItems>>

    @Transaction
    @Query("""
        SELECT * FROM bills 
        WHERE createdAt BETWEEN :startEpoch AND :endEpoch 
        ORDER BY createdAt DESC, id DESC
    """)
    fun observeBillsByDateRange(startEpoch: Long, endEpoch: Long): Flow<List<BillWithItems>>

    @Transaction
    @Query("""
        SELECT * FROM bills 
        WHERE createdAt BETWEEN :startEpoch AND :endEpoch 
        ORDER BY createdAt DESC, id DESC
    """)
    suspend fun getBillsByDateRange(startEpoch: Long, endEpoch: Long): List<BillWithItems>

    @Transaction
    @Query("SELECT * FROM bills WHERE customerId = :customerId ORDER BY createdAt DESC, id DESC")
    fun observeBillsForCustomer(customerId: Long): Flow<List<BillWithItems>>

    @Transaction
    @Query("SELECT * FROM bills WHERE customerId = :customerId ORDER BY createdAt DESC, id DESC")
    suspend fun getBillsForCustomer(customerId: Long): List<BillWithItems>

    @Transaction
    @Query("""
        SELECT * FROM bills 
        WHERE customerId = :customerId AND createdAt BETWEEN :startEpoch AND :endEpoch 
        ORDER BY createdAt DESC, id DESC
    """)
    fun observeBillsForCustomerByDateRange(customerId: Long, startEpoch: Long, endEpoch: Long): Flow<List<BillWithItems>>

    @Transaction
    @Query("""
        SELECT * FROM bills 
        WHERE customerId = :customerId AND createdAt BETWEEN :startEpoch AND :endEpoch 
        ORDER BY createdAt DESC, id DESC
    """)
    suspend fun getBillsForCustomerByDateRange(customerId: Long, startEpoch: Long, endEpoch: Long): List<BillWithItems>

    @Query("SELECT COUNT(*) FROM bills WHERE createdAt BETWEEN :startOfDay AND :endOfDay")
    suspend fun countBillsForDay(startOfDay: Long, endOfDay: Long): Int

    @Query("SELECT * FROM bills WHERE idempotencyKey = :idempotencyKey LIMIT 1")
    suspend fun findByIdempotencyKey(idempotencyKey: String): BillEntity?

    @Query("UPDATE bills SET status = :status WHERE id = :billId")
    suspend fun updateBillStatus(billId: Long, status: String)

    @Transaction
    suspend fun createBillWithItems(bill: BillEntity, items: List<BillItemEntity>): Long {
        val billId = insertBill(bill)
        if (items.isNotEmpty()) {
            val itemsWithBillId = items.map { it.copy(billId = billId) }
            insertBillItems(itemsWithBillId)
        }
        return billId
    }

    @Query("SELECT * FROM bills")
    suspend fun getAllBillsRaw(): List<BillEntity>

    @Query("SELECT * FROM bill_items")
    suspend fun getAllBillItemsRaw(): List<BillItemEntity>
}
