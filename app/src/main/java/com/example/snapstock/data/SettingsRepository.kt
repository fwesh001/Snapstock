package com.example.snapstock.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private const val SETTINGS_DATASTORE_NAME = "app_settings"
private const val DEFAULT_SHOP_NAME = "SnapStock"
private const val DEFAULT_CURRENCY = "USD"
private const val DEFAULT_CATEGORY = "Shirts"
private const val DEFAULT_GREEN_STOCK_THRESHOLD = 3
private const val DEFAULT_AMBER_STOCK_THRESHOLD = 1

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = SETTINGS_DATASTORE_NAME
)

data class AppSettings(
    val shopName: String = DEFAULT_SHOP_NAME,
    val currencyCode: String = DEFAULT_CURRENCY,
    val defaultCategory: String = DEFAULT_CATEGORY,
    val customCategories: Set<String> = emptySet(),
    val greenStockThreshold: Int = DEFAULT_GREEN_STOCK_THRESHOLD,
    val amberStockThreshold: Int = DEFAULT_AMBER_STOCK_THRESHOLD,
    val hapticFeedbackEnabled: Boolean = true,
    val highOcrSensitivity: Boolean = false,
    val autoSaveBatches: Boolean = false,
    val reducedConfettiEffects: Boolean = false
    val reducedConfettiEffects: Boolean = false
)

class SettingsRepository(private val context: Context) {

    val settingsFlow: Flow<AppSettings> = context.settingsDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            AppSettings(
                shopName = preferences[KEY_SHOP_NAME]?.takeIf { it.isNotBlank() } ?: DEFAULT_SHOP_NAME,
                currencyCode = preferences[KEY_CURRENCY]?.takeIf { it.isNotBlank() } ?: DEFAULT_CURRENCY,
                defaultCategory = preferences[KEY_DEFAULT_CATEGORY]?.takeIf { it.isNotBlank() } ?: DEFAULT_CATEGORY,
                customCategories = preferences[KEY_CUSTOM_CATEGORIES] ?: emptySet(),
                greenStockThreshold = preferences[KEY_GREEN_STOCK_THRESHOLD] ?: DEFAULT_GREEN_STOCK_THRESHOLD,
                amberStockThreshold = preferences[KEY_AMBER_STOCK_THRESHOLD] ?: DEFAULT_AMBER_STOCK_THRESHOLD,
                hapticFeedbackEnabled = preferences[KEY_HAPTIC_FEEDBACK] ?: true,
                highOcrSensitivity = preferences[KEY_HIGH_OCR_SENSITIVITY] ?: false,
                autoSaveBatches = preferences[KEY_AUTO_SAVE_BATCHES] ?: false,
                reducedConfettiEffects = preferences[KEY_REDUCED_CONFETTI_EFFECTS] ?: false
                reducedConfettiEffects = preferences[KEY_REDUCED_CONFETTI_EFFECTS] ?: false
            )
        }

    suspend fun updateShopName(shopName: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_SHOP_NAME] = shopName
        }
    }

    suspend fun updateCurrencyCode(currencyCode: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_CURRENCY] = currencyCode
        }
    }

    suspend fun updateDefaultCategory(defaultCategory: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_DEFAULT_CATEGORY] = defaultCategory
        }
    }

    suspend fun addCustomCategory(category: String) {
        val normalized = category.trim()
        if (normalized.isBlank()) return

        context.settingsDataStore.edit { preferences ->
            val existing = (preferences[KEY_CUSTOM_CATEGORIES] ?: emptySet()).toMutableSet()
            if (existing.none { it.equals(normalized, ignoreCase = true) }) {
                existing.add(normalized)
                preferences[KEY_CUSTOM_CATEGORIES] = existing
            }
        }
    }

    suspend fun updateGreenStockThreshold(threshold: Int) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_GREEN_STOCK_THRESHOLD] = threshold
        }
    }

    suspend fun updateAmberStockThreshold(threshold: Int) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_AMBER_STOCK_THRESHOLD] = threshold
        }
    }

    suspend fun updateHapticFeedbackEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_HAPTIC_FEEDBACK] = enabled
        }
    }

    suspend fun updateHighOcrSensitivity(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_HIGH_OCR_SENSITIVITY] = enabled
        }
    }

    suspend fun updateAutoSaveBatches(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_AUTO_SAVE_BATCHES] = enabled
        }
    }

    suspend fun updateReducedConfettiEffects(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_REDUCED_CONFETTI_EFFECTS] = enabled
        }
    }

    suspend fun updateReducedConfettiEffects(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_REDUCED_CONFETTI_EFFECTS] = enabled
        }
    }

    suspend fun resetSettings() {
        context.settingsDataStore.edit { preferences ->
            preferences.clear()
        }
    }

    companion object {

        private val KEY_SHOP_NAME = stringPreferencesKey("shop_name")
        private val KEY_CURRENCY = stringPreferencesKey("currency")
        private val KEY_DEFAULT_CATEGORY = stringPreferencesKey("default_category")
        private val KEY_CUSTOM_CATEGORIES = stringSetPreferencesKey("custom_categories")
        private val KEY_GREEN_STOCK_THRESHOLD = intPreferencesKey("green_stock_threshold")
        private val KEY_AMBER_STOCK_THRESHOLD = intPreferencesKey("amber_stock_threshold")
        private val KEY_HAPTIC_FEEDBACK = booleanPreferencesKey("haptic_feedback_enabled")
        private val KEY_HIGH_OCR_SENSITIVITY = booleanPreferencesKey("high_ocr_sensitivity")
        private val KEY_AUTO_SAVE_BATCHES = booleanPreferencesKey("auto_save_batches")
        private val KEY_REDUCED_CONFETTI_EFFECTS = booleanPreferencesKey("reduced_confetti_effects")
        private val KEY_REDUCED_CONFETTI_EFFECTS = booleanPreferencesKey("reduced_confetti_effects")

        val PRESET_CATEGORIES = listOf("Shirts", "Pants", "Shoes", "Accessories", "Outerwear", "Dresses")
    }
}

