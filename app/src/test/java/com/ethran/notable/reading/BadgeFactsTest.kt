package com.ethran.notable.reading

import com.ethran.notable.reading.badges.BadgeEngine
import com.ethran.notable.reading.badges.BadgeFacts
import com.ethran.notable.reading.badges.InMemoryBadgeStore
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Builds badge facts from data made the way the app makes it (ReadingStats.build) and checks the numbers and the rules on top. */
class BadgeFactsTest {
    private val zone = ZoneId.of("Europe/Istanbul")
    private val today = LocalDate.of(2026, 10, 31)
    private fun ms(m: Int, d: Int, h: Int = 12, y: Int = 2026) = ZonedDateTime.of(y, m, d, h, 0, 0, 0, zone).toInstant().toEpochMilli()
    private val now = ms(10, 31, 15)
    private fun nrBook(name: String, page: Int = 10, total: Int = 100, last: Long = ms(10, 1), series: String? = null) =
        NrBook("/storage/emulated/0/Books/$name", name, listOf("Writer"), series, page, total, last, ms(1, 1), page >= total, null)
    private fun open(name: String, doc: String, t: Long) = NrEvent(0, t, doc, "m", 0, 0, 0.0, "s", "/storage/emulated/0/Books/$name")
    private fun page(doc: String, t: Long, sec: Long = 60) = NrEvent(1, t, doc, "m", sec * 1000, 3, 0.0, "s", null)
    private fun build(books: List<NrBook>, ev: List<NrEvent>, hl: List<NrHighlight> = emptyList()) =
        ReadingStats.build(books, ev, hl, now, zone, exists = { true })
    private fun facts(books: List<NrBook>, ev: List<NrEvent>, hl: List<NrHighlight> = emptyList()) = BadgeFacts.from(build(books, ev, hl), today, zone)

    @Test fun anEmptyLibraryHasNothing() {
        val f = BadgeFacts.from(ReadingData.Empty, today, zone)
        assertEquals(0, f.booksFinished); assertEquals(0, f.totalMinutes); assertEquals(0, f.longestStreak)
        assertFalse(f.sprint); assertFalse(f.readNight); assertFalse(f.perfectMonth)
        assertNull(f.pace.minutesPerDay); assertNull(f.pace.booksPerDay); assertNull(f.pace.pagesPerHour)
    }

    @Test fun finishedBooksPagesAndTimeAreCounted() {
        val ev = listOf(open("a.epub", "d1", ms(10, 1)), page("d1", ms(10, 1)), page("d1", ms(10, 1, 13)), page("d1", ms(10, 2)))
        val f = facts(listOf(nrBook("a.epub", 100, 100), nrBook("b.pdf", 30, 100)), ev)
        assertEquals(1, f.booksFinished)
        assertEquals(2, f.libraryCount)
        assertEquals(3, f.pageTurns)
        assertEquals(2, f.formats)
        assertEquals(1, f.folders)
        assertEquals(2, f.readDays.size)
        assertEquals(3, f.totalMinutes)                      // three pages of one minute, on two days
        assertEquals(100, f.maxFinishedPages)
    }

    @Test fun pagesInADayComeFromPageTurnsOnThatDay() {
        val ev = listOf(open("a.epub", "d1", ms(10, 1))) + (0 until 5).map { page("d1", ms(10, 1, 10) + it * 1000L) } + listOf(page("d1", ms(10, 2)))
        val f = facts(listOf(nrBook("a.epub")), ev)
        assertEquals(5, f.maxPagesInDay)
    }

    @Test fun aSessionsPagesAreTheLongestRunWithoutATenMinuteGap() {
        val t = ms(10, 1, 10)
        val ev = listOf(open("a.epub", "d1", t)) + (0 until 4).map { page("d1", t + it * 60_000L) } + (0 until 2).map { page("d1", t + 3_600_000L + it * 60_000L) }
        val data = build(listOf(nrBook("a.epub")), ev)
        assertEquals(4, data.records?.maxPages)
        assertEquals(4, BadgeFacts.from(data, today, zone).maxSessionPages)
    }

    @Test fun nightAndEarlyReadingShowInTheHoursOfTheDay() {
        val ev = listOf(open("a.epub", "d1", ms(10, 1)), page("d1", ms(10, 25, 2), 120), page("d1", ms(10, 26, 6), 120))
        val f = facts(listOf(nrBook("a.epub")), ev)
        assertTrue(f.readNight); assertTrue(f.readEarly)
        val none = facts(listOf(nrBook("a.epub")), listOf(open("a.epub", "d1", ms(10, 1)), page("d1", ms(10, 25, 14), 120)))
        assertFalse(none.readNight); assertFalse(none.readEarly)
    }

    @Test fun streaksAndConsistencyComeFromTheDaysRead() {
        val ev = listOf(open("a.epub", "d1", ms(10, 20))) + (20..31).flatMap { d -> (0 until 31).map { page("d1", ms(10, d, 10) + it * 1000L, 60) } }
        val f = facts(listOf(nrBook("a.epub")), ev)
        assertEquals(12, f.longestStreak)
        assertEquals(12, f.consistentRun)                    // 31 minutes every day
        assertEquals(12 * 31, f.totalMinutes)
        assertNotNull(f.pace.minutesPerDay)
        assertEquals(12 * 31 / 90.0, f.pace.minutesPerDay!!, 1e-9)
    }

    @Test fun aSeriesFinishedBookAndTheCompleteSeries() {
        val books = (1..3).map { nrBook("s$it.epub", 100, 100, series = "Saga") } + nrBook("x.epub", 10, 100)
        val f = facts(books, listOf(open("s1.epub", "d1", ms(10, 1))))
        assertTrue(f.firstSeries); assertTrue(f.completeSeries)
        assertEquals(3, f.maxBooksPerAuthor)
        val partial = facts(listOf(nrBook("s1.epub", 100, 100, series = "Saga"), nrBook("s2.epub", 100, 100, series = "Saga"), nrBook("s3.epub", 50, 100, series = "Saga")), emptyList())
        assertFalse(partial.completeSeries); assertTrue(partial.firstSeries)
    }

    @Test fun highlightsAreCountedPerDay() {
        val books = listOf(nrBook("a.epub"))
        val hl = (0 until 12).map { NrHighlight("q$it", ms(10, 5, 9) + it * 1000L, null, "d1") } + NrHighlight("late", ms(10, 6), null, "d1")
        val f = facts(books, listOf(open("a.epub", "d1", ms(10, 1))), hl)
        assertEquals(13, f.highlightCount); assertEquals(12, f.maxHighlightsInDay)
    }

    @Test fun paceHasBooksPerDayFromThisYearAndPagesPerHour() {
        val ev = listOf(open("a.epub", "d1", ms(10, 1))) + (0 until 30).map { page("d1", ms(10, 1, 10) + it * 60_000L, 60) }
        val f = facts(listOf(nrBook("a.epub", 100, 100, last = ms(10, 2))), ev)
        assertEquals(1.0 / today.dayOfYear, f.pace.booksPerDay!!, 1e-9)
        assertEquals(60, f.pace.pagesPerHour)
    }

    @Test fun engineEarnsBadgesFromBuiltData() {
        val ev = listOf(open("a.epub", "d1", ms(10, 1))) + (20..31).flatMap { d -> (0 until 31).map { page("d1", ms(10, d, 10) + it * 1000L) } }
        val f = facts(listOf(nrBook("a.epub", 100, 100)), ev)
        val store = InMemoryBadgeStore()
        BadgeEngine.evaluate(f, store, now)                // first fill
        val b = BadgeEngine.evaluate(f, store, now + 1)
        val keys = b.badges.filter { it.earned }.map { it.key }
        assertTrue("books_finished_1" in keys); assertTrue("streak_1" in keys)
        assertFalse("streak_2" in keys)
        assertNotNull(b.next)
    }
}
