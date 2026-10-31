package com.ethran.notable.reading

import com.ethran.notable.reading.badges.BadgeMetrics
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BadgeMetricsTest {
    private val zone = ZoneId.of("Europe/Istanbul")
    private fun day(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).toEpochDay()
    private fun book(id: String, pages: Int = 200, finished: Boolean = true, author: String = "A", series: String? = null, path: String = "/sdcard/Books/$id.epub") =
        RBook(id, path, id, listOf(author), series, if (finished) 100.0 else 10.0, pages, finishedAt = if (finished) 1L else null)

    @Test fun longestRunCountsConsecutiveDaysOnly() {
        assertEquals(0, BadgeMetrics.longestRun(emptyList()))
        assertEquals(3, BadgeMetrics.longestRun(listOf(5L, 3L, 4L, 9L, 10L)))
        assertEquals(1, BadgeMetrics.longestRun(listOf(1L, 3L, 5L)))
        assertEquals(2, BadgeMetrics.longestRun(listOf(1L, 1L, 2L)))   // a duplicate is not a second day
    }

    @Test fun runReachedOnIsTheDayTheRunGetsLongEnough() {
        assertEquals(7L, BadgeMetrics.runReachedOn(listOf(1L, 2L, 5L, 6L, 7L), 3))
        assertNull(BadgeMetrics.runReachedOn(listOf(1L, 2L, 4L), 3))
    }

    @Test fun breakIsTheDaysWithoutReadingBetweenTwoReadingDays() {
        assertEquals(0, BadgeMetrics.longestBreakDays(listOf(1L)))
        assertEquals(0, BadgeMetrics.longestBreakDays(listOf(1L, 2L, 3L)))
        assertEquals(30, BadgeMetrics.longestBreakDays(listOf(1L, 32L, 33L)))
        assertEquals(29, BadgeMetrics.longestBreakDays(listOf(40L, 1L, 4L, 10L)))   // sorted: 1, 4, 10, 40
    }

    @Test fun monthsAndSeasonsAreCountedInsideOneYear() {
        val days = listOf(day(2025, 1, 5), day(2025, 2, 5), day(2025, 6, 1), day(2026, 3, 1), day(2026, 4, 1), day(2026, 5, 1), day(2026, 9, 1))
        assertEquals(4, BadgeMetrics.maxMonthsInYear(days))     // 2026 has March, April, May, September
        assertEquals(2, BadgeMetrics.maxSeasonsInYear(days))    // 2026: spring and autumn; 2025: winter and summer
    }

    @Test fun seasonsFollowBookOrbitsMonths() {
        assertEquals(1, BadgeMetrics.season(12)); assertEquals(1, BadgeMetrics.season(2))
        assertEquals(2, BadgeMetrics.season(3)); assertEquals(3, BadgeMetrics.season(8)); assertEquals(4, BadgeMetrics.season(11))
        val all = listOf(day(2026, 1, 1), day(2026, 4, 1), day(2026, 7, 1), day(2026, 10, 1))
        assertEquals(4, BadgeMetrics.maxSeasonsInYear(all))
        assertEquals(0, BadgeMetrics.maxSeasonsInYear(emptyList()))
    }

    @Test fun perfectMonthNeedsEveryDay() {
        val feb = (1..28).map { day(2026, 2, it) }
        assertTrue(BadgeMetrics.hasPerfectMonth(feb))
        assertFalse(BadgeMetrics.hasPerfectMonth(feb.drop(1)))
        assertTrue(BadgeMetrics.hasPerfectMonth((1..29).map { day(2028, 2, it) }))      // a leap February has 29
        assertFalse(BadgeMetrics.hasPerfectMonth((1..28).map { day(2028, 2, it) }))
    }

    @Test fun weekendsMustFollowEachOtherWeekByWeek() {
        // 3 Oct 2026 is a Saturday. Saturdays of three weeks in a row, then a Sunday two weeks later.
        val sat = day(2026, 10, 3)
        val three = listOf(sat, sat + 7, sat + 14)
        assertEquals(3, BadgeMetrics.consecutiveWeekends(three))
        assertEquals(1, BadgeMetrics.consecutiveWeekends(listOf(sat, sat + 15)))      // a gap week breaks the run
        assertEquals(1, BadgeMetrics.consecutiveWeekends(listOf(sat, sat + 1)))       // Saturday and Sunday of one weekend is one
        assertEquals(0, BadgeMetrics.consecutiveWeekends(listOf(sat + 2, sat + 3)))   // Monday and Tuesday are no weekend
        assertEquals(2, BadgeMetrics.consecutiveWeekends(listOf(sat + 1, sat + 8)))   // the Sunday of one weekend, then the Sunday of the next
    }

    @Test fun weekIndexKeepsMondayToSundayTogether() {
        val monday = day(2026, 10, 5)
        assertEquals(BadgeMetrics.weekIndex(monday), BadgeMetrics.weekIndex(monday + 6))
        assertEquals(BadgeMetrics.weekIndex(monday) + 1, BadgeMetrics.weekIndex(monday + 7))
    }

    @Test fun weekendMinutesAddSaturdayToItsSunday() {
        val sat = day(2026, 10, 3)
        assertEquals(300, BadgeMetrics.maxWeekendMinutes(mapOf(sat to 100, sat + 1 to 200, sat + 7 to 250, sat + 3 to 999)))   // Tuesday is not a weekend
        assertEquals(0, BadgeMetrics.maxWeekendMinutes(emptyMap()))
    }

    @Test fun consistentRunNeedsThirtyMinutesEachDay() {
        val m = mapOf(1L to 30, 2L to 45, 3L to 29, 4L to 60, 5L to 60, 6L to 60)
        assertEquals(3, BadgeMetrics.consistentRun(m))
        assertEquals(6, BadgeMetrics.consistentRun(m, 29))
    }

    @Test fun newYearsDay() {
        assertTrue(BadgeMetrics.readOnNewYearsDay(listOf(day(2026, 1, 1))))
        assertFalse(BadgeMetrics.readOnNewYearsDay(listOf(day(2026, 1, 2), day(2025, 12, 31))))
    }

    @Test fun finishedWindows() {
        val days = listOf(10L, 12L, 16L, 17L, 40L)
        assertEquals(3, BadgeMetrics.maxFinishedInWindow(days, 7))       // 10, 12, 16 fit in seven days (10..16)
        assertEquals(2, BadgeMetrics.maxFinishedInWindow(days, 5))
        assertEquals(0, BadgeMetrics.maxFinishedInWindow(emptyList()))
        assertEquals(1, BadgeMetrics.maxFinishedInWindow(listOf(1L, 8L)))   // eight days apart is two weeks
    }

    @Test fun finishedPerMonthAndYear() {
        val d = listOf(day(2026, 1, 1), day(2026, 1, 31), day(2026, 2, 1), day(2025, 12, 31))
        assertEquals(2, BadgeMetrics.maxFinishedInMonth(d))
        assertEquals(3, BadgeMetrics.maxFinishedInYear(d))
        assertEquals(0, BadgeMetrics.maxFinishedInYear(emptyList()))
    }

    @Test fun booksPerAuthorCountsFirstAuthorCaseInsensitively() {
        val b = listOf(book("1", author = "Le Guin"), book("2", author = "le guin "), book("3", author = "Other"), book("4", author = ""))
        assertEquals(2, BadgeMetrics.maxBooksPerAuthor(b))
        assertEquals(0, BadgeMetrics.maxBooksPerAuthor(emptyList()))
    }

    @Test fun shortBooksNeedAKnownPageCountUnderAHundred() {
        val b = listOf(book("1", pages = 99), book("2", pages = 100), book("3", pages = 0), book("4", pages = 1))
        assertEquals(2, BadgeMetrics.countShort(b))
    }

    @Test fun thickAndThinNeedsBoth() {
        assertTrue(BadgeMetrics.thickAndThin(listOf(book("1", pages = 50), book("2", pages = 801))))
        assertFalse(BadgeMetrics.thickAndThin(listOf(book("1", pages = 50), book("2", pages = 800))))
        assertFalse(BadgeMetrics.thickAndThin(listOf(book("2", pages = 900))))
    }

    @Test fun aCompleteSeriesIsThreeBooksAllFinished() {
        val done = listOf(book("1", series = "S"), book("2", series = "S"), book("3", series = "S"))
        assertTrue(BadgeMetrics.completeSeries(done))
        assertFalse(BadgeMetrics.completeSeries(done.dropLast(1)))
        assertFalse(BadgeMetrics.completeSeries(done.dropLast(1) + book("3", series = "S", finished = false)))
        assertFalse(BadgeMetrics.completeSeries(listOf(book("1"), book("2"), book("3"))))     // no series name
    }

    @Test fun formatsAndFolders() {
        val b = listOf(book("a", path = "/sdcard/Books/a.EPUB"), book("b", path = "/sdcard/Books/b.pdf"), book("c", path = "/sdcard/Books/Sci/c.epub"), book("d", path = "/x/noext"))
        assertEquals(2, BadgeMetrics.formatCount(b))
        assertEquals(3, BadgeMetrics.folderCount(b))
    }

    @Test fun highlightsPerLocalDay() {
        fun ms(h: Int, d: Int) = ZonedDateTime.of(2026, 10, d, h, 0, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals(3, BadgeMetrics.maxHighlightsInDay(listOf(ms(1, 5), ms(12, 5), ms(23, 5), ms(0, 6)), zone))
        assertEquals(0, BadgeMetrics.maxHighlightsInDay(emptyList(), zone))
    }

    @Test fun sprintIsABookReadOnItsFinishingDayOnly() {
        fun ms(d: Int) = ZonedDateTime.of(2026, 10, d, 12, 0, 0, 0, zone).toInstant().toEpochMilli()
        val one = book("one").copy(finishedAt = ms(5))
        val two = book("two").copy(finishedAt = ms(9))
        val byDay = mapOf(day(2026, 10, 5) to listOf("one", "two"), day(2026, 10, 9) to listOf("two"))
        assertTrue(BadgeMetrics.hasSprint(listOf(one), byDay, zone))
        assertFalse(BadgeMetrics.hasSprint(listOf(two), byDay, zone))                   // read on two days
        assertFalse(BadgeMetrics.hasSprint(listOf(one.copy(finishedAt = ms(6))), byDay, zone))  // read on the 5th, finished on the 6th
        assertFalse(BadgeMetrics.hasSprint(listOf(book("none").copy(finishedAt = ms(5))), byDay, zone))
    }

    @Test fun slowBurnIsMoreThanNinetyDaysFromFirstReadToFinish() {
        fun ms(m: Int, d: Int) = ZonedDateTime.of(2026, m, d, 12, 0, 0, 0, zone).toInstant().toEpochMilli()
        val b = book("b").copy(finishedAt = ms(7, 1))
        val byDay = mapOf(day(2026, 4, 1) to listOf("b"), day(2026, 6, 1) to listOf("b"))   // 91 days from 1 Apr to 1 Jul
        assertTrue(BadgeMetrics.hasSlowBurn(listOf(b), byDay, zone))
        assertFalse(BadgeMetrics.hasSlowBurn(listOf(b.copy(finishedAt = ms(6, 30))), byDay, zone))   // exactly 90 days is not over
        assertFalse(BadgeMetrics.hasSlowBurn(listOf(b), emptyMap(), zone))
    }

    @Test fun hoursOfTheDayAreSummedInARange() {
        val hours = List(24) { if (it == 2 || it == 5) 7 else 0 }
        assertEquals(7, BadgeMetrics.minutesInHours(hours, 1, 4))
        assertEquals(7, BadgeMetrics.minutesInHours(hours, 5, 7))
        assertEquals(0, BadgeMetrics.minutesInHours(hours, 7, 24))
        assertEquals(0, BadgeMetrics.minutesInHours(emptyList(), 1, 4))
    }
}
