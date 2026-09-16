package com.surainvestments.roster.ui.components

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.surainvestments.roster.ui.theme.RosterraTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * `PrimaryButton` is the shared CTA behind every critical submit action in the app (sign in,
 * submit hours, report absence, save availability, enable quick-login) — its enabled/loading/click
 * contract needs to actually hold, not just look right in a design review.
 */
class ButtonsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun clickingAnEnabledButtonInvokesOnClick() {
        var clicked = false
        composeRule.setContent {
            RosterraTheme {
                PrimaryButton(text = "Sign in", onClick = { clicked = true }, enabled = true)
            }
        }

        composeRule.onNodeWithText("Sign in").performClick()
        assertTrue(clicked)
    }

    @Test
    fun clickingADisabledButtonNeverInvokesOnClick() {
        var clicked = false
        composeRule.setContent {
            RosterraTheme {
                PrimaryButton(text = "Sign in", onClick = { clicked = true }, enabled = false)
            }
        }

        composeRule.onNodeWithText("Sign in").assertIsNotEnabled()
        composeRule.onNodeWithText("Sign in").performClick()
        assertFalse(clicked)
    }

    @Test
    fun loadingButtonHidesItsLabelText() {
        composeRule.setContent {
            RosterraTheme {
                PrimaryButton(text = "Submit hours", onClick = {}, loading = true)
            }
        }

        // The loading branch shows a spinner instead of the label — the text node shouldn't exist.
        composeRule.onNodeWithText("Submit hours").assertDoesNotExist()
    }
}
