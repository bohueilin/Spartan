package com.spartan.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import com.spartan.ui.theme.Motion
import com.spartan.ui.theme.rememberReducedMotion

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
    // The one permitted constant motion: a gentle alpha pulse that signals a genuine ongoing
    // process, alive only while loading. Static at the brighter value under reduced motion.
    val infinite = rememberInfiniteTransition(label = "sk")
    val alpha by infinite.animateFloat(
        initialValue = 0.55f, targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(Motion.slow), RepeatMode.Reverse),
        label = "skAlpha",
    )
    val shown = if (rememberReducedMotion()) 0.9f else alpha
    Box(modifier.clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = shown)))
}
