package com.surainvestments.roster.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.surainvestments.roster.domain.model.RosterCalendar
import com.surainvestments.roster.domain.model.RosterFormat
import com.surainvestments.roster.ui.theme.BrandIndigoStrong

/** Week navigator + 7-day strip, shared by Roster (and later Availability). Mirrors iOS `WeekSelector`. */
@Composable
fun WeekSelector(
    mondayKey: String,
    selectedKey: String,
    modifier: Modifier = Modifier,
    markedKeys: Set<String> = emptySet(),
    lockedKeys: Set<String> = emptySet(),
    canGoPrev: Boolean = true,
    canGoNext: Boolean = true,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
    onSelect: (String) -> Unit,
) {
    val todayKey = RosterCalendar.todayKey()
    val days = RosterCalendar.weekDayKeys(mondayKey)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NavButton(icon = Icons.Filled.ChevronLeft, enabled = canGoPrev, onClick = onPrev)
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = RosterFormat.weekRange(mondayKey),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Today",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = BrandIndigoStrong,
                    modifier = Modifier.clickable(onClick = onToday),
                )
            }
            NavButton(icon = Icons.Filled.ChevronRight, enabled = canGoNext, onClick = onNext)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            days.forEach { key ->
                DayChip(
                    dateKey = key,
                    isSelected = key == selectedKey,
                    isToday = key == todayKey,
                    isMarked = key in markedKeys,
                    isLocked = key in lockedKeys,
                    onClick = { onSelect(key) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun NavButton(icon: androidx.compose.ui.graphics.vector.ImageVector, enabled: Boolean, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(40.dp)
            .background(BrandIndigoStrong.copy(alpha = if (enabled) 0.10f else 0.04f), CircleShape),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = if (icon == Icons.Filled.ChevronLeft) "Previous week" else "Next week",
            tint = if (enabled) BrandIndigoStrong else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DayChip(
    dateKey: String,
    isSelected: Boolean,
    isToday: Boolean,
    isMarked: Boolean,
    isLocked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = modifier
            .alpha(if (isLocked) 0.45f else 1f)
            .clip(shape)
            .background(if (isSelected) BrandIndigoStrong else Color.Transparent, shape)
            .border(
                width = if (isToday && !isSelected) 1.5.dp else 0.dp,
                color = if (isToday && !isSelected) BrandIndigoStrong.copy(alpha = 0.6f) else Color.Transparent,
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(
            text = RosterFormat.weekdayInitial(dateKey),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = if (isSelected) Color.White.copy(alpha = 0.9f) else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Text(
            text = RosterFormat.dayNumber(dateKey),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Box(
            modifier = Modifier
                .size(5.dp)
                .background(
                    color = if (isMarked) (if (isSelected) Color.White else BrandIndigoStrong) else Color.Transparent,
                    shape = CircleShape,
                ),
        )
    }
}
