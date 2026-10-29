package com.syntaxa.khata.feature.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.syntaxa.khata.R
import com.syntaxa.khata.core.model.CurrencyFormatter
import com.syntaxa.khata.core.ui.components.CurrencyInputField
import com.syntaxa.khata.core.ui.theme.KiranaCreditGreen
import com.syntaxa.khata.core.ui.theme.KiranaDebitRed
import com.syntaxa.khata.data.repository.CashClosingReconciliation
import kotlinx.coroutines.launch

@Composable
fun DailyCashClosingDialog(
    viewModel: ReportsViewModel,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var openingInput by remember { mutableStateOf("0") }
    var countedInput by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var reconciliation by remember { mutableStateOf<CashClosingReconciliation?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    val summary by viewModel.reportSummary.collectAsState()

    val openingPaise = remember(openingInput) {
        try {
            CurrencyFormatter.parseInputToPaise(openingInput)
        } catch (_: Exception) {
            0L
        }
    }

    val countedPaise = remember(countedInput) {
        try {
            CurrencyFormatter.parseInputToPaise(countedInput)
        } catch (_: Exception) {
            0L
        }
    }

    LaunchedEffect(countedPaise, openingPaise) {
        reconciliation = viewModel.getCashReconciliation(countedPaise, openingPaise)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AccountBalanceWallet,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.daily_cash_closing_title),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        },
        text = {
            val rec = reconciliation
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (rec != null) {
                    // Breakdown Card
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = stringResource(R.string.cash_closing_formula_label),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(stringResource(R.string.opening_cash_label), style = MaterialTheme.typography.bodySmall)
                                Text(CurrencyFormatter.formatPaise(rec.openingCashPaise), style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold))
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(stringResource(R.string.cash_sales_today_label), style = MaterialTheme.typography.bodySmall)
                                Text("+${CurrencyFormatter.formatPaise(rec.cashSalesPaise)}", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold))
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(stringResource(R.string.old_udhaar_collected_label), style = MaterialTheme.typography.bodySmall)
                                Text("+${CurrencyFormatter.formatPaise(rec.cashKhataCollectedPaise)}", style = MaterialTheme.typography.bodySmall.copy(color = KiranaCreditGreen, fontWeight = FontWeight.SemiBold))
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(stringResource(R.string.cash_expenses_paid_label), style = MaterialTheme.typography.bodySmall)
                                Text("-${CurrencyFormatter.formatPaise(rec.cashExpensesPaise)}", style = MaterialTheme.typography.bodySmall.copy(color = KiranaDebitRed, fontWeight = FontWeight.SemiBold))
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(stringResource(R.string.expected_total_cash_label), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                                Text(
                                    CurrencyFormatter.formatPaise(rec.expectedCashPaise),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                )
                            }
                        }
                    }

                    // UPI Separate Information Box
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFF3E5F5),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "🏦 " + stringResource(R.string.direct_bank_upi_info, CurrencyFormatter.formatPaise(summary.totalUpiCollectedPaise)),
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold, color = Color(0xFF4A148C))
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    // Opening cash input
                    CurrencyInputField(
                        value = openingInput,
                        onValueChange = { openingInput = it },
                        label = stringResource(R.string.morning_opening_cash_label),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Counted physical cash input
                    CurrencyInputField(
                        value = countedInput,
                        onValueChange = { countedInput = it },
                        label = stringResource(R.string.physical_cash_counted_label),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Live reconciliation difference badge
                    if (countedInput.isNotBlank()) {
                        val diff = rec.differencePaise
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = when {
                                rec.isBalanced -> Color(0xFFE8F5E9)
                                rec.isSurplus -> Color(0xFFE3F2FD)
                                else -> Color(0xFFFFEBEE)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    1.dp,
                                    when {
                                        rec.isBalanced -> Color(0xFF81C784)
                                        rec.isSurplus -> Color(0xFF64B5F6)
                                        else -> Color(0xFFE57373)
                                    },
                                    RoundedCornerShape(8.dp)
                                )
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (rec.isBalanced) Icons.Default.CheckCircle else Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = when {
                                        rec.isBalanced -> KiranaCreditGreen
                                        rec.isSurplus -> Color(0xFF1976D2)
                                        else -> KiranaDebitRed
                                    },
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = when {
                                            rec.isBalanced -> stringResource(R.string.cash_drawer_balanced)
                                            rec.isSurplus -> stringResource(R.string.cash_surplus_detail, CurrencyFormatter.formatPaise(diff))
                                            else -> stringResource(R.string.cash_shortage_detail, CurrencyFormatter.formatPaise(-diff))
                                        },
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = when {
                                                rec.isBalanced -> KiranaCreditGreen
                                                rec.isSurplus -> Color(0xFF1565C0)
                                                else -> KiranaDebitRed
                                            }
                                        )
                                    )
                                    Text(
                                        text = stringResource(
                                            R.string.cash_counted_expected_summary,
                                            CurrencyFormatter.formatPaise(countedPaise),
                                            CurrencyFormatter.formatPaise(rec.expectedCashPaise)
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text(stringResource(R.string.reconcile_notes)) },
                        placeholder = { Text(stringResource(R.string.reconcile_notes_placeholder)) },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 2
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val rec = reconciliation ?: return@Button
                    isSaving = true
                    coroutineScope.launch {
                        try {
                            viewModel.saveDailyClosing(rec, notes)
                            onSuccess()
                        } finally {
                            isSaving = false
                        }
                    }
                },
                modifier = Modifier.defaultMinSize(minHeight = 44.dp),
                shape = RoundedCornerShape(8.dp),
                enabled = countedInput.isNotBlank() && !isSaving
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text(
                        text = stringResource(R.string.save_and_close_drawer),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        fontSize = 13.sp
                    )
                }
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
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }
    )
}
