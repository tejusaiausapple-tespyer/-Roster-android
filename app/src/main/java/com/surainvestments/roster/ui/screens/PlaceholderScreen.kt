package com.surainvestments.roster.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Construction
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.surainvestments.roster.ui.components.EmptyState
import com.surainvestments.roster.ui.components.ScreenPillTopBar
import com.surainvestments.roster.ui.components.ScreenPillTopBarHeight
import com.surainvestments.roster.ui.components.TopEdgeFade
import com.surainvestments.roster.ui.navigation.LocalNavBarPadding
import com.surainvestments.roster.ui.theme.ContentMaxWidth

/**
 * Branded stand-in matching iOS empty-state chrome (title pill + centered empty).
 */
@Composable
fun PlaceholderScreen(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(LocalNavBarPadding.current),
    ) {
        EmptyState(
            icon = icon ?: Icons.Outlined.Construction,
            title = title,
            message = "Coming soon — this screen will match the iOS & web apps.",
            modifier = Modifier
                .fillMaxSize()
                .padding(top = ScreenPillTopBarHeight)
                .widthIn(max = ContentMaxWidth),
        )

        TopEdgeFade(modifier = Modifier.align(Alignment.TopCenter))
        ScreenPillTopBar(
            title = title,
            icon = icon,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}
