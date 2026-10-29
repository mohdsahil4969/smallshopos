package com.syntaxa.khata.feature.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.syntaxa.khata.R
import com.syntaxa.khata.core.model.CurrencyFormatter
import com.syntaxa.khata.core.model.DateTimeUtils
import com.syntaxa.khata.core.ui.components.ActionIconButton
import com.syntaxa.khata.core.ui.components.KiranaCard
import com.syntaxa.khata.core.ui.components.KiranaConfirmDialog
import com.syntaxa.khata.core.ui.theme.KiranaCreditGreen
import com.syntaxa.khata.core.ui.theme.KiranaDebitRed
import com.syntaxa.khata.core.ui.theme.KiranaGreenPrimary
import com.syntaxa.khata.data.local.entity.FinancialTransactionEntity
import com.syntaxa.khata.data.local.entity.TransactionConstants
import com.syntaxa.khata.feature.subscription.SubscriptionManager
import com.syntaxa.khata.feature.subscription.SubscriptionStatus
import com.syntaxa.khata.feature.subscription.SubscriptionTier

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToKhata: () -> Unit,
    onNavigateToBilling: () -> Unit,
    onNavigateToExpenses: () -> Unit,
    onOpenAddCustomerDialog: () -> Unit,
    onOpenAddUdhaarDialog: () -> Unit,
    onOpenReceivePaymentDialog: () -> Unit,
    subscriptionManager: SubscriptionManager? = null,
    onNavigateToPaywall: () -> Unit = {},
    onOpenUserGuide: () -> Unit = {},
    onNavigateToReports: () -> Unit = {},
    onSelectCustomer: (Long) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val shopProfile by viewModel.shopProfile.collectAsState()
    val dailySummary by viewModel.dailySummary.collectAsState()
    val recentTransactions by viewModel.recentTransactions.collectAsState()
    val subStatus by (subscriptionManager?.status ?: remember {
        kotlinx.coroutines.flow.MutableStateFlow(
            SubscriptionStatus(
                isPro = false,
                tier = SubscriptionTier.FREE,
                tierName = "Free Starter",
                expiryDateEpoch = null,
                daysRemaining = 0,
                paymentRef = null,
                deviceId = ""
            )
        )
    }).collectAsState()

    var transactionToReverse by remember { mutableStateOf<FinancialTransactionEntity?>(null) }
    var reversalReason by remember { mutableStateOf("") }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                viewModel.refreshTodayIfNeeded()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp)
    ) {
        // 1. Shop Header Card
        item {
            KiranaCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    ) {
                        Text(
                            text = shopProfile?.shopName?.takeIf { it.isNotBlank() } ?: stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = KiranaGreenPrimary
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = DateTimeUtils.formatDate(System.currentTimeMillis()),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = CircleShape
                        ) {
                            Text(
                                text = stringResource(R.string.offline_mode_badge),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                ),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.5.dp),
                                maxLines = 1
                            )
                        }
                        Surface(
                            color = if (subStatus.isPro) Color(0xFFFEF3C7) else MaterialTheme.colorScheme.surfaceVariant,
                            shape = CircleShape,
                            border = if (subStatus.isPro) BorderStroke(1.dp, Color(0xFFF59E0B)) else null,
                            modifier = Modifier.clickable { onNavigateToPaywall() }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.WorkspacePremium,
                                    contentDescription = "PRO",
                                    tint = if (subStatus.isPro) Color(0xFFB45309) else Color(0xFF6B7280),
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = if (subStatus.isPro) "PRO" else "₹49",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.5.sp
                                    ),
                                    color = if (subStatus.isPro) Color(0xFFB45309) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                        IconButton(
                            onClick = onOpenUserGuide,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.HelpOutline,
                                contentDescription = stringResource(R.string.user_guide_help),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }

        // PRO Plan Ribbon (Visible when user is on PRO)
        if (subStatus.isPro) {
            item {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF0F3E1E),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToPaywall() }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.WorkspacePremium,
                                contentDescription = null,
                                tint = Color(0xFFFDE68A),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Syntaxa PRO Plan Active",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFFDE68A)
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${subStatus.daysRemaining} days remaining • All features unlocked",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 11.sp,
                                        color = Color(0xFFD1FAE5)
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF16A34A).copy(alpha = 0.3f),
                            border = BorderStroke(1.dp, Color(0xFF4ADE80))
                        ) {
                            Text(
                                text = "PRO MEMBER",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.sp,
                                    color = Color(0xFF86EFAC)
                                ),
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }
        }

        // 2. Primary KPI: Total Sales & Market Udhaar Owed (Horizontal Single-Line Numbering)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Today's Sales
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onNavigateToReports() },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = stringResource(R.string.today_sales),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = CurrencyFormatter.formatPaise(dailySummary.totalSalesPaise),
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Market Udhaar Owed
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onNavigateToKhata() },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = stringResource(R.string.outstanding_udhaar),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = CurrencyFormatter.formatPaise(dailySummary.totalMarketUdhaarOwedPaise),
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            ),
                            color = KiranaDebitRed,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // 3. Secondary Cash & Collections Row (Horizontal Numbering)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            ) {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Cash Collected
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "${stringResource(R.string.today_cash)}:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = CurrencyFormatter.formatPaise(dailySummary.totalCashCollectedPaise),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = KiranaCreditGreen,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    // UPI Collected
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "${stringResource(R.string.today_upi)}:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = CurrencyFormatter.formatPaise(dailySummary.totalUpiCollectedPaise),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = KiranaGreenPrimary,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    // Today's Expenses
                    Row(
                        modifier = Modifier.clickable { onNavigateToExpenses() },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "${stringResource(R.string.today_expenses)}:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = CurrencyFormatter.formatPaise(dailySummary.totalExpensesPaise),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }

        // 4. Quick Actions Grid (Clean, Balanced 2x3 Layout)
        item {
            KiranaCard {
                Text(
                    text = stringResource(R.string.quick_actions),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ActionIconButton(
                        icon = Icons.Default.ArrowUpward,
                        label = stringResource(R.string.action_add_udhaar),
                        onClick = onOpenAddUdhaarDialog,
                        backgroundColor = Color(0xFFFFEBEE),
                        iconTint = KiranaDebitRed,
                        modifier = Modifier.weight(1f)
                    )
                    ActionIconButton(
                        icon = Icons.Default.ArrowDownward,
                        label = stringResource(R.string.action_receive_payment),
                        onClick = onOpenReceivePaymentDialog,
                        backgroundColor = Color(0xFFE8F5E9),
                        iconTint = KiranaCreditGreen,
                        modifier = Modifier.weight(1f)
                    )
                    ActionIconButton(
                        icon = Icons.AutoMirrored.Filled.ReceiptLong,
                        label = stringResource(R.string.action_create_bill),
                        onClick = onNavigateToBilling,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ActionIconButton(
                        icon = Icons.Default.PersonAdd,
                        label = stringResource(R.string.action_add_customer),
                        onClick = onOpenAddCustomerDialog,
                        modifier = Modifier.weight(1f)
                    )
                    ActionIconButton(
                        icon = Icons.Default.MoneyOff,
                        label = stringResource(R.string.action_add_expense),
                        onClick = onNavigateToExpenses,
                        modifier = Modifier.weight(1f)
                    )
                    ActionIconButton(
                        icon = Icons.AutoMirrored.Filled.MenuBook,
                        label = stringResource(R.string.nav_khata),
                        onClick = onNavigateToKhata,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // 5. Recent Transactions Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.recent_transactions),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                TextButton(onClick = onNavigateToKhata) {
                    Text(stringResource(R.string.view_all), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        // 6. Recent Transactions List
        if (recentTransactions.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.no_transactions_today),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(
                items = recentTransactions,
                key = { it.id },
                contentType = { "transaction" }
            ) { tx ->
                TransactionItemRow(
                    transaction = tx,
                    onClick = {
                        tx.customerId?.let { onSelectCustomer(it) }
                    },
                    onReverseClick = { transactionToReverse = tx }
                )
            }
        }
    }

    // Reversal confirmation dialog
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
                        label = { Text(stringResource(R.string.reason_optional)) },
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
                        }
                    },
                    modifier = Modifier.defaultMinSize(minHeight = 44.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(
                        text = stringResource(R.string.reverse_transaction),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { transactionToReverse = null },
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

@Composable
fun TransactionItemRow(
    transaction: FinancialTransactionEntity,
    onClick: () -> Unit = {},
    onReverseClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isUdhaar = transaction.type == TransactionConstants.TYPE_UDHAAR_GIVEN
    val isPayment = transaction.type == TransactionConstants.TYPE_PAYMENT_RECEIVED
    val isReversed = transaction.status == TransactionConstants.STATUS_REVERSED

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(enabled = transaction.customerId != null) { onClick() },
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isReversed) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                             else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                isReversed -> Color.LightGray
                                isUdhaar -> Color(0xFFFFEBEE)
                                isPayment -> Color(0xFFE8F5E9)
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when {
                            isReversed -> Icons.AutoMirrored.Filled.Undo
                            isUdhaar -> Icons.Default.ArrowUpward
                            isPayment -> Icons.Default.ArrowDownward
                            else -> Icons.Default.SwapHoriz
                        },
                        contentDescription = null,
                        tint = when {
                            isReversed -> Color.DarkGray
                            isUdhaar -> KiranaDebitRed
                            isPayment -> KiranaCreditGreen
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when {
                            isReversed -> stringResource(R.string.reversal_entry)
                            isUdhaar -> stringResource(R.string.udhaar_given)
                            isPayment -> "${stringResource(R.string.payment_received)} (${transaction.paymentMethod})"
                            else -> transaction.type
                        },
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (transaction.description.isNotBlank()) {
                        Text(
                            text = transaction.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = DateTimeUtils.formatDateTime(transaction.occurredAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = (if (isUdhaar) "+" else if (isPayment) "-" else "") +
                            CurrencyFormatter.formatPaise(transaction.amountPaise),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = when {
                        isReversed -> Color.Gray
                        isUdhaar -> KiranaDebitRed
                        isPayment -> KiranaCreditGreen
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                )

                if (!isReversed && transaction.status == TransactionConstants.STATUS_ACTIVE) {
                    Text(
                        text = stringResource(R.string.reverse_entry_btn),
                        style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.error),
                        modifier = Modifier
                            .clickable { onReverseClick() }
                            .padding(top = 4.dp)
                    )
                }
            }
        }
    }
}
