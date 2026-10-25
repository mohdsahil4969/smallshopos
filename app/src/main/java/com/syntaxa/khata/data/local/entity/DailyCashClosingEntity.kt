package com.syntaxa.khata.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "daily_cash_closings",
    indices = [
        Index(value = ["closingDateEpoch"], unique = true)
    ]
)
data class DailyCashClosingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val closingDateEpoch: Long, // Start of day epoch millis
    val openingCashPaise: Long = 0L,
    val cashSalesPaise: Long,
    val cashKhataCollectedPaise: Long,
    val cashExpensesPaise: Long,
    val expectedCashPaise: Long, // openingCash + cashSales + cashKhataCollected - cashExpenses
    val countedCashPaise: Long,
    val differencePaise: Long, // countedCash - expectedCash (positive = surplus, negative = shortage)
    val notes: String = "",
    val closedAt: Long = System.currentTimeMillis()
)
