package com.ethran.notable.planner

import kotlin.math.floor
import kotlin.math.max

/**
 * Geometry of the planner pages in page pixels. The pages do not scroll, so everything is computed from the
 * height of the screen: the week page always shows seven rows that exactly fill it.
 */
object PlannerLayout {
    const val HEADER_H = 150f
    const val LABEL_W = 200f
    const val BOTTOM_MARGIN = 40f
    const val MIN_LINES = 5
    private const val MIN_STEP = 64f
    const val DAY_STEP = 80f

    data class Week(val rowH: Float, val lines: Int) {
        val step get() = rowH / lines
        fun rowTop(i: Int) = HEADER_H + i * rowH
    }

    /** Seven equal rows between the header and the bottom margin, at least [MIN_LINES] lines each. */
    fun week(pageHeight: Float): Week {
        val rowH = (pageHeight - HEADER_H - BOTTOM_MARGIN) / 7f
        return Week(rowH, max(MIN_LINES, floor(rowH / MIN_STEP).toInt()))
    }

    /** Number of to-do lines on a day page. */
    fun dayLines(pageHeight: Float): Int = max(8, floor((pageHeight - HEADER_H - BOTTOM_MARGIN) / DAY_STEP).toInt())

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
