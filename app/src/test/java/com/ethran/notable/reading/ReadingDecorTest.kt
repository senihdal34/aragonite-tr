package com.ethran.notable.reading

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingDecorTest {
    private fun pose(
        reading: Boolean = true, streak: Int = 3, best: Int = 10, today: Int = 20, finished: Boolean = false, hour: Int = 14
    ) = ReadingDecor.buddyPose(reading, streak, best, today, finished, hour)

    @Test fun buddyWaitsWhenNothingIsStarted() {
        assertEquals(BuddyPose.Wait, pose(reading = false, streak = 0, today = 0))
    }

    @Test fun buddySleepsWhenNothingWasReadToday() {
        assertEquals(BuddyPose.Sleep, pose(today = 0))
        assertEquals(BuddyPose.Sleep, pose(reading = false, streak = 2, today = 0))
    }

    @Test fun finishingABookBeatsEverythingElseThatDay() {
        assertEquals(BuddyPose.Ribbon, pose(finished = true, streak = 7))
    }

    @Test fun buddyPartiesOnMilestonesAndNewBests() {
        assertEquals(BuddyPose.Party, pose(streak = 7, best = 30))
        assertEquals(BuddyPose.Party, pose(streak = 12, best = 12))
        assertEquals(BuddyPose.Read, pose(streak = 12, best = 30))
        assertEquals(BuddyPose.Read, pose(streak = 1, best = 1))
    }

    @Test fun buddyYawnsLateAtNight() {
        assertEquals(BuddyPose.Yawn, pose(hour = 23))
        assertEquals(BuddyPose.Yawn, pose(hour = 3))
        assertEquals(BuddyPose.Read, pose(hour = 5))
        assertEquals(BuddyPose.Read, pose(hour = 21))
    }

    @Test fun buddyLinesMatchThePose() {
        assertEquals("Reading with you" to "1h 5m today", ReadingDecor.buddyLines(BuddyPose.Read, 3, 10, 65))
        assertEquals("7 days in a row!" to "Your best is 30", ReadingDecor.buddyLines(BuddyPose.Party, 7, 30, 10))
        assertEquals("1 day in a row!" to "A new best", ReadingDecor.buddyLines(BuddyPose.Party, 1, 1, 10))
        assertEquals("Fast asleep", ReadingDecor.buddyLines(BuddyPose.Sleep, 0, 0, 0).first)
    }

    @Test fun everyPoseHasARectangularSprite() {
        BuddyPose.values().forEach { p ->
            val s = ReadingDecor.buddySprite(p)
            assertEquals(ReadingDecor.SPRITE_ROWS, s.size)
            assertTrue(s.all { it.length == ReadingDecor.SPRITE_COLS })
            assertTrue(s.all { row -> row.all { it in "#w." } })
            assertTrue(s.any { 'w' in it })           // the eyes show
        }
    }

    @Test fun posesLookDifferent() {
        val all = BuddyPose.values().map { ReadingDecor.buddySprite(it) }
        assertEquals(all.size, all.distinct().size)
        // Only the sleeper has a "z" above its head; only the partygoer has a hat.
        assertTrue(ReadingDecor.buddySprite(BuddyPose.Sleep)[0].contains('#'))
        assertTrue(ReadingDecor.buddySprite(BuddyPose.Read)[0].none { it == '#' })
        assertTrue(ReadingDecor.buddySprite(BuddyPose.Party)[2].contains('#'))
    }

    @Test fun moonIsNewOnAKnownNewMoon() {
        // 2000-01-06 18:14 UTC and 2024-04-08 18:21 UTC.
        assertEquals("New moon", ReadingDecor.moon(LocalDate.of(2000, 1, 6)).name)
        assertEquals("New moon", ReadingDecor.moon(LocalDate.of(2024, 4, 8)).name)
        assertTrue(ReadingDecor.moon(LocalDate.of(2024, 4, 8)).litPercent <= 2)
    }

    @Test fun moonIsFullAboutFifteenDaysLater() {
        val m = ReadingDecor.moon(LocalDate.of(2000, 1, 21)) // full moon 2000-01-21 04:40 UTC
        assertEquals("Full moon", m.name)
        assertEquals(100, m.litPercent)
        assertTrue(ReadingDecor.terminator(m) > 0.99)
    }

    @Test fun moonQuarterIsHalfLitAndWaxingLitsTheRight() {
        val m = ReadingDecor.moon(LocalDate.of(2000, 1, 14))
        assertEquals("First quarter", m.name)
        assertTrue(m.litPercent in 45..65)
        assertTrue(ReadingDecor.litOnRight(m))
        assertTrue(ReadingDecor.terminator(m) < 0.3)
        assertFalse(ReadingDecor.litOnRight(ReadingDecor.moon(LocalDate.of(2000, 1, 28))))
    }

    @Test fun moonNamesNextFullOrNewMoon() {
        val waxing = ReadingDecor.moon(LocalDate.of(2000, 1, 14))
        assertTrue(waxing.next.startsWith("Full moon in "))
        assertTrue(waxing.daysToNext in 6..9)
        val waning = ReadingDecor.moon(LocalDate.of(2000, 1, 28))
        assertTrue(waning.next.startsWith("New moon in "))
    }

    @Test fun everyDayOfAMonthHasAValidPhase() {
        var d = LocalDate.of(2026, 1, 1)
        repeat(400) {
            val m = ReadingDecor.moon(d)
            assertTrue(m.phase in 0.0..1.0)
            assertTrue(m.litPercent in 0..100)
            d = d.plusDays(1)
        }
    }

    @Test fun clockHandsPointTheRightWay() {
        val three = ReadingDecor.clockAngles(3, 0)
        assertEquals(90f, three.hour, 0.001f)
        assertEquals(0f, three.minute, 0.001f)
        val half = ReadingDecor.clockAngles(6, 30)
        assertEquals(195f, half.hour, 0.001f)  // between 6 and 7
        assertEquals(180f, half.minute, 0.001f)
        assertEquals(0f, ReadingDecor.clockAngles(12, 0).hour, 0.001f)
        assertEquals(30f, ReadingDecor.clockAngles(13, 0).hour, 0.001f)
    }
}
