package com.surainvestments.roster.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.surainvestments.roster.ui.components.ScreenPillTopBar
import com.surainvestments.roster.ui.components.ScreenPillTopBarHeight
import com.surainvestments.roster.ui.components.SettingsSection
import com.surainvestments.roster.ui.components.TopEdgeFade
import com.surainvestments.roster.ui.navigation.LocalNavBarPadding
import com.surainvestments.roster.ui.theme.ContentMaxWidth
import com.surainvestments.roster.ui.theme.ScreenPadding

data class ReleaseNote(val version: String, val dateLabel: String, val highlights: List<String>)

/**
 * Newest first. Append a new entry here each time a build ships — never rewrite a past one, the
 * same append-only convention as iOS's `ReleaseHistory` this mirrors (`IOS-STAFF-AUDIT.md` §16).
 */
object ReleaseHistoryContent {
    val releases: List<ReleaseNote> = listOf(
        ReleaseNote(
            version = "0.1.0",
            dateLabel = "In development — not yet released",
            highlights = listOf(
                "Staff app: Home, Roster, Tasks, Availability, and Account",
                "GPS-verified clock-in/out with geofencing",
                "Payslip viewer with PDF view, share, and print",
                "Shift and timesheet push and local reminder notifications",
            ),
        ),
    )
}

@Composable
fun VersionHistoryScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    BackHandler(onBack = onBack)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
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

                ReleaseHistoryContent.releases.forEach { release ->
                    SettingsSection(title = "Version ${release.version}", footer = release.dateLabel) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            release.highlights.forEach { line ->
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = "•",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        text = line,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        TopEdgeFade(modifier = Modifier.align(Alignment.TopCenter))
        ScreenPillTopBar(
            title = "Version History",
            icon = Icons.Outlined.Info,
            onBack = onBack,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}
