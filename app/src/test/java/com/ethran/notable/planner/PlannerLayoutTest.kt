package com.ethran.notable.planner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlannerLayoutTest {
    @Test fun `seven rows fill a portrait screen with at least five lines`() {
        val w = PlannerLayout.week(2480f)
        assertTrue(w.lines >= 5)
        assertEquals(2480f - PlannerLayout.BOTTOM_MARGIN, w.rowTop(7), 0.5f)
        assertEquals(PlannerLayout.HEADER_H + PlannerLayout.NAV_H, w.rowTop(0), 0.01f)   // rows start under the tab bar
    }

    @Test fun `landscape keeps five lines`() {
        assertEquals(5, PlannerLayout.week(1860f).lines)
    }

    @Test fun `only the date number opens a day`() {
        val h = 2480f
        val w = PlannerLayout.week(h)
        val mid = PlannerLayout.numberTop(w, 3) + PlannerLayout.numberHeight(w) / 2
        assertEquals(3, PlannerLayout.dayAt(100f, mid, h))
        assertNull(PlannerLayout.dayAt(100f, w.rowTop(3) + 10f, h))   // row label above the number
        assertNull(PlannerLayout.dayAt(400f, mid, h))                // writing area
        assertNull(PlannerLayout.dayAt(100f, 40f, h))                // header
    }

    @Test fun `day page has plenty of lines`() {
        assertTrue(PlannerLayout.dayLines(2480f) >= 20)
        assertTrue(PlannerLayout.dayLines(1860f) >= 8)
    }

    @Test fun `tick zone is the check box with a thin margin, not the writing line`() {
        val w = PlannerLayout.week(2480f)
        val zone = PlannerLayout.boxZone(w.rowTop(2), 3, w.step)
        val size = PlannerLayout.boxSize(w.step)
        assertTrue(zone.x <= PlannerLayout.BOX_X && zone.x + zone.w >= PlannerLayout.BOX_X + size)
        assertTrue("handwriting must not tick", zone.x + zone.w < PlannerLayout.BOX_X + size + 20f)
        val bottom = PlannerLayout.boxBottom(w.rowTop(2), 3, w.step)
        assertTrue(zone.y <= bottom - size && zone.y + zone.h >= bottom)
    }
}
