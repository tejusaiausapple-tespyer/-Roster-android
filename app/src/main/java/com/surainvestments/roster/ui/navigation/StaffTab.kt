package com.surainvestments.roster.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.EditCalendar
import androidx.compose.material.icons.outlined.Home
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Staff bottom-nav tabs — same order and SF Symbol meanings as iOS `MainTabView`:
 * house, calendar, list.bullet.clipboard, calendar.badge.clock, person.crop.circle.
 */
enum class StaffTab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    Home("staff/home", "Home", Icons.Filled.Home, Icons.Outlined.Home),
    Roster("staff/roster", "Roster", Icons.Filled.CalendarMonth, Icons.Outlined.CalendarMonth),
    Tasks("staff/tasks", "Tasks", Icons.AutoMirrored.Filled.Assignment, Icons.AutoMirrored.Outlined.Assignment),
    Availability("staff/availability", "Availability", Icons.Filled.EditCalendar, Icons.Outlined.EditCalendar),
    Account("staff/account", "Account", Icons.Filled.AccountCircle, Icons.Outlined.AccountCircle),
}
