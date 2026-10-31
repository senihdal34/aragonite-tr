package com.ethran.notable.reading

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingStatsTest {
    private val zone = ZoneId.of("Europe/Istanbul")
    private fun ms(y: Int, m: Int, d: Int, h: Int = 12, min: Int = 0) =
        ZonedDateTime.of(y, m, d, h, min, 0, 0, zone).toInstant().toEpochMilli()

    private val book = NrBook("/storage/emulated/0/Books/A.pdf", "A", listOf("Writer"), null, 10, 100, ms(2026, 10, 30), ms(2026, 8, 1), false, null)
    private val open = NrEvent(0, ms(2026, 10, 29), "d1", "m", 0, 0, 0.0, "s", "/storage/emulated/0/Books/A.pdf")
    private fun page(t: Long, sec: Long) = NrEvent(1, t, "d1", "m", sec * 1000, 3, 0.0, "s", null)
    private fun run(ev: List<NrEvent>, now: Long, b: List<NrBook> = listOf(book)) =
        ReadingStats.build(b, ev, emptyList(), now, zone, exists = { true })

    @Test fun streakCountsConsecutiveDaysEndingToday() {
        val ev = listOf(open) + listOf(28, 29, 30, 31).map { page(ms(2026, 10, it), 120) }
        assertEquals(4, run(ev, ms(2026, 10, 31, 15)).streakDays)
    }

    @Test fun streakSurvivesUntilEndOfTodayWhenOnlyYesterdayWasRead() {
        val ev = listOf(open) + listOf(29, 30).map { page(ms(2026, 10, it), 120) }
        assertEquals(2, run(ev, ms(2026, 10, 31, 9)).streakDays)
        assertEquals(0, run(ev, ms(2026, 11, 2, 9)).streakDays)
    }

    @Test fun theDayStartsAtMidnightLikeThePlugin() {
        val ev = listOf(open, page(ms(2026, 10, 31, 0, 30), 120))
        val d = run(ev, ms(2026, 10, 31, 3, 0))
        assertEquals(1, d.streakDays)
        assertTrue(d.weekDots.last())              // 00:30 is already today
    }

    @Test fun aSingleShortPageCountsAsAReadingDay() {
        val ev = listOf(open, page(ms(2026, 10, 31, 9), 4))
        val d = run(ev, ms(2026, 10, 31, 15))
        assertEquals(1, d.streakDays)
        assertEquals(1, d.weekMinutes.last())      // shown as at least one minute, like the dots
    }

    @Test fun weekIsTheLastSevenDaysEndingToday() {
        val ev = listOf(open, page(ms(2026, 10, 27), 600), page(ms(2026, 10, 31), 300))
        val d = run(ev, ms(2026, 10, 31, 15))
        assertEquals((0..6).map { java.time.LocalDate.of(2026, 10, 25).plusDays(it.toLong()) }, d.weekDates)
        assertEquals(listOf(0, 0, 3, 0, 0, 0, 3), d.weekMinutes)     // 25..31 Oct, today last; a page counts 3 min at most (idle cap)
        assertEquals(listOf(false, false, true, false, false, false, true), d.weekDots)
        assertEquals(6, d.todayIndex)
    }

    @Test fun anIdlePageIsCapped() {
        val ev = listOf(open, page(ms(2026, 10, 31), 5 * 3600))
        val d = run(ev, ms(2026, 10, 31, 15))
        assertEquals(3, d.minutesByDay.values.sum())
    }

    @Test fun reportsOutsideTheBooksFolderAreIgnored() {
        val report = NrBook("/storage/emulated/0/Documents/Raporlar/R.pdf", "R", emptyList(), null, 1, 5, 1, 1, false, null)
        val d = run(listOf(open), ms(2026, 10, 31), listOf(book, report))
        assertEquals(1, d.totalBooks)
    }

    @Test fun finishedBookCountsForItsYear() {
        val done = book.copy(progressPage = 100, lastAccess = ms(2026, 9, 5))
        val d = run(listOf(open), ms(2026, 10, 31), listOf(done))
        assertEquals(1, d.finishedThisYear.size)
        assertTrue(d.continueReading.isEmpty())
    }

    @Test fun deletedFilesDisappear() {
        val d = ReadingStats.build(listOf(book), emptyList(), emptyList(), ms(2026, 10, 31), zone, exists = { false })
        assertEquals(0, d.totalBooks)
    }
}
