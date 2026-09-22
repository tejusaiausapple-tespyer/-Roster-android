package com.surainvestments.roster.ui.navigation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.surainvestments.roster.data.local.AppearanceMode
import com.surainvestments.roster.data.local.NotificationPreferences
import com.surainvestments.roster.ui.auth.AccountTabContent
import com.surainvestments.roster.ui.auth.AuthViewModel
import com.surainvestments.roster.ui.staff.availability.StaffAvailabilityScreen
import com.surainvestments.roster.ui.staff.home.DailyJobsScreen
import com.surainvestments.roster.ui.staff.home.StaffHomeScreen
import com.surainvestments.roster.ui.staff.payslips.PayslipsScreen
import com.surainvestments.roster.ui.staff.roster.StaffRosterScreen
import com.surainvestments.roster.ui.staff.tasks.StaffTasksScreen

@Composable
fun StaffRootScreen(
    authViewModel: AuthViewModel,
    appearanceMode: AppearanceMode,
    onAppearanceModeChange: (AppearanceMode) -> Unit,
    modifier: Modifier = Modifier,
    pendingDeepLink: String? = null,
    onDeepLinkConsumed: () -> Unit = {},
) {
    val navController = rememberNavController()
    val tabs = remember { StaffTab.entries.map { it.toBottomTab() } }
    val tabHistory = rememberTabHistory(StaffTab.Home.route)
    fun switchTab(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    NotificationPermissionEffect()

    var pendingSubmitShiftId by remember { mutableStateOf<String?>(null) }

    // A tapped notification routes here via its deep link — jump to the destination tab, then clear it.
    // A "submit:{shiftId}" link additionally carries the shiftId through to Roster so it can open
    // Submit Hours directly, matching the plan's "timesheet-rejected → Submit Hours for that shift" routing.
    LaunchedEffect(pendingDeepLink) {
        pendingDeepLink?.let { link ->
            StaffTab.fromDeepLink(link)?.let { target ->
                tabHistory.remove(target.route)
                tabHistory.add(target.route)
                switchTab(target.route)
            }
            if (link.startsWith("submit:")) {
                pendingSubmitShiftId = link.removePrefix("submit:")
            }
            onDeepLinkConsumed()
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        val useNavigationRail = maxWidth >= 600.dp
        val navBarClearance = if (useNavigationRail) {
            0.dp
        } else {
            BottomNavHeight + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        }
        CompositionLocalProvider(
            LocalNavBarPadding provides PaddingValues(bottom = navBarClearance),
        ) {
            NavHost(
                navController = navController,
                startDestination = StaffTab.Home.route,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = if (useNavigationRail) 80.dp else 0.dp)
                    .windowInsetsPadding(WindowInsets.statusBars),
            ) {
                StaffTab.entries.forEach { tab ->
                    composable(tab.route) {
                        when (tab) {
                            StaffTab.Home -> StaffHomeScreen(
                                onOpenTasks = { navController.navigate(TASKS_ROUTE) },
                                onOpenDailyJobs = { navController.navigate(DAILY_JOBS_ROUTE) },
                                onOpenRoster = { switchTab(StaffTab.Roster.route) },
                                modifier = Modifier.fillMaxSize(),
                            )
                            StaffTab.Roster -> StaffRosterScreen(
                                modifier = Modifier.fillMaxSize(),
                                pendingSubmitShiftId = pendingSubmitShiftId,
                                onPendingSubmitConsumed = { pendingSubmitShiftId = null },
                            )
                            StaffTab.Payslips -> PayslipsScreen(modifier = Modifier.fillMaxSize())
                            StaffTab.Availability -> StaffAvailabilityScreen(modifier = Modifier.fillMaxSize())
                            StaffTab.Account -> AccountTabContent(
                                authViewModel = authViewModel,
                                appearanceMode = appearanceMode,
                                onAppearanceModeChange = onAppearanceModeChange,
                            )
                        }
                    }
                }
                val onNavigateToHome: () -> Unit = {
                    if (!navController.popBackStack(StaffTab.Home.route, inclusive = false)) {
                        switchTab(StaffTab.Home.route)
                    }
                }
                composable(TASKS_ROUTE) {
                    StaffTasksScreen(
                        onBack = onNavigateToHome,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                composable(DAILY_JOBS_ROUTE) {
                    DailyJobsScreen(
                        onBack = onNavigateToHome,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        val backStackEntry by navController.currentBackStackEntryAsState()
        val selectedPrimaryRoute = backStackEntry?.destination?.route.let { route ->
            if (route == TASKS_ROUTE || route == DAILY_JOBS_ROUTE) StaffTab.Home.route else route
        }
        val onSelectTab: (String) -> Unit = { route ->
            tabHistory.remove(route)
            tabHistory.add(route)
            switchTab(route)
        }
        if (useNavigationRail) {
            RosterNavigationRail(
                tabs = tabs,
                selectedRoute = selectedPrimaryRoute,
                onSelect = onSelectTab,
                modifier = Modifier.align(Alignment.CenterStart),
            )
        } else {
            RosterBottomBar(
                tabs = tabs,
                selectedRoute = selectedPrimaryRoute,
                onSelect = onSelectTab,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }

        BackHandler(enabled = tabHistory.size > 1) {
            tabHistory.removeAt(tabHistory.lastIndex)
            switchTab(tabHistory.last())
        }
    }
}

private const val TASKS_ROUTE = "staff/home/tasks"
private const val DAILY_JOBS_ROUTE = "staff/home/daily-jobs"

/**
 * Requests POST_NOTIFICATIONS (API 33+) so shift reminders can be shown. Shows a one-time
 * explainer before the first system prompt; on later logins (still ungranted) it requests
 * silently and lets the OS de-dupe — mirroring iOS's "ask every login, explain once" flow.
 */
@Composable
private fun NotificationPermissionEffect() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    val preferences = remember { NotificationPreferences(context.applicationContext) }
    var showRationale by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { /* state reflected in Account → Notifications */ }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (granted) return@LaunchedEffect
        if (preferences.hasRequestedPermission()) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            showRationale = true
        }
    }

    if (showRationale) {
        AlertDialog(
            onDismissRequest = {
                showRationale = false
                preferences.markPermissionRequested()
            },
            title = { Text("Enable shift & hours reminders?") },
            text = {
                Text("Rosterra can remind you before a shift starts and when it's time to submit your hours. You can change this anytime in your phone's settings.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showRationale = false
                    preferences.markPermissionRequested()
                    launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }) { Text("Enable") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showRationale = false
                    preferences.markPermissionRequested()
                }) { Text("Not now") }
            },
            shape = MaterialTheme.shapes.large,
        )
    }
}
