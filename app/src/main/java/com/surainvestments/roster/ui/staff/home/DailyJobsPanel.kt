package com.surainvestments.roster.ui.staff.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * @deprecated Replaced by full-screen [DailyJobsScreen].
 */
@Deprecated("Daily Jobs is now a full page screen. Use DailyJobsScreen instead.")
@Composable
fun DailyJobsPanel(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DailyJobsScreen(onBack = onDismiss, modifier = modifier)
}
