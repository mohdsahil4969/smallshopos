package com.syntaxa.khata.feature.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.syntaxa.khata.KhataAppContainer
import com.syntaxa.khata.data.local.entity.ProductEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class InventoryViewModel(private val container: KhataAppContainer) : ViewModel() {
    private val searchQuery = MutableStateFlow("")
    val query: StateFlow<String> = searchQuery.asStateFlow()

    private val showOnlyLowStock = MutableStateFlow(false)
    val onlyLowStock: StateFlow<Boolean> = showOnlyLowStock.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val products: StateFlow<List<ProductEntity>> = combine(searchQuery, showOnlyLowStock) { q, lowStock ->
        Pair(q, lowStock)
    }.flatMapLatest { (q, lowStock) ->
        if (lowStock) {
            container.inventoryRepository.observeLowStockProducts()
        } else if (q.isNotBlank()) {
            container.inventoryRepository.searchActiveProducts(q)
        } else {
            container.inventoryRepository.observeActiveProducts()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setQuery(q: String) {
        searchQuery.value = q
    }

    fun toggleLowStockFilter() {
        showOnlyLowStock.value = !showOnlyLowStock.value
    }

    fun saveProduct(
        id: Long = 0,
        name: String,
        unit: String,
        sellingPricePaise: Long,
        costPricePaise: Long = 0L,
        stockQuantity: Double,
        lowStockThreshold: Double = 5.0,
        barcode: String = "",
        category: String = ""
    ) {
        viewModelScope.launch {
            if (id == 0L) {
                container.inventoryRepository.addProduct(
                    ProductEntity(
                        name = name.trim(),
                        unit = unit.trim().ifBlank { "pcs" },
                        sellingPricePaise = sellingPricePaise,
                        costPricePaise = costPricePaise,
                        stockQuantity = stockQuantity,
                        lowStockThreshold = lowStockThreshold,
                        barcode = barcode.trim(),
                        category = category.trim()
                    )
                )
            } else {
                container.inventoryRepository.updateProduct(
                    ProductEntity(
                        id = id,
                        name = name.trim(),
                        unit = unit.trim().ifBlank { "pcs" },
                        sellingPricePaise = sellingPricePaise,
                        costPricePaise = costPricePaise,
                        stockQuantity = stockQuantity,
                        lowStockThreshold = lowStockThreshold,
                        barcode = barcode.trim(),
                        category = category.trim()
                    )
                )
            }
        }
    }

    fun adjustStock(productId: Long, delta: Double) {
        viewModelScope.launch {
            container.inventoryRepository.adjustStock(productId, delta)
        }
    }

    fun archiveProduct(productId: Long) {
        viewModelScope.launch {
            container.inventoryRepository.archiveProduct(productId)
        }
    }

    class Factory(private val container: KhataAppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return InventoryViewModel(container) as T
        }
    }
}
