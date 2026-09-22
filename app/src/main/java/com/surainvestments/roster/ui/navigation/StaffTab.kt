package com.surainvestments.roster.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.EditCalendar
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Primary Staff destinations, aligned with the current iOS Staff information architecture.
 * Tasks and Daily Jobs are contextual Home destinations rather than peer tabs.
 */
enum class StaffTab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    Home("staff/home", "Home", Icons.Filled.Home, Icons.Outlined.Home),
    Roster("staff/roster", "Roster", Icons.Filled.CalendarMonth, Icons.Outlined.CalendarMonth),
    Payslips("staff/payslips", "Payslips", Icons.Filled.Payments, Icons.Outlined.Payments),
    Availability("staff/availability", "Availability", Icons.Filled.EditCalendar, Icons.Outlined.EditCalendar),
    Account("staff/account", "Account", Icons.Filled.AccountCircle, Icons.Outlined.AccountCircle),
    ;

    companion object {
        /**
         * Maps a notification deep link to its destination tab, mirroring iOS
         * `AppRouter.handleNotificationUserInfo`'s event→tab routing (`ANDROID-STAFF-BUILD-PLAN.md` §5).
         * `submit:{shiftId}` lands on Roster, where Submit Hours lives. Unrecognized links return null.
         */
        fun fromDeepLink(deepLink: String): StaffTab? = when {
            deepLink == "home" -> Home
            deepLink.startsWith("submit:") -> Roster
            deepLink.contains("roster") || deepLink.contains("history") -> Roster
            deepLink.contains("payslip") -> Payslips
            deepLink.contains("task") || deepLink.contains("job") -> Home
            deepLink.contains("availability") -> Availability
            deepLink.contains("account") -> Account
            else -> null
        }
    }
}
