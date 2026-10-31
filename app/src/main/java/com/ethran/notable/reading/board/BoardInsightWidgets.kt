package com.ethran.notable.reading.board

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import com.ethran.notable.reading.ReadingWords
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

private fun shortMonth(d: LocalDate) = d.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
private fun shortWeekday(d: LocalDate) = d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)

// ============================================================ Year in reading
@Composable
fun HeatmapWidget(c: BoardCtx, w: Int, hh: Int) {
    val bx = LocalBx.current
    val pad = bx.px(12)
    val iw = w - 2 * pad
    val g = max(1, bx.px(1.5))
    val legendH = bx.lh1(11)
    val monthH = bx.lh1(11)
    val gridH = hh - 2 * pad - bx.lh1(13) - bx.px(8) - monthH - bx.px(3) - bx.px(8) - legendH
    var weeks = 52
    var cell = min((iw - (weeks - 1) * g) / weeks, (gridH - 6 * g) / 7)
    if (cell < bx.px(6.5)) {
        cell = max(bx.px(4), min(bx.px(9), (gridH - 6 * g) / 7))
        weeks = min(52, (iw + g) / (cell + g))
    }
    val offset = (52 - weeks) * 7
    val heat = c.d.heat
    var total = 0; var daysRead = 0; var best = 0; var bestI = -1
    for (i in offset until heat.size) {
        val v = heat[i] ?: 0
        total += v
        if (v > 0) daysRead++
        if (v > best) { best = v; bestI = i }
    }
    val ramp = BoardColour.ramp("heatmap", listOf(Ink.ge, Ink.gb, Ink.g8, Ink.g4, Ink.black))
    fun level(v: Int): Color = when { v <= 0 -> ramp[0]; v < 30 -> ramp[1]; v < 60 -> ramp[2]; v < 120 -> ramp[3]; else -> ramp[4] }
    val gridW = weeks * cell + (weeks - 1) * g
    val radius = max(1, bx.px(1.5))
    val todayI = c.d.heatToday
    val label = if (weeks >= 52) "Year in reading" else "Past ${(weeks / 4.35 + 0.5).toInt()} months"
    val used = bx.lh1(13) + bx.px(8) + monthH + bx.px(3) + 7 * cell + 6 * g + bx.px(8) + legendH
    val roomForFigs = total > 0 && hh - 2 * pad - used >= bx.lh1(20) + bx.lh1(11) + bx.px(6)
    val figs = listOf(
        "${(total / 60.0 + 0.5).toInt()} h" to "read", "$daysRead" to "days", "${c.d.longestStreak}" to "best streak",
        "${(total / max(1, daysRead).toDouble() + 0.5).toInt()} min" to "a reading day"
    )
    val g4 = Ink.g4
    WPad(w, hh, pad, Alignment.CenterHorizontally) {
        BHead(iw, label, if (!roomForFigs && total > 0) ({
            BRuns(listOf(Run("${(total / 60.0 + 0.5).toInt()} h", b = true), Run(" · $daysRead days", color = g4)), 13.0)
        }) else null)
        VGap(bx.px(8))
        // Month names above the weeks where the month changes.
        Box(Modifier.size(bx.d(gridW), bx.d(monthH))) {
            var prevM = -1; var prevX = -999
            for (wk in 0 until weeks) {
                val day = c.d.heatStart.plusDays((offset + wk * 7).toLong())
                val x = wk * (cell + g)
                if (prevM != -1 && day.monthValue != prevM && x - prevX >= bx.px(26) && x + bx.px(24) <= gridW) {
                    Box(Modifier.offset(x = bx.d(x))) { BText(shortMonth(day), 11.0, color = g4) }
                    prevX = x
                }
                prevM = day.monthValue
            }
        }
        VGap(bx.px(3))
        Canvas(Modifier.size(bx.d(gridW), bx.d(7 * cell + 6 * g))) {
            for (wk in 0 until weeks) for (day in 0..6) {
                val i = offset + wk * 7 + day
                val v = heat[i] ?: continue
                val x = (wk * (cell + g)).toFloat(); val y = (day * (cell + g)).toFloat()
                if (i == todayI) {
                    val ring = max(2, bx.px(2)).toFloat(); val gap = max(1, bx.px(1)).toFloat()
                    drawRoundRect(Ink.black, Offset(x - ring, y - ring), Size(cell + 2 * ring, cell + 2 * ring), CornerRadius(radius + ring))
                    drawRoundRect(Ink.white, Offset(x - gap, y - gap), Size(cell + 2 * gap, cell + 2 * gap), CornerRadius(radius + gap))
                }
                drawRoundRect(level(v), Offset(x, y), Size(cell.toFloat(), cell.toFloat()), CornerRadius(radius.toFloat()))
            }
        }
        Flex()
        if (roomForFigs) {
            Row(Modifier.width(bx.d(gridW))) {
                figs.forEachIndexed { i, f ->
                    if (i > 0) Flex()
                    Column { BText(f.first, 20.0, bold = true); BText(f.second, 11.0, color = g4) }
                }
            }
            Flex()
        }
        // Legend: the best day on the left, the swatches on the right.
        val sw = max(bx.px(8), min(cell, bx.px(11)))
        Row(Modifier.width(bx.d(gridW)).height(bx.d(legendH)), verticalAlignment = Alignment.CenterVertically) {
            if (bestI >= 0) {
                val d = c.d.heatStart.plusDays(bestI.toLong())
                BRuns(listOf(Run("Best day "), Run(BoardData.shortDuration(best), b = true, color = Ink.black), Run(", ${shortWeekday(d)} ${d.dayOfMonth} ${shortMonth(d)}")), 11.0, color = g4)
            } else BText("Your reading days fill in here", 11.0, color = g4)
            Flex()
            BText("Less", 11.0, color = g4)
            HGap(bx.px(5))
            Row(horizontalArrangement = Arrangement.spacedBy(bx.dd(3))) { listOf(0, 20, 45, 90, 200).forEach { BRect(sw, sw, level(it), radius) } }
            HGap(bx.px(5))
            BText("More", 11.0, color = g4)
        }
    }
}

// ============================================================ Finish forecast
@Composable
fun ForecastWidget(c: BoardCtx, w: Int, hh: Int) {
    val bx = LocalBx.current
    val measurer = rememberMeasurer()
    val f = c.d.forecast
    val pad = bx.px(12)
    val iw = w - 2 * pad
    val ih = hh - 2 * pad
    val tall = hh >= bx.px(160)
    val ink = BoardColour.icon("forecast")
    if (f == null) {
        WPad(w, hh, pad) {
            BHead(iw, "Finish forecast", { Ic("hourglass", 16, ink, iconSw(2.25f)) })
            VGap(bx.px(6))
            BText("Read a little on this device and a finish date shows up here.", 13.0, color = Ink.g4, lines = 3, w = iw)
        }
        return
    }
    val day = when (f.daysOut) { 0 -> "Today"; 1 -> "Tomorrow"; else -> f.date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH) }
    val date = "${f.date.dayOfMonth} ${shortMonth(f.date)}"
    fun wOf(t: String, s: Double, b: Boolean = false) = measurer.measure(AnnotatedString(t), boardStyle(bx, s, bold = b), maxLines = 1, softWrap = false).size.width
    val coverW = (ih * 2 / 3.0 + 0.5).toInt()
    val tw = iw - coverW - bx.px(12)
    val bottoms = listOf("${BoardData.duration(f.minutesLeft)} left at ~${f.perDay} min a day", "${BoardData.duration(f.minutesLeft)} left", "${BoardData.shortDuration(f.minutesLeft)} left")
    val dayS = if (tall) 26.0 else 22.0
    val dayFits = wOf(day, dayS, true) + wOf("  $date", 13.0) <= tw
    val bottom = bottoms.firstOrNull { wOf(it, 12.0) <= tw } ?: bottoms.last()
    WPad(w, hh, 0) {
        Row(Modifier.size(bx.d(w), bx.d(hh))) {
            HGap(pad)
            Column(Modifier.width(bx.d(tw)).fillMaxHeight().padding(vertical = bx.d(pad))) {
                BHead(tw, if (dayFits) "Finish forecast" else "Finish")
                Flex()
                if (dayFits) BRuns(listOf(Run(day, b = true, s = dayS), Run("  $date", color = Ink.g4)), 13.0, w = tw)
                else BText(day, 22.0, bold = true, w = tw)
                VGap(bx.px(4))
                BText(bottom, 12.0, color = Ink.g4, w = tw)
            }
            HGap(bx.px(12))
            Column(Modifier.width(bx.d(coverW)).fillMaxHeight().padding(vertical = bx.d(pad))) { BCover(f.book, coverW, ih) }
        }
    }
}

// ============================================================ When you read
private val DAY_PARTS = listOf("Mornings" to (5..11), "Afternoons" to (12..16), "Evenings" to (17..21), "Late nights" to (22..28))

private fun dayPart(hours: List<Int>): String? {
    var best: String? = null
    var bestMin = 0
    for ((name, range) in DAY_PARTS) {
        val m = range.sumOf { hours.getOrElse(it % 24) { 0 } }
        if (m > bestMin) { best = name; bestMin = m }
    }
    return best
}

@Composable
fun HoursWidget(c: BoardCtx, w: Int, hh: Int) {
    val bx = LocalBx.current
    val measurer = rememberMeasurer()
    val pad = bx.px(12)
    val iw = w - 2 * pad
    val hours = c.d.data.hours
    val part = dayPart(hours)
    val ink = BoardColour.icon("hours")
    if (part == null) {
        WPad(w, hh, pad) {
            BHead(iw, "When you read", { Ic("sun-moon", 17, ink, iconSw(2.25f)) })
            VGap(bx.px(6))
            BText("Read on this device and your hours show up here.", 13.0, color = Ink.g4, lines = 3, w = iw)
        }
        return
    }
    val maxV = max(1, hours.max())
    var peak = 0
    for (i in 1 until 24) if (hours[i] > hours[peak]) peak = i
    val gap = max(1, bx.px(2))
    val barW = max(1, (iw - 23 * gap) / 24)
    val chartW = 24 * barW + 23 * gap
    val cellW = 6 * (barW + gap)
    val base = max(1, bx.px(1))
    val minChart = bx.px(12)
    val tallH = hh - 2 * pad - bx.lh1(13) - bx.px(4) - bx.lh1(20) - bx.px(2) - bx.lh1(12) - bx.px(10) - bx.px(4) - bx.lh1(11)
    val tall = tallH >= bx.px(40)
    val besideH = hh - 2 * pad - bx.lh1(13) - bx.px(8) - bx.px(4) - bx.lh1(11)
    val chartH = if (tall) tallH else max(minChart, besideH)
    val peakLabel = "Most at %02d:00".format(peak)
    val peakCol = BoardColour.ink("hours", Ink.black)
    val midCol = BoardColour.mid("hours", Ink.g8)
    WPad(w, hh, pad) {
        if (tall) {
            BHead(iw, "When you read", { Ic("sun-moon", 17, ink, iconSw(2.25f)) })
            VGap(bx.px(4)); BText(part, 20.0, bold = true, w = iw); VGap(bx.px(2)); BText(peakLabel, 12.0, color = Ink.g4, w = iw); VGap(bx.px(10))
        } else {
            BHead(iw, "When you read", { BText(part, 13.0, bold = true) }, rightW = measurer.measure(AnnotatedString(part), boardStyle(bx, 13.0, bold = true), maxLines = 1, softWrap = false).size.width)
            VGap(bx.px(8))
        }
        Row(Modifier.width(bx.d(chartW)).height(bx.d(chartH - base)), horizontalArrangement = Arrangement.spacedBy(bx.d(gap)), verticalAlignment = Alignment.Bottom) {
            for (i in 0 until 24) {
                val v = hours[i]
                if (v > 0) BRect(barW, max(bx.px(2), floor((chartH - bx.px(2)) * v / maxV.toDouble() + 0.5).toInt()), if (i == peak) peakCol else midCol)
                else Box(Modifier.width(bx.d(barW)))
            }
        }
        BRect(chartW, base, Ink.black)
        VGap(bx.px(4))
        Row(Modifier.width(bx.d(chartW))) {
            listOf("0", "6", "12", "18").forEachIndexed { i, t -> BText(t, 11.0, color = Ink.g4, w = if (i < 3) cellW else chartW - 3 * cellW) }
        }
    }
}

// ============================================================ Reading records
@Composable
fun RecordsWidget(c: BoardCtx, w: Int, hh: Int) {
    val bx = LocalBx.current
    val r = c.d.data.records
    val pad = bx.px(12)
    val iw = w - 2 * pad
    val ink = BoardColour.icon("records")
    if (r == null) {
        WPad(w, hh, pad) {
            BHead(iw, "Reading records", { Ic("award", 17, ink, iconSw(2.25f)) })
            VGap(bx.px(6))
            BText("Your records appear as you read on this device.", 13.0, color = Ink.g4, lines = 3, w = iw)
        }
        return
    }
    val g = bx.px(12)
    val half = (iw - g) / 2
    val stats = listOf(
        BoardData.duration(r.longestMin) to "longest session", "${r.pagesPerHour}" to "pages an hour",
        BoardData.duration(r.avgMin) to "average session", BoardData.count(r.sessions) to "sessions"
    )
    val twoRows = hh >= bx.px(150)
    @Composable fun cell(st: Pair<String, String>, cw: Int, vs: Double) = Column(Modifier.width(bx.d(cw))) {
        BText(st.first, vs, bold = true, w = cw); VGap(bx.px(2)); BText(st.second, 11.0, color = Ink.g4, w = cw)
    }
    WPad(w, hh, pad) {
        BHead(iw, "Reading records", { Ic("award", 17, ink, iconSw(2.25f)) })
        Flex()
        Row(horizontalArrangement = Arrangement.spacedBy(bx.d(g))) { cell(stats[0], half, 22.0); cell(stats[1], iw - half - g, 22.0) }
        if (twoRows) {
            Flex()
            Row(horizontalArrangement = Arrangement.spacedBy(bx.d(g))) { cell(stats[2], half, 22.0); cell(stats[3], iw - half - g, 22.0) }
        }
    }
}

// ============================================================ A year ago
@Composable
fun YearAgoWidget(c: BoardCtx, w: Int, hh: Int) {
    val bx = LocalBx.current
    val y = c.d.yearAgo
    val pad = bx.px(12)
    val iw = w - 2 * pad
    val ih = hh - 2 * pad
    val ink = BoardColour.icon("yearago")
    val cal: @Composable () -> Unit = { Ic("calendar-clock", 17, ink, iconSw(2.25f)) }
    if (y == null) {
        WPad(w, hh, pad) {
            BHead(iw, "A year ago", cal)
            VGap(bx.px(6))
            BText("Next year, this shows what you were reading today.", 13.0, color = Ink.g4, lines = 3, w = iw)
        }
        return
    }
    val tall = hh >= bx.px(170)
    if (!tall) {
        val coverW = (ih * 2 / 3.0 + 0.5).toInt()
        val tw = iw - coverW - bx.px(12)
        Row(Modifier.size(bx.d(w), bx.d(hh)).padding(vertical = bx.d(pad)), verticalAlignment = Alignment.Top) {
            HGap(pad)
            BCover(y.book, coverW, ih)
            HGap(bx.px(12))
            Column(Modifier.width(bx.d(tw)).height(bx.d(ih))) {
                BHead(tw, "A year ago")
                Flex()
                BText(y.book.title, 15.0, bold = true, w = tw)
                BText(BoardData.shortDuration(y.minutes), 12.0, color = Ink.g4, w = tw)
            }
        }
        return
    }
    val bodyH = ih - bx.lh1(13) - bx.px(10)
    val coverH = min(bodyH, bx.px(150))
    val coverW = (coverH * 2 / 3.0 + 0.5).toInt()
    val tw = iw - coverW - bx.px(14)
    val btnH = bx.px(34)
    WPad(w, hh, pad) {
        BHead(iw, "A year ago", if (true) ({ BText(BoardData.partOfMonth(y.date), 12.0, color = Ink.g4) }) else cal)
        VGap(bx.px(10))
        Row(Modifier.width(bx.d(iw)).height(bx.d(bodyH)), horizontalArrangement = Arrangement.spacedBy(bx.dd(14)), verticalAlignment = Alignment.Top) {
            BCover(y.book, coverW, coverH)
            Column(Modifier.width(bx.d(tw)).height(bx.d(bodyH))) {
                BText(y.book.title, 17.0, bold = true, lines = 2, w = tw)
                BText(y.book.author, 13.0, color = Ink.g4, w = tw)
                VGap(bx.px(8))
                BText("${BoardData.duration(y.minutes)} of reading that day.", 13.0, lines = 3, w = tw)
                Flex()
                Row(
                    Modifier.height(bx.d(btnH)).clip(RoundedCornerShape(bx.d(btnH / 2))).border(bx.d(max(1, bx.px(1.5))), Ink.black, RoundedCornerShape(bx.d(btnH / 2)))
                        .tapOrHold({ c.onOpen(y.book) }),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HGap(bx.px(14)); Ic("book-open", 15, Ink.black, 2.25f); HGap(bx.px(6)); BText("Pick it up", 13.0, bold = true); HGap(bx.px(14))
                }
            }
        }
    }
}

// ============================================================ Word of the day
@Composable
fun WordWidget(c: BoardCtx, w: Int, hh: Int) {
    val bx = LocalBx.current
    val measurer = rememberMeasurer()
    val pad = bx.px(12)
    val iw = w - 2 * pad
    val ink = BoardColour.icon("word")
    val wd = ReadingWords.of(c.d.today)
    val tall = hh >= bx.px(170)
    val ws = if (tall) 28.0 else 22.0
    val used = bx.lh1(13) + bx.px(if (tall) 10 else 4) + bx.lh1(ws) + bx.px(4)
    val lines = max(0, min(5, (hh - 2 * pad - used) / max(1, bx.lh(14))))
    val wordFit = fitSize(measurer, bx, wd.word, true, true, iw, ws.toInt(), 18)
    WPad(w, hh, pad) {
        BHead(iw, "Word of the day", { Ic("whole-word", 17, ink, iconSw(2.25f)) })
        VGap(bx.px(if (tall) 10 else 4))
        BText(wd.word, wordFit.toDouble(), bold = true, serif = true, color = BoardColour.ink("word", Ink.black), w = iw)
        if (lines > 0) {
            VGap(bx.px(4))
            BText(wd.meaning, 14.0, italic = true, serif = true, color = Ink.g2, lines = lines, w = iw)
        }
    }
}

// ============================================================ Calendar (device without a frontlight)
@Composable
fun CalendarWidget(c: BoardCtx, w: Int, hh: Int) {
    val bx = LocalBx.current
    val pad = bx.px(12)
    val iw = w - 2 * pad
    val today = c.d.today
    val first = today.withDayOfMonth(1)
    val lead = first.dayOfWeek.value % 7
    val days = today.lengthOfMonth()
    val read = (1..days).map { d -> if (d > today.dayOfMonth) false else (c.d.data.minutesByDay[today.withDayOfMonth(d).toEpochDay()] ?: 0) > 0 }
    val readCount = read.count { it }
    val gap = bx.px(3)
    val monthRows = (lead + days + 6) / 7
    val headRowH = bx.lh1(11) + bx.px(3)
    var availH = hh - 2 * pad - bx.lh1(13) - bx.px(8)
    var monthCell = min((availH - (monthRows - 1) * gap) / monthRows, (iw - 6 * gap) / 7)
    val numbered = monthCell >= bx.px(24)
    if (numbered) {
        availH -= headRowH
        monthCell = min((availH - (monthRows - 1) * gap) / monthRows, (iw - 6 * gap) / 7)
    }
    val strip = monthCell < bx.px(14)
    val perRow = if (strip) 16 else 7
    val rows = if (strip) 2 else monthRows
    val offset = if (strip) 0 else lead
    val cell = if (strip) min((iw - 15 * gap) / 16, (availH - gap) / 2) else monthCell
    val gridW = perRow * cell + (perRow - 1) * gap
    val radius = bx.px(if (numbered) 4 else 2)
    val outline = max(1, bx.px(1))
    val deepCol = BoardColour.deep("calendar", Ink.black)
    val inkCol = BoardColour.ink("calendar", Ink.g6)
    WPad(w, hh, pad, Alignment.CenterHorizontally) {
        BHead(iw, today.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH), { BText("$readCount days read", 12.0, color = Ink.g4) })
        VGap(bx.px(8))
        if (numbered && !strip) {
            Row(Modifier.width(bx.d(gridW)).height(bx.d(headRowH)), horizontalArrangement = Arrangement.spacedBy(bx.d(gap))) {
                listOf("S", "M", "T", "W", "T", "F", "S").forEach { BText(it, 11.0, color = Ink.g4, w = cell, align = TextAlign.Center) }
            }
        }
        Box(Modifier.size(bx.d(gridW), bx.d(rows * cell + (rows - 1) * gap))) {
            Canvas(Modifier.size(bx.d(gridW), bx.d(rows * cell + (rows - 1) * gap))) {
                for (i in 0 until rows * perRow) {
                    val day = i - offset + 1
                    if (day < 1 || day > days) continue
                    val x = ((i % perRow) * (cell + gap)).toFloat(); val y = ((i / perRow) * (cell + gap)).toFloat()
                    if (read[day - 1]) drawRoundRect(if (day == today.dayOfMonth) deepCol else inkCol, Offset(x, y), Size(cell.toFloat(), cell.toFloat()), CornerRadius(radius.toFloat()))
                    else drawRoundRect(if (day > today.dayOfMonth) Ink.gd else Ink.gb, Offset(x + outline / 2f, y + outline / 2f), Size(cell - outline.toFloat(), cell - outline.toFloat()), CornerRadius(radius.toFloat()), style = Stroke(outline.toFloat()))
                }
            }
            if (numbered && !strip) {
                for (day in 1..days) {
                    val i = day - 1 + offset
                    val x = (i % 7) * (cell + gap); val y = (i / 7) * (cell + gap) + (cell - bx.lh1(11)) / 2
                    Box(Modifier.offset(x = bx.d(x), y = bx.d(y))) {
                        BText("$day", 11.0, bold = day == today.dayOfMonth, color = if (read[day - 1]) Ink.white else if (day > today.dayOfMonth) Ink.g8 else Ink.g4, w = cell, align = TextAlign.Center)
                    }
                }
            }
        }
        Flex()
    }
}
