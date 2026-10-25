package com.syntaxa.khata.feature.expenses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.syntaxa.khata.KhataAppContainer
import com.syntaxa.khata.core.model.DateTimeUtils
import com.syntaxa.khata.data.local.entity.ExpenseConstants
import com.syntaxa.khata.data.local.entity.ExpenseEntity
import com.syntaxa.khata.data.local.entity.TransactionConstants
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ExpenseViewModel(private val container: KhataAppContainer) : ViewModel() {
    val activeExpenses: StateFlow<List<ExpenseEntity>> = container.expenseRepository
        .observeActiveExpenses()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val todayExpenses: StateFlow<List<ExpenseEntity>> = container.expenseRepository
        .observeExpensesByDateRange(DateTimeUtils.getStartOfDay(), DateTimeUtils.getEndOfDay())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addExpense(
        amountPaise: Long,
        category: String,
        description: String,
        paymentMethod: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                if (amountPaise <= 0) {
                    onError("Amount must be greater than 0")
                    return@launch
                }
                container.expenseRepository.recordExpense(
                    amountPaise = amountPaise,
                    category = category,
                    description = description,
                    paymentMethod = paymentMethod
                )
                onSuccess()
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "Failed to record expense")
            }
        }
    }

    fun reverseExpense(expenseId: Long, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                container.expenseRepository.reverseExpense(expenseId)
                onComplete()
            } catch (_: Exception) { }
        }
    }

    class Factory(private val container: KhataAppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ExpenseViewModel(container) as T
        }
    }
}
