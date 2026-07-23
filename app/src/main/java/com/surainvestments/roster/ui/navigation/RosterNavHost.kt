package com.surainvestments.roster.ui.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.surainvestments.roster.data.local.AppearanceMode
import com.surainvestments.roster.domain.routing.AppRoute
import com.surainvestments.roster.ui.auth.AuthViewModel
import com.surainvestments.roster.ui.auth.ChangePasswordScreen
import com.surainvestments.roster.ui.auth.DeviceAuthGateScreen
import com.surainvestments.roster.ui.auth.LoginScreen
import com.surainvestments.roster.ui.screens.PlaceholderScreen
import com.surainvestments.roster.ui.theme.BrandIndigoStrong

@Composable
fun RosterNavHost(
    appearanceMode: AppearanceMode,
    onAppearanceModeChange: (AppearanceMode) -> Unit,
) {
    val authViewModel: AuthViewModel = hiltViewModel()
    val route by authViewModel.route.collectAsState()

    AnimatedContent(
        targetState = route,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "app-route",
    ) { current ->
        when (current) {
            AppRoute.Restoring, AppRoute.ProfileLoading -> LoadingScreen()
            AppRoute.Login -> LoginScreen(viewModel = authViewModel)
            AppRoute.ForcedPasswordChange -> ChangePasswordScreen(isForced = true)
            AppRoute.ProfileCompletion -> PlaceholderScreen(title = "Complete your profile")
            AppRoute.DeviceAuthGate -> DeviceAuthGateScreen(viewModel = authViewModel)
            AppRoute.StaffMain -> StaffRootScreen(
                authViewModel = authViewModel,
                appearanceMode = appearanceMode,
                onAppearanceModeChange = onAppearanceModeChange,
            )
            AppRoute.ManagerMain -> ManagerRootScreen(
                authViewModel = authViewModel,
                appearanceMode = appearanceMode,
                onAppearanceModeChange = onAppearanceModeChange,
            )
        }
    }
}

@Composable
private fun LoadingScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(36.dp),
                color = BrandIndigoStrong,
                strokeWidth = 3.dp,
            )
            Text(
                text = "Rosterra",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
