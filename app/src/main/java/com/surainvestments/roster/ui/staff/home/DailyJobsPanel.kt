package com.surainvestments.roster.ui.staff.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.ChecklistRtl
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.surainvestments.roster.domain.model.DailyJobAssignment
import com.surainvestments.roster.ui.components.EmptyState
import com.surainvestments.roster.ui.components.ScrollFadeHints
import com.surainvestments.roster.ui.theme.AccentEmeraldLight
import com.surainvestments.roster.ui.theme.BrandIndigoStrong
import com.surainvestments.roster.ui.theme.WarningAmberLight

/**
 * The Home bell panel — today's Daily Job assignments only (Messages is out of scope; see
 * `docs/ANDROID-STAFF-BUILD-PLAN.md` Phase G). Mirrors iOS `NotificationsSheet`'s two-detent
 * presentation (`.presentationDetents([.medium, .large])`): the content is given
 * `fillMaxHeight()` so Compose's own sheet-state anchors offer the same "starts at roughly half
 * height, drag up to fill the screen" behavior, rather than shrinking to fit just the job list —
 * a 2-item list on iOS still sits in a half-screen sheet, not a content-hugging popup.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyJobsPanel(
    onDismiss: () -> Unit,
    viewModel: DailyJobsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            PanelHeader(doneCount = state.jobs.count { it.completed }, totalCount = state.jobs.size)
            when {
                state.isLoading -> Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = BrandIndigoStrong)
                }
                state.jobs.isEmpty() -> Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    EmptyState(
                        icon = Icons.Outlined.ChecklistRtl,
                        title = "No jobs today",
                        message = "Daily jobs assigned to your shift will show up here.",
                    )
                }
                else -> {
                    val listState = rememberLazyListState()
                    // `ModalBottomSheet` measures its content once at (essentially) full height and
                    // just translates/clips the surface for the partially-expanded state — it never
                    // re-measures with a smaller constraint. Left alone, the LazyColumn thinks it has
                    // a huge viewport and reports "nothing to scroll" even while the sheet is visually
                    // clipping most of it off. iOS hits the same shape of problem and solves it by
                    // capping the compact/medium-detent list at a fixed `maxHeight: 320` regardless of
                    // detent, so the list's own scroll state becomes meaningful; mirrored here.
                    val isExpanded = sheetState.currentValue == SheetValue.Expanded
                    val listModifier = if (isExpanded) Modifier.weight(1f) else Modifier.heightIn(max = 320.dp)
                    Box(modifier = listModifier) {
                        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                            itemsIndexed(state.jobs, key = { _, job -> job.id }) { index, job ->
                                DailyJobRow(job = job, onToggle = { viewModel.toggle(job) })
                                if (index < state.jobs.lastIndex) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                }
                            }
                        }
                        ScrollFadeHints(listState = listState, modifier = Modifier.matchParentSize())
                    }
                }
            }
        }
    }
}

@Composable
private fun PanelHeader(doneCount: Int, totalCount: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Today's Jobs",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        )
        if (totalCount > 0) {
            val pendingCount = totalCount - doneCount
            val tint = if (pendingCount > 0) WarningAmberLight else AccentEmeraldLight
            Text(
                text = "$doneCount/$totalCount Done",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = tint,
                modifier = Modifier
                    .clip(RoundedCornerShape(percent = 50))
                    .background(tint.copy(alpha = 0.14f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun DailyJobRow(job: DailyJobAssignment, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        val tint = if (job.completed) AccentEmeraldLight else MaterialTheme.colorScheme.outline
        Row(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(if (job.completed) AccentEmeraldLight.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (job.completed) {
                Icon(Icons.Filled.Check, contentDescription = "Completed", tint = tint, modifier = Modifier.size(16.dp))
            }
        }
        Text(
            text = job.title,
            style = MaterialTheme.typography.bodyMedium,
            textDecoration = if (job.completed) TextDecoration.LineThrough else null,
            color = if (job.completed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}
