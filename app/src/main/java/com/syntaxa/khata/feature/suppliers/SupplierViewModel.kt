package com.syntaxa.khata.feature.suppliers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.syntaxa.khata.KhataAppContainer
import com.syntaxa.khata.data.local.entity.SupplierTransactionEntity
import com.syntaxa.khata.domain.model.SupplierWithBalance
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class SupplierViewModel(private val container: KhataAppContainer) : ViewModel() {
    private val searchQuery = MutableStateFlow("")
    val query: StateFlow<String> = searchQuery.asStateFlow()

    val suppliers: StateFlow<List<SupplierWithBalance>> = combine(
        container.supplierRepository.observeActiveSuppliersWithBalance(),
        searchQuery
    ) { list, q ->
        if (q.isBlank()) list
        else list.filter {
            it.name.contains(q, ignoreCase = true) || it.phone.contains(q, ignoreCase = true)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val selectedSupplierId = MutableStateFlow<Long?>(null)
    @OptIn(ExperimentalCoroutinesApi::class)
    val supplierTransactions: StateFlow<List<SupplierTransactionEntity>> = selectedSupplierId
        .flatMapLatest { id ->
            if (id != null) container.supplierRepository.observeTransactionsForSupplier(id)
            else flowOf(emptyList())
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectSupplier(id: Long?) {
        selectedSupplierId.value = id
    }

    fun setQuery(q: String) {
        searchQuery.value = q
    }

    fun addSupplier(name: String, phone: String, notes: String) {
        viewModelScope.launch {
            container.supplierRepository.createSupplier(name, phone, notes)
        }
    }

    fun recordPurchase(supplierId: Long, amountPaise: Long, invoiceNumber: String, notes: String) {
        viewModelScope.launch {
            container.supplierRepository.recordPurchase(
                supplierId = supplierId,
                amountPaise = amountPaise,
                billInvoiceNumber = invoiceNumber,
                notes = notes
            )
        }
    }

    fun recordPayment(supplierId: Long, amountPaise: Long, paymentMethod: String, notes: String) {
        viewModelScope.launch {
            container.supplierRepository.recordPaymentToSupplier(
                supplierId = supplierId,
                amountPaise = amountPaise,
                paymentMethod = paymentMethod,
                notes = notes
            )
        }
    }

    class Factory(private val container: KhataAppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SupplierViewModel(container) as T
        }
    }
}
