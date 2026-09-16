package com.surainvestments.roster.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.surainvestments.roster.data.service.NetworkMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class ConnectivityViewModel @Inject constructor(monitor: NetworkMonitor) : ViewModel() {
    // Fail open (assume online) until the first real callback arrives — matches NetworkMonitor's
    // own fail-open default, so a slow-to-initialize monitor never falsely flashes "offline".
    val isOnline: StateFlow<Boolean> = monitor.isOnlineFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
}

/**
 * App-wide "you're offline" strip — mounted once at the root of the nav graph ([RosterNavHost])
 * so it applies across every route/tab rather than being duplicated per-screen. Purely
 * informational: every staff screen already reads from Firestore's own offline cache and degrades
 * gracefully on its own (`ANDROID-STAFF-BUILD-PLAN.md` §6) — this doesn't gate or retry anything.
 */
@Composable
fun OfflineIndicator(modifier: Modifier = Modifier, viewModel: ConnectivityViewModel = hiltViewModel()) {
    val isOnline by viewModel.isOnline.collectAsState()

    AnimatedVisibility(
        visible = !isOnline,
        modifier = modifier,
        enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.errorContainer)
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.WifiOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(16.dp),
            )
            Row(modifier = Modifier.padding(start = 8.dp)) {
                Text(
                    text = "You're offline — changes will sync once reconnected",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}
