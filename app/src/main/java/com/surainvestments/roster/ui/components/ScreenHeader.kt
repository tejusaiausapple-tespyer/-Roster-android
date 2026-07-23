package com.surainvestments.roster.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.surainvestments.roster.ui.theme.ScreenPadding

/** Total height of [ScreenPillTopBar]'s row — reserve this as top spacing in scrollable content beneath it. */
val ScreenPillTopBarHeight: Dp = 60.dp

/**
 * Centered screen-title pill (+ optional leading back button), meant to float
 * fixed above scrolling content — Android analogue of iOS's `ScreenTitlePill`
 * living in the nav bar's `.principal` slot. Pair with [TopEdgeFade] and place
 * both on top of scrollable content inside a `Box`, so the pill stays put
 * while content scrolls and fades out beneath it.
 */
@Composable
fun ScreenPillTopBar(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    onBack: (() -> Unit)? = null,
    onAdd: (() -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenPadding, vertical = 8.dp),
    ) {
        if (onBack != null) {
            BackPillButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart))
        }
        ScreenTitlePill(title = title, icon = icon, modifier = Modifier.align(Alignment.Center))
        if (onAdd != null) {
            AddPillButton(onClick = onAdd, modifier = Modifier.align(Alignment.CenterEnd))
        }
    }
}

/**
 * Edge gradient scrim — content fades out as it scrolls up beneath a fixed
 * header. Mirrors iOS's `ScrollFadeHintsOverlay` top-hint gradient.
 */
@Composable
fun TopEdgeFade(
    modifier: Modifier = Modifier,
    height: Dp = ScreenPillTopBarHeight,
    color: Color = MaterialTheme.colorScheme.background,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(
                Brush.verticalGradient(
                    colors = listOf(color, color.copy(alpha = 0.85f), color.copy(alpha = 0f)),
                ),
            ),
    )
}
