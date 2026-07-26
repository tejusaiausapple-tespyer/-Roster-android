package com.surainvestments.roster

import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import com.surainvestments.roster.data.local.AppearanceMode
import com.surainvestments.roster.data.local.AppearancePreferences
import com.surainvestments.roster.ui.navigation.RosterNavHost
import com.surainvestments.roster.ui.theme.RosterraTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** FragmentActivity (not plain ComponentActivity) because BiometricPrompt requires one. */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject lateinit var appearancePreferences: AppearancePreferences

    /** Deep link from a tapped notification (e.g. "home", "submit:{shiftId}") — consumed once the nav routes to it. */
    private val pendingDeepLink = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        pendingDeepLink.value = intent?.getStringExtra(EXTRA_DEEP_LINK)

        setContent {
            var appearanceMode by remember { mutableStateOf(appearancePreferences.getMode()) }
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (appearanceMode) {
                AppearanceMode.System -> systemDark
                AppearanceMode.Light -> false
                AppearanceMode.Dark -> true
            }

            // Keep status + nav bars in sync with app theme:
            // light theme → light bars + dark icons/buttons
            // dark theme  → dark bars + light icons/buttons
            DisposableEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = if (darkTheme) {
                        SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(
                            AndroidColor.TRANSPARENT,
                            AndroidColor.TRANSPARENT,
                        )
                    },
                    navigationBarStyle = if (darkTheme) {
                        SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(
                            AndroidColor.TRANSPARENT,
                            AndroidColor.TRANSPARENT,
                        )
                    },
                )
                onDispose { }
            }

            RosterraTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    RosterNavHost(
                        appearanceMode = appearanceMode,
                        onAppearanceModeChange = { mode ->
                            appearancePreferences.setMode(mode)
                            appearanceMode = mode
                        },
                        pendingDeepLink = pendingDeepLink.value,
                        onDeepLinkConsumed = { pendingDeepLink.value = null },
                    )
                }
            }
        }
    }

    /** A notification tapped while the app is already running re-delivers here rather than through onCreate. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(EXTRA_DEEP_LINK)?.let { pendingDeepLink.value = it }
    }

    companion object {
        const val EXTRA_DEEP_LINK = "deepLink"
    }
}
