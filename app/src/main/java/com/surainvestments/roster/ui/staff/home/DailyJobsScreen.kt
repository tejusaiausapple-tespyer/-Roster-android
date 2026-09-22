package com.surainvestments.roster.ui.staff.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChecklistRtl
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.surainvestments.roster.domain.model.DailyJobAssignment
import com.surainvestments.roster.domain.model.RosterFormat
import com.surainvestments.roster.ui.components.EmptyState
import com.surainvestments.roster.ui.components.RosterCard
import com.surainvestments.roster.ui.components.ScreenLoadingSkeleton
import com.surainvestments.roster.ui.components.ScreenPillTopBar
import com.surainvestments.roster.ui.components.ScreenPillTopBarHeight
import com.surainvestments.roster.ui.components.TopEdgeFade
import com.surainvestments.roster.ui.navigation.LocalNavBarPadding
import com.surainvestments.roster.ui.theme.AccentEmeraldLight
import com.surainvestments.roster.ui.theme.BrandIndigoStrong
import com.surainvestments.roster.ui.theme.ScreenPadding
import com.surainvestments.roster.ui.theme.WarningAmberLight

/**
 * Full-screen view for today's Daily Job assignments.
 * Displays numbered jobs with "Complete"/"Undo" buttons, recording completion time below job titles when completed.
 */
@Composable
fun DailyJobsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DailyJobsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val navBarPadding = LocalNavBarPadding.current

    BackHandler(onBack = onBack)

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when {
            state.isLoading -> ScreenLoadingSkeleton(modifier = Modifier.fillMaxSize())
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = ScreenPadding,
                    end = ScreenPadding,
                    top = ScreenPillTopBarHeight + 4.dp,
                    bottom = navBarPadding.calculateBottomPadding() + 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    DailyJobsHeader(
                        doneCount = state.jobs.count { it.completed },
                        totalCount = state.jobs.size,
                    )
                }
                if (state.jobs.isEmpty()) {
                    item {
                        EmptyState(
                            icon = Icons.Outlined.ChecklistRtl,
                            title = "No Jobs Today",
                            message = "Daily jobs assigned to your shift will show up here.",
                        )
                    }
                } else {
                    itemsIndexed(state.jobs, key = { _, job -> job.id }) { index, job ->
                        DailyJobCard(
                            number = index + 1,
                            job = job,
                            onToggle = { viewModel.toggle(job) },
                        )
                    }
                }
            }
        }

        TopEdgeFade(modifier = Modifier.align(Alignment.TopCenter))
        ScreenPillTopBar(
            title = "Daily Jobs",
            icon = Icons.Outlined.ChecklistRtl,
            onBack = onBack,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

@Composable
private fun DailyJobsHeader(doneCount: Int, totalCount: Int) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        if (totalCount > 0) {
            val pendingCount = totalCount - doneCount
            val tint = if (pendingCount > 0) WarningAmberLight else AccentEmeraldLight
            Text(
                text = "$doneCount/$totalCount Done",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = tint,
                modifier = Modifier
                    .clip(RoundedCornerShape(percent = 50))
                    .background(tint.copy(alpha = 0.16f))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun DailyJobCard(
    number: Int,
    job: DailyJobAssignment,
    onToggle: () -> Unit,
) {
    val isCompleted = job.completed
    val cardBackground = if (isCompleted) {
        AccentEmeraldLight.copy(alpha = 0.08f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    RosterCard(
        containerColor = cardBackground,
        contentPadding = 14.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Number badge
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(
                        if (isCompleted) AccentEmeraldLight.copy(alpha = 0.2f)
                        else MaterialTheme.colorScheme.surfaceVariant,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = number.toString(),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (isCompleted) AccentEmeraldLight else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Radio outline icon for uncompleted items
            if (!isCompleted) {
                Icon(
                    imageVector = Icons.Outlined.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(20.dp),
                )
            }

            // Title and time record below it when completed
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = job.title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = if (isCompleted) FontWeight.Normal else FontWeight.SemiBold,
                    ),
                    textDecoration = if (isCompleted) TextDecoration.LineThrough else null,
                    color = if (isCompleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
                if (isCompleted) {
                    val timeText = job.completedAt?.let { "Done ${RosterFormat.time(it)}" } ?: "Done"
                    Text(
                        text = timeText,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = AccentEmeraldLight,
                    )
                }
            }

            // Action button: Complete or Undo
            if (isCompleted) {
                FilledTonalButton(
                    onClick = onToggle,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(percent = 50),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                    modifier = Modifier.height(34.dp),
                ) {
                    Text(
                        text = "Undo",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    )
                }
            } else {
                Button(
                    onClick = onToggle,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(percent = 50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BrandIndigoStrong,
                        contentColor = Color.White,
                    ),
                    modifier = Modifier.height(34.dp),
                ) {
                    Text(
                        text = "Complete",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    )
                }
            }
        }
    }
}
