package com.syntaxa.khata.data.repository

import com.syntaxa.khata.data.local.dao.SupplierDao
import com.syntaxa.khata.data.local.entity.SupplierEntity
import com.syntaxa.khata.data.local.entity.SupplierTransactionConstants
import com.syntaxa.khata.data.local.entity.SupplierTransactionEntity
import com.syntaxa.khata.domain.model.SupplierWithBalance
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class SupplierRepository(private val supplierDao: SupplierDao) {

    fun observeActiveSuppliersWithBalance(): Flow<List<SupplierWithBalance>> =
        supplierDao.observeActiveSuppliersWithBalance()

    suspend fun getSupplierWithBalance(id: Long): SupplierWithBalance? =
        supplierDao.getSupplierWithBalance(id)

    suspend fun createSupplier(name: String, phone: String = "", notes: String = ""): Long {
        require(name.isNotBlank()) { "Supplier name cannot be empty" }
        val supplier = SupplierEntity(
            name = name.trim(),
            phone = phone.trim(),
            notes = notes.trim()
        )
        return supplierDao.insertSupplier(supplier)
    }

    suspend fun recordPurchase(
        supplierId: Long,
        amountPaise: Long,
        billInvoiceNumber: String = "",
        paymentMethod: String = "CREDIT_LEDGER",
        notes: String = "",
        occurredAt: Long = System.currentTimeMillis(),
        idempotencyKey: String = UUID.randomUUID().toString()
    ): Long {
        require(amountPaise > 0) { "Purchase amount must be greater than zero" }
        val tx = SupplierTransactionEntity(
            supplierId = supplierId,
            type = SupplierTransactionConstants.TYPE_PURCHASE,
            amountPaise = amountPaise,
            paymentMethod = paymentMethod,
            billInvoiceNumber = billInvoiceNumber.trim(),
            notes = notes.trim(),
            occurredAt = occurredAt,
            idempotencyKey = idempotencyKey
        )
        return supplierDao.insertTransaction(tx)
    }

    suspend fun recordPaymentToSupplier(
        supplierId: Long,
        amountPaise: Long,
        paymentMethod: String = "CASH",
        notes: String = "",
        occurredAt: Long = System.currentTimeMillis(),
        idempotencyKey: String = UUID.randomUUID().toString()
    ): Long {
        require(amountPaise > 0) { "Payment amount must be greater than zero" }
        val tx = SupplierTransactionEntity(
            supplierId = supplierId,
            type = SupplierTransactionConstants.TYPE_PAYMENT_MADE,
            amountPaise = amountPaise,
            paymentMethod = paymentMethod,
            notes = notes.trim(),
            occurredAt = occurredAt,
            idempotencyKey = idempotencyKey
        )
        return supplierDao.insertTransaction(tx)
    }

    fun observeTransactionsForSupplier(supplierId: Long): Flow<List<SupplierTransactionEntity>> =
        supplierDao.observeTransactionsForSupplier(supplierId)

    suspend fun getTransactionsForSupplier(supplierId: Long): List<SupplierTransactionEntity> =
        supplierDao.getTransactionsForSupplier(supplierId)

    suspend fun getAllSuppliersRaw(): List<SupplierEntity> =
        supplierDao.getAllSuppliersRaw()

    suspend fun getAllTransactionsRaw(): List<SupplierTransactionEntity> =
        supplierDao.getAllTransactionsRaw()
}
