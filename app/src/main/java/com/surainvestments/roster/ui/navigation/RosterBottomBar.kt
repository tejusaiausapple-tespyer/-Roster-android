package com.surainvestments.roster.ui.navigation

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.surainvestments.roster.ui.components.HapticEvent
import com.surainvestments.roster.ui.components.Haptics

/** Standard Material 3 bottom-nav height with labels always shown. Public so root screens can reserve content clearance. */
val BottomNavHeight = 80.dp

/**
 * Standard Material 3 bottom navigation bar — replaces an earlier custom floating "glass pill"
 * dock (replaced 2026-07-28: the custom version read as slow/gimmicky in practice). This is
 * deliberately just `NavigationBar`/`NavigationBarItem` with the framework's own defaults for
 * ripple, motion, and accessibility — no hand-rolled shadow, blur, or spring-physics styling.
 * Flush with the bottom edge, full width, opaque surface background; `NavigationBar`'s own default
 * `windowInsets` already pads for the system gesture/navigation-bar area, so callers don't need to
 * add that themselves.
 */
@Composable
fun RosterBottomBar(
    tabs: List<BottomTab>,
    selectedRoute: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current

    NavigationBar(
        modifier = modifier.clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
    ) {
        tabs.forEach { tab ->
            val isSelected = selectedRoute == tab.route
            NavigationBarItem(
                selected = isSelected,
                onClick = {
                    if (!isSelected) Haptics.perform(haptics, HapticEvent.TabChange)
                    onSelect(tab.route)
                },
                icon = { TabIcon(tab = tab, selected = isSelected) },
                label = {
                    Text(
                        text = tab.label,
                        fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                    )
                },
                alwaysShowLabel = true,
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}

/** Material navigation rail for medium and expanded Staff layouts. */
@Composable
fun RosterNavigationRail(
    tabs: List<BottomTab>,
    selectedRoute: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    NavigationRail(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        tabs.forEach { tab ->
            val isSelected = selectedRoute == tab.route
            NavigationRailItem(
                selected = isSelected,
                onClick = {
                    if (!isSelected) Haptics.perform(haptics, HapticEvent.TabChange)
                    onSelect(tab.route)
                },
                icon = { TabIcon(tab = tab, selected = isSelected) },
                label = { Text(tab.label, fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal) },
                alwaysShowLabel = false,
            )
        }
    }
}

/** Icon scale is the one intentional motion touch the spec calls for — 1.0 → 1.1 over 200ms, standard easing. */
@Composable
private fun TabIcon(tab: BottomTab, selected: Boolean) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.1f else 1f,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "tab-icon-scale",
    )
    Icon(
        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
        // Null, not tab.label — the always-visible label below already supplies the accessible
        // name; NavigationBarItem merges icon+label+selected state into one TalkBack node, so a
        // contentDescription here would just announce the label twice.
        contentDescription = null,
        modifier = Modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
    )
}

data class BottomTab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

fun StaffTab.toBottomTab() = BottomTab(route, label, selectedIcon, unselectedIcon)
