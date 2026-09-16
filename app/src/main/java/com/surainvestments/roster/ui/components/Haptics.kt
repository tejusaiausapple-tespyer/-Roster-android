package com.surainvestments.roster.ui.components

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.core.content.getSystemService

/**
 * Android analogue of iOS's `Services/Haptics.swift` — a semantic vocabulary of *named events*
 * layered over the platform's primitive feedback, not a couple of ad hoc button taps.
 * `IOS-STAFF-AUDIT.md` §13 calls this out explicitly as a deliberate "premium feel" signal to
 * replicate, not skip. Each event maps to the closest available
 * [androidx.compose.ui.hapticfeedback.HapticFeedbackType] constant (`Confirm`/`Reject` are the two
 * semantically-named ones this Compose version exposes; `SegmentTick` doubles as a tab-change
 * click since Android has no separate "segment change" haptic concept).
 */
enum class HapticEvent(internal val composeType: HapticFeedbackType) {
    TabChange(HapticFeedbackType.SegmentTick),
    SignIn(HapticFeedbackType.Confirm),
    SignOut(HapticFeedbackType.ContextClick),
    ForcedSignOut(HapticFeedbackType.Reject),
    SaveSuccess(HapticFeedbackType.Confirm),
    SaveError(HapticFeedbackType.Reject),
    SubmitSuccess(HapticFeedbackType.Confirm),
    SubmitError(HapticFeedbackType.Reject),
    AuthSuccess(HapticFeedbackType.Confirm),
    AuthFailure(HapticFeedbackType.Reject),
    NotificationOpened(HapticFeedbackType.ContextClick),
}

object Haptics {

    /** The common case — any Composable with `LocalHapticFeedback.current` in scope. */
    fun perform(haptic: HapticFeedback, event: HapticEvent) {
        haptic.performHapticFeedback(event.composeType)
    }

    /**
     * For the rare non-Compose call site (a `Service`, a plain singleton observer) with no
     * `HapticFeedback`/`View` available at all. **Deliberately not wired into any notification
     * posting path** (`RosterMessagingService`/`LocalAlertObserver`/`ReminderArmer`): every
     * notification channel this app creates already has its own OS-level vibration behavior, and
     * Android ignores a per-notification vibration override on a channel with default-or-higher
     * importance specifically so apps can't second-guess the user's channel settings — calling
     * this there would just double-buzz the device, not add a distinct "urgent" feel.
     */
    fun vibrate(context: Context, oneShotMillis: Long = 40L) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        } ?: return
        vibrator.vibrate(VibrationEffect.createOneShot(oneShotMillis, VibrationEffect.DEFAULT_AMPLITUDE))
    }
}
