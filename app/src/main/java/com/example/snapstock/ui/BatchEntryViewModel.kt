package com.example.snapstock.ui

import android.app.Application
import android.content.Context
import android.graphics.BitmapFactory
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.snapstock.data.AppDatabase
import com.example.snapstock.data.ClothingItem
import com.example.snapstock.utils.OcrExtractor
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
    val category: String = "Shirts"
)

data class BatchEntryUiState(
    val drafts: List<BatchDraft> = emptyList(),
    val isSaving: Boolean = false,
    val enableOcrPrefill: Boolean = true
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

    fun addCapturedImage(imagePath: String) {
        val draft = BatchDraft(localId = nextLocalId++, imagePath = imagePath)
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

        _uiState.update { it.copy(isSaving = true) }

        viewModelScope.launch {
            for (draft in state.drafts) {
                var name = draft.name.trim()
                var price = draft.priceInput

                // Auto-prefill empty fields via OCR if enabled
                if (state.enableOcrPrefill && draft.imagePath.isNotEmpty()) {
                    if (name.isBlank() || price.isBlank()) {
                        try {
                            val bitmap = BitmapFactory.decodeFile(draft.imagePath)
                            if (bitmap != null) {
                                val ocrResult = OcrExtractor.extractTextFromImage(bitmap)
                                if (name.isBlank() && ocrResult.extractedName != null) {
                                    name = ocrResult.extractedName
                                }
                                if (price.isBlank() && ocrResult.extractedPrice != null) {
                                    price = ocrResult.extractedPrice
                                }
                            }
                        } catch (_: Exception) {
                            // OCR failed, continue with current values
                        }
                    }
                }

                val nameVal = name.trim()
                val priceVal = price.toDoubleOrNull()
                val quantity = draft.quantityInput.toIntOrNull()

                if (nameVal.isBlank()) {
                    _uiState.update { it.copy(isSaving = false) }
                    _events.emit(BatchSaveEvent.Error("Every item needs a name."))
                    return@launch
                }
                if (priceVal == null || priceVal <= 0.0) {
                    _uiState.update { it.copy(isSaving = false) }
                    _events.emit(BatchSaveEvent.Error("Every item needs a valid price."))
                    return@launch
                }
                if (quantity == null || quantity <= 0) {
                    _uiState.update { it.copy(isSaving = false) }
                    _events.emit(BatchSaveEvent.Error("Every item needs a valid quantity."))
                    return@launch
                }

                entities += ClothingItem(
                    name = nameVal,
                    price = priceVal,
                    quantity = quantity,
                    category = draft.category,
                    imagePath = draft.imagePath,
                    patternHash = null,
                    dateAdded = now
                )
            }

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

