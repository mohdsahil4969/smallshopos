package com.syntaxa.khata.data.repository

import com.syntaxa.khata.core.model.DateTimeUtils
import com.syntaxa.khata.data.local.dao.CustomerDao
import com.syntaxa.khata.data.local.entity.BillConstants
import com.syntaxa.khata.data.local.entity.ExpenseConstants
import com.syntaxa.khata.data.local.entity.TransactionConstants
import com.syntaxa.khata.domain.model.DailyShopSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class DailyReportRepository(
    private val billingRepository: BillingRepository,
    private val financialRepository: FinancialRepository,
    private val expenseRepository: ExpenseRepository,
    private val customerDao: CustomerDao
) {
    suspend fun getDailySummary(
        startEpoch: Long = DateTimeUtils.getStartOfDay(),
        endEpoch: Long = DateTimeUtils.getEndOfDay()
    ): DailyShopSummary {
        val bills = billingRepository.getBillsByDateRange(startEpoch, endEpoch)
            .filter { it.bill.status == BillConstants.STATUS_COMPLETED }
        val transactions = financialRepository.getTransactionsByDateRange(startEpoch, endEpoch)
            .filter { it.status == TransactionConstants.STATUS_ACTIVE }
        val expenses = expenseRepository.getExpensesByDateRange(startEpoch, endEpoch)
            .filter { it.status == ExpenseConstants.STATUS_ACTIVE }

        var totalSales = 0L
        var cashSales = 0L
        var upiSales = 0L
        var otherSales = 0L
        var creditSales = 0L

        for (bw in bills) {
            val b = bw.bill
            totalSales += b.totalPaise
            val paid = b.amountPaidPaise
            val unpaid = maxOf(0L, b.totalPaise - paid)
            creditSales += unpaid

            val (cash, upi, other) = when (b.paymentMethod) {
                TransactionConstants.METHOD_CASH -> Triple(paid, 0L, 0L)
                TransactionConstants.METHOD_UPI -> Triple(0L, paid, 0L)
                "SPLIT" -> Triple(b.cashPaidPaise, b.upiPaidPaise, b.otherPaidPaise)
                else -> Triple(0L, 0L, paid)
            }

            cashSales += cash
            upiSales += upi
            otherSales += other
        }

        var cashKhata = 0L
        var upiKhata = 0L
        var otherKhata = 0L
        var udhaarGiven = 0L
        var udhaarRecovered = 0L

        for (tx in transactions) {
            when (tx.type) {
                TransactionConstants.TYPE_UDHAAR_GIVEN -> {
                    udhaarGiven += tx.amountPaise
                }
                TransactionConstants.TYPE_PAYMENT_RECEIVED -> {
                    udhaarRecovered += tx.amountPaise
                    when (tx.paymentMethod) {
                        TransactionConstants.METHOD_CASH -> cashKhata += tx.amountPaise
                        TransactionConstants.METHOD_UPI -> upiKhata += tx.amountPaise
                        else -> otherKhata += tx.amountPaise
                    }
                }
            }
        }

        var totalExpenses = 0L
        var cashExpenses = 0L
        for (ex in expenses) {
            totalExpenses += ex.amountPaise
            if (ex.paymentMethod == TransactionConstants.METHOD_CASH) {
                cashExpenses += ex.amountPaise
            }
        }

        val totalCashCollected = cashSales + cashKhata
        val totalUpiCollected = upiSales + upiKhata
        val totalOtherCollected = otherSales + otherKhata
        val netCashMovement = totalCashCollected - cashExpenses

        // Market Udhaar is computed dynamically from active customer ledgers
        val customersWithBalance = customerDao.getAllCustomersRaw()
        var marketUdhaar = 0L
        for (c in customersWithBalance) {
            if (c.archivedAt == null) {
                val balance = customerDao.getCustomerWithBalance(c.id)?.balancePaise ?: 0L
                if (balance > 0L) {
                    marketUdhaar += balance
                }
            }
        }

        return DailyShopSummary(
            dateEpochMillis = startEpoch,
            totalSalesPaise = totalSales,
            cashSalesPaise = cashSales,
            upiSalesPaise = upiSales,
            otherSalesPaise = otherSales,
            creditSalesPaise = creditSales,
            cashKhataCollectedPaise = cashKhata,
            upiKhataCollectedPaise = upiKhata,
            otherKhataCollectedPaise = otherKhata,
            totalCashCollectedPaise = totalCashCollected,
            totalUpiCollectedPaise = totalUpiCollected,
            totalOtherCollectedPaise = totalOtherCollected,
            udhaarGivenTodayPaise = udhaarGiven,
            udhaarRecoveredTodayPaise = udhaarRecovered,
            totalExpensesPaise = totalExpenses,
            cashExpensesPaise = cashExpenses,
            netCashMovementPaise = netCashMovement,
            totalMarketUdhaarOwedPaise = marketUdhaar
        )
    }

    fun observeDailySummary(
        startEpoch: Long = DateTimeUtils.getStartOfDay(),
        endEpoch: Long = DateTimeUtils.getEndOfDay()
    ): Flow<DailyShopSummary> {
        return combine(
            billingRepository.observeBillsByDateRange(startEpoch, endEpoch),
            financialRepository.observeTransactionsByDateRange(startEpoch, endEpoch),
            expenseRepository.observeExpensesByDateRange(startEpoch, endEpoch),
            customerDao.observeTotalMarketUdhaar()
        ) { bills, txs, expenses, marketUdhaar ->
            var totalSales = 0L
            var cashSales = 0L
            var upiSales = 0L
            var otherSales = 0L
            var creditSales = 0L

            for (bw in bills.filter { it.bill.status == BillConstants.STATUS_COMPLETED }) {
                val b = bw.bill
                totalSales += b.totalPaise
                val paid = b.amountPaidPaise
                creditSales += maxOf(0L, b.totalPaise - paid)

                val (cash, upi, other) = when (b.paymentMethod) {
                    TransactionConstants.METHOD_CASH -> Triple(paid, 0L, 0L)
                    TransactionConstants.METHOD_UPI -> Triple(0L, paid, 0L)
                    "SPLIT" -> Triple(b.cashPaidPaise, b.upiPaidPaise, b.otherPaidPaise)
                    else -> Triple(0L, 0L, paid)
                }

                cashSales += cash
                upiSales += upi
                otherSales += other
            }

            var cashKhata = 0L
            var upiKhata = 0L
            var otherKhata = 0L
            var udhaarGiven = 0L
            var udhaarRecovered = 0L

            for (tx in txs.filter { it.status == TransactionConstants.STATUS_ACTIVE }) {
                when (tx.type) {
                    TransactionConstants.TYPE_UDHAAR_GIVEN -> udhaarGiven += tx.amountPaise
                    TransactionConstants.TYPE_PAYMENT_RECEIVED -> {
                        udhaarRecovered += tx.amountPaise
                        when (tx.paymentMethod) {
                            TransactionConstants.METHOD_CASH -> cashKhata += tx.amountPaise
                            TransactionConstants.METHOD_UPI -> upiKhata += tx.amountPaise
                            else -> otherKhata += tx.amountPaise
                        }
                    }
                }
            }

            var totalExpenses = 0L
            var cashExpenses = 0L
            for (ex in expenses.filter { it.status == ExpenseConstants.STATUS_ACTIVE }) {
                totalExpenses += ex.amountPaise
                if (ex.paymentMethod == TransactionConstants.METHOD_CASH) {
                    cashExpenses += ex.amountPaise
                }
            }

            val totalCash = cashSales + cashKhata
            val totalUpi = upiSales + upiKhata
            val totalOther = otherSales + otherKhata

            DailyShopSummary(
                dateEpochMillis = startEpoch,
                totalSalesPaise = totalSales,
                cashSalesPaise = cashSales,
                upiSalesPaise = upiSales,
                otherSalesPaise = otherSales,
                creditSalesPaise = creditSales,
                cashKhataCollectedPaise = cashKhata,
                upiKhataCollectedPaise = upiKhata,
                otherKhataCollectedPaise = otherKhata,
                totalCashCollectedPaise = totalCash,
                totalUpiCollectedPaise = totalUpi,
                totalOtherCollectedPaise = totalOther,
                udhaarGivenTodayPaise = udhaarGiven,
                udhaarRecoveredTodayPaise = udhaarRecovered,
                totalExpensesPaise = totalExpenses,
                cashExpensesPaise = cashExpenses,
                netCashMovementPaise = totalCash - cashExpenses,
                totalMarketUdhaarOwedPaise = maxOf(0L, marketUdhaar)
            )
        }
    }
}
