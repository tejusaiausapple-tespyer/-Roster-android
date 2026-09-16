package com.surainvestments.roster.ui.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.surainvestments.roster.domain.model.RosterCalendar
import com.surainvestments.roster.domain.model.RosterFormat
import com.surainvestments.roster.ui.theme.RosterraTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Roster's week-strip selector is the entry point to every date-scoped staff action (viewing a
 * shift, submitting hours) — worth a real instrumented test rather than trusting the unit-tested
 * date math alone, since a lost tap-to-callback wiring bug wouldn't show up there.
 */
class WeekSelectorTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun tappingNextWeekInvokesOnNext() {
        var nextCalled = false
        val monday = RosterCalendar.weekStartKey()

        composeRule.setContent {
            RosterraTheme {
                WeekSelector(
                    mondayKey = monday,
                    selectedKey = RosterCalendar.todayKey(),
                    onPrev = {},
                    onNext = { nextCalled = true },
                    onToday = {},
                    onSelect = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Next week").performClick()
        assertTrue(nextCalled)
    }

    @Test
    fun tappingPreviousWeekInvokesOnPrev() {
        var prevCalled = false
        val monday = RosterCalendar.weekStartKey()

        composeRule.setContent {
            RosterraTheme {
                WeekSelector(
                    mondayKey = monday,
                    selectedKey = RosterCalendar.todayKey(),
                    onPrev = { prevCalled = true },
                    onNext = {},
                    onToday = {},
                    onSelect = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Previous week").performClick()
        assertTrue(prevCalled)
    }

    @Test
    fun tappingADayChipInvokesOnSelectWithThatDatesKey() {
        var selectedKey: String? = null
        val monday = RosterCalendar.weekStartKey()
        val wednesday = RosterCalendar.weekDayKeys(monday)[2]

        composeRule.setContent {
            RosterraTheme {
                WeekSelector(
                    mondayKey = monday,
                    selectedKey = monday,
                    onPrev = {},
                    onNext = {},
                    onToday = {},
                    onSelect = { selectedKey = it },
                )
            }
        }

        composeRule.onNodeWithText(RosterFormat.dayNumber(wednesday)).performClick()
        assertEquals(wednesday, selectedKey)
    }

    @Test
    fun tappingTodayLabelInvokesOnToday() {
        var todayCalled = false
        val monday = RosterCalendar.weekStartKey()

        composeRule.setContent {
            RosterraTheme {
                WeekSelector(
                    mondayKey = monday,
                    selectedKey = monday,
                    onPrev = {},
                    onNext = {},
                    onToday = { todayCalled = true },
                    onSelect = {},
                )
            }
        }

        composeRule.onNodeWithText("Today").performClick()
        assertTrue(todayCalled)
    }
}
