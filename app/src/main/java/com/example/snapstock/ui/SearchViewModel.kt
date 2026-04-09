package com.example.snapstock.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.snapstock.data.AppDatabase
import com.example.snapstock.data.ClothingItem
import com.example.snapstock.utils.ImageMatcher
import com.example.snapstock.utils.ImageSignature
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
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
import kotlinx.coroutines.withContext

data class ScannedImageContext(
    val imagePath: String,
    val signature: ImageSignature
)

data class SearchUiState(
    val query: String = "",
    val isSearching: Boolean = false,
    val hasSearched: Boolean = false,
    val topMatches: List<ClothingItem> = emptyList(),
    val results: List<ClothingItem> = emptyList(),
    val scannedImage: ScannedImageContext? = null
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
    private val scannedImage = MutableStateFlow<ScannedImageContext?>(null)
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

    val uiState: StateFlow<SearchUiState> = combine(
        query,
        resultState,
        scannedImage,
        allItems
    ) { activeQuery, searchState, activeScannedImage, itemPool ->
        val topMatches = findTopVisualMatches(itemPool, activeScannedImage?.signature)
        val topMatchIds = topMatches.map { it.id }.toSet()

        SearchUiState(
            query = activeQuery,
            isSearching = searchState.isSearching,
            hasSearched = searchState.hasSearched || activeScannedImage != null,
            topMatches = topMatches,
            results = searchState.results.filterNot { topMatchIds.contains(it.id) },
            scannedImage = activeScannedImage
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
        val signature = ImageMatcher.buildSignature(imagePath) ?: return
        scannedImage.value = ScannedImageContext(imagePath = imagePath, signature = signature)
    }

    fun clearImageContext() {
        scannedImage.value = null
    }

    private suspend fun findTopVisualMatches(
        items: List<ClothingItem>,
        sourceSignature: ImageSignature?
    ): List<ClothingItem> {
        if (sourceSignature == null) return emptyList()

        return withContext(Dispatchers.Default) {
            items.mapNotNull { item ->
                val signature = resolveSignature(item.imagePath) ?: return@mapNotNull null
                val hashDistance = ImageMatcher.hammingDistance(sourceSignature.averageHash, signature.averageHash)
                val colorDistance = ImageMatcher.colorDistance(sourceSignature.dominantColor, signature.dominantColor)
                val score = (hashDistance * 3) + (colorDistance / 32)
                item to score
            }
                .sortedBy { (_, score) -> score }
                .take(8)
                .map { (item, _) -> item }
        }
    }

    private fun resolveSignature(imagePath: String): ImageSignature? {
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
