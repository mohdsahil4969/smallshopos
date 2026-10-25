package com.syntaxa.khata.feature.billing

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.syntaxa.khata.KhataAppContainer
import com.syntaxa.khata.core.model.DateTimeUtils
import com.syntaxa.khata.data.local.entity.BillConstants
import com.syntaxa.khata.data.local.entity.BillItemEntity
import com.syntaxa.khata.data.local.entity.ShopProfileEntity
import com.syntaxa.khata.data.local.entity.TransactionConstants
import com.syntaxa.khata.domain.model.BillWithItems
import com.syntaxa.khata.domain.model.CustomerWithBalance
import com.syntaxa.khata.feature.printing.BluetoothPrinterManager
import com.syntaxa.khata.feature.printing.PrintResult
import com.syntaxa.khata.feature.receipts.PdfReceiptGenerator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

class BillingViewModel(private val container: KhataAppContainer) : ViewModel() {
    val activeCustomers: StateFlow<List<CustomerWithBalance>> = container.customerRepository
        .observeActiveCustomers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentBills: StateFlow<List<BillWithItems>> = container.billingRepository
        .observeRecentBills(20)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _lastGeneratedBill = MutableStateFlow<BillWithItems?>(null)
    val lastGeneratedBill: StateFlow<BillWithItems?> = _lastGeneratedBill.asStateFlow()

    fun createBill(
        customerId: Long?,
        subtotalPaise: Long,
        discountPaise: Long,
        totalPaise: Long,
        amountPaidPaise: Long,
        paymentMethod: String,
        billType: String,
        notes: String,
        items: List<BillItemEntity>,
        cashPaidPaise: Long = 0L,
        upiPaidPaise: Long = 0L,
        otherPaidPaise: Long = 0L,
        idempotencyKey: String = UUID.randomUUID().toString(),
        onSuccess: (BillWithItems) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                if (totalPaise <= 0) {
                    onError("Bill total must be greater than 0")
                    return@launch
                }
                val unpaid = totalPaise - amountPaidPaise
                if (unpaid > 0 && customerId == null) {
                    onError("Please select a customer for credit (Udhaar) sales.")
                    return@launch
                }
                val receiptNumber = container.billingRepository.generateNextReceiptNumber()
                val billId = container.billingRepository.createBill(
                    receiptNumber = receiptNumber,
                    customerId = customerId,
                    subtotalPaise = subtotalPaise,
                    discountPaise = discountPaise,
                    totalPaise = totalPaise,
                    amountPaidPaise = amountPaidPaise,
                    paymentMethod = paymentMethod,
                    billType = billType,
                    notes = notes,
                    items = items,
                    cashPaidPaise = cashPaidPaise,
                    upiPaidPaise = upiPaidPaise,
                    otherPaidPaise = otherPaidPaise,
                    idempotencyKey = idempotencyKey
                )

                val fullBill = container.billingRepository.getBillWithItems(billId)
                if (fullBill != null) {
                    _lastGeneratedBill.value = fullBill
                    onSuccess(fullBill)
                } else {
                    onError("Failed to load saved bill")
                }
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "Failed to generate bill")
            }
        }
    }

    fun cancelBill(billId: Long, reason: String = "", onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val ok = container.billingRepository.cancelBill(billId, reason)
                onComplete(ok)
            } catch (e: Exception) {
                onComplete(false)
            }
        }
    }

    suspend fun generateBillPdf(context: Context, billWithItems: BillWithItems): File {
        val shop = container.shopProfileRepository.getShopProfile()
        return PdfReceiptGenerator.generateBillReceipt(context, shop, billWithItems)
    }

    suspend fun printBillThermal(
        context: Context,
        device: android.bluetooth.BluetoothDevice,
        billWithItems: BillWithItems,
        paperWidth: Int
    ): PrintResult {
        val shop = container.shopProfileRepository.getShopProfile()
        val pm = BluetoothPrinterManager(context)
        return pm.printBillReceipt(device, shop, billWithItems, paperWidth)
    }

    class Factory(private val container: KhataAppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return BillingViewModel(container) as T
        }
    }
}
