package com.syntaxa.khata.domain.model

import androidx.room.Embedded
import androidx.room.Relation
import com.syntaxa.khata.data.local.entity.BillEntity
import com.syntaxa.khata.data.local.entity.BillItemEntity
import com.syntaxa.khata.data.local.entity.CustomerEntity
import com.syntaxa.khata.data.local.entity.FinancialTransactionEntity

data class CustomerWithBalance(
    val id: Long,
    val name: String,
    val phone: String,
    val notes: String,
    val createdAt: Long,
    val archivedAt: Long?,
    val balancePaise: Long,
    val lastTransactionAt: Long?,
    val transactionCount: Int,
    val dueDate: Long? = null
)

data class SupplierWithBalance(
    val id: Long,
    val name: String,
    val phone: String,
    val notes: String,
    val createdAt: Long,
    val archivedAt: Long?,
    val balancePaise: Long,
    val lastTransactionAt: Long?,
    val transactionCount: Int
)

data class BillWithItems(
    @Embedded val bill: BillEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "billId"
    )
    val items: List<BillItemEntity> = emptyList(),
    @Relation(
        parentColumn = "customerId",
        entityColumn = "id"
    )
    val customer: CustomerEntity? = null
)

data class CustomerStatement(
    val customer: CustomerEntity,
    val currentBalancePaise: Long,
    val transactions: List<FinancialTransactionEntity>,
    val totalUdhaarPaise: Long,
    val totalPaidPaise: Long,
    val startDate: Long? = null,
    val endDate: Long? = null,
    val openingBalancePaise: Long = 0L,
    val closingBalancePaise: Long = currentBalancePaise
)

enum class LedgerDateFilter(val labelEn: String, val labelHi: String) {
    ALL_TIME("All Time", "सब समय"),
    LAST_30_DAYS("Last 30 Days", "पिछले 30 दिन"),
    LAST_3_MONTHS("Last 3 Months", "पिछले 3 महीने"),
    LAST_YEAR("Last Year", "पिछला 1 वर्ष"),
    CUSTOM("Custom Range", "कस्टम अवधि")
}

data class CustomerFinancialSummary(
    val totalPurchasesPaise: Long,
    val paymentsReceivedPaise: Long,
    val currentOutstandingBalancePaise: Long,
    val transactionCount: Int,
    val dateFilter: LedgerDateFilter = LedgerDateFilter.ALL_TIME,
    val startEpoch: Long = 0L,
    val endEpoch: Long = Long.MAX_VALUE
)

sealed class CustomerLedgerEntry {
    abstract val timestamp: Long
    abstract val id: Long

    data class BillPurchase(
        val billWithItems: BillWithItems,
        override val timestamp: Long = billWithItems.bill.createdAt,
        override val id: Long = billWithItems.bill.id
    ) : CustomerLedgerEntry() {
        val receiptNumber: String get() = billWithItems.bill.receiptNumber
        val totalPaise: Long get() = billWithItems.bill.totalPaise
        val amountPaidPaise: Long get() = billWithItems.bill.amountPaidPaise
        val unpaidPaise: Long get() = (totalPaise - amountPaidPaise).coerceAtLeast(0L)
        val status: String get() = billWithItems.bill.status
        val paymentMethod: String get() = billWithItems.bill.paymentMethod
        val items: List<BillItemEntity> get() = billWithItems.items
        val discountPaise: Long get() = billWithItems.bill.discountPaise
        val notes: String get() = billWithItems.bill.notes
        val billType: String get() = billWithItems.bill.billType
    }

    data class Payment(
        val transaction: FinancialTransactionEntity,
        override val timestamp: Long = transaction.occurredAt,
        override val id: Long = transaction.id
    ) : CustomerLedgerEntry() {
        val amountPaise: Long get() = transaction.amountPaise
        val paymentMethod: String get() = transaction.paymentMethod
        val description: String get() = transaction.description
    }

    data class DirectUdhaar(
        val transaction: FinancialTransactionEntity,
        override val timestamp: Long = transaction.occurredAt,
        override val id: Long = transaction.id
    ) : CustomerLedgerEntry() {
        val amountPaise: Long get() = transaction.amountPaise
        val description: String get() = transaction.description
    }

    data class Reversal(
        val transaction: FinancialTransactionEntity,
        override val timestamp: Long = transaction.occurredAt,
        override val id: Long = transaction.id
    ) : CustomerLedgerEntry() {
        val amountPaise: Long get() = transaction.amountPaise
        val description: String get() = transaction.description
        val targetTransactionId: Long? get() = transaction.reversalOfTransactionId
    }
}

data class DailyShopSummary(
    val dateEpochMillis: Long,
    val totalSalesPaise: Long,
    val cashSalesPaise: Long,
    val upiSalesPaise: Long,
    val otherSalesPaise: Long = 0L,
    val creditSalesPaise: Long,
    val cashKhataCollectedPaise: Long,
    val upiKhataCollectedPaise: Long,
    val otherKhataCollectedPaise: Long = 0L,
    val totalCashCollectedPaise: Long,
    val totalUpiCollectedPaise: Long,
    val totalOtherCollectedPaise: Long = 0L,
    val udhaarGivenTodayPaise: Long,
    val udhaarRecoveredTodayPaise: Long,
    val totalExpensesPaise: Long,
    val cashExpensesPaise: Long,
    val netCashMovementPaise: Long,
    val totalMarketUdhaarOwedPaise: Long
)

data class PaymentReminderTemplate(
    val customerName: String,
    val shopName: String,
    val balanceFormatted: String,
    val shopPhone: String,
    val dueDateFormatted: String? = null
) {
    fun toEnglishText(): String {
        val duePart = if (!dueDateFormatted.isNullOrBlank()) " (Payment Due: $dueDateFormatted)" else ""
        return "Namaste $customerName ji, this is a gentle reminder from $shopName that your outstanding balance is $balanceFormatted$duePart. For any queries or payment details, please call $shopPhone. Thank you!"
    }

    fun toHindiText(): String {
        val duePart = if (!dueDateFormatted.isNullOrBlank()) " (भुगतान की तारीख: $dueDateFormatted)" else ""
        return "नमस्ते $customerName जी, $shopName की तरफ से आपकी बाकी उधारी राशि $balanceFormatted$duePart है। कृपया यथाशीघ्र भुगतान करें। संपर्क: $shopPhone धन्यवाद!"
    }
}
