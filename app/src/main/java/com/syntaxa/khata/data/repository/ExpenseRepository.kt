package com.syntaxa.khata.data.repository

import com.syntaxa.khata.data.local.dao.ExpenseDao
import com.syntaxa.khata.data.local.entity.ExpenseConstants
import com.syntaxa.khata.data.local.entity.ExpenseEntity
import com.syntaxa.khata.data.local.entity.TransactionConstants
import kotlinx.coroutines.flow.Flow

class ExpenseRepository(private val expenseDao: ExpenseDao) {
    fun observeActiveExpenses(): Flow<List<ExpenseEntity>> =
        expenseDao.observeActiveExpenses()

    fun observeExpensesByDateRange(startEpoch: Long, endEpoch: Long): Flow<List<ExpenseEntity>> =
        expenseDao.observeExpensesByDateRange(startEpoch, endEpoch)

    suspend fun getExpensesByDateRange(startEpoch: Long, endEpoch: Long): List<ExpenseEntity> =
        expenseDao.getExpensesByDateRange(startEpoch, endEpoch)

    suspend fun recordExpense(
        amountPaise: Long,
        category: String = ExpenseConstants.CAT_OTHER,
        description: String = "",
        paymentMethod: String = TransactionConstants.METHOD_CASH,
        occurredAt: Long = System.currentTimeMillis()
    ): Long {
        require(amountPaise > 0) { "Expense amount must be greater than zero" }
        val expense = ExpenseEntity(
            amountPaise = amountPaise,
            category = category.trim(),
            description = description.trim(),
            paymentMethod = paymentMethod,
            occurredAt = occurredAt,
            createdAt = System.currentTimeMillis(),
            status = ExpenseConstants.STATUS_ACTIVE
        )
        return expenseDao.insertExpense(expense)
    }

    suspend fun reverseExpense(id: Long) {
        val existing = expenseDao.getExpenseById(id)
            ?: throw IllegalArgumentException("Expense not found")
        expenseDao.updateExpenseStatus(id, ExpenseConstants.STATUS_REVERSED)
    }

    suspend fun getAllExpensesRaw(): List<ExpenseEntity> = expenseDao.getAllExpensesRaw()
}
