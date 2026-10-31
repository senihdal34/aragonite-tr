package com.ethran.notable.reading.badges

import com.ethran.notable.reading.RBook
import com.ethran.notable.reading.ReadingData
import com.ethran.notable.reading.ReadingInsights
import com.ethran.notable.reading.ReadingStats
import java.time.LocalDate
import java.time.ZoneId

/** How fast the reader goes, for the time-to-go line (BoardData.badges pace in lb_board_data.lua). */
data class BadgePace(val minutesPerDay: Double?, val booksPerDay: Double?, val pagesPerHour: Int?)

/**
 * Every number the badge rules need, worked out once from [ReadingData]. Pure: no Android calls, so it is unit tested
 * with built data. Earned badges are never taken back, so a number that later shrinks (a deleted book) costs nothing.
 */
data class BadgeFacts(
    val zone: ZoneId,
    val finished: List<RBook>,
    val booksFinished: Int,
    val libraryCount: Int,
    val pageTurns: Int,
    val totalMinutes: Int,
    val longestSessionMin: Int,
    val maxSessionPages: Int,
    val maxPagesInDay: Int,
    val readNight: Boolean,
    val readEarly: Boolean,
    val maxFinishedPages: Int,
    val sprint: Boolean,
    val bingeMax: Int,
    val monthlyMax: Int,
    val slowBurn: Boolean,
    val highlightCount: Int,
    val maxHighlightsInDay: Int,
    val formats: Int,
    val folders: Int,
    val maxBooksPerAuthor: Int,
    val shortFinished: Int,
    val thickAndThin: Boolean,
    val completeSeries: Boolean,
    val firstSeries: Boolean,
    val longestStreak: Int,
    val monthsInYear: Int,
    val seasonsInYear: Int,
    val perfectMonth: Boolean,
    val breakDays: Int,
    val newYear: Boolean,
    val consistentRun: Int,
    val weekendRun: Int,
    val weekendMinutes: Int,
    val yearlyMax: Int,
    /** Epoch days with some reading, ascending. */
    val readDays: List<Long>,
    val minutesByDay: Map<Long, Int>,
    val pace: BadgePace
) {
    companion object {
        /** Days of reading the pace is averaged over, as the plugin does. */
        const val PACE_DAYS = 90

        fun from(data: ReadingData, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): BadgeFacts {
            val readDaySet = data.minutesByDay.filterValues { it > 0 }.keys
            val finished = data.books.filter { it.finishedAt != null }.sortedBy { it.finishedAt }
            val finishDays = finished.map { ReadingStats.dayOf(it.finishedAt ?: 0L, zone).toEpochDay() }
            val records = data.records
            val minutes = data.minutesByDay.values.sum()
            val paceMinutes = (0 until PACE_DAYS).sumOf { data.minutesByDay[today.minusDays(it.toLong()).toEpochDay()] ?: 0 }
            val finishedYear = data.finishedThisYear.size
            return BadgeFacts(
                zone = zone,
                finished = finished,
                booksFinished = finished.size,
                libraryCount = data.books.size,
                pageTurns = data.pageTurns?.total ?: 0,
                totalMinutes = minutes,
                longestSessionMin = records?.longestMin ?: 0,
                maxSessionPages = records?.maxPages ?: 0,
                maxPagesInDay = data.pagesByDay.values.maxOrNull() ?: 0,
                readNight = BadgeMetrics.minutesInHours(data.hours, 1, 4) > 0,
                readEarly = BadgeMetrics.minutesInHours(data.hours, 5, 7) > 0,
                maxFinishedPages = finished.maxOfOrNull { it.pages } ?: 0,
                sprint = BadgeMetrics.hasSprint(finished, data.booksByDay, zone),
                bingeMax = BadgeMetrics.maxFinishedInWindow(finishDays),
                monthlyMax = BadgeMetrics.maxFinishedInMonth(finishDays),
                slowBurn = BadgeMetrics.hasSlowBurn(finished, data.booksByDay, zone),
                highlightCount = data.highlights.size,
                maxHighlightsInDay = BadgeMetrics.maxHighlightsInDay(data.highlights.map { it.time }, zone),
                formats = BadgeMetrics.formatCount(data.books),
                folders = BadgeMetrics.folderCount(data.books),
                maxBooksPerAuthor = BadgeMetrics.maxBooksPerAuthor(finished),
                shortFinished = BadgeMetrics.countShort(finished),
                thickAndThin = BadgeMetrics.thickAndThin(finished),
                completeSeries = BadgeMetrics.completeSeries(data.books),
                firstSeries = finished.any { it.series != null },
                longestStreak = maxOf(ReadingInsights.longestStreak(data.minutesByDay), data.streakDays),
                monthsInYear = BadgeMetrics.maxMonthsInYear(readDaySet),
                seasonsInYear = BadgeMetrics.maxSeasonsInYear(readDaySet),
                perfectMonth = BadgeMetrics.hasPerfectMonth(readDaySet),
                breakDays = BadgeMetrics.longestBreakDays(readDaySet),
                newYear = BadgeMetrics.readOnNewYearsDay(readDaySet),
                consistentRun = BadgeMetrics.consistentRun(data.minutesByDay),
                weekendRun = BadgeMetrics.consecutiveWeekends(readDaySet),
                weekendMinutes = BadgeMetrics.maxWeekendMinutes(data.minutesByDay),
                yearlyMax = BadgeMetrics.maxFinishedInYear(finishDays),
                readDays = readDaySet.sorted(),
                minutesByDay = data.minutesByDay,
                pace = BadgePace(
                    minutesPerDay = paceMinutes.takeIf { it > 0 }?.let { it.toDouble() / PACE_DAYS },
                    booksPerDay = finishedYear.takeIf { it > 0 }?.let { it.toDouble() / maxOf(1, today.dayOfYear) },
                    pagesPerHour = records?.pagesPerHour?.takeIf { it > 0 }
                )
            )
        }
    }
}
