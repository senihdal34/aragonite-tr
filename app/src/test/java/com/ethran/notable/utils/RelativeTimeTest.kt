package com.ethran.notable.utils

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class RelativeTimeTest {
    private val today = LocalDate.of(2026, 10, 30)
    private fun at(y: Int, m: Int, d: Int) = RelativeTime.format(LocalDate.of(y, m, d), today)

    @Test fun today_and_yesterday() {
        assertEquals("bugün", at(2026, 10, 30))
        assertEquals("dün", at(2026, 10, 29))
        assertEquals("bugün", at(2026, 10, 31))   // clock skew
    }

    @Test fun days_and_weeks() {
        assertEquals("2 gün önce", at(2026, 10, 28))
        assertEquals("6 gün önce", at(2026, 10, 24))
        assertEquals("1 hafta önce", at(2026, 10, 23))
        assertEquals("1 hafta 2 gün önce", at(2026, 10, 21))
        assertEquals("3 hafta 6 gün önce", at(2026, 10, 3))
    }

    @Test fun months_with_exact_days() {
        assertEquals("1 ay önce", at(2026, 9, 30))
        assertEquals("1 ay 5 gün önce", at(2026, 9, 25))
        assertEquals("1 ay 16 gün önce", at(2026, 9, 14))
        assertEquals("6 ay 2 gün önce", at(2026, 4, 28))
    }

    @Test fun years() {
        assertEquals("1 yıl önce", at(2025, 10, 30))
        assertEquals("1 yıl 2 ay önce", at(2025, 8, 30))
        assertEquals("2 yıl 3 ay önce", at(2024, 7, 15))
    }
}
