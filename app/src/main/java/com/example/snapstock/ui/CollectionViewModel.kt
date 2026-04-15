package com.example.snapstock.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.snapstock.data.AppDatabase
import com.example.snapstock.data.ClothingItem
import com.example.snapstock.data.TodoEntry
import com.example.snapstock.utils.CURRENT_SIGNATURE_VERSION
import com.example.snapstock.utils.DualEngineSignatureExtractor
import com.example.snapstock.utils.SignatureCodec
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CollectionViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppDatabase.getDatabase(application).clothingItemDao()
    private val todoDao = AppDatabase.getDatabase(application).todoEntryDao()

    val items: StateFlow<List<ClothingItem>> = dao.getAllItems().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    val pendingTodos: StateFlow<List<TodoEntry>> = todoDao.getPendingTodos().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    fun updateItem(item: ClothingItem) {
        viewModelScope.launch {
            val appContext = getApplication<Application>().applicationContext
            val signature = runCatching {
                DualEngineSignatureExtractor.extractFromImagePath(appContext, item.imagePath)
            }.getOrNull()

            val indexed = item.copy(
                visualEmbedding = SignatureCodec.encodeEmbedding(signature?.embedding),
                ocrText = signature?.ocrText?.takeIf { it.isNotBlank() },
                ocrTokens = SignatureCodec.encodeTokens(signature?.ocrTokens ?: emptySet()),
                signatureVersion = signature?.signatureVersion ?: CURRENT_SIGNATURE_VERSION
            )
            dao.updateItem(indexed)
        }
    }
}