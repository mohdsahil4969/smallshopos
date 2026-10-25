package com.syntaxa.khata.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

object ExpenseConstants {
    const val CAT_STOCK = "Stock purchase"
    const val CAT_TRANSPORT = "Transport"
    const val CAT_RENT = "Rent"
    const val CAT_ELECTRICITY = "Electricity"
    const val CAT_WAGES = "Wages"
    const val CAT_OTHER = "Other"

    const val STATUS_ACTIVE = "ACTIVE"
    const val STATUS_REVERSED = "REVERSED"
}

@Entity(
    tableName = "expenses",
    indices = [
        Index(value = ["occurredAt"]),
        Index(value = ["category"])
    ]
)
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amountPaise: Long,
    val category: String = ExpenseConstants.CAT_OTHER,
    val description: String = "",
    val paymentMethod: String = TransactionConstants.METHOD_CASH,
    val occurredAt: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis(),
    val status: String = ExpenseConstants.STATUS_ACTIVE
)
