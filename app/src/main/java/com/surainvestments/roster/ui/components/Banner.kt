package com.surainvestments.roster.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

enum class BannerKind {
    Info, Warning, Error, Success;

    val tint: Color
        @Composable get() = when (this) {
            Info -> MaterialTheme.colorScheme.primary
            Warning -> MaterialTheme.colorScheme.tertiary
            Error -> MaterialTheme.colorScheme.error
            Success -> MaterialTheme.colorScheme.secondary
        }

    val icon: ImageVector
        get() = when (this) {
            Info -> Icons.Filled.Info
            Warning -> Icons.Filled.Warning
            Error -> Icons.Filled.Error
            Success -> Icons.Filled.CheckCircle
        }
}

/**
 * Inline informational banner (iOS `Banner`).
 */
@Composable
fun Banner(
    kind: BannerKind,
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    actionTitle: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val tint = kind.tint
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(tint.copy(alpha = 0.10f))
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = kind.icon,
            contentDescription = null,
            tint = tint,
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            message?.let {
                Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (actionTitle != null && onAction != null) {
            TextButton(onClick = onAction) {
                Text(text = actionTitle, style = MaterialTheme.typography.labelMedium, color = tint)
            }
        }
    }
}
