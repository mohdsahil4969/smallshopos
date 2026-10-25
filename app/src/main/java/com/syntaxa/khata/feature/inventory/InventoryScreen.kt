package com.syntaxa.khata.feature.inventory

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.syntaxa.khata.R
import com.syntaxa.khata.core.model.CurrencyFormatter
import com.syntaxa.khata.core.ui.components.CurrencyInputField
import com.syntaxa.khata.core.ui.components.KiranaCard
import com.syntaxa.khata.core.ui.theme.KiranaCreditGreen
import com.syntaxa.khata.core.ui.theme.KiranaDebitRed
import com.syntaxa.khata.data.local.entity.ProductEntity

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun InventoryScreen(
    viewModel: InventoryViewModel,
    modifier: Modifier = Modifier
) {
    val products by viewModel.products.collectAsState()
    val searchQuery by viewModel.query.collectAsState()
    val onlyLowStock by viewModel.onlyLowStock.collectAsState()

    var showAddEditDialog by remember { mutableStateOf(false) }
    var selectedProductForEdit by remember { mutableStateOf<ProductEntity?>(null) }
    var showAdjustStockDialog by remember { mutableStateOf(false) }
    var selectedProductForStock by remember { mutableStateOf<ProductEntity?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    selectedProductForEdit = null
                    showAddEditDialog = true
                },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Product", tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setQuery(it) },
                label = { Text(stringResource(R.string.search_products_hint)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { viewModel.setQuery("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Filter Chips
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                FilterChip(
                    selected = !onlyLowStock,
                    onClick = { if (onlyLowStock) viewModel.toggleLowStockFilter() },
                    label = { Text("All Products (${products.size})") }
                )
                FilterChip(
                    selected = onlyLowStock,
                    onClick = { viewModel.toggleLowStockFilter() },
                    label = { Text("⚠️ Low Stock Only") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = KiranaDebitRed.copy(alpha = 0.15f),
                        selectedLabelColor = KiranaDebitRed
                    )
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (products.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (onlyLowStock) "No low stock items!" else "No products found. Tap + to add stock.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(products, key = { it.id }) { product ->
                        ProductItemCard(
                            product = product,
                            onEdit = {
                                selectedProductForEdit = product
                                showAddEditDialog = true
                            },
                            onAdjustStock = {
                                selectedProductForStock = product
                                showAdjustStockDialog = true
                            },
                            onArchive = {
                                viewModel.archiveProduct(product.id)
                            }
                        )
                    }
                }
            }
        }
    }

    if (showAddEditDialog) {
        AddEditProductDialog(
            product = selectedProductForEdit,
            onDismiss = { showAddEditDialog = false },
            onSave = { name, unit, sellingPaise, costPaise, stock, threshold, barcode, category ->
                viewModel.saveProduct(
                    id = selectedProductForEdit?.id ?: 0L,
                    name = name,
                    unit = unit,
                    sellingPricePaise = sellingPaise,
                    costPricePaise = costPaise,
                    stockQuantity = stock,
                    lowStockThreshold = threshold,
                    barcode = barcode,
                    category = category
                )
                showAddEditDialog = false
            }
        )
    }

    if (showAdjustStockDialog && selectedProductForStock != null) {
        AdjustStockDialog(
            product = selectedProductForStock!!,
            onDismiss = { showAdjustStockDialog = false },
            onAdjust = { delta ->
                viewModel.adjustStock(selectedProductForStock!!.id, delta)
                showAdjustStockDialog = false
            }
        )
    }
}

@Composable
private fun ProductItemCard(
    product: ProductEntity,
    onEdit: () -> Unit,
    onAdjustStock: () -> Unit,
    onArchive: () -> Unit
) {
    KiranaCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                    text = product.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Sale: ${CurrencyFormatter.formatPaise(product.sellingPricePaise)} / ${product.unit}",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                    )
                    if (product.costPricePaise > 0) {
                        Text(
                            text = " • Cost: ${CurrencyFormatter.formatPaise(product.costPricePaise)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Stock Badge
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (product.isLowStock) Color(0xFFFFEBEE) else Color(0xFFE8F5E9)
            ) {
                Text(
                    text = "${product.stockQuantity} ${product.unit}",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (product.isLowStock) KiranaDebitRed else KiranaCreditGreen
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        HorizontalDivider()

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = onAdjustStock,
                modifier = Modifier.defaultMinSize(minHeight = 36.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.SyncAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.adjust_stock),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onArchive) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Archive", tint = Color.Gray, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun AddEditProductDialog(
    product: ProductEntity?,
    onDismiss: () -> Unit,
    onSave: (String, String, Long, Long, Double, Double, String, String) -> Unit
) {
    var name by remember { mutableStateOf(product?.name ?: "") }
    var unit by remember { mutableStateOf(product?.unit ?: "pcs") }
    var sellingInput by remember { mutableStateOf(if (product != null) (product.sellingPricePaise / 100.0).toString() else "") }
    var costInput by remember { mutableStateOf(if (product != null && product.costPricePaise > 0) (product.costPricePaise / 100.0).toString() else "") }
    var stockInput by remember { mutableStateOf(if (product != null) product.stockQuantity.toString() else "0") }
    var thresholdInput by remember { mutableStateOf(if (product != null) product.lowStockThreshold.toString() else "5") }
    var barcode by remember { mutableStateOf(product?.barcode ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (product == null) stringResource(R.string.add_product_title) else stringResource(R.string.edit_product_title), fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.product_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = unit,
                        onValueChange = { unit = it },
                        label = { Text(stringResource(R.string.unit_label)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = barcode,
                        onValueChange = { barcode = it },
                        label = { Text(stringResource(R.string.barcode_label)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CurrencyInputField(
                        value = sellingInput,
                        onValueChange = { sellingInput = it },
                        label = stringResource(R.string.selling_price_label),
                        modifier = Modifier.weight(1f)
                    )
                    CurrencyInputField(
                        value = costInput,
                        onValueChange = { costInput = it },
                        label = stringResource(R.string.cost_price_label),
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = stockInput,
                        onValueChange = { stockInput = it },
                        label = { Text(stringResource(R.string.initial_stock_label)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = thresholdInput,
                        onValueChange = { thresholdInput = it },
                        label = { Text(stringResource(R.string.low_stock_threshold_label)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val sPaise = try { CurrencyFormatter.parseInputToPaise(sellingInput) } catch (_: Exception) { 0L }
                    val cPaise = try { CurrencyFormatter.parseInputToPaise(costInput) } catch (_: Exception) { 0L }
                    val stock = stockInput.toDoubleOrNull() ?: 0.0
                    val thresh = thresholdInput.toDoubleOrNull() ?: 5.0

                    if (name.isNotBlank() && sPaise > 0) {
                        onSave(name, unit, sPaise, cPaise, stock, thresh, barcode, "")
                    }
                },
                enabled = name.isNotBlank() && sellingInput.isNotBlank(),
                modifier = Modifier.defaultMinSize(minHeight = 44.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(stringResource(R.string.save), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.defaultMinSize(minHeight = 44.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(stringResource(R.string.cancel), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    )
}

@Composable
private fun AdjustStockDialog(
    product: ProductEntity,
    onDismiss: () -> Unit,
    onAdjust: (Double) -> Unit
) {
    var deltaInput by remember { mutableStateOf("") }
    var isAddition by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.adjust_stock_title, product.name), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "${stringResource(R.string.in_stock)}: ${product.stockQuantity} ${product.unit}",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = isAddition,
                        onClick = { isAddition = true },
                        label = { Text(stringResource(R.string.add_stock_mode)) }
                    )
                    FilterChip(
                        selected = !isAddition,
                        onClick = { isAddition = false },
                        label = { Text(stringResource(R.string.remove_stock_mode)) }
                    )
                }

                OutlinedTextField(
                    value = deltaInput,
                    onValueChange = { deltaInput = it },
                    label = { Text(stringResource(R.string.stock_quantity_label, product.unit)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val qty = deltaInput.toDoubleOrNull() ?: return@Button
                    val delta = if (isAddition) qty else -qty
                    onAdjust(delta)
                },
                enabled = deltaInput.toDoubleOrNull() != null && deltaInput.toDouble() > 0,
                modifier = Modifier.defaultMinSize(minHeight = 44.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(stringResource(R.string.apply), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.defaultMinSize(minHeight = 44.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(stringResource(R.string.cancel), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    )
}
