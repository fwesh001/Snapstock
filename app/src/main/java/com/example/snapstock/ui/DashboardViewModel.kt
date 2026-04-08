package com.example.snapstock.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.snapstock.data.AppDatabase
import com.example.snapstock.data.ClothingItem
import com.example.snapstock.data.TodoEntry
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class DashboardUiState(
    val totalItems: Int = 0,
    val lowStockCount: Int = 0,
    val totalInventoryValue: Double = 0.0,
    val lastAddedItem: ClothingItem? = null,
    val pendingTodos: List<TodoEntry> = emptyList(),
    val isEmpty: Boolean = true
)

class DashboardViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppDatabase.getDatabase(application).clothingItemDao()
    private val todoDao = AppDatabase.getDatabase(application).todoEntryDao()

    val uiState: StateFlow<DashboardUiState> = combine(
        dao.getAllItems(),
        dao.getLastAddedItem(),
        todoDao.getPendingTodos()
    ) { items, lastAddedItem, pendingTodos ->
        DashboardUiState(
            totalItems = items.size,
            lowStockCount = items.count { it.quantity <= 5 },
            totalInventoryValue = items.sumOf { it.price * it.quantity },
            lastAddedItem = lastAddedItem,
            pendingTodos = pendingTodos,
            isEmpty = items.isEmpty()
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DashboardUiState()
    )
}
