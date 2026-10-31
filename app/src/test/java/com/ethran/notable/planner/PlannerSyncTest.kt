package com.ethran.notable.planner

import com.ethran.notable.data.db.Stroke
import com.ethran.notable.data.db.StrokePoint
import com.ethran.notable.editor.utils.Pen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlannerSyncTest {
    private fun stroke(id: String, y: Float, page: String = "week") = Stroke(
        id = id, size = 3f, pen = Pen.BALLPEN, top = y - 5f, bottom = y + 5f, left = 300f, right = 400f,
        points = listOf(StrokePoint(300f, y - 5f), StrokePoint(400f, y + 5f)), pageId = page
    )

    @Test fun `only strokes centred in the region are taken`() {
        val all = listOf(stroke("a", 100f), stroke("b", 500f), stroke("c", 900f))
        assertEquals(listOf("b"), PlannerSync.inRegion(all, 400f, 600f).map { it.id })
        assertTrue(PlannerSync.inRegion(all, 1000f, 1200f).isEmpty())
    }

    @Test fun `copies move vertically and keep the page of the target`() {
        val moved = PlannerSync.copies(listOf(stroke("b", 500f)), -300f, "day")
        assertEquals(1, moved.size)
        assertEquals("day", moved[0].pageId)
        assertEquals(195f, moved[0].top, 0.01f)
        assertEquals(205f, moved[0].bottom, 0.01f)
        assertEquals(200f, moved[0].points[0].y + 5f, 0.01f)
        assertEquals(300f, moved[0].left, 0.01f)          // no horizontal move
    }

    @Test fun `copy ids are stable per source and target`() {
        val s = stroke("b", 500f)
        val first = PlannerSync.copies(listOf(s), 0f, "day")[0].id
        assertEquals(first, PlannerSync.copies(listOf(s), 0f, "day")[0].id)
        assertNotEquals(first, PlannerSync.copies(listOf(s), 0f, "other")[0].id)
        assertNotEquals(s.id, first)
    }

    @Test fun `day page shares the week step and rows start under the tab bar`() {
        assertEquals(PlannerLayout.week(2480f).step, PlannerLayout.dayStep(2480f), 0.001f)
        assertTrue(PlannerLayout.dayLines(2480f) > PlannerLayout.week(2480f).lines)
    }
}
