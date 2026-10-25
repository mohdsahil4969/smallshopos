package com.syntaxa.khata.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

object SupplierTransactionConstants {
    const val TYPE_PURCHASE = "PURCHASE"             // Goods bought from supplier (Payable increases)
    const val TYPE_PAYMENT_MADE = "PAYMENT_MADE"     // Shopkeeper paid supplier (Payable decreases)
    const val TYPE_REVERSAL = "REVERSAL"

    const val STATUS_ACTIVE = "ACTIVE"
    const val STATUS_REVERSED = "REVERSED"
}

@Entity(
    tableName = "suppliers",
    indices = [
        Index(value = ["name"]),
        Index(value = ["phone"])
    ]
)
data class SupplierEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String = "",
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val archivedAt: Long? = null
)

@Entity(
    tableName = "supplier_transactions",
    foreignKeys = [
        ForeignKey(
            entity = SupplierEntity::class,
            parentColumns = ["id"],
            childColumns = ["supplierId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["supplierId"]),
        Index(value = ["occurredAt"]),
        Index(value = ["type"]),
        Index(value = ["idempotencyKey"], unique = true)
    ]
)
data class SupplierTransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val supplierId: Long,
    val type: String, // PURCHASE, PAYMENT_MADE, REVERSAL
    val amountPaise: Long,
    val paymentMethod: String = "CASH", // CASH, UPI, CREDIT_LEDGER, OTHER
    val billInvoiceNumber: String = "",
    val notes: String = "",
    val occurredAt: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis(),
    val reversalOfTransactionId: Long? = null,
    val status: String = SupplierTransactionConstants.STATUS_ACTIVE,
    val idempotencyKey: String = java.util.UUID.randomUUID().toString()
)
