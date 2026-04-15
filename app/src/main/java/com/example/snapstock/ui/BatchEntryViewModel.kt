package com.example.snapstock.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.snapstock.data.AppDatabase
import com.example.snapstock.data.AppSettings
import com.example.snapstock.data.ClothingItem
import com.example.snapstock.data.SettingsRepository
import com.example.snapstock.data.TodoEntry
import com.example.snapstock.utils.CURRENT_SIGNATURE_VERSION
import com.example.snapstock.utils.DualEngineSignatureExtractor
import com.example.snapstock.utils.SignatureCodec
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

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
    data class Success(val savedCount: Int, val todoCount: Int = 0) : BatchSaveEvent
    data class Error(val message: String) : BatchSaveEvent
}

class BatchEntryViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppDatabase.getDatabase(application).clothingItemDao()
    private val todoDao = AppDatabase.getDatabase(application).todoEntryDao()
    private val settingsRepository = SettingsRepository(application)
    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private var currentSettings = AppSettings()

    private val _uiState = MutableStateFlow(BatchEntryUiState())
    val uiState: StateFlow<BatchEntryUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<BatchSaveEvent>()
    val events: SharedFlow<BatchSaveEvent> = _events.asSharedFlow()

    val categoryOptions: StateFlow<List<String>> = combine(
        settingsRepository.settingsFlow,
        dao.getAllItems()
    ) { settings, items ->
        mergeCategoryOptions(settings, items)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsRepository.PRESET_CATEGORIES
    )

    private val _shouldShowFirstScanTutorial = MutableStateFlow(
        prefs.getBoolean(KEY_SHOW_FIRST_SCAN_TUTORIAL, true)
    )
    val shouldShowFirstScanTutorial: StateFlow<Boolean> = _shouldShowFirstScanTutorial.asStateFlow()

    private var nextLocalId: Int = 1
    private var pendingRemoval: PendingRemoval? = null

    private data class PendingRemoval(
        val draft: BatchDraft,
        val index: Int
    )

    init {
        viewModelScope.launch {
            settingsRepository.settingsFlow.collect { settings ->
                currentSettings = settings
            }
        }
    }

    fun startNewSession() {
        commitLastRemoval()
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
            category = currentSettings.defaultCategory,
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

    fun removeDraftAt(index: Int): Int? {
        val state = _uiState.value
        if (index !in state.drafts.indices) return null

        commitLastRemoval()

        val drafts = state.drafts.toMutableList()
        val removedDraft = drafts.removeAt(index)
        pendingRemoval = PendingRemoval(draft = removedDraft, index = index)
        _uiState.value = state.copy(drafts = drafts)

        if (drafts.isEmpty()) return null
        return index.coerceAtMost(drafts.lastIndex)
    }

    fun undoLastRemoval(): Int? {
        val pending = pendingRemoval ?: return null
        val state = _uiState.value
        val drafts = state.drafts.toMutableList()
        val insertIndex = pending.index.coerceIn(0, drafts.size)
        drafts.add(insertIndex, pending.draft)
        _uiState.value = state.copy(drafts = drafts)
        pendingRemoval = null
        return insertIndex
    }

    fun commitLastRemoval() {
        val pending = pendingRemoval ?: return
        runCatching { File(pending.draft.imagePath).delete() }
        pendingRemoval = null
    }

    fun updateDraft(
        localId: Int,
        name: String? = null,
        priceInput: String? = null,
        quantityInput: String? = null,
        category: String? = null
    ) {
        val sanitizedPrice = priceInput?.let(::sanitizePriceInput)
        val sanitizedQuantity = quantityInput?.let(::sanitizeQuantityInput)
        val sanitizedCategory = category?.trim()?.takeIf { it.isNotBlank() }

        _uiState.update { state ->
            state.copy(
                drafts = state.drafts.map { draft ->
                    if (draft.localId != localId) return@map draft
                    draft.copy(
                        name = name ?: draft.name,
                        priceInput = sanitizedPrice ?: draft.priceInput,
                        quantityInput = sanitizedQuantity ?: draft.quantityInput,
                        category = sanitizedCategory ?: draft.category
                    )
                }
            )
        }
    }

    fun addCustomCategory(category: String) {
        val normalized = category.trim()
        if (normalized.isBlank()) return

        viewModelScope.launch {
            settingsRepository.addCustomCategory(normalized)
        }
    }

    fun saveBatch(createTodo: Boolean = false) {
        commitLastRemoval()
        val state = _uiState.value
        if (state.drafts.isEmpty()) {
            viewModelScope.launch { _events.emit(BatchSaveEvent.Error("Capture at least one item first.")) }
            return
        }
        _uiState.update { it.copy(isSaving = true) }

        viewModelScope.launch {
            try {
                val now = System.currentTimeMillis()
                val appContext = getApplication<Application>().applicationContext
                val entities = state.drafts.map { draft ->
                    val signature = extractSignatureSafely(appContext, draft.imagePath)

                    val name = draft.name.trim().ifBlank { "Pending Item ${draft.localId}" }
                    val price = draft.priceInput.toDoubleOrNull()?.takeIf { it > 0.0 } ?: 0.0
                    val quantity = draft.quantityInput.toIntOrNull()?.takeIf { it > 0 } ?: 1

                    ClothingItem(
                        name = name,
                        price = price,
                        quantity = quantity,
                        category = draft.category,
                        imagePath = draft.imagePath,
                        patternHash = null,
                        visualEmbedding = SignatureCodec.encodeEmbedding(signature?.embedding),
                        ocrText = signature?.ocrText?.takeIf { it.isNotBlank() },
                        ocrTokens = SignatureCodec.encodeTokens(signature?.ocrTokens ?: emptySet()),
                        signatureVersion = signature?.signatureVersion ?: CURRENT_SIGNATURE_VERSION,
                        dateAdded = now
                    )
                }

                val insertedIds = dao.insertItems(entities).map { it.toInt() }
                if (createTodo) {
                    todoDao.insertTodoEntry(
                        TodoEntry(
                            title = "Complete batch details",
                            itemIdsCsv = insertedIds.joinToString(","),
                            createdAt = now,
                            completed = false
                        )
                    )
                }
                nextLocalId = 1
                _uiState.value = BatchEntryUiState()
                _events.emit(BatchSaveEvent.Success(savedCount = entities.size))
            } catch (_: Exception) {
                _uiState.update { it.copy(isSaving = false) }
                _events.emit(BatchSaveEvent.Error("Batch save failed. Please try again."))
            }
        }
    }

    fun saveBatchWithTodo() {
        commitLastRemoval()
        val state = _uiState.value
        if (state.drafts.isEmpty()) {
            viewModelScope.launch { _events.emit(BatchSaveEvent.Error("Capture at least one item first.")) }
            return
        }

        _uiState.update { it.copy(isSaving = true) }

        viewModelScope.launch {
            try {
                val now = System.currentTimeMillis()
                val appContext = getApplication<Application>().applicationContext
                val completeDrafts = state.drafts.filter(::isDraftComplete)
                val incompleteDrafts = state.drafts.filterNot(::isDraftComplete)

                val entities = buildList {
                    completeDrafts.forEach { draft ->
                        add(buildIndexedItem(appContext, draft, now))
                    }
                }

                val insertedIds = if (entities.isNotEmpty()) {
                    dao.insertItems(entities).map { it.toInt() }
                } else {
                    emptyList()
                }

                if (incompleteDrafts.isNotEmpty()) {
                    todoDao.insertTodoEntry(
                        TodoEntry(
                            title = buildTodoTitle(incompleteDrafts),
                            itemIdsCsv = insertedIds.joinToString(","),
                            createdAt = now,
                            completed = false
                        )
                    )
                }

                nextLocalId = 1
                _uiState.value = BatchEntryUiState()
                _events.emit(
                    BatchSaveEvent.Success(
                        savedCount = entities.size,
                        todoCount = incompleteDrafts.size
                    )
                )
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

    private fun mergeCategoryOptions(settings: AppSettings, items: List<ClothingItem>): List<String> {
        val ordered = linkedMapOf<String, String>()

        fun addCategory(raw: String?) {
            val value = raw?.trim().orEmpty()
            if (value.isBlank()) return
            val key = value.lowercase()
            if (!ordered.containsKey(key)) {
                ordered[key] = value
            }
        }

        SettingsRepository.PRESET_CATEGORIES.forEach(::addCategory)
        addCategory(settings.defaultCategory)
        settings.customCategories.forEach(::addCategory)
        items.forEach { addCategory(it.category) }

        return ordered.values.toList()
    }

    private fun sanitizePriceInput(raw: String): String {
        val filtered = raw.filter { it.isDigit() || it == '.' }
        if (filtered.isEmpty()) return ""

        val firstDotIndex = filtered.indexOf('.')
        if (firstDotIndex < 0) return filtered

        val head = filtered.substring(0, firstDotIndex + 1)
        val tail = filtered.substring(firstDotIndex + 1).replace(".", "")
        return head + tail
    }

    private fun sanitizeQuantityInput(raw: String): String {
        val digitsOnly = raw.filter { it.isDigit() }
        if (digitsOnly.isEmpty()) return ""

        val normalized = digitsOnly.trimStart('0')
        return normalized
    }

    private fun isDraftComplete(draft: BatchDraft): Boolean {
        return draft.name.trim().isNotBlank() &&
            draft.category.trim().isNotBlank() &&
            draft.priceInput.toDoubleOrNull()?.let { it > 0.0 } == true &&
            draft.quantityInput.toIntOrNull()?.let { it > 0 } == true
    }

    private suspend fun buildIndexedItem(
        appContext: Context,
        draft: BatchDraft,
        now: Long
    ): ClothingItem {
        val signature = extractSignatureSafely(appContext, draft.imagePath)

        val name = draft.name.trim().ifBlank { "Pending Item ${draft.localId}" }
        val price = draft.priceInput.toDoubleOrNull()?.takeIf { it > 0.0 } ?: 0.0
        val quantity = draft.quantityInput.toIntOrNull()?.takeIf { it > 0 } ?: 1

        return ClothingItem(
            name = name,
            price = price,
            quantity = quantity,
            category = draft.category,
            imagePath = draft.imagePath,
            patternHash = null,
            visualEmbedding = SignatureCodec.encodeEmbedding(signature?.embedding),
            ocrText = signature?.ocrText?.takeIf { it.isNotBlank() },
            ocrTokens = SignatureCodec.encodeTokens(signature?.ocrTokens ?: emptySet()),
            signatureVersion = signature?.signatureVersion ?: CURRENT_SIGNATURE_VERSION,
            dateAdded = now
        )
    }

    private fun buildTodoTitle(incompleteDrafts: List<BatchDraft>): String {
        val summary = incompleteDrafts.joinToString(separator = ", ") { draft ->
            val missingFields = buildList {
                if (draft.name.trim().isBlank()) add("Name")
                if (draft.category.trim().isBlank()) add("Category")
                if (draft.priceInput.toDoubleOrNull()?.let { it > 0.0 } != true) add("Price")
                if (draft.quantityInput.toIntOrNull()?.let { it > 0 } != true) add("Qty")
            }
            "Item ${draft.localId}: Missing ${missingFields.joinToString(", ")}"
        }

        return "Complete batch details — $summary"
    }

    private suspend fun extractSignatureSafely(context: Context, imagePath: String) =
        try {
            DualEngineSignatureExtractor.extractFromImagePath(context, imagePath)
        } catch (_: Exception) {
            null
        }
}

