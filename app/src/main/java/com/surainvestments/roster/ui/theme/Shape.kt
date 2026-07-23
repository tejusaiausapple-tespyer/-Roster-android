package com.surainvestments.roster.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Corner radii mirrored from iOS `Theme` (10 / 14 / 20) so cards, fields,
 * and buttons feel like the same product across platforms.
 */
val RosterraShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

val ScreenPadding = 16.dp
val ContentMaxWidth = 720.dp
