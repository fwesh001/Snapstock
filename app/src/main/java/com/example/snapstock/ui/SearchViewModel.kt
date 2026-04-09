package com.example.snapstock.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.snapstock.data.AppDatabase
import com.example.snapstock.data.ClothingItem
import com.example.snapstock.utils.ImageMatcher
import com.example.snapstock.utils.ImageSignature
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

data class ScannedImageContext(
    val imagePath: String,
    val signature: ImageSignature
)

data class SearchUiState(
    val query: String = "",
    val isSearching: Boolean = false,
    val hasSearched: Boolean = false,
    val results: List<ClothingItem> = emptyList(),
    val selectedColorHex: String? = null,
    val similarItems: List<ClothingItem> = emptyList(),
    val scannedImages: List<ScannedImageContext> = emptyList(),
    val hasImageContext: Boolean = false
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
    private val selectedColorHex = MutableStateFlow<String?>(null)
    private val scannedImages = MutableStateFlow<List<ScannedImageContext>>(emptyList())
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
        selectedColorHex,
        scannedImages,
        allItems
    ) { activeQuery, searchState, activeColorHex, activeImages, itemPool ->
        val activeSignatures = activeImages.map { it.signature }
        val normalized = activeQuery.trim()
        val sourceResults = if (normalized.isBlank()) itemPool else searchState.results
        val colorFiltered = filterByColor(sourceResults, activeColorHex)
        val similarItems = findSimilarItems(itemPool, activeSignatures)
        val similarIds = similarItems.map { it.id }.toSet()

        SearchUiState(
            query = activeQuery,
            isSearching = searchState.isSearching,
            hasSearched = searchState.hasSearched || activeColorHex != null || activeSignatures.isNotEmpty(),
            results = colorFiltered.filterNot { similarIds.contains(it.id) },
            selectedColorHex = activeColorHex,
            similarItems = similarItems,
            scannedImages = activeImages,
            hasImageContext = activeSignatures.isNotEmpty()
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SearchUiState()
    )

    fun onQueryChange(newQuery: String) {
        query.update { newQuery }
    }

    fun onScannerTextDetected(scannedText: String) {
        val normalized = scannedText.trim()
        if (normalized.isNotBlank()) {
            query.update { normalized }
        }
    }

    fun onImagesScanned(imagePaths: List<String>) {
        viewModelScope.launch(Dispatchers.Default) {
            val newContexts = imagePaths.mapNotNull { path ->
                val sig = ImageMatcher.buildSignature(path)
                sig?.let { ScannedImageContext(path, it) }
            }
            if (newContexts.isNotEmpty()) {
                val current = scannedImages.value.toMutableList()
                current.addAll(newContexts)
                scannedImages.value = current
                
                if (selectedColorHex.value == null) {
                    val combinedColors = current.map { it.signature.dominantColor }
                    val avgColorValue = combinedColors.first() // For simplicity, take the first new dominant color
                    selectedColorHex.value = ImageMatcher.colorToHex(avgColorValue)
                }
            }
        }
    }

    fun onScannerImageRemoved(index: Int) {
        val currentList = scannedImages.value.toMutableList()
        if (index in currentList.indices) {
            currentList.removeAt(index)
            scannedImages.value = currentList
            if (currentList.isEmpty()) {
                clearImageContext()
            }
        }
    }

    fun onColorFilterChange(colorHex: String?) {
        selectedColorHex.value = colorHex
    }

    fun clearImageContext() {
        scannedImages.value = emptyList()
        selectedColorHex.value = null
    }

    private suspend fun filterByColor(items: List<ClothingItem>, colorHex: String?): List<ClothingItem> {
        val targetColor = ImageMatcher.parseColorHex(colorHex) ?: return items
        return withContext(Dispatchers.Default) {
            items.filter { item ->
                val signature = resolveSignature(item.imagePath) ?: return@filter false
                ImageMatcher.colorDistance(signature.dominantColor, targetColor) <= 120
            }
        }
    }

    private suspend fun findSimilarItems(
        items: List<ClothingItem>,
        sourceSignatures: List<ImageSignature>
    ): List<ClothingItem> {
        if (sourceSignatures.isEmpty()) return emptyList()

        return withContext(Dispatchers.Default) {
            items.mapNotNull { item ->
                val signature = resolveSignature(item.imagePath) ?: return@mapNotNull null
                val minDistance = sourceSignatures.minOf { 
                    ImageMatcher.hammingDistance(it.averageHash, signature.averageHash)
                }
                if (minDistance <= 14) {
                    item to minDistance
                } else {
                    null
                }
            }
                .sortedBy { (_, distance) -> distance }
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

