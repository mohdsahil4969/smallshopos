package com.syntaxa.khata.feature.reports

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.syntaxa.khata.KhataAppContainer
import com.syntaxa.khata.core.model.CurrencyFormatter
import com.syntaxa.khata.core.model.DateTimeUtils
import com.syntaxa.khata.domain.model.DailyShopSummary
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import java.io.File
import java.io.FileOutputStream

enum class ReportDateRange {
    TODAY, LAST_7_DAYS, THIS_MONTH
}

class ReportsViewModel(private val container: KhataAppContainer) : ViewModel() {
    private val selectedRange = MutableStateFlow(ReportDateRange.TODAY)
    val dateRange: StateFlow<ReportDateRange> = selectedRange.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val reportSummary: StateFlow<DailyShopSummary> = selectedRange
        .flatMapLatest { range ->
            val (start, end) = when (range) {
                ReportDateRange.TODAY -> Pair(DateTimeUtils.getStartOfDay(), DateTimeUtils.getEndOfDay())
                ReportDateRange.LAST_7_DAYS -> Pair(DateTimeUtils.getDaysAgoStart(7), DateTimeUtils.getEndOfDay())
                ReportDateRange.THIS_MONTH -> Pair(DateTimeUtils.getStartOfMonth(), DateTimeUtils.getEndOfDay())
            }
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

    fun setDateRange(range: ReportDateRange) {
        selectedRange.value = range
    }

    suspend fun getCashReconciliation(countedPaise: Long, openingCashPaise: Long = 0L): com.syntaxa.khata.data.repository.CashClosingReconciliation {
        return container.dailyClosingRepository.calculateExpectedCashReconciliation(countedPaise, openingCashPaise)
    }

    suspend fun saveDailyClosing(reconciliation: com.syntaxa.khata.data.repository.CashClosingReconciliation, notes: String = ""): Long {
        return container.dailyClosingRepository.saveDailyClosing(reconciliation, notes)
    }

    suspend fun exportReportCsv(context: Context): File {
        val summary = reportSummary.value
        val reportsDir = File(context.cacheDir, "reports").apply { mkdirs() }
        val filename = "SHOP_REPORT_${DateTimeUtils.formatReceiptTimestamp(System.currentTimeMillis())}.csv"
        val file = File(reportsDir, filename)

        FileOutputStream(file).bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write("Metric,Amount (INR)\n")
            writer.write("Total Recorded Sales,${summary.totalSalesPaise / 100.0}\n")
            writer.write("Cash Collected (Sales + Khata),${summary.totalCashCollectedPaise / 100.0}\n")
            writer.write("UPI Collected (Sales + Khata),${summary.totalUpiCollectedPaise / 100.0}\n")
            writer.write("Udhaar Given,${summary.udhaarGivenTodayPaise / 100.0}\n")
            writer.write("Udhaar Recovered,${summary.udhaarRecoveredTodayPaise / 100.0}\n")
            writer.write("Total Expenses,${summary.totalExpensesPaise / 100.0}\n")
            writer.write("Net Cash Movement,${summary.netCashMovementPaise / 100.0}\n")
            writer.write("Total Outstanding Udhaar,${summary.totalMarketUdhaarOwedPaise / 100.0}\n")
            writer.flush()
        }
        return file
    }

    class Factory(private val container: KhataAppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ReportsViewModel(container) as T
        }
    }
}
