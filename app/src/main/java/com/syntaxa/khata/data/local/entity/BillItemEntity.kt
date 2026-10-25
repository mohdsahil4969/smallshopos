package com.syntaxa.khata.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "bill_items",
    foreignKeys = [
        ForeignKey(
            entity = BillEntity::class,
            parentColumns = ["id"],
            childColumns = ["billId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["billId"]),
        Index(value = ["productId"])
    ]
)
data class BillItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val billId: Long,
    val description: String,
    val quantity: Double = 1.0,
    val unitPricePaise: Long,
    val lineTotalPaise: Long,
    val productId: Long? = null
)
