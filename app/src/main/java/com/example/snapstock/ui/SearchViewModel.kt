package com.example.snapstock.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.snapstock.data.AppDatabase
import com.example.snapstock.data.ClothingItem
import com.example.snapstock.utils.DualEngineSignature
import com.example.snapstock.utils.DualEngineSignatureExtractor
import com.example.snapstock.utils.OcrExtractor
import com.example.snapstock.utils.SignatureCodec
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
    val signature: DualEngineSignature
)

enum class MatchBadge {
    PatternMatch,
    TagMatch,
    DualMatch
}

data class RankedMatch(
    val item: ClothingItem,
    val combinedScore: Float,
    val visualScore: Float,
    val textScore: Float,
    val badge: MatchBadge
)

data class SearchUiState(
    val isLoading: Boolean = true,
    val query: String = "",
    val isSearching: Boolean = false,
    val hasSearched: Boolean = false,
    val topMatches: List<RankedMatch> = emptyList(),
    val results: List<ClothingItem> = emptyList(),
    val scannedImage: ScannedImageContext? = null,
    val visualMatchConfidence: Float = 0f
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
    private val isDataReady = allItems
        .map { true }
        .onStart { emit(false) }

    private val visualWeight = 0.60f
    private val textWeight = 0.40f
    private val combinedThreshold = 0.58f
    private val singleEngineThreshold = 0.78f

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
        allItems,
        isDataReady
    ) { activeQuery, searchState, activeScannedImage, itemPool, dataReady ->
        val visualMatchResult = findTopVisualMatches(
            items = itemPool,
            sourceSignature = activeScannedImage?.signature,
            appContext = getApplication<Application>().applicationContext
        )
        val topMatches = visualMatchResult.matches
        val topMatchIds = topMatches.map { it.item.id }.toSet()

        SearchUiState(
            isLoading = !dataReady,
            query = activeQuery,
            isSearching = searchState.isSearching,
            hasSearched = searchState.hasSearched || activeScannedImage != null,
            topMatches = topMatches,
            results = searchState.results.filterNot { topMatchIds.contains(it.id) },
            scannedImage = activeScannedImage,
            visualMatchConfidence = visualMatchResult.confidence
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SearchUiState()
    )

    fun onQueryChange(newQuery: String) {
        query.update { newQuery }
    }

    suspend fun onImageScanned(imagePath: String): Boolean {
        val appContext = getApplication<Application>().applicationContext
        val signature = DualEngineSignatureExtractor.extractFromImagePath(appContext, imagePath) ?: return false
        query.value = ""
        scannedImage.value = ScannedImageContext(imagePath = imagePath, signature = signature)
        return true
    }

    fun clearImageContext() {
        scannedImage.value = null
    }

    private suspend fun findTopVisualMatches(
        items: List<ClothingItem>,
        sourceSignature: DualEngineSignature?,
        appContext: Context
    ): VisualMatchResult {
        if (sourceSignature == null) return VisualMatchResult()

        return withContext(Dispatchers.Default) {
            val candidates = items.mapNotNull { item ->
                val signature = resolveSignature(appContext, item) ?: return@mapNotNull null
                val visualScore = SignatureCodec.cosineSimilarity(sourceSignature.embedding, signature.embedding)
                val textScore = SignatureCodec.tokenSimilarity(sourceSignature.ocrTokens, signature.ocrTokens)
                val combinedScore = (visualScore * visualWeight) + (textScore * textWeight)
                val qualifies = combinedScore >= combinedThreshold ||
                    visualScore >= singleEngineThreshold ||
                    textScore >= singleEngineThreshold
                if (!qualifies && combinedScore < 0.20f && visualScore < 0.30f && textScore < 0.30f) {
                    return@mapNotNull null
                }

                VisualMatchCandidate(
                    item = item,
                    combinedScore = combinedScore,
                    visualScore = visualScore,
                    textScore = textScore,
                    badge = determineBadge(visualScore = visualScore, textScore = textScore)
                )
            }

            val sortedCandidates = candidates.sortedByDescending { it.combinedScore }
            val shortlisted = if (sortedCandidates.any { candidate ->
                    candidate.combinedScore >= combinedThreshold ||
                        candidate.visualScore >= singleEngineThreshold ||
                        candidate.textScore >= singleEngineThreshold
                }) {
                sortedCandidates
            } else {
                sortedCandidates.take(1)
            }

            val matches = shortlisted.take(8)
                .map {
                    RankedMatch(
                        item = it.item,
                        combinedScore = it.combinedScore,
                        visualScore = it.visualScore,
                        textScore = it.textScore,
                        badge = it.badge
                    )
                }

            VisualMatchResult(
                matches = matches,
                confidence = matches.firstOrNull()?.combinedScore ?: 0f
            )
        }
    }

    private suspend fun resolveSignature(appContext: Context, item: ClothingItem): DualEngineSignature? {
        val embedded = SignatureCodec.decodeEmbedding(item.visualEmbedding)
        val tokenSet = SignatureCodec.decodeTokens(item.ocrTokens)
        if (embedded != null) {
            return DualEngineSignature(
                embedding = embedded,
                ocrText = item.ocrText?.takeIf { it.isNotBlank() } ?: "${item.name} ${item.category}",
                ocrTokens = tokenSet.ifEmpty { OcrExtractor.normalizeTokens("${item.name} ${item.category}") },
                signatureVersion = item.signatureVersion
            )
        }

        val rebuilt = runCatching {
            DualEngineSignatureExtractor.extractFromImagePath(appContext, item.imagePath)
        }.getOrNull()

        if (rebuilt != null) {
            return rebuilt
        }

        val fallbackTokens = tokenSet.ifEmpty { OcrExtractor.normalizeTokens(item.name + " " + item.category) }
        return DualEngineSignature(
            embedding = null,
            ocrText = "${item.name} ${item.category}",
            ocrTokens = fallbackTokens,
            signatureVersion = item.signatureVersion
        )
    }

    private fun determineBadge(visualScore: Float, textScore: Float): MatchBadge {
        return when {
            visualScore >= 0.68f && textScore < 0.2f -> MatchBadge.PatternMatch
            textScore >= 0.68f && visualScore < 0.2f -> MatchBadge.TagMatch
            else -> MatchBadge.DualMatch
        }
    }

    private data class VisualMatchCandidate(
        val item: ClothingItem,
        val combinedScore: Float,
        val visualScore: Float,
        val textScore: Float,
        val badge: MatchBadge
    )

    private data class VisualMatchResult(
        val matches: List<RankedMatch> = emptyList(),
        val confidence: Float = 0f
    )
}
