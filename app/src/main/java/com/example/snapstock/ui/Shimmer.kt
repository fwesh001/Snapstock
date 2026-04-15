package com.example.snapstock.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp

@Composable
fun ShimmerBlock(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(12.dp)
) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerProgress"
    )

    val base = MaterialTheme.colorScheme.surfaceVariant
    val highlight = MaterialTheme.colorScheme.surface
    val brush = Brush.linearGradient(
        colors = listOf(base.copy(alpha = 0.70f), highlight.copy(alpha = 0.95f), base.copy(alpha = 0.70f)),
        start = androidx.compose.ui.geometry.Offset(progress * 800f - 400f, 0f),
        end = androidx.compose.ui.geometry.Offset(progress * 800f, 800f)
    )

    Spacer(
        modifier = modifier
            .background(brush = brush, shape = shape)
    )
}

@Composable
fun DashboardShimmer() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            ShimmerBlock(modifier = Modifier.weight(1f).height(76.dp))
            ShimmerBlock(modifier = Modifier.weight(1f).height(76.dp))
            ShimmerBlock(modifier = Modifier.weight(1f).height(76.dp))
        }
        ShimmerBlock(modifier = Modifier.fillMaxWidth().height(320.dp), shape = RoundedCornerShape(18.dp))
    }
}

@Composable
fun SearchShimmer() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(4) {
            ShimmerBlock(modifier = Modifier.fillMaxWidth().height(92.dp), shape = RoundedCornerShape(14.dp))
        }
    }
}

@Composable
fun CollectionShimmerGrid() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(3) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                ShimmerBlock(modifier = Modifier.weight(1f).height(220.dp), shape = RoundedCornerShape(18.dp))
                ShimmerBlock(modifier = Modifier.weight(1f).height(220.dp), shape = RoundedCornerShape(18.dp))
            }
        }
    }
}

@Composable
fun SettingsShimmerRows() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        repeat(5) {
            Card(shape = RoundedCornerShape(12.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    ShimmerBlock(modifier = Modifier.fillMaxWidth(0.55f).height(20.dp))
                    ShimmerBlock(modifier = Modifier.size(44.dp, 24.dp), shape = RoundedCornerShape(999.dp))
                }
            }
        }
    }
}
