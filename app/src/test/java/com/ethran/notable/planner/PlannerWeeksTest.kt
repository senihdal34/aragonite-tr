package com.ethran.notable.planner

import org.junit.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlannerWeeksTest {
    @Test fun `monday of any day in the week`() {
        assertEquals(LocalDate.of(2026, 10, 26), PlannerWeeks.mondayOf(LocalDate.of(2026, 10, 30)))   // Friday
        assertEquals(LocalDate.of(2026, 10, 26), PlannerWeeks.mondayOf(LocalDate.of(2026, 11, 1)))    // Sunday
        assertEquals(LocalDate.of(2026, 10, 26), PlannerWeeks.mondayOf(LocalDate.of(2026, 10, 26)))   // Monday
    }

    @Test fun `weeks to ensure start with this week and run eight weeks ahead`() {
        val weeks = PlannerWeeks.weeksToEnsure(LocalDate.of(2026, 10, 30))
        assertEquals(9, weeks.size)
        assertEquals(LocalDate.of(2026, 10, 26), weeks.first())
        assertEquals(LocalDate.of(2026, 12, 21), weeks.last())
    }

    @Test fun `label shows range and week number`() =
        assertEquals("26 Eki – 1 Kas · 44. hafta", PlannerWeeks.weekLabel(LocalDate.of(2026, 10, 26)))

    @Test fun `background string round trip`() {
        val monday = LocalDate.of(2026, 10, 26)
        assertEquals(monday, PlannerWeeks.parseWeek(PlannerWeeks.weekBackground(monday)))
        assertEquals(monday, PlannerWeeks.parseTodo(PlannerWeeks.todoBackground(monday)))
        assertNull(PlannerWeeks.parseWeek("planner-week:not-a-date"))
        assertNull(PlannerWeeks.parseWeek("inbox"))
    }

    @Test fun `iso week 53 in 2026`() =
        assertEquals(53, PlannerWeeks.weekNumber(LocalDate.of(2026, 12, 31)))
}
