package com.syntaxa.khata.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.syntaxa.khata.KhataAppContainer
import com.syntaxa.khata.core.model.DateTimeUtils
import com.syntaxa.khata.data.local.entity.FinancialTransactionEntity
import com.syntaxa.khata.data.local.entity.ShopProfileEntity
import com.syntaxa.khata.domain.model.DailyShopSummary
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val shopProfile: ShopProfileEntity? = null,
    val summary: DailyShopSummary = DailyShopSummary(
        dateEpochMillis = System.currentTimeMillis(),
        totalSalesPaise = 0L,
        cashSalesPaise = 0L,
        upiSalesPaise = 0L,
        creditSalesPaise = 0L,
        cashKhataCollectedPaise = 0L,
        upiKhataCollectedPaise = 0L,
        totalCashCollectedPaise = 0L,
        totalUpiCollectedPaise = 0L,
        udhaarGivenTodayPaise = 0L,
        udhaarRecoveredTodayPaise = 0L,
        totalExpensesPaise = 0L,
        cashExpensesPaise = 0L,
        netCashMovementPaise = 0L,
        totalMarketUdhaarOwedPaise = 0L
    ),
    val recentTransactions: List<FinancialTransactionEntity> = emptyList()
)

class HomeViewModel(private val container: KhataAppContainer) : ViewModel() {
    val shopProfile: StateFlow<ShopProfileEntity?> = container.shopProfileRepository
        .observeShopProfile()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val dailySummary: StateFlow<DailyShopSummary> = container.dailyReportRepository
        .observeDailySummary(DateTimeUtils.getStartOfDay(), DateTimeUtils.getEndOfDay())
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            DailyShopSummary(
                dateEpochMillis = System.currentTimeMillis(),
                totalSalesPaise = 0L,
                cashSalesPaise = 0L,
                upiSalesPaise = 0L,
                creditSalesPaise = 0L,
                cashKhataCollectedPaise = 0L,
                upiKhataCollectedPaise = 0L,
                totalCashCollectedPaise = 0L,
                totalUpiCollectedPaise = 0L,
                udhaarGivenTodayPaise = 0L,
                udhaarRecoveredTodayPaise = 0L,
                totalExpensesPaise = 0L,
                cashExpensesPaise = 0L,
                netCashMovementPaise = 0L,
                totalMarketUdhaarOwedPaise = 0L
            )
        )

    val recentTransactions: StateFlow<List<FinancialTransactionEntity>> = container.financialRepository
        .observeRecentTransactions(10)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun reverseTransaction(transactionId: Long, reason: String, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                container.financialRepository.reverseTransaction(transactionId, reason)
                onComplete()
            } catch (_: Exception) { }
        }
    }

    class Factory(private val container: KhataAppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HomeViewModel(container) as T
        }
    }
}
