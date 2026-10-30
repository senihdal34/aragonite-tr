package com.ethran.notable.planner

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.IsoFields
import java.time.temporal.TemporalAdjusters

/** Dates and labels of the weekly planner. Everything comes from the device clock, no network. */
object PlannerWeeks {
    const val WEEK_PREFIX = "planner-week:"
    const val TODO_PREFIX = "planner-todo:"
    const val NOTEBOOK_TITLE = "Planlayıcı"
    const val WEEKS_AHEAD = 8

    val DAY_NAMES = listOf("Pzt", "Sal", "Çar", "Per", "Cum", "Cmt", "Paz")
    private val MONTHS = listOf("Oca", "Şub", "Mar", "Nis", "May", "Haz", "Tem", "Ağu", "Eyl", "Eki", "Kas", "Ara")

    fun mondayOf(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /** Mondays of the weeks that must exist: this week and the next [ahead] weeks. */
    fun weeksToEnsure(today: LocalDate, ahead: Int = WEEKS_AHEAD): List<LocalDate> {
        val first = mondayOf(today)
        return (0..ahead).map { first.plusWeeks(it.toLong()) }
    }

    fun weekBackground(monday: LocalDate) = WEEK_PREFIX + monday
    fun todoBackground(monday: LocalDate) = TODO_PREFIX + monday

    fun parseWeek(background: String): LocalDate? = parse(background, WEEK_PREFIX)
    fun parseTodo(background: String): LocalDate? = parse(background, TODO_PREFIX)
    fun isPlanner(background: String) = background.startsWith("planner-")

    private fun parse(background: String, prefix: String): LocalDate? =
        if (background.startsWith(prefix)) runCatching { LocalDate.parse(background.removePrefix(prefix)) }.getOrNull() else null

    fun weekNumber(date: LocalDate): Int = date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)

    fun shortDate(d: LocalDate) = "${d.dayOfMonth} ${MONTHS[d.monthValue - 1]}"

    /** "26 Eki – 1 Kas · 44. hafta" */
    fun weekLabel(monday: LocalDate) =
        "${shortDate(monday)} – ${shortDate(monday.plusDays(6))} · ${weekNumber(monday)}. hafta"
}
