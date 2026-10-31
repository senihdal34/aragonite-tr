package com.ethran.notable.planner

import kotlin.math.floor
import kotlin.math.max

/**
 * Geometry of the planner pages in page pixels. The pages do not scroll, so everything is computed from the
 * height of the screen: the week page always shows seven rows that exactly fill it.
 */
object PlannerLayout {
    const val HEADER_H = 150f
    /** Strip under the header for the shared HAFTA | NOTLAR | SABİT bar (drawn by the editor, not by the background). */
    const val NAV_H = 153f   // same height as the tab bar and its padding on Home, so both pages line up
    /** Home's side padding in page pixels; the header block on the right ends here. */
    const val RIGHT_MARGIN = 105f
    const val ROWS_TOP = HEADER_H + NAV_H
    const val LABEL_W = 200f
    const val BOTTOM_MARGIN = 40f
    const val MIN_LINES = 5
    private const val MIN_STEP = 64f

    data class Week(val rowH: Float, val lines: Int) {
        val step get() = rowH / lines
        fun rowTop(i: Int) = ROWS_TOP + i * rowH
    }

    /** Seven equal rows between the header and the bottom margin, at least [MIN_LINES] lines each. */
    fun week(pageHeight: Float): Week {
        val rowH = (pageHeight - ROWS_TOP - BOTTOM_MARGIN) / 7f
        return Week(rowH, max(MIN_LINES, floor(rowH / MIN_STEP).toInt()))
    }

    /** A day page uses the week's line step, so its first lines coincide with the day's row on the week page. */
    fun dayStep(pageHeight: Float): Float = week(pageHeight).step

    /** Number of to-do lines on a day page. */
    fun dayLines(pageHeight: Float): Int = max(8, floor((pageHeight - ROWS_TOP - BOTTOM_MARGIN) / dayStep(pageHeight)).toInt())

    /** Horizontal start of a check box, the same on the week and the day page. */
    const val BOX_X = LABEL_W + 28f

    fun boxSize(step: Float) = step * 0.46f
    fun boxBottom(rowTop: Float, line: Int, step: Float) = rowTop + line * step - step * 0.2f

    data class Zone(val x: Float, val y: Float, val w: Float, val h: Float)

    /** Where a tap ticks line [line] (1-based) of a row starting at [rowTop]: the check box and a thin margin, never the writing area. */
    fun boxZone(rowTop: Float, line: Int, step: Float, margin: Float = 10f): Zone {
        val size = boxSize(step)
        return Zone(BOX_X - margin, boxBottom(rowTop, line, step) - size - margin, size + 2 * margin, size + 2 * margin)
    }

    /** Vertical band of a row that holds the date number (the only part that opens the day). */
    fun numberTop(week: Week, i: Int) = week.rowTop(i) + week.rowH * 0.32f
    fun numberHeight(week: Week) = week.rowH * 0.48f

    /** The day (0..6) whose date number a tap at page [x], [y] hits; null elsewhere. */
    fun dayAt(x: Float, y: Float, pageHeight: Float): Int? {
        if (x !in 20f..(LABEL_W - 20f)) return null
        val w = week(pageHeight)
        for (i in 0..6) if (y >= numberTop(w, i) && y <= numberTop(w, i) + numberHeight(w)) return i
        return null
    }
}
