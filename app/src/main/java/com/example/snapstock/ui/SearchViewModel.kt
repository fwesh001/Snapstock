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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.snapstock.utils.ImageMatcher
import com.example.snapstock.utils.ImageSignature
import android.graphics.BitmapFactory

data class SearchUiState(
    val query: String = "",
    val isSearching: Boolean = false,
    val hasSearched: Boolean = false,
    val results: List<ClothingItem> = emptyList(),
    val topMatches: List<ClothingItem> = emptyList(),
    val hasScannedImage: Boolean = false
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
    private val scannedImagePath = MutableStateFlow<String?>(null)
    private val allItems = dao.getAllItems()

    private val signatureCache = mutableMapOf<String, ImageSignature?>()
    private val cacheLock = Any()

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

    val uiState: StateFlow<SearchUiState> = combine(query, resultState, scannedImagePath, allItems) { activeQuery, searchState, imagePath, items ->
        val topMatches = if (imagePath != null) {
            findTopMatches(items, imagePath)
        } else {
            emptyList()
        }

        SearchUiState(
            query = activeQuery,
            isSearching = searchState.isSearching,
            hasSearched = searchState.hasSearched || imagePath != null,
            results = searchState.results.filterNot { topMatches.map { t -> t.id }.contains(it.id) },
            topMatches = topMatches,
            hasScannedImage = imagePath != null
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SearchUiState()
    )

    fun onQueryChange(newQuery: String) {
        query.update { newQuery }
    }

    fun onImageScanned(imagePath: String) {
        scannedImagePath.value = imagePath
    }

    fun clearScannedImage() {
        scannedImagePath.value = null
    }

    private fun findTopMatches(items: List<ClothingItem>, scannedPath: String): List<ClothingItem> {
        val scannedSig = buildSignatureSync(scannedPath) ?: return emptyList()
        return items
            .mapNotNull { item ->
                val itemSig = buildSignatureSync(item.imagePath) ?: return@mapNotNull null
                val distance = ImageMatcher.hammingDistance(scannedSig.averageHash, itemSig.averageHash)
                if (distance <= 14) {
                    item to distance
                } else {
                    null
                }
            }
            .sortedBy { (_, distance) -> distance }
            .take(8)
            .map { (item, _) -> item }
    }

    private fun buildSignatureSync(imagePath: String): ImageSignature? {
        synchronized(cacheLock) {
            if (signatureCache.containsKey(imagePath)) {
                return signatureCache[imagePath]
            }
        }
        val computed = ImageMatcher.buildSignature(imagePath)
        synchronized(cacheLock) {
            signatureCache[imagePath] = computed
        }
        return computed
    }
}

