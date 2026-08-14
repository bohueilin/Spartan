package com.spartan.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/**
 * The shared loading-skeleton language: rounded 10dp blocks in surfaceVariant, used by every tab
 * while its first data load is in flight (and never on sync failure).
 */
@Composable
internal fun SkeletonRow(widthFraction: Float) {
    Skeleton(Modifier.fillMaxWidth(widthFraction).height(14.dp))
}

@Composable
internal fun Skeleton(modifier: Modifier) {
    val alpha by animateFloatAsState(0.9f, tween(600), label = "sk")
    Box(modifier.clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f * alpha)))
}
