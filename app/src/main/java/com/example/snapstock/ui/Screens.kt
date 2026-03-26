package com.example.snapstock.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onBatchCaptureClick: () -> Unit,
    dashboardViewModel: DashboardViewModel = viewModel()
) {
    val uiState by dashboardViewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(text = "SnapStock", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    TextButton(onClick = onSettingsClick) {
                        Text(text = "Settings")
                    }
                },
                actions = {
                    TextButton(onClick = onSearchClick) {
                        Text(text = "Search")
                    }
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
                    totalInventoryValue = uiState.totalInventoryValue
                )
            }

            item {
                LastActionCard(uiState = uiState)
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
private fun StatsRow(totalItems: Int, lowStockCount: Int, totalInventoryValue: Double) {
    val currencyFormatter = NumberFormat.getCurrencyInstance(Locale.getDefault())

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
private fun LastActionCard(uiState: DashboardUiState) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = "Last Action", style = MaterialTheme.typography.labelLarge)
            if (uiState.lastAddedItem == null) {
                Text(text = "No items yet. Your first Snap will appear here.")
            } else {
                Text(
                    text = "Added ${uiState.lastAddedItem.name}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(text = "Category: ${uiState.lastAddedItem.category}")
                Text(text = "Quantity: ${uiState.lastAddedItem.quantity}")
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
                text = "Tap the highlighted Snap button to capture your first item.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun HighlightedFab(showHighlight: Boolean, onClick: () -> Unit) {
    Box(contentAlignment = Alignment.Center) {
        if (showHighlight) {
            val transition = rememberInfiniteTransition(label = "fabPulse")
            val radiusScale by transition.animateFloat(
                initialValue = 1f,
                targetValue = 1.5f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 1100),
                    repeatMode = RepeatMode.Restart
                ),
                label = "fabPulseScale"
            )
            val alpha by transition.animateFloat(
                initialValue = 0.35f,
                targetValue = 0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 1100),
                    repeatMode = RepeatMode.Restart
                ),
                label = "fabPulseAlpha"
            )

            Canvas(
                modifier = Modifier
                    .size(88.dp)
                    .alpha(alpha)
            ) {
                drawCircle(
                    color = MaterialTheme.colorScheme.primary,
                    radius = (size.minDimension / 2f) * radiusScale
                )
            }
        }

        FloatingActionButton(onClick = onClick) {
            Text(text = "Snap", fontWeight = FontWeight.Bold)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(onBackClick: () -> Unit) {
    PlaceholderScreen(title = "Search", subtitle = "Unified Vision Search will be implemented next.", onBackClick = onBackClick)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBackClick: () -> Unit) {
    PlaceholderScreen(title = "Settings", subtitle = "Personalization, Performance, and Safety Zone tabs coming next.", onBackClick = onBackClick)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchCaptureScreen(onBackClick: () -> Unit) {
    PlaceholderScreen(title = "Batch Capture", subtitle = "CameraX rapid capture flow will be implemented next.", onBackClick = onBackClick)
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

