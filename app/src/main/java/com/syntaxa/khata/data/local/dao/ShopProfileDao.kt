package com.syntaxa.khata.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.syntaxa.khata.data.local.entity.ShopProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ShopProfileDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveShopProfile(profile: ShopProfileEntity)

    @Query("SELECT * FROM shop_profile WHERE id = 1 LIMIT 1")
    fun observeShopProfile(): Flow<ShopProfileEntity?>

    @Query("SELECT * FROM shop_profile WHERE id = 1 LIMIT 1")
    suspend fun getShopProfile(): ShopProfileEntity?
}
