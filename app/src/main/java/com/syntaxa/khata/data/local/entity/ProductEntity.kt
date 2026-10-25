package com.syntaxa.khata.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "products",
    indices = [
        Index(value = ["name"]),
        Index(value = ["barcode"])
    ]
)
data class ProductEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val unit: String = "pcs", // pcs, kg, g, ltr, ml, packet
    val sellingPricePaise: Long,
    val costPricePaise: Long = 0L,
    val stockQuantity: Double = 0.0,
    val lowStockThreshold: Double = 5.0,
    val barcode: String = "",
    val category: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isArchived: Boolean = false
) {
    val isLowStock: Boolean
        get() = stockQuantity <= lowStockThreshold
}
