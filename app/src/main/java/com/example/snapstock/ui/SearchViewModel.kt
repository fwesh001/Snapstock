package com.example.snapstock.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.snapstock.data.AppDatabase
import com.example.snapstock.data.ClothingItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview

data class SearchUiState(
    val query: String = "",
    val isSearching: Boolean = false,
    val hasSearched: Boolean = false,
    val results: List<ClothingItem> = emptyList()
)

private data class SearchResultState(
    val isSearching: Boolean = false,
    val hasSearched: Boolean = false,
    val results: List<ClothingItem> = emptyList()
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppDatabase.getDatabase(application).clothingItemDao()

    private val query = MutableStateFlow("")

    private val resultState = query
        .debounce(250)
        .distinctUntilChanged()
        .flatMapLatest { rawQuery ->
            val normalized = rawQuery.trim()
            if (normalized.isBlank()) {
                flowOf(SearchResultState())
            } else {
                dao.searchItems(normalized)
                    .map { items ->
                        SearchResultState(
                            isSearching = false,
                            hasSearched = true,
                            results = items
                        )
                    }
                    .onStart {
                        emit(
                            SearchResultState(
                                isSearching = true,
                                hasSearched = true,
                                results = emptyList()
                            )
                        )
                    }
            }
        }

    val uiState: StateFlow<SearchUiState> = combine(query, resultState) { activeQuery, searchState ->
        SearchUiState(
            query = activeQuery,
            isSearching = searchState.isSearching,
            hasSearched = searchState.hasSearched,
            results = searchState.results
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SearchUiState()
    )

    fun onQueryChange(newQuery: String) {
        query.update { newQuery }
    }
}

