package com.example.snapstock.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.snapstock.data.AppSettings
import com.example.snapstock.data.SettingsActionsRepository
import com.example.snapstock.data.SettingsMaintenanceResult
import com.example.snapstock.data.SettingsRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import androidx.core.content.FileProvider
import java.io.File

data class SettingsActionState(
    val isBusy: Boolean = false,
    val busyLabel: String = ""
)

sealed interface SettingsSideEffect {
    data class Message(val text: String) : SettingsSideEffect
    data class ShareExport(val uri: Uri, val mimeType: String = "application/zip") : SettingsSideEffect
}

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val settingsRepository = SettingsRepository(application)
    private val actionsRepository = SettingsActionsRepository(application)
    private val appContext = application.applicationContext

    val uiState: StateFlow<AppSettings> = settingsRepository.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppSettings()
    )

    private val _actionState = MutableStateFlow(SettingsActionState())
    val actionState: StateFlow<SettingsActionState> = _actionState

    private val _events = MutableSharedFlow<SettingsSideEffect>()
    val events: SharedFlow<SettingsSideEffect> = _events.asSharedFlow()

    fun updateShopName(shopName: String) {
        viewModelScope.launch {
            settingsRepository.updateShopName(shopName)
        }
    }

    fun updateCurrencyCode(currencyCode: String) {
        viewModelScope.launch {
            settingsRepository.updateCurrencyCode(currencyCode)
        }
    }

    fun updateDefaultCategory(defaultCategory: String) {
        viewModelScope.launch {
            settingsRepository.updateDefaultCategory(defaultCategory)
        }
    }

    fun updateGreenStockThreshold(threshold: Int) {
        viewModelScope.launch {
            settingsRepository.updateGreenStockThreshold(threshold)
        }
    }

    fun updateAmberStockThreshold(threshold: Int) {
        viewModelScope.launch {
            settingsRepository.updateAmberStockThreshold(threshold)
        }
    }

    fun updateHapticFeedbackEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateHapticFeedbackEnabled(enabled)
        }
    }

    fun updateHighOcrSensitivity(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateHighOcrSensitivity(enabled)
        }
    }

    fun updateAutoSaveBatches(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateAutoSaveBatches(enabled)
        }
    }

    fun updateReducedConfettiEffects(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateReducedConfettiEffects(enabled)
        }
    }

    fun updateReducedConfettiEffects(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateReducedConfettiEffects(enabled)
        }
    }

    fun compressImages() {
        runMaintenance("Compressing images") {
            actionsRepository.compressStoredImages()
        }
    }

    fun cleanDatabase() {
        runMaintenance("Cleaning database") {
            actionsRepository.cleanDatabaseAndFiles()
        }
    }

    fun factoryReset() {
        runMaintenance("Resetting app") {
            actionsRepository.runFactoryReset()
        }
    }

    fun exportData() {
        runMaintenance("Preparing export") {
            actionsRepository.exportData()
        }
    }

    private fun runMaintenance(
        label: String,
        block: suspend () -> SettingsMaintenanceResult
    ) {
        viewModelScope.launch {
            _actionState.update { it.copy(isBusy = true, busyLabel = label) }
            try {
                when (val result = block()) {
                    is SettingsMaintenanceResult.Success -> {
                        _events.emit(SettingsSideEffect.Message(result.message))
                    }
                    is SettingsMaintenanceResult.ExportReady -> {
                        val uri = FileProvider.getUriForFile(
                            appContext,
                            "${appContext.packageName}.fileprovider",
                            result.file
                        )
                        _events.emit(SettingsSideEffect.ShareExport(uri))
                        _events.emit(SettingsSideEffect.Message("Export ready to share."))
                    }
                    is SettingsMaintenanceResult.Error -> {
                        _events.emit(SettingsSideEffect.Message(result.message))
                    }
                }
            } finally {
                _actionState.update { it.copy(isBusy = false, busyLabel = "") }
            }
        }
    }
}
