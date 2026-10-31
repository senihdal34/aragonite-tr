package com.ethran.notable.reading.board

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import com.ethran.notable.reading.RBook
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** Storage of the device for the On device link. */
data class Storage(val freeGb: Double, val totalGb: Double)

/** Everything a box needs: the derived data, the device state and what a tap does. */
class BoardCtx(
    val d: BoardData,
    val clock: String, val weekday: String, val dayMonth: String,
    val online: Boolean, val storage: Storage?, val lastSyncMs: Long?, val nowMs: Long,
    /** The colour family of the lead book's cover (Continue reading and the chapter line wear it). */
    val lead: Family?,
    val onOpen: (RBook) -> Unit, val onInfo: (RBook) -> Unit, val onLink: (String) -> Unit, val onSync: () -> Unit,
    val onToggle: (String) -> Unit, val onShuffle: () -> Unit,
    /** The step the Gathering dust box is past today's book (its next arrow). */
    val dustSkip: Int = 0, val onDustNext: () -> Unit = {},
    /** The badges worked out from the reading (null until the reading has loaded) and the tap that opens the list of them. */
    val badges: com.ethran.notable.reading.badges.BadgeBoard? = null, val onBadges: () -> Unit = {}
)

@Composable internal fun ColumnScope.Flex() = Spacer(Modifier.weight(1f))
@Composable internal fun RowScope.Flex() = Spacer(Modifier.weight(1f))

/** A widget's content area: the whole tile with [pad] around it. */
@Composable
internal fun WPad(w: Int, h: Int, pad: Int, align: Alignment.Horizontal = Alignment.Start, content: @Composable ColumnScope.() -> Unit) {
    val bx = LocalBx.current
    Column(Modifier.size(bx.d(w), bx.d(h)).padding(bx.d(pad)), horizontalAlignment = align, content = content)
}

private fun thin(bx: Bx) = max(1, bx.px(0.75))

// ============================================================ Continue reading
@Composable
fun HeroWidget(c: BoardCtx, w: Int, hh: Int) {
    val bx = LocalBx.current
    val measurer = rememberMeasurer()
    val book = c.d.data.continueReading.firstOrNull()
    val total = c.d.data.continueReading.size
    val pad = bx.px(16)
    val iw = w - 2 * pad
    val ih = hh - 2 * pad
    val fillCol = Ink.white
    val label = BoardColour.onInk(c.lead, Ink.gb)
    val soft = BoardColour.onInk(c.lead, Ink.gc)
    val author = BoardColour.onInk(c.lead, Ink.gd)
    val track = BoardColour.inkTrack(c.lead, Ink.g5)
    if (book == null) {
        EmptyHero(c, w, hh, pad, label)
        return
    }
    val pct = book.progress
    val coverH = ih
    val coverW = min((coverH * 2 / 3.0 + 0.5).toInt(), (iw * 0.4 + 0.5).toInt())
    val gutter = bx.px(16)
    val tw = iw - coverW - gutter
    val compact = false
    val labelGap = bx.px(if (compact) 4 else 8); val authorGap = bx.px(if (compact) 4 else 6); val progressGap = bx.px(if (compact) 4 else 10)
    val metaGap = bx.px(if (compact) 4 else 8); val actionGap = bx.px(if (compact) 8 else 12)
    val hasAuthor = book.author.isNotBlank()
    val fixed = bx.lh1(12) + labelGap + (if (hasAuthor) authorGap + bx.lh1(15) else 0) + progressGap + bx.px(6) + metaGap + bx.lh1(13) + actionGap + bx.px(40)
    val tf = fitTitle(measurer, bx, book.title, true, false, tw, listOf(26.0, 23.0, 20.0, 18.0, 16.0), 3, max(coverH - fixed, bx.lh(16)))
    // The Resume button and the round "more" button side by side when the column is wide enough, else Resume alone.
    val btnTextFull = measurer.measure(AnnotatedString("Resume reading"), boardStyle(bx, 14.0, bold = true), maxLines = 1, softWrap = false).size.width
    val fullBtnW = bx.px(13) + bx.px(14) + bx.px(8) + btnTextFull + bx.px(16)
    val withGhost = fullBtnW + bx.px(8) + bx.px(40) <= tw
    Row(Modifier.size(bx.d(w), bx.d(hh)), verticalAlignment = Alignment.Top) {
        HGap(pad)
        Column(Modifier.width(bx.d(coverW)).fillMaxHeight().padding(vertical = bx.d(pad)), horizontalAlignment = Alignment.Start) {
            Flex()
            BCover(book, coverW, min(coverH, (coverW * 1.5 + 0.5).toInt()), max(1, bx.px(1)), Ink.g4)
            Flex()
        }
        HGap(gutter)
        Column(Modifier.width(bx.d(tw)).fillMaxHeight().padding(vertical = bx.d(pad))) {
            Row(Modifier.width(bx.d(tw)), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { BText("Continue reading", 12.0, bold = true, color = label) }
                if (total > 1) {
                    BText("1 / $total", 12.0, color = label)
                    HGap(bx.px(2))
                    Ic("chevron-right", 15, label, 2.5f)
                }
            }
            VGap(labelGap)
            BText(book.title, tf.s, bold = true, color = fillCol, lines = tf.lines, w = tw)
            if (hasAuthor) {
                VGap(authorGap)
                BText(book.author, 15.0, color = author, w = tw)
            }
            Flex()
            BBar(tw, bx.px(6), pct, track, fillCol, bx.px(3))
            VGap(metaGap)
            BRuns(
                buildList {
                    add(Run(BoardData.pctLabel(pct), b = true, color = fillCol))
                    book.minutesLeft?.let { add(Run(" · ${BoardData.duration(it)} left", color = soft)) }
                }, 13.0, w = tw
            )
            VGap(actionGap)
            Row(verticalAlignment = Alignment.CenterVertically) {
                PillButton(if (withGhost) "Resume reading" else "Resume", "play") { c.onOpen(book) }
                if (withGhost) {
                    HGap(bx.px(8))
                    BCircle(bx.px(40), border = max(1, bx.px(1.5)), borderCol = Ink.white, modifier = Modifier.tapOrHold({ c.onInfo(book) })) {
                        Ic("ellipsis", 18, Ink.white, 2.5f)
                    }
                }
            }
        }
    }
}

/** The Resume pill: play icon, label, 40 dp high. White on the dark hero tile, black on a white page ([dark]). */
@Composable
fun PillButton(label: String, icon: String, dark: Boolean = false, onClick: () -> Unit) {
    val bx = LocalBx.current
    val fg = if (dark) Ink.white else Ink.black
    Row(
        Modifier.height(bx.dd(40)).clip(RoundedCornerShape(bx.dd(20))).background(if (dark) Ink.black else Ink.white).tapOrHold(onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HGap(bx.px(13))
        Ic(icon, 14, fg, 2.25f, fill = true)
        HGap(bx.px(8))
        BText(label, 14.0, bold = true, color = fg)
        HGap(bx.px(16))
    }
}

@Composable
private fun EmptyHero(c: BoardCtx, w: Int, hh: Int, pad: Int, label: Color) {
    val bx = LocalBx.current
    val iw = w - 2 * pad
    val ih = hh - 2 * pad
    val picks = c.d.data.discover
    val g2 = bx.px(8)
    val btnH = bx.px(38)
    val ch = min(bx.px(120), ih - bx.lh1(11) - bx.px(3) - 2 * bx.lh(16) - bx.px(10) - bx.px(10) - btnH)
    val n = min(3, picks.size)
    val cw = if (n > 0) min((ch * 2 / 3.0 + 0.5).toInt(), (iw - (n - 1) * g2) / n) else 0
    WPad(w, hh, pad) {
        BText("Start reading", 11.0, bold = true, color = label, w = iw)
        VGap(bx.px(3))
        BText("Pick a book from your library to begin", 16.0, bold = true, color = Ink.white, lines = 2, w = iw)
        Flex()
        Row(horizontalArrangement = Arrangement.spacedBy(bx.d(g2)), verticalAlignment = Alignment.Bottom) {
            picks.take(n).forEach { b -> Box(Modifier.tapOrHold({ c.onOpen(b) }, { c.onInfo(b) })) { BCover(b, cw, (cw * 1.5 + 0.5).toInt(), max(1, bx.px(1)), Ink.g6) } }
        }
        VGap(bx.px(10))
        Row(
            Modifier.height(bx.d(btnH)).clip(RoundedCornerShape(bx.d(btnH / 2))).background(Ink.white).tapOrHold({ c.onShuffle() }),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HGap(bx.px(13)); Ic("shuffle", 16, Ink.black, 2.25f); HGap(bx.px(8)); BText("Shuffle", 14.0, bold = true); HGap(bx.px(16))
        }
    }
}

/** Width of a header action: bold text, 2 dp, then a 15 dp chevron. */
fun headActionW(bx: Bx, measurer: androidx.compose.ui.text.TextMeasurer, label: String): Int =
    measurer.measure(AnnotatedString(label), boardStyle(bx, 13.0, bold = true), maxLines = 1, softWrap = false).size.width + bx.px(2) + bx.px(15)

// ============================================================ Streak
@Composable
fun StreakWidget(c: BoardCtx, w: Int, hh: Int) {
    val bx = LocalBx.current
    val pad = bx.px(12)
    val iw = w - 2 * pad
    val n = c.d.streak
    val wide = w > bx.px(200)
    val valS = BoardData.fillSize(hh - 2 * pad - bx.lh1(13) - bx.px(18), bx.scale.dp, 20, if (wide) 40 else 36)
    val dotSize = bx.px(if (wide) 11 else 9)
    val dotGap = if (wide) bx.px(7) else max(bx.px(3), (iw - 7 * bx.px(9)) / 6)
    val ink = BoardColour.ink("streak", Ink.black)
    WPad(w, hh, pad) {
        BHead(iw, "Streak", { Ic("flame", 17, ink, iconSw(2.25f)) })
        Flex()
        if (n > 0) BRuns(listOf(Run("$n", b = true, s = valS.toDouble()), Run(" " + if (n == 1) "day" else "days", color = Ink.g4)), 13.0, w = iw)
        else BRuns(listOf(Run("Read today", b = true, s = min(valS, 18).toDouble())), 13.0, w = iw)
        Flex()
        val dotRow: @Composable () -> Unit = { Row(horizontalArrangement = Arrangement.spacedBy(bx.d(dotGap))) { c.d.streakWeek.forEach { BDot(dotSize, it, ink) } } }
        if (wide) {
            Row(Modifier.width(bx.d(iw)), verticalAlignment = Alignment.CenterVertically) { dotRow(); Flex(); BText("best ${c.d.longestStreak}", 12.0, color = Ink.g4) }
        } else dotRow()
    }
}

// ============================================================ Week
@Composable
fun WeekWidget(c: BoardCtx, w: Int, hh: Int) {
    val bx = LocalBx.current
    val measurer = rememberMeasurer()
    val pad = bx.px(12)
    val iw = w - 2 * pad
    val week = c.d.week
    val maxV = max(1, week.max())
    val barW = min(bx.px(22), (iw - 6 * bx.px(6)) / 7)
    val gap = (iw - 7 * barW) / 6
    val base = max(1, bx.px(1))
    val long = if (c.d.weekMinutes > 0) BoardData.duration(c.d.weekMinutes) else "Nothing yet"
    val short = if (c.d.weekMinutes > 0) BoardData.shortDuration(c.d.weekMinutes) else "Nothing yet"
    val besideH = hh - 2 * pad - bx.lh1(13) - bx.px(8) - bx.lh1(11) - bx.px(4)
    fun textW(t: String) = measurer.measure(AnnotatedString(t), boardStyle(bx, 13.0, bold = true), maxLines = 1, softWrap = false).size.width
    val labelW = measurer.measure(AnnotatedString("This week"), boardStyle(bx, 11.0, bold = true), maxLines = 1, softWrap = false).size.width
    val total = if (labelW + bx.px(4) + textW(long) <= iw) long else if (labelW + bx.px(4) + textW(short) <= iw) short else null
    val chartH = if (total != null) besideH else hh - 2 * pad - 2 * bx.lh1(13) - bx.px(6) - bx.px(4) - bx.lh1(11)
    val ink = BoardColour.ink("week", Ink.black)
    val mid = BoardColour.mid("week", Ink.g8)
    val trk = BoardColour.track("week", Ink.gc)
    WPad(w, hh, pad) {
        BHead(iw, "This week", if (total != null) ({ BText(total, 13.0, bold = true) }) else null, rightW = total?.let { textW(it) } ?: 0)
        if (total == null) BText(short, 13.0, bold = true, w = iw)
        VGap(if (total != null) bx.px(8) else bx.px(6))
        Row(Modifier.width(bx.d(iw)).height(bx.d(chartH - base)), horizontalArrangement = Arrangement.spacedBy(bx.d(gap)), verticalAlignment = Alignment.Bottom) {
            week.forEachIndexed { i, v ->
                val h = if (v > 0) max(bx.px(3), floor((chartH - bx.px(2)) * v / maxV.toDouble() + 0.5).toInt()) else bx.px(2)
                BRect(barW, h, if (v > 0) (if (i == 6) ink else mid) else trk)
            }
        }
        BRect(iw, base, Ink.black)
        VGap(bx.px(4))
        Row(horizontalArrangement = Arrangement.spacedBy(bx.d(gap))) {
            c.d.weekInitials.forEachIndexed { i, t ->
                BText(t, 11.0, bold = i == 6, color = if (i == 6) Ink.black else Ink.g4, w = barW, align = TextAlign.Center)
            }
        }
    }
}

// ============================================================ Goal pace
@Composable
fun GoalPaceWidget(c: BoardCtx, w: Int, hh: Int) {
    val bx = LocalBx.current
    val measurer = rememberMeasurer()
    val pad = bx.px(12)
    val iw = w - 2 * pad
    val goal = c.d.data.goalBooks
    val done = c.d.finishedYear
    val year = c.d.today.year
    val title = "$year goal"
    if (goal <= 0) {
        val f = fitTitle(measurer, bx, "Set a reading goal to track your pace.", false, false, iw, listOf(13.0, 12.0, 11.0), 4, hh - 2 * pad - bx.lh1(13) - bx.px(6))
        WPad(w, hh, pad) {
            BHead(iw, title, { Ic("target", 16, BoardColour.ink("goalpace", Ink.black), iconSw(2.25f)) })
            VGap(bx.px(6))
            BText("Set a reading goal to track your pace.", f.s, color = Ink.g4, lines = max(1, f.lines), w = iw)
        }
        return
    }
    val expected = goal * c.d.dayOfYear / 365.0
    val projected = (done * 365.0 / max(1, c.d.dayOfYear) + 0.5).toInt()
    val met = done >= goal
    val statuses = when {
        done == 0 -> listOf("First book starts it", "Starting out", "New")
        met -> listOf("Goal met · on pace for $projected", "on pace for $projected", "Goal met")
        done >= expected -> (floor(done - expected + 0.5).toInt()).let { listOf("$it ahead of pace", "$it ahead") }
        else -> (floor(expected - done + 0.5).toInt()).let { listOf("$it behind pace", "$it behind") }
    }
    val behind = !met && done > 0 && done < expected
    val ink = BoardColour.ink("goalpace", Ink.black)
    fun wOf(t: String, s: Double, b: Boolean = false) = measurer.measure(AnnotatedString(t), boardStyle(bx, s, bold = b), maxLines = 1, softWrap = false).size.width
    val numW = wOf("$done", 24.0, true)
    var pickedStatus: String? = null
    var pickedUnit = " of $goal"
    outer@ for (st in statuses) for (unit in listOf(" of $goal books", " of $goal")) {
        if (numW + wOf(unit, 13.0) + bx.px(4) + wOf(st, 12.0, behind) <= iw) { pickedStatus = st; pickedUnit = unit; break@outer }
    }
    WPad(w, hh, pad) {
        BHead(iw, title, { Ic(if (met) "trophy" else "target", 16, ink, iconSw(2.25f)) })
        Flex()
        if (pickedStatus != null) {
            Row(Modifier.width(bx.d(iw)), verticalAlignment = Alignment.CenterVertically) {
                BRuns(listOf(Run("$done", b = true, s = 24.0), Run(pickedUnit, color = Ink.g4)), 13.0)
                Flex()
                BText(pickedStatus, 12.0, bold = behind, color = if (behind) Ink.black else Ink.g4)
            }
            VGap(bx.px(4))
            GoalBar(iw, bx, done, goal, projected, met)
        } else {
            val valS = BoardData.fillSize(hh - 2 * pad - bx.lh1(13) - bx.lh1(12) - bx.px(10), bx.scale.dp, 20, 32)
            BRuns(listOf(Run("$done", b = true, s = valS.toDouble()), Run(" of $goal", color = Ink.g4)), 13.0, w = iw)
            VGap(bx.px(5))
            BText(statuses.last(), 12.0, bold = behind, color = if (behind) Ink.black else Ink.g4, w = iw)
        }
    }
}

@Composable
private fun GoalBar(iw: Int, bx: Bx, done: Int, goal: Int, projected: Int, met: Boolean) {
    val scale = max(max(goal, projected), max(done, 1))
    val goalX = floor(iw * goal / scale.toDouble() + 0.5).toInt()
    val tickW = max(1, bx.px(2))
    val one = max(1, bx.px(1))
    Box(Modifier.size(bx.d(iw), bx.dd(14))) {
        BBar(iw, bx.px(6), floor(100.0 * done / scale + 0.5), BoardColour.track("goalpace", Ink.gd), BoardColour.ink("goalpace", Ink.black), bx.px(3), Modifier.offset(y = bx.dd(4)))
        if (met) {
            Box(Modifier.offset(x = bx.d(min(iw - bx.px(2), goalX) - one), y = bx.d(-one)).size(bx.d(tickW + 2 * one), bx.d(bx.px(14) + 2 * one)).background(Ink.white).border(bx.d(one), Ink.black))
        } else {
            BRect(tickW, bx.px(14), Ink.black, modifier = Modifier.offset(x = bx.d(min(iw - bx.px(2), goalX))))
        }
    }
}

// ============================================================ Toggles
@Composable
fun TogglesWidget(c: BoardCtx, w: Int, hh: Int) {
    val bx = LocalBx.current
    val pad = bx.px(10)
    val iw = w - 2 * pad
    val ih = hh - 2 * pad
    val small = w < bx.px(200)
    val onCol = BoardColour.ink("toggles", Ink.black)
    // Wi-Fi, Light, Night and Sync, as the plugin draws them with a frontlight.
    val items = listOf(Triple("wifi", c.online, "wifi"), Triple("sun", false, "light"), Triple("moon", false, "night"), Triple("refresh-cw", false, "sync"))
    val labels = mapOf("wifi" to "Wi-Fi", "light" to "Light", "night" to "Night", "sync" to "Sync")
    @Composable fun btn(size: Int, it: Triple<String, Boolean, String>) {
        val on = it.second
        BCircle(size, bg = if (on) onCol else Ink.white, border = if (on) 0 else max(1, bx.px(1.5)), modifier = Modifier.tapOrHold({ c.onToggle(it.third) })) {
            IcPx(it.first, floor(size * 0.46 + 0.5).toInt(), if (on) Ink.white else Ink.black, 2.25f)
        }
    }
    if (small) {
        val bd = min((iw - bx.px(8)) / 2, (ih - bx.px(8)) / 2)
        Column(Modifier.size(bx.d(w), bx.d(hh)), horizontalAlignment = Alignment.CenterHorizontally) {
            Flex()
            Row(horizontalArrangement = Arrangement.spacedBy(bx.dd(8))) { btn(bd, items[0]); btn(bd, items[1]) }
            VGap(bx.px(8))
            Row(horizontalArrangement = Arrangement.spacedBy(bx.dd(8))) { btn(bd, items[2]); btn(bd, items[3]) }
            Flex()
        }
    } else {
        val cw = iw / items.size
        val bd = min(bx.px(44), min(ih - bx.lh1(12) - bx.px(6), cw - bx.px(8)))
        Row(Modifier.size(bx.d(w), bx.d(hh)).padding(start = bx.d(pad)), verticalAlignment = Alignment.CenterVertically) {
            items.forEach {
                Column(Modifier.width(bx.d(cw)), horizontalAlignment = Alignment.CenterHorizontally) {
                    btn(bd, it)
                    VGap(bx.px(5))
                    BText(labels.getValue(it.third), 12.0, bold = it.second, w = cw, align = TextAlign.Center)
                }
            }
        }
    }
}

// ============================================================ Also reading
@Composable
fun AlsoWidget(c: BoardCtx, w: Int, hh: Int) {
    val bx = LocalBx.current
    val pad = bx.px(12)
    val iw = w - 2 * pad
    val books = c.d.data.alsoReading
    val avail = hh - 2 * pad - bx.lh1(13) - bx.px(6)
    val n = max(0, min(books.size, avail / bx.px(46)))
    val rowH = if (n > 0) min(bx.px(64), avail / n) else bx.px(56)
    val coverH = min(bx.px(44), rowH - bx.px(8))
    val coverW = (coverH * 2 / 3.0 + 0.5).toInt()
    val tw = iw - coverW - bx.px(10)
    val ink = BoardColour.ink("also", Ink.black)
    val trk = BoardColour.track("also", Ink.gc)
    val measurer = rememberMeasurer()
    WPad(w, hh, pad) {
        BHead(iw, "Also reading", { BHeadAction("All ${c.d.data.continueReading.size}") { c.onLink("in-progress") } }, rightW = headActionW(bx, measurer, "All ${c.d.data.continueReading.size}"))
        VGap(bx.px(6))
        if (n == 0) BText("Books you start show up here.", 13.0, color = Ink.g4, w = iw, lines = 3)
        for (i in 0 until n) {
            val b = books[i]
            if (i > 0) BRect(iw, thin(bx), Ink.gc)
            val pctText = BoardData.pctLabel(b.progress)
            val rightW = max(bx.px(38), measurer.measure(AnnotatedString(pctText), boardStyle(bx, 12.0, bold = true), maxLines = 1, softWrap = false).size.width)
            Row(
                Modifier.width(bx.d(iw)).height(bx.d(rowH - if (i > 0) thin(bx) else 0)).tapOrHold({ c.onOpen(b) }, { c.onInfo(b) }),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BCover(b, coverW, coverH, thin(bx))
                HGap(bx.px(10))
                Column(Modifier.width(bx.d(tw))) {
                    BText(b.title, 14.0, bold = true, w = tw)
                    VGap(bx.px(5))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BBar(tw - rightW - bx.px(8), bx.px(4), b.progress, trk, ink, bx.px(2))
                        HGap(bx.px(8))
                        Row(Modifier.width(bx.d(rightW)), horizontalArrangement = Arrangement.End) { BText(pctText, 12.0, bold = true) }
                    }
                }
            }
        }
    }
}

// ============================================================ Highlight of the day
@Composable
fun HighlightWidget(c: BoardCtx, w: Int, hh: Int) {
    val bx = LocalBx.current
    val measurer = rememberMeasurer()
    val hl = c.d.data.highlight
    val pad = bx.px(14)
    val iw = w - 2 * pad
    val deep = BoardColour.art("highlight", Color.Black, "deep")
    if (hl == null) {
        val str = "Your highlights come back here, one a day."
        val room = hh - 2 * pad - bx.px(20) - bx.px(8)
        val f = fitTitle(measurer, bx, str, false, true, iw, listOf(15.0, 14.0, 13.0, 12.0), 4, room)
        WPad(w, hh, pad) {
            Ic("quote", 20, deep, 1f, fill = true)
            VGap(bx.px(8))
            BText(str, f.s, italic = true, serif = true, color = Ink.g3, lines = max(1, f.lines), w = iw)
        }
        return
    }
    val tall = hh > bx.px(200)
    val p3 = if (hh < bx.px(130)) bx.px(12) else pad
    val iw3 = w - 2 * p3
    val attrH = bx.lh1(12) + if (tall) bx.px(2) + bx.lh1(12) else 0
    val qs = if (hh < bx.px(130)) 14.0 else 15.0
    val budget = hh - 2 * p3 - bx.lh1(12) - bx.px(6) - bx.px(6) - attrH
    val lines = max(1, budget / bx.lh(qs))
    WPad(w, hh, p3) {
        Row(Modifier.width(bx.d(iw3)).height(bx.d(bx.lh1(12))), verticalAlignment = Alignment.CenterVertically) {
            Ic("quote", 14, deep, 1f, fill = true)
            HGap(bx.px(6))
            BText("Highlight of the day", 12.0, bold = true, color = Ink.g4)
        }
        VGap(bx.px(6))
        BText(hl.quote, qs, italic = true, serif = true, color = Ink.g1, lines = lines, w = iw3)
        Flex()
        BText(hl.book?.title.orEmpty(), 12.0, bold = true, w = iw3)
    }
}

// ============================================================ Shelf
private val SHELF_TITLES = mapOf(
    "want-to-read" to "Want to read", "up-next-in-series" to "Up next in series", "random" to "Discover", "recently-added" to "Recently added",
    "in-progress" to "In progress", "pick-back-up" to "Pick back up", "finished" to "Finished this year"
)
private val SHELF_EMPTY = mapOf(
    "want-to-read" to "Books you have not started wait here.", "pick-back-up" to "Books you put down a while ago show up here.",
    "finished" to "Books you finish this year gather here.", "up-next-in-series" to "The next book of a series you started shows up here."
)

fun shelfTitle(kind: String?, year: Int): String = if (kind == "finished") "Finished in $year" else SHELF_TITLES[kind] ?: "Shelf"

@Composable
fun ShelfWidget(c: BoardCtx, w: Int, hh: Int, tile: Tile, cols: Int) {
    val bx = LocalBx.current
    val pad = bx.px(12)
    val iw = w - 2 * pad
    val kind = tile.kind ?: "random"
    val books = c.d.shelf(kind)
    val titleSize = 11
    val capH = bx.px(6) + bx.lh1(titleSize)
    var coverH = max(1, hh - 2 * pad - bx.lh1(13) - bx.px(10) - capH)
    var coverW = min(iw, (coverH * 2 / 3.0 + 0.5).toInt())
    coverH = min(coverH, (coverW * 1.5 + 0.5).toInt())
    val minGap = bx.px(12)
    val fullWidth = tile.cw >= cols
    val maxBooks = if (!fullWidth && tile.cw <= 2) 3 else Int.MAX_VALUE
    var n = min(maxBooks, max(1, (iw + minGap) / (coverW + minGap)))
    if (n < maxBooks && iw - (n * coverW + (n - 1) * minGap) > coverW * 0.25) {
        n += 1
        coverW = (iw - (n - 1) * minGap) / n
        coverH = min(coverH, (coverW * 1.5 + 0.5).toInt())
    }
    val gap = if (n > 1) (iw - n * coverW) / (n - 1) else 0
    val title = shelfTitle(kind, c.d.today.year)
    val iconCol = BoardColour.art(tile.key, Color.Black)
    val right: (@Composable () -> Unit)? = when {
        kind == "random" -> ({ Box(Modifier.width(bx.dd(36)).height(bx.d(bx.lh1(13))).tapOrHold({ c.onShuffle() }), contentAlignment = Alignment.CenterEnd) { Ic("shuffle", 17, iconCol, iconSw(2.25f)) } })
        books.isEmpty() -> null
        kind == "finished" -> ({ BHeadAction("${c.d.finishedYear}") { c.onLink("shelf:finished") } })
        kind == "want-to-read" -> null
        else -> ({ BHeadAction("See all") { c.onLink("shelf:$kind") } })
    }
    WPad(w, hh, pad) {
        BHead(iw, title, right, rightW = if (kind == "random") bx.px(36) else if (kind == "want-to-read" || books.isEmpty()) 0 else headActionW(bx, rememberMeasurer(), if (kind == "finished") "${c.d.finishedYear}" else "See all"))
        if (books.isEmpty()) {
            VGap(bx.px(8))
            BText(SHELF_EMPTY[kind] ?: "Nothing here yet.", 13.0, color = Ink.g4, lines = 3, w = iw)
        } else {
            VGap(bx.px(10))
            Row(horizontalArrangement = Arrangement.spacedBy(bx.d(gap)), verticalAlignment = Alignment.Top) {
                books.take(n).forEach { b ->
                    Column(Modifier.width(bx.d(coverW)).tapOrHold({ c.onOpen(b) }, { c.onInfo(b) })) {
                        BCover(b, coverW, coverH)
                        VGap(bx.px(6))
                        BText(b.title, titleSize.toDouble(), bold = true, w = coverW)
                    }
                }
            }
        }
    }
}

// ============================================================ Links
private data class LinkSpec(val icon: String, val label: String, val unit: (Int) -> String)

private val LINKS = mapOf(
    "library" to LinkSpec("book-copy", "Library") { "books" },
    "series" to LinkSpec("layers-2", "Series") { "series" },
    "authors" to LinkSpec("feather", "Authors") { "authors" },
    "collections" to LinkSpec("folder-open", "Collections") { if (it == 1) "collection" else "collections" },
    "scopes" to LinkSpec("circle-check", "Finished") { "books" },
    "device" to LinkSpec("tablet", "On device") { if (it == 1) "book here" else "books here" },
    "search" to LinkSpec("search", "Search") { "" }
)

private fun fanWidth(coverH: Int, count: Int): Int {
    val cw = (coverH * 2 / 3.0 + 0.5).toInt()
    return cw + (cw * 0.6 + 0.5).toInt() * (min(3, count) - 1)
}

@Composable
private fun Fan(books: List<RBook>, coverH: Int, ring: Int) {
    val bx = LocalBx.current
    val cw = (coverH * 2 / 3.0 + 0.5).toInt()
    val step = (cw * 0.6 + 0.5).toInt()
    val list = books.take(3)
    if (list.isEmpty()) { Spacer(Modifier.size(bx.d(1), bx.d(coverH))); return }
    val width = cw + step * (list.size - 1)
    Box(Modifier.size(bx.d(width), bx.d(coverH))) {
        for (i in list.indices.reversed()) {
            val x = (list.size - 1 - i) * step
            Box(Modifier.offset(x = bx.d(x - ring), y = bx.d(-ring)).size(bx.d(cw + 2 * ring), bx.d(coverH + 2 * ring)).background(Ink.white))
            Box(Modifier.offset(x = bx.d(x))) { BCover(list[i], cw, coverH, thin(bx), Ink.g9) }
        }
    }
}

@Composable
fun LinkWidget(c: BoardCtx, w: Int, hh: Int, tile: Tile) {
    val bx = LocalBx.current
    val measurer = rememberMeasurer()
    val kind = tile.kind ?: "library"
    val spec = LINKS[kind] ?: LINKS.getValue("library")
    val pad = bx.px(12)
    val iw = w - 2 * pad
    val ih = hh - 2 * pad
    val n = when (kind) {
        "library" -> c.d.data.totalBooks; "series" -> c.d.data.seriesCount; "authors" -> c.d.data.authorCount
        "collections" -> c.d.folders.size; "scopes" -> c.d.finished.size; "device" -> c.d.data.totalBooks; else -> 0
    }
    val unit = spec.unit(n)
    val iconCol = BoardColour.icon(tile.key)
    fun wOf(t: String, s: Double, b: Boolean = false) = measurer.measure(AnnotatedString(t), boardStyle(bx, s, bold = b), maxLines = 1, softWrap = false).size.width
    // The name stays whole: the chevron gives way first, then the icon, then the name steps down a size or two.
    val need0 = wOf(spec.label, 13.0, true)
    val iconW = bx.px(15) + bx.px(5)
    var chevron = w >= bx.px(150)
    val cwid = if (chevron) bx.px(14) + bx.px(5) else 0
    if (chevron && need0 > iw - iconW - cwid) chevron = false
    val withIcon = need0 <= iw - iconW
    val labW = iw - (if (withIcon) iconW else 0) - (if (chevron) cwid else 0)
    var size = 13
    while (wOf(spec.label, size.toDouble(), true) > labW && size > 11) size--
    val room = ih - bx.lh1(13)
    val valS = BoardData.fillSize(room - bx.lh1(11) - bx.px(4), bx.scale.dp, 18, 30)
    @Composable fun head() {
        Row(Modifier.width(bx.d(iw)).height(bx.d(bx.lh1(13))), verticalAlignment = Alignment.CenterVertically) {
            if (withIcon) { Ic(spec.icon, 15, iconCol, iconSw(2.25f)); HGap(bx.px(5)) }
            Box(Modifier.weight(1f)) { BText(spec.label, size.toDouble(), bold = true, w = labW) }
            if (chevron) { HGap(bx.px(5)); Ic("chevron-right", 14, Ink.g6, 2.5f) }
        }
    }
    @Composable fun count(extraCap: Int = 24) {
        var cs = min(valS, extraCap)
        fun runsW(s: Int) = wOf(BoardData.count(n), s.toDouble(), true) + wOf("  $unit", 11.0)
        while (cs > 16 && runsW(cs) > iw) cs -= 2
        BRuns(listOf(Run(BoardData.count(n), b = true, s = cs.toDouble()), Run("  $unit", color = Ink.g4)), 11.0, w = iw)
    }
    WPad(w, hh, pad) {
        head()
        Flex()
        when (kind) {
            "device" -> {
                count()
                c.storage?.let { st ->
                    val label2 = "%.1f GB free".format(java.util.Locale.ENGLISH, st.freeGb).replace(".0 GB", " GB")
                    val used = floor(100 * (st.totalGb - st.freeGb) / st.totalGb + 0.5)
                    VGap(bx.px(5))
                    val l2w = wOf(label2, 11.0)
                    Row(Modifier.width(bx.d(iw)), verticalAlignment = Alignment.CenterVertically) {
                        BBar(iw - l2w - bx.px(8), bx.px(6), used, BoardColour.track("link:device", Ink.gd), BoardColour.ink("link:device", Ink.black), bx.px(3))
                        HGap(bx.px(8)); BText(label2, 11.0, color = Ink.g4)
                    }
                }
            }
            "search" -> {
                val border = max(1, bx.px(1.25))
                val fieldH = min(bx.px(34), room - bx.px(4))
                Row(
                    Modifier.width(bx.d(iw)).height(bx.d(fieldH)).clip(RoundedCornerShape(bx.d(fieldH / 2))).border(bx.d(border), Ink.g6, RoundedCornerShape(bx.d(fieldH / 2))),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HGap(bx.px(10))
                    IcPx("search", min(bx.px(14), fieldH - 2 * border), Ink.g4, 2.5f)
                    HGap(bx.px(6))
                    BText("Title, author", 12.0, color = Ink.g4)
                }
            }
            "authors" -> {
                count()
                val names = c.d.data.books.mapNotNull { b -> b.author.takeIf { it.isNotBlank() }?.let { a -> a.trim().split(Regex("\\s+")).last() } }.distinct().take(4)
                if (names.isNotEmpty()) {
                    VGap(bx.px(4))
                    BText(names.joinToString(", "), 12.0, italic = true, serif = true, color = Ink.g3, w = iw)
                }
            }
            else -> {
                val books = when (kind) {
                    "series" -> c.d.upNext.ifEmpty { c.d.data.books.filter { it.series != null } }
                    "scopes" -> c.d.finished.ifEmpty { c.d.data.books.filter { it.finishedAt != null } }
                    else -> c.d.data.recentlyAdded
                }
                val countW = max(wOf(BoardData.count(n), valS.toDouble(), true), wOf(unit, 11.0))
                val fanRoom = iw - countW - bx.px(8)
                var fh = max(bx.px(30), min(bx.px(66), min(room - bx.px(6), (fanRoom / 2.2 * 1.5).toInt())))
                val shown = books.take(3).toMutableList()
                while (fh > bx.px(30) && shown.isNotEmpty() && fanWidth(fh, shown.size) > fanRoom) fh--
                while (shown.isNotEmpty() && fanWidth(fh, shown.size) > fanRoom) shown.removeAt(shown.lastIndex)
                Row(Modifier.width(bx.d(iw)), verticalAlignment = Alignment.Bottom) {
                    Column {
                        BText(BoardData.count(n), valS.toDouble(), bold = true)
                        BText(unit, 11.0, color = Ink.g4)
                    }
                    Flex()
                    Fan(shown, fh, max(1, bx.px(1.5)))
                }
            }
        }
    }
}

// ============================================================ Sync
@Composable
fun SyncWidget(c: BoardCtx, w: Int, hh: Int) {
    val bx = LocalBx.current
    val pad = bx.px(12)
    val iw = w - 2 * pad
    val ink = BoardColour.icon("sync")
    val whenText = c.lastSyncMs?.let { relative(c.nowMs - it) } ?: "not yet"
    val title = if (c.lastSyncMs != null) "Synced" else "Sync"
    WPad(w, hh, pad) {
        BHead(iw, title, { Ic("cloud-check", 17, ink, iconSw(2.25f)) })
        Flex()
        BText(whenText, 16.0, bold = true, w = iw)
        VGap(bx.px(6))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Ic("refresh-cw", 14, ink, iconSw(2.5f)); HGap(bx.px(5)); BText("Sync now", 12.0, bold = true)
        }
    }
}

private fun relative(deltaMs: Long): String {
    val s = deltaMs / 1000
    return when {
        s < 60 -> "just now"
        s < 3600 -> "${s / 60} min ago"
        s < 86400 -> "${s / 3600} h ago"
        else -> "${s / 86400} d ago"
    }
}

// ============================================================ Clock (device)
@Composable
fun ClockWidget(c: BoardCtx, w: Int, hh: Int) {
    val bx = LocalBx.current
    val measurer = rememberMeasurer()
    val pad = bx.px(12)
    val iw = w - 2 * pad
    var valS = BoardData.fillSize(hh - 2 * pad - bx.lh1(13) * 2 - bx.px(6), bx.scale.dp, 20, 40)
    val showDate = hh - 2 * pad >= bx.lh1(20) + bx.lh1(13) * 2
    while (valS > 20 && measurer.measure(AnnotatedString(c.clock), boardStyle(bx, valS.toDouble(), bold = true), maxLines = 1, softWrap = false).size.width > iw) valS -= 2
    WPad(w, hh, pad) {
        BText(c.clock, valS.toDouble(), bold = true, color = Ink.white, w = iw)
        Flex()
        if (showDate) {
            BText(c.weekday, 13.0, bold = true, color = Ink.white, w = iw)
            BText(c.dayMonth, 13.0, color = Ink.gc, w = iw)
        } else BText(c.dayMonth, 13.0, bold = true, color = Ink.white, w = iw)
    }
}
