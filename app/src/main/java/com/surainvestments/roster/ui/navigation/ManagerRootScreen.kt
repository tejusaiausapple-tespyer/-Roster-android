package com.surainvestments.roster.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.surainvestments.roster.data.local.AppearanceMode
import com.surainvestments.roster.ui.auth.AccountTabContent
import com.surainvestments.roster.ui.auth.AuthViewModel
import com.surainvestments.roster.ui.manager.dashboard.ManagerDashboardScreen
import com.surainvestments.roster.ui.screens.PlaceholderScreen

@Composable
fun ManagerRootScreen(
    authViewModel: AuthViewModel,
    appearanceMode: AppearanceMode,
    onAppearanceModeChange: (AppearanceMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    val tabs = remember { ManagerTab.entries.map { it.toBottomTab() } }
    val tabHistory = rememberTabHistory(ManagerTab.Dashboard.route)
    val navBarClearance = DockVisualHeight + DockOuterVerticalPadding +
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 8.dp

    fun switchTab(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        CompositionLocalProvider(
            LocalNavBarPadding provides PaddingValues(bottom = navBarClearance),
        ) {
            NavHost(
                navController = navController,
                startDestination = ManagerTab.Dashboard.route,
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars),
            ) {
                ManagerTab.entries.forEach { tab ->
                    composable(tab.route) {
                        when (tab) {
                            ManagerTab.Dashboard -> ManagerDashboardScreen()
                            ManagerTab.Account -> AccountTabContent(
                                authViewModel = authViewModel,
                                appearanceMode = appearanceMode,
                                onAppearanceModeChange = onAppearanceModeChange,
                            )
                            else -> PlaceholderScreen(title = tab.label, icon = tab.selectedIcon)
                        }
                    }
                }
            }
        }

        val backStackEntry by navController.currentBackStackEntryAsState()
        RosterBottomBar(
            tabs = tabs,
            selectedRoute = backStackEntry?.destination?.route,
            onSelect = { route ->
                tabHistory.remove(route)
                tabHistory.add(route)
                switchTab(route)
            },
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        BackHandler(enabled = tabHistory.size > 1) {
            tabHistory.removeAt(tabHistory.lastIndex)
            switchTab(tabHistory.last())
        }
    }
}
