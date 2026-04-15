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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

data class TodoItemCardModel(
    val todoId: Int,
    val item: ClothingItem
)

class TodoViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppDatabase.getDatabase(application).clothingItemDao()
    private val todoDao = AppDatabase.getDatabase(application).todoEntryDao()

    val pendingTodos: StateFlow<List<TodoEntry>> = todoDao.getPendingTodos().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    val todoItems: StateFlow<List<TodoItemCardModel>> = combine(
        pendingTodos,
        dao.getAllItems()
    ) { todos, items ->
        val itemById = items.associateBy { it.id }
        val cards = mutableListOf<TodoItemCardModel>()

        todos.forEach { todo ->
            parseIds(todo.itemIdsCsv).forEach { itemId ->
                val item = itemById[itemId] ?: return@forEach
                cards += TodoItemCardModel(todoId = todo.id, item = item)
            }
        }

        cards
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    val isLoading: StateFlow<Boolean> = pendingTodos
        .map { false }
        .onStart { emit(true) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = true
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

    fun markItemComplete(itemId: Int) {
        viewModelScope.launch {
            removeItemFromAllTodos(itemId)
        }
    }

    fun deleteTodo(todoId: Int) {
        viewModelScope.launch {
            todoDao.deleteTodo(todoId)
        }
    }

    fun deleteItem(item: ClothingItem) {
        viewModelScope.launch {
            dao.deleteItem(item)
            runCatching { File(item.imagePath).delete() }
            removeItemFromAllTodos(item.id)
        }
    }

    private suspend fun removeItemFromAllTodos(itemId: Int) {
        val todos = todoDao.getAllTodosOnce().filter { !it.completed }
        todos.forEach { todo ->
            val ids = parseIds(todo.itemIdsCsv)
            if (itemId !in ids) return@forEach

            val remaining = ids.filter { it != itemId }
            if (remaining.isEmpty()) {
                todoDao.markCompleted(todo.id)
            } else {
                todoDao.updateTodoEntry(todo.copy(itemIdsCsv = remaining.joinToString(",")))
            }
        }
    }

    private fun parseIds(csv: String): List<Int> {
        return csv.split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .distinct()
    }
}
