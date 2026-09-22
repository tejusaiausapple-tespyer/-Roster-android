package com.surainvestments.roster.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.surainvestments.roster.notifications.NotificationChannels
import com.surainvestments.roster.ui.components.ScreenPillTopBar
import com.surainvestments.roster.ui.components.ScreenPillTopBarHeight
import com.surainvestments.roster.ui.components.SettingsDivider
import com.surainvestments.roster.ui.components.SettingsRow
import com.surainvestments.roster.ui.components.SettingsSection
import com.surainvestments.roster.ui.components.TopEdgeFade
import com.surainvestments.roster.ui.navigation.LocalNavBarPadding
import com.surainvestments.roster.ui.theme.AccentEmeraldLight
import com.surainvestments.roster.ui.theme.ContentMaxWidth
import com.surainvestments.roster.ui.theme.ScreenPadding
import com.surainvestments.roster.ui.theme.TextTertiaryLight

private data class NotificationChannelRow(val channelId: String, val title: String, val icon: ImageVector)

/**
 * Once a channel exists, Android reserves its importance/sound/vibration to the user — the app
 * can no longer flip it programmatically (only at first creation). So "settings" here means a
 * direct deep link into each channel's own system settings page
 * (`Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS`), not an in-app toggle that couldn't actually
 * control anything post-creation.
 */
private val notificationChannelRows = listOf(
    NotificationChannelRow(NotificationChannels.SHIFT_UPCOMING, "Shift reminders", Icons.Outlined.Schedule),
    NotificationChannelRow(NotificationChannels.TIMESHEET_ACTION, "Timesheet & hours", Icons.Outlined.AccessTime),
    NotificationChannelRow(NotificationChannels.TASKS, "Tasks", Icons.Outlined.Checklist),
    NotificationChannelRow(NotificationChannels.GENERAL, "General", Icons.Outlined.Notifications),
)

/**
 * Pushed from Account's single "Notifications" row (reworked 2026-07-28 from an inline list to
 * its own page) — the master on/off status plus one row per category, each a deep link into that
 * category's own system settings.
 */
@Composable
fun NotificationSettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var notificationsEnabled by remember { mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    BackHandler(onBack = onBack)

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(LocalNavBarPadding.current)
                .padding(ScreenPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Spacer(modifier = Modifier.height(ScreenPillTopBarHeight))

                SettingsSection(title = "Alerts") {
                    SettingsRow(
                        title = "Alerts allowed",
                        icon = Icons.Outlined.Notifications,
                        value = if (notificationsEnabled) "On" else "Off",
                        valueColor = if (notificationsEnabled) AccentEmeraldLight else TextTertiaryLight,
                        showChevron = true,
                        onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", context.packageName, null)
                                },
                            )
                        },
                    )
                }

                SettingsSection(
                    title = "Categories",
                    footer = "Shift start and hours reminders are scheduled on this device from your last roster sync. Tap a category to change its sound, vibration, or importance.",
                ) {
                    notificationChannelRows.forEachIndexed { index, row ->
                        SettingsRow(
                            title = row.title,
                            icon = row.icon,
                            showChevron = true,
                            onClick = {
                                context.startActivity(
                                    Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                        putExtra(Settings.EXTRA_CHANNEL_ID, row.channelId)
                                    },
                                )
                            },
                        )
                        if (index < notificationChannelRows.lastIndex) SettingsDivider()
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        TopEdgeFade(modifier = Modifier.align(Alignment.TopCenter))
        ScreenPillTopBar(
            title = "Notifications",
            icon = Icons.Outlined.Notifications,
            onBack = onBack,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}
