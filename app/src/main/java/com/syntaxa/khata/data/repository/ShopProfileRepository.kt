package com.syntaxa.khata.data.repository

import com.syntaxa.khata.data.local.dao.ShopProfileDao
import com.syntaxa.khata.data.local.entity.ShopProfileEntity
import kotlinx.coroutines.flow.Flow

class ShopProfileRepository(private val shopProfileDao: ShopProfileDao) {
    fun observeShopProfile(): Flow<ShopProfileEntity?> =
        shopProfileDao.observeShopProfile()

    suspend fun getShopProfile(): ShopProfileEntity {
        return shopProfileDao.getShopProfile() ?: ShopProfileEntity(
            id = 1,
            shopName = "My Kirana Store",
            receiptFooter = "Thank you! Visit again."
        )
    }

    suspend fun updateShopProfile(profile: ShopProfileEntity) {
        require(profile.shopName.isNotBlank()) { "Shop name is required" }
        shopProfileDao.saveShopProfile(
            profile.copy(
                id = 1,
                shopName = profile.shopName.trim(),
                address = profile.address.trim(),
                phone = profile.phone.trim(),
                upiId = profile.upiId.trim(),
                receiptFooter = profile.receiptFooter.trim(),
                updatedAt = System.currentTimeMillis()
            )
        )
    }
}
