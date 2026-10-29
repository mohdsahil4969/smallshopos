package com.syntaxa.khata.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.syntaxa.khata.KhataAppContainer
import com.syntaxa.khata.core.model.DateTimeUtils
import com.syntaxa.khata.data.local.entity.FinancialTransactionEntity
import com.syntaxa.khata.data.local.entity.ShopProfileEntity
import com.syntaxa.khata.domain.model.DailyShopSummary
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
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

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(private val container: KhataAppContainer) : ViewModel() {
    private val _todayRange = MutableStateFlow(
        DateTimeUtils.getStartOfDay() to DateTimeUtils.getEndOfDay()
    )

    val shopProfile: StateFlow<ShopProfileEntity?> = container.shopProfileRepository
        .observeShopProfile()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val dailySummary: StateFlow<DailyShopSummary> = _todayRange
        .flatMapLatest { (start, end) ->
            container.dailyReportRepository.observeDailySummary(start, end)
        }
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

    fun refreshTodayIfNeeded() {
        val start = DateTimeUtils.getStartOfDay()
        val end = DateTimeUtils.getEndOfDay()
        if (_todayRange.value.first != start || _todayRange.value.second != end) {
            _todayRange.value = start to end
        }
    }

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
