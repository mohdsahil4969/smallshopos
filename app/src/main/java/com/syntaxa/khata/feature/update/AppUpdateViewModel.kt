package com.syntaxa.khata.feature.update

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.syntaxa.khata.data.local.KhataDatabase
import com.syntaxa.khata.feature.backup.BackupManager
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AppUpdateViewModel(application: Application) : AndroidViewModel(application) {
    private val updateService = AppUpdateService(application)
    private val database = KhataDatabase.getInstance(application)
    private val backupManager = BackupManager(application, database)

    val updateState: StateFlow<AppUpdateState> = updateService.updateState

    fun isAutoCheckEnabled(): Boolean = updateService.isAutoCheckEnabled()
    fun setAutoCheckEnabled(enabled: Boolean) = updateService.setAutoCheckEnabled(enabled)

    fun checkForUpdate(isManual: Boolean = false) {
        viewModelScope.launch {
            updateService.checkForUpdate(isManual = isManual)
        }
    }

    fun startOneClickUpdate() {
        val info = updateState.value.updateInfo ?: return
        viewModelScope.launch {
            updateService.executeOneClickUpdate(info, backupManager)
        }
    }

    fun dismissUpdate() {
        updateService.dismissUpdate()
    }

    fun requestInstallPermission() {
        updateService.requestInstallPermission()
    }

    fun resumeInstallAfterPermission(): Boolean {
        return updateService.resumeInstallAfterPermission()
    }
}
