package com.syntaxa.khata.feature.reports

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.syntaxa.khata.R
import com.syntaxa.khata.core.model.CurrencyFormatter
import com.syntaxa.khata.core.ui.components.KiranaCard
import com.syntaxa.khata.core.ui.theme.KiranaCreditGreen
import com.syntaxa.khata.core.ui.theme.KiranaDebitRed
import com.syntaxa.khata.core.ui.theme.KiranaGreenPrimary
import com.syntaxa.khata.feature.receipts.ShareHelper
import com.syntaxa.khata.feature.subscription.FeatureEntitlement
import com.syntaxa.khata.feature.subscription.SubscriptionManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReportsScreen(
    viewModel: ReportsViewModel,
    subscriptionManager: SubscriptionManager? = null,
    onNavigateToPaywall: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val selectedRange by viewModel.dateRange.collectAsState()
    val summary by viewModel.reportSummary.collectAsState()
    var showCashClosingDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp)
    ) {
        // Range Filter Chips
        item {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                FilterChip(
                    selected = selectedRange == ReportDateRange.TODAY,
                    onClick = { viewModel.setDateRange(ReportDateRange.TODAY) },
                    label = { Text(stringResource(R.string.today)) }
                )
                FilterChip(
                    selected = selectedRange == ReportDateRange.LAST_7_DAYS,
                    onClick = { viewModel.setDateRange(ReportDateRange.LAST_7_DAYS) },
                    label = { Text(stringResource(R.string.last_7_days)) }
                )
                FilterChip(
                    selected = selectedRange == ReportDateRange.THIS_MONTH,
                    onClick = { viewModel.setDateRange(ReportDateRange.THIS_MONTH) },
                    label = { Text(stringResource(R.string.this_month)) }
                )
            }
        }

        // Overview Summary
        item {
            KiranaCard {
                Text(
                    text = stringResource(R.string.sales_summary),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(12.dp))

                ReportMetricRow(
                    label = stringResource(R.string.today_sales),
                    value = CurrencyFormatter.formatPaise(summary.totalSalesPaise),
                    isPrimary = true
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                ReportMetricRow(
                    label = stringResource(R.string.today_cash),
                    value = CurrencyFormatter.formatPaise(summary.totalCashCollectedPaise),
                    valueColor = KiranaCreditGreen
                )
                ReportMetricRow(
                    label = stringResource(R.string.today_upi),
                    value = CurrencyFormatter.formatPaise(summary.totalUpiCollectedPaise),
                    valueColor = KiranaGreenPrimary
                )
                if (summary.totalOtherCollectedPaise > 0L) {
                    ReportMetricRow(
                        label = stringResource(R.string.today_other),
                        value = CurrencyFormatter.formatPaise(summary.totalOtherCollectedPaise)
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                ReportMetricRow(
                    label = stringResource(R.string.action_add_udhaar),
                    value = "+${CurrencyFormatter.formatPaise(summary.udhaarGivenTodayPaise)}",
                    valueColor = KiranaDebitRed
                )
                ReportMetricRow(
                    label = stringResource(R.string.action_receive_payment),
                    value = "-${CurrencyFormatter.formatPaise(summary.udhaarRecoveredTodayPaise)}",
                    valueColor = KiranaCreditGreen
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                ReportMetricRow(
                    label = stringResource(R.string.today_expenses),
                    value = "-${CurrencyFormatter.formatPaise(summary.totalExpensesPaise)}",
                    valueColor = KiranaDebitRed
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                ReportMetricRow(
                    label = stringResource(R.string.net_cash_movement),
                    value = CurrencyFormatter.formatPaise(summary.netCashMovementPaise),
                    isPrimary = true,
                    valueColor = if (summary.netCashMovementPaise >= 0) KiranaCreditGreen else KiranaDebitRed
                )
            }
        }

        // Market Udhaar Card
        item {
            KiranaCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.outstanding_udhaar),
                            style = MaterialTheme.typography.labelMedium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = CurrencyFormatter.formatPaise(summary.totalMarketUdhaarOwedPaise),
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = KiranaDebitRed
                            )
                        )
                    }
                }
            }
        }

        // Daily Cash Closing & Export Actions
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = {
                        if (subscriptionManager?.canUse(FeatureEntitlement.DAILY_CASH_CLOSING) == false) {
                            onNavigateToPaywall()
                        } else {
                            showCashClosingDialog = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.AccountBalanceWallet, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.daily_cash_closing_title),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    if (subscriptionManager?.canUse(FeatureEntitlement.DAILY_CASH_CLOSING) == false) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "PRO",
                            tint = Color(0xFFD97706),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Button(
                    onClick = {
                        coroutineScope.launch {
                            try {
                                val csv = viewModel.exportReportCsv(context)
                                ShareHelper.sharePdf(context, csv, "Export Business CSV Report")
                            } catch (e: Exception) {
                                Toast.makeText(context, "Error exporting CSV: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.FileDownload, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.export_csv),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }
        }
    }

    if (showCashClosingDialog) {
        DailyCashClosingDialog(
            viewModel = viewModel,
            onDismiss = { showCashClosingDialog = false },
            onSuccess = {
                showCashClosingDialog = false
                Toast.makeText(context, "Daily cash closing saved successfully!", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

@Composable
private fun ReportMetricRow(
    label: String,
    value: String,
    isPrimary: Boolean = false,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = if (isPrimary) MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                    else MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = if (isPrimary) MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    else MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = valueColor
        )
    }
}
