package com.surainvestments.roster.ui.components

import androidx.compose.animation.core.animate
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt

/**
 * A trailing swipe-to-reveal row — the Android-idiomatic pattern for what iOS implements as
 * `.swipeActions(edge: .trailing)`, used for the Roster tab's Submit/Report Absence/Undo/Resubmit
 * actions (`ANDROID-STAFF-BUILD-PLAN.md`'s own Android-adaptation note called for exactly this
 * gesture, not a literal `.swipeActions` port). [actions] sit behind [content] at the trailing
 * edge; dragging [content] left reveals them, past the halfway point of their measured width snaps
 * fully open, otherwise it springs back closed. Each row manages its own open/closed state
 * independently — there's no cross-row "close others when one opens" coordination, matching the
 * scope of a first correct implementation over a more elaborate list-wide one.
 */
@Composable
fun SwipeToRevealActions(
    actions: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var offsetX by remember { mutableFloatStateOf(0f) }
    var actionsWidthPx by remember { mutableFloatStateOf(0f) }

    val draggableState = rememberDraggableState { delta ->
        offsetX = (offsetX + delta).coerceIn(-actionsWidthPx, 0f)
    }

    Box(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .onSizeChanged { size -> actionsWidthPx = size.width.toFloat() },
            content = actions,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.roundToInt(), 0) }
                .background(MaterialTheme.colorScheme.surface)
                .draggable(
                    state = draggableState,
                    orientation = Orientation.Horizontal,
                    onDragStopped = { velocity ->
                        val target = if (offsetX < -actionsWidthPx / 2f) -actionsWidthPx else 0f
                        animate(initialValue = offsetX, targetValue = target, initialVelocity = velocity) { value, _ ->
                            offsetX = value
                        }
                    },
                ),
        ) {
            content()
        }
    }
}
