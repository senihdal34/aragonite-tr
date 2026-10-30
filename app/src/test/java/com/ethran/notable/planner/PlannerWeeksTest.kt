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

    @Test fun `label shows range and week of year`() {
        assertEquals("26 Eki – 1 Kas", PlannerWeeks.rangeLabel(LocalDate.of(2026, 10, 26)))
        assertEquals("26 Eki – 1 Kas · 44/53", PlannerWeeks.weekLabel(LocalDate.of(2026, 10, 26)))   // 2026 has 53 ISO weeks
        assertEquals("43/52", PlannerWeeks.weekOfYear(LocalDate.of(2027, 10, 25)))
    }

    @Test fun `weeks in year is 52 or 53`() {
        assertEquals(53, PlannerWeeks.weeksInYear(LocalDate.of(2026, 10, 26)))
        assertEquals(52, PlannerWeeks.weeksInYear(LocalDate.of(2025, 10, 27)))
        assertEquals(52, PlannerWeeks.weeksInYear(LocalDate.of(2027, 3, 1)))
    }

    @Test fun `year percent follows the day of the year`() {
        assertEquals(83, PlannerWeeks.yearPercent(LocalDate.of(2026, 10, 30)))   // day 303 of 365
        assertEquals(0, PlannerWeeks.yearPercent(LocalDate.of(2026, 1, 1)))
        assertEquals(100, PlannerWeeks.yearPercent(LocalDate.of(2026, 12, 31)))
    }

    @Test fun `weeks to ensure start with this week and run eight weeks ahead`() {
        val weeks = PlannerWeeks.weeksToEnsure(LocalDate.of(2026, 10, 30))
        assertEquals(9, weeks.size)
        assertEquals(LocalDate.of(2026, 10, 26), weeks.first())
        assertEquals(LocalDate.of(2026, 12, 21), weeks.last())
    }

    @Test fun `background string round trip`() {
        val monday = LocalDate.of(2026, 10, 26)
        assertEquals(monday, PlannerWeeks.parseWeek(PlannerWeeks.weekBackground(monday)))
        assertEquals(monday, PlannerWeeks.parseTodo("planner-todo:2026-10-26"))
        assertNull(PlannerWeeks.parseWeek("planner-week:not-a-date"))
        assertNull(PlannerWeeks.parseWeek("inbox"))
    }

    @Test fun `planner pages sort by week`() {
        val monday = LocalDate.of(2026, 10, 26)
        val order = listOf(PlannerWeeks.weekBackground(monday.plusWeeks(2)), PlannerWeeks.weekBackground(monday), PlannerWeeks.weekBackground(monday.minusWeeks(1)))
            .sortedBy { PlannerWeeks.sortKey(it) }
        assertEquals(
            listOf(PlannerWeeks.weekBackground(monday.minusWeeks(1)), PlannerWeeks.weekBackground(monday), PlannerWeeks.weekBackground(monday.plusWeeks(2))),
            order
        )
        assertNull(PlannerWeeks.sortKey("blank"))
    }

    @Test fun `day pages follow their week page`() {
        val monday = LocalDate.of(2026, 10, 26)
        val order = listOf(
            PlannerWeeks.weekBackground(monday.plusWeeks(1)), PlannerWeeks.dayBackground(monday.plusDays(2)),
            PlannerWeeks.dayBackground(monday), PlannerWeeks.weekBackground(monday)
        ).sortedBy { PlannerWeeks.sortKey(it) }
        assertEquals(
            listOf(PlannerWeeks.weekBackground(monday), PlannerWeeks.dayBackground(monday), PlannerWeeks.dayBackground(monday.plusDays(2)), PlannerWeeks.weekBackground(monday.plusWeeks(1))),
            order
        )
        assertEquals(monday.plusDays(2), PlannerWeeks.parseDay("planner-day:2026-10-28"))
    }

    @Test fun `iso week 53 in 2026`() =
        assertEquals(53, PlannerWeeks.weekNumber(LocalDate.of(2026, 12, 31)))
}
