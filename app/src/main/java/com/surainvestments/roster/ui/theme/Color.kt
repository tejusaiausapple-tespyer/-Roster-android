package com.surainvestments.roster.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Brand palette ported from iOS `Theme.swift` — calm indigo used sparingly,
 * flat grouped surfaces, emerald/amber/red for semantics.
 */

// Light scheme
val BrandIndigoLight = Color(0xFF4F46E5)
val BrandIndigoStrong = Color(0xFF4F46E5)
val BrandIndigoDeep = Color(0xFF4338CA)
val AccentEmeraldLight = Color(0xFF059669)
val WarningAmberLight = Color(0xFFB45309)
val ErrorRedLight = Color(0xFFDC2626)
val BackgroundLight = Color(0xFFF2F3F7)
val SurfaceLight = Color(0xFFFFFFFF)
val SurfaceContainerLight = Color(0xFFE8EAF0)
val SeparatorLight = Color(0xFFE5E7EB)
val OutlineLight = Color(0xFFC5CAD3)
val TextPrimaryLight = Color(0xFF0F172A)
val TextSecondaryLight = Color(0xFF5B6472)
val TextTertiaryLight = Color(0xFF94A3B8)
val PrimaryContainerLight = Color(0xFFE0E7FF)
val OnPrimaryContainerLight = Color(0xFF312E81)
val SecondaryContainerLight = Color(0xFFD1FAE5)
val OnSecondaryContainerLight = Color(0xFF064E3B)

// Dark scheme
val BrandIndigoDark = Color(0xFF818CF8)
val AccentEmeraldDark = Color(0xFF34D399)
val WarningAmberDark = Color(0xFFF59E0B)
val ErrorRedDark = Color(0xFFEF4444)
val BackgroundDark = Color(0xFF000000)
val SurfaceDark = Color(0xFF1C1C1E)
val SurfaceContainerDark = Color(0xFF2A2A2C)
val SeparatorDark = Color(0xFF2C2C2E)
val OutlineDark = Color(0xFF3F3F46)
val TextPrimaryDark = Color(0xFFF3F4F6)
val TextSecondaryDark = Color(0xFF9CA7B8)
val TextTertiaryDark = Color(0xFF6B7688)
val PrimaryContainerDark = Color(0xFF312E81)
val OnPrimaryContainerDark = Color(0xFFE0E7FF)
val SecondaryContainerDark = Color(0xFF064E3B)
val OnSecondaryContainerDark = Color(0xFFD1FAE5)

/** Soft status fills (~14% opacity) matching iOS `Theme.StatusStyle`. */
object StatusColors {
    val Scheduled = Color(0xFF3B82F6)
    val Pending = Color(0xFFB45309)
    val Draft = Color(0xFF6B7280)
    val Approved = Color(0xFF059669)
    val Rejected = Color(0xFFDC2626)
    /** `absent_reported` (staff self-report, not yet confirmed) — also the merged manager-side "absence" bucket. */
    val Absent = Color(0xFFC2410C)
    /** `absent` (manager-confirmed terminal no-show) — distinct from [Absent]/`absent_reported` on staff screens. */
    val AbsentConfirmed = Color(0xFFDC2626)
    val Active = AccentEmeraldLight
    val Locked = ErrorRedLight
    val Inactive = TextTertiaryLight
}
