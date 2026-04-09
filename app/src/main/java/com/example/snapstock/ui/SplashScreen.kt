package com.example.snapstock.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.delay

@Composable
fun SplashScreenContent(onAnimationEnd: () -> Unit) {
    val visibleWordCount = remember { mutableIntStateOf(0) }
    val snapAlpha by animateFloatAsState(
        targetValue = if (visibleWordCount.intValue >= 1) 1f else 0f,
        animationSpec = tween(durationMillis = 250),
        label = "snapAlpha"
    )
    val scanAlpha by animateFloatAsState(
        targetValue = if (visibleWordCount.intValue >= 2) 1f else 0f,
        animationSpec = tween(durationMillis = 250),
        label = "scanAlpha"
    )
    val stockedAlpha by animateFloatAsState(
        targetValue = if (visibleWordCount.intValue >= 3) 1f else 0f,
        animationSpec = tween(durationMillis = 250),
        label = "stockedAlpha"
    )

    LaunchedEffect(Unit) {
        delay(500)
        visibleWordCount.intValue = 1
        delay(300)
        visibleWordCount.intValue = 2
        delay(300)
        visibleWordCount.intValue = 3
        delay(350)
        onAnimationEnd()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.primary),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Snap.",
            style = MaterialTheme.typography.displayMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = snapAlpha)
        )
        Text(
            text = "Scan.",
            style = MaterialTheme.typography.displayMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = scanAlpha)
        )
        Text(
            text = "Stocked.",
            style = MaterialTheme.typography.displayMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = stockedAlpha)
        )
    }
}
