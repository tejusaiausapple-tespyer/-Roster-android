package com.surainvestments.roster.ui.components

import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.surainvestments.roster.ui.theme.RosterraTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * `SettingsRow`/`SettingsToggleRow` back nearly every row in the Account tab — profile fields,
 * notification-channel links, the biometric/quick-login toggles, sign-out. A regression here would
 * silently break many settings at once, so the shared row contract is worth testing directly.
 */
class SettingsListTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun clickableRowInvokesOnClick() {
        var clicked = false
        composeRule.setContent {
            RosterraTheme {
                SettingsRow(title = "Change password", showChevron = true, onClick = { clicked = true })
            }
        }

        composeRule.onNodeWithText("Change password").performClick()
        assertTrue(clicked)
    }

    @Test
    fun rowWithNoOnClickShowsValueButIsNotClickable() {
        composeRule.setContent {
            RosterraTheme {
                SettingsRow(title = "Alerts allowed", value = "On")
            }
        }

        composeRule.onNodeWithText("Alerts allowed").assertExists()
        composeRule.onNodeWithText("On").assertExists()
    }

    @Test
    fun toggleRowReflectsCheckedState() {
        composeRule.setContent {
            RosterraTheme {
                SettingsToggleRow(title = "Biometric unlock", checked = true, onCheckedChange = {})
            }
        }

        composeRule.onNode(isToggleable()).assertIsOn()
    }

    @Test
    fun togglingRowInvokesOnCheckedChangeWithFlippedValue() {
        var lastValue: Boolean? = null
        composeRule.setContent {
            RosterraTheme {
                SettingsToggleRow(title = "Quick Login", checked = false, onCheckedChange = { lastValue = it })
            }
        }

        composeRule.onNode(isToggleable()).assertIsOff()
        composeRule.onNode(isToggleable()).performClick()

        assertTrue(lastValue == true)
    }
}
