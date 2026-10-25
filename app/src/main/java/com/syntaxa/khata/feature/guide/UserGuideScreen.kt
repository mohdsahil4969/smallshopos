package com.syntaxa.khata.feature.guide

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.syntaxa.khata.R
import com.syntaxa.khata.core.ui.components.KiranaCard
import com.syntaxa.khata.core.ui.theme.KiranaGreenPrimary

data class GuideSectionItem(
    val title: String,
    val details: String,
    val steps: List<String> = emptyList(),
    val proTip: String? = null
)

data class GuideCategory(
    val id: String,
    val titleEn: String,
    val titleHi: String,
    val icon: ImageVector,
    val items: List<GuideSectionItem>
)

val guideCategories = listOf(
    GuideCategory(
        id = "setup",
        titleEn = "Shop Setup & Getting Started",
        titleHi = "दुकान सेटअप और शुरुआत",
        icon = Icons.Default.Storefront,
        items = listOf(
            GuideSectionItem(
                title = "Configuring Shop Profile & UPI ID",
                details = "Set your Shop Name, Address, Contact Phone, and Shop UPI ID in the Settings tab. Your Shop Name and UPI ID will automatically appear on all generated PDF receipts and Bluetooth thermal printouts.",
                steps = listOf(
                    "Open 'More' tab from bottom navigation",
                    "Enter your Shop Name, Address, Phone, and UPI ID (e.g. shopname@upi)",
                    "Add an optional receipt footer note (e.g. 'धन्यवाद! फिर पधारें')",
                    "Tap 'Save' to apply changes"
                ),
                proTip = "Adding your UPI ID enables automatic QR code generation on bills and customer payment reminder messages."
            ),
            GuideSectionItem(
                title = "Language Selection (English / हिन्दी)",
                details = "Syntaxa Khata provides 100% bilingual support for English and Hindi. You can switch languages at any time from the 'More' tab under 'Language / भाषा'.",
                steps = listOf(
                    "Navigate to 'More' -> 'Language / भाषा'",
                    "Select 'English' or 'हिन्दी (Hindi)'",
                    "All screen titles, buttons, receipts, and reminder templates switch instantly"
                )
            )
        )
    ),
    GuideCategory(
        id = "khata",
        titleEn = "Customer Khata & Udhaar Management",
        titleHi = "ग्राहक खाता और उधारी प्रबंधन",
        icon = Icons.AutoMirrored.Filled.MenuBook,
        items = listOf(
            GuideSectionItem(
                title = "Adding a Customer & Recording Credit",
                details = "Keep track of credit given to regular customers with exact paisa accuracy and zero math errors.",
                steps = listOf(
                    "Tap 'Give Udhaar' (उधार दें) on the Home Screen",
                    "Select an existing customer or tap '+ Add Customer'",
                    "Enter the amount in Rupees and an optional item note (e.g. '2L Oil, 5kg Sugar')",
                    "Tap 'Confirm & Save' to record the credit"
                ),
                proTip = "Always add a phone number when creating a customer to enable one-tap WhatsApp payment reminders."
            ),
            GuideSectionItem(
                title = "Collecting Customer Payments",
                details = "When a customer visits your counter to pay off their dues:",
                steps = listOf(
                    "Tap 'Receive Payment' (जमा लें) on the Home Screen",
                    "Select the customer",
                    "Enter the payment amount and payment mode (Cash, UPI, or Other)",
                    "Tap 'Confirm & Save'—the customer's balance updates instantly"
                )
            ),
            GuideSectionItem(
                title = "Setting Due Dates & Recovery Filters",
                details = "Prioritize who to collect from first using customer due dates.",
                steps = listOf(
                    "Open a customer's profile from the Khata tab",
                    "Tap 'Set Due Date' and pick a quick preset (+3 Days, +7 Days, +15 Days, +30 Days)",
                    "On the Customer List, use the filter chips: 'Overdue' (समय सीमा समाप्त) and 'Due Today' (आज देय)",
                    "Tap 'Send Reminder' to send a polite WhatsApp message with balance and UPI ID"
                ),
                proTip = "Customers who receive polite payment reminders on the due date are 70% more likely to settle accounts on time."
            ),
            GuideSectionItem(
                title = "Correcting Mistakes with Reversals",
                details = "If you enter a wrong amount, you can reverse the transaction safely without silently altering the audit trail.",
                steps = listOf(
                    "Open customer profile and locate the incorrect transaction",
                    "Tap the 'Reverse' (निरस्त करें) icon",
                    "Enter a reason for reversal (e.g. 'Typo in amount')",
                    "A compensating reversal entry is logged, restoring the correct customer balance"
                )
            )
        )
    ),
    GuideCategory(
        id = "billing",
        titleEn = "Quick & Itemized Billing",
        titleHi = "तेज़ बिलिंग और रसीदें",
        icon = Icons.AutoMirrored.Filled.ReceiptLong,
        items = listOf(
            GuideSectionItem(
                title = "Quick Total vs Itemized Mode",
                details = "Syntaxa Khata supports two billing modes tailored for counter speed:",
                steps = listOf(
                    "Quick Total Mode: Simply type the total amount (e.g. ₹250) for fast rush-hour billing without selecting items",
                    "Itemized Mode: Search and pick items from your catalog, enter quantity, rate, and discounts",
                    "Split Payment: Enter amounts received via Cash, UPI, and assign any remaining balance to Customer Udhaar",
                    "Tap 'Save & Print' or 'Share PDF'"
                ),
                proTip = "Credit sales require selecting an existing customer so the unpaid balance is automatically posted to their ledger."
            ),
            GuideSectionItem(
                title = "Bill Cancellation & Stock Return",
                details = "If a customer cancels an order or returns goods, cancelling the bill automatically restores deducted inventory.",
                steps = listOf(
                    "Go to 'Records' tab -> 'Bills'",
                    "Find the bill and tap 'Cancel Bill'",
                    "Confirm cancellation—deducted stock items are automatically credited back to your inventory"
                )
            )
        )
    ),
    GuideCategory(
        id = "inventory",
        titleEn = "Stock & Inventory Management",
        titleHi = "स्टॉक और माल का हिसाब",
        icon = Icons.Default.Inventory2,
        items = listOf(
            GuideSectionItem(
                title = "Adding Products to Catalog",
                details = "Track stock levels, cost prices, and selling prices for packaged goods.",
                steps = listOf(
                    "Navigate to 'Records' tab -> 'Stock' tab",
                    "Tap '+ Add Product'",
                    "Enter item name, unit (kg, pcs, ltr, etc.), selling price, purchase cost, and low-stock alert threshold",
                    "Tap 'Save'"
                ),
                proTip = "Catalog setup is optional! You can continue using Quick Total bills even without setting up a product catalog."
            ),
            GuideSectionItem(
                title = "Low Stock Alerts & Stock Adjustments",
                details = "Identify items running low before they run out.",
                steps = listOf(
                    "In the 'Stock' tab, tap the 'Low Stock Only' filter chip to see items below their threshold",
                    "To adjust physical inventory, tap 'Adjust Stock' on any item",
                    "Choose operation (+ Add Stock or - Reduce Stock), enter quantity, and select a reason (Purchase, Damaged, Theft, Return, Audit)",
                    "Tap 'Save Adjustment' to log the change"
                )
            )
        )
    ),
    GuideCategory(
        id = "suppliers",
        titleEn = "Wholesale Suppliers & Payables",
        titleHi = "थोक सप्लायर और डिस्ट्रीब्यूटर",
        icon = Icons.Default.LocalShipping,
        items = listOf(
            GuideSectionItem(
                title = "Recording Distributor Purchases & Payments",
                details = "Keep money owed to distributors strictly separated from customer receivables.",
                steps = listOf(
                    "Go to 'Records' tab -> 'Suppliers' tab",
                    "Add wholesale distributors with their name, phone, and company",
                    "Tap '+ Record Purchase' to record goods bought on credit or cash",
                    "Tap '+ Record Payment' when settling invoices via Cash, UPI, or Bank Transfer",
                    "Tap 'View Statement' to see the complete transaction history"
                ),
                proTip = "Supplier balances represent money you owe (payables), whereas Customer Khata represents money owed to you (receivables)."
            )
        )
    ),
    GuideCategory(
        id = "closing",
        titleEn = "Daily Cash Drawer Closing (Rokad)",
        titleHi = "दैनिक रोकड़ मिलान (दुकान बंद करते समय)",
        icon = Icons.Default.AccountBalanceWallet,
        items = listOf(
            GuideSectionItem(
                title = "Evening Cash Drawer Reconciliation",
                details = "Reconcile register cash every evening to catch discrepancies and prevent cashier loss.",
                steps = listOf(
                    "Navigate to 'Records' tab -> 'Reports' tab",
                    "Tap 'Close Daily Cash Drawer'",
                    "Enter morning Opening Cash (the cash float left in the drawer at start of day)",
                    "Review Expected Cash calculated by formula: Opening Cash + (Cash Sales + Udhaar Cash Collected) - Cash Expenses",
                    "Count physical register cash and enter Counted Cash",
                    "Review Difference badge: Balanced (matched), Surplus (extra cash), or Shortage (missing cash)",
                    "Add notes if needed and tap 'Save Closing Record'"
                ),
                proTip = "UPI payments go straight to your bank account, so they are displayed separately and excluded from drawer physical cash."
            )
        )
    ),
    GuideCategory(
        id = "security",
        titleEn = "App Lock, PIN & Backups",
        titleHi = "सुरक्षा, पिन लॉक और बैकअप",
        icon = Icons.Default.Security,
        items = listOf(
            GuideSectionItem(
                title = "Setting 4-Digit Security PIN & Biometrics",
                details = "Prevent unauthorized access to your shop's accounts.",
                steps = listOf(
                    "Go to 'More' tab -> 'App Lock'",
                    "Toggle 'Enable PIN' and enter a 4-digit security PIN",
                    "Toggle 'Use Biometrics' to unlock via fingerprint or face recognition",
                    "Persistent throttling locks out repeated failed attempts automatically"
                )
            ),
            GuideSectionItem(
                title = "Creating Encrypted Backups & Device Transfer",
                details = "Back up your entire shop database safely using authenticated AES-256-GCM encryption.",
                steps = listOf(
                    "Go to 'More' tab -> 'Backup & Restore'",
                    "Tap 'Create Encrypted Backup' and set a password (min 12 characters)",
                    "Save the encrypted file to Google Drive or internal storage",
                    "On a new phone, install Syntaxa Khata, select 'Restore from Backup', enter your password, and your data is restored"
                ),
                proTip = "Export an encrypted backup every Sunday to Google Drive. Your financial records will remain safe even if your phone is lost or damaged."
            )
        )
    ),
    GuideCategory(
        id = "printing",
        titleEn = "Bluetooth Thermal Printing & Updates",
        titleHi = "थर्मल प्रिंटर और ऐप अपडेट",
        icon = Icons.Default.Print,
        items = listOf(
            GuideSectionItem(
                title = "Connecting 58mm / 80mm Bluetooth Printers",
                details = "Print crisp physical receipts directly from your phone counter.",
                steps = listOf(
                    "Pair your Bluetooth thermal printer in Android Bluetooth Settings first",
                    "Open Syntaxa Khata -> 'More' tab -> 'Bluetooth Printer'",
                    "Select your paired printer and choose paper width (58mm mini or 80mm wide)",
                    "Tap 'Test Print' to verify the connection"
                )
            ),
            GuideSectionItem(
                title = "One-Tap Serverless Updates",
                details = "Keep your app updated without relying on the Google Play Store.",
                steps = listOf(
                    "Go to 'More' tab -> 'Software Updates'",
                    "Tap 'Check for Updates'",
                    "If an update is available, review the release notes and tap 'Update Now'",
                    "The app creates an encrypted pre-update safety backup automatically, validates the APK signature, and prompts installation"
                )
            )
        )
    ),
    GuideCategory(
        id = "faqs",
        titleEn = "Frequently Asked Questions (FAQs)",
        titleHi = "अक्सर पूछे जाने वाले सवाल (FAQs)",
        icon = Icons.AutoMirrored.Filled.HelpOutline,
        items = listOf(
            GuideSectionItem(
                title = "Does this app require an internet connection?",
                details = "No! Syntaxa Khata is 100% offline-first. All customer records, billing, printing, inventory, expenses, and PDF receipts work completely offline without an internet connection."
            ),
            GuideSectionItem(
                title = "What happens if I change my phone?",
                details = "Simply export an Encrypted Backup from the 'More' tab and save it to Google Drive. When you install Syntaxa Khata on your new phone, tap 'Restore from Backup', enter your backup password, and all your records will be restored seamlessly."
            ),
            GuideSectionItem(
                title = "Is my financial or customer data uploaded anywhere?",
                details = "Never. Syntaxa Khata does not have cloud databases, analytics SDKs, or ad trackers. Your data is stored locally in an encrypted Room SQLite database on your own device."
            )
        )
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserGuideScreen(
    onBack: () -> Unit,
    onReplayWalkthrough: () -> Unit,
    onOpenTerms: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var expandedCategories by remember { mutableStateOf(setOf<String>()) }

    val filteredCategories = remember(searchQuery) {
        if (searchQuery.isBlank()) {
            guideCategories
        } else {
            val q = searchQuery.trim().lowercase()
            guideCategories.mapNotNull { category ->
                val matchesCategory = category.titleEn.lowercase().contains(q) ||
                        category.titleHi.lowercase().contains(q)
                val matchingItems = category.items.filter { item ->
                    item.title.lowercase().contains(q) ||
                            item.details.lowercase().contains(q) ||
                            item.steps.any { it.lowercase().contains(q) } ||
                            (item.proTip?.lowercase()?.contains(q) == true)
                }
                if (matchesCategory || matchingItems.isNotEmpty()) {
                    category.copy(items = if (matchesCategory && matchingItems.isEmpty()) category.items else matchingItems)
                } else null
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.user_guide_title),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = onReplayWalkthrough) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.replay_walkthrough),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 40.dp)
        ) {
            // Search Bar
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(stringResource(R.string.search_guide_placeholder)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Interactive Walkthrough Banner Card
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onReplayWalkthrough() }
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.MenuBook,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.replay_walkthrough),
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.user_guide_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            // Legal Terms of Service & Software Disclaimer Quick Tile
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenTerms() }
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Gavel,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.terms_menu_item_title),
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.terms_menu_item_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                        }
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Categorized Guide Items
            items(filteredCategories, key = { it.id }) { category ->
                val isExpanded = expandedCategories.contains(category.id) || searchQuery.isNotEmpty()

                ElevatedCard(
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Category Header Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    expandedCategories = if (isExpanded) {
                                        expandedCategories - category.id
                                    } else {
                                        expandedCategories + category.id
                                    }
                                }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = category.icon,
                                contentDescription = null,
                                tint = KiranaGreenPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = category.titleEn,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = category.titleHi,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Expanded Content
                        AnimatedVisibility(
                            visible = isExpanded,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                                    .padding(bottom = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                                category.items.forEach { item ->
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Text(
                                            text = item.title,
                                            style = MaterialTheme.typography.titleSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = KiranaGreenPrimary
                                            )
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = item.details,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )

                                        if (item.steps.isNotEmpty()) {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            item.steps.forEachIndexed { idx, stepText ->
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 2.dp),
                                                    verticalAlignment = Alignment.Top
                                                ) {
                                                    Surface(
                                                        shape = RoundedCornerShape(4.dp),
                                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                                        modifier = Modifier.padding(top = 2.dp)
                                                    ) {
                                                        Text(
                                                            text = "${idx + 1}",
                                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        text = stepText,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }

                                        if (item.proTip != null) {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(8.dp),
                                                    verticalAlignment = Alignment.Top
                                                ) {
                                                    Icon(
                                                        Icons.Default.Lightbulb,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = item.proTip,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
