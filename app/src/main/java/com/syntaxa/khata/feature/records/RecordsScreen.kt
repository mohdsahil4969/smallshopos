package com.syntaxa.khata.feature.records

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.syntaxa.khata.R
import com.syntaxa.khata.feature.billing.BillingScreen
import com.syntaxa.khata.feature.billing.BillingViewModel
import com.syntaxa.khata.feature.expenses.ExpenseScreen
import com.syntaxa.khata.feature.expenses.ExpenseViewModel
import com.syntaxa.khata.feature.inventory.InventoryScreen
import com.syntaxa.khata.feature.inventory.InventoryViewModel
import com.syntaxa.khata.feature.reports.ReportsScreen
import com.syntaxa.khata.feature.reports.ReportsViewModel
import com.syntaxa.khata.feature.subscription.FeatureEntitlement
import com.syntaxa.khata.feature.subscription.ProFeatureGate
import com.syntaxa.khata.feature.subscription.SubscriptionManager
import com.syntaxa.khata.feature.suppliers.SupplierScreen
import com.syntaxa.khata.feature.suppliers.SupplierViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordsScreen(
    billingViewModel: BillingViewModel,
    expenseViewModel: ExpenseViewModel,
    reportsViewModel: ReportsViewModel,
    inventoryViewModel: InventoryViewModel,
    supplierViewModel: SupplierViewModel,
    subscriptionManager: SubscriptionManager? = null,
    onNavigateToPaywall: () -> Unit = {},
    initialTab: Int = 0,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember(initialTab) { mutableIntStateOf(initialTab) }

    Column(modifier = modifier.fillMaxSize()) {
        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            edgePadding = 8.dp
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text(stringResource(R.string.action_create_bill), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                icon = { Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = null) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text(stringResource(R.string.expenses), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                icon = { Icon(Icons.Default.MoneyOff, contentDescription = null) }
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = { Text(stringResource(R.string.stock_inventory), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                icon = { Icon(Icons.Default.Inventory2, contentDescription = null) }
            )
            Tab(
                selected = selectedTab == 3,
                onClick = { selectedTab = 3 },
                text = { Text(stringResource(R.string.suppliers), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                icon = { Icon(Icons.Default.LocalShipping, contentDescription = null) }
            )
            Tab(
                selected = selectedTab == 4,
                onClick = { selectedTab = 4 },
                text = { Text(stringResource(R.string.reports), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                icon = { Icon(Icons.Default.BarChart, contentDescription = null) }
            )
        }

        Box(modifier = Modifier.fillMaxSize()) {
            when (selectedTab) {
                0 -> BillingScreen(viewModel = billingViewModel)
                1 -> ExpenseScreen(viewModel = expenseViewModel)
                2 -> {
                    if (subscriptionManager != null) {
                        ProFeatureGate(
                            subscriptionManager = subscriptionManager,
                            entitlement = FeatureEntitlement.FULL_INVENTORY,
                            featureTitle = "Inventory & Stock Tracking",
                            featureDescription = "Manage your shop catalog, set custom units, and automatically deduct stock whenever an item is billed at your counter.",
                            icon = Icons.Default.Inventory2,
                            onUpgradeClick = onNavigateToPaywall,
                            benefits = listOf(
                                "Automatic inventory deduction on billing",
                                "Low stock warning badges & threshold alerts",
                                "Barcode lookup & quick catalog search"
                            )
                        ) {
                            InventoryScreen(viewModel = inventoryViewModel)
                        }
                    } else {
                        InventoryScreen(viewModel = inventoryViewModel)
                    }
                }
                3 -> {
                    if (subscriptionManager != null) {
                        ProFeatureGate(
                            subscriptionManager = subscriptionManager,
                            entitlement = FeatureEntitlement.SUPPLIERS_PURCHASES,
                            featureTitle = "Supplier & Wholesaler Khata",
                            featureDescription = "Keep your vendor purchases and distributor payable dues strictly isolated from your customer receivables.",
                            icon = Icons.Default.LocalShipping,
                            onUpgradeClick = onNavigateToPaywall,
                            benefits = listOf(
                                "Distributor credit & payable dues ledger",
                                "Wholesale purchase invoice logging",
                                "Supplier statement PDF generation"
                            )
                        ) {
                            SupplierScreen(viewModel = supplierViewModel)
                        }
                    } else {
                        SupplierScreen(viewModel = supplierViewModel)
                    }
                }
                4 -> ReportsScreen(
                    viewModel = reportsViewModel,
                    subscriptionManager = subscriptionManager,
                    onNavigateToPaywall = onNavigateToPaywall
                )
            }
        }
    }
}
