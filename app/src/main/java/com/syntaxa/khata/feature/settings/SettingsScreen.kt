package com.syntaxa.khata.feature.settings

import android.bluetooth.BluetoothDevice
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.syntaxa.khata.R
import com.syntaxa.khata.core.audio.AudioSoundboxManager
import com.syntaxa.khata.core.model.DateTimeUtils
import com.syntaxa.khata.core.model.SupportedLanguages
import com.syntaxa.khata.core.ui.components.KiranaCard
import com.syntaxa.khata.core.ui.theme.KiranaDebitRed
import com.syntaxa.khata.core.util.StorageCleanerManager
import com.syntaxa.khata.data.local.entity.ShopProfileEntity
import com.syntaxa.khata.feature.backup.BackupValidationResult
import com.syntaxa.khata.feature.backup.ParsedBackupData
import com.syntaxa.khata.feature.printing.BluetoothPrinterManager
import com.syntaxa.khata.feature.printing.DiscoveredPrinter
import com.syntaxa.khata.feature.printing.PrintResult
import com.syntaxa.khata.feature.update.AppUpdateService
import com.syntaxa.khata.feature.update.AppUpdateViewModel
import com.syntaxa.khata.feature.update.UpdateStatus
import com.syntaxa.khata.feature.subscription.FeatureEntitlement
import com.syntaxa.khata.feature.subscription.SubscriptionManager
import com.syntaxa.khata.feature.subscription.SubscriptionStatus
import com.syntaxa.khata.feature.subscription.SubscriptionTier
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    updateViewModel: AppUpdateViewModel? = null,
    subscriptionManager: SubscriptionManager? = null,
    onNavigateToPaywall: () -> Unit = {},
    onOpenUserGuide: () -> Unit = {},
    onReplayWalkthrough: () -> Unit = {},
    onOpenTerms: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val shopProfile by viewModel.shopProfile.collectAsState()
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

    var shopName by remember(shopProfile) { mutableStateOf(shopProfile?.shopName ?: "") }
    var address by remember(shopProfile) { mutableStateOf(shopProfile?.address ?: "") }
    var phone by remember(shopProfile) { mutableStateOf(shopProfile?.phone ?: "") }
    var upiId by remember(shopProfile) { mutableStateOf(shopProfile?.upiId ?: "") }
    var footerMsg by remember(shopProfile) { mutableStateOf(shopProfile?.receiptFooter ?: "") }
    var selectedLanguage by remember(shopProfile) { mutableStateOf(shopProfile?.language ?: "en") }

    // Printer state
    var pairedDevices by remember { mutableStateOf<List<DiscoveredPrinter>>(emptyList()) }
    var selectedPrinter by remember { mutableStateOf<DiscoveredPrinter?>(null) }
    var paperWidth by remember { mutableIntStateOf(BluetoothPrinterManager.WIDTH_58MM) }
    var isTestingPrint by remember { mutableStateOf(false) }

    // Virtual Soundbox state
    var isSoundboxEnabled by remember {
        mutableStateOf(try { viewModel.isSoundboxEnabled() } catch (_: Throwable) { true })
    }
    var soundboxLang by remember {
        mutableStateOf(try { viewModel.getSoundboxLanguage() } catch (_: Throwable) { AudioSoundboxManager.LANG_AUTO })
    }

    // Security PIN state
    var isAppLockEnabled by remember {
        mutableStateOf(try { viewModel.isAppLockEnabled() } catch (_: Throwable) { false })
    }

    // Update state
    val updateState by (updateViewModel?.updateState ?: remember { kotlinx.coroutines.flow.MutableStateFlow(com.syntaxa.khata.feature.update.AppUpdateState()) }).collectAsState()
    var autoCheckEnabled by remember {
        mutableStateOf(try { updateViewModel?.isAutoCheckEnabled() ?: true } catch (_: Throwable) { true })
    }
    var isBiometricEnabled by remember {
        mutableStateOf(try { viewModel.isBiometricEnabled() } catch (_: Throwable) { false })
    }
    var showPinDialog by remember { mutableStateOf(false) }
    var pinInput by remember { mutableStateOf("") }
    var pinConfirmInput by remember { mutableStateOf("") }

    // Backup creation state
    var showBackupPasswordDialog by remember { mutableStateOf(false) }
    var backupPasswordInput by remember { mutableStateOf("") }
    var backupPasswordConfirmInput by remember { mutableStateOf("") }
    var pendingBackupPassword by remember { mutableStateOf<String?>(null) }
    var isExportingUnencrypted by remember { mutableStateOf(false) }

    // Restore state
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }
    var pendingRestoreValidation by remember { mutableStateOf<BackupValidationResult?>(null) }
    var pendingRestoreData by remember { mutableStateOf<ParsedBackupData?>(null) }
    var showRestorePasswordDialog by remember { mutableStateOf(false) }
    var restorePasswordInput by remember { mutableStateOf("") }

    // Storage & Cache cleaner state
    var cacheSizeBytes by remember {
        mutableStateOf(StorageCleanerManager.getCacheSizeBytes(context))
    }

    // Crash log diagnostic state
    val crashFile = remember { java.io.File(context.filesDir, "crash_log.txt") }
    var crashLogContent by remember {
        mutableStateOf(if (crashFile.exists()) try { crashFile.readText() } catch (_: Throwable) { null } else null)
    }
    var showCrashDialog by remember { mutableStateOf(false) }

    // SAF Launcher for creating backup
    val createBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            coroutineScope.launch {
                try {
                    context.contentResolver.openOutputStream(it)?.use { stream ->
                        val password = pendingBackupPassword
                        val success = if (isExportingUnencrypted || password == null) {
                            viewModel.exportUnencryptedBackup(stream)
                        } else {
                            viewModel.exportEncryptedBackup(stream, password)
                        }

                        if (success) {
                            val msg = if (isExportingUnencrypted) {
                                context.getString(R.string.backup_success_unencrypted)
                            } else {
                                context.getString(R.string.backup_success_encrypted)
                            }
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Backup failed: ${e.message}", Toast.LENGTH_SHORT).show()
                } finally {
                    pendingBackupPassword = null
                    isExportingUnencrypted = false
                }
            }
        }
    }

    // SAF Launcher for selecting restore file
    val restoreBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { fileUri ->
            coroutineScope.launch {
                try {
                    context.contentResolver.openInputStream(fileUri)?.use { stream ->
                        val inspection = viewModel.inspectBackup(stream)
                        if (!inspection.isValid && inspection.errorMessage != null) {
                            Toast.makeText(context, inspection.errorMessage, Toast.LENGTH_LONG).show()
                            return@launch
                        }

                        pendingRestoreUri = fileUri
                        if (inspection.isEncrypted) {
                            restorePasswordInput = ""
                            showRestorePasswordDialog = true
                        } else {
                            // Unencrypted backup: re-read and parse
                            context.contentResolver.openInputStream(fileUri)?.use { readStream ->
                                val (validation, parsedData) = viewModel.validateAndParseBackup(readStream, null)
                                if (validation.isValid && parsedData != null) {
                                    pendingRestoreValidation = validation
                                    pendingRestoreData = parsedData
                                } else {
                                    Toast.makeText(context, validation.errorMessage ?: context.getString(R.string.backup_integrity_failed), Toast.LENGTH_LONG).show()
                                    pendingRestoreUri = null
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Validation error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        try {
            pairedDevices = viewModel.getPairedPrinters(context)
            selectedPrinter = pairedDevices.firstOrNull()
        } catch (_: Throwable) {
            pairedDevices = emptyList()
            selectedPrinter = null
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp)
    ) {
        // PRO Membership Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (subStatus.isPro) Color(0xFFFEF3C7) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                ),
                border = if (subStatus.isPro) androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFF59E0B)) else null
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.WorkspacePremium,
                                contentDescription = null,
                                tint = if (subStatus.isPro) Color(0xFFB45309) else Color(0xFF6B7280),
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = if (subStatus.isPro) "Syntaxa PRO Active" else "Syntaxa PRO",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = if (subStatus.isPro) Color(0xFF92400E) else MaterialTheme.colorScheme.onSurface
                                    )
                                )
                                Text(
                                    text = if (subStatus.isPro) {
                                        "${subStatus.daysRemaining} days remaining"
                                    } else {
                                        "Upgrade for ₹49 / month"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (subStatus.isPro) Color(0xFFB45309) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Button(
                            onClick = onNavigateToPaywall,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (subStatus.isPro) Color(0xFFD97706) else MaterialTheme.colorScheme.primary
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = if (subStatus.isPro) "Extend" else "Get PRO",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (subStatus.isPro) {
                            "All premium kirana tools unlocked: Bluetooth 58mm/80mm thermal printing, inventory stock deduction, wholesaler ledger, and daily cash reconciler."
                        } else {
                            "Unlock Bluetooth Thermal Printing, Inventory Stock Deduction, Supplier & Wholesaler Khata, and Daily Cash Drawer Reconciler."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (subStatus.isPro) Color(0xFF78350F) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 0. User Guide & Shop Manual Card
        item {
            KiranaCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.user_guide_title),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.user_guide_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.MenuBook,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = onOpenUserGuide,
                    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.open_shop_manual),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = onReplayWalkthrough,
                    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.replay_walkthrough),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = onOpenTerms,
                    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Gavel, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.terms_menu_item_title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // 1. Shop Profile Card
        item {
            KiranaCard {
                Text(
                    text = stringResource(R.string.shop_profile),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = shopName,
                    onValueChange = { shopName = it },
                    label = { Text(stringResource(R.string.shop_name)) },
                    placeholder = { Text("e.g. Sharma General Store") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text(stringResource(R.string.shop_address)) },
                    placeholder = { Text("e.g. Main Market, Near Clock Tower") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text(stringResource(R.string.shop_phone)) },
                    placeholder = { Text("e.g. 9876543210") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = upiId,
                    onValueChange = { upiId = it },
                    label = { Text(stringResource(R.string.upi_id)) },
                    placeholder = { Text("e.g. shopname@upi") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = footerMsg,
                    onValueChange = { footerMsg = it },
                    label = { Text(stringResource(R.string.receipt_footer)) },
                    placeholder = { Text("e.g. Thank you! Visit again.") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        if (shopName.isNotBlank()) {
                            val updated = (shopProfile ?: ShopProfileEntity()).copy(
                                shopName = shopName.trim(),
                                address = address.trim(),
                                phone = phone.trim(),
                                upiId = upiId.trim(),
                                receiptFooter = footerMsg.trim(),
                                language = selectedLanguage
                            )
                            viewModel.updateShopProfile(updated) {
                                Toast.makeText(context, "Shop profile updated", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = stringResource(R.string.save),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // 2. Language & Preferences Card
        item {
            KiranaCard {
                Text(
                    text = stringResource(R.string.language),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(10.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SupportedLanguages.all.forEach { lang ->
                        FilterChip(
                            selected = selectedLanguage.equals(lang.code, ignoreCase = true),
                            onClick = {
                                selectedLanguage = lang.code
                                val current = shopProfile ?: ShopProfileEntity()
                                viewModel.updateShopProfile(current.copy(language = lang.code)) {}
                            },
                            label = {
                                val labelText = if (lang.code == "en") {
                                    lang.englishName
                                } else {
                                    "${lang.nativeName} (${lang.englishName})"
                                }
                                Text(labelText)
                            }
                        )
                    }
                }
            }
        }

        // 3. Bluetooth Thermal Printer Card
        item {
            KiranaCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.bluetooth_printer),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    IconButton(onClick = {
                        pairedDevices = viewModel.getPairedPrinters(context)
                        selectedPrinter = pairedDevices.firstOrNull()
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                if (pairedDevices.isEmpty()) {
                    Text(
                        text = stringResource(R.string.printer_not_connected),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(stringResource(R.string.select_paired_printer), style = MaterialTheme.typography.labelSmall)
                    pairedDevices.forEach { dev ->
                        FilterChip(
                            selected = selectedPrinter?.address == dev.address,
                            onClick = { selectedPrinter = dev },
                            label = { Text(dev.name) },
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(stringResource(R.string.paper_width), style = MaterialTheme.typography.labelSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = paperWidth == BluetoothPrinterManager.WIDTH_58MM,
                            onClick = { paperWidth = BluetoothPrinterManager.WIDTH_58MM },
                            label = { Text(stringResource(R.string.printer_58mm)) }
                        )
                        FilterChip(
                            selected = paperWidth == BluetoothPrinterManager.WIDTH_80MM,
                            onClick = { paperWidth = BluetoothPrinterManager.WIDTH_80MM },
                            label = { Text(stringResource(R.string.printer_80mm)) }
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = {
                            if (subscriptionManager?.canUse(FeatureEntitlement.PDF_THERMAL_PRINTING) == false) {
                                Toast.makeText(context, "Bluetooth Thermal Printing requires Syntaxa PRO (₹49/month)", Toast.LENGTH_SHORT).show()
                                onNavigateToPaywall()
                                return@Button
                            }
                            selectedPrinter?.let { printer ->
                                isTestingPrint = true
                                coroutineScope.launch {
                                    val result = viewModel.testPrint(context, printer.device, paperWidth)
                                    isTestingPrint = false
                                    when (result) {
                                        is PrintResult.Success -> Toast.makeText(context, "Test print successful!", Toast.LENGTH_SHORT).show()
                                        is PrintResult.Failure -> Toast.makeText(context, "Print failed: ${result.message}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        },
                        enabled = !isTestingPrint && selectedPrinter != null,
                        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Print, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.test_print),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (subscriptionManager?.canUse(FeatureEntitlement.PDF_THERMAL_PRINTING) == false) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "PRO",
                                tint = Color(0xFFD97706),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        // 3b. Virtual Soundbox (Audio Payment Confirmation) Card
        item {
            KiranaCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            text = stringResource(R.string.soundbox_title),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.soundbox_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(stringResource(R.string.soundbox_enable_label), fontWeight = FontWeight.SemiBold)
                        Text(
                            text = stringResource(R.string.soundbox_enable_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isSoundboxEnabled,
                        onCheckedChange = { enabled ->
                            isSoundboxEnabled = enabled
                            viewModel.setSoundboxEnabled(enabled)
                        }
                    )
                }

                if (isSoundboxEnabled) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(stringResource(R.string.soundbox_lang_label), style = MaterialTheme.typography.labelSmall)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            AudioSoundboxManager.LANG_AUTO to stringResource(R.string.soundbox_lang_auto),
                            AudioSoundboxManager.LANG_HI to stringResource(R.string.soundbox_lang_hi),
                            AudioSoundboxManager.LANG_EN to stringResource(R.string.soundbox_lang_en)
                        ).forEach { (code, label) ->
                            FilterChip(
                                selected = soundboxLang == code,
                                onClick = {
                                    soundboxLang = code
                                    viewModel.setSoundboxLanguage(code)
                                },
                                label = { Text(label) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = {
                            viewModel.testSoundbox(selectedLanguage)
                        },
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.soundbox_test_btn),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // 4. Security & App Lock Card
        item {
            KiranaCard {
                Text(
                    text = stringResource(R.string.app_lock),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(stringResource(R.string.enable_pin), fontWeight = FontWeight.SemiBold)
                        Text(
                            text = if (isAppLockEnabled) "PIN lock is active (PBKDF2-HMAC-SHA256)" else "Protect app from unauthorized access",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isAppLockEnabled,
                        onCheckedChange = { enable ->
                            if (enable) {
                                showPinDialog = true
                            } else {
                                viewModel.disablePin()
                                isAppLockEnabled = false
                                isBiometricEnabled = false
                                Toast.makeText(context, "App lock disabled", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }

                if (isAppLockEnabled) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.enable_biometrics), style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = isBiometricEnabled,
                            onCheckedChange = {
                                viewModel.setBiometricEnabled(it)
                                isBiometricEnabled = it
                            }
                        )
                    }
                }
            }
        }

        // 5. Backup & Restore Card
        item {
            KiranaCard {
                Text(
                    text = stringResource(R.string.backup_restore),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.backup_notice),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Create Encrypted Backup Button
                Button(
                    onClick = {
                        backupPasswordInput = ""
                        backupPasswordConfirmInput = ""
                        showBackupPasswordDialog = true
                    },
                    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Security, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.create_backup),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Unencrypted Export Button
                OutlinedButton(
                    onClick = {
                        isExportingUnencrypted = true
                        val timestamp = DateTimeUtils.formatReceiptTimestamp(System.currentTimeMillis())
                        try {
                            createBackupLauncher.launch("syntaxa_khata_plain_$timestamp.json")
                        } catch (e: Exception) {
                            Toast.makeText(context, "Cannot open file picker: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.CloudUpload, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.export_unencrypted_backup),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Restore Button
                FilledTonalButton(
                    onClick = {
                        try {
                            restoreBackupLauncher.launch(arrayOf("application/json", "*/*"))
                        } catch (e: Exception) {
                            Toast.makeText(context, "Cannot open file picker: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Restore, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.restore_backup),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // 6. Software Updates & In-App Update Engine Card
        item {
            KiranaCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.software_updates),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(
                                R.string.app_version_label,
                                AppUpdateService.CURRENT_VERSION,
                                AppUpdateService.CURRENT_BUILD_NUMBER
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Text(
                            text = "v${AppUpdateService.CURRENT_VERSION}",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.auto_check_updates),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Switch(
                        checked = autoCheckEnabled,
                        onCheckedChange = {
                            autoCheckEnabled = it
                            updateViewModel?.setAutoCheckEnabled(it)
                        }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        updateViewModel?.checkForUpdate(isManual = true)
                    },
                    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp),
                    shape = RoundedCornerShape(10.dp),
                    enabled = updateState.status != UpdateStatus.CHECKING && updateState.status != UpdateStatus.DOWNLOADING
                ) {
                    if (updateState.status == UpdateStatus.CHECKING) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.checking_updates),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else {
                        Icon(Icons.Default.SystemUpdate, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.check_for_updates),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (updateState.status == UpdateStatus.UP_TO_DATE) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = updateState.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // 7. Storage & Cache Cleaner Card
        item {
            KiranaCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.storage_cleaner_title),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.storage_cleaner_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (cacheSizeBytes > 0L) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Text(
                            text = StorageCleanerManager.formatSize(cacheSizeBytes),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (cacheSizeBytes > 0L) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = {
                        val freed = StorageCleanerManager.cleanTemporaryStorage(context)
                        cacheSizeBytes = StorageCleanerManager.getCacheSizeBytes(context)
                        if (freed > 0L) {
                            val freedStr = StorageCleanerManager.formatSize(freed)
                            Toast.makeText(
                                context,
                                context.getString(R.string.cache_cleaned_toast, freedStr),
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            Toast.makeText(
                                context,
                                context.getString(R.string.storage_already_clean),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.CleaningServices, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.clean_storage_btn),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // 8. System Diagnostic Log Card (only shown if a crash was captured)
        if (!crashLogContent.isNullOrBlank()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.diagnostic_details_title),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.error
                            )
                            IconButton(onClick = {
                                try {
                                    crashFile.delete()
                                    crashLogContent = null
                                    Toast.makeText(context, "Diagnostic log cleared", Toast.LENGTH_SHORT).show()
                                } catch (_: Throwable) {}
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = "Clear Log", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                        Text(
                            text = stringResource(R.string.prev_error_captured),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { showCrashDialog = true },
                            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.BugReport, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.view_error_details),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // 9. Footer: Made by Syntaxa
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.app_made_from_syntaxa),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(
                        R.string.app_version_label,
                        AppUpdateService.CURRENT_VERSION,
                        AppUpdateService.CURRENT_BUILD_NUMBER
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }

    // Set PIN Dialog
    if (showPinDialog) {
        AlertDialog(
            onDismissRequest = { showPinDialog = false },
            title = { Text(stringResource(R.string.enable_pin)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) pinInput = it },
                        label = { Text(stringResource(R.string.enter_pin)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = pinConfirmInput,
                        onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) pinConfirmInput = it },
                        label = { Text(stringResource(R.string.confirm_pin)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (pinInput.length == 4 && pinInput == pinConfirmInput) {
                            viewModel.setPin(pinInput)
                            isAppLockEnabled = true
                            showPinDialog = false
                            pinInput = ""
                            pinConfirmInput = ""
                            Toast.makeText(context, "PIN set successfully", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "PIN must be 4 digits and match", Toast.LENGTH_SHORT).show()
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
                    onClick = {
                        showPinDialog = false
                        pinInput = ""
                        pinConfirmInput = ""
                    },
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

    // Backup Password Creation Dialog
    if (showBackupPasswordDialog) {
        AlertDialog(
            onDismissRequest = { showBackupPasswordDialog = false },
            title = { Text(stringResource(R.string.backup_password_label), fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.set_secure_backup_pwd),
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = backupPasswordInput,
                        onValueChange = { backupPasswordInput = it },
                        label = { Text(stringResource(R.string.backup_password_hint)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = backupPasswordConfirmInput,
                        onValueChange = { backupPasswordConfirmInput = it },
                        label = { Text(stringResource(R.string.backup_password_confirm)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (backupPasswordInput.length >= 12 && backupPasswordInput == backupPasswordConfirmInput) {
                            pendingBackupPassword = backupPasswordInput
                            isExportingUnencrypted = false
                            showBackupPasswordDialog = false
                            val timestamp = DateTimeUtils.formatReceiptTimestamp(System.currentTimeMillis())
                            try {
                                createBackupLauncher.launch("syntaxa_khata_backup_encrypted_$timestamp.json")
                            } catch (e: Exception) {
                                Toast.makeText(context, "Cannot open file picker: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        } else {
                            Toast.makeText(context, "Password must be at least 12 characters and match", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.defaultMinSize(minHeight = 44.dp),
                    shape = RoundedCornerShape(8.dp)
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
                    onClick = { showBackupPasswordDialog = false },
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

    // Restore Password Decryption Dialog
    if (showRestorePasswordDialog) {
        AlertDialog(
            onDismissRequest = {
                showRestorePasswordDialog = false
                pendingRestoreUri = null
            },
            title = { Text(stringResource(R.string.backup_password_restore_title), fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.backup_password_restore_hint),
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = restorePasswordInput,
                        onValueChange = { restorePasswordInput = it },
                        label = { Text(stringResource(R.string.backup_password_label)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val uri = pendingRestoreUri
                        if (uri != null && restorePasswordInput.isNotBlank()) {
                            showRestorePasswordDialog = false
                            coroutineScope.launch {
                                try {
                                    context.contentResolver.openInputStream(uri)?.use { stream ->
                                        val (validation, parsedData) = viewModel.validateAndParseBackup(stream, restorePasswordInput)
                                        if (validation.isValid && parsedData != null) {
                                            pendingRestoreValidation = validation
                                            pendingRestoreData = parsedData
                                        } else {
                                            Toast.makeText(context, validation.errorMessage ?: context.getString(R.string.backup_integrity_failed), Toast.LENGTH_LONG).show()
                                            pendingRestoreUri = null
                                        }
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Decryption error: ${e.message}", Toast.LENGTH_SHORT).show()
                                    pendingRestoreUri = null
                                }
                            }
                        }
                    },
                    modifier = Modifier.defaultMinSize(minHeight = 44.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.unlock_validate),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showRestorePasswordDialog = false
                        pendingRestoreUri = null
                        Toast.makeText(context, context.getString(R.string.restore_cancelled), Toast.LENGTH_SHORT).show()
                    },
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

    // Restore Confirmation Dialog
    pendingRestoreValidation?.let { validation ->
        AlertDialog(
            onDismissRequest = {
                pendingRestoreValidation = null
                pendingRestoreData = null
                pendingRestoreUri = null
                Toast.makeText(context, context.getString(R.string.restore_cancelled), Toast.LENGTH_SHORT).show()
            },
            title = { Text(stringResource(R.string.confirm_restore_title), fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.confirm_restore_warning),
                        color = KiranaDebitRed,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(stringResource(R.string.backup_summary_label), fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.backup_summary_customers, validation.customerCount))
                    Text(stringResource(R.string.backup_summary_txns, validation.transactionCount))
                    Text(stringResource(R.string.backup_summary_bills, validation.billCount))
                    Text(stringResource(R.string.backup_summary_expenses, validation.expenseCount))
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val data = pendingRestoreData
                        if (data != null) {
                            coroutineScope.launch {
                                try {
                                    val success = viewModel.restoreVerifiedData(data)
                                    if (success) {
                                        Toast.makeText(context, context.getString(R.string.restore_success), Toast.LENGTH_LONG).show()
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, context.getString(R.string.restore_failed_preserved), Toast.LENGTH_LONG).show()
                                } finally {
                                    pendingRestoreValidation = null
                                    pendingRestoreData = null
                                    pendingRestoreUri = null
                                }
                            }
                        }
                    },
                    modifier = Modifier.defaultMinSize(minHeight = 44.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(
                        text = stringResource(R.string.proceed_overwrite),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        pendingRestoreValidation = null
                        pendingRestoreData = null
                        pendingRestoreUri = null
                        Toast.makeText(context, context.getString(R.string.restore_cancelled), Toast.LENGTH_SHORT).show()
                    },
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

    // Diagnostic Crash Details Dialog
    if (showCrashDialog && crashLogContent != null) {
        AlertDialog(
            onDismissRequest = { showCrashDialog = false },
            title = { Text(stringResource(R.string.diagnostic_details_title), fontWeight = FontWeight.Bold) },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 350.dp)) {
                    item {
                        Text(
                            text = crashLogContent ?: "",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        try {
                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                            val clip = android.content.ClipData.newPlainText("Syntaxa Crash Log", crashLogContent)
                            clipboard?.setPrimaryClip(clip)
                            Toast.makeText(context, "Log copied to clipboard", Toast.LENGTH_SHORT).show()
                        } catch (_: Throwable) {}
                        showCrashDialog = false
                    },
                    modifier = Modifier.defaultMinSize(minHeight = 44.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.copy_log_btn),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showCrashDialog = false },
                    modifier = Modifier.defaultMinSize(minHeight = 44.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.close),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        )
    }
}
