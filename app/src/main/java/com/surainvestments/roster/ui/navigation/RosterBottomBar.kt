package com.surainvestments.roster.ui.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

private val DockShape = RoundedCornerShape(percent = 50)
private val PillShape = RoundedCornerShape(percent = 50)

/** Dock dimensions, public so root screens can compute content clearance. */
val DockVisualHeight = 54.dp
val DockOuterVerticalPadding = 6.dp

private val SlotHeight = 46.dp
private val InactiveSlotWidth = 46.dp
private val SlotRowHorizontalPadding = 6.dp
private val PillHPad = 14.dp
private val IconSize = 20.dp
private val IconLabelGap = 6.dp

// No-bounce springs tuned for a clearly visible ~200-250ms glide: high stiffness reads as
// a teleport, low damping reads as mushy overshoot — this sits between the two.
private val PillOffsetSpring = spring<Float>(dampingRatio = 0.9f, stiffness = 400f)
private val PillWidthSpring = spring<Float>(dampingRatio = 0.9f, stiffness = 500f)
private val SlotWidthSpring = spring<Dp>(dampingRatio = 0.9f, stiffness = 500f)

/**
 * Floating glass dock: a translucent tinted bar (no real-time backdrop blur — flat tint
 * only, cheap to draw every frame) with a single pill that slides between tabs on
 * retargeting springs with a velocity-driven squash/stretch.
 */
@Composable
fun RosterBottomBar(
    tabs: List<BottomTab>,
    selectedRoute: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val surface = MaterialTheme.colorScheme.surface
    val haptics = LocalHapticFeedback.current

    var dockCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val slotCoords = remember { HashMap<String, LayoutCoordinates>() }
    val slotRects = remember { mutableStateMapOf<String, Rect>() }
    val pillContentWidths = remember { mutableStateMapOf<String, Dp>() }

    val pillX = remember { Animatable(0f) }
    val pillW = remember { Animatable(0f) }
    var pillInitialized by remember { mutableStateOf(false) }

    val selectedTab = tabs.firstOrNull { it.route == selectedRoute }
    // Keeps the pill populated during transient null routes (e.g. mid back-stack swap).
    var lastSelectedTab by remember { mutableStateOf(selectedTab) }
    if (selectedTab != null) lastSelectedTab = selectedTab
    val pillTab = selectedTab ?: lastSelectedTab

    // The pill continuously retargets toward the live measured rect of the selected slot.
    // snapshotFlow re-emits as the slot's own width animation moves that rect, so the
    // springs chase a moving target with preserved velocity; collectLatest keeps only the
    // newest target alive. The first emission snaps so there is no slide on cold start.
    LaunchedEffect(selectedRoute, tabs) {
        snapshotFlow { selectedRoute?.let { slotRects[it] } }
            .filterNotNull()
            .collectLatest { rect ->
                if (!pillInitialized) {
                    pillX.snapTo(rect.left)
                    pillW.snapTo(rect.width)
                    pillInitialized = true
                } else {
                    coroutineScope {
                        launch { pillX.animateTo(rect.left, PillOffsetSpring) }
                        launch { pillW.animateTo(rect.width, PillWidthSpring) }
                    }
                }
            }
    }

    val dockHairline = if (dark) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.60f)
    val pageBackground = MaterialTheme.colorScheme.background
    val navBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // Only the dock's own 54dp rect gets the glass tint. Without this scrim, content
    // scrolling behind the bar shows through completely unmasked in the margin above/below
    // it and in the system nav-bar inset zone the dock is pushed clear of.
    val scrimHeight = navBarInset + DockOuterVerticalPadding * 2 + DockVisualHeight
    val scrimFadeFraction = (32.dp / scrimHeight).coerceIn(0f, 1f)

    Box(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .height(scrimHeight)
                .padding(horizontal = 16.dp)
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        scrimFadeFraction to pageBackground,
                        1f to pageBackground,
                    ),
                ),
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 16.dp, vertical = DockOuterVerticalPadding),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(DockVisualHeight)
                    .onGloballyPositioned { coords ->
                        dockCoords = coords
                        // Recompute slot rects here too: guarantees population even if the
                        // slots' own positioned callbacks ran before the dock's on first pass.
                        slotCoords.forEach { (route, slot) ->
                            if (slot.isAttached) {
                                slotRects[route] = coords.localBoundingBoxOf(slot, clipBounds = false)
                            }
                        }
                    }
                    .shadow(
                        elevation = 10.dp,
                        shape = DockShape,
                        ambientColor = Color.Black.copy(alpha = 0.10f),
                        spotColor = Color.Black.copy(alpha = 0.16f),
                        clip = false,
                    )
                    .clip(DockShape)
                    .drawBehind {
                        if (dark) {
                            drawRect(Color.White.copy(alpha = 0.07f))
                            drawRect(surface.copy(alpha = 0.92f))
                        } else {
                            drawRect(surface.copy(alpha = 0.94f))
                        }
                    }
                    .border(width = 1.dp, color = dockHairline, shape = DockShape),
            ) {
                val currentPillTab = pillTab
                if (currentPillTab != null) {
                    SlidingPill(
                        tab = currentPillTab,
                        pillX = pillX,
                        pillW = pillW,
                        visible = pillInitialized,
                        onContentWidthMeasured = { route, width ->
                            if (pillContentWidths[route] != width) pillContentWidths[route] = width
                        },
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = SlotRowHorizontalPadding),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    tabs.forEach { tab ->
                        val isSelected = selectedRoute == tab.route
                        TabSlot(
                            tab = tab,
                            selected = isSelected,
                            activeWidth = pillContentWidths[tab.route]
                                ?: (InactiveSlotWidth + 56.dp),
                            onClick = {
                                if (!isSelected) {
                                    haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                                }
                                onSelect(tab.route)
                            },
                            onPositioned = { coords ->
                                slotCoords[tab.route] = coords
                                val dock = dockCoords
                                if (dock != null && dock.isAttached) {
                                    slotRects[tab.route] =
                                        dock.localBoundingBoxOf(coords, clipBounds = false)
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TabSlot(
    tab: BottomTab,
    selected: Boolean,
    activeWidth: Dp,
    onClick: () -> Unit,
    onPositioned: (LayoutCoordinates) -> Unit,
) {
    val width by animateDpAsState(
        targetValue = if (selected) activeWidth else InactiveSlotWidth,
        animationSpec = SlotWidthSpring,
        label = "slot-width",
    )
    // Selected: icon hands off to the pill quickly. Deselected: it waits a beat so it
    // reappears after the pill has visibly departed.
    val iconAlpha by animateFloatAsState(
        targetValue = if (selected) 0f else 1f,
        animationSpec = if (selected) tween(120) else tween(180, delayMillis = 60),
        label = "slot-icon-alpha",
    )

    Box(
        modifier = Modifier
            .width(width)
            .height(SlotHeight)
            .onGloballyPositioned(onPositioned)
            .clip(PillShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true),
                role = Role.Tab,
                onClick = onClick,
            )
            .semantics { this.selected = selected },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = tab.unselectedIcon,
            contentDescription = tab.label,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(IconSize)
                .graphicsLayer { alpha = iconAlpha },
        )
    }
}

@Composable
private fun SlidingPill(
    tab: BottomTab,
    pillX: Animatable<Float, AnimationVector1D>,
    pillW: Animatable<Float, AnimationVector1D>,
    visible: Boolean,
    onContentWidthMeasured: (route: String, width: Dp) -> Unit,
) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val brand = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surface
    val density = LocalDensity.current
    val pillHairline = if (dark) Color.White.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.75f)

    Box(
        modifier = Modifier
            .layout { measurable, _ ->
                val w = pillW.value.roundToInt().coerceAtLeast(1)
                val h = SlotHeight.roundToPx()
                val placeable = measurable.measure(Constraints.fixed(w, h))
                layout(w, h) { placeable.place(0, 0) }
            }
            .graphicsLayer {
                translationX = pillX.value
                translationY = (DockVisualHeight.toPx() - SlotHeight.toPx()) / 2f
                // Velocity-driven squash/stretch; decays naturally as the spring settles.
                val stretch = (abs(pillX.velocity) / 9000f).coerceIn(0f, 0.14f)
                scaleX = 1f + stretch
                scaleY = 1f - stretch * 0.55f
                alpha = if (visible) 1f else 0f
            }
            .shadow(
                elevation = 12.dp,
                shape = PillShape,
                ambientColor = brand.copy(alpha = 0.16f),
                spotColor = brand.copy(alpha = 0.30f),
                clip = false,
            )
            .clip(PillShape)
            .drawBehind {
                if (dark) {
                    drawRect(Color.White.copy(alpha = 0.10f))
                    drawRect(brand.copy(alpha = 0.16f))
                } else {
                    drawRect(surface.copy(alpha = 0.85f))
                    drawRect(brand.copy(alpha = 0.12f))
                }
            }
            .border(width = 1.dp, color = pillHairline, shape = PillShape)
            // Decorative duplication: the slots carry label + selection semantics.
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = tab,
            transitionSpec = {
                (fadeIn(tween(140, delayMillis = 60)) togetherWith fadeOut(tween(90)))
                    .using(SizeTransform(clip = false))
            },
            label = "pill-content",
        ) { current ->
            Row(
                modifier = Modifier
                    // Unbounded so the row reports its intrinsic width even while the pill
                    // is still narrower mid-flight — that intrinsic width is fed back as
                    // the selected slot's target width.
                    .wrapContentWidth(unbounded = true)
                    .onSizeChanged { size ->
                        val target = with(density) { size.width.toDp() } + PillHPad * 2
                        onContentWidthMeasured(current.route, target)
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = current.selectedIcon,
                    contentDescription = null,
                    tint = brand,
                    modifier = Modifier.size(IconSize),
                )
                Spacer(modifier = Modifier.width(IconLabelGap))
                Text(
                    text = current.label,
                    color = brand,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Visible,
                )
            }
        }
    }
}

data class BottomTab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

fun ManagerTab.toBottomTab() = BottomTab(route, label, selectedIcon, unselectedIcon)

fun StaffTab.toBottomTab() = BottomTab(route, label, selectedIcon, unselectedIcon)
