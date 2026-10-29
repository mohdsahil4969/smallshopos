package com.syntaxa.khata.feature.customers

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.syntaxa.khata.R
import com.syntaxa.khata.core.model.CurrencyFormatter
import com.syntaxa.khata.core.ui.components.CurrencyInputField
import com.syntaxa.khata.core.ui.components.QuickAmountPills
import com.syntaxa.khata.core.ui.theme.KiranaCreditGreen
import com.syntaxa.khata.core.ui.theme.KiranaDebitRed
import com.syntaxa.khata.data.local.entity.TransactionConstants
import com.syntaxa.khata.domain.model.CustomerWithBalance
import kotlinx.coroutines.launch

@Composable
fun AddCustomerDialog(
    onDismiss: () -> Unit,
    onSave: (name: String, phone: String, notes: String) -> Unit,
    checkDuplicatePhone: suspend (String) -> Boolean
) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var isPhoneDuplicate by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val nameRequiredMsg = stringResource(R.string.error_customer_name_required)
    val coroutineScope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.action_add_customer), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        if (it.isNotBlank()) errorMessage = null
                    },
                    label = { Text(stringResource(R.string.customer_name)) },
                    isError = errorMessage != null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (errorMessage != null) {
                    Text(errorMessage!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                OutlinedTextField(
                    value = phone,
                    onValueChange = { input ->
                        phone = input
                        if (input.length >= 10) {
                            coroutineScope.launch {
                                isPhoneDuplicate = checkDuplicatePhone(input)
                            }
                        } else {
                            isPhoneDuplicate = false
                        }
                    },
                    label = { Text(stringResource(R.string.phone_number)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (isPhoneDuplicate) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                        Text(
                            text = stringResource(R.string.duplicate_phone_warning),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(stringResource(R.string.notes)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank()) {
                        errorMessage = nameRequiredMsg
                    } else {
                        onSave(name.trim(), phone.trim(), notes.trim())
                    }
                },
                modifier = Modifier.defaultMinSize(minHeight = 44.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.save),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
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

@Composable
fun EditCustomerDialog(
    initialName: String,
    initialPhone: String,
    initialNotes: String,
    onDismiss: () -> Unit,
    onSave: (name: String, phone: String, notes: String) -> Unit,
    checkDuplicatePhone: suspend (String) -> Boolean
) {
    var name by remember { mutableStateOf(initialName) }
    var phone by remember { mutableStateOf(initialPhone) }
    var notes by remember { mutableStateOf(initialNotes) }
    var isPhoneDuplicate by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val nameRequiredMsg = stringResource(R.string.error_customer_name_required)
    val coroutineScope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_customer_title), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        if (it.isNotBlank()) errorMessage = null
                    },
                    label = { Text(stringResource(R.string.customer_name)) },
                    isError = errorMessage != null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (errorMessage != null) {
                    Text(errorMessage!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                OutlinedTextField(
                    value = phone,
                    onValueChange = { input ->
                        phone = input
                        if (input.length >= 10 && input != initialPhone) {
                            coroutineScope.launch {
                                isPhoneDuplicate = checkDuplicatePhone(input)
                            }
                        } else {
                            isPhoneDuplicate = false
                        }
                    },
                    label = { Text(stringResource(R.string.phone_number)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (isPhoneDuplicate) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                        Text(
                            text = stringResource(R.string.duplicate_phone_warning),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(stringResource(R.string.notes)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank()) {
                        errorMessage = nameRequiredMsg
                    } else {
                        onSave(name.trim(), phone.trim(), notes.trim())
                    }
                },
                modifier = Modifier.defaultMinSize(minHeight = 44.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.save),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
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

@Composable
fun AddUdhaarDialog(
    preselectedCustomerId: Long? = null,
    customers: List<CustomerWithBalance>,
    onDismiss: () -> Unit,
    onSave: (customerId: Long, amountPaise: Long, description: String) -> Unit
) {
    var selectedCustomer by remember {
        mutableStateOf(customers.firstOrNull { it.id == preselectedCustomerId } ?: customers.firstOrNull())
    }
    var amountInput by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var isError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        title = { Text(stringResource(R.string.action_add_udhaar), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Customer Selector
                if (preselectedCustomerId == null) {
                    Text(stringResource(R.string.select_customer_label), style = MaterialTheme.typography.labelMedium)
                    CustomerDropdown(
                        customers = customers,
                        selectedCustomer = selectedCustomer,
                        onSelect = { selectedCustomer = it }
                    )
                } else {
                    Text(
                        text = stringResource(R.string.customer_colon, selectedCustomer?.name ?: ""),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }

                // Amount input with quick pills
                CurrencyInputField(
                    value = amountInput,
                    onValueChange = {
                        amountInput = it
                        isError = false
                    },
                    isError = isError,
                    errorMessage = stringResource(R.string.error_invalid_amount)
                )

                QuickAmountPills(
                    currentAmount = amountInput,
                    onAmountChange = { amountInput = it }
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.description)) },
                    placeholder = { Text("e.g. Atta 5kg, Sugar 2kg") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (isSubmitting) return@Button
                    val targetCustomer = selectedCustomer
                    val paise = try { CurrencyFormatter.parseInputToPaise(amountInput) } catch (_: Exception) { 0L }
                    if (targetCustomer == null || paise <= 0) {
                        isError = true
                    } else {
                        isSubmitting = true
                        onSave(targetCustomer.id, paise, description.trim())
                    }
                },
                modifier = Modifier.defaultMinSize(minHeight = 44.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = KiranaDebitRed),
                enabled = !isSubmitting
            ) {
                Text(
                    text = stringResource(R.string.confirm_and_save),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.defaultMinSize(minHeight = 44.dp),
                shape = RoundedCornerShape(8.dp),
                enabled = !isSubmitting
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReceivePaymentDialog(
    preselectedCustomerId: Long? = null,
    customers: List<CustomerWithBalance>,
    onDismiss: () -> Unit,
    onSave: (customerId: Long, amountPaise: Long, paymentMethod: String, description: String) -> Unit
) {
    var selectedCustomer by remember {
        mutableStateOf(customers.firstOrNull { it.id == preselectedCustomerId } ?: customers.firstOrNull())
    }
    var amountInput by remember { mutableStateOf("") }
    var paymentMethod by remember { mutableStateOf(TransactionConstants.METHOD_CASH) }
    var description by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var isError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        title = { Text(stringResource(R.string.action_receive_payment), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (preselectedCustomerId == null) {
                    Text(stringResource(R.string.select_customer_label), style = MaterialTheme.typography.labelMedium)
                    CustomerDropdown(
                        customers = customers,
                        selectedCustomer = selectedCustomer,
                        onSelect = { selectedCustomer = it }
                    )
                } else {
                    selectedCustomer?.let { c ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(c.name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                            Text(
                                stringResource(R.string.due_colon, CurrencyFormatter.formatPaise(c.balancePaise)),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (c.balancePaise > 0) KiranaDebitRed else KiranaCreditGreen
                                )
                            )
                        }
                    }
                }

                CurrencyInputField(
                    value = amountInput,
                    onValueChange = {
                        amountInput = it
                        isError = false
                    },
                    isError = isError,
                    errorMessage = stringResource(R.string.error_invalid_amount)
                )

                QuickAmountPills(
                    currentAmount = amountInput,
                    onAmountChange = { amountInput = it }
                )

                // Payment Method Selector
                Text(stringResource(R.string.payment_method), style = MaterialTheme.typography.labelMedium)
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        TransactionConstants.METHOD_CASH to stringResource(R.string.method_cash),
                        TransactionConstants.METHOD_UPI to stringResource(R.string.method_upi),
                        TransactionConstants.METHOD_OTHER to stringResource(R.string.method_other)
                    ).forEach { (method, label) ->
                        FilterChip(
                            selected = paymentMethod == method,
                            onClick = { paymentMethod = method },
                            label = { Text(label) }
                        )
                    }
                }

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.description)) },
                    placeholder = { Text("e.g. Partial repayment") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (isSubmitting) return@Button
                    val targetCustomer = selectedCustomer
                    val paise = try { CurrencyFormatter.parseInputToPaise(amountInput) } catch (_: Exception) { 0L }
                    if (targetCustomer == null || paise <= 0) {
                        isError = true
                    } else {
                        isSubmitting = true
                        onSave(targetCustomer.id, paise, paymentMethod, description.trim())
                    }
                },
                modifier = Modifier.defaultMinSize(minHeight = 44.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = KiranaCreditGreen),
                enabled = !isSubmitting
            ) {
                Text(
                    text = stringResource(R.string.confirm_and_save),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.defaultMinSize(minHeight = 44.dp),
                shape = RoundedCornerShape(8.dp),
                enabled = !isSubmitting
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
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selectedCustomer?.let { "${it.name} (${stringResource(R.string.due_colon, CurrencyFormatter.formatPaise(it.balancePaise))})" } ?: stringResource(R.string.select_customer_label),
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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(customer.name)
                            Text(CurrencyFormatter.formatPaise(customer.balancePaise), fontWeight = FontWeight.Bold)
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
