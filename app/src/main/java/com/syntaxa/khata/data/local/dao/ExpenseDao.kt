package com.syntaxa.khata.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.syntaxa.khata.data.local.entity.ExpenseConstants
import com.syntaxa.khata.data.local.entity.ExpenseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertExpense(expense: ExpenseEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restoreAllExpenses(expenses: List<ExpenseEntity>)

    @Query("DELETE FROM expenses")
    suspend fun deleteAllExpenses()

    @Update
    suspend fun updateExpense(expense: ExpenseEntity)

    @Query("SELECT * FROM expenses WHERE status = :status ORDER BY occurredAt DESC, id DESC")
    fun observeActiveExpenses(status: String = ExpenseConstants.STATUS_ACTIVE): Flow<List<ExpenseEntity>>

    @Query("""
        SELECT * FROM expenses 
        WHERE status = 'ACTIVE' AND occurredAt BETWEEN :startEpoch AND :endEpoch 
        ORDER BY occurredAt DESC, id DESC
    """)
    fun observeExpensesByDateRange(startEpoch: Long, endEpoch: Long): Flow<List<ExpenseEntity>>

    @Query("""
        SELECT * FROM expenses 
        WHERE status = 'ACTIVE' AND occurredAt BETWEEN :startEpoch AND :endEpoch 
        ORDER BY occurredAt DESC, id DESC
    """)
    suspend fun getExpensesByDateRange(startEpoch: Long, endEpoch: Long): List<ExpenseEntity>

    @Query("UPDATE expenses SET status = :status WHERE id = :id")
    suspend fun updateExpenseStatus(id: Long, status: String)

    @Query("SELECT * FROM expenses WHERE id = :id LIMIT 1")
    suspend fun getExpenseById(id: Long): ExpenseEntity?

    @Query("SELECT * FROM expenses")
    suspend fun getAllExpensesRaw(): List<ExpenseEntity>
}
