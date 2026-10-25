package com.syntaxa.khata.feature.subscription

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.syntaxa.khata.R
import com.syntaxa.khata.core.ui.components.KiranaCard
import com.syntaxa.khata.core.ui.theme.KiranaCreditGreen
import com.syntaxa.khata.core.ui.theme.KiranaGreenPrimary
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionPaywallScreen(
    subscriptionManager: SubscriptionManager,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val status by subscriptionManager.status.collectAsState()

    var utrInput by remember { mutableStateOf("") }
    var offlineCodeInput by remember { mutableStateOf("") }
    var isVerifyingUtr by remember { mutableStateOf(false) }
    var isAutoChecking by remember { mutableStateOf(false) }
    var utrError by remember { mutableStateOf<String?>(null) }
    var offlineCodeError by remember { mutableStateOf<String?>(null) }
    var showSuccessDialog by remember { mutableStateOf(false) }
    var showOfflineTab by remember { mutableStateOf(false) }

    fun startAutoPaymentDetection() {
        if (isAutoChecking) return
        isAutoChecking = true
        coroutineScope.launch {
            for (attempt in 1..6) {
                kotlinx.coroutines.delay(3000L)
                val unlocked = subscriptionManager.checkAndAutoActivatePro()
                if (unlocked) {
                    isAutoChecking = false
                    showSuccessDialog = true
                    return@launch
                }
            }
            isAutoChecking = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Syntaxa PRO",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.close)
                        )
                    }
                },
                actions = {
                    if (status.isPro) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFD1FAE5),
                            modifier = Modifier.padding(end = 12.dp)
                        ) {
                            Text(
                                text = "PRO ACTIVE (${status.daysRemaining}d)",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF065F46),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            )
        },
        modifier = modifier
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 40.dp)
        ) {
            // Auto Checking Indicator Banner (Mirrors shopmanager FamPay Cloud Detection)
            if (isAutoChecking) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFEFF6FF)
                        ),
                        border = BorderStroke(1.5.dp, Color(0xFF3B82F6))
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = Color(0xFF2563EB),
                                strokeWidth = 2.5.dp
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = "Detecting Payment Automatically...",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1E40AF)
                                    )
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Checking FamPay cloud confirmation. Pro will auto-unlock...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF3B82F6)
                                )
                            }
                        }
                    }
                }
            }

            // 1. Hero Card
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF0B2E13)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(68.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        listOf(Color(0xFFF59E0B), Color(0xFFD97706))
                                    )
                                )
                                .border(2.dp, Color(0xFFFDE68A), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = "Upgrade to Syntaxa PRO",
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            ),
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "Just ₹49 / Month • Less than ₹1.7 / day",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFDE68A)
                            ),
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Supercharge your counter with Bluetooth thermal printing, full inventory, supplier ledger, and daily cash reconciler.",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFE2E8F0)),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // 2. Active PRO Banner if already subscribed
            if (status.isPro) {
                item {
                    KiranaCard {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = KiranaCreditGreen,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Your PRO Plan is Active!",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = "${status.daysRemaining} days remaining. Paying again will extend your subscription by +30 days.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // 3. Plan Highlight Card
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(2.dp, KiranaGreenPrimary),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "PRO Monthly Plan",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = "30 Days Complete Store Suite",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFFEF3C7)
                            ) {
                                Text(
                                    text = "BEST VALUE",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = Color(0xFF92400E),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = "₹49",
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = KiranaGreenPrimary
                                )
                            )
                            Text(
                                text = " / 30 days",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 6.dp, start = 4.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.height(14.dp))

                        val proFeatures = listOf(
                            "🖨️ Bluetooth Thermal Printing (58mm & 80mm)",
                            "📦 Simple Inventory & Auto Stock Deduction",
                            "🚚 Supplier / Wholesaler Khata & Dues",
                            "💰 Daily Cash Drawer Closing Reconciler",
                            "🏷️ Custom Shop Watermark on Receipts",
                            "🔒 App PIN Lock & Biometric Protection"
                        )

                        proFeatures.forEach { feature ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = 3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = KiranaGreenPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = feature,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }

            // 4. Step 1: 1-Tap UPI Payment
            item {
                KiranaCard {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("1", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Pay ₹49 via Any UPI App",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "Tap the button below to open Google Pay, PhonePe, Paytm, or BHIM directly:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = {
                                val upiUri = subscriptionManager.buildUpiPaymentUri()
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(upiUri)).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                try {
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "No UPI app found. Please copy the UPI ID below.", Toast.LENGTH_SHORT).show()
                                }
                                startAutoPaymentDetection()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = KiranaGreenPrimary)
                        ) {
                            Icon(Icons.Default.Payment, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Pay ₹49 via UPI App",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Manual UPI ID row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .clickable {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("UPI ID", SubscriptionManager.DEFAULT_UPI_ID))
                                    Toast.makeText(context, "UPI ID copied: ${SubscriptionManager.DEFAULT_UPI_ID}", Toast.LENGTH_SHORT).show()
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Or pay directly to UPI ID:", style = MaterialTheme.typography.labelSmall)
                                Text(
                                    SubscriptionManager.DEFAULT_UPI_ID,
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy UPI ID",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // 5. Step 2: Instant 12-Digit UTR Activation
            item {
                KiranaCard {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("2", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Enter 12-Digit UTR to Activate",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "After paying ₹49, copy the 12-digit UTR / Transaction Reference number from your payment app and paste it below:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = utrInput,
                            onValueChange = {
                                if (it.length <= 12 && it.all { c -> c.isDigit() }) {
                                    utrInput = it
                                    utrError = null
                                }
                            },
                            label = { Text("12-Digit UPI UTR Number") },
                            placeholder = { Text("e.g. 428912345678") },
                            isError = utrError != null,
                            singleLine = true,
                            trailingIcon = {
                                if (utrInput.length == 12) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = KiranaCreditGreen)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (utrError != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = utrError!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = {
                                if (utrInput.length != 12) {
                                    utrError = "Please enter a valid 12-digit numeric UPI UTR number"
                                    return@Button
                                }
                                isVerifyingUtr = true
                                utrError = null
                                coroutineScope.launch {
                                    val result = subscriptionManager.verifyUtrWithServer(utr = utrInput)
                                    isVerifyingUtr = false
                                    when (result) {
                                        is UtrVerificationResult.Success -> {
                                            utrInput = ""
                                            showSuccessDialog = true
                                        }
                                        is UtrVerificationResult.Failure -> {
                                            utrError = result.message
                                        }
                                    }
                                }
                            },
                            enabled = utrInput.length == 12 && !isVerifyingUtr,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (isVerifyingUtr) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Verifying with Google Sheets...")
                            } else {
                                Icon(Icons.Default.CloudDone, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Verify UTR & Activate PRO",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // 6. Step 3: Offline Activation Code Option
            item {
                KiranaCard {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showOfflineTab = !showOfflineTab },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Have an Offline License Code?",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            Icon(
                                imageVector = if (showOfflineTab) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null
                            )
                        }

                        AnimatedVisibility(visible = showOfflineTab) {
                            Column(modifier = Modifier.padding(top = 10.dp)) {
                                Text(
                                    text = "If you purchased a voucher or received a 6-digit license code from support, enter it here:",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                OutlinedTextField(
                                    value = offlineCodeInput,
                                    onValueChange = {
                                        if (it.length <= 6) {
                                            offlineCodeInput = it.uppercase()
                                            offlineCodeError = null
                                        }
                                    },
                                    label = { Text("6-Digit License Code") },
                                    placeholder = { Text("e.g. 7A8B9C") },
                                    isError = offlineCodeError != null,
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                if (offlineCodeError != null) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = offlineCodeError!!,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Button(
                                    onClick = {
                                        val success = subscriptionManager.verifyOfflineCode(offlineCodeInput)
                                        if (success) {
                                            offlineCodeInput = ""
                                            showSuccessDialog = true
                                        } else {
                                            offlineCodeError = "Invalid offline license code for this device."
                                        }
                                    },
                                    enabled = offlineCodeInput.length == 6,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Activate Offline License")
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // Device ID for support
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                        .padding(8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("Your Device ID (for support):", style = MaterialTheme.typography.labelSmall)
                                        Text(
                                            subscriptionManager.hardwareDeviceId,
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                                        )
                                    }
                                    IconButton(onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("Device ID", subscriptionManager.hardwareDeviceId))
                                        Toast.makeText(context, "Device ID copied!", Toast.LENGTH_SHORT).show()
                                    }) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Pro Activation Celebration Dialog
    if (showSuccessDialog) {
        AlertDialog(
            onDismissRequest = {
                showSuccessDialog = false
                onBack()
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = Color(0xFFF59E0B),
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Welcome to Syntaxa PRO! 🎉", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Your subscription has been successfully activated for 30 days.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "• Thermal Bluetooth Printing: Unlocked\n• Inventory & Auto Stock: Unlocked\n• Supplier Khata: Unlocked\n• Daily Cash Closing Reconciler: Unlocked",
                        style = MaterialTheme.typography.bodySmall,
                        color = KiranaGreenPrimary
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSuccessDialog = false
                        onBack()
                    }
                ) {
                    Text("Start Using PRO")
                }
            }
        )
    }
}
