package com.syntaxa.khata.data.repository

import com.syntaxa.khata.data.local.dao.ProductDao
import com.syntaxa.khata.data.local.entity.BillItemEntity
import com.syntaxa.khata.data.local.entity.ProductEntity
import kotlinx.coroutines.flow.Flow

class InventoryRepository(private val productDao: ProductDao) {

    fun observeActiveProducts(): Flow<List<ProductEntity>> =
        productDao.observeActiveProducts()

    fun searchActiveProducts(query: String): Flow<List<ProductEntity>> =
        productDao.searchActiveProducts(query)

    fun observeLowStockProducts(): Flow<List<ProductEntity>> =
        productDao.observeLowStockProducts()

    suspend fun getProductById(id: Long): ProductEntity? =
        productDao.getProductById(id)

    suspend fun addProduct(
        name: String,
        unit: String = "pcs",
        sellingPricePaise: Long,
        costPricePaise: Long = 0L,
        initialStock: Double = 0.0,
        lowStockThreshold: Double = 5.0,
        barcode: String = "",
        category: String = ""
    ): Long {
        require(name.isNotBlank()) { "Product name cannot be empty" }
        require(sellingPricePaise >= 0) { "Selling price cannot be negative" }
        require(costPricePaise >= 0) { "Cost price cannot be negative" }

        val product = ProductEntity(
            name = name.trim(),
            unit = unit.trim().ifBlank { "pcs" },
            sellingPricePaise = sellingPricePaise,
            costPricePaise = costPricePaise,
            stockQuantity = maxOf(0.0, initialStock),
            lowStockThreshold = maxOf(0.0, lowStockThreshold),
            barcode = barcode.trim(),
            category = category.trim()
        )
        return productDao.insertProduct(product)
    }

    suspend fun addProduct(product: ProductEntity): Long =
        addProduct(
            name = product.name,
            unit = product.unit,
            sellingPricePaise = product.sellingPricePaise,
            costPricePaise = product.costPricePaise,
            initialStock = product.stockQuantity,
            lowStockThreshold = product.lowStockThreshold,
            barcode = product.barcode,
            category = product.category
        )

    suspend fun updateProduct(product: ProductEntity) {
        require(product.name.isNotBlank()) { "Product name cannot be empty" }
        require(product.sellingPricePaise >= 0) { "Selling price cannot be negative" }
        productDao.updateProduct(product.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun adjustStock(productId: Long, delta: Double) {
        productDao.adjustStock(productId, delta)
    }

    suspend fun archiveProduct(productId: Long) {
        productDao.archiveProduct(productId)
    }

    /**
     * Atomically deducts stock for itemized sales matching product names.
     */
    suspend fun deductStockForBillItems(items: List<BillItemEntity>) {
        if (items.isEmpty()) return
        val allProducts = productDao.getAllProductsRaw()
        for (item in items) {
            val matchedProduct = if (item.productId != null) {
                productDao.getProductById(item.productId)
            } else {
                allProducts.firstOrNull { 
                    it.name.equals(item.description.trim(), ignoreCase = true) && !it.isArchived 
                }
            }
            if (matchedProduct != null) {
                productDao.adjustStock(matchedProduct.id, -item.quantity)
            }
        }
    }

    suspend fun getAllProductsRaw(): List<ProductEntity> =
        productDao.getAllProductsRaw()
}
