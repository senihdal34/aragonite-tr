package com.ethran.notable.reading.board

import com.ethran.notable.reading.RBook
import com.ethran.notable.reading.ReadingData
import com.ethran.notable.reading.ReadingInsights
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.floor

/** One month of the tree rings: its calendar month (1..12), year and minutes read. */
data class MonthMinutes(val month: Int, val year: Int, val minutes: Int)

/**
 * What the board's boxes show, derived from [ReadingData] once per load: the plugin's d.* table (lb_board_data.lua).
 * Pure, so it is unit tested.
 */
class BoardData(val data: ReadingData, val today: LocalDate, zone: ZoneId = ZoneId.systemDefault()) {
    private val minutesByDay = data.minutesByDay
    private fun minutesOn(d: LocalDate) = minutesByDay[d.toEpochDay()] ?: 0

    /** Minutes read on each of the last 7 days, today last (the Week box). */
    val week: List<Int> = (6 downTo 0).map { minutesOn(today.minusDays(it.toLong())) }

    /** First letter of each of those days (S M T W T F S ending on today's). */
    val weekInitials: List<String> = (6 downTo 0).map { today.minusDays(it.toLong()).dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).take(1) }
    val weekMinutes: Int = week.sum()

    /** Whether something was read on each of the last 7 days, today last (the Streak box). */
    val streakWeek: List<Boolean> = week.map { it > 0 }
    val streak: Int = data.streakDays
    val longestStreak: Int = maxOf(ReadingInsights.longestStreak(minutesByDay), data.streakDays)
    val todayMinutes: Int = minutesOn(today)

    val finishedYear: Int = data.finishedThisYear.size
    val dayOfYear: Int = today.dayOfYear
    val daysInYear: Int = today.lengthOfYear()

    /** The year of reading: 52 weeks, Sunday first, ending with this week. Null for days that have not come. */
    val heat: List<Int?>
    val heatStart: LocalDate
    val heatToday: Int

    init {
        val sunday = today.minusDays((today.dayOfWeek.value % 7).toLong())
        heatStart = sunday.minusWeeks(51)
        heat = (0 until 52 * 7).map { i -> val d = heatStart.plusDays(i.toLong()); if (d > today) null else minutesOn(d) }
        heatToday = heat.indexOfLast { it != null }
    }

    /** Minutes in each of the last twelve calendar months, oldest first. */
    val months: List<MonthMinutes> = run {
        val sums = HashMap<Int, Int>()
        for ((day, m) in minutesByDay) {
            if (m <= 0) continue
            val d = LocalDate.ofEpochDay(day)
            if (d > today) continue
            sums.merge(d.year * 12 + d.monthValue - 1, m, Int::plus)
        }
        val last = today.year * 12 + today.monthValue - 1
        (last - 11..last).map { key -> MonthMinutes(key % 12 + 1, key / 12, sums[key] ?: 0) }
    }

    val forecast = ReadingInsights.forecast(data.continueReading, minutesByDay, today)
    val yearAgo = ReadingInsights.yearAgo(data, today)

    /** Books started and not finished, most recently read first. */
    val pickBackUp: List<RBook> = data.continueReading

    /** Unread books of a series with a book already started or finished. */
    val upNext: List<RBook> = run {
        val touched = data.books.filter { it.series != null && (it.finishedAt != null || it.progress > 0.0) }.mapNotNull { it.series }.toSet()
        data.books.filter { it.series in touched && it.series != null && it.progress == 0.0 && it.finishedAt == null }.sortedBy { it.title.lowercase() }
    }

    /** Books not started yet, the longest waiting first. */
    val wantToRead: List<RBook> = data.books.filter { it.progress == 0.0 && it.finishedAt == null }.sortedBy { it.addedAt }

    val finished: List<RBook> = data.finishedThisYear

    /** The folders books sit in, for the Collections link. */
    val folders: List<String> = data.books.map { it.path.substringBeforeLast('/', "") }.distinct()

    fun shelf(kind: String): List<RBook> = when (kind) {
        "random" -> data.discover
        "recently-added" -> data.recentlyAdded
        "pick-back-up" -> pickBackUp
        "up-next-in-series" -> upNext
        "want-to-read" -> wantToRead
        "finished" -> finished
        "in-progress" -> data.continueReading
        else -> emptyList()
    }

    companion object {
        /** "1 min", "45 min", "1 h 20 min", "2 h" (BoardData.formatDuration). */
        fun duration(min: Int): String {
            val m = min
            if (m < 60) return "$m min"
            val h = m / 60
            val r = m % 60
            return if (r == 0) "$h h" else "$h h $r min"
        }

        /** "45m", "1h 20m", "2h" (BoardData.formatShortDuration). */
        fun shortDuration(min: Int): String {
            if (min < 60) return "${min}m"
            val h = min / 60
            val r = min % 60
            return if (r == 0) "${h}h" else "${h}h ${r}m"
        }

        /** The percentage a book shows: a started book never reads 0%, an unfinished one never 100% (BoardData.pctLabel). */
        fun pctLabel(p: Double): String = when {
            p > 0 && p < 0.5 -> "<1%"
            p < 100 && p >= 99.5 -> "99%"
            else -> "${floor(p + 0.5).toInt()}%"
        }

        /** 1,234 style grouping. */
        fun count(n: Int): String = "%,d".format(Locale.ENGLISH, n)

        fun fillSize(room: Int, dp: Double, lo: Int, hi: Int): Int = floor(room / 1.36 / dp).toInt().coerceIn(lo, hi)

        /** "Early/Mid/Late September 2025" for a date (BoardData.partOfMonth). */
        fun partOfMonth(d: LocalDate): String {
            val part = if (d.dayOfMonth <= 10) "Early" else if (d.dayOfMonth <= 20) "Mid" else "Late"
            return "$part ${d.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)} ${d.year}"
        }
    }
}
