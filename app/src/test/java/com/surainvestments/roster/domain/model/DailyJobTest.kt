package com.surainvestments.roster.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/** `excludingOrphaned` — self-heal for a deleted-then-recreated shift leaving a stale assignment behind (no cascade-delete ties the two lifecycles together). */
class DailyJobTest {

    private fun assignment(id: String, shiftId: String) = DailyJobAssignment(
        id = id, shiftId = shiftId, staffId = "staff-1", templateId = "t1", title = "Wipe counters",
        date = "2026-07-24", assignedAt = null, assignedBy = null,
        completed = false, completedAt = null, completedBy = null,
    )

    @Test
    fun `drops an assignment whose shift no longer exists`() {
        val jobs = listOf(assignment("a1", "shift-old"), assignment("a2", "shift-current"))
        val result = jobs.excludingOrphaned(validShiftIds = setOf("shift-current"))
        assertEquals(listOf("a2"), result.map { it.id })
    }

    @Test
    fun `keeps everything when the shift window is empty (not yet loaded)`() {
        val jobs = listOf(assignment("a1", "shift-old"))
        val result = jobs.excludingOrphaned(validShiftIds = emptySet())
        assertEquals(jobs, result)
    }

    @Test
    fun `keeps everything when every shift is still valid`() {
        val jobs = listOf(assignment("a1", "s1"), assignment("a2", "s2"))
        val result = jobs.excludingOrphaned(validShiftIds = setOf("s1", "s2", "s3"))
        assertEquals(jobs, result)
    }
}
