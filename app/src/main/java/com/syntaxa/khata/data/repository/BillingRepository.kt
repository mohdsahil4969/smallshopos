package com.syntaxa.khata.data.repository

import androidx.room.withTransaction
import com.syntaxa.khata.core.model.DateTimeUtils
import com.syntaxa.khata.data.local.KhataDatabase
import com.syntaxa.khata.data.local.dao.BillDao
import com.syntaxa.khata.data.local.dao.FinancialTransactionDao
import com.syntaxa.khata.data.local.dao.ProductDao
import com.syntaxa.khata.data.local.entity.BillConstants
import com.syntaxa.khata.data.local.entity.BillEntity
import com.syntaxa.khata.data.local.entity.BillItemEntity
import com.syntaxa.khata.data.local.entity.FinancialTransactionEntity
import com.syntaxa.khata.data.local.entity.TransactionConstants
import com.syntaxa.khata.domain.model.BillWithItems
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

class BillingRepository(
    private val billDao: BillDao,
    private val financialTransactionDao: FinancialTransactionDao,
    private val database: KhataDatabase? = null,
    private val productDao: ProductDao? = null
) {
    private suspend fun <R> executeTransactional(block: suspend () -> R): R {
        return if (database != null) {
            database.withTransaction { block() }
        } else {
            block()
        }
    }

    fun observeBillWithItems(id: Long): Flow<BillWithItems?> =
        billDao.observeBillWithItems(id)

    suspend fun getBillWithItems(id: Long): BillWithItems? =
        billDao.getBillWithItems(id)

    fun observeRecentBills(limit: Int = 10): Flow<List<BillWithItems>> =
        billDao.observeRecentBills(limit)

    fun observeBillsByDateRange(startEpoch: Long, endEpoch: Long): Flow<List<BillWithItems>> =
        billDao.observeBillsByDateRange(startEpoch, endEpoch)

    suspend fun getBillsByDateRange(startEpoch: Long, endEpoch: Long): List<BillWithItems> =
        billDao.getBillsByDateRange(startEpoch, endEpoch)

    fun observeBillsForCustomer(customerId: Long): Flow<List<BillWithItems>> =
        billDao.observeBillsForCustomer(customerId)

    suspend fun getBillsForCustomer(customerId: Long): List<BillWithItems> =
        billDao.getBillsForCustomer(customerId)

    fun observeBillsForCustomerByDateRange(customerId: Long, startEpoch: Long, endEpoch: Long): Flow<List<BillWithItems>> =
        billDao.observeBillsForCustomerByDateRange(customerId, startEpoch, endEpoch)

    suspend fun getBillsForCustomerByDateRange(customerId: Long, startEpoch: Long, endEpoch: Long): List<BillWithItems> =
        billDao.getBillsForCustomerByDateRange(customerId, startEpoch, endEpoch)

    suspend fun generateNextReceiptNumber(): String {
        val todayStart = DateTimeUtils.getStartOfDay()
        val todayEnd = DateTimeUtils.getEndOfDay()
        val countToday = billDao.countBillsForDay(todayStart, todayEnd)
        val datePrefix = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd", Locale.ENGLISH))
        return String.format(Locale.ENGLISH, "BILL-%s-%03d", datePrefix, countToday + 1)
    }

    /**
     * Atomically creates a Bill, its Bill Items, any associated Customer Khata Udhaar entry,
     * and deducts inventory stock for catalog products within a single Room database transaction boundary.
     * If any part fails (including insufficient inventory stock), the entire transaction rolls back cleanly.
     */
    suspend fun createBill(
        receiptNumber: String,
        customerId: Long?,
        subtotalPaise: Long,
        discountPaise: Long = 0L,
        totalPaise: Long,
        amountPaidPaise: Long,
        paymentMethod: String,
        billType: String,
        notes: String = "",
        items: List<BillItemEntity> = emptyList(),
        cashPaidPaise: Long = 0L,
        upiPaidPaise: Long = 0L,
        otherPaidPaise: Long = 0L,
        idempotencyKey: String = UUID.randomUUID().toString()
    ): Long {
        require(totalPaise >= 0) { "Total bill amount cannot be negative" }
        require(amountPaidPaise in 0..totalPaise) { "Amount paid must be between 0 and total bill amount" }
        val unpaidPaise = totalPaise - amountPaidPaise

        // Rule: Credit sales MUST be linked to a customer. Unassigned credit is strictly prohibited.
        if (unpaidPaise > 0) {
            require(customerId != null) { "Customer must be selected for credit (Udhaar) sales" }
        }

        val totalAllocatedPaid = cashPaidPaise + upiPaidPaise + otherPaidPaise
        require(totalAllocatedPaid == amountPaidPaise) {
            "Sum of payment allocations ($totalAllocatedPaid) does not equal total paid amount ($amountPaidPaise)"
        }

        return executeTransactional {
            // 1. Check idempotency to prevent duplicate writes on network / UI retry
            if (idempotencyKey.isNotBlank()) {
                val existing = billDao.findByIdempotencyKey(idempotencyKey)
                if (existing != null) {
                    return@executeTransactional existing.id
                }
            }

            // 2. Pre-validate stock availability for all catalog-linked items before making any mutations
            if (productDao != null) {
                for (item in items) {
                    if (item.productId != null) {
                        val product = productDao.getProductById(item.productId)
                            ?: throw IllegalArgumentException("Product #${item.productId} ('${item.description}') not found in inventory catalog")
                        if (product.isArchived) {
                            throw IllegalStateException("Cannot sell archived product '${product.name}'")
                        }
                        if (product.stockQuantity < item.quantity) {
                            throw IllegalStateException(
                                "Insufficient stock for '${product.name}': requested ${item.quantity} ${product.unit}, available ${product.stockQuantity} ${product.unit}"
                            )
                        }
                    }
                }
            }

            val bill = BillEntity(
                receiptNumber = receiptNumber,
                customerId = customerId,
                subtotalPaise = subtotalPaise,
                discountPaise = discountPaise,
                totalPaise = totalPaise,
                amountPaidPaise = amountPaidPaise,
                paymentMethod = paymentMethod,
                billType = billType,
                notes = notes.trim(),
                createdAt = System.currentTimeMillis(),
                status = BillConstants.STATUS_COMPLETED,
                cashPaidPaise = cashPaidPaise,
                upiPaidPaise = upiPaidPaise,
                otherPaidPaise = otherPaidPaise,
                idempotencyKey = idempotencyKey
            )

            // 3. Insert bill and bill items
            val billId = billDao.createBillWithItems(bill, items)

            // 4. Atomically deduct inventory stock for all catalog-linked items
            if (productDao != null) {
                for (item in items) {
                    if (item.productId != null) {
                        productDao.adjustStock(item.productId, -item.quantity)
                    }
                }
            }

            // 5. Atomically record Udhaar ledger entry if unpaid balance exists
            if (customerId != null && unpaidPaise > 0) {
                val udhaarTx = FinancialTransactionEntity(
                    type = TransactionConstants.TYPE_UDHAAR_GIVEN,
                    customerId = customerId,
                    amountPaise = unpaidPaise,
                    paymentMethod = TransactionConstants.METHOD_CREDIT_LEDGER,
                    description = "Bill #$receiptNumber (Balance due)",
                    occurredAt = bill.createdAt,
                    createdAt = System.currentTimeMillis(),
                    relatedBillId = billId,
                    idempotencyKey = "BILL-$billId-UDHAAR-$idempotencyKey",
                    status = TransactionConstants.STATUS_ACTIVE
                )
                financialTransactionDao.insertTransaction(udhaarTx)
            }

            billId
        }
    }

    /**
     * Cancels a bill and atomically restores inventory stock for all linked catalog products,
     * as well as reversing any associated customer Udhaar ledger entries.
     */
    suspend fun cancelBill(billId: Long, reason: String = ""): Boolean {
        return executeTransactional {
            val billWithItems = billDao.getBillWithItems(billId) ?: return@executeTransactional false
            if (billWithItems.bill.status == BillConstants.STATUS_CANCELLED) {
                return@executeTransactional true
            }

            // 1. Mark bill as cancelled
            billDao.updateBillStatus(billId, BillConstants.STATUS_CANCELLED)

            // 2. Atomically reverse inventory stock deductions for all catalog items in this bill
            if (productDao != null) {
                for (item in billWithItems.items) {
                    if (item.productId != null) {
                        productDao.adjustStock(item.productId, item.quantity)
                    }
                }
            }

            // 3. Reverse any linked active Udhaar transaction in ledger
            val customerId = billWithItems.bill.customerId
            if (customerId != null) {
                val txs = financialTransactionDao.getTransactionsForCustomer(customerId)
                for (tx in txs) {
                    if (tx.relatedBillId == billId &&
                        tx.status == TransactionConstants.STATUS_ACTIVE &&
                        tx.type == TransactionConstants.TYPE_UDHAAR_GIVEN
                    ) {
                        financialTransactionDao.executeReversal(
                            targetTransactionId = tx.id,
                            reason = if (reason.isNotBlank()) "Bill #${billWithItems.bill.receiptNumber} cancelled: $reason"
                            else "Bill #${billWithItems.bill.receiptNumber} cancelled"
                        )
                    }
                }
            }
            true
        }
    }

    suspend fun getAllBillsRaw(): List<BillEntity> = billDao.getAllBillsRaw()
    suspend fun getAllBillItemsRaw(): List<BillItemEntity> = billDao.getAllBillItemsRaw()
}
