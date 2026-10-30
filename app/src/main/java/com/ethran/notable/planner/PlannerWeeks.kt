package com.ethran.notable.planner

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.IsoFields
import java.time.temporal.TemporalAdjusters

/** Dates and labels of the weekly planner. Everything comes from the device clock, no network. */
object PlannerWeeks {
    /** One handwritten page per day, opened by tapping the day on the week page: a long to-do list. */
    const val DAY_PREFIX = "planner-day:"
    /** One handwritten page per week: seven day rows with at least five to-do lines each. */
    const val WEEK_PREFIX = "planner-week:"
    /** To-do/notes page of the first version; no longer created, pages that have ink still draw. */
    const val TODO_PREFIX = "planner-todo:"
    const val NOTEBOOK_TITLE = "Planlayıcı"
    /** Weeks created in advance after the current one, so swiping to the next page stays inside the planner. */
    const val WEEKS_AHEAD = 8

    val DAY_NAMES = listOf("Pzt", "Sal", "Çar", "Per", "Cum", "Cmt", "Paz")
    val DAY_FULL = listOf("Pazartesi", "Salı", "Çarşamba", "Perşembe", "Cuma", "Cumartesi", "Pazar")
    private val MONTHS = listOf("Oca", "Şub", "Mar", "Nis", "May", "Haz", "Tem", "Ağu", "Eyl", "Eki", "Kas", "Ara")
    private val MONTHS_FULL = listOf(
        "Ocak", "Şubat", "Mart", "Nisan", "Mayıs", "Haziran", "Temmuz", "Ağustos", "Eylül", "Ekim", "Kasım", "Aralık"
    )

    fun mondayOf(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    fun weekBackground(monday: LocalDate) = WEEK_PREFIX + monday
    fun dayBackground(day: LocalDate) = DAY_PREFIX + day

    /** Mondays of the weeks that exist from the start: this week and the next [ahead] weeks. */
    fun weeksToEnsure(today: LocalDate, ahead: Int = WEEKS_AHEAD): List<LocalDate> {
        val first = mondayOf(today)
        return (0..ahead).map { first.plusWeeks(it.toLong()) }
    }

    /** Sort key that keeps the planner notebook in date order: a week page, then its seven day pages. */
    fun sortKey(background: String): Long? {
        parseWeek(background)?.let { return mondayOf(it).toEpochDay() * 10 }
        parseDay(background)?.let { return mondayOf(it).toEpochDay() * 10 + 1 + (it.dayOfWeek.value - 1) }
        parseTodo(background)?.let { return mondayOf(it).toEpochDay() * 10 + 9 }
        return null
    }


    fun parseTodo(background: String): LocalDate? = parse(background, TODO_PREFIX)
    fun parseWeek(background: String): LocalDate? = parse(background, WEEK_PREFIX)
    fun parseDay(background: String): LocalDate? = parse(background, DAY_PREFIX)
    fun isPlanner(background: String) = background.startsWith("planner-")

    private fun parse(background: String, prefix: String): LocalDate? =
        if (background.startsWith(prefix)) runCatching { LocalDate.parse(background.removePrefix(prefix)) }.getOrNull() else null

    fun weekNumber(date: LocalDate): Int = date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)

    /** 52 or 53, the number of ISO weeks in the year the week belongs to. */
    fun weeksInYear(date: LocalDate): Int = date.with(DayOfWeek.THURSDAY).range(IsoFields.WEEK_OF_WEEK_BASED_YEAR).maximum.toInt()

    /** "44/52" */
    fun weekOfYear(monday: LocalDate) = "${weekNumber(monday)}/${weeksInYear(monday)}"

    /** Share of the calendar year that is over at the end of [date], 0..100. */
    fun yearPercent(date: LocalDate): Int = Math.round(date.dayOfYear * 100f / date.lengthOfYear())

    fun shortDate(d: LocalDate) = "${d.dayOfMonth} ${MONTHS[d.monthValue - 1]}"
    fun monthName(d: LocalDate) = MONTHS_FULL[d.monthValue - 1]

    /** "26 Eki – 1 Kas" */
    fun rangeLabel(monday: LocalDate) = "${shortDate(monday)} – ${shortDate(monday.plusDays(6))}"

    /** "26 Eki – 1 Kas · 44/52" */
    fun weekLabel(monday: LocalDate) = "${rangeLabel(monday)} · ${weekOfYear(monday)}"
}
