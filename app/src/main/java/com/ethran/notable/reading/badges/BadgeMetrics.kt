package com.ethran.notable.reading.badges

import com.ethran.notable.reading.RBook
import com.ethran.notable.reading.ReadingStats
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * The counting behind the badges. Every function is pure (plain numbers in, a number or flag out), so each rule is unit
 * tested on its own. Days are epoch days, the way [com.ethran.notable.reading.ReadingData.minutesByDay] keys them.
 */
object BadgeMetrics {
    /** The longest run of consecutive days in [days]. */
    fun longestRun(days: Collection<Long>): Int {
        var best = 0
        var run = 0
        var prev = Long.MIN_VALUE
        for (d in days.toSortedSet()) {
            run = if (d == prev + 1) run + 1 else 1
            prev = d
            if (run > best) best = run
        }
        return best
    }

    /** The first day on which a run of consecutive days reaches [length], or null when none does. */
    fun runReachedOn(days: Collection<Long>, length: Int): Long? {
        var run = 0
        var prev = Long.MIN_VALUE
        for (d in days.toSortedSet()) {
            run = if (d == prev + 1) run + 1 else 1
            prev = d
            if (run >= length) return d
        }
        return null
    }

    /** The most days without reading between two reading days (0 when there is no gap or fewer than two days). */
    fun longestBreakDays(days: Collection<Long>): Int {
        val sorted = days.toSortedSet().toList()
        var best = 0
        for (i in 1 until sorted.size) best = maxOf(best, (sorted[i] - sorted[i - 1] - 1).toInt())
        return best
    }

    /** The most calendar months with reading inside one year. */
    fun maxMonthsInYear(days: Collection<Long>): Int =
        days.map { LocalDate.ofEpochDay(it) }.groupBy { it.year }.maxOfOrNull { (_, ds) -> ds.map { it.monthValue }.toSet().size } ?: 0

    /** Meteorological seasons as BookOrbit counts them: Dec to Feb, Mar to May, Jun to Aug, Sep to Nov. */
    fun season(month: Int): Int = when (month) { 12, 1, 2 -> 1; 3, 4, 5 -> 2; 6, 7, 8 -> 3; else -> 4 }

    /** The most seasons with reading inside one year. */
    fun maxSeasonsInYear(days: Collection<Long>): Int =
        days.map { LocalDate.ofEpochDay(it) }.groupBy { it.year }.maxOfOrNull { (_, ds) -> ds.map { season(it.monthValue) }.toSet().size } ?: 0

    /** Whether some calendar month has reading on every one of its days. */
    fun hasPerfectMonth(days: Collection<Long>): Boolean =
        days.map { LocalDate.ofEpochDay(it) }.groupBy { YearMonth.from(it) }.any { (month, ds) -> ds.map { it.dayOfMonth }.toSet().size == month.lengthOfMonth() }

    /** The index of the Monday-first week a day sits in, counting from the epoch: consecutive weeks have consecutive indexes. */
    fun weekIndex(epochDay: Long): Long = Math.floorDiv(epochDay + 3, 7L)

    private fun isWeekend(epochDay: Long): Boolean = LocalDate.ofEpochDay(epochDay).dayOfWeek.let { it == DayOfWeek.SATURDAY || it == DayOfWeek.SUNDAY }

    /** The longest run of consecutive weeks in which a Saturday or a Sunday was read. */
    fun consecutiveWeekends(days: Collection<Long>): Int = longestRun(days.filter(::isWeekend).map(::weekIndex))

    /** The most minutes read over one Saturday and Sunday together. */
    fun maxWeekendMinutes(minutesByDay: Map<Long, Int>): Int =
        minutesByDay.filter { isWeekend(it.key) && it.value > 0 }.entries.groupBy { weekIndex(it.key) }.maxOfOrNull { (_, es) -> es.sumOf { it.value } } ?: 0

    /** The longest run of days with at least [minMinutes] minutes read. */
    fun consistentRun(minutesByDay: Map<Long, Int>, minMinutes: Int = 30): Int =
        longestRun(minutesByDay.filterValues { it >= minMinutes }.keys)

    /** Whether any January 1st has reading. */
    fun readOnNewYearsDay(days: Collection<Long>): Boolean = days.any { LocalDate.ofEpochDay(it).let { d -> d.monthValue == 1 && d.dayOfMonth == 1 } }

    /** The most books finished inside any window of [spanDays] consecutive days (a week is 7). */
    fun maxFinishedInWindow(finishDays: List<Long>, spanDays: Int = 7): Int {
        val sorted = finishDays.sorted()
        var best = 0
        var lo = 0
        for (hi in sorted.indices) {
            while (sorted[hi] - sorted[lo] >= spanDays) lo++
            best = maxOf(best, hi - lo + 1)
        }
        return best
    }

    /** The most books finished in one calendar month. */
    fun maxFinishedInMonth(finishDays: List<Long>): Int =
        finishDays.map { YearMonth.from(LocalDate.ofEpochDay(it)) }.groupingBy { it }.eachCount().values.maxOrNull() ?: 0

    /** The most books finished in one calendar year. */
    fun maxFinishedInYear(finishDays: List<Long>): Int =
        finishDays.map { LocalDate.ofEpochDay(it).year }.groupingBy { it }.eachCount().values.maxOrNull() ?: 0

    /** The most finished books by one author (the first author of a book counts). */
    fun maxBooksPerAuthor(finished: List<RBook>): Int =
        finished.mapNotNull { it.author.trim().lowercase().takeIf { a -> a.isNotEmpty() } }.groupingBy { it }.eachCount().values.maxOrNull() ?: 0

    /** Finished books of fewer than [pages] pages (a book with no page count is not counted). */
    fun countShort(finished: List<RBook>, pages: Int = 100): Int = finished.count { it.pages in 1 until pages }

    /** One finished book under 100 pages and one over 800. */
    fun thickAndThin(finished: List<RBook>): Boolean = finished.any { it.pages in 1 until 100 } && finished.any { it.pages > 800 }

    /** A series of at least three books, all finished. */
    fun completeSeries(books: List<RBook>, minBooks: Int = 3): Boolean =
        books.filter { it.series != null }.groupBy { it.series }.any { (_, bs) -> bs.size >= minBooks && bs.all { it.finishedAt != null } }

    /** How many different file formats (extensions) the books come in. */
    fun formatCount(books: List<RBook>): Int =
        books.mapNotNull { it.path.substringAfterLast('.', "").lowercase().takeIf { e -> e.isNotEmpty() && e.length <= 5 } }.toSet().size

    /** How many different folders the books sit in (the Collections of the board). */
    fun folderCount(books: List<RBook>): Int = books.map { it.path.substringBeforeLast('/', "") }.toSet().size

    /** The most highlights made on one day (days by the local clock of [zone]). */
    fun maxHighlightsInDay(times: List<Long>, zone: ZoneId): Int =
        times.groupingBy { ReadingStats.dayOf(it, zone) }.eachCount().values.maxOrNull() ?: 0

    /** The first day a book was read, per book id, from the board's per-day book lists. */
    fun firstReadDay(booksByDay: Map<Long, List<String>>): Map<String, Long> {
        val first = HashMap<String, Long>()
        for ((day, ids) in booksByDay) for (id in ids) first[id] = minOf(first[id] ?: Long.MAX_VALUE, day)
        return first
    }

    /** Whether a finished book was read on one day only, which is the day it was finished. */
    fun hasSprint(finished: List<RBook>, booksByDay: Map<Long, List<String>>, zone: ZoneId): Boolean {
        val daysOf = HashMap<String, MutableSet<Long>>()
        for ((day, ids) in booksByDay) for (id in ids) daysOf.getOrPut(id) { HashSet() }.add(day)
        return finished.any { b ->
            val days = daysOf[b.id] ?: return@any false
            days.size == 1 && days.first() == ReadingStats.dayOf(b.finishedAt ?: return@any false, zone).toEpochDay()
        }
    }

    /** Whether a finished book took more than [days] days from the first day it was read to the day it was finished. */
    fun hasSlowBurn(finished: List<RBook>, booksByDay: Map<Long, List<String>>, zone: ZoneId, days: Int = 90): Boolean {
        val first = firstReadDay(booksByDay)
        return finished.any { b ->
            val start = first[b.id] ?: return@any false
            val end = ReadingStats.dayOf(b.finishedAt ?: return@any false, zone).toEpochDay()
            end - start > days
        }
    }

    /** Minutes read in the local hours [from] until [to] (24 hours of the day, as in ReadingData.hours). */
    fun minutesInHours(hours: List<Int>, from: Int, to: Int): Int = (from until to).sumOf { hours.getOrElse(it) { 0 } }
}
