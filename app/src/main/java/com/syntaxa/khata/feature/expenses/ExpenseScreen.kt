package com.syntaxa.khata.feature.expenses

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.syntaxa.khata.R
import com.syntaxa.khata.core.model.CurrencyFormatter
import com.syntaxa.khata.core.model.DateTimeUtils
import com.syntaxa.khata.core.ui.components.CurrencyInputField
import com.syntaxa.khata.core.ui.components.KiranaCard
import com.syntaxa.khata.core.ui.components.QuickAmountPills
import com.syntaxa.khata.data.local.entity.ExpenseConstants
import com.syntaxa.khata.data.local.entity.TransactionConstants

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExpenseScreen(
    viewModel: ExpenseViewModel,
    modifier: Modifier = Modifier
) {
    val expenses by viewModel.activeExpenses.collectAsState()
    val todayExpenses by viewModel.todayExpenses.collectAsState()

    var amountInput by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(ExpenseConstants.CAT_STOCK) }
    var description by remember { mutableStateOf("") }
    var paymentMethod by remember { mutableStateOf(TransactionConstants.METHOD_CASH) }
    var isSubmitting by remember { mutableStateOf(false) }

    val categories = listOf(
        ExpenseConstants.CAT_STOCK,
        ExpenseConstants.CAT_TRANSPORT,
        ExpenseConstants.CAT_RENT,
        ExpenseConstants.CAT_ELECTRICITY,
        ExpenseConstants.CAT_WAGES,
        ExpenseConstants.CAT_OTHER
    )

    val todayTotalPaise = todayExpenses.sumOf { it.amountPaise }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp)
    ) {
        // Today's Expense Header Card
        item {
            KiranaCard {
                Text(
                    text = stringResource(R.string.today_expenses),
                    style = MaterialTheme.typography.labelMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = CurrencyFormatter.formatPaise(todayTotalPaise),
                    style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold)
                )
            }
        }

        // Add Expense Form Card
        item {
            KiranaCard {
                Text(
                    text = stringResource(R.string.add_expense),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(10.dp))

                CurrencyInputField(
                    value = amountInput,
                    onValueChange = { amountInput = it }
                )

                QuickAmountPills(
                    currentAmount = amountInput,
                    onAmountChange = { amountInput = it }
                )

                // Category Chips
                Text(stringResource(R.string.expense_category), style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(4.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    categories.forEach { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            label = { Text(cat, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.description)) },
                    placeholder = { Text("e.g. Milk crate delivery") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Payment Method
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        TransactionConstants.METHOD_CASH to stringResource(R.string.method_cash),
                        TransactionConstants.METHOD_UPI to stringResource(R.string.method_upi)
                    ).forEach { (method, label) ->
                        FilterChip(
                            selected = paymentMethod == method,
                            onClick = { paymentMethod = method },
                            label = { Text(label) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        val paise = try { CurrencyFormatter.parseInputToPaise(amountInput) } catch (_: Exception) { 0L }
                        if (paise > 0 && !isSubmitting) {
                            isSubmitting = true
                            viewModel.addExpense(
                                amountPaise = paise,
                                category = selectedCategory,
                                description = description.trim(),
                                paymentMethod = paymentMethod,
                                onSuccess = {
                                    isSubmitting = false
                                    amountInput = ""
                                    description = ""
                                },
                                onError = {
                                    isSubmitting = false
                                }
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(stringResource(R.string.save), fontWeight = FontWeight.Bold)
                }
            }
        }

        // Recorded Expenses Section
        item {
            Text(
                text = "Recorded Expenses (${expenses.size})",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        }

        if (expenses.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                ) {
                    Box(modifier = Modifier.padding(24.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("No expenses recorded yet")
                    }
                }
            }
        } else {
            items(expenses, key = { it.id }) { ex ->
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
                            Text(ex.category, fontWeight = FontWeight.SemiBold)
                            if (ex.description.isNotBlank()) {
                                Text(ex.description, style = MaterialTheme.typography.bodySmall)
                            }
                            Text(
                                "${DateTimeUtils.formatDateTime(ex.occurredAt)} • ${ex.paymentMethod}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                CurrencyFormatter.formatPaise(ex.amountPaise),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            IconButton(onClick = { viewModel.reverseExpense(ex.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}
