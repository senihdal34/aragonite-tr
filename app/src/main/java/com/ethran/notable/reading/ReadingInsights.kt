package com.ethran.notable.reading

import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.ceil

/** One heatmap cell is one day: its minutes, or null when the day has not come yet. */
data class HeatGrid(
    /** Columns of the grid, oldest first. Each column is a Sunday-first week of 7 days. */
    val weeks: List<List<Int?>>,
    /** Month name above the first week of each new month, null elsewhere (same length as [weeks]). */
    val monthLabels: List<String?>,
    val totalMinutes: Int,
    val daysRead: Int,
    val bestDayMinutes: Int
)

data class Forecast(val book: RBook, val minutesLeft: Int, val perDay: Int, val daysOut: Int, val date: LocalDate)

data class HoursRead(val yearMinutes: Int, val monthMinutes: Int, val year: Int, val month: String)

data class Records(
    val longestStreak: Int,
    val bestDay: LocalDate?,
    val bestDayMinutes: Int,
    /** (year, books finished), newest year first. */
    val booksPerYear: List<Pair<Int, Int>>
)

data class YearAgo(val date: LocalDate, val book: RBook, val minutes: Int)

/** The numbers behind the Insights page. Pure: no Android calls, so it is unit tested. */
object ReadingInsights {
    const val HEAT_WEEKS = 16
    /** Recent reading pace is the average over this many days. */
    const val PACE_DAYS = 14
    /** A finish date further out than this is not a forecast. */
    const val MAX_DAYS_OUT = 365
    /** How far around "a year ago today" to look for a book. */
    const val YEAR_AGO_SPREAD = 3

    /** The reading day of the data: `now` is 0 until the first load, then today's date is used. */
    fun today(data: ReadingData, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
        ReadingStats.dayOf(if (data.now > 0L) data.now else System.currentTimeMillis(), zone)

    /** 0 = nothing, then four steps: under 30, under 60, under 120 and 120 or more minutes. */
    fun heatLevel(minutes: Int?): Int = when {
        minutes == null || minutes <= 0 -> 0
        minutes < 30 -> 1
        minutes < 60 -> 2
        minutes < 120 -> 3
        else -> 4
    }

    fun heatmap(minutesByDay: Map<Long, Int>, today: LocalDate, weeks: Int = HEAT_WEEKS): HeatGrid {
        val sunday = today.minusDays((today.dayOfWeek.value % 7).toLong())
        val first = sunday.minusWeeks((weeks - 1).toLong())
        var total = 0
        var read = 0
        var best = 0
        var prevMonth = -1
        val columns = ArrayList<List<Int?>>()
        val labels = ArrayList<String?>()
        for (w in 0 until weeks) {
            val start = first.plusWeeks(w.toLong())
            columns += (0..6).map { i ->
                val d = start.plusDays(i.toLong())
                if (d > today) null else (minutesByDay[d.toEpochDay()] ?: 0).also { m ->
                    total += m
                    if (m > 0) read++
                    if (m > best) best = m
                }
            }
            // The month of the week's Sunday names the column; a label only where it changes.
            labels += if (start.monthValue != prevMonth) start.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH) else null
            prevMonth = start.monthValue
        }
        // Two labels side by side would collide: the later one wins.
        if (labels.size > 1 && labels[1] != null) labels[0] = null
        return HeatGrid(columns, labels, total, read, best)
    }

    /** Average minutes per day over the last [days] days, today included. */
    fun recentPace(minutesByDay: Map<Long, Int>, today: LocalDate, days: Int = PACE_DAYS): Double =
        (0 until days).sumOf { minutesByDay[today.minusDays(it.toLong()).toEpochDay()] ?: 0 }.toDouble() / days

    /** When the first book in progress that has an estimate would be finished at the recent pace; null if it cannot be said. */
    fun forecast(continueReading: List<RBook>, minutesByDay: Map<Long, Int>, today: LocalDate): Forecast? {
        val book = continueReading.firstOrNull { it.minutesLeft != null } ?: return null
        val left = book.minutesLeft ?: return null
        val pace = recentPace(minutesByDay, today)
        if (pace <= 0.0) return null
        val daysOut = ceil(left / pace).toInt().coerceAtLeast(1)
        if (daysOut > MAX_DAYS_OUT) return null
        return Forecast(book, left, Math.round(pace).toInt().coerceAtLeast(1), daysOut, today.plusDays(daysOut.toLong()))
    }

    /** "Tomorrow" or "Saturday, 7 Nov". */
    fun finishLabel(f: Forecast): String =
        if (f.daysOut <= 1) "Tomorrow"
        else "${f.date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)}, ${f.date.dayOfMonth} ${f.date.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)}"

    fun hours(minutesByDay: Map<Long, Int>, today: LocalDate): HoursRead {
        var year = 0
        var month = 0
        for ((day, m) in minutesByDay) {
            val d = LocalDate.ofEpochDay(day)
            if (d > today || d.year != today.year) continue
            year += m
            if (d.monthValue == today.monthValue) month += m
        }
        return HoursRead(year, month, today.year, today.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH))
    }

    /** The longest run of consecutive days with some reading in [minutesByDay]. */
    fun longestStreak(minutesByDay: Map<Long, Int>): Int {
        var best = 0
        var run = 0
        var prev = Long.MIN_VALUE
        for (day in minutesByDay.filterValues { it > 0 }.keys.sorted()) {
            run = if (day == prev + 1) run + 1 else 1
            prev = day
            if (run > best) best = run
        }
        return best
    }

    fun records(minutesByDay: Map<Long, Int>, books: List<RBook>, zone: ZoneId = ZoneId.systemDefault()): Records {
        val best = minutesByDay.filterValues { it > 0 }.maxByOrNull { it.value }
        val perYear = books.mapNotNull { it.finishedAt }.groupingBy { ReadingStats.dayOf(it, zone).year }.eachCount()
            .toList().sortedByDescending { it.first }
        return Records(longestStreak(minutesByDay), best?.let { LocalDate.ofEpochDay(it.key) }, best?.value ?: 0, perYear)
    }

    /** The book read on this day a year ago (or on a day within [YEAR_AGO_SPREAD] of it, nearest first); null if there is none. */
    fun yearAgo(data: ReadingData, today: LocalDate): YearAgo? {
        val target = today.minusYears(1)
        val byId = data.books.associateBy { it.id }
        val offsets = listOf(0L) + (1..YEAR_AGO_SPREAD).flatMap { listOf(-it.toLong(), it.toLong()) }
        for (o in offsets) {
            val d = target.plusDays(o)
            if (d >= today) continue
            val book = data.booksByDay[d.toEpochDay()]?.firstNotNullOfOrNull { byId[it] } ?: continue
            return YearAgo(d, book, data.minutesByDay[d.toEpochDay()] ?: 0)
        }
        return null
    }

    /** True when some book was finished on the reading day of [now]. */
    fun finishedToday(books: List<RBook>, now: Long, zone: ZoneId = ZoneId.systemDefault()): Boolean {
        val today = ReadingStats.dayOf(now, zone)
        return books.any { b -> b.finishedAt?.let { ReadingStats.dayOf(it, zone) == today } == true }
    }

    fun minutesOn(data: ReadingData, day: LocalDate): Int = data.minutesByDay[day.toEpochDay()] ?: 0

    /** "6 May 2026". */
    fun shortDate(d: LocalDate): String = "${d.dayOfMonth} ${d.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)} ${d.year}"

    /** Day name shown in the year-ago box: "Sat, 31 Oct 2025". */
    fun dateLabel(d: LocalDate): String =
        "${d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)}, ${d.dayOfMonth} ${d.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)} ${d.year}"
}
