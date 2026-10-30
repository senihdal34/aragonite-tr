package com.ethran.notable.utils

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Date

/** "bugün", "dün", "3 gün önce", "1 hafta önce", "2 ay önce", "1 yıl önce": how old a note is, in calendar days. */
object RelativeTime {
    fun format(date: Date, today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): String =
        format(Instant.ofEpochMilli(date.time).atZone(zone).toLocalDate(), today)

    fun format(day: LocalDate, today: LocalDate): String {
        val days = ChronoUnit.DAYS.between(day, today)
        return when {
            days <= 0 -> "bugün"
            days == 1L -> "dün"
            days < 7 -> "$days gün önce"
            days < 28 -> "${days / 7} hafta önce"
            days < 365 -> "${maxOf(1, days / 30)} ay önce"
            else -> "${days / 365} yıl önce"
        }
    }
}
