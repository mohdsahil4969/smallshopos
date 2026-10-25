package com.syntaxa.khata.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

object BillConstants {
    const val TYPE_QUICK = "QUICK"
    const val TYPE_ITEMIZED = "ITEMIZED"

    const val STATUS_COMPLETED = "COMPLETED"
    const val STATUS_CANCELLED = "CANCELLED"
}

@Entity(
    tableName = "bills",
    foreignKeys = [
        ForeignKey(
            entity = CustomerEntity::class,
            parentColumns = ["id"],
            childColumns = ["customerId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["receiptNumber"], unique = true),
        Index(value = ["customerId"]),
        Index(value = ["createdAt"]),
        Index(value = ["idempotencyKey"])
    ]
)
data class BillEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val receiptNumber: String,
    val customerId: Long? = null,
    val subtotalPaise: Long,
    val discountPaise: Long = 0L,
    val totalPaise: Long,
    val amountPaidPaise: Long,
    val paymentMethod: String = TransactionConstants.METHOD_CASH,
    val billType: String = BillConstants.TYPE_QUICK,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val status: String = BillConstants.STATUS_COMPLETED,
    val cashPaidPaise: Long = 0L,
    val upiPaidPaise: Long = 0L,
    val otherPaidPaise: Long = 0L,
    val idempotencyKey: String = ""
)
