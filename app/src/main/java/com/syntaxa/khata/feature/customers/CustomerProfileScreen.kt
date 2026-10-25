package com.syntaxa.khata.feature.customers

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.syntaxa.khata.R
import com.syntaxa.khata.core.model.CurrencyFormatter
import com.syntaxa.khata.core.model.DateTimeUtils
import com.syntaxa.khata.core.ui.components.KiranaCard
import com.syntaxa.khata.core.ui.theme.KiranaCreditGreen
import com.syntaxa.khata.core.ui.theme.KiranaDebitRed
import com.syntaxa.khata.data.local.entity.BillConstants
import com.syntaxa.khata.data.local.entity.BillEntity
import com.syntaxa.khata.data.local.entity.BillItemEntity
import com.syntaxa.khata.data.local.entity.FinancialTransactionEntity
import com.syntaxa.khata.data.local.entity.TransactionConstants
import com.syntaxa.khata.domain.model.BillWithItems
import com.syntaxa.khata.domain.model.CustomerFinancialSummary
import com.syntaxa.khata.domain.model.CustomerLedgerEntry
import com.syntaxa.khata.domain.model.LedgerDateFilter
import com.syntaxa.khata.feature.printing.PrintResult
import com.syntaxa.khata.feature.receipts.ShareHelper
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerProfileScreen(
    customerId: Long,
    viewModel: CustomerViewModel,
    onBack: () -> Unit,
    onOpenAddUdhaar: (Long) -> Unit,
    onOpenReceivePayment: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(customerId) {
        viewModel.selectCustomer(customerId)
    }

    val customerWithBalance by viewModel.selectedCustomerWithBalance.collectAsState()
    val ledgerEntries by viewModel.customerLedgerEntries.collectAsState()
    val financialSummary by viewModel.customerFinancialSummary.collectAsState()
    val selectedFilter by viewModel.dateFilter.collectAsState()

    // Dialog states
    var transactionToReverse by remember { mutableStateOf<FinancialTransactionEntity?>(null) }
    var transactionToShare by remember { mutableStateOf<FinancialTransactionEntity?>(null) }
    var billDetailToView by remember { mutableStateOf<BillWithItems?>(null) }
    var billToShareModal by remember { mutableStateOf<BillWithItems?>(null) }
    var reversalReason by remember { mutableStateOf("") }
    var showReminderDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showDueDatePicker by remember { mutableStateOf(false) }
    var showCustomDateDialog by remember { mutableStateOf(false) }
    var showNoPrinterDialog by remember { mutableStateOf(false) }
    var reminderText by remember { mutableStateOf("") }

    // Expanded bills set
    val expandedBillIds = remember { mutableStateMapOf<Long, Boolean>() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = customerWithBalance?.name ?: stringResource(R.string.customers),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.Bold
                        )
                        customerWithBalance?.phone?.takeIf { it.isNotBlank() }?.let { phone ->
                            Text(
                                text = phone,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    customerWithBalance?.let { c ->
                        IconButton(onClick = { showEditDialog = true }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit Customer")
                        }
                        IconButton(onClick = {
                            if (c.archivedAt == null) {
                                viewModel.archiveCustomer(c.id) {
                                    Toast.makeText(context, "Customer archived", Toast.LENGTH_SHORT).show()
                                    onBack()
                                }
                            } else {
                                viewModel.restoreCustomer(c.id) {
                                    Toast.makeText(context, "Customer restored", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }) {
                            Icon(
                                imageVector = if (c.archivedAt == null) Icons.Default.Archive else Icons.Default.Unarchive,
                                contentDescription = "Archive/Restore"
                            )
                        }
                    }
                }
            )
        },
        modifier = modifier
    ) { paddingValues ->
        val customer = customerWithBalance
        if (customer == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            val owesMoney = customer.balancePaise > 0
            val hasAdvance = customer.balancePaise < 0

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 80.dp)
            ) {
                // ==========================================
                // 1. Header: Customer Balance & Actions Card
                // ==========================================
                item {
                    KiranaCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = when {
                                        owesMoney -> stringResource(R.string.customer_balance)
                                        hasAdvance -> stringResource(R.string.advance_balance)
                                        else -> stringResource(R.string.no_dues)
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = CurrencyFormatter.formatPaise(customer.balancePaise),
                                    style = MaterialTheme.typography.headlineLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 32.sp
                                    ),
                                    color = when {
                                        owesMoney -> KiranaDebitRed
                                        hasAdvance -> KiranaCreditGreen
                                        else -> MaterialTheme.colorScheme.onSurface
                                    }
                                )
                            }

                            if (customer.phone.isNotBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.clickable {
                                        ShareHelper.shareTextViaSms(context, "", customer.phone)
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Text(customer.phone, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }

                        if (customer.notes.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Note: ${customer.notes}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                        // Promised Due Date Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { showDueDatePicker = true }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Event,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (customer.dueDate != null) "Promised Due: ${DateTimeUtils.formatDate(customer.dueDate)}" else "Set Payment Due Date",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Text(
                                text = if (customer.dueDate != null) "Change" else "+ Add",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // ==========================================
                // 2. Main Financial Actions: Give Udhaar & Receive Payment
                // ==========================================
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { onOpenAddUdhaar(customer.id) },
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 46.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = KiranaDebitRed),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.ArrowUpward, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.action_add_udhaar),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Button(
                            onClick = { onOpenReceivePayment(customer.id) },
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 46.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = KiranaCreditGreen),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.ArrowDownward, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.action_receive_payment),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // ==========================================
                // 3. Customer Financial Summary (4-Figure Grid + Date Filter)
                // ==========================================
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            // Section header
                            Text(
                                text = "Account & Purchase Summary",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            // Date filter chips
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                LedgerDateFilter.values().forEach { filter ->
                                    val isSelected = selectedFilter == filter
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            if (filter == LedgerDateFilter.CUSTOM) {
                                                showCustomDateDialog = true
                                            } else {
                                                viewModel.setDateFilter(filter)
                                            }
                                        },
                                        label = {
                                            Text(
                                                text = when (filter) {
                                                    LedgerDateFilter.ALL_TIME -> stringResource(R.string.filter_all_time)
                                                    LedgerDateFilter.LAST_30_DAYS -> stringResource(R.string.filter_last_30_days)
                                                    LedgerDateFilter.LAST_3_MONTHS -> stringResource(R.string.filter_last_3_months)
                                                    LedgerDateFilter.LAST_YEAR -> stringResource(R.string.filter_last_year)
                                                    LedgerDateFilter.CUSTOM -> stringResource(R.string.filter_custom_range)
                                                },
                                                fontSize = 11.sp
                                            )
                                        }
                                    )
                                }
                            }

                            // Explicit period label
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = formatPeriodText(financialSummary),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // 4 figures grid (2x2)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                SummaryStatBox(
                                    modifier = Modifier.weight(1f),
                                    label = stringResource(R.string.ledger_total_purchases),
                                    value = CurrencyFormatter.formatPaise(financialSummary.totalPurchasesPaise),
                                    valueColor = MaterialTheme.colorScheme.onSurface
                                )
                                SummaryStatBox(
                                    modifier = Modifier.weight(1f),
                                    label = stringResource(R.string.ledger_payments_received),
                                    value = CurrencyFormatter.formatPaise(financialSummary.paymentsReceivedPaise),
                                    valueColor = KiranaCreditGreen
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                SummaryStatBox(
                                    modifier = Modifier.weight(1f),
                                    label = stringResource(R.string.ledger_outstanding_balance),
                                    value = CurrencyFormatter.formatPaise(financialSummary.currentOutstandingBalancePaise),
                                    valueColor = if (financialSummary.currentOutstandingBalancePaise > 0) KiranaDebitRed else KiranaCreditGreen,
                                    subtitle = "(Overall Due)"
                                )
                                SummaryStatBox(
                                    modifier = Modifier.weight(1f),
                                    label = stringResource(R.string.ledger_transactions_count),
                                    value = "${financialSummary.transactionCount}",
                                    valueColor = MaterialTheme.colorScheme.primary,
                                    subtitle = "(In selected period)"
                                )
                            }
                        }
                    }
                }

                // ==========================================
                // 4. Customer-Level Action Strip (WhatsApp, SMS, Email, PDF, Reminder)
                // ==========================================
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // PDF Statement button
                        OutlinedButton(
                            onClick = {
                                coroutineScope.launch {
                                    try {
                                        val pdf = viewModel.generateStatementPdf(
                                            context,
                                            customer.id,
                                            financialSummary.startEpoch,
                                            financialSummary.endEpoch
                                        )
                                        ShareHelper.sharePdf(context, pdf, "Statement - ${customer.name}")
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            modifier = Modifier.height(38.dp),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.share_pdf), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }

                        // WhatsApp statement summary
                        OutlinedButton(
                            onClick = {
                                val text = buildStatementShareText(customer.name, financialSummary)
                                ShareHelper.shareTextViaWhatsApp(context, text, customer.phone)
                            },
                            modifier = Modifier.height(38.dp),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.share_via_whatsapp), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }

                        // SMS statement summary
                        OutlinedButton(
                            onClick = {
                                val text = buildStatementShareText(customer.name, financialSummary)
                                ShareHelper.shareTextViaSms(context, text, customer.phone)
                            },
                            modifier = Modifier.height(38.dp),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.Sms, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("SMS", fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }

                        // Email statement
                        OutlinedButton(
                            onClick = {
                                coroutineScope.launch {
                                    try {
                                        val pdf = viewModel.generateStatementPdf(
                                            context,
                                            customer.id,
                                            financialSummary.startEpoch,
                                            financialSummary.endEpoch
                                        )
                                        val body = buildStatementShareText(customer.name, financialSummary)
                                        ShareHelper.shareViaEmail(
                                            context,
                                            "Statement - ${customer.name}",
                                            body,
                                            pdf
                                        )
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Email error: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            modifier = Modifier.height(38.dp),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Email", fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }

                        // Reminder button
                        OutlinedButton(
                            onClick = {
                                coroutineScope.launch {
                                    val template = viewModel.getReminderTemplate(customer.id)
                                    reminderText = template.toEnglishText()
                                    showReminderDialog = true
                                }
                            },
                            modifier = Modifier.height(38.dp),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.reminder_message), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }

                // ==========================================
                // 5. Timeline / Ledger History Section (Chronological, Newest First)
                // ==========================================
                item {
                    Text(
                        text = "${stringResource(R.string.all_interactions)} (${ledgerEntries.size})",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }

                if (ledgerEntries.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(28.dp)
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No bills or transactions recorded in this period",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    items(ledgerEntries, key = { "${it.javaClass.simpleName}_${it.id}" }) { entry ->
                        when (entry) {
                            is CustomerLedgerEntry.BillPurchase -> {
                                val isExpanded = expandedBillIds[entry.id] ?: false
                                BillLedgerItemCard(
                                    entry = entry,
                                    isExpanded = isExpanded,
                                    onToggleExpand = {
                                        expandedBillIds[entry.id] = !isExpanded
                                    },
                                    onViewFullBill = {
                                        billDetailToView = entry.billWithItems
                                    },
                                    onSharePdf = {
                                        coroutineScope.launch {
                                            try {
                                                val pdf = viewModel.generateBillPdf(context, entry.billWithItems)
                                                ShareHelper.sharePdf(context, pdf, "Bill ${entry.receiptNumber}")
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    onOpenShareModal = {
                                        billToShareModal = entry.billWithItems
                                    },
                                    onPrintBill = {
                                        coroutineScope.launch {
                                            val paired = viewModel.getPairedPrinters(context)
                                            if (paired.isEmpty()) {
                                                showNoPrinterDialog = true
                                            } else {
                                                val res = viewModel.printBillThermal(context, entry.billWithItems)
                                                when (res) {
                                                    is PrintResult.Success -> {
                                                        Toast.makeText(context, "Receipt sent to printer!", Toast.LENGTH_SHORT).show()
                                                    }
                                                    is PrintResult.Failure -> {
                                                        Toast.makeText(context, "Print error: ${res.message}", Toast.LENGTH_LONG).show()
                                                    }
                                                }
                                            }
                                        }
                                    }
                                )
                            }

                            is CustomerLedgerEntry.Payment -> {
                                PaymentLedgerItemCard(
                                    entry = entry,
                                    onClick = {
                                        transactionToShare = entry.transaction
                                    }
                                )
                            }

                            is CustomerLedgerEntry.DirectUdhaar -> {
                                DirectUdhaarLedgerItemCard(
                                    entry = entry,
                                    onClick = {
                                        transactionToShare = entry.transaction
                                    },
                                    onReverse = {
                                        transactionToReverse = entry.transaction
                                    }
                                )
                            }

                            is CustomerLedgerEntry.Reversal -> {
                                ReversalLedgerItemCard(entry = entry)
                            }
                        }
                    }
                }
            }
        }
    }

    // ==========================================
    // Modals & Dialogs
    // ==========================================

    // 1. Bill Detail Modal
    billDetailToView?.let { bwi ->
        BillDetailDialog(
            billWithItems = bwi,
            onDismiss = { billDetailToView = null },
            onSharePdf = {
                coroutineScope.launch {
                    try {
                        val pdf = viewModel.generateBillPdf(context, bwi)
                        ShareHelper.sharePdf(context, pdf, "Bill ${bwi.bill.receiptNumber}")
                    } catch (e: Exception) {
                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onPrint = {
                coroutineScope.launch {
                    val paired = viewModel.getPairedPrinters(context)
                    if (paired.isEmpty()) {
                        showNoPrinterDialog = true
                    } else {
                        val res = viewModel.printBillThermal(context, bwi)
                        when (res) {
                            is PrintResult.Success -> {
                                Toast.makeText(context, "Receipt sent to printer!", Toast.LENGTH_SHORT).show()
                            }
                            is PrintResult.Failure -> {
                                Toast.makeText(context, "Print error: ${res.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            }
        )
    }

    // 2. Bill Share Modal (WhatsApp / SMS / Email)
    billToShareModal?.let { bwi ->
        AlertDialog(
            onDismissRequest = { billToShareModal = null },
            title = { Text("Share Bill #${bwi.bill.receiptNumber}", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Total: ${CurrencyFormatter.formatPaise(bwi.bill.totalPaise)}")
                    Text("Paid: ${CurrencyFormatter.formatPaise(bwi.bill.amountPaidPaise)} | Due: ${CurrencyFormatter.formatPaise(bwi.bill.totalPaise - bwi.bill.amountPaidPaise)}")
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Choose how you want to share this bill receipt:")
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            try {
                                val pdf = viewModel.generateBillPdf(context, bwi)
                                ShareHelper.shareViaWhatsApp(context, pdf)
                            } catch (e: Exception) {
                                Toast.makeText(context, "WhatsApp error: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                            billToShareModal = null
                        }
                    }
                ) {
                    Text("WhatsApp")
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            val msg = "Bill #${bwi.bill.receiptNumber} total: ${CurrencyFormatter.formatPaise(bwi.bill.totalPaise)}, Paid: ${CurrencyFormatter.formatPaise(bwi.bill.amountPaidPaise)}. Thank you!"
                            ShareHelper.shareTextViaSms(context, msg, customerWithBalance?.phone ?: "")
                            billToShareModal = null
                        }
                    ) {
                        Text("SMS")
                    }
                    TextButton(
                        onClick = {
                            coroutineScope.launch {
                                try {
                                    val pdf = viewModel.generateBillPdf(context, bwi)
                                    ShareHelper.sharePdf(context, pdf, "Bill #${bwi.bill.receiptNumber}")
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                                billToShareModal = null
                            }
                        }
                    ) {
                        Text("More...")
                    }
                }
            }
        )
    }

    // 3. Bluetooth Printer Guidance Dialog
    if (showNoPrinterDialog) {
        AlertDialog(
            onDismissRequest = { showNoPrinterDialog = false },
            icon = { Icon(Icons.Default.Print, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text(stringResource(R.string.printer_guidance_title), fontWeight = FontWeight.Bold) },
            text = {
                Text(stringResource(R.string.printer_guidance_msg))
            },
            confirmButton = {
                Button(
                    onClick = {
                        showNoPrinterDialog = false
                        try {
                            val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Could not open Bluetooth settings", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text(stringResource(R.string.printer_guidance_open_settings))
                }
            },
            dismissButton = {
                TextButton(onClick = { showNoPrinterDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // 4. Custom Date Range Dialog
    if (showCustomDateDialog) {
        CustomDateRangeDialog(
            onDismiss = { showCustomDateDialog = false },
            onApply = { startEpoch, endEpoch ->
                viewModel.setCustomDateRange(startEpoch, endEpoch)
                showCustomDateDialog = false
            }
        )
    }

    // 5. Edit Customer Dialog
    if (showEditDialog) {
        customerWithBalance?.let { c ->
            EditCustomerDialog(
                initialName = c.name,
                initialPhone = c.phone,
                initialNotes = c.notes,
                onDismiss = { showEditDialog = false },
                onSave = { updatedName, updatedPhone, updatedNotes ->
                    viewModel.updateCustomer(
                        customerId = c.id,
                        name = updatedName,
                        phone = updatedPhone,
                        notes = updatedNotes,
                        onSuccess = {
                            showEditDialog = false
                            Toast.makeText(context, "Customer details updated", Toast.LENGTH_SHORT).show()
                        },
                        onError = { err ->
                            Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                        }
                    )
                },
                checkDuplicatePhone = { phone ->
                    viewModel.isPhoneDuplicate(phone, c.id)
                }
            )
        }
    }

    // 6. Single Transaction Share Dialog
    transactionToShare?.let { tx ->
        AlertDialog(
            onDismissRequest = { transactionToShare = null },
            title = {
                Text(
                    text = if (tx.type == TransactionConstants.TYPE_PAYMENT_RECEIVED) "Payment Receipt" else "Udhaar Receipt",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Ref: #TXN-${tx.id}")
                    Text("Amount: ${CurrencyFormatter.formatPaise(tx.amountPaise)}")
                    if (tx.description.isNotBlank()) Text("Note: ${tx.description}")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Share single transaction PDF receipt with customer:")
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            try {
                                val pdf = viewModel.generateTransactionPdf(context, customerId, tx)
                                ShareHelper.shareViaWhatsApp(context, pdf)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                            transactionToShare = null
                        }
                    }
                ) {
                    Text(stringResource(R.string.share_via_whatsapp))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            try {
                                val pdf = viewModel.generateTransactionPdf(context, customerId, tx)
                                ShareHelper.sharePdf(context, pdf, "Transaction Receipt #TXN-${tx.id}")
                            } catch (e: Exception) {
                                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                            transactionToShare = null
                        }
                    }
                ) {
                    Text(stringResource(R.string.share_pdf))
                }
            }
        )
    }

    // 7. Reminder Dialog
    if (showReminderDialog) {
        customerWithBalance?.let { c ->
            AlertDialog(
                onDismissRequest = { showReminderDialog = false },
                title = { Text(stringResource(R.string.reminder_message)) },
                text = {
                    Column {
                        OutlinedTextField(
                            value = reminderText,
                            onValueChange = { reminderText = it },
                            label = { Text("Message Text") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp),
                            maxLines = 5
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showReminderDialog = false
                            ShareHelper.shareTextViaWhatsApp(context, reminderText, c.phone)
                        }
                    ) {
                        Text(stringResource(R.string.share_via_whatsapp))
                    }
                },
                dismissButton = {
                    Row {
                        TextButton(
                            onClick = {
                                showReminderDialog = false
                                ShareHelper.shareTextViaSms(context, reminderText, c.phone)
                            }
                        ) {
                            Text("SMS")
                        }
                        TextButton(
                            onClick = {
                                ShareHelper.copyToClipboard(context, reminderText)
                                showReminderDialog = false
                            }
                        ) {
                            Text(stringResource(R.string.copy_text))
                        }
                    }
                }
            )
        }
    }

    // 8. Reversal Confirmation Dialog
    transactionToReverse?.let { tx ->
        AlertDialog(
            onDismissRequest = { transactionToReverse = null },
            title = { Text(stringResource(R.string.reverse_confirm_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.reverse_confirm_msg))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Amount: ${CurrencyFormatter.formatPaise(tx.amountPaise)} (${tx.type})",
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = reversalReason,
                        onValueChange = { reversalReason = it },
                        label = { Text("Reason (Optional)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.reverseTransaction(tx.id, reversalReason) {
                            transactionToReverse = null
                            reversalReason = ""
                            Toast.makeText(context, "Transaction reversed", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.reverse_transaction))
                }
            },
            dismissButton = {
                TextButton(onClick = { transactionToReverse = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // 9. Due Date Picker
    if (showDueDatePicker && customerWithBalance != null) {
        val now = System.currentTimeMillis()
        val c = customerWithBalance!!
        AlertDialog(
            onDismissRequest = { showDueDatePicker = false },
            title = { Text("Set Payment Due Date") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Select when ${c.name} promised to clear the balance:",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedButton(
                        onClick = {
                            viewModel.updateDueDate(c.id, now + 3 * 86400000L) {
                                showDueDatePicker = false
                                Toast.makeText(context, "Due date set: In 3 days", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("+3 Days")
                    }
                    OutlinedButton(
                        onClick = {
                            viewModel.updateDueDate(c.id, now + 7 * 86400000L) {
                                showDueDatePicker = false
                                Toast.makeText(context, "Due date set: In 1 week", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("+7 Days (1 Week)")
                    }
                    OutlinedButton(
                        onClick = {
                            viewModel.updateDueDate(c.id, now + 15 * 86400000L) {
                                showDueDatePicker = false
                                Toast.makeText(context, "Due date set: In 15 days", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("+15 Days")
                    }
                    OutlinedButton(
                        onClick = {
                            viewModel.updateDueDate(c.id, now + 30 * 86400000L) {
                                showDueDatePicker = false
                                Toast.makeText(context, "Due date set: In 30 days", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("+30 Days (Next Month)")
                    }
                    if (c.dueDate != null) {
                        TextButton(
                            onClick = {
                                viewModel.updateDueDate(c.id, null) {
                                    showDueDatePicker = false
                                    Toast.makeText(context, "Due date cleared", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Clear Due Date")
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showDueDatePicker = false }) {
                    Text("Close")
                }
            }
        )
    }
}

// ==========================================
// Sub-components: Stat Box & Ledger Row Cards
// ==========================================

@Composable
fun SummaryStatBox(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier,
    subtitle: String? = null
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(10.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = valueColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
fun BillLedgerItemCard(
    entry: CustomerLedgerEntry.BillPurchase,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onViewFullBill: () -> Unit,
    onSharePdf: () -> Unit,
    onOpenShareModal: () -> Unit,
    onPrintBill: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bill = entry.billWithItems.bill
    val items = entry.billWithItems.items
    val isCancelled = bill.status == BillConstants.STATUS_CANCELLED
    val isPaid = bill.amountPaidPaise >= bill.totalPaise
    val isPartiallyPaid = bill.amountPaidPaise > 0 && bill.amountPaidPaise < bill.totalPaise

    val statusBadgeColor = when {
        isCancelled -> MaterialTheme.colorScheme.outline
        isPaid -> KiranaCreditGreen
        isPartiallyPaid -> Color(0xFFE65100) // Deep Orange
        else -> KiranaDebitRed
    }

    val statusBadgeText = when {
        isCancelled -> stringResource(R.string.bill_status_cancelled)
        isPaid -> stringResource(R.string.bill_status_paid)
        isPartiallyPaid -> stringResource(R.string.bill_status_partially_paid)
        else -> stringResource(R.string.bill_status_credit_unpaid)
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header Row: Receipt #, Status Badge, Date
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ReceiptLong,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "#${bill.receiptNumber}",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = statusBadgeColor.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = statusBadgeText,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            ),
                            color = statusBadgeColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Text(
                    text = DateTimeUtils.formatDateTime(entry.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Main Financial Row: Total, Paid, Unpaid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Total Bill",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = CurrencyFormatter.formatPaise(entry.totalPaise),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Paid",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = CurrencyFormatter.formatPaise(entry.amountPaidPaise),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = KiranaCreditGreen
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Unpaid (Credit)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = CurrencyFormatter.formatPaise(entry.unpaidPaise),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = if (entry.unpaidPaise > 0) KiranaDebitRed else MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Expand/Collapse Inline Item Breakdown Button
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { onToggleExpand() }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val itemCount = items.size
                Text(
                    text = if (itemCount > 0) "$itemCount item(s) in this purchase" else "Quick Amount Bill",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Inline Item Breakdown (Expanded)
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 4.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            shape = RoundedCornerShape(6.dp)
                        )
                        .padding(8.dp)
                ) {
                    if (items.isNotEmpty()) {
                        items.forEach { item ->
                            val qtyFormatted = if (item.quantity % 1.0 == 0.0) item.quantity.toLong().toString() else "%.2f".format(item.quantity)
                            val unitPriceFormatted = CurrencyFormatter.formatPaise(item.unitPricePaise)
                            val lineTotalFormatted = CurrencyFormatter.formatPaise(item.lineTotalPaise)

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${item.description} — $qtyFormatted × $unitPriceFormatted",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = lineTotalFormatted,
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
                                )
                            }
                        }
                    } else {
                        val desc = if (bill.notes.isNotBlank()) bill.notes else "Quick Amount Item"
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = desc, style = MaterialTheme.typography.bodySmall)
                            Text(
                                text = CurrencyFormatter.formatPaise(bill.totalPaise),
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
                            )
                        }
                    }

                    if (bill.discountPaise > 0) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Discount Applied:",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "-${CurrencyFormatter.formatPaise(bill.discountPaise)}",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = KiranaCreditGreen
                            )
                        }
                    }
                }
            }

            // Action Buttons Strip: View Full Bill, PDF, Share, Print
            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onViewFullBill,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp)
                ) {
                    Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(2.dp))
                    Text("View", fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }

                OutlinedButton(
                    onClick = onSharePdf,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp)
                ) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(2.dp))
                    Text("PDF", fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }

                OutlinedButton(
                    onClick = onOpenShareModal,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(2.dp))
                    Text("Share", fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }

                OutlinedButton(
                    onClick = onPrintBill,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp)
                ) {
                    Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(2.dp))
                    Text("Print", fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
fun PaymentLedgerItemCard(
    entry: CustomerLedgerEntry.Payment,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = KiranaCreditGreen.copy(alpha = 0.12f),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.ArrowDownward,
                            contentDescription = null,
                            tint = KiranaCreditGreen,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Payment Received",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = entry.paymentMethod,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                    if (entry.description.isNotBlank()) {
                        Text(
                            text = entry.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = DateTimeUtils.formatDateTime(entry.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "+${CurrencyFormatter.formatPaise(entry.amountPaise)}",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = KiranaCreditGreen
                )
                Text(
                    text = "Tap to share",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun DirectUdhaarLedgerItemCard(
    entry: CustomerLedgerEntry.DirectUdhaar,
    onClick: () -> Unit,
    onReverse: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = KiranaDebitRed.copy(alpha = 0.12f),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.ArrowUpward,
                            contentDescription = null,
                            tint = KiranaDebitRed,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Column {
                    Text(
                        text = stringResource(R.string.direct_udhaar),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    if (entry.description.isNotBlank()) {
                        Text(
                            text = entry.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = DateTimeUtils.formatDateTime(entry.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "-${CurrencyFormatter.formatPaise(entry.amountPaise)}",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = KiranaDebitRed
                )
                Text(
                    text = "Reverse",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.clickable { onReverse() }
                )
            }
        }
    }
}

@Composable
fun ReversalLedgerItemCard(
    entry: CustomerLedgerEntry.Reversal,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Column {
                    Text(
                        text = stringResource(R.string.reversal_entry),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    if (entry.description.isNotBlank()) {
                        Text(
                            text = "Reason: ${entry.description}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    entry.targetTransactionId?.let { targetId ->
                        Text(
                            text = "Reversed Txn #$targetId",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = DateTimeUtils.formatDateTime(entry.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
            }

            Text(
                text = CurrencyFormatter.formatPaise(entry.amountPaise),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

// ==========================================
// Dialogs: Bill Detail, Custom Date Range
// ==========================================

@Composable
fun BillDetailDialog(
    billWithItems: BillWithItems,
    onDismiss: () -> Unit,
    onSharePdf: () -> Unit,
    onPrint: () -> Unit
) {
    val bill = billWithItems.bill
    val items = billWithItems.items
    val customer = billWithItems.customer

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
        ) {
            Column(
                modifier = Modifier
                    .padding(18.dp)
                    .fillMaxWidth()
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.bill_details),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "#${bill.receiptNumber}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // Metadata
                Text(
                    text = "Date: ${DateTimeUtils.formatDateTime(bill.createdAt)}",
                    style = MaterialTheme.typography.bodySmall
                )
                customer?.let {
                    Text(
                        text = "Customer: ${it.name} (${it.phone})",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Text(
                    text = "Payment Mode: ${bill.paymentMethod} | Status: ${bill.status}",
                    style = MaterialTheme.typography.bodySmall
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Items list
                Text(
                    text = "Purchased Items:",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(4.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (items.isNotEmpty()) {
                        items.forEach { item ->
                            val qtyFormatted = if (item.quantity % 1.0 == 0.0) item.quantity.toLong().toString() else "%.2f".format(item.quantity)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${item.description} ($qtyFormatted × ${CurrencyFormatter.formatPaise(item.unitPricePaise)})",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = CurrencyFormatter.formatPaise(item.lineTotalPaise),
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
                                )
                            }
                        }
                    } else {
                        val desc = if (bill.notes.isNotBlank()) bill.notes else "Quick Amount Goods"
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = desc, style = MaterialTheme.typography.bodySmall)
                            Text(
                                text = CurrencyFormatter.formatPaise(bill.totalPaise),
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Totals
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.subtotal), style = MaterialTheme.typography.bodySmall)
                    Text(CurrencyFormatter.formatPaise(bill.subtotalPaise), style = MaterialTheme.typography.bodySmall)
                }
                if (bill.discountPaise > 0) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.discount), style = MaterialTheme.typography.bodySmall, color = KiranaCreditGreen)
                        Text("-${CurrencyFormatter.formatPaise(bill.discountPaise)}", style = MaterialTheme.typography.bodySmall, color = KiranaCreditGreen)
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.total_bill), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    Text(CurrencyFormatter.formatPaise(bill.totalPaise), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.amount_received_label), style = MaterialTheme.typography.bodySmall, color = KiranaCreditGreen)
                    Text(CurrencyFormatter.formatPaise(bill.amountPaidPaise), style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold), color = KiranaCreditGreen)
                }
                val unpaid = bill.totalPaise - bill.amountPaidPaise
                if (unpaid > 0) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.unpaid_balance), style = MaterialTheme.typography.bodySmall, color = KiranaDebitRed)
                        Text(CurrencyFormatter.formatPaise(unpaid), style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = KiranaDebitRed)
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                // Actions: Share PDF & Thermal Print
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onSharePdf,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            stringResource(R.string.share_pdf),
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Button(
                        onClick = onPrint,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            stringResource(R.string.print_receipt),
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CustomDateRangeDialog(
    onDismiss: () -> Unit,
    onApply: (Long, Long) -> Unit
) {
    val defaultStart = LocalDate.now().minusDays(30).toString()
    val defaultEnd = LocalDate.now().toString()

    var startText by remember { mutableStateOf(defaultStart) }
    var endText by remember { mutableStateOf(defaultEnd) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.custom_date_range_title), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Enter dates in YYYY-MM-DD format:",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = startText,
                    onValueChange = {
                        startText = it
                        errorMsg = null
                    },
                    label = { Text(stringResource(R.string.from_date)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = endText,
                    onValueChange = {
                        endText = it
                        errorMsg = null
                    },
                    label = { Text(stringResource(R.string.to_date)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (errorMsg != null) {
                    Text(
                        text = errorMsg!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    try {
                        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
                        val start = LocalDate.parse(startText.trim(), formatter)
                        val end = LocalDate.parse(endText.trim(), formatter)
                        if (start.isAfter(end)) {
                            errorMsg = "Start date must be before or equal to End date"
                            return@Button
                        }
                        val zone = ZoneId.systemDefault()
                        val startEpoch = start.atStartOfDay(zone).toInstant().toEpochMilli()
                        val endEpoch = end.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1L
                        onApply(startEpoch, endEpoch)
                    } catch (e: Exception) {
                        errorMsg = "Invalid date format. Use YYYY-MM-DD (e.g. 2026-10-01)"
                    }
                }
            ) {
                Text(stringResource(R.string.apply))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

private fun formatPeriodText(summary: CustomerFinancialSummary): String {
    val formatter = DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH)
    val zone = ZoneId.systemDefault()
    return when (summary.dateFilter) {
        LedgerDateFilter.ALL_TIME -> "Showing: All time customer history"
        LedgerDateFilter.LAST_30_DAYS -> "Showing: Last 30 days"
        LedgerDateFilter.LAST_3_MONTHS -> "Showing: Last 3 months"
        LedgerDateFilter.LAST_YEAR -> "Showing: Last 1 year"
        LedgerDateFilter.CUSTOM -> {
            val s = java.time.Instant.ofEpochMilli(summary.startEpoch).atZone(zone).toLocalDate().format(formatter)
            val e = java.time.Instant.ofEpochMilli(summary.endEpoch.coerceAtMost(System.currentTimeMillis())).atZone(zone).toLocalDate().format(formatter)
            "Showing: $s to $e"
        }
    }
}

private fun buildStatementShareText(customerName: String, summary: CustomerFinancialSummary): String {
    return "Account statement for $customerName:\n" +
            "• Total Purchases: ${CurrencyFormatter.formatPaise(summary.totalPurchasesPaise)}\n" +
            "• Payments Received: ${CurrencyFormatter.formatPaise(summary.paymentsReceivedPaise)}\n" +
            "• Current Outstanding Balance: ${CurrencyFormatter.formatPaise(summary.currentOutstandingBalancePaise)}\n" +
            "Thank you for your business!"
}
