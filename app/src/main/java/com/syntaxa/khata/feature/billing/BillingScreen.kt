package com.syntaxa.khata.feature.billing

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
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
    preselectedCustomerId: Long? = null,
    onOpenAddCustomerDialog: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val customers by viewModel.activeCustomers.collectAsState()
    val activeProducts by viewModel.activeProducts.collectAsState()

    var isItemizedMode by remember { mutableStateOf(false) }
    var selectedCustomer by remember(preselectedCustomerId, customers) {
        mutableStateOf(customers.firstOrNull { it.id == preselectedCustomerId })
    }
    var quickItemName by remember { mutableStateOf("") }
    var quickItemQty by remember { mutableStateOf("1") }
    var quickItemUnit by remember { mutableStateOf("pcs") }
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
    var includeOldUdhaar by remember { mutableStateOf(false) }

    // Itemized items
    val draftItems = remember { mutableStateListOf<DraftItem>() }
    var newItemName by remember { mutableStateOf("") }
    var newItemQty by remember { mutableStateOf("1") }
    var newItemUnit by remember { mutableStateOf("pcs") }
    var newItemPrice by remember { mutableStateOf("") }
    var selectedItemizedProduct by remember { mutableStateOf<ProductEntity?>(null) }

    var createdBillForPreview by remember { mutableStateOf<BillWithItems?>(null) }
    var lastBilledCustomer by remember { mutableStateOf<CustomerWithBalance?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }

    // Repeat Udhaar customer calculations
    val oldUdhaarBalancePaise: Long = selectedCustomer?.balancePaise ?: 0L
    val hasOldUdhaar: Boolean = oldUdhaarBalancePaise > 0L
    val effectiveOldUdhaarPaise: Long = if (includeOldUdhaar && hasOldUdhaar) oldUdhaarBalancePaise else 0L

    // Calculate subtotal
    val itemsSubtotalPaise: Long = if (isItemizedMode) {
        draftItems.sumOf { it.lineTotalPaise }
    } else {
        try { CurrencyFormatter.parseInputToPaise(quickTotalInput) } catch (_: Exception) { 0L }
    }

    val subtotalPaise: Long = itemsSubtotalPaise + effectiveOldUdhaarPaise

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
                        label = { Text(stringResource(R.string.walk_in_customer), fontSize = 12.sp, maxLines = 1) },
                        modifier = Modifier.weight(1f).height(38.dp)
                    )
                    OutlinedButton(
                        onClick = onOpenAddCustomerDialog,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f).height(38.dp)
                    ) {
                        Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.action_add_customer),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (customers.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    CustomerDropdown(
                        customers = customers,
                        selectedCustomer = selectedCustomer,
                        onSelect = { selectedCustomer = it }
                    )
                }

                if (hasOldUdhaar) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = KiranaDebitRed,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.old_udhaar_detected_banner, CurrencyFormatter.formatPaise(oldUdhaarBalancePaise)),
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = KiranaDebitRed
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                SegmentedButton(
                                    selected = !includeOldUdhaar,
                                    onClick = { includeOldUdhaar = false },
                                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                                ) {
                                    Text(
                                        text = stringResource(R.string.fresh_bill_only_option),
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                SegmentedButton(
                                    selected = includeOldUdhaar,
                                    onClick = { includeOldUdhaar = true },
                                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                                ) {
                                    Text(
                                        text = stringResource(R.string.add_old_udhaar_option, CurrencyFormatter.formatPaise(oldUdhaarBalancePaise)),
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
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
                        OutlinedButton(
                            onClick = {
                                newProductDialogName = quickItemName
                                newProductDialogPrice = quickTotalInput
                                showCreateProductDialog = true
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.add_new_product_btn), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    // Line 1: Item Name (Full Width)
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
                        shape = RoundedCornerShape(10.dp),
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

                    // Line 2: Pre-added / Saved Products Selection Chips
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
                                            quickItemUnit = product.unit.ifBlank { "pcs" }
                                            if (quickTotalInput.isBlank() && product.sellingPricePaise > 0L) {
                                                quickTotalInput = CurrencyFormatter.formatPaise(product.sellingPricePaise).replace("₹", "").trim()
                                            }
                                        }
                                    },
                                    label = {
                                        val priceTag = if (product.sellingPricePaise > 0L) " (${CurrencyFormatter.formatPaise(product.sellingPricePaise)})" else ""
                                        Text("${product.name}$priceTag", fontSize = 11.sp)
                                    },
                                    modifier = Modifier.defaultMinSize(minHeight = 32.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Line 3: Balanced 50/50 Row for Quantity and Total Bill Amount
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = quickItemQty,
                            onValueChange = { quickItemQty = it },
                            label = { Text(stringResource(R.string.quantity)) },
                            placeholder = { Text("1") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            trailingIcon = {
                                Text(
                                    text = quickItemUnit,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = KiranaGreenPrimary,
                                    modifier = Modifier.padding(end = 10.dp)
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = quickTotalInput,
                            onValueChange = { quickTotalInput = it },
                            label = { Text(stringResource(R.string.amount_in_rupees)) },
                            placeholder = { Text("0.00") },
                            prefix = { Text("₹ ", fontWeight = FontWeight.Bold) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Line 4: Compact Unit Selector Chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.unit) + ":",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        listOf("pcs", "kg", "gm", "ltr", "ml", "pack").forEach { u ->
                            FilterChip(
                                selected = quickItemUnit == u,
                                onClick = { quickItemUnit = u },
                                label = { Text(u, fontSize = 12.sp, fontWeight = if (quickItemUnit == u) FontWeight.Bold else FontWeight.Normal) },
                                modifier = Modifier.height(34.dp)
                            )
                        }
                    }
                }
            }
        } else {
            // Itemized Mode: Item Input Card
            item {
                KiranaCard {
                    Text(
                        text = stringResource(R.string.add_item),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    // Line 1: Item Name (Full Width)
                    OutlinedTextField(
                        value = newItemName,
                        onValueChange = {
                            newItemName = it
                            if (selectedItemizedProduct != null && selectedItemizedProduct?.name != it) {
                                selectedItemizedProduct = null
                            }
                        },
                        label = { Text(stringResource(R.string.item_name)) },
                        placeholder = { Text("e.g. Atta, Sugar, Dal") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            if (newItemName.isNotBlank()) {
                                IconButton(onClick = {
                                    newItemName = ""
                                    selectedItemizedProduct = null
                                }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    )

                    // Line 1b: Saved Products Selection Chips for Itemized Mode
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
                            val matchingProducts = if (newItemName.isNotBlank() && selectedItemizedProduct == null) {
                                activeProducts.filter { it.name.contains(newItemName, ignoreCase = true) }.take(6)
                            } else {
                                activeProducts.take(6)
                            }
                            matchingProducts.forEach { product ->
                                val isChosen = selectedItemizedProduct?.id == product.id
                                FilterChip(
                                    selected = isChosen,
                                    onClick = {
                                        if (isChosen) {
                                            selectedItemizedProduct = null
                                            newItemName = ""
                                        } else {
                                            selectedItemizedProduct = product
                                            newItemName = product.name
                                            newItemUnit = product.unit.ifBlank { "pcs" }
                                            if (product.sellingPricePaise > 0L) {
                                                newItemPrice = CurrencyFormatter.formatPaise(product.sellingPricePaise).replace("₹", "").trim()
                                            }
                                        }
                                    },
                                    label = {
                                        val priceTag = if (product.sellingPricePaise > 0L) " (${CurrencyFormatter.formatPaise(product.sellingPricePaise)})" else ""
                                        Text("${product.name}$priceTag", fontSize = 11.sp)
                                    },
                                    modifier = Modifier.defaultMinSize(minHeight = 32.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Line 2: Balanced 50/50 Row for Quantity and Unit Price
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = newItemQty,
                            onValueChange = { newItemQty = it },
                            label = { Text(stringResource(R.string.quantity)) },
                            placeholder = { Text("1") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            trailingIcon = {
                                Text(
                                    text = newItemUnit,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = KiranaGreenPrimary,
                                    modifier = Modifier.padding(end = 10.dp)
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = newItemPrice,
                            onValueChange = { newItemPrice = it },
                            label = { Text(stringResource(R.string.unit_price)) },
                            placeholder = { Text("0.00") },
                            prefix = { Text("₹ ", fontWeight = FontWeight.Bold) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Line 3: Unit Chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.unit) + ":",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        listOf("pcs", "kg", "gm", "ltr", "ml", "pack").forEach { u ->
                            FilterChip(
                                selected = newItemUnit == u,
                                onClick = { newItemUnit = u },
                                label = { Text(u, fontSize = 12.sp, fontWeight = if (newItemUnit == u) FontWeight.Bold else FontWeight.Normal) },
                                modifier = Modifier.height(34.dp)
                            )
                        }
                    }

                    // Line 4: Live Line Total Preview
                    val liveQty = newItemQty.toDoubleOrNull() ?: 1.0
                    val livePrice = try { CurrencyFormatter.parseInputToPaise(newItemPrice) } catch (_: Exception) { 0L }
                    if (livePrice > 0L && newItemName.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.line_total_preview, liveQty, newItemUnit, CurrencyFormatter.formatPaise(livePrice), CurrencyFormatter.formatPaise((liveQty * livePrice).toLong())),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = KiranaCreditGreen
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Line 5: Add Item Button
                    Button(
                        onClick = {
                            val qty = newItemQty.toDoubleOrNull() ?: 1.0
                            val pricePaise = try { CurrencyFormatter.parseInputToPaise(newItemPrice) } catch (_: Exception) { 0L }

                            if (newItemName.isNotBlank() && qty > 0 && pricePaise > 0) {
                                val lineTotal = (qty * pricePaise).toLong()
                                val descWithUnit = if (newItemUnit.isNotBlank() && newItemUnit != "pcs") {
                                    "${newItemName.trim()} ($newItemUnit)"
                                } else {
                                    newItemName.trim()
                                }
                                draftItems.add(
                                    DraftItem(
                                        description = descWithUnit,
                                        quantity = qty,
                                        unitPricePaise = pricePaise,
                                        lineTotalPaise = lineTotal,
                                        productId = selectedItemizedProduct?.id
                                    )
                                )
                                newItemName = ""
                                newItemQty = "1"
                                newItemPrice = ""
                                newItemUnit = "pcs"
                                selectedItemizedProduct = null
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.add_item),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // List of added items
            if (draftItems.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.items_count, draftItems.size),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }

                items(draftItems, key = { it.id }, contentType = { "draft_item" }) { item ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.description, fontWeight = FontWeight.SemiBold)
                                Text("${item.quantity} × ${CurrencyFormatter.formatPaise(item.unitPricePaise)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(CurrencyFormatter.formatPaise(item.lineTotalPaise), fontWeight = FontWeight.Bold, color = KiranaGreenPrimary)
                                IconButton(onClick = { draftItems.remove(item) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Gray, modifier = Modifier.size(20.dp))
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
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Line 1: Balanced 50/50 Row for Discount and Amount Received
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = discountInput,
                        onValueChange = { discountInput = it },
                        label = { Text(stringResource(R.string.discount)) },
                        placeholder = { Text("0") },
                        prefix = { Text("₹ ", fontWeight = FontWeight.Bold) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = amountPaidInput,
                        onValueChange = { amountPaidInput = it },
                        label = { Text(stringResource(R.string.amount_received_label)) },
                        placeholder = { Text("0.00") },
                        prefix = { Text("₹ ", fontWeight = FontWeight.Bold) },
                        isError = !isAmountValid,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
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

                // Line 2: Quick Payment Helper Chips
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AssistChip(
                        onClick = {
                            amountPaidInput = CurrencyFormatter.formatPaise(totalBillPaise).replace("₹", "").trim()
                        },
                        leadingIcon = {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp), tint = KiranaGreenPrimary)
                        },
                        label = { Text(stringResource(R.string.full_paid, CurrencyFormatter.formatPaise(totalBillPaise)), fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                        modifier = Modifier.height(36.dp)
                    )
                    if (selectedCustomer != null) {
                        AssistChip(
                            onClick = { amountPaidInput = "0" },
                            leadingIcon = {
                                Icon(Icons.Default.Pending, contentDescription = null, modifier = Modifier.size(16.dp), tint = KiranaDebitRed)
                            },
                            label = { Text(stringResource(R.string.full_udhaar), fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                            modifier = Modifier.height(36.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Line 3: Payment Method Selection (Organized 2x2 Grid)
                Text(
                    text = stringResource(R.string.payment_method),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = paymentMethod == TransactionConstants.METHOD_CASH,
                        onClick = { paymentMethod = TransactionConstants.METHOD_CASH },
                        leadingIcon = {
                            Icon(Icons.Default.Payments, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        label = { Text(stringResource(R.string.method_cash), fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                        modifier = Modifier.weight(1f).height(40.dp)
                    )
                    FilterChip(
                        selected = paymentMethod == TransactionConstants.METHOD_UPI,
                        onClick = { paymentMethod = TransactionConstants.METHOD_UPI },
                        leadingIcon = {
                            Icon(Icons.Default.QrCode, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        label = { Text(stringResource(R.string.method_upi), fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                        modifier = Modifier.weight(1f).height(40.dp)
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = paymentMethod == "SPLIT",
                        onClick = { paymentMethod = "SPLIT" },
                        leadingIcon = {
                            Icon(Icons.Default.CallSplit, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        label = { Text(stringResource(R.string.method_split), fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                        modifier = Modifier.weight(1f).height(40.dp)
                    )
                    FilterChip(
                        selected = paymentMethod == TransactionConstants.METHOD_OTHER,
                        onClick = { paymentMethod = TransactionConstants.METHOD_OTHER },
                        leadingIcon = {
                            Icon(Icons.Default.MoreHoriz, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        label = { Text(stringResource(R.string.method_other), fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                        modifier = Modifier.weight(1f).height(40.dp)
                    )
                }

                // Line 4: Split payment inputs (if SPLIT)
                if (isSplitMode) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = stringResource(R.string.split_breakdown, CurrencyFormatter.formatPaise(actualAmountPaidPaise)),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = splitCashInput,
                            onValueChange = { splitCashInput = it },
                            label = { Text(stringResource(R.string.split_cash_label)) },
                            prefix = { Text("₹ ", fontWeight = FontWeight.Bold) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = splitUpiInput,
                            onValueChange = { splitUpiInput = it },
                            label = { Text(stringResource(R.string.split_upi_label)) },
                            prefix = { Text("₹ ", fontWeight = FontWeight.Bold) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (!isSplitAllocatedCorrectly) {
                        Text(
                            text = stringResource(R.string.error_split_mismatch, CurrencyFormatter.formatPaise(actualAmountPaidPaise)),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }

                // Line 5: Unpaid balance / Udhaar warning
                if (balanceUdhaarPaise > 0) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        color = KiranaDebitRed.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, KiranaDebitRed.copy(alpha = 0.3f)),
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
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = CurrencyFormatter.formatPaise(balanceUdhaarPaise),
                                    color = KiranaDebitRed,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
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

                Spacer(modifier = Modifier.height(10.dp))

                // Line 6: Notes Field
                OutlinedTextField(
                    value = billNotes,
                    onValueChange = { billNotes = it },
                    label = { Text(stringResource(R.string.notes)) },
                    placeholder = { Text("e.g. Paid via PhonePe, delivered by Raju") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Line 7: Bill Summary Box
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        if (effectiveOldUdhaarPaise > 0L) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(stringResource(R.string.today_items_total), style = MaterialTheme.typography.bodyMedium)
                                Text(CurrencyFormatter.formatPaise(itemsSubtotalPaise), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(stringResource(R.string.previous_udhaar_balance), style = MaterialTheme.typography.bodyMedium, color = KiranaDebitRed)
                                Text("+${CurrencyFormatter.formatPaise(effectiveOldUdhaarPaise)}", style = MaterialTheme.typography.bodyMedium, color = KiranaDebitRed, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                        if (discountPaise > 0L) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(stringResource(R.string.discount), style = MaterialTheme.typography.bodyMedium, color = KiranaCreditGreen)
                                Text("-${CurrencyFormatter.formatPaise(discountPaise)}", style = MaterialTheme.typography.bodyMedium, color = KiranaCreditGreen, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(stringResource(R.string.total_due), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                            Text(
                                CurrencyFormatter.formatPaise(totalBillPaise),
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = KiranaGreenPrimary)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Line 8: Primary Action Button
                Button(
                    onClick = {
                        if (!canSubmit) return@Button
                        isSubmitting = true
                        lastBilledCustomer = selectedCustomer

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
                            }.toMutableList()
                        } else if (quickItemName.isNotBlank()) {
                            val qQty = quickItemQty.toDoubleOrNull() ?: 1.0
                            val formattedDesc = if (quickItemUnit.isNotBlank() && quickItemUnit != "pcs") {
                                "${quickItemName.trim()} ($quickItemQty $quickItemUnit)"
                            } else if (quickItemQty.isNotBlank() && quickItemQty != "1") {
                                "${quickItemName.trim()} ($quickItemQty pcs)"
                            } else {
                                quickItemName.trim()
                            }
                            val unitPrice = if (qQty > 0) (itemsSubtotalPaise / qQty).toLong() else itemsSubtotalPaise
                            mutableListOf(
                                BillItemEntity(
                                    billId = 0L,
                                    description = formattedDesc,
                                    quantity = qQty,
                                    unitPricePaise = unitPrice,
                                    lineTotalPaise = itemsSubtotalPaise,
                                    productId = selectedProduct?.id
                                )
                            )
                        } else {
                            mutableListOf()
                        }

                        if (includeOldUdhaar && hasOldUdhaar) {
                            billItems.add(
                                BillItemEntity(
                                    billId = 0L,
                                    description = BillConstants.ITEM_DESC_OLD_UDHAAR,
                                    quantity = 1.0,
                                    unitPricePaise = oldUdhaarBalancePaise,
                                    lineTotalPaise = oldUdhaarBalancePaise,
                                    productId = null
                                )
                            )
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
                            oldUdhaarIncludedPaise = if (includeOldUdhaar && hasOldUdhaar) oldUdhaarBalancePaise else 0L,
                            onSuccess = { fullBill ->
                                isSubmitting = false
                                createdBillForPreview = fullBill
                                // Reset form & generate fresh idempotency key
                                quickTotalInput = ""
                                quickItemName = ""
                                quickItemQty = "1"
                                quickItemUnit = "pcs"
                                newItemUnit = "pcs"
                                includeOldUdhaar = false
                                selectedProduct = null
                                discountInput = ""
                                amountPaidInput = ""
                                paymentMethod = TransactionConstants.METHOD_CASH
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
                    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 52.dp),
                    shape = RoundedCornerShape(12.dp),
                    enabled = canSubmit
                ) {
                    Icon(Icons.Default.Receipt, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.generate_bill),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }

    // Receipt Generated Dialog with WhatsApp / PDF Share
    createdBillForPreview?.let { billWithItemsRaw ->
        val effectiveCustomer = billWithItemsRaw.customer ?: lastBilledCustomer?.let {
            com.syntaxa.khata.data.local.entity.CustomerEntity(
                id = it.id,
                name = it.name,
                phone = it.phone,
                notes = it.notes,
                createdAt = it.createdAt
            )
        }
        val billWithItems = if (billWithItemsRaw.customer == null && effectiveCustomer != null) {
            billWithItemsRaw.copy(customer = effectiveCustomer)
        } else {
            billWithItemsRaw
        }

        val customerPhone = effectiveCustomer?.phone?.takeIf { it.isNotBlank() }
            ?: lastBilledCustomer?.phone?.takeIf { it.isNotBlank() }
            ?: selectedCustomer?.phone?.takeIf { it.isNotBlank() }
            ?: ""
        val customerDisplayName = effectiveCustomer?.name?.takeIf { it.isNotBlank() }
            ?: lastBilledCustomer?.name?.takeIf { it.isNotBlank() }
            ?: selectedCustomer?.name?.takeIf { it.isNotBlank() }
            ?: ""

        AlertDialog(
            onDismissRequest = { createdBillForPreview = null },
            title = {
                Text(
                    text = "${stringResource(R.string.receipt)}: ${billWithItems.bill.receiptNumber}",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(R.string.total_amount_label, CurrencyFormatter.formatPaise(billWithItems.bill.totalPaise)),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Text(
                        text = stringResource(R.string.amount_paid_label, CurrencyFormatter.formatPaise(billWithItems.bill.amountPaidPaise), billWithItems.bill.paymentMethod),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    val unpaid = maxOf(0L, billWithItems.bill.totalPaise - billWithItems.bill.amountPaidPaise)
                    if (unpaid > 0) {
                        Text(
                            text = stringResource(R.string.udhaar_recorded_label, CurrencyFormatter.formatPaise(unpaid)),
                            color = KiranaDebitRed,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                    if (customerPhone.isNotBlank()) {
                        Text(
                            text = "${stringResource(R.string.customer_colon, customerDisplayName)} ($customerPhone)",
                            style = MaterialTheme.typography.bodySmall,
                            color = KiranaGreenPrimary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.choose_share_receipt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // 1. WhatsApp Direct Text Message
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                try {
                                    val shop = viewModel.getShopProfile()
                                    val itemsList = billWithItems.items.map { it.description to it.lineTotalPaise }
                                    val billText = ShareHelper.buildBillWhatsAppText(
                                        shopName = shop.shopName,
                                        receiptNumber = billWithItems.bill.receiptNumber,
                                        customerName = customerDisplayName,
                                        items = itemsList,
                                        subtotalPaise = billWithItems.bill.subtotalPaise,
                                        discountPaise = billWithItems.bill.discountPaise,
                                        totalPaise = billWithItems.bill.totalPaise,
                                        amountPaidPaise = billWithItems.bill.amountPaidPaise,
                                        paymentMethod = billWithItems.bill.paymentMethod,
                                        notes = billWithItems.bill.notes
                                    )
                                    ShareHelper.shareTextViaWhatsApp(context, billText, phone = customerPhone)
                                    createdBillForPreview = null
                                } catch (e: Exception) {
                                    Toast.makeText(context, context.getString(R.string.error_with_message, e.localizedMessage ?: ""), Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Chat,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.whatsapp_direct),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }

                    // 2. Send PDF on WhatsApp
                    FilledTonalButton(
                        onClick = {
                            coroutineScope.launch {
                                try {
                                    val pdf = viewModel.generateBillPdf(context, billWithItems)
                                    val shop = viewModel.getShopProfile()
                                    val itemsList = billWithItems.items.map { it.description to it.lineTotalPaise }
                                    val billText = ShareHelper.buildBillWhatsAppText(
                                        shopName = shop.shopName,
                                        receiptNumber = billWithItems.bill.receiptNumber,
                                        customerName = customerDisplayName,
                                        items = itemsList,
                                        subtotalPaise = billWithItems.bill.subtotalPaise,
                                        discountPaise = billWithItems.bill.discountPaise,
                                        totalPaise = billWithItems.bill.totalPaise,
                                        amountPaidPaise = billWithItems.bill.amountPaidPaise,
                                        paymentMethod = billWithItems.bill.paymentMethod,
                                        notes = billWithItems.bill.notes
                                    )
                                    ShareHelper.shareViaWhatsApp(context, pdf, phone = customerPhone, caption = billText)
                                    createdBillForPreview = null
                                } catch (e: Exception) {
                                    Toast.makeText(context, context.getString(R.string.error_with_message, e.localizedMessage ?: ""), Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            Icons.Default.PictureAsPdf,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.share_pdf_whatsapp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }

                    // 3. Share via Other Apps
                    OutlinedButton(
                        onClick = {
                            coroutineScope.launch {
                                try {
                                    val pdf = viewModel.generateBillPdf(context, billWithItems)
                                    ShareHelper.sharePdf(context, pdf)
                                    createdBillForPreview = null
                                } catch (e: Exception) {
                                    Toast.makeText(context, context.getString(R.string.error_with_message, e.localizedMessage ?: ""), Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.share_more),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }

                    // 4. Close Dialog
                    TextButton(
                        onClick = { createdBillForPreview = null },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.close),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            },
            dismissButton = null
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
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = dialogPrice,
                        onValueChange = { dialogPrice = it },
                        label = { Text(stringResource(R.string.unit_price)) },
                        placeholder = { Text("0.00 (Optional)") },
                        prefix = { Text("₹ ", fontWeight = FontWeight.Bold) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
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
        val dueText = selectedCustomer?.let {
            if (it.balancePaise > 0) "${it.name} (${stringResource(R.string.due_colon, CurrencyFormatter.formatPaise(it.balancePaise))})"
            else it.name
        } ?: stringResource(R.string.dropdown_select_customer_hint)

        OutlinedTextField(
            value = dueText,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
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
