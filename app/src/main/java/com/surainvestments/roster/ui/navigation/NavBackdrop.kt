package com.surainvestments.roster.ui.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp

/**
 * Bottom clearance root-tab screens add as content padding so their last items can scroll
 * clear of the floating dock while still drawing beneath it. Defaults to zero so screens
 * remain correct when hosted outside the tab scaffolds.
 */
val LocalNavBarPadding = staticCompositionLocalOf { PaddingValues(0.dp) }
