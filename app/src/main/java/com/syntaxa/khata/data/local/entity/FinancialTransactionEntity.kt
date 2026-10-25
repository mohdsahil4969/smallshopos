package com.syntaxa.khata.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

object TransactionConstants {
    const val TYPE_UDHAAR_GIVEN = "UDHAAR_GIVEN"
    const val TYPE_PAYMENT_RECEIVED = "PAYMENT_RECEIVED"
    const val TYPE_REVERSAL = "REVERSAL"

    const val METHOD_CASH = "CASH"
    const val METHOD_UPI = "UPI"
    const val METHOD_OTHER = "OTHER"
    const val METHOD_CREDIT_LEDGER = "CREDIT_LEDGER"

    const val STATUS_ACTIVE = "ACTIVE"
    const val STATUS_REVERSED = "REVERSED"
}

@Entity(
    tableName = "financial_transactions",
    foreignKeys = [
        ForeignKey(
            entity = CustomerEntity::class,
            parentColumns = ["id"],
            childColumns = ["customerId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["customerId"]),
        Index(value = ["occurredAt"]),
        Index(value = ["type"]),
        Index(value = ["dueDate"]),
        Index(value = ["idempotencyKey"], unique = true)
    ]
)
data class FinancialTransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String, // UDHAAR_GIVEN, PAYMENT_RECEIVED, REVERSAL
    val customerId: Long?,
    val amountPaise: Long,
    val paymentMethod: String = TransactionConstants.METHOD_CASH,
    val description: String = "",
    val occurredAt: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis(),
    val reversalOfTransactionId: Long? = null,
    val relatedBillId: Long? = null,
    val idempotencyKey: String = java.util.UUID.randomUUID().toString(),
    val status: String = TransactionConstants.STATUS_ACTIVE,
    val dueDate: Long? = null
)
