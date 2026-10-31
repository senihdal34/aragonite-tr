package com.ethran.notable.reading.board

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import com.ethran.notable.reading.RBook
import com.ethran.notable.reading.chapters.ChapterLayout
import com.ethran.notable.reading.chapters.ChapterLine
import com.ethran.notable.reading.chapters.ChapterPlan
import com.ethran.notable.reading.chapters.ChapterPlans
import com.ethran.notable.reading.chapters.ChapterState
import com.ethran.notable.reading.chapters.LayoutMetrics
import com.ethran.notable.reading.chapters.NoChapters
import kotlin.math.max
import kotlin.math.min

// ============================================================ Next stop
// The book on top as a metro line (Decor.chapters in lb_board_decor.lua): the chapters are the stations, the reader's page the
// car between two of them, the next stop named with how long to it. The chapters come from the book file itself
// (reading/chapters), because NeoReader does not publish a table of contents.

private fun Bx.metrics() = object : LayoutMetrics {
    override fun px(dp: Double) = this@metrics.px(dp)
    override fun lh1(dp: Double) = this@metrics.lh1(dp)
}

@Composable
fun ChaptersWidget(c: BoardCtx, w: Int, hh: Int) {
    val bx = LocalBx.current
    val fam = if (BoardColour.enabled) BoardColour.fam("chapters", c.lead) else null
    val lineCol = fam?.let { BoardColour.color(it.ink) } ?: Ink.black
    val aheadCol = fam?.let { BoardColour.color(it.tint) } ?: Ink.gb
    val book = c.d.data.continueReading.firstOrNull()
    val state = c.d.chapters
    val line = c.d.chapterLine
    val icon: @Composable () -> Unit = { Ic("train-front", 17, lineCol, iconSw(2.25f)) }
    when {
        book == null -> ChapterHint(w, hh, icon, "Open a book and its chapters line up here.", "Open a book to start.")
        line != null -> NextStopBox(c, book, line, w, hh, lineCol, aheadCol, icon, bx)
        state is ChapterState.Empty -> when (state.reason) {
            NoChapters.NoOutline -> ChapterHint(w, hh, icon, "This book has no table of contents to follow.", "No table of contents.")
            NoChapters.UnsupportedFormat -> ChapterHint(w, hh, icon, "Chapters can only be read from PDF and EPUB books.", "No chapters for this format.")
            NoChapters.Unreadable -> ChapterHint(w, hh, icon, "The chapter list could not be read from this file.", "Chapters not readable.")
        }
        // Read, but a contents list too short to draw a line from (fewer than two usable chapters).
        state is ChapterState.Ready && state.bookId == book.id -> ChapterHint(w, hh, icon, "This book has too few chapters to draw a line.", "Too few chapters.")
        else -> ChapterHint(w, hh, icon, "Looking up the chapters of this book.", "Looking up chapters.")
    }
}

/** A quiet text under the title: smaller type, then without the title, before any of it is cut (emptyTile). */
@Composable
private fun ChapterHint(w: Int, hh: Int, right: @Composable () -> Unit, hint: String, short: String) {
    val bx = LocalBx.current
    val measurer = rememberMeasurer()
    val pad = bx.px(12)
    val iw = w - 2 * pad
    val ih = hh - 2 * pad
    class V(val s: Double, val maxLines: Int, val head: Boolean, val text: String)
    val variants = listOf(V(13.0, 3, true, hint), V(12.0, 4, true, hint), V(12.0, 6, false, hint), V(12.0, 3, true, short), V(12.0, 5, false, short))
    val v = variants.firstOrNull { v ->
        val lines = measurer.measure(AnnotatedString(v.text), boardStyle(bx, v.s, multiline = true), constraints = Constraints(maxWidth = max(1, iw))).lineCount
        lines <= v.maxLines && (if (v.head) bx.lh1(13) + bx.px(6) else 0) + lines * bx.lh(v.s) <= ih
    } ?: variants.last()
    WPad(w, hh, pad) {
        if (v.head) { BHead(iw, "Next stop", right); VGap(bx.px(6)) }
        BText(v.text, v.s, color = Ink.g4, lines = v.maxLines, w = iw)
    }
}

private fun textW(measurer: TextMeasurer, style: TextStyle, text: String): Int =
    measurer.measure(AnnotatedString(text), style, maxLines = 1, softWrap = false).size.width

@Composable
private fun NextStopBox(
    c: BoardCtx, book: RBook, line: ChapterLine, w: Int, hh: Int, lineCol: Color, aheadCol: Color, icon: @Composable () -> Unit, bx: Bx
) {
    val measurer = rememberMeasurer()
    val pad = bx.px(12)
    val iw = w - 2 * pad
    val ih = hh - 2 * pad
    val plan = remember(line.signature, iw, ih) { ChapterPlans.choose(bx.metrics(), line, iw, ih) }
    val tilde = if (line.approximate) "~" else ""
    // How long to the next stop; at the last stop, how long to the end of the book.
    val whenText = (if (line.atLastStop) line.minutesLeft?.let { BoardData.duration(it) } else line.minutesToNext?.let { tilde + BoardData.duration(it) })
        ?: BoardData.pctLabel(book.progress)
    val stopsLeft = line.stopsLeft
    val head = if (line.atLastStop) "Last stop" else "Next stop"
    val nextTitle = line.stops[line.next].title

    // "End of book in 3 h 20 min" (or how many stops to go when there is no pace yet).
    @Composable
    fun EndRuns(s: Double) {
        val left = line.minutesLeft
        if (left == null) BText(if (stopsLeft == 1) "1 stop to the end" else "$stopsLeft stops to the end", s, color = Ink.g4)
        else BRuns(listOf(Run("End of book in "), Run(BoardData.duration(left), b = true, color = Ink.black)), s, color = Ink.g4)
    }

    when (plan) {
        is ChapterPlan.Timetable -> {
            val pct = BoardData.pctLabel(book.progress)
            WPad(w, hh, pad) {
                BHead(iw, book.title, { BText(pct, 12.0, color = Ink.g4) }, rightW = textW(measurer, boardStyle(bx, 12.0), pct))
                VGap(bx.px(12))
                Timetable(iw, line, plan.rows, lineCol, aheadCol, tilde)
                Flex()
                EndRuns(12.0)
            }
        }
        is ChapterPlan.Big -> WPad(w, hh, pad) {
            BHead(iw, head, { BText(whenText, 13.0, bold = true) }, rightW = textW(measurer, boardStyle(bx, 13.0, bold = true), whenText))
            BText(nextTitle, plan.size, bold = true, serif = true, w = iw)
            Flex()
            LineArt(iw, line, plan.labels, ChapterLayout.maxLabels(iw / bx.scale.dp), lineCol, aheadCol)
            if (plan.foot) {
                Flex()
                Row(Modifier.width(bx.d(iw)), verticalAlignment = Alignment.CenterVertically) {
                    EndRuns(12.0)
                    Flex()
                    HGap(bx.px(4))
                    BText(if (stopsLeft == 1) "1 stop" else "$stopsLeft stops", 12.0, color = Ink.g4)
                }
            }
        }
        is ChapterPlan.Mini -> WPad(w, hh, pad) {
            BHead(iw, head, icon)
            Flex()
            BText(whenText, 20.0, bold = true, w = iw)
            if (plan.name) BText(nextTitle, 12.0, bold = true, serif = true, w = iw)
            Flex()
            LineArt(iw, line, false, 0, lineCol, aheadCol)
        }
        is ChapterPlan.Minimal -> WPad(w, hh, pad) {
            BHead(iw, head)
            Flex()
            BText(whenText, 18.0, bold = true, w = iw)
            Flex()
        }
    }
}

// ------------------------------------------------------------------ drawing
/** The car: a black rounded box with a white edge and two white windows. */
private fun DrawScope.tram(x: Float, y: Float, bx: Bx) {
    val tw = bx.px(16).toFloat()
    val th = bx.px(11).toFloat()
    val win = bx.px(3.2).toFloat()
    val sw = bx.px(1.5).toFloat()
    val rx = CornerRadius(bx.px(3).toFloat())
    drawRoundRect(Color.Black, Offset(x - tw / 2, y - th / 2), Size(tw, th), rx)
    drawRoundRect(Color.White, Offset(x - tw / 2, y - th / 2), Size(tw, th), rx, style = Stroke(sw))
    val wr = CornerRadius(bx.px(0.8).toFloat())
    drawRoundRect(Color.White, Offset(x - tw / 2 + bx.px(2.5), y - th / 2 + bx.px(2.4)), Size(win * 1.25f, win), wr)
    drawRoundRect(Color.White, Offset(x + tw / 2 - bx.px(2.5) - win * 1.25f, y - th / 2 + bx.px(2.4)), Size(win * 1.25f, win), wr)
}

/** A station: a ring, filled when the reader is past it; the last one has a dot in it. */
private fun DrawScope.station(cx: Float, cy: Float, r: Float, filled: Boolean, last: Boolean, lineCol: Color, bx: Bx) {
    drawCircle(if (filled) lineCol else Color.White, r, Offset(cx, cy))
    drawCircle(lineCol, r, Offset(cx, cy), style = Stroke(max(1, bx.px(1.5)).toFloat()))
    if (last) drawCircle(lineCol, r * 0.42f, Offset(cx, cy))
}

/** The whole line, stations evenly spaced like a metro map, with labels placed left to right where they do not collide. */
@Composable
private fun LineArt(iw: Int, line: ChapterLine, labels: Boolean, maxLabels: Int, lineCol: Color, aheadCol: Color) {
    val bx = LocalBx.current
    val measurer = rememberMeasurer()
    val count = line.stops.size
    val lw = max(3, bx.px(4)).toFloat()
    val r = bx.px(4).toFloat()
    val x0 = bx.px(9).toFloat()
    val x1 = (iw - bx.px(9)).toFloat()
    fun xi(i: Int) = x0 + (x1 - x0) * i / (count - 1)
    val trainX = xi(line.cur) + (xi(min(line.cur + 1, count - 1)) - xi(line.cur)) * line.trainFraction.toFloat()
    val trackY = bx.px(10).toFloat()
    val artH = bx.px(20)
    val rowY = artH + bx.px(2)
    val placed = remember(line.signature, iw, labels, maxLabels) {
        if (!labels) emptyList() else {
            val regular = boardStyle(bx, 11.0)
            val bold = boardStyle(bx, 11.0, bold = true)
            ChapterLayout.placeLabels(
                line, iw, maxLabels, bx.px(8), { xi(it).toDouble() },
                { i -> textW(measurer, if (i == line.next) bold else regular, ChapterLayout.labelText(line, i, BoardData::shortDuration)) },
                textW(measurer, bold, "End")
            )
        }
    }
    Box(Modifier.size(bx.d(iw), bx.d(if (labels) rowY + bx.lh1(11) else artH))) {
        Canvas(Modifier.size(bx.d(iw), bx.d(artH))) {
            drawLine(aheadCol, Offset(trainX, trackY), Offset(x1, trackY), lw, StrokeCap.Round)
            drawLine(lineCol, Offset(x0, trackY), Offset(trainX, trackY), lw, StrokeCap.Round)
            for ((i, stop) in line.stops.withIndex()) {
                val last = i == count - 1
                val rr = min(if (last) r * 1.25f else r, (x1 - x0) / (count - 1) * 0.36f)
                station(xi(i), trackY, rr, stop.pct <= line.pos, last, lineCol, bx)
            }
            tram(trainX, trackY, bx)
        }
        for (p in placed) {
            val text = if (p.index == ChapterLayout.END_LABEL) "End" else ChapterLayout.labelText(line, p.index, BoardData::shortDuration)
            BText(text, 11.0, bold = p.bold, color = if (p.bold) Ink.black else Ink.g4, modifier = Modifier.offset(bx.d(p.x), bx.d(rowY)))
        }
    }
}

/** The next stations as a timetable down a vertical line, with how long until each; the stretch the car is on is taller. */
@Composable
private fun Timetable(w: Int, line: ChapterLine, rows: Int, lineCol: Color, aheadCol: Color, tilde: String) {
    val bx = LocalBx.current
    val measurer = rememberMeasurer()
    val table = remember(line.signature, rows) { ChapterLayout.timetableRows(line, rows) }
    val list = table.indices
    val rowH = bx.lh1(14) + bx.px(6)
    val stretch = 0.7
    val h = Math.round((list.size + stretch) * rowH).toInt()
    fun cy(k: Int) = (k * rowH + rowH / 2f + (if (k >= 1) (stretch * rowH).toFloat() else 0f))
    val lx = bx.px(10).toFloat()
    val lw = max(3, bx.px(4)).toFloat()
    val r = bx.px(5).toFloat()
    val carH = bx.px(16).toFloat()
    val clear = r + carH / 2 + bx.px(1)
    val ty = (cy(0) + (cy(1) - cy(0)) * line.trainFraction.toFloat()).coerceIn(cy(0) + clear, max(cy(0) + clear, cy(1) - clear))
    val nameX = (lx + bx.px(18)).toInt()
    Box(Modifier.size(bx.d(w), bx.d(h))) {
        Canvas(Modifier.size(bx.d(w), bx.d(h))) {
            val lastY = cy(list.size - 1)
            drawLine(lineCol, Offset(lx, cy(0)), Offset(lx, ty), lw)
            if (table.gap && list.size >= 2) {
                val gapY = cy(list.size - 2)
                drawLine(aheadCol, Offset(lx, ty), Offset(lx, gapY), lw)
                drawLine(aheadCol, Offset(lx, gapY), Offset(lx, lastY), lw, pathEffect = PathEffect.dashPathEffect(floatArrayOf(bx.px(2).toFloat(), bx.px(4).toFloat())))
            } else drawLine(aheadCol, Offset(lx, ty), Offset(lx, lastY), lw)
            for ((k, i) in list.withIndex()) {
                val last = i == line.lastIndex
                station(lx, cy(k), if (last) r * 1.2f else r, k == 0, last, lineCol, bx)
            }
            val carW = bx.px(11).toFloat()
            val win = bx.px(3.2).toFloat()
            val rx = CornerRadius(bx.px(3).toFloat())
            drawRoundRect(Color.Black, Offset(lx - carW / 2, ty - carH / 2), Size(carW, carH), rx)
            drawRoundRect(Color.White, Offset(lx - carW / 2, ty - carH / 2), Size(carW, carH), rx, style = Stroke(bx.px(1.5).toFloat()))
            drawRoundRect(Color.White, Offset(lx - win * 0.65f, ty + carH / 2 - bx.px(2.4) - win), Size(win * 1.3f, win), CornerRadius(bx.px(0.8).toFloat()))
        }
        for ((k, i) in list.withIndex()) {
            val m = line.minutes(i)
            val right = if (k == 0) "now" else m?.let { tilde + BoardData.duration(it) } ?: ""
            val rw = textW(measurer, boardStyle(bx, 13.0, bold = k == 1), right)
            val nw = w - nameX - rw - bx.px(8)
            val y = Math.round(cy(k) - bx.lh1(14) / 2f)
            val col = if (k == 0) Ink.g4 else Ink.black
            BText(line.stops[i].title, 14.0, bold = k == 1, color = col, w = max(1, nw), modifier = Modifier.offset(bx.d(nameX), bx.d(y)))
            if (right.isNotEmpty()) BText(right, 13.0, bold = k == 1, color = col, modifier = Modifier.offset(bx.d(w - rw), bx.d(y + Math.round((bx.lh1(14) - bx.lh1(13)) / 2f))))
        }
    }
}
