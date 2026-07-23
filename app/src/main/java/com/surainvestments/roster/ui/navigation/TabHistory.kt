package com.surainvestments.roster.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList

private val TabHistorySaver = listSaver<SnapshotStateList<String>, String>(
    save = { it.toList() },
    restore = { it.toMutableStateList() },
)

/**
 * Order in which bottom-nav tab routes were visited, so the system back button can
 * step back through previously selected tabs instead of always landing on the start
 * destination. Survives process death/recreation in step with the NavController's own
 * saved back stack.
 */
@Composable
fun rememberTabHistory(startRoute: String): SnapshotStateList<String> =
    rememberSaveable(saver = TabHistorySaver) { mutableStateListOf(startRoute) }
