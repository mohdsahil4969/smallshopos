package com.syntaxa.khata.feature.billing

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.syntaxa.khata.R
import com.syntaxa.khata.core.model.CurrencyFormatter
import com.syntaxa.khata.core.ui.components.KiranaCard
import com.syntaxa.khata.core.ui.theme.KiranaCreditGreen
import com.syntaxa.khata.core.ui.theme.KiranaDebitRed
import com.syntaxa.khata.core.ui.theme.KiranaGreenPrimary
import com.syntaxa.khata.data.local.entity.BillConstants
import com.syntaxa.khata.data.local.entity.BillItemEntity
import com.syntaxa.khata.data.local.entity.ProductEntity
import com.syntaxa.khata.data.local.entity.TransactionConstants
import com.syntaxa.khata.domain.model.BillWithItems
import com.syntaxa.khata.domain.model.CustomerWithBalance
import com.syntaxa.khata.feature.receipts.ShareHelper
import kotlinx.coroutines.launch
import java.util.UUID

data class DraftItem(
    val id: String = UUID.randomUUID().toString(),
    val description: String,
    val quantity: Double,
    val unitPricePaise: Long,
    val lineTotalPaise: Long,
    val productId: Long? = null
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BillingScreen(
    viewModel: BillingViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val customers by viewModel.activeCustomers.collectAsState()
    val activeProducts by viewModel.activeProducts.collectAsState()

    var isItemizedMode by remember { mutableStateOf(false) }
    var selectedCustomer by remember { mutableStateOf<CustomerWithBalance?>(null) }
    var quickItemName by remember { mutableStateOf("") }
    var selectedProduct by remember { mutableStateOf<ProductEntity?>(null) }
    var showCreateProductDialog by remember { mutableStateOf(false) }
    var newProductDialogName by remember { mutableStateOf("") }
    var newProductDialogPrice by remember { mutableStateOf("") }
    var quickTotalInput by remember { mutableStateOf("") }
    var discountInput by remember { mutableStateOf("") }
    var amountPaidInput by remember { mutableStateOf("") }
    var paymentMethod by remember { mutableStateOf(TransactionConstants.METHOD_CASH) }
    var splitCashInput by remember { mutableStateOf("") }
    var splitUpiInput by remember { mutableStateOf("") }
    var billNotes by remember { mutableStateOf("") }
    var formIdempotencyKey by remember { mutableStateOf(UUID.randomUUID().toString()) }

    // Itemized items
    val draftItems = remember { mutableStateListOf<DraftItem>() }
    var newItemName by remember { mutableStateOf("") }
    var newItemQty by remember { mutableStateOf("1") }
    var newItemPrice by remember { mutableStateOf("") }

    var createdBillForPreview by remember { mutableStateOf<BillWithItems?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }

    // Calculate subtotal
    val subtotalPaise: Long = if (isItemizedMode) {
        draftItems.sumOf { it.lineTotalPaise }
    } else {
        try { CurrencyFormatter.parseInputToPaise(quickTotalInput) } catch (_: Exception) { 0L }
    }

    val discountPaise: Long = try {
        CurrencyFormatter.parseInputToPaise(discountInput)
    } catch (_: Exception) { 0L }

    val totalBillPaise = maxOf(0L, subtotalPaise - discountPaise)

    // Parse received amount with strict validation (never silent full payment fallback)
    val parsedAmountPaid: Long? = if (amountPaidInput.isBlank()) {
        0L // Blank input defaults explicitly to 0 paid (remaining as Udhaar if customer selected)
    } else {
        try {
            val v = CurrencyFormatter.parseInputToPaise(amountPaidInput)
            if (v >= 0) v else null
        } catch (_: Exception) {
            null
        }
    }

    val actualAmountPaidPaise = parsedAmountPaid ?: 0L
    val isAmountValid = parsedAmountPaid != null && actualAmountPaidPaise in 0..totalBillPaise
    val balanceUdhaarPaise = maxOf(0L, totalBillPaise - actualAmountPaidPaise)

    // Credit sale validation: if unpaid balance exists, a customer MUST be chosen
    val isCreditSaleMissingCustomer = balanceUdhaarPaise > 0 && selectedCustomer == null

    // Split tender calculations
    val isSplitMode = paymentMethod == "SPLIT"
    val splitCashPaise = try { CurrencyFormatter.parseInputToPaise(splitCashInput) } catch (_: Exception) { 0L }
    val splitUpiPaise = try { CurrencyFormatter.parseInputToPaise(splitUpiInput) } catch (_: Exception) { 0L }
    val isSplitAllocatedCorrectly = !isSplitMode || (splitCashPaise + splitUpiPaise == actualAmountPaidPaise)

    val canSubmit = !isSubmitting && totalBillPaise > 0 && isAmountValid && !isCreditSaleMissingCustomer && isSplitAllocatedCorrectly

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp)
    ) {
        // Mode Selector: Quick Total vs Itemized
        item {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = !isItemizedMode,
                    onClick = { isItemizedMode = false },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) {
                    Text(
                        text = stringResource(R.string.quick_amount_mode),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 13.sp
                    )
                }
                SegmentedButton(
                    selected = isItemizedMode,
                    onClick = { isItemizedMode = true },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) {
                    Text(
                        text = stringResource(R.string.itemized_mode),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 13.sp
                    )
                }
            }
        }

        // Customer Selection Card
        item {
            KiranaCard {
                Text(
                    text = stringResource(R.string.select_customer),
                    style = MaterialTheme.typography.labelMedium
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedCustomer == null,
                        onClick = { selectedCustomer = null },
                        label = { Text(stringResource(R.string.walk_in_customer)) }
                    )
                }

                if (customers.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    CustomerDropdown(
                        customers = customers,
                        selectedCustomer = selectedCustomer,
                        onSelect = { selectedCustomer = it }
                    )
                }
            }
        }

        // Quick Total Mode Input
        if (!isItemizedMode) {
            item {
                KiranaCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.item_name_optional),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        TextButton(
                            onClick = {
                                newProductDialogName = quickItemName
                                newProductDialogPrice = quickTotalInput
                                showCreateProductDialog = true
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.add_new_product_btn), fontSize = 12.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedTextField(
                        value = quickItemName,
                        onValueChange = {
                            quickItemName = it
                            if (selectedProduct != null && selectedProduct?.name != it) {
                                selectedProduct = null
                            }
                        },
                        label = { Text(stringResource(R.string.item_name)) },
                        placeholder = { Text("e.g. Basmati Rice, Atta, Mixed Goods") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            if (quickItemName.isNotBlank()) {
                                IconButton(onClick = {
                                    quickItemName = ""
                                    selectedProduct = null
                                }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    )

                    // Pre-added / Saved Products Selection Chips
                    if (activeProducts.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.select_saved_product),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val matchingProducts = if (quickItemName.isNotBlank() && selectedProduct == null) {
                                activeProducts.filter { it.name.contains(quickItemName, ignoreCase = true) }.take(6)
                            } else {
                                activeProducts.take(6)
                            }
                            matchingProducts.forEach { product ->
                                val isChosen = selectedProduct?.id == product.id
                                FilterChip(
                                    selected = isChosen,
                                    onClick = {
                                        if (isChosen) {
                                            selectedProduct = null
                                            quickItemName = ""
                                        } else {
                                            selectedProduct = product
                                            quickItemName = product.name
                                            if (quickTotalInput.isBlank() && product.sellingPricePaise > 0L) {
                                                quickTotalInput = CurrencyFormatter.formatPaise(product.sellingPricePaise).replace("₹", "").trim()
                                            }
                                        }
                                    },
                                    label = {
                                        val priceTag = if (product.sellingPricePaise > 0L) " (${CurrencyFormatter.formatPaise(product.sellingPricePaise)})" else ""
                                        Text("${product.name}$priceTag", fontSize = 11.sp)
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = stringResource(R.string.total_bill),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = quickTotalInput,
                        onValueChange = { quickTotalInput = it },
                        label = { Text(stringResource(R.string.amount_in_rupees)) },
                        placeholder = { Text("0.00") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        } else {
            // Itemized Mode: Item Input Card
            item {
                KiranaCard {
                    Text(
                        text = stringResource(R.string.add_item),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = newItemName,
                        onValueChange = { newItemName = it },
                        label = { Text(stringResource(R.string.item_name)) },
                        placeholder = { Text("e.g. Atta, Sugar, Dal") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = newItemQty,
                            onValueChange = { newItemQty = it },
                            label = { Text(stringResource(R.string.quantity)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = newItemPrice,
                            onValueChange = { newItemPrice = it },
                            label = { Text(stringResource(R.string.unit_price)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1.5f)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = {
                            val qty = newItemQty.toDoubleOrNull() ?: 1.0
                            val pricePaise = try {
                                CurrencyFormatter.parseInputToPaise(newItemPrice)
                            } catch (_: Exception) { 0L }

                            if (newItemName.isNotBlank() && qty > 0 && pricePaise > 0) {
                                val lineTotal = (qty * pricePaise).toLong()
                                draftItems.add(
                                    DraftItem(
                                        description = newItemName.trim(),
                                        quantity = qty,
                                        unitPricePaise = pricePaise,
                                        lineTotalPaise = lineTotal
                                    )
                                )
                                newItemName = ""
                                newItemQty = "1"
                                newItemPrice = ""
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.add_item),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // List of added items
            if (draftItems.isNotEmpty()) {
                item {
                    Text("Items (${draftItems.size})", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                }

                items(draftItems, key = { it.id }) { item ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(item.description, fontWeight = FontWeight.SemiBold)
                                Text("${item.quantity} x ${CurrencyFormatter.formatPaise(item.unitPricePaise)}", style = MaterialTheme.typography.bodySmall)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(CurrencyFormatter.formatPaise(item.lineTotalPaise), fontWeight = FontWeight.Bold)
                                IconButton(onClick = { draftItems.remove(item) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Gray)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Discount & Payment Section
        item {
            KiranaCard {
                Text(
                    text = stringResource(R.string.bill_summary),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = discountInput,
                        onValueChange = { discountInput = it },
                        label = { Text(stringResource(R.string.discount)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = amountPaidInput,
                        onValueChange = { amountPaidInput = it },
                        label = { Text(stringResource(R.string.amount_received_label)) },
                        placeholder = { Text("0.00") },
                        isError = !isAmountValid,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1.5f)
                    )
                }

                if (!isAmountValid && amountPaidInput.isNotBlank()) {
                    Text(
                        text = if (actualAmountPaidPaise > totalBillPaise) {
                            stringResource(R.string.error_payment_exceeds_bill)
                        } else {
                            stringResource(R.string.error_invalid_payment_amount)
                        },
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                // Quick Payment Helper Chips
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AssistChip(
                        onClick = {
                            amountPaidInput = CurrencyFormatter.formatPaise(totalBillPaise).replace("₹", "").trim()
                        },
                        label = { Text(stringResource(R.string.full_paid, CurrencyFormatter.formatPaise(totalBillPaise)), fontSize = 12.sp) }
                    )
                    if (selectedCustomer != null) {
                        AssistChip(
                            onClick = { amountPaidInput = "0" },
                            label = { Text(stringResource(R.string.full_udhaar), fontSize = 12.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Payment Method Selection
                Text(stringResource(R.string.payment_method), style = MaterialTheme.typography.labelSmall)
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        TransactionConstants.METHOD_CASH to stringResource(R.string.method_cash),
                        TransactionConstants.METHOD_UPI to stringResource(R.string.method_upi),
                        "SPLIT" to stringResource(R.string.method_split),
                        TransactionConstants.METHOD_OTHER to stringResource(R.string.method_other)
                    ).forEach { (method, label) ->
                        FilterChip(
                            selected = paymentMethod == method,
                            onClick = { paymentMethod = method },
                            label = { Text(label, fontSize = 12.sp) }
                        )
                    }
                }

                // Split payment inputs
                if (isSplitMode) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Split Breakdown (Must sum to ${CurrencyFormatter.formatPaise(actualAmountPaidPaise)}):", style = MaterialTheme.typography.bodySmall)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = splitCashInput,
                            onValueChange = { splitCashInput = it },
                            label = { Text("Cash (₹)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = splitUpiInput,
                            onValueChange = { splitUpiInput = it },
                            label = { Text("UPI (₹)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (!isSplitAllocatedCorrectly) {
                        Text(
                            text = "Cash + UPI must equal received amount (${CurrencyFormatter.formatPaise(actualAmountPaidPaise)})",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                // Unpaid balance / Udhaar warning
                if (balanceUdhaarPaise > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = Color(0xFFFFEBEE),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.unpaid_balance),
                                    color = KiranaDebitRed,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = CurrencyFormatter.formatPaise(balanceUdhaarPaise),
                                    color = KiranaDebitRed,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            if (isCreditSaleMissingCustomer) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = stringResource(R.string.error_credit_sale_requires_customer),
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Bill Summary Box
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(stringResource(R.string.total_due), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                    Text(
                        CurrencyFormatter.formatPaise(totalBillPaise),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = KiranaGreenPrimary)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        if (!canSubmit) return@Button
                        isSubmitting = true

                        val billItems = if (isItemizedMode) {
                            draftItems.map {
                                BillItemEntity(
                                    billId = 0L,
                                    description = it.description,
                                    quantity = it.quantity,
                                    unitPricePaise = it.unitPricePaise,
                                    lineTotalPaise = it.lineTotalPaise,
                                    productId = it.productId
                                )
                            }
                        } else if (quickItemName.isNotBlank()) {
                            listOf(
                                BillItemEntity(
                                    billId = 0L,
                                    description = quickItemName.trim(),
                                    quantity = 1.0,
                                    unitPricePaise = subtotalPaise,
                                    lineTotalPaise = subtotalPaise,
                                    productId = selectedProduct?.id
                                )
                            )
                        } else {
                            emptyList()
                        }

                        val (cashPaise, upiPaise, otherPaise) = when (paymentMethod) {
                            TransactionConstants.METHOD_CASH -> Triple(actualAmountPaidPaise, 0L, 0L)
                            TransactionConstants.METHOD_UPI -> Triple(0L, actualAmountPaidPaise, 0L)
                            "SPLIT" -> Triple(splitCashPaise, splitUpiPaise, 0L)
                            else -> Triple(0L, 0L, actualAmountPaidPaise)
                        }

                        viewModel.createBill(
                            customerId = selectedCustomer?.id,
                            subtotalPaise = subtotalPaise,
                            discountPaise = discountPaise,
                            totalPaise = totalBillPaise,
                            amountPaidPaise = actualAmountPaidPaise,
                            paymentMethod = paymentMethod,
                            billType = if (isItemizedMode) BillConstants.TYPE_ITEMIZED else BillConstants.TYPE_QUICK,
                            notes = billNotes,
                            items = billItems,
                            cashPaidPaise = cashPaise,
                            upiPaidPaise = upiPaise,
                            otherPaidPaise = otherPaise,
                            idempotencyKey = formIdempotencyKey,
                            onSuccess = { fullBill ->
                                isSubmitting = false
                                createdBillForPreview = fullBill
                                // Reset form & generate fresh idempotency key
                                quickTotalInput = ""
                                quickItemName = ""
                                selectedProduct = null
                                discountInput = ""
                                amountPaidInput = ""
                                splitCashInput = ""
                                splitUpiInput = ""
                                billNotes = ""
                                draftItems.clear()
                                formIdempotencyKey = UUID.randomUUID().toString()
                            },
                            onError = { err ->
                                isSubmitting = false
                                Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                            }
                        )
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(10.dp),
                    enabled = canSubmit
                ) {
                    Icon(Icons.Default.Receipt, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.generate_bill),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }

    // Receipt Generated Dialog with WhatsApp / PDF Share
    createdBillForPreview?.let { billWithItems ->
        AlertDialog(
            onDismissRequest = { createdBillForPreview = null },
            title = {
                Text("${stringResource(R.string.receipt)}: ${billWithItems.bill.receiptNumber}", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Total Amount: ${CurrencyFormatter.formatPaise(billWithItems.bill.totalPaise)}")
                    Text("Amount Paid: ${CurrencyFormatter.formatPaise(billWithItems.bill.amountPaidPaise)} (${billWithItems.bill.paymentMethod})")
                    val unpaid = billWithItems.bill.totalPaise - billWithItems.bill.amountPaidPaise
                    if (unpaid > 0) {
                        Text("Udhaar Recorded: ${CurrencyFormatter.formatPaise(unpaid)}", color = KiranaDebitRed, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Choose how to share this receipt with your customer:", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            val pdf = viewModel.generateBillPdf(context, billWithItems)
                            ShareHelper.shareViaWhatsApp(context, pdf)
                            createdBillForPreview = null
                        }
                    },
                    modifier = Modifier.defaultMinSize(minHeight = 44.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.share_via_whatsapp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            val pdf = viewModel.generateBillPdf(context, billWithItems)
                            ShareHelper.sharePdf(context, pdf)
                            createdBillForPreview = null
                        }
                    },
                    modifier = Modifier.defaultMinSize(minHeight = 44.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.share_pdf),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        )
    }

    // Create New Product Dialog
    if (showCreateProductDialog) {
        var dialogName by remember { mutableStateOf(newProductDialogName) }
        var dialogPrice by remember { mutableStateOf(newProductDialogPrice) }
        var dialogUnit by remember { mutableStateOf("pcs") }

        AlertDialog(
            onDismissRequest = { showCreateProductDialog = false },
            title = { Text(stringResource(R.string.create_item_title), fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = dialogName,
                        onValueChange = { dialogName = it },
                        label = { Text(stringResource(R.string.item_name)) },
                        placeholder = { Text("e.g. Basmati Rice 5kg") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = dialogPrice,
                        onValueChange = { dialogPrice = it },
                        label = { Text(stringResource(R.string.unit_price)) },
                        placeholder = { Text("0.00 (Optional)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("pcs", "kg", "ltr", "pkt").forEach { u ->
                            FilterChip(
                                selected = dialogUnit == u,
                                onClick = { dialogUnit = u },
                                label = { Text(u, fontSize = 11.sp) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (dialogName.isNotBlank()) {
                            val pricePaise = try { CurrencyFormatter.parseInputToPaise(dialogPrice) } catch (_: Exception) { 0L }
                            viewModel.createQuickProduct(
                                name = dialogName.trim(),
                                sellingPricePaise = pricePaise,
                                unit = dialogUnit
                            ) { newProd ->
                                selectedProduct = newProd
                                quickItemName = newProd.name
                                if (pricePaise > 0L) {
                                    quickTotalInput = CurrencyFormatter.formatPaise(pricePaise).replace("₹", "").trim()
                                }
                                Toast.makeText(context, context.getString(R.string.product_saved_success), Toast.LENGTH_SHORT).show()
                            }
                            showCreateProductDialog = false
                        }
                    },
                    modifier = Modifier.defaultMinSize(minHeight = 44.dp),
                    shape = RoundedCornerShape(8.dp),
                    enabled = dialogName.isNotBlank()
                ) {
                    Text(
                        text = stringResource(R.string.save_item),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showCreateProductDialog = false },
                    modifier = Modifier.defaultMinSize(minHeight = 44.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.cancel),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerDropdown(
    customers: List<CustomerWithBalance>,
    selectedCustomer: CustomerWithBalance?,
    onSelect: (CustomerWithBalance) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selectedCustomer?.name ?: "Tap to choose registered customer...",
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            customers.forEach { customer ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(customer.name, fontWeight = FontWeight.SemiBold)
                            if (customer.phone.isNotBlank()) {
                                Text(customer.phone, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            }
                        }
                    },
                    onClick = {
                        onSelect(customer)
                        expanded = false
                    }
                )
            }
        }
    }
}
