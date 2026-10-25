package com.syntaxa.khata.feature.customers

import android.bluetooth.BluetoothDevice
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.syntaxa.khata.KhataAppContainer
import com.syntaxa.khata.core.model.CurrencyFormatter
import com.syntaxa.khata.core.model.DateTimeUtils
import com.syntaxa.khata.data.local.entity.BillConstants
import com.syntaxa.khata.data.local.entity.FinancialTransactionEntity
import com.syntaxa.khata.data.local.entity.TransactionConstants
import com.syntaxa.khata.domain.model.BillWithItems
import com.syntaxa.khata.domain.model.CustomerFinancialSummary
import com.syntaxa.khata.domain.model.CustomerLedgerEntry
import com.syntaxa.khata.domain.model.CustomerStatement
import com.syntaxa.khata.domain.model.CustomerWithBalance
import com.syntaxa.khata.domain.model.LedgerDateFilter
import com.syntaxa.khata.domain.model.PaymentReminderTemplate
import com.syntaxa.khata.feature.printing.BluetoothPrinterManager
import com.syntaxa.khata.feature.printing.PrintResult
import com.syntaxa.khata.feature.receipts.PdfReceiptGenerator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class DueDateFilter {
    ALL,
    OVERDUE,
    DUE_TODAY,
    UPCOMING
}

class CustomerViewModel(private val container: KhataAppContainer) : ViewModel() {
    private val searchQuery = MutableStateFlow("")
    val searchInput: StateFlow<String> = searchQuery.asStateFlow()

    private val _dueDateFilter = MutableStateFlow(DueDateFilter.ALL)
    val dueDateFilter: StateFlow<DueDateFilter> = _dueDateFilter.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val rawActiveCustomers: StateFlow<List<CustomerWithBalance>> = searchQuery
        .flatMapLatest { query ->
            if (query.isBlank()) {
                container.customerRepository.observeActiveCustomers()
            } else {
                container.customerRepository.searchCustomers(query.trim())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeCustomers: StateFlow<List<CustomerWithBalance>> = combine(
        rawActiveCustomers,
        _dueDateFilter
    ) { list, filter ->
        val startOfToday = DateTimeUtils.getStartOfDay()
        val endOfToday = DateTimeUtils.getEndOfDay()

        when (filter) {
            DueDateFilter.ALL -> list
            DueDateFilter.OVERDUE -> list.filter {
                it.balancePaise > 0 && it.dueDate != null && it.dueDate < startOfToday
            }
            DueDateFilter.DUE_TODAY -> list.filter {
                it.balancePaise > 0 && it.dueDate != null && it.dueDate in startOfToday..endOfToday
            }
            DueDateFilter.UPCOMING -> list.filter {
                it.balancePaise > 0 && it.dueDate != null && it.dueDate > endOfToday
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val archivedCustomers: StateFlow<List<CustomerWithBalance>> = container.customerRepository
        .observeArchivedCustomers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedCustomerId = MutableStateFlow<Long?>(null)
    val selectedCustomerId: StateFlow<Long?> = _selectedCustomerId.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val selectedCustomerWithBalance: StateFlow<CustomerWithBalance?> = _selectedCustomerId
        .flatMapLatest { id ->
            if (id != null) container.customerRepository.observeCustomerWithBalance(id)
            else flowOf(null)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val selectedCustomerTransactions: StateFlow<List<FinancialTransactionEntity>> = _selectedCustomerId
        .flatMapLatest { id ->
            if (id != null) container.financialRepository.observeTransactionsForCustomer(id)
            else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val selectedCustomerBills: StateFlow<List<BillWithItems>> = _selectedCustomerId
        .flatMapLatest { id ->
            if (id != null) container.billingRepository.observeBillsForCustomer(id)
            else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _dateFilter = MutableStateFlow(LedgerDateFilter.ALL_TIME)
    val dateFilter: StateFlow<LedgerDateFilter> = _dateFilter.asStateFlow()

    private val _customDateRange = MutableStateFlow<Pair<Long, Long>?>(null)
    val customDateRange: StateFlow<Pair<Long, Long>?> = _customDateRange.asStateFlow()

    fun setDateFilter(filter: LedgerDateFilter) {
        _dateFilter.value = filter
    }

    fun setCustomDateRange(startEpoch: Long, endEpoch: Long) {
        _customDateRange.value = Pair(startEpoch, endEpoch)
        _dateFilter.value = LedgerDateFilter.CUSTOM
    }

    fun computeEpochRange(filter: LedgerDateFilter, customRange: Pair<Long, Long>?): Pair<Long, Long> {
        val zone = ZoneId.systemDefault()
        val now = LocalDate.now()
        val endOfToday = now.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1L
        return when (filter) {
            LedgerDateFilter.ALL_TIME -> Pair(0L, Long.MAX_VALUE)
            LedgerDateFilter.LAST_30_DAYS -> Pair(now.minusDays(30).atStartOfDay(zone).toInstant().toEpochMilli(), endOfToday)
            LedgerDateFilter.LAST_3_MONTHS -> Pair(now.minusMonths(3).atStartOfDay(zone).toInstant().toEpochMilli(), endOfToday)
            LedgerDateFilter.LAST_YEAR -> Pair(now.minusYears(1).atStartOfDay(zone).toInstant().toEpochMilli(), endOfToday)
            LedgerDateFilter.CUSTOM -> customRange ?: Pair(0L, Long.MAX_VALUE)
        }
    }

    val customerLedgerEntries: StateFlow<List<CustomerLedgerEntry>> = combine(
        selectedCustomerBills,
        selectedCustomerTransactions,
        _dateFilter,
        _customDateRange
    ) { bills, transactions, filter, customRange ->
        val (startEpoch, endEpoch) = computeEpochRange(filter, customRange)
        val entries = mutableListOf<CustomerLedgerEntry>()

        // 1. Itemized bills in range
        bills.forEach { bwi ->
            if (bwi.bill.createdAt in startEpoch..endEpoch) {
                entries.add(CustomerLedgerEntry.BillPurchase(bwi))
            }
        }

        // 2. Direct Khata transactions in range
        transactions.forEach { tx ->
            if (tx.occurredAt in startEpoch..endEpoch) {
                when (tx.type) {
                    TransactionConstants.TYPE_PAYMENT_RECEIVED -> {
                        entries.add(CustomerLedgerEntry.Payment(tx))
                    }
                    TransactionConstants.TYPE_UDHAAR_GIVEN -> {
                        // Avoid double-counting: only add direct udhaar when not linked to a bill
                        if (tx.relatedBillId == null) {
                            entries.add(CustomerLedgerEntry.DirectUdhaar(tx))
                        }
                    }
                    TransactionConstants.TYPE_REVERSAL -> {
                        entries.add(CustomerLedgerEntry.Reversal(tx))
                    }
                }
            }
        }

        entries.sortedWith(compareByDescending<CustomerLedgerEntry> { it.timestamp }.thenByDescending { it.id })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val customerFinancialSummary: StateFlow<CustomerFinancialSummary> = combine(
        selectedCustomerBills,
        selectedCustomerTransactions,
        selectedCustomerWithBalance,
        _dateFilter,
        _customDateRange
    ) { bills, transactions, customerWithBalance, filter, customRange ->
        val (startEpoch, endEpoch) = computeEpochRange(filter, customRange)

        var totalPurchasesPaise = 0L
        var paymentsReceivedPaise = 0L
        var count = 0

        // Non-cancelled bills in period
        bills.forEach { bwi ->
            if (bwi.bill.createdAt in startEpoch..endEpoch) {
                if (bwi.bill.status != BillConstants.STATUS_CANCELLED) {
                    totalPurchasesPaise += bwi.bill.totalPaise
                    paymentsReceivedPaise += bwi.bill.amountPaidPaise
                    count++
                }
            }
        }

        // Direct payments and udhaar in period
        transactions.forEach { tx ->
            if (tx.occurredAt in startEpoch..endEpoch && tx.status == TransactionConstants.STATUS_ACTIVE) {
                when (tx.type) {
                    TransactionConstants.TYPE_PAYMENT_RECEIVED -> {
                        paymentsReceivedPaise += tx.amountPaise
                        count++
                    }
                    TransactionConstants.TYPE_UDHAAR_GIVEN -> {
                        if (tx.relatedBillId == null) {
                            totalPurchasesPaise += tx.amountPaise
                            count++
                        }
                    }
                }
            }
        }

        CustomerFinancialSummary(
            totalPurchasesPaise = totalPurchasesPaise,
            paymentsReceivedPaise = paymentsReceivedPaise,
            currentOutstandingBalancePaise = customerWithBalance?.balancePaise ?: 0L,
            transactionCount = count,
            dateFilter = filter,
            startEpoch = startEpoch,
            endEpoch = endEpoch
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CustomerFinancialSummary(0L, 0L, 0L, 0))

    fun onSearchQueryChanged(query: String) {
        searchQuery.value = query
    }

    fun setDueDateFilter(filter: DueDateFilter) {
        _dueDateFilter.value = filter
    }

    fun selectCustomer(id: Long?) {
        _selectedCustomerId.value = id
    }

    fun addCustomer(
        name: String,
        phone: String,
        notes: String,
        dueDate: Long? = null,
        onSuccess: (Long) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                if (name.isBlank()) {
                    onError("Customer name is required")
                    return@launch
                }
                val newId = container.customerRepository.createCustomer(name, phone, notes, dueDate)
                onSuccess(newId)
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "Failed to add customer")
            }
        }
    }

    fun updateDueDate(customerId: Long, dueDate: Long?, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                container.customerRepository.updateDueDate(customerId, dueDate)
                onComplete()
            } catch (_: Exception) { }
        }
    }

    suspend fun isPhoneDuplicate(phone: String, excludeId: Long? = null): Boolean {
        return container.customerRepository.isPhoneDuplicate(phone, excludeId)
    }

    fun recordUdhaar(
        customerId: Long,
        amountPaise: Long,
        description: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                if (amountPaise <= 0) {
                    onError("Amount must be greater than 0")
                    return@launch
                }
                container.financialRepository.recordUdhaar(customerId, amountPaise, description)
                onSuccess()
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "Failed to record Udhaar")
            }
        }
    }

    fun recordPayment(
        customerId: Long,
        amountPaise: Long,
        paymentMethod: String,
        description: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                if (amountPaise <= 0) {
                    onError("Amount must be greater than 0")
                    return@launch
                }
                container.financialRepository.recordPayment(customerId, amountPaise, paymentMethod, description)
                onSuccess()
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "Failed to record payment")
            }
        }
    }

    fun reverseTransaction(transactionId: Long, reason: String, onComplete: () -> Unit) {
        viewModelScope.launch {
            try {
                container.financialRepository.reverseTransaction(transactionId, reason)
                onComplete()
            } catch (_: Exception) { }
        }
    }

    fun archiveCustomer(customerId: Long, onComplete: () -> Unit) {
        viewModelScope.launch {
            container.customerRepository.archiveCustomer(customerId)
            onComplete()
        }
    }

    fun restoreCustomer(customerId: Long, onComplete: () -> Unit) {
        viewModelScope.launch {
            container.customerRepository.restoreCustomer(customerId)
            onComplete()
        }
    }

    fun updateCustomer(
        customerId: Long,
        name: String,
        phone: String,
        notes: String,
        dueDate: Long? = null,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                if (name.isBlank()) {
                    onError("Customer name is required")
                    return@launch
                }
                val existing = container.customerRepository.getCustomerById(customerId)
                    ?: throw IllegalArgumentException("Customer not found")
                val updated = existing.copy(
                    name = name.trim(),
                    phone = phone.trim(),
                    notes = notes.trim(),
                    dueDate = dueDate
                )
                container.customerRepository.updateCustomer(updated)
                onSuccess()
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "Failed to update customer")
            }
        }
    }

    suspend fun generateTransactionPdf(context: Context, customerId: Long, transaction: FinancialTransactionEntity): File {
        val shop = container.shopProfileRepository.getShopProfile()
        val customer = container.customerRepository.getCustomerById(customerId)
            ?: throw IllegalArgumentException("Customer not found")
        val statement = container.financialRepository.getCustomerStatement(customerId)
        return PdfReceiptGenerator.generateTransactionReceipt(context, shop, customer, transaction, statement.currentBalancePaise)
    }

    suspend fun generateStatementPdf(
        context: Context,
        customerId: Long,
        startEpoch: Long = 0L,
        endEpoch: Long = Long.MAX_VALUE
    ): File {
        val shop = container.shopProfileRepository.getShopProfile()
        val statement = container.financialRepository.getCustomerStatement(customerId, startEpoch, endEpoch)
        return PdfReceiptGenerator.generateStatementReceipt(context, shop, statement)
    }

    suspend fun generateBillPdf(context: Context, billWithItems: BillWithItems): File {
        val shop = container.shopProfileRepository.getShopProfile()
        return PdfReceiptGenerator.generateBillReceipt(context, shop, billWithItems)
    }

    suspend fun printBillThermal(
        context: Context,
        billWithItems: BillWithItems,
        device: BluetoothDevice? = null
    ): PrintResult {
        val shop = container.shopProfileRepository.getShopProfile()
        val printerManager = BluetoothPrinterManager(context)
        val targetDevice = device ?: printerManager.getPairedPrinters().firstOrNull()
            ?: return PrintResult.Failure("No paired Bluetooth printer found")
        return printerManager.printBillReceipt(targetDevice, shop, billWithItems)
    }

    fun getPairedPrinters(context: Context): List<BluetoothDevice> {
        return BluetoothPrinterManager(context).getPairedPrinters()
    }

    suspend fun getReminderTemplate(customerId: Long): PaymentReminderTemplate {
        val shop = container.shopProfileRepository.getShopProfile()
        val statement = container.financialRepository.getCustomerStatement(customerId)
        val dueStr = statement.customer.dueDate?.let { epoch ->
            val date = Instant.ofEpochMilli(epoch).atZone(ZoneId.systemDefault()).toLocalDate()
            date.format(DateTimeFormatter.ofPattern("dd-MMM-yyyy"))
        }
        return PaymentReminderTemplate(
            customerName = statement.customer.name,
            shopName = shop.shopName,
            balanceFormatted = CurrencyFormatter.formatPaise(statement.currentBalancePaise),
            shopPhone = shop.phone,
            dueDateFormatted = dueStr
        )
    }

    class Factory(private val container: KhataAppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return CustomerViewModel(container) as T
        }
    }
}
