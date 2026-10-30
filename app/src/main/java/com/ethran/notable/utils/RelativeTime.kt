package com.ethran.notable.utils

import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
import java.util.Date

/**
 * How old a note is, in exact calendar units: "bugün", "dün", "5 gün önce", "1 hafta 2 gün önce",
 * "1 ay 5 gün önce", "1 yıl 2 ay önce". Zero parts are left out.
 */
object RelativeTime {
    fun format(date: Date, today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): String =
        format(Instant.ofEpochMilli(date.time).atZone(zone).toLocalDate(), today)

    fun format(day: LocalDate, today: LocalDate): String {
        if (!day.isBefore(today)) return "bugün"      // clock skew never shows "-1 gün"
        val p = Period.between(day, today)
        val parts = when {
            p.years > 0 -> listOf(p.years to "yıl", p.months to "ay")
            p.months > 0 -> listOf(p.months to "ay", p.days to "gün")
            p.days >= 7 -> listOf(p.days / 7 to "hafta", p.days % 7 to "gün")
            p.days == 1 -> return "dün"
            else -> listOf(p.days to "gün")
        }
        return parts.filter { it.first > 0 }.joinToString(" ") { "${it.first} ${it.second}" } + " önce"
    }
}
