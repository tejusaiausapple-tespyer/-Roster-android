package com.surainvestments.roster.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.absoluteValue
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

private val hhmmFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val hourLabels = (1..12).map { it.toString() }
private val minuteLabels = (0..59).map { it.toString().padStart(2, '0') }
private val periodLabels = listOf("AM", "PM")

private val WheelItemHeight = 40.dp
private const val WheelVisibleCount = 5 // odd, so there's a single centered row

/**
 * Time picker dialog over an "HH:mm" string — shared by Submit Hours and Availability's day
 * editor. A scrolling wheel (hour / minute / AM-PM), matching the wheel-style picker used
 * elsewhere in this product's other clients, rather than Material's numeric-keypad `TimeInput`.
 */
@Composable
fun HhmmPickerDialog(initial: String, title: String = "Select time", onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val seedTime = runCatching { LocalTime.parse(initial, hhmmFormatter) }.getOrDefault(LocalTime.NOON)
    val initialHour12 = if (seedTime.hour % 12 == 0) 12 else seedTime.hour % 12
    val initialPeriod = if (seedTime.hour < 12) 0 else 1

    var hourIndex by remember { mutableStateOf(initialHour12 - 1) }
    var minuteIndex by remember { mutableStateOf(seedTime.minute) }
    var periodIndex by remember { mutableStateOf(initialPeriod) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        confirmButton = {
            TextButton(onClick = {
                val hour12 = hourIndex + 1
                val isPm = periodIndex == 1
                val hour24 = when {
                    hour12 == 12 && !isPm -> 0
                    hour12 == 12 && isPm -> 12
                    isPm -> hour12 + 12
                    else -> hour12
                }
                onConfirm(LocalTime.of(hour24, minuteIndex).format(hhmmFormatter))
            }) { Text("OK", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = {
            Box(
                modifier = Modifier.padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                // Selection band behind the centered row, shared across all three wheels.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(WheelItemHeight)
                        .align(Alignment.Center)
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                ) {
                    HorizontalDivider(
                        modifier = Modifier.align(Alignment.TopCenter),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                    HorizontalDivider(
                        modifier = Modifier.align(Alignment.BottomCenter),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Wheel(items = hourLabels, selectedIndex = hourIndex, onSelectedIndexChange = { hourIndex = it }, modifier = Modifier.weight(1f))
                    
                    Text(
                        text = ":",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )

                    Wheel(items = minuteLabels, selectedIndex = minuteIndex, onSelectedIndexChange = { minuteIndex = it }, modifier = Modifier.weight(1f))
                    
                    Spacer(modifier = Modifier.width(8.dp))

                    Wheel(items = periodLabels, selectedIndex = periodIndex, onSelectedIndexChange = { periodIndex = it }, modifier = Modifier.weight(1f))
                }
                WheelEdgeFade(alignTop = true, modifier = Modifier.align(Alignment.TopCenter))
                WheelEdgeFade(alignTop = false, modifier = Modifier.align(Alignment.BottomCenter))
            }
        },
    )
}

/** A single scrolling, snap-to-center wheel of [items]; reports the centered one once scrolling settles. */
@Composable
private fun Wheel(
    items: List<String>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            // snapshotFlow emits its current value immediately on collection — before this
            // list has even laid out — so without dropping that first (always-false) emission,
            // this fires once on initial composition against an empty layoutInfo and stomps the
            // seeded selection back to index 0.
            .drop(1)
            .filter { inProgress -> !inProgress }
            .collect {
                val centered = centeredItemIndex(listState)
                if (centered in items.indices && centered != selectedIndex) {
                    onSelectedIndexChange(centered)
                }
            }
    }

    LazyColumn(
        state = listState,
        flingBehavior = flingBehavior,
        modifier = modifier.height(WheelItemHeight * WheelVisibleCount),
        contentPadding = PaddingValues(vertical = WheelItemHeight * (WheelVisibleCount / 2)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        itemsIndexed(items) { index, label ->
            val isSelected by remember(listState) {
                derivedStateOf { centeredItemIndex(listState) == index }
            }

            Box(
                modifier = Modifier
                    .height(WheelItemHeight)
                    .fillMaxWidth()
                    .clickable { coroutineScope.launch { listState.animateScrollToItem(index) } }
                    .graphicsLayer {
                        val info = listState.layoutInfo
                        val itemInfo = info.visibleItemsInfo.firstOrNull { it.index == index }
                        if (itemInfo != null) {
                            val viewportCenter = (info.viewportStartOffset + info.viewportEndOffset) / 2f
                            val itemCenter = itemInfo.offset + itemInfo.size / 2f
                            val distance = ((itemCenter - viewportCenter).absoluteValue / WheelItemHeight.toPx()).coerceIn(0f, 2f)
                            alpha = (1f - distance * 0.45f).coerceIn(0.2f, 1f)
                            val scale = (1f - distance * 0.15f).coerceIn(0.8f, 1f)
                            scaleX = scale
                            scaleY = scale
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = if (isSelected) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** The data-item index whose visual center is nearest the viewport's center right now. */
private fun centeredItemIndex(listState: LazyListState): Int {
    val info = listState.layoutInfo
    val viewportCenter = (info.viewportStartOffset + info.viewportEndOffset) / 2
    return info.visibleItemsInfo.minByOrNull { (it.offset + it.size / 2 - viewportCenter).absoluteValue }?.index ?: 0
}

/** Fades wheel items to transparent as they approach the top/bottom edge, like a physical wheel. */
@Composable
private fun WheelEdgeFade(alignTop: Boolean, modifier: Modifier = Modifier, height: Dp = WheelItemHeight * 2) {
    val surface = MaterialTheme.colorScheme.surface
    val colors = if (alignTop) {
        listOf(surface, surface.copy(alpha = 0.6f), surface.copy(alpha = 0f))
    } else {
        listOf(surface.copy(alpha = 0f), surface.copy(alpha = 0.6f), surface)
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(Brush.verticalGradient(colors)),
    )
}
