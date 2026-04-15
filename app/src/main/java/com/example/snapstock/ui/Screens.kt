package com.example.snapstock.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.snapstock.data.AppSettings
import com.example.snapstock.data.ClothingItem
import com.example.snapstock.utils.ImageSharpness
import com.example.snapstock.utils.OcrExtractor
import com.example.snapstock.ui.theme.InfoBlue
import java.io.File
import java.io.FileOutputStream
import java.util.Currency
import java.text.NumberFormat
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onCollectionClick: () -> Unit,
    onTodoClick: () -> Unit,
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
                onCollectionClick = {
                    selectedNavItem = 1
                    onCollectionClick()
                },
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
            if (uiState.isLoading) {
                item { DashboardShimmer() }
            } else {
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
            }

            if (uiState.pendingTodos.isNotEmpty()) {
                item {
                    TodoReminderCard(
                        todo = uiState.pendingTodos.first(),
                        items = uiState.items,
                        onContinue = onTodoClick
                    )
                }
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
private fun TodoReminderCard(
    todo: com.example.snapstock.data.TodoEntry,
    items: List<ClothingItem>,
    onContinue: () -> Unit
) {
    val todoItemIds = remember(todo.itemIdsCsv) {
        todo.itemIdsCsv.split(",")
            .mapNotNull { it.trim().toIntOrNull() }
    }
    val todoItems = remember(todoItemIds, items) {
        todoItemIds.mapNotNull { id -> items.firstOrNull { it.id == id } }
    }
    val pendingCount = todoItems.size.takeIf { it > 0 } ?: todoItemIds.size

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "To Do",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Text(
                text = "$pendingCount items to complete",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Text(
                text = todo.title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (todoItems.isNotEmpty()) {
                    LazyRow(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(todoItems, key = { it.id }) { item ->
                            ItemImageThumbnail(
                                imagePath = item.imagePath,
                                modifier = Modifier.size(64.dp)
                            )
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }

                Button(
                    onClick = onContinue,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary
                    )
                ) {
                    Icon(imageVector = Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.size(6.dp))
                    Text("Continue")
                }
            }
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
    searchViewModel: SearchViewModel = viewModel(),
    onHomeClick: () -> Unit,
    onCollectionClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onCameraClick: () -> Unit = {},
    onCreateMysteryItem: () -> Unit = {}
) {
    val collectionViewModel: CollectionViewModel = viewModel()
    val uiState by searchViewModel.uiState.collectAsState()
    var selectedItem by rememberSaveable { mutableStateOf<ClothingItem?>(null) }
    var isEditing by rememberSaveable { mutableStateOf(false) }

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
                    IconButton(onClick = onCameraClick) {
                        Icon(
                            imageVector = Icons.Filled.PhotoCamera,
                            contentDescription = "Open camera search"
                        )
                    }
                }
            }

            if (uiState.scannedImage != null && uiState.topMatches.isNotEmpty()) {
                item {
                    BestMatchHeroCard(
                        match = uiState.topMatches.first(),
                        matchCount = uiState.topMatches.size,
                        onClick = {
                            selectedItem = uiState.topMatches.first().item
                            isEditing = false
                        }
                    )
                }

                if (uiState.topMatches.size > 1) {
                    item {
                        Text(
                            text = "More matches",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    items(uiState.topMatches.drop(1), key = { it.item.id }) { match ->
                        SearchResultCard(item = match.item, badge = match.badge, onClick = {
                            selectedItem = match.item
                            isEditing = false
                        })
                    }
                }
            } else if (uiState.scannedImage != null && uiState.topMatches.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "That pattern is a mystery!",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Text(
                                text = "Create New Item?",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.78f)
                            )
                            Button(
                                onClick = onCreateMysteryItem,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.tertiary,
                                    contentColor = MaterialTheme.colorScheme.onTertiary
                                )
                            ) {
                                Text("Create New Item")
                            }
                        }
                    }
                }
            }

            if (uiState.scannedImage == null) {
                when {
                    uiState.isLoading -> {
                        item { SearchShimmer() }
                    }

                    uiState.query.isBlank() && uiState.scannedImage == null -> {
                        item { SearchPromptCard() }
                    }

                    uiState.isSearching -> {
                        item { SearchShimmer() }
                    }

                    uiState.hasSearched && uiState.results.isEmpty() && uiState.topMatches.isEmpty() -> {
                        item { SearchNoMatchCard(query = uiState.query.trim()) }
                    }

                    else -> {
                        if (uiState.results.isNotEmpty()) {
                            item {
                                Text(
                                    text = "Matches",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        items(uiState.results, key = { it.id }) { item ->
                            SearchResultCard(item = item, onClick = {
                                selectedItem = item
                                isEditing = false
                            })
                        }
                    }
                }
            }
        }

        selectedItem?.let { item ->
            CollectionDetailDialog(
                item = item,
                isEditing = isEditing,
                onEditToggle = { isEditing = !isEditing },
                onDismiss = {
                    selectedItem = null
                    isEditing = false
                },
                onSave = { updatedItem ->
                    collectionViewModel.updateItem(updatedItem)
                    selectedItem = updatedItem
                    isEditing = false
                },
                onRetake = { updatedPath ->
                    val updated = item.copy(imagePath = updatedPath)
                    selectedItem = updated
                    collectionViewModel.updateItem(updated)
                },
                onGalleryPick = { updatedPath ->
                    val updated = item.copy(imagePath = updatedPath)
                    selectedItem = updated
                    collectionViewModel.updateItem(updated)
                },
                onDelete = {
                    collectionViewModel.deleteItem(item)
                    selectedItem = null
                    isEditing = false
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchCameraScreen(
    searchViewModel: SearchViewModel,
    onDoneClick: () -> Unit
) {
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
    var torchEnabled by rememberSaveable { mutableStateOf(false) }

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
                snackbarHostState.showSnackbar("Camera permission is required for visual search.")
            }
        }
    }

    LaunchedEffect(hasCameraPermission) {
        if (!hasCameraPermission) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    LaunchedEffect(hasCameraPermission, lifecycleOwner) {
        if (hasCameraPermission) {
            cameraController.bindToLifecycle(lifecycleOwner)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
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

                ScannerLaserOverlay(
                    active = isCapturing,
                    modifier = Modifier.fillMaxSize()
                )

                Card(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f)
                    )
                ) {
                    Text(
                        text = if (isCapturing) "Scanning..." else "Tap shutter to scan",
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                IconButton(
                    onClick = {
                        if (!hasCameraPermission) {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            return@IconButton
                        }
                        torchEnabled = !torchEnabled
                        cameraController.enableTorch(torchEnabled)
                    },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 16.dp, end = 16.dp)
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.78f))
                ) {
                    Icon(
                        imageVector = if (torchEnabled) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                        contentDescription = if (torchEnabled) "Flash on" else "Flash off",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 24.dp)
                ) {
                    IconButton(
                        onClick = {
                            if (!hasCameraPermission || isCapturing) {
                                if (!hasCameraPermission) {
                                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                }
                                return@IconButton
                            }

                            scope.launch {
                                isCapturing = true
                                val burstFiles = mutableListOf<File>()
                                try {
                                    val start = System.currentTimeMillis()
                                    while (burstFiles.size < 6 && (System.currentTimeMillis() - start) < 2_000L) {
                                        val captured = captureSingleFrame(cameraController, context)
                                        if (captured != null) {
                                            burstFiles += captured
                                        }
                                        if (burstFiles.size >= 5 && (System.currentTimeMillis() - start) >= 1_500L) {
                                            break
                                        }
                                        delay(120)
                                    }

                                    val golden = selectGoldenFrame(burstFiles)
                                    if (golden == null) {
                                        snackbarHostState.showSnackbar("Capture failed. Please try again.")
                                    } else {
                                        val processed = searchViewModel.onImageScanned(golden.absolutePath)
                                        if (processed) {
                                            onDoneClick()
                                        } else {
                                            snackbarHostState.showSnackbar("Scan failed. Please try again.")
                                        }
                                        burstFiles
                                            .filter { it.absolutePath != golden.absolutePath }
                                            .forEach { file -> runCatching { file.delete() } }
                                    }
                                } finally {
                                    isCapturing = false
                                }
                            }
                        },
                        modifier = Modifier
                            .size(92.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.22f))
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.32f),
                                shape = CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PhotoCamera,
                            contentDescription = if (isCapturing) "Scanning" else "Shutter",
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
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
    val settingsLoading by settingsViewModel.isLoading.collectAsState()
    var isTabSwitchLoading by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(selectedTabIndex) {
        isTabSwitchLoading = true
        delay(120)
        isTabSwitchLoading = false
    }

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

            if (settingsLoading || isTabSwitchLoading) {
                SettingsShimmerRows()
            } else {
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
                        onAutoSaveBatchesChange = settingsViewModel::updateAutoSaveBatches,
                        onReducedConfettiEffectsChange = settingsViewModel::setReducedConfettiEffects,
                        onGreenStockThresholdChange = settingsViewModel::updateGreenStockThreshold,
                        onAmberStockThresholdChange = settingsViewModel::updateAmberStockThreshold
                    )
                    else -> SafetyZoneTab()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchCaptureScreen(
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
    var torchEnabled by rememberSaveable { mutableStateOf(false) }
    var previewImageIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var isAwaitingUndo by rememberSaveable { mutableStateOf(false) }

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

    LaunchedEffect(hasCameraPermission, lifecycleOwner) {
        if (hasCameraPermission) {
            cameraController.bindToLifecycle(lifecycleOwner)
        }
    }

    fun removeCaptureAt(index: Int) {
        if (isAwaitingUndo) return

        val nextIndex = batchEntryViewModel.removeDraftAt(index)
        previewImageIndex = nextIndex

        scope.launch {
            isAwaitingUndo = true
            val result = snackbarHostState.showSnackbar(
                message = "Capture removed",
                actionLabel = "Undo",
                withDismissAction = true
            )

            if (result == SnackbarResult.ActionPerformed) {
                previewImageIndex = batchEntryViewModel.undoLastRemoval()
            } else {
                batchEntryViewModel.commitLastRemoval()
            }
            isAwaitingUndo = false
        }
    }

    Scaffold(
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LazyRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(uiState.drafts, key = { _, draft -> draft.localId }) { index, draft ->
                        val isHighlighted = if (previewImageIndex != null) {
                            previewImageIndex == index
                        } else {
                            index == uiState.drafts.lastIndex
                        }

                        ItemImageThumbnail(
                            imagePath = draft.imagePath,
                            modifier = Modifier
                                .size(56.dp)
                                .border(
                                    width = if (isHighlighted) 1.5.dp else 0.dp,
                                    color = if (isHighlighted) {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                    } else {
                                        MaterialTheme.colorScheme.outline.copy(alpha = 0f)
                                    },
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable { previewImageIndex = index }
                        )
                    }
                }

                IconButton(
                    onClick = {
                        val targetIndex = previewImageIndex ?: uiState.drafts.lastIndex
                        if (targetIndex >= 0) {
                            removeCaptureAt(targetIndex)
                        }
                    },
                    enabled = uiState.captureCount > 0 && !isAwaitingUndo,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(
                            if (uiState.captureCount > 0 && !isAwaitingUndo) {
                                MaterialTheme.colorScheme.errorContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        )
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "Remove capture",
                        tint = if (uiState.captureCount > 0 && !isAwaitingUndo) {
                            MaterialTheme.colorScheme.onErrorContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }

                IconButton(
                    onClick = onDoneClick,
                    enabled = uiState.captureCount > 0,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(
                            if (uiState.captureCount > 0) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        )
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "Done",
                        modifier = Modifier.size(32.dp),
                        tint = if (uiState.captureCount > 0) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
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

                IconButton(
                    onClick = {
                        if (!hasCameraPermission) {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            return@IconButton
                        }
                        torchEnabled = !torchEnabled
                        cameraController.enableTorch(torchEnabled)
                    },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 16.dp, end = 16.dp)
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.78f))
                ) {
                    Icon(
                        imageVector = if (torchEnabled) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                        contentDescription = if (torchEnabled) "Flash on" else "Flash off",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp)
                ) {
                    IconButton(
                        onClick = {
                            if (!hasCameraPermission || isCapturing) {
                                if (!hasCameraPermission) {
                                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                }
                                return@IconButton
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
                        modifier = Modifier
                            .size(92.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.22f))
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.32f),
                                shape = CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PhotoCamera,
                            contentDescription = if (isCapturing) "Capturing" else "Capture",
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    Card(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 2.dp, end = 2.dp),
                        shape = CircleShape,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Text(
                            text = uiState.captureCount.toString(),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        previewImageIndex?.let { index ->
            FullImagePreviewDialog(
                imagePaths = uiState.drafts.map { it.imagePath },
                initialIndex = index,
                onIndexChange = { previewImageIndex = it },
                onRemoveCurrent = { removeCaptureAt(it) },
                onDismiss = { previewImageIndex = null }
            )
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
    onContinueEditingClick: () -> Unit,
    onExitClick: () -> Unit,
    onSaveComplete: () -> Unit,
    batchEntryViewModel: BatchEntryViewModel
) {
    val uiState by batchEntryViewModel.uiState.collectAsState()
    val categoryOptions by batchEntryViewModel.categoryOptions.collectAsState()
    val settingsViewModel: SettingsViewModel = viewModel()
    val settingsState by settingsViewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showConfettiBurst by rememberSaveable { mutableStateOf(false) }
    var showMissingFieldsModal by rememberSaveable { mutableStateOf(false) }
    var showExitConfirm by rememberSaveable { mutableStateOf(false) }
    val missingItemIssues = remember(uiState.drafts) {
        uiState.drafts.mapIndexedNotNull { index, draft ->
            val missingFields = buildList {
                if (draft.name.trim().isBlank()) add("Name")
                if (draft.category.trim().isBlank()) add("Category")
                if (draft.priceInput.toDoubleOrNull()?.let { it > 0.0 } != true) add("Price")
                if (draft.quantityInput.toIntOrNull()?.let { it > 0 } != true) add("Qty")
            }
            if (missingFields.isEmpty()) {
                null
            } else {
                "Item ${index + 1}: Missing ${missingFields.joinToString(", ")}" 
            }
        }
    }
    val incompleteCount = missingItemIssues.size
    val totalCount = uiState.drafts.size

    fun requestExitBatchEntry() {
        if (uiState.drafts.isNotEmpty()) {
            showExitConfirm = true
        } else {
            onBackClick()
        }
    }

    BackHandler {
        requestExitBatchEntry()
    }

    LaunchedEffect(Unit) {
        batchEntryViewModel.events.collect { event ->
            when (event) {
                is BatchSaveEvent.Error -> snackbarHostState.showSnackbar(event.message)
                is BatchSaveEvent.Success -> {
                    showConfettiBurst = true
                    val message = if (event.todoCount > 0) {
                        "Saved ${event.savedCount} items. ${event.todoCount} need follow-up."
                    } else {
                        "Saved ${event.savedCount} items."
                    }
                    snackbarHostState.showSnackbar(message)
                    delay(1500)
                    showConfettiBurst = false
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
                    IconButton(onClick = { requestExitBatchEntry() }) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            Button(
                onClick = {
                    if (missingItemIssues.isEmpty()) {
                        batchEntryViewModel.saveBatch(createTodo = false)
                    } else {
                        showMissingFieldsModal = true
                    }
                },
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
                        text = "$incompleteCount/$totalCount items incomplete",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
                item {
                    Text(
                        text = "${uiState.drafts.size} items to complete",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                items(uiState.drafts, key = { it.localId }) { draft ->
                    BatchDraftEditorCard(
                        draft = draft,
                        categoryOptions = categoryOptions,
                        onNameChange = { batchEntryViewModel.updateDraft(localId = draft.localId, name = it) },
                        onPriceChange = { batchEntryViewModel.updateDraft(localId = draft.localId, priceInput = it) },
                        onQuantityChange = { batchEntryViewModel.updateDraft(localId = draft.localId, quantityInput = it) },
                        onCategoryChange = { batchEntryViewModel.updateDraft(localId = draft.localId, category = it) },
                        onAddCategory = { newCategory -> batchEntryViewModel.addCustomCategory(newCategory) }
                    )
                }
            }
        }

        if (showMissingFieldsModal) {
            AlertDialog(
                onDismissRequest = { showMissingFieldsModal = false },
                title = { Text("Complete required fields") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        missingItemIssues.forEach { issue ->
                            Text(issue)
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = { showMissingFieldsModal = false }) {
                        Text("Continue Editing")
                    }
                },
                dismissButton = {
                    Button(onClick = {
                        showMissingFieldsModal = false
                        batchEntryViewModel.saveBatchWithTodo()
                    }) {
                        Text("Save + To-Do")
                    }
                }
            )
        }

        if (showExitConfirm) {
            AlertDialog(
                onDismissRequest = { showExitConfirm = false },
                title = { Text("Exit Batch Entry?") },
                text = { Text("Exit Batch Entry? Your captured photos will be lost.") },
                confirmButton = {
                    Button(
                        onClick = {
                            showExitConfirm = false
                            onExitClick()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    ) {
                        Text("Exit")
                    }
                },
                dismissButton = {
                    Button(
                        onClick = {
                            showExitConfirm = false
                            onContinueEditingClick()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Text("Continue Editing")
                    }
                }
            )
        }

        SnapStockConfetti(
            visible = showConfettiBurst,
            reducedEffects = settingsState.reducedConfettiEffects,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun SnapStockConfetti(
    visible: Boolean,
    reducedEffects: Boolean = false,
    modifier: Modifier = Modifier
) {
    if (!visible) return

    val transition = rememberInfiniteTransition(label = "confettiBurst")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (reducedEffects) 900 else 1500),
            repeatMode = RepeatMode.Restart
        ),
        label = "confettiProgress"
    )
    val brandGreen = Color(0xFF2E7D32)
    val brandAmber = Color(0xFFFFB300)
    val accentColor = InfoBlue

    val particles = remember(
        brandGreen,
        brandAmber,
        accentColor,
        reducedEffects
    ) {
        val palette = listOf(
            brandGreen,
            brandAmber,
            accentColor,
            brandGreen
        )
        val particleCount = if (reducedEffects) 24 else 56
        List(particleCount) { index ->
            val fromLeft = index % 2 == 0
            ConfettiParticle(
                xFraction = if (fromLeft) 0.10f + (index % 4) * 0.035f else 0.90f - (index % 4) * 0.035f,
                startFraction = (index * 0.018f) % 0.10f,
                drift = if (fromLeft) 0.26f + (index % 5) * 0.02f else -0.26f - (index % 5) * 0.02f,
                speed = if (reducedEffects) 0.42f + (index % 4) * 0.05f else 0.60f + (index % 6) * 0.06f,
                radius = if (reducedEffects) 3.8f + (index % 3) * 1.3f else 4.6f + (index % 5) * 2.0f,
                color = palette[index % palette.size]
            )
        }
    }

    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            particles.forEachIndexed { index, particle ->
                val travelProgress = (progress * particle.speed + index * 0.028f) % 1f
                val xProgress = (particle.xFraction + progress * particle.drift) % 1f
                val x = size.width * xProgress
                val y = size.height * (1f - ((particle.startFraction + travelProgress) % 1f))
                drawCircle(
                    color = particle.color.copy(alpha = 0.92f),
                    radius = particle.radius,
                    center = Offset(x, y)
                )
            }
        }
    }
}

private data class ConfettiParticle(
    val xFraction: Float,
    val startFraction: Float,
    val drift: Float,
    val speed: Float,
    val radius: Float,
    val color: Color
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionScreen(
    onHomeClick: () -> Unit,
    onCollectionClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onTodoClick: () -> Unit,
    collectionViewModel: CollectionViewModel = viewModel()
) {
    val items by collectionViewModel.items.collectAsState()
    val pendingTodos by collectionViewModel.pendingTodos.collectAsState()
    val isLoading by collectionViewModel.isLoading.collectAsState()
    val settingsViewModel: SettingsViewModel = viewModel()
    val settingsState by settingsViewModel.uiState.collectAsState()
    var selectedCategory by rememberSaveable { mutableStateOf("All") }
    var selectedItem by rememberSaveable { mutableStateOf<ClothingItem?>(null) }
    var isEditing by rememberSaveable { mutableStateOf(false) }
    var isCategorySwitchLoading by rememberSaveable { mutableStateOf(false) }
    var itemPendingDelete by rememberSaveable { mutableStateOf<ClothingItem?>(null) }
    val categories = remember(items) { listOf("All") + items.map { it.category }.distinct().sorted() }
    val filteredItems = remember(items, selectedCategory) {
        if (selectedCategory == "All") items else items.filter { it.category == selectedCategory }
    }
    val firstPendingTodo = pendingTodos.firstOrNull()
    val firstPendingTodoItems = remember(firstPendingTodo, items) {
        val ids = firstPendingTodo?.itemIdsCsv.orEmpty()
            .split(",")
            .mapNotNull { it.trim().toIntOrNull() }
        ids.mapNotNull { id -> items.firstOrNull { it.id == id } }
    }
    LaunchedEffect(selectedCategory) {
        isCategorySwitchLoading = true
        delay(160)
        isCategorySwitchLoading = false
    }
    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text("Collection") }) },
        bottomBar = {
            AppBottomNavigationBar(
                selectedNavItem = 1,
                onHomeClick = onHomeClick,
                onCollectionClick = { },
                onSettingsClick = onSettingsClick
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                    item {
                        FilterChipLike(
                            label = "To-Do",
                            selected = false,
                            onClick = onTodoClick
                        )
                    }
                    items(categories) { category ->
                        FilterChipLike(
                            label = category,
                            selected = category == selectedCategory,
                            onClick = { selectedCategory = category }
                        )
                    }
                }
            }

            if (firstPendingTodo != null) {
                item {
                    TodoReminderCard(
                        todo = firstPendingTodo,
                        items = items,
                        onContinue = onTodoClick
                    )
                }
            }

            if (isLoading || isCategorySwitchLoading) {
                item {
                    CollectionShimmerGrid()
                }
            } else if (filteredItems.isEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Text("No items in this collection.", modifier = Modifier.padding(16.dp))
                    }
                }
            } else {
                items(filteredItems.chunked(2)) { rowItems ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        rowItems.forEach { item ->
                            CollectionGridCard(
                                item = item,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    selectedItem = item
                                    isEditing = false
                                },
                                greenThreshold = settingsState.greenStockThreshold,
                                amberThreshold = settingsState.amberStockThreshold
                            )
                        }
                        if (rowItems.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        selectedItem?.let { item ->
            CollectionDetailDialog(
                item = item,
                isEditing = isEditing,
                onEditToggle = { isEditing = !isEditing },
                onDismiss = {
                    selectedItem = null
                    isEditing = false
                },
                onSave = { updatedItem ->
                    collectionViewModel.updateItem(updatedItem)
                    selectedItem = updatedItem
                    isEditing = false
                },
                onRetake = { updatedPath ->
                    selectedItem = item.copy(imagePath = updatedPath)
                    collectionViewModel.updateItem(item.copy(imagePath = updatedPath))
                },
                onGalleryPick = { updatedPath ->
                    selectedItem = item.copy(imagePath = updatedPath)
                    collectionViewModel.updateItem(item.copy(imagePath = updatedPath))
                },
                onDelete = { itemPendingDelete = item }
            )
        }

        itemPendingDelete?.let { item ->
            AlertDialog(
                onDismissRequest = { itemPendingDelete = null },
                title = { Text("Delete item?") },
                text = { Text("Are you sure you want to delete this item?") },
                confirmButton = {
                    Button(
                        onClick = {
                            collectionViewModel.deleteItem(item)
                            if (selectedItem?.id == item.id) {
                                selectedItem = null
                                isEditing = false
                            }
                            itemPendingDelete = null
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    Button(
                        onClick = { itemPendingDelete = null },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoListScreen(
    onHomeClick: () -> Unit,
    onCollectionClick: () -> Unit,
    onSettingsClick: () -> Unit,
    todoViewModel: TodoViewModel = viewModel()
) {
    val todoItems by todoViewModel.todoItems.collectAsState()
    val isLoading by todoViewModel.isLoading.collectAsState()
    var selectedItem by rememberSaveable { mutableStateOf<ClothingItem?>(null) }
    var isEditing by rememberSaveable { mutableStateOf(false) }
    var itemPendingDelete by rememberSaveable { mutableStateOf<ClothingItem?>(null) }

    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text("To-Do") }) },
        bottomBar = {
            AppBottomNavigationBar(
                selectedNavItem = 1,
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
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("Items needing details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = "${todoItems.size} items incomplete",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (isLoading) {
                item {
                    CollectionShimmerGrid()
                }
            } else if (todoItems.isEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "No pending To-Do items.",
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                items(todoItems.chunked(2)) { rowItems ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        rowItems.forEach { model ->
                            TodoGridCard(
                                model = model,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    selectedItem = model.item
                                    isEditing = false
                                },
                                onMarkComplete = {
                                    if (isItemCompleteForTodo(model.item)) {
                                        todoViewModel.markItemComplete(model.item.id)
                                        if (selectedItem?.id == model.item.id) {
                                            selectedItem = null
                                            isEditing = false
                                        }
                                    } else {
                                        selectedItem = model.item
                                        isEditing = true
                                    }
                                },
                                onDelete = {
                                    itemPendingDelete = model.item
                                }
                            )
                        }
                        if (rowItems.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        selectedItem?.let { item ->
            CollectionDetailDialog(
                item = item,
                isEditing = isEditing,
                onEditToggle = { isEditing = !isEditing },
                onDismiss = {
                    selectedItem = null
                    isEditing = false
                },
                onSave = { updatedItem ->
                    todoViewModel.updateItem(updatedItem)
                    selectedItem = updatedItem
                    isEditing = false
                    if (isItemCompleteForTodo(updatedItem)) {
                        todoViewModel.markItemComplete(updatedItem.id)
                        selectedItem = null
                    }
                },
                onRetake = { updatedPath ->
                    selectedItem = item.copy(imagePath = updatedPath)
                    todoViewModel.updateItem(item.copy(imagePath = updatedPath))
                },
                onGalleryPick = { updatedPath ->
                    selectedItem = item.copy(imagePath = updatedPath)
                    todoViewModel.updateItem(item.copy(imagePath = updatedPath))
                },
                onDelete = { itemPendingDelete = item }
            )
        }

        itemPendingDelete?.let { item ->
            AlertDialog(
                onDismissRequest = { itemPendingDelete = null },
                title = { Text("Delete item?") },
                text = { Text("Are you sure you want to delete this item?") },
                confirmButton = {
                    Button(
                        onClick = {
                            todoViewModel.deleteItem(item)
                            if (selectedItem?.id == item.id) {
                                selectedItem = null
                                isEditing = false
                            }
                            itemPendingDelete = null
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    Button(
                        onClick = { itemPendingDelete = null },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
private fun TodoGridCard(
    model: TodoItemCardModel,
    modifier: Modifier,
    onClick: () -> Unit,
    onMarkComplete: () -> Unit,
    onDelete: () -> Unit
) {
    val item = model.item
    val missing = remember(item.id, item.name, item.price, item.quantity, item.category) {
        missingFieldsForTodo(item)
    }

    Card(
        modifier = modifier,
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(8.dp)) {
            Box {
                ItemImageThumbnail(
                    imagePath = item.imagePath,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(152.dp)
                        .clip(RoundedCornerShape(14.dp))
                )

                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = onMarkComplete,
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = "Mark complete",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.error)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "Delete item",
                            tint = MaterialTheme.colorScheme.onError,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (missing.isEmpty()) {
                Text(
                    text = "Ready to complete",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Text(
                    text = "Missing: ${missing.joinToString(", ")}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun missingFieldsForTodo(item: ClothingItem): List<String> {
    val missing = mutableListOf<String>()
    if (item.name.trim().isBlank() || item.name.startsWith("Pending Item")) {
        missing += "Name"
    }
    if (item.price <= 0.0) {
        missing += "Price"
    }
    if (item.quantity <= 0) {
        missing += "Qty"
    }
    if (item.category.trim().isBlank()) {
        missing += "Category"
    }
    return missing
}

private fun isItemCompleteForTodo(item: ClothingItem): Boolean {
    return missingFieldsForTodo(item).isEmpty()
}

@Composable
private fun FilterChipLike(label: String, selected: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (selected) 4.dp else 1.dp)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CollectionGridCard(
    item: ClothingItem,
    modifier: Modifier,
    onClick: () -> Unit,
    greenThreshold: Int,
    amberThreshold: Int
) {
    val stockColor = when {
        item.quantity >= greenThreshold -> MaterialTheme.colorScheme.primary
        item.quantity >= amberThreshold -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.error
    }

    Card(
        modifier = modifier,
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(8.dp)) {
            Box {
                ItemImageThumbnail(
                    imagePath = item.imagePath,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(152.dp)
                        .clip(RoundedCornerShape(14.dp))
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(7.dp)
                        .size(11.dp)
                        .clip(CircleShape)
                        .background(stockColor)
                )
            }
            Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(item.category, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun CollectionDetailDialog(
    item: ClothingItem,
    isEditing: Boolean,
    onEditToggle: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (ClothingItem) -> Unit,
    onRetake: (String) -> Unit,
    onGalleryPick: (String) -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    var name by rememberSaveable(item.id) { mutableStateOf(item.name) }
    var price by rememberSaveable(item.id) { mutableStateOf(item.price.toString()) }
    var quantity by rememberSaveable(item.id) { mutableStateOf(item.quantity.toString()) }
    var category by rememberSaveable(item.id) { mutableStateOf(item.category) }

    val retakeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap: Bitmap? ->
        bitmap?.let { onRetake(saveBitmapToCollectionFile(context, it)) }
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) onGalleryPick(copyUriToCollectionFile(context, uri))
    }

    val rotation by animateFloatAsState(targetValue = if (isEditing) 180f else 0f, label = "collectionFlip")
    val density = LocalDensity.current

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .heightIn(max = 700.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        rotationY = rotation
                        cameraDistance = 16 * density.density
                    }
                    .padding(18.dp)
            ) {
                if (rotation < 90f) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        ItemImageThumbnail(
                            imagePath = item.imagePath,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(360.dp)
                                .clip(RoundedCornerShape(18.dp))
                        )
                        Text(item.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text("Category: ${item.category}")
                        Text("Qty: ${item.quantity}   Price: ${item.price}")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = onEditToggle) { Icon(Icons.Filled.Edit, contentDescription = null); Text("Edit") }
                            Button(
                                onClick = onDelete,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError
                                )
                            ) { Icon(Icons.Filled.Delete, contentDescription = null); Text("Delete") }
                            TextButton(onClick = onDismiss) { Text("Close") }
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.graphicsLayer { rotationY = 180f }) {
                        ItemImageThumbnail(
                            imagePath = item.imagePath,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(320.dp)
                                .clip(RoundedCornerShape(18.dp))
                        )
                        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = category, onValueChange = { category = it }, label = { Text("Category") }, modifier = Modifier.fillMaxWidth())
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = price,
                                onValueChange = { price = sanitizePriceInputForField(it) },
                                label = { Text("Price") },
                                modifier = Modifier.weight(1f),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                            )
                            OutlinedTextField(
                                value = quantity,
                                onValueChange = { quantity = sanitizeQuantityInputForField(it) },
                                label = { Text("Qty") },
                                modifier = Modifier.weight(1f),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { retakeLauncher.launch(null) }) { Text("Retake") }
                            Button(onClick = { galleryLauncher.launch("image/*") }) { Text("Gallery") }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                val updated = item.copy(
                                    name = name.trim(),
                                    price = price.toDoubleOrNull() ?: item.price,
                                    quantity = quantity.toIntOrNull() ?: item.quantity,
                                    category = category.trim().ifBlank { item.category }
                                )
                                onSave(updated)
                            }) { Text("Save") }
                            TextButton(onClick = onEditToggle) { Text("Back") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BatchDraftEditorCard(
    draft: BatchDraft,
    categoryOptions: List<String>,
    onNameChange: (String) -> Unit,
    onPriceChange: (String) -> Unit,
    onQuantityChange: (String) -> Unit,
    onCategoryChange: (String) -> Unit,
    onAddCategory: (String) -> Unit
) {
    var categoryExpanded by rememberSaveable(draft.localId) { mutableStateOf(false) }
    var showAddCategoryDialog by rememberSaveable(draft.localId) { mutableStateOf(false) }
    var pendingCategoryName by rememberSaveable(draft.localId) { mutableStateOf("") }

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
                            Text("OK", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            )

            Box(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { categoryExpanded = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Category: ${draft.category}")
                    }

                    OutlinedButton(onClick = { showAddCategoryDialog = true }) {
                        Icon(imageVector = Icons.Filled.Add, contentDescription = null)
                        Spacer(modifier = Modifier.size(4.dp))
                        Text("Add")
                    }
                }

                DropdownMenu(
                    expanded = categoryExpanded,
                    onDismissRequest = { categoryExpanded = false }
                ) {
                    categoryOptions.forEach { category ->
                        DropdownMenuItem(
                            text = { Text(category) },
                            onClick = {
                                onCategoryChange(category)
                                categoryExpanded = false
                            }
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = draft.priceInput,
                    onValueChange = onPriceChange,
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    label = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Price")
                            if (draft.ocrPriceConfident) {
                                Text("OK", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                )
                OutlinedTextField(
                    value = draft.quantityInput,
                    onValueChange = onQuantityChange,
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    label = { Text("Qty") }
                )
            }
        }
    }

    if (showAddCategoryDialog) {
        AlertDialog(
            onDismissRequest = { showAddCategoryDialog = false },
            title = { Text("Add category") },
            text = {
                OutlinedTextField(
                    value = pendingCategoryName,
                    onValueChange = { pendingCategoryName = it },
                    singleLine = true,
                    label = { Text("Category name") }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val newCategory = pendingCategoryName.trim()
                        if (newCategory.isNotBlank()) {
                            onAddCategory(newCategory)
                            onCategoryChange(newCategory)
                        }
                        pendingCategoryName = ""
                        showAddCategoryDialog = false
                    },
                    enabled = pendingCategoryName.trim().isNotBlank()
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    pendingCategoryName = ""
                    showAddCategoryDialog = false
                }) {
                    Text("Cancel")
                }
            }
        )
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
                    IconButton(onClick = onBackClick) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
private fun ScannerLaserOverlay(
    active: Boolean,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "scannerLaser")
    val laserColor = MaterialTheme.colorScheme.primary
    val sweepProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1300, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "laserSweepProgress"
    )
    val pulseProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "laserPulseProgress"
    )

    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
            val scanBandHalfWidth = size.width * 0.34f
            val scanY = size.height * (0.18f + sweepProgress * 0.62f)
            val pulseRadius = size.minDimension * (0.18f + pulseProgress * 0.02f)
            val ringAlpha = if (active) 0.42f else 0.18f
            val beamAlpha = if (active) 1f else 0.45f

            drawCircle(
                color = laserColor.copy(alpha = ringAlpha * (1f - pulseProgress * 0.45f)),
                radius = pulseRadius,
                center = center,
                style = Stroke(width = 6f)
            )
            drawCircle(
                color = laserColor.copy(alpha = ringAlpha * 0.5f),
                radius = pulseRadius * 1.42f,
                center = center,
                style = Stroke(width = 2f)
            )
            drawLine(
                color = laserColor.copy(alpha = beamAlpha),
                start = androidx.compose.ui.geometry.Offset(center.x - scanBandHalfWidth, scanY),
                end = androidx.compose.ui.geometry.Offset(center.x + scanBandHalfWidth, scanY),
                strokeWidth = if (active) 10f else 7f,
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
            drawLine(
                color = androidx.compose.ui.graphics.Color.White.copy(alpha = if (active) 0.45f else 0.18f),
                start = androidx.compose.ui.geometry.Offset(center.x - scanBandHalfWidth * 0.72f, scanY),
                end = androidx.compose.ui.geometry.Offset(center.x + scanBandHalfWidth * 0.72f, scanY),
                strokeWidth = 2f,
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(220.dp)
                .border(
                    width = 1.5.dp,
                    color = laserColor.copy(alpha = if (active) 0.42f else 0.22f),
                    shape = RoundedCornerShape(28.dp)
                )
        )

        Text(
            text = if (active) "Scanning object..." else "Ready to scan",
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 104.dp)
                .background(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.70f),
                    shape = RoundedCornerShape(999.dp)
                )
                .padding(horizontal = 14.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun BestMatchHeroCard(
    match: RankedMatch,
    matchCount: Int,
    onClick: () -> Unit
) {
    val item = match.item
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Best match",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Card(
                    shape = CircleShape,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(
                        text = "$matchCount matches",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ItemImageThumbnail(
                    imagePath = item.imagePath,
                    modifier = Modifier
                        .size(104.dp)
                        .clip(RoundedCornerShape(20.dp))
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = item.category,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f)
                    )
                    Text(
                        text = "Qty ${item.quantity} • ${item.price}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    val badgeLabel = when (match.badge) {
                        MatchBadge.PatternMatch -> "Pattern Match"
                        MatchBadge.TagMatch -> "Tag Match"
                        MatchBadge.DualMatch -> null
                    }
                    if (badgeLabel != null) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            )
                        ) {
                            Text(
                                text = badgeLabel,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }
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
private fun SearchResultCard(item: ClothingItem, badge: MatchBadge? = null, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
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
                if (badge != null) {
                    val badgeLabel = when (badge) {
                        MatchBadge.PatternMatch -> "Pattern Match"
                        MatchBadge.TagMatch -> "Tag Match"
                        MatchBadge.DualMatch -> null
                    }
                    if (badgeLabel != null) {
                        Text(
                            text = badgeLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
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
private fun FullImagePreviewDialog(
    imagePaths: List<String>,
    initialIndex: Int,
    onIndexChange: (Int) -> Unit,
    onRemoveCurrent: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    if (imagePaths.isEmpty()) {
        onDismiss()
        return
    }

    var selectedIndex by remember(imagePaths, initialIndex) {
        mutableStateOf(initialIndex.coerceIn(0, imagePaths.lastIndex))
    }
    var totalDragX by remember { mutableStateOf(0f) }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(12.dp)
        ) {
            ItemImageThumbnail(
                imagePath = imagePaths[selectedIndex],
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .border(
                        width = 1.5.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .pointerInput(selectedIndex, imagePaths.size) {
                        detectHorizontalDragGestures(
                            onHorizontalDrag = { _, dragAmount ->
                                totalDragX += dragAmount
                            },
                            onDragEnd = {
                                when {
                                    totalDragX <= -90f && selectedIndex < imagePaths.lastIndex -> {
                                        selectedIndex += 1
                                        onIndexChange(selectedIndex)
                                    }

                                    totalDragX >= 90f && selectedIndex > 0 -> {
                                        selectedIndex -= 1
                                        onIndexChange(selectedIndex)
                                    }
                                }
                                totalDragX = 0f
                            },
                            onDragCancel = {
                                totalDragX = 0f
                            }
                        )
                    }
            )

            Row(
                modifier = Modifier.align(Alignment.TopEnd),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = { onRemoveCurrent(selectedIndex) },
                    modifier = Modifier
                        .background(
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.92f),
                            shape = CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "Remove capture",
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .background(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
                            shape = CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close preview"
                    )
                }
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
    val currencies = listOf("USD", "MXN", "EUR", "NGN")
    val categories = listOf("Shirts", "Pants", "Denim", "Outerwear")
    var currencyExpanded by remember { mutableStateOf(false) }
    var categoryExpanded by remember { mutableStateOf(false) }
    val selectedCurrency = settings.currencyCode
    val selectedCategory = settings.defaultCategory
    val selectedCurrencyLabel = if (selectedCurrency == "NGN") "NGN (Naira)" else selectedCurrency

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
                Text("Currency: $selectedCurrencyLabel")
            }
            DropdownMenu(expanded = currencyExpanded, onDismissRequest = { currencyExpanded = false }) {
                currencies.forEach { currency ->
                    val displayLabel = if (currency == "NGN") "NGN (Naira)" else currency
                    DropdownMenuItem(
                        text = { Text(displayLabel) },
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
    onAutoSaveBatchesChange: (Boolean) -> Unit,
    onReducedConfettiEffectsChange: (Boolean) -> Unit,
    onGreenStockThresholdChange: (Int) -> Unit,
    onAmberStockThresholdChange: (Int) -> Unit
) {
    var greenThresholdText by rememberSaveable(settings.greenStockThreshold) { mutableStateOf(settings.greenStockThreshold.toString()) }
    var amberThresholdText by rememberSaveable(settings.amberStockThreshold) { mutableStateOf(settings.amberStockThreshold.toString()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SettingToggleRow("Haptic Feedback", settings.hapticFeedbackEnabled, onHapticFeedbackChange)
        SettingToggleRow("OCR Sensitivity (High)", settings.highOcrSensitivity, onOcrSensitivityChange)
        SettingToggleRow("Auto-Save Batches", settings.autoSaveBatches, onAutoSaveBatchesChange)
        SettingToggleRow("Reduced Confetti", settings.reducedConfettiEffects, onReducedConfettiEffectsChange)

        Text(text = "Stock thresholds", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        OutlinedTextField(
            value = greenThresholdText,
            onValueChange = {
                val filtered = it.filter { ch -> ch.isDigit() }
                greenThresholdText = filtered
                filtered.toIntOrNull()?.let(onGreenStockThresholdChange)
            },
            label = { Text("Green at or above") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = amberThresholdText,
            onValueChange = {
                val filtered = it.filter { ch -> ch.isDigit() }
                amberThresholdText = filtered
                filtered.toIntOrNull()?.let(onAmberStockThresholdChange)
            },
            label = { Text("Amber at or above") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private fun saveBitmapToCollectionFile(context: Context, bitmap: Bitmap): String {
    val imageDir = File(context.filesDir, "collection_images")
    if (!imageDir.exists()) {
        imageDir.mkdirs()
    }
    val outputFile = File(imageDir, "collection_${System.currentTimeMillis()}.jpg")
    FileOutputStream(outputFile).use { stream ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, 92, stream)
    }
    return outputFile.absolutePath
}

private fun copyUriToCollectionFile(context: Context, uri: android.net.Uri): String {
    val imageDir = File(context.filesDir, "collection_images")
    if (!imageDir.exists()) {
        imageDir.mkdirs()
    }
    val outputFile = File(imageDir, "collection_${System.currentTimeMillis()}.jpg")
    context.contentResolver.openInputStream(uri)?.use { input ->
        FileOutputStream(outputFile).use { output ->
            input.copyTo(output)
        }
    }
    return outputFile.absolutePath
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

private suspend fun captureSingleFrame(
    cameraController: LifecycleCameraController,
    context: Context
): File? = suspendCancellableCoroutine { continuation ->
    val photoFile = createBatchImageFile(context)
    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
    cameraController.takePicture(
        outputOptions,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                if (continuation.isActive) {
                    continuation.resume(photoFile)
                }
            }

            override fun onError(exception: ImageCaptureException) {
                runCatching { photoFile.delete() }
                if (continuation.isActive) {
                    continuation.resume(null)
                }
            }
        }
    )
}

private suspend fun selectGoldenFrame(frames: List<File>): File? = withContext(Dispatchers.Default) {
    var bestFile: File? = null
    var bestScore = Double.NEGATIVE_INFINITY

    frames.forEach { file ->
        val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return@forEach
        val sharpness = ImageSharpness.score(bitmap)
        if (sharpness > bestScore) {
            bestScore = sharpness
            bestFile = file
        }
    }

    bestFile
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

private fun sanitizePriceInputForField(raw: String): String {
    val filtered = raw.filter { it.isDigit() || it == '.' }
    if (filtered.isEmpty()) return ""

    val firstDotIndex = filtered.indexOf('.')
    if (firstDotIndex < 0) return filtered

    val head = filtered.substring(0, firstDotIndex + 1)
    val tail = filtered.substring(firstDotIndex + 1).replace(".", "")
    return head + tail
}

private fun sanitizeQuantityInputForField(raw: String): String {
    val digitsOnly = raw.filter { it.isDigit() }
    if (digitsOnly.isEmpty()) return ""

    return digitsOnly.trimStart('0')
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

