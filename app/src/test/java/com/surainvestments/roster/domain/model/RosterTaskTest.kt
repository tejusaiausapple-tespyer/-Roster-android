package com.surainvestments.roster.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ported from iOS's `RosterTaskTests.swift` — same named cases, same expected values, minus the
 * manager-only notification-recipient/assignee-changed tests (out of scope for the Staff app) and
 * the compression test (covered separately, needs `Bitmap`).
 */
class RosterTaskTest {

    private fun makeTask(
        id: String = "t1",
        title: String = "Test",
        frequency: String = "daily",
        date: String? = null,
        dayOfWeek: List<Int>? = null,
        active: Boolean = true,
        assignedTo: List<String>? = null,
        priority: String? = null,
        requiresPhoto: Boolean? = null,
        endDate: String? = null,
    ) = RosterTask(
        id = id,
        title = title,
        description = null,
        managerPhotoUrl = null,
        frequency = frequency,
        date = date,
        dayOfWeek = dayOfWeek,
        active = active,
        createdAt = null,
        createdBy = null,
        assignedTo = assignedTo,
        dueTime = null,
        priority = priority,
        requiresPhoto = requiresPhoto,
        endDate = endDate,
    )

    @Test
    fun `once task active only on its date`() {
        val task = makeTask(frequency = "once", date = "2026-07-06")
        assertTrue(task.isActive(onDayKey = "2026-07-06", weekday = 1))
        assertFalse(task.isActive(onDayKey = "2026-07-07", weekday = 2))
    }

    @Test
    fun `weekly task active on selected weekdays`() {
        val task = makeTask(frequency = "weekly", dayOfWeek = listOf(1, 3))
        assertTrue(task.isActive(onDayKey = "2026-07-06", weekday = 1)) // Mon
        assertFalse(task.isActive(onDayKey = "2026-07-07", weekday = 2)) // Tue
        assertTrue(task.isActive(onDayKey = "2026-07-08", weekday = 3)) // Wed
    }

    @Test
    fun `daily task active every day`() {
        val task = makeTask(frequency = "daily")
        assertTrue(task.isActive(onDayKey = "2026-07-06", weekday = 1))
        assertTrue(task.isActive(onDayKey = "2026-07-12", weekday = 7))
    }

    @Test
    fun `inactive task never active`() {
        val task = makeTask(frequency = "daily", active = false)
        assertFalse(task.isActive(onDayKey = "2026-07-06", weekday = 1))
    }

    @Test
    fun `end date stops recurring task`() {
        val task = makeTask(frequency = "daily", endDate = "2026-07-10")
        assertTrue("inclusive of the end date itself", task.isActive(onDayKey = "2026-07-10", weekday = 5))
        assertFalse(task.isActive(onDayKey = "2026-07-11", weekday = 6))
    }

    @Test
    fun `nil or empty assignment means everyone`() {
        assertTrue(makeTask().isAssigned("anyone"))
        assertTrue(makeTask(assignedTo = emptyList()).isAssigned("anyone"))
    }

    @Test
    fun `explicit assignment filters`() {
        val task = makeTask(assignedTo = listOf("alice", "bob"))
        assertTrue(task.isAssigned("alice"))
        assertFalse(task.isAssigned("carol"))
        assertFalse(task.isAssigned(null))
    }

    @Test
    fun `legacy doc defaults`() {
        val task = makeTask(priority = null, requiresPhoto = null)
        assertTrue(task.photoRequired)
        assertEquals(TaskPriority.Normal, task.priorityLevel)
    }

    @Test
    fun `priority sort weight`() {
        assertTrue(TaskPriority.High.weight < TaskPriority.Normal.weight)
        assertTrue(TaskPriority.Normal.weight < TaskPriority.Low.weight)
    }
}
