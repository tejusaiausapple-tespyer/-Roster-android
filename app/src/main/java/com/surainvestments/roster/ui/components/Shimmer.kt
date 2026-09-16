package com.surainvestments.roster.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.surainvestments.roster.ui.theme.ScreenPadding

/**
 * A subtle sweeping-gradient shimmer, hand-rolled (no extra dependency) since it's just an
 * infinite-transition-driven `Brush` — the same technique every common shimmer library uses
 * internally. Base/highlight tones derive from `onSurface` so it looks correct in both themes
 * without a separate dark-mode palette.
 */
@Composable
fun Modifier.shimmer(shape: Shape = RoundedCornerShape(10.dp)): Modifier {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translate by transition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerTranslate",
    )
    val base = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
    val highlight = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)

    return this
        .clip(shape)
        .drawWithCache {
            val width = size.width.coerceAtLeast(1f)
            val brush = Brush.linearGradient(
                colors = listOf(base, highlight, base),
                start = Offset(width * (translate - 0.3f), 0f),
                end = Offset(width * (translate + 0.3f), 0f),
            )
            onDrawBehind { drawRect(brush) }
        }
}

/** A single shimmering placeholder shape — the building block every skeleton layout composes from. */
@Composable
fun SkeletonBox(modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(10.dp)) {
    Box(modifier = modifier.shimmer(shape))
}

/**
 * Generic full-screen loading placeholder for every listener-backed staff screen — a top-down
 * stack of card-shaped shimmer boxes matching the width/padding of the real `LazyColumn` content
 * that replaces it once the first snapshot arrives, so there's no visual "jump" between the two.
 * Deliberately one shared shape rather than a bespoke skeleton per screen (Home/Roster/Tasks/
 * Availability/History/Payslips all render a vertical list of roughly card-shaped rows) — a
 * pixel-perfect per-screen skeleton wasn't judged worth the extra maintenance surface for a
 * loading state that's on screen for well under a second in the common case.
 */
@Composable
fun ScreenLoadingSkeleton(
    modifier: Modifier = Modifier,
    itemCount: Int = 4,
    itemHeight: Dp = 76.dp,
    topPadding: Dp = ScreenPillTopBarHeight + 16.dp,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(top = topPadding)
            .padding(horizontal = ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(itemCount) {
            SkeletonBox(
                modifier = Modifier.fillMaxWidth().height(itemHeight),
                shape = MaterialTheme.shapes.medium,
            )
        }
    }
}
