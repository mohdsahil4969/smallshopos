package com.syntaxa.khata.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "shop_profile")
data class ShopProfileEntity(
    @PrimaryKey val id: Int = 1,
    val shopName: String = "",
    val address: String = "",
    val phone: String = "",
    val upiId: String = "",
    val receiptFooter: String = "",
    val language: String = "en",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
