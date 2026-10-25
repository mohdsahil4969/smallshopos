package com.syntaxa.khata.feature.settings

import android.bluetooth.BluetoothDevice
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.syntaxa.khata.KhataAppContainer
import com.syntaxa.khata.core.security.PinSecurityManager
import com.syntaxa.khata.data.local.entity.ShopProfileEntity
import com.syntaxa.khata.feature.backup.BackupManager
import com.syntaxa.khata.feature.backup.BackupValidationResult
import com.syntaxa.khata.feature.backup.ParsedBackupData
import com.syntaxa.khata.feature.printing.BluetoothPrinterManager
import com.syntaxa.khata.feature.printing.DiscoveredPrinter
import com.syntaxa.khata.feature.printing.PrintResult
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.InputStream
import java.io.OutputStream

class SettingsViewModel(
    private val container: KhataAppContainer,
    private val pinSecurityManager: PinSecurityManager,
    private val backupManager: BackupManager
) : ViewModel() {
    val shopProfile: StateFlow<ShopProfileEntity?> = container.shopProfileRepository
        .observeShopProfile()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun updateShopProfile(profile: ShopProfileEntity, onComplete: () -> Unit) {
        viewModelScope.launch {
            container.shopProfileRepository.updateShopProfile(profile)
            onComplete()
        }
    }

    // App Lock
    fun isAppLockEnabled(): Boolean = pinSecurityManager.isAppLockEnabled()
    fun isBiometricEnabled(): Boolean = pinSecurityManager.isBiometricEnabled()
    fun setBiometricEnabled(enabled: Boolean) = pinSecurityManager.setBiometricEnabled(enabled)
    fun setPin(pin: String) = pinSecurityManager.setPin(pin)
    fun disablePin() = pinSecurityManager.disablePin()
    fun verifyPin(pin: String): Boolean = pinSecurityManager.verifyPin(pin)

    // Bluetooth Printing
    fun getPairedPrinters(context: Context): List<DiscoveredPrinter> {
        return try {
            BluetoothPrinterManager(context).getDiscoveredPrinters()
        } catch (_: Throwable) {
            emptyList()
        }
    }

    suspend fun testPrint(context: Context, device: BluetoothDevice, paperWidth: Int): PrintResult {
        val shop = container.shopProfileRepository.getShopProfile()
        return BluetoothPrinterManager(context).printTestReceipt(device, shop, paperWidth)
    }

    // Backup & Restore
    suspend fun exportEncryptedBackup(outputStream: OutputStream, password: String): Boolean {
        return backupManager.exportEncryptedBackup(outputStream, password)
    }

    suspend fun exportUnencryptedBackup(outputStream: OutputStream): Boolean {
        return backupManager.exportUnencryptedBackup(outputStream)
    }

    suspend fun inspectBackup(inputStream: InputStream): BackupValidationResult {
        return backupManager.inspectBackup(inputStream)
    }

    suspend fun validateAndParseBackup(
        inputStream: InputStream,
        password: String? = null
    ): Pair<BackupValidationResult, ParsedBackupData?> {
        return backupManager.validateAndParseBackup(inputStream, password)
    }

    suspend fun restoreVerifiedData(data: ParsedBackupData): Boolean {
        return backupManager.restoreVerifiedData(data)
    }

    class Factory(
        private val container: KhataAppContainer,
        private val context: Context
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val pinManager = PinSecurityManager(context)
            val backupMgr = BackupManager(context, container.database)
            return SettingsViewModel(container, pinManager, backupMgr) as T
        }
    }
}
