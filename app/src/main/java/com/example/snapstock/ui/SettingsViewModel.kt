package com.example.snapstock.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.snapstock.data.AppSettings
import com.example.snapstock.data.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val settingsRepository = SettingsRepository(application)

    val uiState: StateFlow<AppSettings> = settingsRepository.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppSettings()
    )

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
}

