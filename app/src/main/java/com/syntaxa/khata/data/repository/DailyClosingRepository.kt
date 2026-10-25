package com.syntaxa.khata.data.repository

import com.syntaxa.khata.core.model.DateTimeUtils
import com.syntaxa.khata.data.local.dao.DailyCashClosingDao
import com.syntaxa.khata.data.local.entity.DailyCashClosingEntity
import com.syntaxa.khata.domain.model.DailyShopSummary
import kotlinx.coroutines.flow.Flow

data class CashClosingReconciliation(
    val dateEpoch: Long,
    val openingCashPaise: Long = 0L,
    val cashSalesPaise: Long,
    val cashKhataCollectedPaise: Long,
    val cashExpensesPaise: Long,
    val expectedCashPaise: Long,
    val countedCashPaise: Long,
    val differencePaise: Long,
    val isBalanced: Boolean,
    val isSurplus: Boolean,
    val isShortage: Boolean
)

class DailyClosingRepository(
    private val dailyCashClosingDao: DailyCashClosingDao,
    private val dailyReportRepository: DailyReportRepository
) {
    suspend fun calculateExpectedCashReconciliation(
        countedCashPaise: Long,
        openingCashPaise: Long = 0L,
        dateEpoch: Long = DateTimeUtils.getStartOfDay()
    ): CashClosingReconciliation {
        val summary: DailyShopSummary = dailyReportRepository.getDailySummary(
            startEpoch = dateEpoch,
            endEpoch = dateEpoch + 86400000L - 1L
        )

        val cashSales = summary.cashSalesPaise
        val cashKhata = summary.cashKhataCollectedPaise
        val cashExpenses = summary.cashExpensesPaise

        // Explicit formula: Expected Cash = Opening Cash + (Cash Sales + Old Udhaar Cash Collected) - Cash Expenses
        val expectedCash = openingCashPaise + (cashSales + cashKhata) - cashExpenses
        val diff = countedCashPaise - expectedCash

        return CashClosingReconciliation(
            dateEpoch = dateEpoch,
            openingCashPaise = openingCashPaise,
            cashSalesPaise = cashSales,
            cashKhataCollectedPaise = cashKhata,
            cashExpensesPaise = cashExpenses,
            expectedCashPaise = expectedCash,
            countedCashPaise = countedCashPaise,
            differencePaise = diff,
            isBalanced = diff == 0L,
            isSurplus = diff > 0L,
            isShortage = diff < 0L
        )
    }

    suspend fun saveDailyClosing(reconciliation: CashClosingReconciliation, notes: String = ""): Long {
        val entity = DailyCashClosingEntity(
            closingDateEpoch = reconciliation.dateEpoch,
            openingCashPaise = reconciliation.openingCashPaise,
            cashSalesPaise = reconciliation.cashSalesPaise,
            cashKhataCollectedPaise = reconciliation.cashKhataCollectedPaise,
            cashExpensesPaise = reconciliation.cashExpensesPaise,
            expectedCashPaise = reconciliation.expectedCashPaise,
            countedCashPaise = reconciliation.countedCashPaise,
            differencePaise = reconciliation.differencePaise,
            notes = notes.trim(),
            closedAt = System.currentTimeMillis()
        )
        return dailyCashClosingDao.saveClosing(entity)
    }

    suspend fun getClosingForDate(dateEpoch: Long = DateTimeUtils.getStartOfDay()): DailyCashClosingEntity? =
        dailyCashClosingDao.getClosingForDate(dateEpoch)

    fun observeRecentClosings(limit: Int = 30): Flow<List<DailyCashClosingEntity>> =
        dailyCashClosingDao.observeRecentClosings(limit)
}
