package com.example.snapstock.ui

import android.Manifest
import android.content.ClipData
import android.content.Intent
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    var didCapture by rememberSaveable { mutableStateOf(false) }
    var isScanning by rememberSaveable { mutableStateOf(false) }

    val cameraController = remember(context) {
        LifecycleCameraController(context).apply {
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
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

    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }
    var previousSignature by remember { mutableStateOf<ImageSignature?>(null) }

    LaunchedEffect(hasCameraPermission, isScanning, didCapture) {
        if (!hasCameraPermission || !isScanning || didCapture) return@LaunchedEffect

        repeat(8) {
            delay(450)
            if (didCapture) return@LaunchedEffect

            val bitmap = previewViewRef?.bitmap ?: return@repeat
            val currentSignature = ImageMatcher.buildSignature(bitmap)
            val stable = previousSignature?.let {
                ImageMatcher.hammingDistance(it.averageHash, currentSignature.averageHash) <= 10
            } ?: false

            previousSignature = currentSignature

            if (stable) {
                val photoFile = createBatchImageFile(context)
                withContext(Dispatchers.Default) {
                    FileOutputStream(photoFile).use { output ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)
                    }
                }
                searchViewModel.onImageScanned(photoFile.absolutePath)
                didCapture = true
                onDoneClick()
                return@LaunchedEffect
            }
        }

        if (!didCapture) {
            val bitmap = previewViewRef?.bitmap
            if (bitmap != null) {
                val photoFile = createBatchImageFile(context)
                withContext(Dispatchers.Default) {
                    FileOutputStream(photoFile).use { output ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)
                    }
                }
                searchViewModel.onImageScanned(photoFile.absolutePath)
                didCapture = true
                onDoneClick()
            }
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
                                previewViewRef = this
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

                if (isScanning) {
                    ScannerLaserOverlay()
                } else {
                    Text(
                        text = "Tap shutter to start scanning",
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 108.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 18.dp)
                ) {
                    IconButton(
                        onClick = {
                            if (isScanning || didCapture) return@IconButton
                            isScanning = true
                        },
                        modifier = Modifier
                            .size(92.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.22f))
                            .border(
                                width = 1.dp,
                                color = if (isScanning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.32f),
                                shape = CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PhotoCamera,
                            contentDescription = if (isScanning) "Scanning" else "Start scanning",
                            modifier = Modifier.size(54.dp),
                            tint = if (isScanning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
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

            if (uiState.pendingTodos.isNotEmpty()) {
                item {
                    TodoReminderCard(
                        todo = uiState.pendingTodos.first(),
                        items = uiState.items,
                        onContinue = onCollectionClick
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
            if (todoItems.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(todoItems, key = { it.id }) { item ->
                        ItemImageThumbnail(
                            imagePath = item.imagePath,
                            modifier = Modifier.size(64.dp)
                        )
                    }
                }
            }
            Button(
                onClick = onContinue,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary
                )
            ) {
                Text("Continue editing")
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
    onCameraClick: () -> Unit = {}
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
                            contentDescription = "Open scanner"
                        )
                    }
                }
            }

            if (uiState.scannedImage != null && uiState.topMatches.isNotEmpty()) {
                item {
                    SearchBestMatchHero(
                        item = uiState.topMatches.first(),
                        onClick = {
                            selectedItem = uiState.topMatches.first()
                            isEditing = false
                        }
                    )
                }

                item {
                    Text(
                        text = "Top matches",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                items(uiState.topMatches.drop(1), key = { it.id }) { item ->
                    SearchResultCard(item = item, onClick = {
                        selectedItem = item
                        isEditing = false
                    })
                }
            }

            when {
                uiState.query.isBlank() && uiState.scannedImage == null -> {
                    item { SearchPromptCard() }
                }

                uiState.isSearching -> {
                    item { SearchLoadingCard() }
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
    var isScanning by rememberSaveable { mutableStateOf(false) }
        )
    }
    var didCapture by rememberSaveable { mutableStateOf(false) }

    val cameraController = remember(context) {
        LifecycleCameraController(context).apply {
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
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

    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }
    var previousSignature by remember { mutableStateOf<ImageSignature?>(null) }

    LaunchedEffect(hasCameraPermission, didCapture) {
        if (!hasCameraPermission || didCapture) return@LaunchedEffect

        repeat(8) {
            delay(450)
            if (didCapture) return@LaunchedEffect

            val bitmap = previewViewRef?.bitmap ?: return@repeat
            val currentSignature = ImageMatcher.buildSignature(bitmap)
            val stable = previousSignature?.let {
                ImageMatcher.hammingDistance(it.averageHash, currentSignature.averageHash) <= 10
            } ?: false

            previousSignature = currentSignature

            if (stable) {
                val photoFile = createBatchImageFile(context)
                withContext(Dispatchers.Default) {
                    FileOutputStream(photoFile).use { output ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)
                    }
                }

                searchViewModel.onImageScanned(photoFile.absolutePath)
                didCapture = true
                onDoneClick()
                return@LaunchedEffect
            }
        }

        if (!didCapture) {
            val bitmap = previewViewRef?.bitmap
            if (bitmap != null) {
                val photoFile = createBatchImageFile(context)
                withContext(Dispatchers.Default) {
                    FileOutputStream(photoFile).use { output ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)
                    }
                }
                searchViewModel.onImageScanned(photoFile.absolutePath)
                didCapture = true
                onDoneClick()
                if (!isScanning) {
                    Text(
                        text = "Tap shutter to start scanning",
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 108.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelLarge
                    )
                } else {
                    ScannerLaserOverlay()
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 18.dp)
                ) {
                    IconButton(
                        onClick = {
                            if (isScanning || didCapture) return@IconButton
                            isScanning = true
                        },
                        modifier = Modifier
                            .size(92.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.22f))
                            .border(
                                width = 1.dp,
                                color = if (isScanning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.32f),
                                shape = CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PhotoCamera,
                            contentDescription = if (isScanning) "Scanning" else "Start scanning",
                            modifier = Modifier.size(54.dp),
                            tint = if (isScanning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
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
                                previewViewRef = this
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

                Text(
                    text = "Auto-detecting… hold steady",
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 18.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelLarge
                )
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
    val tabs = listOf(
        "Personalization" to Icons.Filled.Storefront,
        "Performance" to Icons.Filled.Speed,
        "Safety Zone" to Icons.Filled.Shield
    )
    var selectedTabIndex by rememberSaveable { mutableStateOf(0) }
    val settingsViewModel: SettingsViewModel = viewModel()
    val settingsState by settingsViewModel.uiState.collectAsState()
    val actionState by settingsViewModel.actionState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var confirmCleanup by rememberSaveable { mutableStateOf(false) }
    var confirmReset by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        settingsViewModel.events.collect { effect ->
            when (effect) {
                is SettingsSideEffect.Message -> snackbarHostState.showSnackbar(effect.text)
                is SettingsSideEffect.ShareExport -> {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = effect.mimeType
                        putExtra(Intent.EXTRA_STREAM, effect.uri)
                        clipData = ClipData.newRawUri("snapstock_export", effect.uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Share export"))
                }
            }
        }
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
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            TabRow(selectedTabIndex = selectedTabIndex) {
                tabs.forEachIndexed { index, (title, icon) ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        icon = { Icon(imageVector = icon, contentDescription = null) },
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
                    onAutoSaveBatchesChange = settingsViewModel::updateAutoSaveBatches,
                    onGreenStockThresholdChange = settingsViewModel::updateGreenStockThreshold,
                    onAmberStockThresholdChange = settingsViewModel::updateAmberStockThreshold
                )
                else -> SafetyZoneTab(
                    isBusy = actionState.isBusy,
                    busyLabel = actionState.busyLabel,
                    onCompress = settingsViewModel::compressImages,
                    onCleanup = { confirmCleanup = true },
                    onReset = { confirmReset = true },
                    onExport = settingsViewModel::exportData
                )
            }
        }
    }

    if (confirmCleanup) {
        AlertDialog(
            onDismissRequest = { confirmCleanup = false },
            title = { Text("Clean up database") },
            text = { Text("This will remove completed To Do entries and orphaned image files. Continue?") },
            confirmButton = {
                Button(onClick = {
                    confirmCleanup = false
                    settingsViewModel.cleanDatabase()
                }) {
                    Text("Clean up")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmCleanup = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Factory reset") },
            text = { Text("This clears settings, inventory, To Do entries, and local image files. Continue?") },
            confirmButton = {
                Button(
                    onClick = {
                        confirmReset = false
                        settingsViewModel.factoryReset()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Reset")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) {
                    Text("Cancel")
                }
            }
        )
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

    LaunchedEffect(Unit) {
        batchEntryViewModel.startNewSession()
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
    onSaveComplete: () -> Unit,
    batchEntryViewModel: BatchEntryViewModel
) {
    val uiState by batchEntryViewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showConfettiPlaceholder by rememberSaveable { mutableStateOf(false) }
    var showSaveChooser by rememberSaveable { mutableStateOf(false) }

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
                    IconButton(onClick = onBackClick) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            Button(
                onClick = { showSaveChooser = true },
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
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
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

        if (showSaveChooser) {
            AlertDialog(
                onDismissRequest = { showSaveChooser = false },
                title = { Text("Save batch") },
                text = { Text("Save this inventory now, or save it and create a To Do for finishing the details later.") },
                confirmButton = {
                    Button(onClick = {
                        showSaveChooser = false
                        batchEntryViewModel.saveBatch(createTodo = true)
                    }) {
                        Text("Save + To Do")
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showSaveChooser = false
                        batchEntryViewModel.saveBatch(createTodo = false)
                    }) {
                        Text("Save")
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionScreen(
    onHomeClick: () -> Unit,
    onCollectionClick: () -> Unit,
    onSettingsClick: () -> Unit,
    collectionViewModel: CollectionViewModel = viewModel()
) {
    val items by collectionViewModel.items.collectAsState()
    val settingsViewModel: SettingsViewModel = viewModel()
    val settingsState by settingsViewModel.uiState.collectAsState()
    var selectedCategory by rememberSaveable { mutableStateOf("All") }
    var selectedItem by rememberSaveable { mutableStateOf<ClothingItem?>(null) }
    var isEditing by rememberSaveable { mutableStateOf(false) }
    val categories = remember(items) { listOf("All") + items.map { it.category }.distinct().sorted() }
    val filteredItems = remember(items, selectedCategory) {
        if (selectedCategory == "All") items else items.filter { it.category == selectedCategory }
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
                    items(categories) { category ->
                        FilterChipLike(
                            label = category,
                            selected = category == selectedCategory,
                            onClick = { selectedCategory = category }
                        )
                    }
                }
            }

            if (filteredItems.isEmpty()) {
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
                }
            )
        }
    }
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
    onGalleryPick: (String) -> Unit
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
        Card(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        rotationY = rotation
                        cameraDistance = 16 * density.density
                    }
                    .padding(16.dp)
            ) {
                if (rotation < 90f) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        ItemImageThumbnail(
                            imagePath = item.imagePath,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(230.dp)
                                .clip(RoundedCornerShape(18.dp))
                        )
                        Text(item.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text("Category: ${item.category}")
                        Text("Qty: ${item.quantity}   Price: ${item.price}")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = onEditToggle) { Icon(Icons.Filled.Edit, contentDescription = null); Text("Edit") }
                            TextButton(onClick = onDismiss) { Text("Close") }
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.graphicsLayer { rotationY = 180f }) {
                        ItemImageThumbnail(
                            imagePath = item.imagePath,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .clip(RoundedCornerShape(18.dp))
                        )
                        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = category, onValueChange = { category = it }, label = { Text("Category") }, modifier = Modifier.fillMaxWidth())
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = price, onValueChange = { price = it }, label = { Text("Price") }, modifier = Modifier.weight(1f))
                            OutlinedTextField(value = quantity, onValueChange = { quantity = it }, label = { Text("Qty") }, modifier = Modifier.weight(1f))
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
private fun SearchResultCard(item: ClothingItem, onClick: () -> Unit) {
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
    val selectedCurrencyLabel = if (selectedCurrency == "NGN") "₦" else selectedCurrency

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
                    val displayLabel = if (currency == "NGN") "₦" else currency
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
private fun SafetyZoneTab(
    isBusy: Boolean,
    busyLabel: String,
    onCompress: () -> Unit,
    onCleanup: () -> Unit,
    onReset: () -> Unit,
    onExport: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Safety Zone",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Text(
                    text = if (isBusy) busyLabel else "Amber / danger controls for app maintenance.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
        }

        Button(
            onClick = onCompress,
            enabled = !isBusy,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary
            )
        ) {
            Text(if (isBusy && busyLabel.contains("Compress", ignoreCase = true)) "Compressing..." else "Image Compression")
        }

        OutlinedButton(onClick = onCleanup, modifier = Modifier.fillMaxWidth(), enabled = !isBusy) {
            Text("Database Cleanup")
        }

        Button(
            onClick = onExport,
            enabled = !isBusy,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(imageVector = Icons.Filled.Share, contentDescription = null)
            Spacer(modifier = Modifier.size(8.dp))
            Text("Export Data")
        }

        Button(
            onClick = onReset,
            enabled = !isBusy,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError
            )
        ) {
            Text(if (isBusy && busyLabel.contains("Reset", ignoreCase = true)) "Resetting..." else "Factory Reset")
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

