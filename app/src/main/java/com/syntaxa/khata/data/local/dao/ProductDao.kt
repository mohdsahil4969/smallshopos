package com.syntaxa.khata.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.syntaxa.khata.data.local.entity.ProductEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertProduct(product: ProductEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restoreAllProducts(products: List<ProductEntity>)

    @Update
    suspend fun updateProduct(product: ProductEntity)

    @Query("SELECT * FROM products WHERE id = :id LIMIT 1")
    suspend fun getProductById(id: Long): ProductEntity?

    @Query("SELECT * FROM products WHERE isArchived = 0 ORDER BY name COLLATE NOCASE ASC")
    fun observeActiveProducts(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE isArchived = 0 AND (name LIKE '%' || :query || '%' OR barcode LIKE '%' || :query || '%') ORDER BY name COLLATE NOCASE ASC")
    fun searchActiveProducts(query: String): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE isArchived = 0 AND stockQuantity <= lowStockThreshold ORDER BY stockQuantity ASC")
    fun observeLowStockProducts(): Flow<List<ProductEntity>>

    @Query("UPDATE products SET stockQuantity = stockQuantity + :quantityChange, updatedAt = :updatedAt WHERE id = :productId")
    suspend fun adjustStock(productId: Long, quantityChange: Double, updatedAt: Long = System.currentTimeMillis()): Int

    @Query("UPDATE products SET isArchived = 1, updatedAt = :updatedAt WHERE id = :productId")
    suspend fun archiveProduct(productId: Long, updatedAt: Long = System.currentTimeMillis())

    @Query("SELECT * FROM products")
    suspend fun getAllProductsRaw(): List<ProductEntity>

    @Query("DELETE FROM products")
    suspend fun deleteAllProducts()
}
