package com.syntaxa.khata.feature.subscription

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.syntaxa.khata.R
import com.syntaxa.khata.core.ui.components.KiranaCard
import com.syntaxa.khata.core.ui.theme.KiranaCreditGreen
import com.syntaxa.khata.core.ui.theme.KiranaGreenPrimary
import com.syntaxa.khata.core.util.QrCodeGenerator
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
    var showCancelDialog by remember { mutableStateOf(false) }
    var showOfflineTab by remember { mutableStateOf(false) }
    var autoCheckPromptUtr by remember { mutableStateOf(false) }

    fun checkAndApplyClipboardUtr(): String? {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return null
            if (!clipboard.hasPrimaryClip()) return null
            val clip = clipboard.primaryClip ?: return null
            if (clip.itemCount > 0) {
                val text = clip.getItemAt(0)?.text?.toString()?.trim() ?: return null
                val regex = Regex("""\b\d{12}\b""")
                val match = regex.find(text)
                if (match != null) {
                    val detected = match.value
                    if (!subscriptionManager.isDummyOrInvalidUtr(detected)) {
                        return detected
                    }
                }
            }
        } catch (_: Exception) {}
        return null
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                val detected = checkAndApplyClipboardUtr()
                if (detected != null && utrInput.isBlank()) {
                    utrInput = detected
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    fun startAutoPaymentDetection() {
        if (isAutoChecking) return
        isAutoChecking = true
        autoCheckPromptUtr = false
        coroutineScope.launch {
            for (attempt in 1..8) {
                kotlinx.coroutines.delay(2500L)
                // 1. Check if user copied 12-digit UTR to clipboard while in UPI app
                val clipboardUtr = checkAndApplyClipboardUtr()
                if (clipboardUtr != null && utrInput.isBlank()) {
                    utrInput = clipboardUtr
                    val verifyRes = subscriptionManager.verifyUtrWithServer(utr = clipboardUtr)
                    if (verifyRes is UtrVerificationResult.Success) {
                        isAutoChecking = false
                        showSuccessDialog = true
                        return@launch
                    }
                }
                // 2. Check if server already has an active confirmation for this device
                val unlocked = subscriptionManager.checkAndAutoActivatePro()
                if (unlocked) {
                    isAutoChecking = false
                    showSuccessDialog = true
                    return@launch
                }
            }
            isAutoChecking = false
            autoCheckPromptUtr = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "SmartKhata PRO",
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
                                text = stringResource(R.string.pro_active_days, status.daysRemaining),
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
            // Auto Checking Indicator Banner
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
                                    text = "Listening for UPI Payment...",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1E40AF)
                                    )
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Copy 12-digit UPI UTR in GPay to activate automatically...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF3B82F6)
                                )
                            }
                        }
                    }
                }
            } else if (autoCheckPromptUtr && !status.isPro) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7)),
                        border = BorderStroke(1.5.dp, Color(0xFFF59E0B))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(22.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Payment Completed in GPay / PhonePe?",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = Color(0xFF92400E))
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "NPCI/GPay does not share payment status automatically. Copy the 12-digit UPI Transaction ID from your GPay receipt and tap below to log in Google Sheets & unlock PRO instantly!",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF78350F)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        val detected = checkAndApplyClipboardUtr()
                                        if (detected != null) {
                                            utrInput = detected
                                            utrError = null
                                            Toast.makeText(context, "Pasted UTR: $detected", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "No 12-digit UTR found in clipboard. Copy it from your GPay receipt.", Toast.LENGTH_LONG).show()
                                        }
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Paste from Clipboard", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
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
                            text = "Upgrade to SmartKhata PRO",
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
                                    text = stringResource(R.string.pro_monthly_plan),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = stringResource(R.string.plan_duration_suite),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFFEF3C7)
                            ) {
                                Text(
                                    text = stringResource(R.string.best_value),
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

            // 4. Step 1: Scan QR Code or Open UPI App
            item {
                val upiUri = remember { subscriptionManager.buildUpiPaymentUri() }
                val qrBitmap = remember(upiUri) { QrCodeGenerator.generateQrBitmap(upiUri, 512) }

                fun launchUpiPayment(targetPackage: String? = null) {
                    try {
                        val baseIntent = Intent(Intent.ACTION_VIEW, Uri.parse(upiUri)).apply {
                            if (targetPackage != null) {
                                setPackage(targetPackage)
                            }
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        if (targetPackage == null) {
                            val chooser = Intent.createChooser(baseIntent, "Choose UPI App to Pay ₹49").apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(chooser)
                        } else {
                            context.startActivity(baseIntent)
                        }
                    } catch (e: Exception) {
                        try {
                            val chooserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(upiUri)).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            val chooser = Intent.createChooser(chooserIntent, "Choose UPI App to Pay ₹49").apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(chooser)
                        } catch (_: Exception) {
                            Toast.makeText(
                                context,
                                "No UPI app found. Please scan the QR code or pay manually to ${SubscriptionManager.DEFAULT_UPI_ID}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                    startAutoPaymentDetection()
                }

                KiranaCard {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
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
                                text = stringResource(R.string.pay_via_upi_title),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // QR Code Surface
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color.White,
                            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant),
                            shadowElevation = 2.dp,
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                if (qrBitmap != null) {
                                    Image(
                                        bitmap = qrBitmap.asImageBitmap(),
                                        contentDescription = "Scan to Pay ₹49",
                                        modifier = Modifier.size(190.dp)
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier.size(190.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.QrCodeScanner,
                                            contentDescription = null,
                                            modifier = Modifier.size(64.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Scan with any UPI App",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color.Black
                                )
                                Text(
                                    text = "Google Pay • PhonePe • Paytm • BHIM",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.DarkGray,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Copyable UPI ID Box
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "UPI ID / VPA:",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = SubscriptionManager.DEFAULT_UPI_ID,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        color = KiranaGreenPrimary
                                    )
                                }
                                OutlinedButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("UPI ID", SubscriptionManager.DEFAULT_UPI_ID))
                                        Toast.makeText(context, "UPI ID copied: ${SubscriptionManager.DEFAULT_UPI_ID}", Toast.LENGTH_SHORT).show()
                                    },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Copy", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Or tap to pay directly from your installed app:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Row 1 of App buttons: GPay + PhonePe
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { launchUpiPayment("com.google.android.apps.nbu.paisa.user") },
                                modifier = Modifier.weight(1f).height(46.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(Icons.Default.Payment, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Google Pay", fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
                            }

                            Button(
                                onClick = { launchUpiPayment("com.phonepe.app") },
                                modifier = Modifier.weight(1f).height(46.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = KiranaGreenPrimary)
                            ) {
                                Icon(Icons.Default.Payment, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("PhonePe", fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Row 2 of App buttons: Paytm + Other UPI Apps
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilledTonalButton(
                                onClick = { launchUpiPayment("net.one97.paytm") },
                                modifier = Modifier.weight(1f).height(46.dp),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Payment, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Paytm", fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
                            }

                            OutlinedButton(
                                onClick = { launchUpiPayment(null) },
                                modifier = Modifier.weight(1f).height(46.dp),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Launch, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Any UPI App", fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
                            }
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
                                text = stringResource(R.string.enter_utr_title),
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
                                } else {
                                    IconButton(onClick = {
                                        val detected = checkAndApplyClipboardUtr()
                                        if (detected != null) {
                                            utrInput = detected
                                            utrError = null
                                            Toast.makeText(context, "Pasted UTR: $detected", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "No 12-digit UPI UTR in clipboard. Copy it from your UPI receipt.", Toast.LENGTH_LONG).show()
                                        }
                                    }) {
                                        Icon(Icons.Default.ContentPaste, contentDescription = "Paste from Clipboard", tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Found in GPay -> 'UPI transaction ID'",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            TextButton(
                                onClick = {
                                    val detected = checkAndApplyClipboardUtr()
                                    if (detected != null) {
                                        utrInput = detected
                                        utrError = null
                                        Toast.makeText(context, "Pasted UTR: $detected", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "No 12-digit UPI UTR in clipboard. Copy it from your UPI receipt.", Toast.LENGTH_LONG).show()
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Paste from Clipboard", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

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
                                .defaultMinSize(minHeight = 48.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            if (isVerifyingUtr) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.verifying_with_server), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            } else {
                                Icon(Icons.Default.CloudDone, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Verify UTR & Activate PRO",
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
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
                                text = stringResource(R.string.have_offline_code),
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
                                        if (it.length <= 16) {
                                            offlineCodeInput = it.uppercase()
                                            offlineCodeError = null
                                        }
                                    },
                                    label = { Text("License / Promo Code") },
                                    placeholder = { Text("e.g. SYN-9X8K2P") },
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
                                        val clean = offlineCodeInput.trim().uppercase()
                                        if (clean == "CANCEL" || clean == "RESET" || clean == "REVOKE") {
                                            subscriptionManager.resetToFree()
                                            offlineCodeInput = ""
                                            Toast.makeText(context, "Subscription cancelled. Reverted to Free plan.", Toast.LENGTH_LONG).show()
                                            return@Button
                                        }
                                        val success = subscriptionManager.verifyOfflineCode(offlineCodeInput)
                                        if (success) {
                                            offlineCodeInput = ""
                                            showSuccessDialog = true
                                        } else {
                                            offlineCodeError = "Invalid offline license or promo code."
                                        }
                                    },
                                    enabled = offlineCodeInput.length >= 5,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .defaultMinSize(minHeight = 48.dp),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text(stringResource(R.string.activate_license), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }

                                if (status.isPro) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedButton(
                                        onClick = { showCancelDialog = true },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .defaultMinSize(minHeight = 48.dp),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = MaterialTheme.colorScheme.error
                                        ),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                                    ) {
                                        Icon(Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(stringResource(R.string.cancel_subscription_btn), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
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
                    Text(stringResource(R.string.welcome_pro_title), fontWeight = FontWeight.Bold)
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
                    },
                    modifier = Modifier.defaultMinSize(minHeight = 44.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(stringResource(R.string.start_using_pro), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        )
    }

    // Cancel Subscription Confirmation Dialog
    if (showCancelDialog) {
        AlertDialog(
            onDismissRequest = { showCancelDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.cancel_pro_title), fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Text(
                    text = "Are you sure you want to cancel your PRO subscription? Your plan will be immediately reverted to the Free tier, and PRO features will be locked.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        subscriptionManager.resetToFree()
                        showCancelDialog = false
                        Toast.makeText(context, "Subscription cancelled. Reverted to Free plan.", Toast.LENGTH_LONG).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.defaultMinSize(minHeight = 44.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(stringResource(R.string.yes_cancel_sub), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showCancelDialog = false },
                    modifier = Modifier.defaultMinSize(minHeight = 44.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(stringResource(R.string.keep_active), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        )
    }
}
