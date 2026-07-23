package com.surainvestments.roster.ui.auth

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.surainvestments.roster.R
import com.surainvestments.roster.ui.components.PrimaryButton
import com.surainvestments.roster.ui.components.QuietOutlinedButton
import com.surainvestments.roster.ui.theme.BrandIndigoStrong
import com.surainvestments.roster.ui.theme.ContentMaxWidth
import kotlinx.coroutines.launch

/** Android analogue of iOS's `DeviceAuthGateView`. */
@Composable
fun DeviceAuthGateScreen(viewModel: AuthViewModel = hiltViewModel(), modifier: Modifier = Modifier) {
    val activity = LocalActivity.current as FragmentActivity
    val scope = rememberCoroutineScope()
    var isAuthenticating by remember { mutableStateOf(false) }

    fun unlock() {
        if (isAuthenticating) return
        isAuthenticating = true
        scope.launch {
            val success = activity.authenticateDeviceOwner(
                title = "Unlock Rosterra",
                subtitle = "Use your device's biometric or screen lock",
            )
            if (success) viewModel.markDeviceAuthVerified()
            isAuthenticating = false
        }
    }

    LaunchedEffect(Unit) { unlock() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            BrandIndigoStrong.copy(alpha = 0.12f),
                            MaterialTheme.colorScheme.background.copy(alpha = 0f),
                        ),
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier.widthIn(max = ContentMaxWidth),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Box {
                    Image(
                        painter = painterResource(R.drawable.app_logo),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(88.dp)
                            .shadow(elevation = 14.dp, shape = RoundedCornerShape(22.dp), clip = false)
                            .clip(RoundedCornerShape(22.dp)),
                    )
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(28.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(4.dp),
                    )
                }
                Text(text = "Locked", style = MaterialTheme.typography.headlineSmall)
                Text(
                    text = "Unlock with biometrics to continue to Rosterra.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                PrimaryButton(
                    text = "Unlock",
                    onClick = ::unlock,
                    enabled = !isAuthenticating,
                    loading = isAuthenticating,
                    leadingIcon = if (isAuthenticating) null else Icons.Outlined.LockOpen,
                )
                QuietOutlinedButton(
                    text = "Sign out",
                    onClick = viewModel::logout,
                    leadingIcon = Icons.AutoMirrored.Outlined.Logout,
                )
            }
        }
    }
}
