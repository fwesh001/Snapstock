package com.example.snapstock.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.snapstock.data.AppDatabase
import com.example.snapstock.data.ClothingItem
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BatchDraft(
    val localId: Int,
    val imagePath: String,
    val name: String = "",
    val priceInput: String = "",
    val quantityInput: String = "1",
    val category: String = "Shirts",
    val ocrNameConfident: Boolean = false,
    val ocrPriceConfident: Boolean = false
)

data class BatchEntryUiState(
    val drafts: List<BatchDraft> = emptyList(),
    val isSaving: Boolean = false
) {
    val captureCount: Int
        get() = drafts.size
}

sealed interface BatchSaveEvent {
    data class Success(val savedCount: Int) : BatchSaveEvent
    data class Error(val message: String) : BatchSaveEvent
}

class BatchEntryViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppDatabase.getDatabase(application).clothingItemDao()
    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(BatchEntryUiState())
    val uiState: StateFlow<BatchEntryUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<BatchSaveEvent>()
    val events: SharedFlow<BatchSaveEvent> = _events.asSharedFlow()

    private val _shouldShowFirstScanTutorial = MutableStateFlow(
        prefs.getBoolean(KEY_SHOW_FIRST_SCAN_TUTORIAL, true)
    )
    val shouldShowFirstScanTutorial: StateFlow<Boolean> = _shouldShowFirstScanTutorial.asStateFlow()

    private var nextLocalId: Int = 1

    fun startNewSession() {
        nextLocalId = 1
        _uiState.value = BatchEntryUiState()
    }

    fun addCapturedImage(
        imagePath: String,
        initialName: String = "",
        initialPriceInput: String = "",
        ocrNameConfident: Boolean = false,
        ocrPriceConfident: Boolean = false
    ) {
        val draft = BatchDraft(
            localId = nextLocalId++,
            imagePath = imagePath,
            name = initialName,
            priceInput = initialPriceInput,
            ocrNameConfident = ocrNameConfident,
            ocrPriceConfident = ocrPriceConfident
        )
        _uiState.update { state ->
            state.copy(drafts = state.drafts + draft)
        }
    }

    fun markFirstScanTutorialSeen() {
        if (!_shouldShowFirstScanTutorial.value) return
        prefs.edit().putBoolean(KEY_SHOW_FIRST_SCAN_TUTORIAL, false).apply()
        _shouldShowFirstScanTutorial.value = false
    }

    fun updateDraft(
        localId: Int,
        name: String? = null,
        priceInput: String? = null,
        quantityInput: String? = null,
        category: String? = null
    ) {
        _uiState.update { state ->
            state.copy(
                drafts = state.drafts.map { draft ->
                    if (draft.localId != localId) return@map draft
                    draft.copy(
                        name = name ?: draft.name,
                        priceInput = priceInput ?: draft.priceInput,
                        quantityInput = quantityInput ?: draft.quantityInput,
                        category = category ?: draft.category
                    )
                }
            )
        }
    }

    fun saveBatch() {
        val state = _uiState.value
        if (state.drafts.isEmpty()) {
            viewModelScope.launch { _events.emit(BatchSaveEvent.Error("Capture at least one item first.")) }
            return
        }

        val now = System.currentTimeMillis()
        val entities = mutableListOf<ClothingItem>()

        for (draft in state.drafts) {
            val name = draft.name.trim()
            val price = draft.priceInput.toDoubleOrNull()
            val quantity = draft.quantityInput.toIntOrNull()

            if (name.isBlank()) {
                viewModelScope.launch { _events.emit(BatchSaveEvent.Error("Every item needs a name.")) }
                return
            }
            if (price == null || price <= 0.0) {
                viewModelScope.launch { _events.emit(BatchSaveEvent.Error("Every item needs a valid price.")) }
                return
            }
            if (quantity == null || quantity <= 0) {
                viewModelScope.launch { _events.emit(BatchSaveEvent.Error("Every item needs a valid quantity.")) }
                return
            }

            entities += ClothingItem(
                name = name,
                price = price,
                quantity = quantity,
                category = draft.category,
                imagePath = draft.imagePath,
                patternHash = null,
                dateAdded = now
            )
        }

        _uiState.update { it.copy(isSaving = true) }

        viewModelScope.launch {
            try {
                dao.insertItems(entities)
                nextLocalId = 1
                _uiState.value = BatchEntryUiState()
                _events.emit(BatchSaveEvent.Success(savedCount = entities.size))
            } catch (_: Exception) {
                _uiState.update { it.copy(isSaving = false) }
                _events.emit(BatchSaveEvent.Error("Batch save failed. Please try again."))
            }
        }
    }

    companion object {
        private const val PREFS_NAME = "snapstock_prefs"
        private const val KEY_SHOW_FIRST_SCAN_TUTORIAL = "show_first_scan_tutorial"
    }
}

