package com.ethran.notable.reading

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingInsightsTest {
    private val zone = ZoneId.of("Europe/Istanbul")
    private val today = LocalDate.of(2026, 10, 28) // a Wednesday
    private fun day(d: LocalDate) = d.toEpochDay()
    private fun ms(y: Int, m: Int, d: Int, h: Int = 12) = ZonedDateTime.of(y, m, d, h, 0, 0, 0, zone).toInstant().toEpochMilli()
    private fun book(id: String, left: Int? = null, finishedAt: Long? = null) =
        RBook(id, "/sdcard/Books/$id.pdf", id, listOf("A"), minutesLeft = left, finishedAt = finishedAt)

    @Test fun heatLevelsFollowTheBoardThresholds() {
        assertEquals(0, ReadingInsights.heatLevel(null))
        assertEquals(0, ReadingInsights.heatLevel(0))
        assertEquals(1, ReadingInsights.heatLevel(1))
        assertEquals(1, ReadingInsights.heatLevel(29))
        assertEquals(2, ReadingInsights.heatLevel(30))
        assertEquals(3, ReadingInsights.heatLevel(60))
        assertEquals(4, ReadingInsights.heatLevel(120))
    }

    @Test fun heatmapHasSixteenSundayFirstWeeksEndingThisWeek() {
        val g = ReadingInsights.heatmap(emptyMap(), today)
        assertEquals(16, g.weeks.size)
        assertEquals(16, g.monthLabels.size)
        assertTrue(g.weeks.all { it.size == 7 })
        // Wednesday: Sunday..Wednesday are known, Thursday..Saturday have not come yet.
        assertEquals(listOf(0, 0, 0, 0, null, null, null), g.weeks.last())
        assertTrue(g.weeks.dropLast(1).all { w -> w.all { it != null } })
    }

    @Test fun heatmapCountsMinutesDaysAndBestDay() {
        val m = mapOf(
            day(today) to 20, day(today.minusDays(1)) to 90, day(today.minusDays(40)) to 15,
            day(today.minusDays(400)) to 500, // outside the 16 weeks
            day(today.plusDays(1)) to 99      // in the future: not drawn
        )
        val g = ReadingInsights.heatmap(m, today)
        assertEquals(125, g.totalMinutes)
        assertEquals(3, g.daysRead)
        assertEquals(90, g.bestDayMinutes)
        assertEquals(20, g.weeks.last()[3]) // Wednesday is index 3
        assertEquals(90, g.weeks.last()[2])
    }

    @Test fun heatmapLabelsAMonthOnlyWhereItChanges() {
        val labels = ReadingInsights.heatmap(emptyMap(), today).monthLabels
        val named = labels.filterNotNull()
        assertTrue(named.size in 3..5)
        assertEquals(named.size, named.zipWithNext().count { it.first != it.second } + 1)
        assertFalse(labels.zipWithNext().any { it.first != null && it.second != null })
    }

    @Test fun paceAveragesTheLastFourteenDays() {
        val m = (0 until 14).associate { day(today.minusDays(it.toLong())) to 30 } + (day(today.minusDays(20)) to 999)
        assertEquals(30.0, ReadingInsights.recentPace(m, today), 0.0001)
        assertEquals(0.0, ReadingInsights.recentPace(emptyMap(), today), 0.0)
    }

    @Test fun forecastUsesTheFirstBookWithAnEstimate() {
        val m = (0 until 14).associate { day(today.minusDays(it.toLong())) to 30 }
        val f = ReadingInsights.forecast(listOf(book("none"), book("a", left = 100), book("b", left = 5)), m, today)
        assertNotNull(f)
        assertEquals("a", f!!.book.id)
        assertEquals(30, f.perDay)
        assertEquals(4, f.daysOut)               // ceil(100 / 30)
        assertEquals(today.plusDays(4), f.date)
    }

    @Test fun forecastIsNullWithoutPaceEstimateOrWithAFarFinish() {
        val m = (0 until 14).associate { day(today.minusDays(it.toLong())) to 30 }
        assertNull(ReadingInsights.forecast(listOf(book("a", left = 100)), emptyMap(), today))
        assertNull(ReadingInsights.forecast(listOf(book("a")), m, today))
        assertNull(ReadingInsights.forecast(emptyList(), m, today))
        assertNull(ReadingInsights.forecast(listOf(book("a", left = 100_000)), m, today))
    }

    @Test fun finishLabelNamesTheDay() {
        val b = book("a", left = 10)
        assertEquals("Tomorrow", ReadingInsights.finishLabel(Forecast(b, 10, 30, 1, today.plusDays(1))))
        // 2026-11-07 is a Saturday.
        assertEquals("Saturday, 7 Nov", ReadingInsights.finishLabel(Forecast(b, 10, 30, 10, LocalDate.of(2026, 11, 7))))
    }

    @Test fun hoursSplitThisYearAndThisMonth() {
        val m = mapOf(
            day(today) to 60, day(LocalDate.of(2026, 10, 2)) to 30, day(LocalDate.of(2026, 3, 1)) to 120,
            day(LocalDate.of(2025, 12, 31)) to 500, // last year
            day(LocalDate.of(2026, 11, 2)) to 77     // after today
        )
        val h = ReadingInsights.hours(m, today)
        assertEquals(210, h.yearMinutes)
        assertEquals(90, h.monthMinutes)
        assertEquals(2026, h.year)
        assertEquals("October", h.month)
    }

    @Test fun longestStreakFindsTheLongestRun() {
        val base = LocalDate.of(2026, 1, 1)
        val days = listOf(0, 1, 2, 10, 11, 12, 13, 14, 30).associate { day(base.plusDays(it.toLong())) to 5 } +
            (day(base.plusDays(3)) to 0) // a zero day breaks the run
        assertEquals(5, ReadingInsights.longestStreak(days))
        assertEquals(0, ReadingInsights.longestStreak(emptyMap()))
    }

    @Test fun recordsListBestDayAndBooksPerYear() {
        val m = mapOf(day(LocalDate.of(2026, 5, 5)) to 45, day(LocalDate.of(2026, 5, 6)) to 130)
        val books = listOf(
            book("a", finishedAt = ms(2026, 2, 1)), book("b", finishedAt = ms(2026, 8, 1)),
            book("c", finishedAt = ms(2025, 6, 1)), book("d")
        )
        val r = ReadingInsights.records(m, books, zone)
        assertEquals(2, r.longestStreak)
        assertEquals(LocalDate.of(2026, 5, 6), r.bestDay)
        assertEquals(130, r.bestDayMinutes)
        assertEquals(listOf(2026 to 2, 2025 to 1), r.booksPerYear)
        val empty = ReadingInsights.records(emptyMap(), emptyList(), zone)
        assertNull(empty.bestDay)
        assertTrue(empty.booksPerYear.isEmpty())
    }

    @Test fun yearAgoFindsTheBookReadOnThatDay() {
        val a = book("a"); val b = book("b")
        val ago = today.minusYears(1)
        val data = ReadingData.Empty.copy(
            books = listOf(a, b),
            booksByDay = mapOf(day(ago) to listOf("gone", "b"), day(ago.plusDays(2)) to listOf("a")),
            minutesByDay = mapOf(day(ago) to 33)
        )
        val y = ReadingInsights.yearAgo(data, today)
        assertNotNull(y)
        assertEquals("b", y!!.book.id) // the id of a book no longer on the shelf is skipped
        assertEquals(ago, y.date)
        assertEquals(33, y.minutes)
    }

    @Test fun yearAgoFallsBackToANearbyDayThenToNothing() {
        val a = book("a")
        val ago = today.minusYears(1)
        val near = ReadingData.Empty.copy(books = listOf(a), booksByDay = mapOf(day(ago.plusDays(2)) to listOf("a")))
        assertEquals(ago.plusDays(2), ReadingInsights.yearAgo(near, today)!!.date)
        val far = ReadingData.Empty.copy(books = listOf(a), booksByDay = mapOf(day(ago.plusDays(20)) to listOf("a")))
        assertNull(ReadingInsights.yearAgo(far, today))
        assertNull(ReadingInsights.yearAgo(ReadingData.Empty, today))
    }

    @Test fun yearAgoNeverReturnsADayFromThisYear() {
        // On 1 Mar 2027, "a year ago" is 1 Mar 2026; nothing can reach into the future of that date.
        val a = book("a")
        val data = ReadingData.Empty.copy(books = listOf(a), booksByDay = mapOf(day(LocalDate.of(2027, 2, 28)) to listOf("a")))
        assertNull(ReadingInsights.yearAgo(data, LocalDate.of(2027, 3, 1)))
    }

    @Test fun datesAreWrittenOutShort() {
        assertEquals("6 May 2026", ReadingInsights.shortDate(LocalDate.of(2026, 5, 6)))
        assertEquals("Sat, 31 Oct 2026", ReadingInsights.dateLabel(LocalDate.of(2026, 10, 31)))
    }

    @Test fun finishedTodayLooksAtTheReadingDay() {
        val books = listOf(book("a", finishedAt = ms(2026, 10, 31, 1)))   // the reading day starts at midnight: 01:00 is 31 Oct
        assertFalse(ReadingInsights.finishedToday(books, ms(2026, 10, 30, 20), zone))
        assertTrue(ReadingInsights.finishedToday(books, ms(2026, 10, 31, 20), zone))
    }

    @Test fun buildRecordsWhichBooksWereReadOnWhichDay() {
        val nr = NrBook("/storage/emulated/0/Books/A.pdf", "A", listOf("W"), null, 10, 100, ms(2026, 10, 30), ms(2026, 8, 1), false, null)
        val nr2 = NrBook("/storage/emulated/0/Books/B.pdf", "B", listOf("W"), null, 10, 100, ms(2026, 10, 30), ms(2026, 8, 1), false, null)
        val ev = listOf(
            NrEvent(0, ms(2026, 10, 29), "d1", "m", 0, 0, 0.0, "s", "/storage/emulated/0/Books/A.pdf"),
            NrEvent(0, ms(2026, 10, 29), "d2", "m", 0, 0, 0.0, "s", "/storage/emulated/0/Books/B.pdf"),
            NrEvent(1, ms(2026, 10, 29), "d1", "m", 60_000, 3, 0.0, "s", null),
            NrEvent(1, ms(2026, 10, 29), "d2", "m", 120_000, 3, 0.0, "s", null),
            NrEvent(1, ms(2026, 10, 30), "d1", "m", 60_000, 3, 0.0, "s", null)
        )
        val d = ReadingStats.build(listOf(nr, nr2), ev, emptyList(), ms(2026, 10, 31), zone, exists = { true })
        val d29 = LocalDate.of(2026, 10, 29).toEpochDay()
        assertEquals(listOf("/sdcard/Books/B.pdf", "/sdcard/Books/A.pdf"), d.booksByDay[d29]) // most read first
        assertEquals(listOf("/sdcard/Books/A.pdf"), d.booksByDay[LocalDate.of(2026, 10, 30).toEpochDay()])
    }
}
