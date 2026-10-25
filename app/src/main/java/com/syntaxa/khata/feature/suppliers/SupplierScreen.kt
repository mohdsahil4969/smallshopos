package com.syntaxa.khata.feature.suppliers

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.syntaxa.khata.R
import com.syntaxa.khata.core.model.CurrencyFormatter
import com.syntaxa.khata.core.model.DateTimeUtils
import com.syntaxa.khata.core.ui.components.CurrencyInputField
import com.syntaxa.khata.core.ui.components.KiranaCard
import com.syntaxa.khata.core.ui.theme.KiranaCreditGreen
import com.syntaxa.khata.core.ui.theme.KiranaDebitRed
import com.syntaxa.khata.data.local.entity.SupplierTransactionEntity
import com.syntaxa.khata.domain.model.SupplierWithBalance

@Composable
fun SupplierScreen(
    viewModel: SupplierViewModel,
    modifier: Modifier = Modifier
) {
    val suppliers by viewModel.suppliers.collectAsState()
    val searchQuery by viewModel.query.collectAsState()
    val transactions by viewModel.supplierTransactions.collectAsState()

    var showAddSupplierDialog by remember { mutableStateOf(false) }
    var showRecordPurchaseDialog by remember { mutableStateOf(false) }
    var showRecordPaymentDialog by remember { mutableStateOf(false) }
    var showStatementDialog by remember { mutableStateOf(false) }
    var activeSupplier by remember { mutableStateOf<SupplierWithBalance?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddSupplierDialog = true },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Supplier", tint = MaterialTheme.colorScheme.onPrimary)
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
                label = { Text("Search Suppliers / Wholesalers") },
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

            Spacer(modifier = Modifier.height(10.dp))

            if (suppliers.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No suppliers found. Tap + to add a wholesaler/vendor.",
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
                    items(suppliers, key = { it.id }) { supplier ->
                        SupplierItemCard(
                            supplier = supplier,
                            onRecordPurchase = {
                                activeSupplier = supplier
                                showRecordPurchaseDialog = true
                            },
                            onRecordPayment = {
                                activeSupplier = supplier
                                showRecordPaymentDialog = true
                            },
                            onViewStatement = {
                                activeSupplier = supplier
                                viewModel.selectSupplier(supplier.id)
                                showStatementDialog = true
                            }
                        )
                    }
                }
            }
        }
    }

    if (showAddSupplierDialog) {
        AddSupplierDialog(
            onDismiss = { showAddSupplierDialog = false },
            onSave = { name, phone, notes ->
                viewModel.addSupplier(name, phone, notes)
                showAddSupplierDialog = false
            }
        )
    }

    if (showRecordPurchaseDialog && activeSupplier != null) {
        RecordSupplierPurchaseDialog(
            supplier = activeSupplier!!,
            onDismiss = { showRecordPurchaseDialog = false },
            onConfirm = { amountPaise, invoiceNo, notes ->
                viewModel.recordPurchase(activeSupplier!!.id, amountPaise, invoiceNo, notes)
                showRecordPurchaseDialog = false
            }
        )
    }

    if (showRecordPaymentDialog && activeSupplier != null) {
        RecordSupplierPaymentDialog(
            supplier = activeSupplier!!,
            onDismiss = { showRecordPaymentDialog = false },
            onConfirm = { amountPaise, mode, notes ->
                viewModel.recordPayment(activeSupplier!!.id, amountPaise, mode, notes)
                showRecordPaymentDialog = false
            }
        )
    }

    if (showStatementDialog && activeSupplier != null) {
        SupplierStatementDialog(
            supplier = activeSupplier!!,
            transactions = transactions,
            onDismiss = {
                showStatementDialog = false
                viewModel.selectSupplier(null)
            }
        )
    }
}

@Composable
private fun SupplierItemCard(
    supplier: SupplierWithBalance,
    onRecordPurchase: () -> Unit,
    onRecordPayment: () -> Unit,
    onViewStatement: () -> Unit
) {
    KiranaCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                    text = supplier.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (supplier.phone.isNotBlank()) {
                    Text(
                        text = "📞 ${supplier.phone}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Balance Payable Badge
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = if (supplier.balancePaise > 0) stringResource(R.string.supplier_to_pay) else stringResource(R.string.supplier_settled),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = CurrencyFormatter.formatPaise(supplier.balancePaise),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (supplier.balancePaise > 0) KiranaDebitRed else KiranaCreditGreen
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        HorizontalDivider()

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onViewStatement,
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
            ) {
                Icon(Icons.Default.Receipt, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = stringResource(R.string.supplier_statement),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            FilledTonalButton(
                onClick = onRecordPurchase,
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp),
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onErrorContainer)
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    text = stringResource(R.string.record_purchase),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Button(
                onClick = onRecordPayment,
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
            ) {
                Icon(Icons.Default.Payment, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    text = stringResource(R.string.record_payment),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun AddSupplierDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_supplier_title), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.supplier_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text(stringResource(R.string.phone_number)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(stringResource(R.string.notes_goods_label)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotBlank()) onSave(name, phone, notes) },
                enabled = name.isNotBlank(),
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
private fun RecordSupplierPurchaseDialog(
    supplier: SupplierWithBalance,
    onDismiss: () -> Unit,
    onConfirm: (Long, String, String) -> Unit
) {
    var amountInput by remember { mutableStateOf("") }
    var invoiceNo by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.record_purchase_title, supplier.name), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CurrencyInputField(
                    value = amountInput,
                    onValueChange = { amountInput = it },
                    label = "Purchase Invoice Total (₹) *",
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = invoiceNo,
                    onValueChange = { invoiceNo = it },
                    label = { Text(stringResource(R.string.supplier_invoice_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(stringResource(R.string.items_desc_label)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val paise = try { CurrencyFormatter.parseInputToPaise(amountInput) } catch (_: Exception) { 0L }
                    if (paise > 0) onConfirm(paise, invoiceNo, notes)
                },
                enabled = amountInput.isNotBlank(),
                modifier = Modifier.defaultMinSize(minHeight = 44.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(stringResource(R.string.add_to_payable), maxLines = 1, overflow = TextOverflow.Ellipsis)
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecordSupplierPaymentDialog(
    supplier: SupplierWithBalance,
    onDismiss: () -> Unit,
    onConfirm: (Long, String, String) -> Unit
) {
    var amountInput by remember { mutableStateOf("") }
    var paymentMode by remember { mutableStateOf("CASH") }
    var notes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pay_supplier_title, supplier.name), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CurrencyInputField(
                    value = amountInput,
                    onValueChange = { amountInput = it },
                    label = "Amount Paid (₹) *",
                    modifier = Modifier.fillMaxWidth()
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FilterChip(
                        selected = paymentMode == "CASH",
                        onClick = { paymentMode = "CASH" },
                        label = { Text(stringResource(R.string.method_cash)) }
                    )
                    FilterChip(
                        selected = paymentMode == "UPI",
                        onClick = { paymentMode = "UPI" },
                        label = { Text(stringResource(R.string.method_upi)) }
                    )
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Transaction Reference / Notes") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val paise = try { CurrencyFormatter.parseInputToPaise(amountInput) } catch (_: Exception) { 0L }
                    if (paise > 0) onConfirm(paise, paymentMode, notes)
                },
                enabled = amountInput.isNotBlank(),
                modifier = Modifier.defaultMinSize(minHeight = 44.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(stringResource(R.string.record_payment), maxLines = 1, overflow = TextOverflow.Ellipsis)
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
private fun SupplierStatementDialog(
    supplier: SupplierWithBalance,
    transactions: List<SupplierTransactionEntity>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("${supplier.name} — Statement", fontWeight = FontWeight.Bold)
                Text(
                    "Current Due: ${CurrencyFormatter.formatPaise(supplier.balancePaise)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (supplier.balancePaise > 0) KiranaDebitRed else KiranaCreditGreen
                )
            }
        },
        text = {
            if (transactions.isEmpty()) {
                Text("No transactions recorded yet for this supplier.")
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(transactions, key = { it.id }) { tx ->
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(8.dp)
                                    .fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = if (tx.type == "PURCHASE") "Purchase ${if (tx.billInvoiceNumber.isNotBlank()) "#${tx.billInvoiceNumber}" else ""}" else "Payment Made (${tx.paymentMethod})",
                                        fontWeight = FontWeight.SemiBold,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = DateTimeUtils.formatDateTime(tx.occurredAt),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = "${if (tx.type == "PURCHASE") "+" else "-"}${CurrencyFormatter.formatPaise(tx.amountPaise)}",
                                    fontWeight = FontWeight.Bold,
                                    color = if (tx.type == "PURCHASE") KiranaDebitRed else KiranaCreditGreen
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.defaultMinSize(minHeight = 44.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(stringResource(R.string.close), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    )
}
