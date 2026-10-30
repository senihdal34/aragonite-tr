package com.ethran.notable.utils

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class RelativeTimeTest {
    private val today = LocalDate.of(2026, 10, 30)
    private fun ago(days: Long) = RelativeTime.format(today.minusDays(days), today)

    @Test fun today_and_yesterday() {
        assertEquals("bugün", ago(0))
        assertEquals("dün", ago(1))
        assertEquals("bugün", RelativeTime.format(today.plusDays(1), today))   // clock skew never shows "-1 gün"
    }

    @Test fun days_weeks_months_years() {
        assertEquals("2 gün önce", ago(2))
        assertEquals("6 gün önce", ago(6))
        assertEquals("1 hafta önce", ago(7))
        assertEquals("2 hafta önce", ago(14))
        assertEquals("3 hafta önce", ago(27))
        assertEquals("1 ay önce", ago(28))
        assertEquals("1 ay önce", ago(35))
        assertEquals("6 ay önce", ago(190))
        assertEquals("1 yıl önce", ago(365))
        assertEquals("2 yıl önce", ago(800))
    }
}
