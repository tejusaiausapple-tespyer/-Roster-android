package com.surainvestments.roster.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Faded chevron hints at the top/bottom edges of a scrollable [LazyListState], shown only while
 * there's more content to scroll to in that direction — Android analogue of iOS
 * `ScrollFadeHintsOverlay` / `FadedScrollView` (`DesignSystem/Components/ScrollFadeHints.swift`).
 * `canScrollBackward`/`canScrollForward` already give us what iOS computes manually via
 * GeometryReader + PreferenceKey offset tracking.
 *
 * Place inside a `Box` alongside the scrollable content (e.g. with `Modifier.matchParentSize()`),
 * not wrapping it — purely decorative, never intercepts touch/scroll.
 */
@Composable
fun ScrollFadeHints(
    listState: LazyListState,
    modifier: Modifier = Modifier,
    fadeColor: Color = MaterialTheme.colorScheme.surface,
    showsChevrons: Boolean = true,
) {
    Box(modifier = modifier.fillMaxWidth()) {
        AnimatedVisibility(
            visible = listState.canScrollBackward,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(200)),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(30.dp)
                    .background(Brush.verticalGradient(listOf(fadeColor.copy(alpha = 0.92f), fadeColor.copy(alpha = 0f)))),
                contentAlignment = Alignment.BottomCenter,
            ) {
                if (showsChevrons) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowUp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                        modifier = Modifier.padding(bottom = 2.dp),
                    )
                }
            }
        }
        AnimatedVisibility(
            visible = listState.canScrollForward,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(200)),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp)
                    .background(Brush.verticalGradient(listOf(fadeColor.copy(alpha = 0f), fadeColor.copy(alpha = 0.92f)))),
                contentAlignment = Alignment.TopCenter,
            ) {
                if (showsChevrons) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}
