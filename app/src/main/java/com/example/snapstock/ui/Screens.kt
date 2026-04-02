package com.example.snapstock.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.snapstock.data.AppSettings
import com.example.snapstock.data.ClothingItem
import com.example.snapstock.utils.OcrExtractor
import java.io.File
import java.util.Currency
import java.text.NumberFormat
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onBatchCaptureClick: () -> Unit,
    dashboardViewModel: DashboardViewModel = viewModel(),
    settingsViewModel: SettingsViewModel = viewModel()
) {
    val uiState by dashboardViewModel.uiState.collectAsState()
    val settingsState by settingsViewModel.uiState.collectAsState()
    var selectedNavItem by rememberSaveable { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        selectedNavItem = 0
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(text = "SnapStock", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {},
                actions = {
                    TextButton(onClick = onSearchClick) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = "Search",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            )
        },
        bottomBar = {
            AppBottomNavigationBar(
                selectedNavItem = selectedNavItem,
                onHomeClick = { selectedNavItem = 0 },
                onCollectionClick = { selectedNavItem = 1 },
                onSettingsClick = {
                    selectedNavItem = 2
                    onSettingsClick()
                }
            )
        },
        floatingActionButton = {
            HighlightedFab(
                showHighlight = uiState.isEmpty,
                onClick = onBatchCaptureClick
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            item {
                StatsRow(
                    totalItems = uiState.totalItems,
                    lowStockCount = uiState.lowStockCount,
                    totalInventoryValue = uiState.totalInventoryValue,
                    currencyCode = settingsState.currencyCode
                )
            }

            item {
                LastActionCard(
                    uiState = uiState,
                    currencyCode = settingsState.currencyCode
                )
            }

            if (uiState.isEmpty) {
                item {
                    EmptyStateCallout()
                }
            }
        }
    }
}

@Composable
private fun StatsRow(totalItems: Int, lowStockCount: Int, totalInventoryValue: Double, currencyCode: String) {
    val currencyFormatter = remember(currencyCode) {
        NumberFormat.getCurrencyInstance(Locale.getDefault()).apply {
            runCatching {
                currency = Currency.getInstance(currencyCode)
            }
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatCard(
            modifier = Modifier.weight(1f),
            title = "Total",
            value = totalItems.toString()
        )
        StatCard(
            modifier = Modifier.weight(1f),
            title = "Low",
            value = lowStockCount.toString()
        )
        StatCard(
            modifier = Modifier.weight(1f),
            title = "Value",
            value = currencyFormatter.format(totalInventoryValue)
        )
    }
}

@Composable
private fun StatCard(modifier: Modifier, title: String, value: String) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(text = title, style = MaterialTheme.typography.labelMedium)
            Text(text = value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun LastActionCard(uiState: DashboardUiState, currencyCode: String) {
    val currencyFormatter = remember(currencyCode) {
        NumberFormat.getCurrencyInstance(Locale.getDefault()).apply {
            runCatching {
                currency = Currency.getInstance(currencyCode)
            }
            maximumFractionDigits = 0
            minimumFractionDigits = 0
        }
    }

    val lastItem = uiState.lastAddedItem
    if (lastItem != null) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 320.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = "Last Action", style = MaterialTheme.typography.labelLarge)

                ItemImageThumbnail(
                    imagePath = lastItem.imagePath,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(12.dp))
                )

                Text(
                    text = lastItem.name.uppercase(Locale.getDefault()),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = currencyFormatter.format(lastItem.price),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        }
    } else {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = "Last Action", style = MaterialTheme.typography.labelLarge)
                Text(text = "No items yet. Your first capture will appear here.")
            }
        }
    }
}

@Composable
private fun EmptyStateCallout() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Your shop is a blank canvas!",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Tap the highlighted camera button to capture your first item.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun HighlightedFab(showHighlight: Boolean, onClick: () -> Unit) {
    val highlightColor = MaterialTheme.colorScheme.primary
    val transition = rememberInfiniteTransition(label = "fabSpotlight")
    val pulseProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1300),
            repeatMode = RepeatMode.Restart
        ),
        label = "fabPulseProgress"
    )
    val fabScale by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (showHighlight) 1.06f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700),
            repeatMode = RepeatMode.Reverse
        ),
        label = "fabScale"
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (showHighlight) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Text(
                    text = "Start here: Tap Camera",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }

        Box(contentAlignment = Alignment.Center) {
            if (showHighlight) {
                val ringTwoProgress = (pulseProgress + 0.5f) % 1f
                Canvas(modifier = Modifier.size(96.dp)) {
                    drawCircle(
                        color = highlightColor.copy(alpha = (1f - pulseProgress) * 0.32f),
                        radius = (size.minDimension / 2f) * (1f + (pulseProgress * 0.6f))
                    )
                    drawCircle(
                        color = highlightColor.copy(alpha = (1f - ringTwoProgress) * 0.22f),
                        radius = (size.minDimension / 2f) * (1f + (ringTwoProgress * 0.6f))
                    )
                }
            }

            FloatingActionButton(
                onClick = onClick,
                modifier = Modifier.graphicsLayer {
                    scaleX = fabScale
                    scaleY = fabScale
                }
            ) {
                Icon(
                    imageVector = Icons.Filled.PhotoCamera,
                    contentDescription = "Camera"
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onHomeClick: () -> Unit,
    onCollectionClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val searchViewModel: SearchViewModel = viewModel()
    val uiState by searchViewModel.uiState.collectAsState()
    var scannerActive by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(text = "Search") }
            )
        },
        bottomBar = {
            AppBottomNavigationBar(
                selectedNavItem = 0,
                onHomeClick = onHomeClick,
                onCollectionClick = onCollectionClick,
                onSettingsClick = onSettingsClick
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = uiState.query,
                        onValueChange = searchViewModel::onQueryChange,
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        label = { Text("Search items") },
                        placeholder = { Text("Name or category") }
                    )
                    TextButton(onClick = { scannerActive = !scannerActive }) {
                        Text(if (scannerActive) "Stop" else "Camera")
                    }
                }
            }

            item {
                if (scannerActive) {
                    ScannerPreviewPlaceholder()
                }
            }

            if (!scannerActive) {
                when {
                    uiState.query.isBlank() -> {
                        item { SearchPromptCard() }
                    }

                    uiState.isSearching -> {
                        item { SearchLoadingCard() }
                    }

                    uiState.hasSearched && uiState.results.isEmpty() -> {
                        item { SearchNoMatchCard(query = uiState.query.trim()) }
                    }

                    else -> {
                        items(uiState.results, key = { it.id }) { item ->
                            SearchResultCard(item = item)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onHomeClick: () -> Unit,
    onCollectionClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val tabs = listOf("Personalization", "Performance", "Safety Zone")
    var selectedTabIndex by rememberSaveable { mutableStateOf(0) }
    val settingsViewModel: SettingsViewModel = viewModel()
    val settingsState by settingsViewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(text = "Settings") }
            )
        },
        bottomBar = {
            AppBottomNavigationBar(
                selectedNavItem = 2,
                onHomeClick = onHomeClick,
                onCollectionClick = onCollectionClick,
                onSettingsClick = onSettingsClick
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            TabRow(selectedTabIndex = selectedTabIndex) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(title) }
                    )
                }
            }

            when (selectedTabIndex) {
                0 -> PersonalizationTab(
                    settings = settingsState,
                    onShopNameChange = settingsViewModel::updateShopName,
                    onCurrencyChange = settingsViewModel::updateCurrencyCode,
                    onDefaultCategoryChange = settingsViewModel::updateDefaultCategory
                )
                1 -> PerformanceTab(
                    settings = settingsState,
                    onHapticFeedbackChange = settingsViewModel::updateHapticFeedbackEnabled,
                    onOcrSensitivityChange = settingsViewModel::updateHighOcrSensitivity,
                    onAutoSaveBatchesChange = settingsViewModel::updateAutoSaveBatches
                )
                else -> SafetyZoneTab()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchCaptureScreen(
    onBackClick: () -> Unit,
    onDoneClick: () -> Unit,
    batchEntryViewModel: BatchEntryViewModel
) {
    val uiState by batchEntryViewModel.uiState.collectAsState()
    val shouldShowTutorial by batchEntryViewModel.shouldShowFirstScanTutorial.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    var isCapturing by rememberSaveable { mutableStateOf(false) }

    val cameraController = remember(context) {
        LifecycleCameraController(context).apply {
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            setEnabledUseCases(CameraController.IMAGE_CAPTURE)
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (!granted) {
            scope.launch {
                snackbarHostState.showSnackbar("Camera permission is required to capture inventory.")
            }
        }
    }

    LaunchedEffect(hasCameraPermission) {
        if (!hasCameraPermission) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    LaunchedEffect(Unit) {
        batchEntryViewModel.startNewSession()
    }

    LaunchedEffect(hasCameraPermission, lifecycleOwner) {
        if (hasCameraPermission) {
            cameraController.bindToLifecycle(lifecycleOwner)
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(text = "Batch Capture") },
                navigationIcon = {
                    TextButton(onClick = onBackClick) {
                        Text(text = "Back")
                    }
                }
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        if (!hasCameraPermission || isCapturing) {
                            if (!hasCameraPermission) {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                            return@OutlinedButton
                        }

                        val photoFile = createBatchImageFile(context)
                        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
                        isCapturing = true
                        cameraController.takePicture(
                            outputOptions,
                            ContextCompat.getMainExecutor(context),
                            object : ImageCapture.OnImageSavedCallback {
                                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                    isCapturing = false
                                    scope.launch {
                                        val hints = extractDraftHintsFromImage(photoFile.absolutePath)
                                        batchEntryViewModel.addCapturedImage(
                                            imagePath = photoFile.absolutePath,
                                            initialName = hints.prefilledName,
                                            initialPriceInput = hints.prefilledPrice,
                                            ocrNameConfident = hints.nameConfident,
                                            ocrPriceConfident = hints.priceConfident
                                        )
                                        batchEntryViewModel.markFirstScanTutorialSeen()

                                        if (hints.prefilledName.isNotBlank() || hints.prefilledPrice.isNotBlank()) {
                                            snackbarHostState.showSnackbar("OCR prefilled draft fields.")
                                        }
                                    }
                                }

                                override fun onError(exception: ImageCaptureException) {
                                    isCapturing = false
                                    scope.launch {
                                        snackbarHostState.showSnackbar(
                                            "Capture failed. Please try again."
                                        )
                                    }
                                }
                            }
                        )
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (isCapturing) "Capturing..." else "Capture")
                }
                Button(
                    onClick = onDoneClick,
                    enabled = uiState.captureCount > 0,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Done")
                }
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                        .padding(12.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    if (hasCameraPermission) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { ctx ->
                                PreviewView(ctx).apply {
                                    scaleType = PreviewView.ScaleType.FILL_CENTER
                                    controller = cameraController
                                }
                            }
                        )
                    } else {
                        Text(
                            text = "Camera permission required",
                            modifier = Modifier.align(Alignment.Center),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    if (shouldShowTutorial) {
                        FirstScanTutorialOverlay()
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "${uiState.captureCount} items captured",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(text = "Tap Capture repeatedly, then Done to enter batch details.")
                }
            }
        }
    }
}

@Composable
private fun FirstScanTutorialOverlay() {
    val tutorialStrokeColor = MaterialTheme.colorScheme.onPrimary

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.35f))
    ) {
        Canvas(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.78f)
                .height(170.dp)
        ) {
            drawRoundRect(
                color = tutorialStrokeColor,
                cornerRadius = CornerRadius(28f, 28f),
                style = Stroke(
                    width = 6f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 14f), 0f)
                )
            )
        }

        Text(
            text = "Keep the item and the price tag in frame.",
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(20.dp),
            color = MaterialTheme.colorScheme.onPrimary,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchEntryScreen(
    onBackClick: () -> Unit,
    onSaveComplete: () -> Unit,
    batchEntryViewModel: BatchEntryViewModel
) {
    val uiState by batchEntryViewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showConfettiPlaceholder by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        batchEntryViewModel.events.collect { event ->
            when (event) {
                is BatchSaveEvent.Error -> snackbarHostState.showSnackbar(event.message)
                is BatchSaveEvent.Success -> {
                    showConfettiPlaceholder = true
                    snackbarHostState.showSnackbar("Saved ${event.savedCount} items.")
                    delay(450)
                    showConfettiPlaceholder = false
                    onSaveComplete()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(text = "Batch Entry") },
                navigationIcon = {
                    TextButton(onClick = onBackClick) {
                        Text(text = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            Button(
                onClick = { batchEntryViewModel.saveBatch() },
                enabled = uiState.drafts.isNotEmpty() && !uiState.isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(if (uiState.isSaving) "Saving..." else "Save Batch")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            if (showConfettiPlaceholder) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Text(
                            text = "Confetti: Batch saved!",
                            modifier = Modifier.padding(16.dp),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            if (uiState.drafts.isEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "No captured items yet. Go back and tap Capture first.",
                            modifier = Modifier.padding(16.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                item {
                    Text(
                        text = "${uiState.drafts.size} items to complete",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                items(uiState.drafts, key = { it.localId }) { draft ->
                    BatchDraftEditorCard(
                        draft = draft,
                        onNameChange = { batchEntryViewModel.updateDraft(localId = draft.localId, name = it) },
                        onPriceChange = { batchEntryViewModel.updateDraft(localId = draft.localId, priceInput = it) },
                        onQuantityChange = { batchEntryViewModel.updateDraft(localId = draft.localId, quantityInput = it) },
                        onCategoryChange = { batchEntryViewModel.updateDraft(localId = draft.localId, category = it) }
                    )
                }
            }
        }
    }
}

@Composable
private fun BatchDraftEditorCard(
    draft: BatchDraft,
    onNameChange: (String) -> Unit,
    onPriceChange: (String) -> Unit,
    onQuantityChange: (String) -> Unit,
    onCategoryChange: (String) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DraftImageThumbnail(imagePath = draft.imagePath)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Item #${draft.localId}", style = MaterialTheme.typography.labelLarge)
                if (draft.ocrNameConfident || draft.ocrPriceConfident) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        ),
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Text(
                            text = "OCR",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            OutlinedTextField(
                value = draft.name,
                onValueChange = onNameChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Name")
                        if (draft.ocrNameConfident) {
                            Text("✓", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            )
            OutlinedTextField(
                value = draft.category,
                onValueChange = onCategoryChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Category") }
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = draft.priceInput,
                    onValueChange = onPriceChange,
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Price")
                            if (draft.ocrPriceConfident) {
                                Text("✓", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                )
                OutlinedTextField(
                    value = draft.quantityInput,
                    onValueChange = onQuantityChange,
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("Qty") }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaceholderScreen(title: String, subtitle: String, onBackClick: () -> Unit) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(text = title) },
                navigationIcon = {
                    TextButton(onClick = onBackClick) {
                        Text(text = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = subtitle,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        }
    }
}

@Composable
private fun ScannerPreviewPlaceholder() {
    val transition = rememberInfiniteTransition(label = "laserScan")
    val laserColor = MaterialTheme.colorScheme.primary
    val lineProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200),
            repeatMode = RepeatMode.Restart
        ),
        label = "laserLineProgress"
    )

    Card(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .padding(12.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val y = size.height * lineProgress
                drawLine(
                    color = laserColor,
                    start = androidx.compose.ui.geometry.Offset(0f, y),
                    end = androidx.compose.ui.geometry.Offset(size.width, y),
                    strokeWidth = 5f
                )
            }
            Text(
                text = "Laser scan active...",
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 10.dp),
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

@Composable
private fun SearchPromptCard() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Search your inventory",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Type an item name or category to find matches.",
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun SearchLoadingCard() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Searching...",
            modifier = Modifier.padding(20.dp),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun SearchNoMatchCard(query: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "No matches found",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(text = "Try another term for \"$query\".")
        }
    }
}

@Composable
private fun SearchResultCard(item: ClothingItem) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ItemImageThumbnail(
                imagePath = item.imagePath,
                modifier = Modifier.size(72.dp)
            )

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = item.name, style = MaterialTheme.typography.titleMedium)
                Text(text = "Category: ${item.category}")
                Text(text = "Qty: ${item.quantity}   Price: ${item.price}")
            }
        }
    }
}

@Composable
private fun DraftImageThumbnail(imagePath: String) {
    ItemImageThumbnail(
        imagePath = imagePath,
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
    )
}

@Composable
private fun ItemImageThumbnail(imagePath: String, modifier: Modifier) {
    val bitmap = remember(imagePath) {
        BitmapFactory.decodeFile(imagePath)
    }

    Card(modifier = modifier) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Item thumbnail",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No preview",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun PersonalizationTab(
    settings: AppSettings,
    onShopNameChange: (String) -> Unit,
    onCurrencyChange: (String) -> Unit,
    onDefaultCategoryChange: (String) -> Unit
) {
    val currencies = listOf("USD", "MXN", "EUR")
    val categories = listOf("Shirts", "Pants", "Denim", "Outerwear")
    var currencyExpanded by remember { mutableStateOf(false) }
    var categoryExpanded by remember { mutableStateOf(false) }
    val selectedCurrency = settings.currencyCode
    val selectedCategory = settings.defaultCategory

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = settings.shopName,
            onValueChange = onShopNameChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Shop Name") },
            singleLine = true
        )

        Box {
            OutlinedButton(onClick = { currencyExpanded = true }) {
                Text("Currency: $selectedCurrency")
            }
            DropdownMenu(expanded = currencyExpanded, onDismissRequest = { currencyExpanded = false }) {
                currencies.forEach { currency ->
                    DropdownMenuItem(
                        text = { Text(currency) },
                        onClick = {
                            onCurrencyChange(currency)
                            currencyExpanded = false
                        }
                    )
                }
            }
        }

        Box {
            OutlinedButton(onClick = { categoryExpanded = true }) {
                Text("Default Category: $selectedCategory")
            }
            DropdownMenu(expanded = categoryExpanded, onDismissRequest = { categoryExpanded = false }) {
                categories.forEach { category ->
                    DropdownMenuItem(
                        text = { Text(category) },
                        onClick = {
                            onDefaultCategoryChange(category)
                            categoryExpanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PerformanceTab(
    settings: AppSettings,
    onHapticFeedbackChange: (Boolean) -> Unit,
    onOcrSensitivityChange: (Boolean) -> Unit,
    onAutoSaveBatchesChange: (Boolean) -> Unit
) {

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SettingToggleRow("Haptic Feedback", settings.hapticFeedbackEnabled, onHapticFeedbackChange)
        SettingToggleRow("OCR Sensitivity (High)", settings.highOcrSensitivity, onOcrSensitivityChange)
        SettingToggleRow("Auto-Save Batches", settings.autoSaveBatches, onAutoSaveBatchesChange)
    }
}

@Composable
private fun SafetyZoneTab() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        ) {
            Text(
                text = "Safety Zone",
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }

        OutlinedButton(onClick = { }, modifier = Modifier.fillMaxWidth()) {
            Text("Image Compression")
        }
        OutlinedButton(onClick = { }, modifier = Modifier.fillMaxWidth()) {
            Text("Database Cleanup")
        }
        Button(onClick = { }, modifier = Modifier.fillMaxWidth()) {
            Text("Factory Reset")
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Destructive tools are intentionally separated here.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun AppBottomNavigationBar(
    selectedNavItem: Int,
    onHomeClick: () -> Unit,
    onCollectionClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    NavigationBar {
        NavigationBarItem(
            icon = { Icon(Icons.Filled.Home, contentDescription = "Home") },
            label = { Text("Home") },
            selected = selectedNavItem == 0,
            onClick = onHomeClick
        )
        NavigationBarItem(
            icon = { Icon(Icons.Filled.GridView, contentDescription = "Collection") },
            label = { Text("Collection") },
            selected = selectedNavItem == 1,
            onClick = onCollectionClick
        )
        NavigationBarItem(
            icon = { Icon(Icons.Filled.Settings, contentDescription = "Settings") },
            label = { Text("Settings") },
            selected = selectedNavItem == 2,
            onClick = onSettingsClick
        )
    }
}

private fun createBatchImageFile(context: Context): File {
    val imageDir = File(context.filesDir, "batch_images")
    if (!imageDir.exists()) {
        imageDir.mkdirs()
    }
    return File(imageDir, "snap_${System.currentTimeMillis()}.jpg")
}

private data class DraftOcrHints(
    val prefilledName: String = "",
    val prefilledPrice: String = "",
    val nameConfident: Boolean = false,
    val priceConfident: Boolean = false
)

private suspend fun extractDraftHintsFromImage(imagePath: String): DraftOcrHints = withContext(Dispatchers.Default) {
    val bitmap = BitmapFactory.decodeFile(imagePath) ?: return@withContext DraftOcrHints()
    val ocrResult = OcrExtractor.extractTextFromImage(bitmap)

    val extractedName = ocrResult.extractedName.orEmpty().trim()
    val normalizedPrice = normalizePriceInput(ocrResult.extractedPrice)

    DraftOcrHints(
        prefilledName = extractedName,
        prefilledPrice = normalizedPrice,
        nameConfident = extractedName.isNotBlank(),
        priceConfident = normalizedPrice.isNotBlank()
    )
}

private fun normalizePriceInput(rawPrice: String?): String {
    if (rawPrice.isNullOrBlank()) return ""
    val match = Regex("""\d+(?:[.,]\d{1,2})?""").find(rawPrice) ?: return ""
    return match.value.replace(',', '.')
}

@Composable
private fun SettingToggleRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = title)
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

