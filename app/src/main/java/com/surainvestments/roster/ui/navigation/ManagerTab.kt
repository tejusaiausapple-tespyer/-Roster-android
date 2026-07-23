package com.surainvestments.roster.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Manager phone bottom tabs — matches iOS `ManagerMainView` compact layout:
 * Dashboard, Roster, Tasks, Timesheets, Account.
 * Staff / Availability / Reports / etc. live under Account → Management.
 */
enum class ManagerTab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    Dashboard("manager/dashboard", "Dashboard", Icons.Filled.GridView, Icons.Outlined.GridView),
    Roster("manager/roster", "Roster", Icons.Filled.CalendarMonth, Icons.Outlined.CalendarMonth),
    Tasks("manager/tasks", "Tasks", Icons.AutoMirrored.Filled.Assignment, Icons.AutoMirrored.Outlined.Assignment),
    Timesheets("manager/timesheets", "Timesheets", Icons.Filled.ContentPaste, Icons.Outlined.ContentPaste),
    Account("manager/account", "Account", Icons.Filled.Settings, Icons.Outlined.Settings),
}
