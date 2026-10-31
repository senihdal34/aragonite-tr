package com.ethran.notable.reading

import com.ethran.notable.reading.board.BoardData
import com.ethran.notable.reading.board.glyphOfTheDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class BoardDataTest {
    private val today = LocalDate.of(2026, 10, 31)   // a Saturday
    private fun book(id: String, progress: Double = 0.0, left: Int? = null, finished: Long? = null, series: String? = null, added: Long = 0L, last: Long? = null) =
        RBook(id, "/sdcard/Books/$id.epub", id, listOf("A"), series, progress, 100, last, finished, added, null, left)
    private fun data(books: List<RBook> = emptyList(), minutes: Map<Long, Int> = emptyMap(), cont: List<RBook> = emptyList()) =
        ReadingData.Empty.copy(books = books, continueReading = cont, minutesByDay = minutes)
    private fun epoch(d: LocalDate) = d.toEpochDay()

    @Test fun theWeekIsTheLastSevenDaysWithTodayLast() {
        val d = data(minutes = mapOf(epoch(today) to 12, epoch(today.minusDays(1)) to 30, epoch(today.minusDays(6)) to 5))
        val bd = BoardData(d, today)
        assertEquals(listOf(5, 0, 0, 0, 0, 30, 12), bd.week)
        assertEquals(listOf("S", "S", "M", "T", "W", "T", "F").map { it }.size, bd.weekInitials.size)
        assertEquals("S", bd.weekInitials.last())             // today is a Saturday
        assertEquals(listOf(true, false, false, false, false, true, true), bd.streakWeek)
        assertEquals(47, bd.weekMinutes)
    }

    @Test fun theWeekComesFromTheDataLayerWhenItIsThere() {
        val dates = (6 downTo 0).map { today.minusDays(it.toLong()) }
        val d = data().copy(weekMinutes = listOf(1, 2, 3, 4, 5, 6, 7), weekDots = List(7) { true }, weekDates = dates)
        val bd = BoardData(d, today)
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7), bd.week)
        assertEquals(List(7) { true }, bd.streakWeek)
    }

    @Test fun formatsDurationsLikeTheBoard() {
        assertEquals("0 min", BoardData.duration(0)); assertEquals("45 min", BoardData.duration(45))
        assertEquals("1 h", BoardData.duration(60)); assertEquals("1 h 20 min", BoardData.duration(80))
        assertEquals("45m", BoardData.shortDuration(45)); assertEquals("2h", BoardData.shortDuration(120)); assertEquals("1h 5m", BoardData.shortDuration(65))
        assertEquals("1,234", BoardData.count(1234))
    }

    @Test fun aStartedBookNeverReadsZeroAndAnUnfinishedNeverReadsHundred() {
        assertEquals("<1%", BoardData.pctLabel(0.2)); assertEquals("0%", BoardData.pctLabel(0.0))
        assertEquals("99%", BoardData.pctLabel(99.7)); assertEquals("100%", BoardData.pctLabel(100.0)); assertEquals("26%", BoardData.pctLabel(26.4))
    }

    @Test fun theForecastUsesAtLeastFiveMinutesADay() {
        val b = book("a", 10.0, left = 40)
        val bd = BoardData(data(cont = listOf(b)), today)             // nothing read in two weeks
        val f = bd.forecast
        assertNotNull(f)
        assertEquals(5, f!!.perDay)
        assertEquals(7, f.daysOut)                                    // ceil(40 / 5) - 1
        assertEquals(today.plusDays(7), f.date)                       // Saturday 7 Nov, as in the screenshot
    }

    @Test fun theForecastFollowsTheRecentPaceAndCanBeToday() {
        val minutes = (0 until 14).associate { epoch(today.minusDays(it.toLong())) to 20 }
        val bd = BoardData(data(cont = listOf(book("a", 10.0, left = 15)), minutes = minutes), today)
        assertEquals(20, bd.forecast!!.perDay); assertEquals(0, bd.forecast!!.daysOut)
        assertNull(BoardData(data(cont = listOf(book("a", 10.0, left = null))), today).forecast)
        assertNull(BoardData(data(), today).forecast)
    }

    @Test fun theMoonOfTheReferenceDayIsWaningGibbous() {
        val noon = today.atTime(12, 0).atZone(ZoneId.of("Europe/Istanbul")).toInstant().toEpochMilli()
        val m = BoardData.moon(noon)
        assertEquals("Waning gibbous", m.name)
        assertTrue(m.litPercent in 50..100)
        assertTrue(m.next.startsWith("New moon"))
        assertEquals("New moon", BoardData.moon(947182440_000L + 3_600_000L).name)           // just after the reference new moon
        assertEquals("Full moon", BoardData.moon(947182440_000L + (14.77 * 86400_000).toLong()).name)
    }

    @Test fun theGlyphOfTheDayWalksTheListByLocalDay() {
        assertEquals("Ka", glyphOfTheDay(today).name)                 // the screenshot's glyph
        assertEquals("Section sign", glyphOfTheDay(today.plusDays(1)).name)
        assertEquals(glyphOfTheDay(today).cp, glyphOfTheDay(today.plusDays(16)).cp)
    }

    @Test fun dustIsOneUnopenedBookADayAndSkipStepsPastIt() {
        val books = listOf(book("old", added = 1000L), book("newer", added = 2000L), book("read", progress = 10.0, added = 500L), book("opened", added = 600L, last = 5L))
        val bd = BoardData(data(books), today)
        assertEquals(listOf("old", "newer"), bd.unopened.map { it.id })
        val first = bd.dust(0)!!
        assertTrue(bd.dust(1)!!.id != first.id)
        assertEquals(first.id, bd.dust(2)!!.id)                       // walks the whole list before repeating
        assertNull(BoardData(data(), today).dust(0))
    }

    @Test fun shelvesFollowTheLibrary() {
        val books = listOf(
            book("s1", finished = 10L, series = "S", added = 1), book("s2", series = "S", added = 2), book("lone", added = 3),
            book("mid", progress = 40.0, added = 4)
        )
        val bd = BoardData(data(books, cont = listOf(books[3])), today)
        assertEquals(listOf("s2"), bd.upNext.map { it.id })                         // the next of a series already started
        assertEquals(listOf("s2", "lone"), bd.wantToRead.map { it.id })
        assertEquals(listOf("mid"), bd.fullShelf("pick-back-up").map { it.id })
        assertEquals(listOf("mid", "lone", "s2", "s1"), bd.fullShelf("recently-added").map { it.id })
    }

    @Test fun theYearOfReadingIsFiftyTwoWeeksSundayFirstEndingToday() {
        val bd = BoardData(data(), today)
        assertEquals(52 * 7, bd.heat.size)
        assertEquals(java.time.DayOfWeek.SUNDAY, bd.heatStart.dayOfWeek)
        assertEquals(today.dayOfWeek.value % 7, 6 - (bd.heat.size - 1 - bd.heatToday))   // today's row: Saturday is the last row
        assertTrue(bd.heat.drop(bd.heatToday + 1).all { it == null })
    }

    @Test fun twelveMonthsOfRingsEndWithThisMonth() {
        val bd = BoardData(data(minutes = mapOf(epoch(LocalDate.of(2026, 9, 1)) to 48)), today)
        assertEquals(12, bd.months.size)
        assertEquals(10, bd.months.last().month); assertEquals(2025, bd.months.first().year); assertEquals(11, bd.months.first().month)
        assertEquals(48, bd.months.first { it.month == 9 }.minutes)
    }
}
