package com.ethran.notable.reading.chapters

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** A station of the line: where a chapter starts, as a percentage of the book (0..100). */
data class Stop(val title: String, val pct: Double)

/**
 * The stations a [ChapterMap] draws (ChapterMap.stations in lb_chapter_map.lua): the shallowest level of the contents that
 * has at least [MIN_STOPS] entries, so a book in parts gets its chapters and not four parts; entries shorter than
 * [MIN_SPAN] of the book (a title page, a dedication) fold into the entry after them. The last entry always stays, it is
 * the end of the line.
 */
object ChapterStops {
    const val MIN_SPAN = 0.01
    const val MIN_STOPS = 6

    fun stations(map: ChapterMap): List<Stop>? {
        val entries = map.entries
        if (entries.isEmpty()) return null
        val maxDepth = entries.maxOf { max(1, it.depth) }
        var depth = maxDepth
        for (level in 1..maxDepth) {
            if (entries.count { max(1, it.depth) <= level } >= MIN_STOPS) { depth = level; break }
        }
        val picked = entries.filter { max(1, it.depth) <= depth }
            .map { Stop(it.title, (it.startFraction * 100).coerceIn(0.0, 100.0)) }
        val stops = ArrayList<Stop>()
        for ((i, s) in picked.withIndex()) {
            val nextPct = picked.getOrNull(i + 1)?.pct ?: 100.0
            if (s.title.isNotEmpty() && (nextPct - s.pct >= MIN_SPAN * 100 || i == picked.lastIndex)) stops.add(s)
        }
        return if (stops.size < 2) null else stops
    }
}

/**
 * The Chapter line of one book (BoardData.chapterLine): its [stops], where the reader is ([pos], percent of the book),
 * the station the reader is at ([cur], 0-based: the last one that starts at or before the reader) and how long to each stop
 * at the pace the finish forecast uses ([minutesLeft] over the percent left).
 *
 * [approximate] is true for an EPUB, whose chapter positions are estimated from file sizes (see [EpubChapters]); the board
 * puts a "~" before the times then.
 */
class ChapterLine(val stops: List<Stop>, val pos: Double, val minutesLeft: Int?, val approximate: Boolean = false) {
    init { require(stops.size >= 2) { "a line needs two stops" } }

    val lastIndex: Int = stops.size - 1

    val cur: Int = stops.indexOfLast { it.pct <= pos }.coerceAtLeast(0)

    /** The stop the train is heading for; the last stop heads for itself. */
    val next: Int = min(cur + 1, lastIndex)

    /** The reader is in the last chapter: there is no next stop, only the end of the book. */
    val atLastStop: Boolean = cur == lastIndex

    val stopsLeft: Int = lastIndex - cur

    private val perPct: Double? = minutesLeft?.let { it / max(0.1, 100.0 - pos) }

    /** Minutes until station [i] starts, at least 1; null when the book has no reading pace yet. */
    fun minutes(i: Int): Int? = perPct?.let { max(1, floor((stops[i].pct - pos) * it + 0.5).toInt()) }

    /** Minutes to the next stop, or null when there is none ([atLastStop]) or no pace. */
    val minutesToNext: Int? get() = if (atLastStop) null else minutes(next)

    /** Where the train is between its station and the next, 0 to 1. */
    val trainFraction: Double
        get() {
            val here = stops[cur]
            val nxt = stops.getOrNull(cur + 1) ?: return 0.0
            if (nxt.pct <= here.pct) return 0.0
            return ((pos - here.pct) / (nxt.pct - here.pct)).coerceIn(0.0, 1.0)
        }

    /** Something to sort out the box's layout choice on, so a choice is not reused for other data. */
    val signature: String get() = "$cur:${minutesToNext}:${stops.size}"
}

object ChapterLines {
    /**
     * Where the reader is in the book, as a percentage comparable with [Stop.pct].
     *
     * For a PDF whose page count NeoReader agrees with, the reader's page decides (page p starts at (p - 1) / pages, so a
     * chapter that starts on the page being read counts, and one that starts on the next page does not). NeoReader's
     * progress is the page number over the page count. For an EPUB (NeoReader's own pagination, not the file's) and for a PDF
     * whose page counts differ, the progress percent is used as it is.
     */
    fun position(map: ChapterMap, progressPct: Double, bookPages: Int): Double {
        val p = progressPct.coerceIn(0.0, 100.0)
        if (map.source == ChapterSource.Pdf && map.pages > 0 && bookPages == map.pages) {
            val page = floor(p / 100.0 * map.pages + 0.5).toInt().coerceIn(1, map.pages)
            return (page - 1).toDouble() / map.pages * 100.0
        }
        return p
    }

    /** The line for a book, or null when the contents have fewer than two usable stops. */
    fun forBook(map: ChapterMap, progressPct: Double, bookPages: Int, minutesLeft: Int?): ChapterLine? {
        val stops = ChapterStops.stations(map) ?: return null
        return ChapterLine(stops, position(map, progressPct, bookPages), minutesLeft, approximate = map.source == ChapterSource.Epub)
    }
}

/** Where a label under the line goes: [index] of the stop (-1 for the "End" label), left edge [x], and whether it is bold. */
data class PlacedLabel(val index: Int, val x: Int, val bold: Boolean)

/**
 * The plugin's presentation rules for the Next stop box (Decor.chapters), without any drawing: which rows a timetable
 * shows, which labels fit under the line and how long a title may be. Sizes are in pixels, handed in by the caller.
 */
object ChapterLayout {
    /** The [PlacedLabel.index] of the "End" label. */
    const val END_LABEL = -1

    /** A timetable shows at least this many rows or it is not used. */
    const val MIN_TIMETABLE_ROWS = 3

    /** Labels under the line: up to six on a wide box, three on a narrow one (wider than 500 plugin dp is wide). */
    fun maxLabels(innerWidthDp: Double): Int = if (innerWidthDp > 500) 6 else 3

    /** The stops a timetable lists: [indices] from the one the reader is at, the last stop last; [gap] when stops are left out before it. */
    data class TimetableRows(val indices: List<Int>, val gap: Boolean)

    /** The stops a timetable of [rows] rows lists (the stretch to the end is dashed when [TimetableRows.gap]). */
    fun timetableRows(line: ChapterLine, rows: Int): TimetableRows {
        val lastI = line.lastIndex
        val ahead = lastI - line.cur
        val showEnd = line.cur + rows - 1 < lastI
        val count = min(rows - (if (showEnd) 1 else 0), ahead + 1)
        val list = ArrayList<Int>()
        for (k in 0 until count) list.add(line.cur + k)
        if (showEnd) list.add(lastI)
        return TimetableRows(list, showEnd)
    }

    /** How many timetable rows fit in [availPx], with [rowH] per row and the stretch the train stands on 0.7 rows tall. */
    fun timetableRowCount(availPx: Int, rowH: Int): Int = floor(availPx.toDouble() / rowH - 0.7).toInt()

    /**
     * The labels under the line, left to right, where they do not collide: "End" first (bold, at the last stop), then the
     * stops from the next one on, up to [maxLabels] in all, each at its station's centre ([center]) clamped into
     * [0, width - labelWidth]. A label that collides is skipped, except that failing to place the next stop's label ends
     * the row: nothing farther along is shown without it. [labelWidth] gives the width of stop i's text, [endWidth] the
     * width of "End".
     */
    fun placeLabels(
        line: ChapterLine, width: Int, maxLabels: Int, gap: Int, center: (Int) -> Double, labelWidth: (Int) -> Int, endWidth: Int
    ): List<PlacedLabel> {
        val placed = ArrayList<PlacedLabel>()
        val spans = ArrayList<IntRange>()
        fun place(stop: Int, shown: Int, tw: Int, bold: Boolean): Boolean {
            val x = Math.round(center(stop) - tw / 2.0).toInt().coerceIn(0, max(0, width - tw))
            for (s in spans) if (x < s.last + gap && x + tw > s.first - gap) return false
            spans.add(x..x + tw)
            placed.add(PlacedLabel(shown, x, bold))
            return true
        }
        place(line.lastIndex, END_LABEL, endWidth, true)
        if (!line.atLastStop) {
            for (i in line.next until line.lastIndex) {
                if (placed.size >= maxLabels) break
                if (!place(i, i, labelWidth(i), i == line.next) && i == line.next) break
            }
        }
        return placed
    }

    /** The text of the label for stop [i]: its title cut to [TitleText.LABEL_MAX] and, when there is a pace, the time to it. */
    fun labelText(line: ChapterLine, i: Int, shortDuration: (Int) -> String): String {
        val title = TitleText.shorten(line.stops[i].title)
        val m = line.minutes(i) ?: return title
        return "$title · ${if (line.approximate) "~" else ""}${shortDuration(m)}"
    }
}

/** The sizes the layout rules need, in pixels: the board's px(dp) and single-line text height lh1(dp). */
interface LayoutMetrics {
    fun px(dp: Double): Int
    fun lh1(dp: Double): Int
}

/** Which of the Next stop box's layouts is used (the first that fits, in the plugin's order). */
sealed interface ChapterPlan {
    /** The next stops as a timetable down a vertical line: needs [rows] (at least 3) rows. */
    data class Timetable(val rows: Int) : ChapterPlan
    /** The next stop large with the whole line under it, in type of [size], with or without [labels] and [foot]. */
    data class Big(val size: Double, val labels: Boolean, val foot: Boolean) : ChapterPlan
    /** How long, which stop (when [name]) and the line in miniature. */
    data class Mini(val name: Boolean) : ChapterPlan
    /** Only how long: the layout for a box with no room for anything else. */
    data object Minimal : ChapterPlan
}

object ChapterPlans {
    private val BIG = listOf(Triple(16.0, true, true), Triple(16.0, false, true), Triple(15.0, false, false), Triple(14.0, false, false))

    /** The tallest the line drawing is: 20 dp, plus a 2 dp gap and a 11 sp label row when labelled. */
    fun artHeight(m: LayoutMetrics, labels: Boolean): Int = m.px(20.0) + if (labels) m.px(2.0) + m.lh1(11.0) else 0

    /**
     * Picks the layout for a box [iw] by [ih] pixels inside its padding (Decor.chapters): a timetable on a tall, narrow
     * box with room for three rows; else the next stop large over the line (first with labels and a footer, then
     * smaller and plainer) when the box is at least 220 dp wide; else the miniature; else just the time.
     */
    fun choose(m: LayoutMetrics, line: ChapterLine, iw: Int, ih: Int): ChapterPlan {
        val head = m.lh1(13.0)
        if (!line.atLastStop && ih > m.px(150.0) && iw < ih * 1.9) {
            val foot = m.lh1(12.0) + m.px(10.0)
            val rows = ChapterLayout.timetableRowCount(ih - head - m.px(12.0) - foot, m.lh1(14.0) + m.px(6.0))
            if (rows >= ChapterLayout.MIN_TIMETABLE_ROWS) return ChapterPlan.Timetable(rows)
        }
        if (iw >= m.px(220.0)) {
            for ((size, labels, foot) in BIG) {
                val h = head + m.lh1(size) + artHeight(m, labels) + (if (foot) m.lh1(12.0) else 0)
                if (h <= ih) return ChapterPlan.Big(size, labels, foot)
            }
        }
        for (name in listOf(true, false)) {
            val h = head + m.lh1(20.0) + (if (name) m.lh1(12.0) else 0) + artHeight(m, false)
            if (h <= ih) return ChapterPlan.Mini(name)
        }
        return ChapterPlan.Minimal
    }
}
