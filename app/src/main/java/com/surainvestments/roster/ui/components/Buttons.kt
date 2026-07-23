package com.surainvestments.roster.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.surainvestments.roster.ui.theme.BrandIndigoStrong

/**
 * Primary CTA — brand fill + Expressive shape-morph on press.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    fullWidth: Boolean = true,
    leadingIcon: ImageVector? = null,
) {
    val height = ButtonDefaults.MediumContainerHeight
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = if (fullWidth) {
            modifier.fillMaxWidth().height(height)
        } else {
            modifier.height(height)
        },
        shapes = ButtonDefaults.shapes(
            shape = MaterialTheme.shapes.medium,
            pressedShape = ButtonDefaults.mediumPressedShape,
        ),
        colors = ButtonDefaults.buttonColors(
            containerColor = BrandIndigoStrong,
            contentColor = Color.White,
            disabledContainerColor = BrandIndigoStrong.copy(alpha = 0.48f),
            disabledContentColor = Color.White.copy(alpha = 0.75f),
        ),
        contentPadding = ButtonDefaults.MediumContentPadding,
    ) {
        when {
            loading -> LoadingIndicator(
                modifier = Modifier.size(ButtonDefaults.MediumIconSize),
                color = Color.White,
            )
            else -> {
                if (leadingIcon != null) {
                    Icon(
                        imageVector = leadingIcon,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(end = ButtonDefaults.MediumIconSpacing)
                            .size(ButtonDefaults.MediumIconSize),
                    )
                }
                Text(text = text, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/**
 * Soft brand-tint secondary with Expressive press morph.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    fullWidth: Boolean = true,
) {
    val height = ButtonDefaults.MediumContainerHeight
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        modifier = if (fullWidth) {
            modifier.fillMaxWidth().height(height)
        } else {
            modifier.height(height)
        },
        shapes = ButtonDefaults.shapes(
            shape = MaterialTheme.shapes.medium,
            pressedShape = ButtonDefaults.mediumPressedShape,
        ),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            contentColor = MaterialTheme.colorScheme.primary,
        ),
        contentPadding = ButtonDefaults.MediumContentPadding,
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun QuietOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    fullWidth: Boolean = true,
    leadingIcon: ImageVector? = null,
) {
    val height = ButtonDefaults.MediumContainerHeight
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = if (fullWidth) {
            modifier.fillMaxWidth().height(height)
        } else {
            modifier.height(height)
        },
        shapes = ButtonDefaults.shapes(
            shape = MaterialTheme.shapes.medium,
            pressedShape = ButtonDefaults.mediumPressedShape,
        ),
        border = ButtonDefaults.outlinedButtonBorder(enabled = enabled),
        contentPadding = ButtonDefaults.MediumContentPadding,
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                modifier = Modifier
                    .padding(end = ButtonDefaults.MediumIconSpacing)
                    .size(ButtonDefaults.MediumIconSize),
            )
        }
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

/** Circular pill button matching the app's floating-glass chrome, with haptic feedback. */
@Composable
private fun CircularPillButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    Box(
        modifier = modifier
            .size(44.dp)
            .shadow(
                elevation = 6.dp,
                shape = CircleShape,
                ambientColor = Color.Black.copy(alpha = 0.08f),
                spotColor = Color.Black.copy(alpha = 0.14f),
                clip = false,
            )
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true),
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                    onClick()
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

/** Circular pill back button matching the app's floating-glass chrome, with haptic feedback. */
@Composable
fun BackPillButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    CircularPillButton(
        icon = Icons.AutoMirrored.Filled.ArrowBack,
        contentDescription = "Back",
        onClick = onClick,
        modifier = modifier,
    )
}

/** Circular pill add button matching the app's floating-glass chrome, with haptic feedback. */
@Composable
fun AddPillButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    CircularPillButton(
        icon = Icons.Filled.Add,
        contentDescription = "Add",
        onClick = onClick,
        modifier = modifier,
    )
}

@Composable
fun LinkButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
