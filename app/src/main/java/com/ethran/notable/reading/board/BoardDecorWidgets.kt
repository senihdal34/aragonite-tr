package com.ethran.notable.reading.board

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import com.ethran.notable.R
import com.ethran.notable.reading.BuddyPose
import com.ethran.notable.reading.MoonPhase
import com.ethran.notable.reading.ReadingDecor
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** The same sequence of numbers as the design mock's generator (xorshift), so a seed draws the same stars and wobble. */
class BoardRng(seed: Int) {
    private var s: Int = ((seed.toLong() * 2654435761L) % 4294967296L).toInt().let { if (it == 0) 1 else it }
    fun next(): Double {
        s = s xor (s shl 13)
        s = s xor (s ushr 17)
        s = s xor (s shl 5)
        return (s.toLong() and 0xFFFFFFFFL) / 4294967296.0
    }
}

// ============================================================ shared
@Composable
private fun HintTile(w: Int, hh: Int, title: String, hint: String, right: (@Composable () -> Unit)? = null) {
    val bx = LocalBx.current
    val pad = bx.px(12)
    val iw = w - 2 * pad
    WPad(w, hh, pad) {
        BHead(iw, title, right)
        VGap(bx.px(6))
        BText(hint, 13.0, color = Ink.g4, lines = 3, w = iw)
    }
}

// ============================================================ badge / dust (the chapter line is in BoardChaptersWidget.kt)
@Composable
fun BadgeWidget(c: BoardCtx, w: Int, hh: Int) {
    HintTile(w, hh, "Next badge", "Badges are not available in this app.", { Ic("award", 17, BoardColour.icon("badge"), iconSw(2.25f)) })
}

/** Dust over the cover and a cobweb across its top corner, each thread drawn dark and then light so it shows on any cover. */
private fun DrawScope.drawDust(cw: Float, ch: Float, seed: Int, bx: Bx) {
    drawRect(Color.White.copy(alpha = 0.22f), Offset.Zero, Size(cw, ch))
    val rng = BoardRng(seed)
    val count = Math.round(cw * ch / (bx.px(5.5) * bx.px(5.5).toFloat()))
    repeat(count) {
        val x = (rng.next() * cw).toFloat(); val y = (rng.next() * ch).toFloat()
        val rr = max(0.6f, bx.px(0.35 + rng.next() * 0.8).toFloat())
        drawCircle(if (rng.next() > 0.5) Color(0xFF999999) else Color(0xFFDDDDDD), rr, Offset(x, y))
    }
    val len = min(cw, ch) * 0.62f
    val ox = cw; val oy = 0f
    val angs = listOf(0.5, 0.78, 1.05, 1.32).map { PI / 2 + it * PI / 2 * 0.95 }
    val threads = ArrayList<Path>()
    for (a in angs) threads += Path().apply { moveTo(ox, oy); lineTo((ox + len * cos(a)).toFloat(), (oy + len * sin(a)).toFloat()) }
    for (k in listOf(0.3, 0.5, 0.7, 0.9)) {
        val p = Path()
        angs.forEachIndexed { i, a ->
            val x = (ox + len * k * cos(a)).toFloat(); val y = (oy + len * k * sin(a)).toFloat()
            if (i == 0) p.moveTo(x, y) else {
                val pa = angs[i - 1]
                p.quadraticTo((ox + len * k * 0.86 * cos((a + pa) / 2)).toFloat(), (oy + len * k * 0.86 * sin((a + pa) / 2)).toFloat(), x, y)
            }
        }
        threads += p
    }
    val tw = max(1, bx.px(0.8)).toFloat()
    for (t in threads) drawPath(t, Color(0xFF333333), style = Stroke(tw + bx.px(1), cap = StrokeCap.Round))
    for (t in threads) drawPath(t, Color.White, style = Stroke(tw, cap = StrokeCap.Round))
    // A small spider let down on a thread.
    val sx = (ox + len * 0.62 * cos(angs[1])).toFloat(); val sy0 = (oy + len * 0.62 * sin(angs[1])).toFloat(); val sy = sy0 + ch * 0.2f
    drawLine(Color(0xFF333333), Offset(sx, sy0), Offset(sx, sy), strokeWidth = tw)
    val br = bx.px(3.2).toFloat()
    drawCircle(Color.White, br + bx.px(1), Offset(sx, sy + br * 0.35f)); drawCircle(Color.Black, br, Offset(sx, sy + br * 0.35f))
    drawCircle(Color.Black, br * 0.6f, Offset(sx, sy - br * 0.85f))
}

@Composable
fun DustWidget(c: BoardCtx, w: Int, hh: Int) {
    val bx = LocalBx.current
    val pad = bx.px(12)
    val iw = w - 2 * pad
    val ih = hh - 2 * pad
    val book = c.d.dust(c.dustSkip)
    if (book == null) {
        HintTile(w, hh, "Gathering dust", "Books you download and never open collect dust here.")
        return
    }
    val days = max(1L, (c.nowMs - book.addedAt) / 86_400_000L).toInt()
    val months = days / 30
    val since = if (months >= 1) "Never opened in $months ${if (months == 1) "month" else "months"}" else "Never opened in $days ${if (days == 1) "day" else "days"}"
    val coverH = ih
    val coverW = Math.round(ih * 2 / 3f)
    val tw = iw - coverW - bx.px(12)
    val seed = book.id.hashCode()
    WPad(w, hh, pad) {
        Row(Modifier.width(bx.d(iw)).height(bx.d(ih)), horizontalArrangement = Arrangement.spacedBy(bx.dd(12))) {
            Box(Modifier.tapOrHold({ c.onOpen(book) }, { c.onInfo(book) })) {
                BCover(book, coverW, coverH, max(1, bx.px(1)), Ink.gb)
                Canvas(Modifier.size(bx.d(coverW), bx.d(coverH))) { drawDust(coverW.toFloat(), coverH.toFloat(), seed, bx) }
            }
            Column(Modifier.width(bx.d(tw)).height(bx.d(ih))) {
                BText("Gathering dust", 12.0, color = Ink.g4, w = tw)
                BText(book.title, 14.0, bold = true, serif = true, lines = 2, w = tw)
                BText(since, 12.0, color = Ink.g4, lines = 2, w = tw)
                Flex()
                Row(horizontalArrangement = Arrangement.spacedBy(bx.dd(8)), verticalAlignment = Alignment.CenterVertically) {
                    val h = bx.px(30)
                    Row(
                        Modifier.height(bx.d(h)).clip(RoundedCornerShape(bx.d(h / 2))).background(Ink.black).tapOrHold({ c.onOpen(book) }),
                        verticalAlignment = Alignment.CenterVertically
                    ) { HGap(bx.px(10)); Ic("book-open", 15, Ink.white, 2.5f); HGap(bx.px(6)); BText("Open", 13.0, bold = true, color = Ink.white); HGap(bx.px(12)) }
                    BCircle(h, bg = Ink.white, border = max(1, bx.px(1.5)), modifier = Modifier.tapOrHold({ c.onDustNext() })) { Ic("arrow-right", 15, Ink.black, 2.25f) }
                }
            }
        }
    }
}

// ============================================================ buddy (pixel cat)
class BuddySprites(val palette: Map<Char, Int>, val poses: Map<String, List<String>>)

private var spriteCache: BuddySprites? = null

fun loadBuddy(context: Context): BuddySprites = spriteCache ?: run {
    val palette = HashMap<Char, Int>()
    val poses = LinkedHashMap<String, MutableList<String>>()
    var cur: MutableList<String>? = null
    context.resources.openRawResource(R.raw.buddy_cat).bufferedReader().useLines { lines ->
        for (line in lines) {
            when {
                line.startsWith("P ") -> { val p = line.split(' '); palette[p[1].toInt().toChar()] = 0xFF000000.toInt() or p[2].toInt(16) }
                line.startsWith("S ") -> { cur = ArrayList<String>().also { poses[line.substring(2).trim()] = it } }
                else -> cur?.add(line)
            }
        }
    }
    BuddySprites(palette, poses).also { spriteCache = it }
}

private fun edge(i: Int, k: Double) = floor(i * k + 1e-6).toInt()

/** The sprite drawn as runs of solid rectangles at scale [k] (whole or half steps). */
@Composable
fun SpriteView(rows: List<String>, palette: Map<Char, Int>, k: Double) {
    val bx = LocalBx.current
    val wpx = edge(rows[0].length, k)
    val hpx = edge(rows.size, k)
    Canvas(Modifier.size(bx.d(wpx), bx.d(hpx))) {
        rows.forEachIndexed { r, line ->
            var col = 0
            while (col < line.length) {
                val ch = line[col]
                var end = col
                while (end + 1 < line.length && line[end + 1] == ch) end++
                val argb = palette[ch]
                if (ch != ' ' && argb != null) {
                    val left = edge(col, k); val top = edge(r, k)
                    drawRect(Color(argb), Offset(left.toFloat(), top.toFloat()), Size((edge(end + 1, k) - left).toFloat(), (edge(r + 1, k) - top).toFloat()))
                }
                col = end + 1
            }
        }
    }
}

private fun buddyKey(p: BuddyPose) = p.name.lowercase()

/** What the buddy says, in the plugin's wording. */
fun buddyLines(c: BoardCtx, pose: BuddyPose): List<String> {
    val streak = c.d.streak; val best = c.d.longestStreak; val today = c.d.todayMinutes
    return when (pose) {
        BuddyPose.Read -> listOf("Reading with you", "${BoardData.duration(today)} today")
        BuddyPose.Sleep -> listOf("Fast asleep", "Wakes up when you read")
        BuddyPose.Party -> listOf("$streak ${if (streak == 1) "day" else "days"} in a row!", if (streak >= best) "A new best" else "Your best is $best")
        BuddyPose.Ribbon -> listOf("Book finished!", c.d.data.books.firstOrNull { it.finishedAt != null }?.title.orEmpty())
        BuddyPose.Yawn -> listOf("Past bedtime", "${BoardData.duration(today)} read today")
        BuddyPose.Wait -> listOf("Ready when you are", "Open a book to start")
    }
}

/** A speech bubble whose tail points at the companion on the left. [w] includes the tail. */
@Composable
private fun Bubble(w: Int, lines: List<String>, s: Double) {
    val bx = LocalBx.current
    val bw = max(1, bx.px(1.5))
    val padX = bx.px(10); val padY = bx.px(7)
    val t = bx.px(9)
    val frameW = w - (t - bw)
    val tw = frameW - 2 * padX - 2 * bw
    Row(verticalAlignment = Alignment.Top) {
        Box(Modifier.width(bx.d(t - bw)).height(bx.d(bx.px(14) + 2 * t))) {
            Canvas(Modifier.offset(y = bx.d(bx.px(14))).size(bx.d(t), bx.d(2 * t))) {
                val p = Path().apply { moveTo(t.toFloat(), 0f); lineTo(0f, t.toFloat()); lineTo(t.toFloat(), 2f * t) }
                drawPath(p, Ink.white)
                drawPath(p, Ink.black, style = Stroke(bw.toFloat(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawRect(Ink.white, Offset((t - bw).toFloat(), bw.toFloat()), Size(bw.toFloat(), (2 * t - 2 * bw).toFloat()))
            }
        }
        Column(
            Modifier.width(bx.d(frameW)).clip(RoundedCornerShape(bx.dd(10))).background(Ink.white).border(bx.d(bw), Ink.black, RoundedCornerShape(bx.dd(10)))
                .padding(horizontal = bx.d(padX), vertical = bx.d(padY))
        ) {
            BText(lines[0], s, bold = true, lines = 2, w = tw)
            if (lines.size > 1 && lines[1].isNotEmpty()) {
                VGap(bx.px(2))
                BText(lines[1], max(11.0, s - 2), color = Ink.g4, lines = 2, w = tw)
            }
        }
    }
}

@Composable
fun BuddyWidget(c: BoardCtx, w: Int, hh: Int) {
    val bx = LocalBx.current
    val ctx = LocalContext.current
    val pad = bx.px(12)
    val iw = w - 2 * pad
    val ih = hh - 2 * pad
    val sprites = remember { loadBuddy(ctx) }
    val d = c.d.data
    val finishedToday = d.books.any { b -> b.finishedAt?.let { com.ethran.notable.reading.ReadingStats.dayOf(it, java.time.ZoneId.systemDefault()) == c.d.today } == true }
    val pose = ReadingDecor.buddyPose(d.continueReading.isNotEmpty(), c.d.streak, c.d.longestStreak, c.d.todayMinutes, finishedToday, java.time.LocalTime.now().hour)
    val rows = sprites.poses[buddyKey(pose)] ?: sprites.poses.values.first()
    val lines = buddyLines(c, pose)
    val sw0 = rows[0].length; val sh0 = rows.size
    fun scaleFor(aw: Double, ah: Double) = max(1.0, floor(min(aw / sw0, ah / sh0) * 2) / 2)
    val wide = iw > ih * 1.6
    WPad(w, hh, pad) {
        if (wide) {
            val k = scaleFor(iw * 0.42, ih.toDouble())
            val spriteW = edge(sw0, k)
            val gap = bx.px(14)
            val bubbleW = iw - spriteW - gap
            Row(Modifier.width(bx.d(iw)).height(bx.d(ih)), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.width(bx.d(spriteW)).fillMaxHeight()) { Flex(); SpriteView(rows, sprites.palette, k) }
                HGap(gap - (bx.px(9) - max(1, bx.px(1.5))))
                Bubble(bubbleW + (bx.px(9) - max(1, bx.px(1.5))), lines, 14.0)
            }
        } else {
            val k = scaleFor(iw * 0.6, (ih - bx.lh1(12) - bx.px(4)).toDouble())
            Column(Modifier.width(bx.d(iw)).height(bx.d(ih)), horizontalAlignment = Alignment.CenterHorizontally) {
                Flex(); SpriteView(rows, sprites.palette, k); VGap(bx.px(4))
                BText(lines[0], 12.0, bold = true, w = iw, align = TextAlign.Center); Flex()
            }
        }
    }
}

// ============================================================ analog clock
@Composable
fun AnalogWidget(c: BoardCtx, w: Int, hh: Int) {
    val bx = LocalBx.current
    val pad = bx.px(12)
    val iw = w - 2 * pad
    val ih = hh - 2 * pad
    val accent = BoardColour.art("analog", Color.Black, "ink").let { if (BoardColour.enabled) BoardColour.color(BoardColour.fam("red").ink) else Ink.black }
    val now = java.time.LocalTime.now()
    val dia = max(1, min(iw, ih))
    Box(Modifier.size(bx.d(w), bx.d(hh)), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(bx.d(dia))) {
            val r = dia / 2f
            val cx = r; val cy = r
            drawCircle(Ink.white, r - 0.5f, Offset(cx, cy))
            for (i in 0..11) {
                val a = i * PI / 6
                val major = i % 3 == 0
                val r1 = r - bx.px(if (major) 4 else 5)
                val r2 = r - (if (major) r * 0.2 else r * 0.12) - bx.px(3)
                drawLine(
                    if (major) accent else Ink.g5, Offset((cx + r1 * sin(a)).toFloat(), (cy - r1 * cos(a)).toFloat()),
                    Offset((cx + r2 * sin(a)).toFloat(), (cy - r2 * cos(a)).toFloat()),
                    strokeWidth = (if (major) max(2, bx.px(2.5)) else max(1, bx.px(1.25))).toFloat(), cap = StrokeCap.Round
                )
            }
            val ah = ((now.hour % 12) + now.minute / 60.0) * PI / 6
            val am = now.minute * PI / 30
            fun hand(a: Double, len: Double, width: Float, tail: Double) = drawLine(
                Ink.black, Offset((cx - tail * sin(a)).toFloat(), (cy + tail * cos(a)).toFloat()), Offset((cx + len * sin(a)).toFloat(), (cy - len * cos(a)).toFloat()),
                strokeWidth = width, cap = StrokeCap.Round
            )
            hand(ah, r * 0.5, max(3, bx.px(4.5)).toFloat(), r * 0.08)
            hand(am, r * 0.76, max(2, bx.px(2.75)).toFloat(), r * 0.1)
            drawCircle(accent, max(3, bx.px(4)).toFloat(), Offset(cx, cy))
            drawCircle(Ink.white, max(1, bx.px(1.25)).toFloat(), Offset(cx, cy))
        }
    }
}

// ============================================================ moon
private val MARIA = listOf(
    doubleArrayOf(-0.26, -0.36, 0.24, 0.17, -0.3), doubleArrayOf(0.2, -0.3, 0.13, 0.12, 0.0), doubleArrayOf(0.32, -0.02, 0.15, 0.12, 0.4),
    doubleArrayOf(-0.5, 0.06, 0.15, 0.3, 0.1), doubleArrayOf(-0.08, 0.42, 0.15, 0.09, 0.2)
)

private fun isLit(p: Double, x: Double, y: Double): Boolean {
    if (x * x + y * y > 1) return false
    val k = cos(2 * PI * p)
    val tx = k * sqrt(max(0.0, 1 - y * y))
    return if (p < 0.5) x > tx else x < -tx
}

private fun DrawScope.drawMoon(dia: Float, m: MoonPhase, bx: Bx) {
    val r = dia / 2 - max(1, bx.px(1))
    val cx = dia / 2; val cy = dia / 2
    val night = BoardColour.enabled
    drawCircle(if (night) Color(0xFF2A3062) else Ink.g2, r, Offset(cx, cy))
    drawCircle(if (night) Color(0xFF5A628F) else Ink.g6, r, Offset(cx, cy), style = Stroke(max(1, bx.px(1)).toFloat()))
    if (m.litPercent / 100.0 > 0.004) {
        val k = cos(2 * PI * m.phase)
        val waxing = m.phase < 0.5
        val path = Path()
        val steps = 72
        for (i in 0..steps) {
            val y = -1.0 + 2.0 * i / steps
            val s = sqrt(max(0.0, 1 - y * y))
            val x = if (waxing) s else -s
            val px = (cx + x * r).toFloat(); val py = (cy + y * r).toFloat()
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        for (i in steps downTo 0) {
            val y = -1.0 + 2.0 * i / steps
            val s = sqrt(max(0.0, 1 - y * y))
            val x = if (waxing) k * s else -k * s
            path.lineTo((cx + x * r).toFloat(), (cy + y * r).toFloat())
        }
        path.close()
        drawPath(path, if (night) Color(0xFFF7EFCF) else Ink.white)
        for (mare in MARIA) {
            val (mx, my, ex, ey, rot) = mare.toList()
            var ok = true
            for (i in 0..11) {
                if (!ok) break
                val a = i * PI / 6
                val qx = ex * cos(a); val qy = ey * sin(a)
                ok = isLit(m.phase, mx + qx * cos(rot) - qy * sin(rot), my + qx * sin(rot) + qy * cos(rot))
            }
            if (ok) {
                val ecx = (cx + mx * r).toFloat(); val ecy = (cy + my * r).toFloat()
                rotate((rot * 57.3).toFloat(), Offset(ecx, ecy)) {
                    drawOval(if (night) Color(0xFFE2D3A2) else Ink.gd, Offset((ecx - ex * r).toFloat(), (ecy - ey * r).toFloat()), Size((2 * ex * r).toFloat(), (2 * ey * r).toFloat()))
                }
            }
        }
    }
}

private operator fun <T> List<T>.component4() = this[3]
private operator fun <T> List<T>.component5() = this[4]

/** A few fixed stars on the night sky, kept clear of the moon and the words. */
private fun DrawScope.drawSky(w: Float, hh: Float, bx: Bx, avoidX: Float, avoidY: Float, avoidR: Float, clear: FloatArray?) {
    val rng = BoardRng(7)
    val count = Math.round(w * hh / (bx.px(34) * bx.px(34).toFloat()))
    repeat(count) {
        val x = (rng.next() * w).toFloat(); val y = (rng.next() * hh).toFloat()
        val big = rng.next() > 0.82
        var keep = true
        if (sqrt((x - avoidX) * (x - avoidX) + (y - avoidY) * (y - avoidY)) < avoidR + bx.px(6)) keep = false
        if (keep && clear != null && x > clear[0] - bx.px(6) && x < clear[0] + clear[2] + bx.px(6) && y > clear[1] - bx.px(6) && y < clear[1] + clear[3] + bx.px(6)) keep = false
        if (keep && (x < bx.px(8) || y < bx.px(8) || x > w - bx.px(8) || y > hh - bx.px(8))) keep = false
        if (keep) {
            if (big) {
                val a = bx.px(3).toFloat(); val b = bx.px(0.8).toFloat()
                val p = Path().apply {
                    moveTo(x, y - a); lineTo(x + b, y - b); lineTo(x + a, y); lineTo(x + b, y + b); lineTo(x, y + a); lineTo(x - b, y + b); lineTo(x - a, y); lineTo(x - b, y - b); close()
                }
                drawPath(p, Color.White)
            } else {
                drawCircle(if (rng.next() > 0.5) Color.White else Color(0xFF999999), max(0.8f, bx.px(0.9).toFloat()), Offset(x, y))
            }
        }
    }
}

@Composable
fun MoonWidget(c: BoardCtx, w: Int, hh: Int) {
    val bx = LocalBx.current
    val pad = bx.px(12)
    val iw = w - 2 * pad
    val ih = hh - 2 * pad
    val m = remember(c.nowMs / 600_000) { BoardData.moon(c.nowMs) }
    val capH = bx.lh1(12) + bx.px(6)
    val dia0 = min(iw, ih - capH)
    val caption = dia0 >= bx.px(40)
    val dia = max(1, if (caption) dia0 else min(iw, ih))
    val top = pad + (ih - (if (caption) capH else 0) - dia) / 2f
    Box(Modifier.size(bx.d(w), bx.d(hh))) {
        Canvas(Modifier.size(bx.d(w), bx.d(hh))) {
            drawSky(w.toFloat(), hh.toFloat(), bx, w / 2f, top + dia / 2f, dia / 2f, if (caption) floatArrayOf(0f, (hh - pad - bx.lh1(12)).toFloat(), w.toFloat(), bx.lh1(12).toFloat()) else null)
        }
        Canvas(Modifier.offset(x = bx.d(Math.round((w - dia) / 2f)), y = bx.d(Math.round(top))).size(bx.d(dia))) { drawMoon(dia.toFloat(), m, bx) }
        if (caption) Box(Modifier.offset(x = bx.d(pad), y = bx.d(hh - pad - bx.lh1(12)))) { BText(m.name, 12.0, bold = true, color = Ink.white, w = iw, align = TextAlign.Center) }
    }
}

// ============================================================ odometer
@Composable
fun OdometerWidget(c: BoardCtx, w: Int, hh: Int) {
    val bx = LocalBx.current
    val pad = bx.px(12)
    val iw = w - 2 * pad
    val ih = hh - 2 * pad
    val turns = c.d.data.pageTurns
    val total = turns?.total ?: 0
    val today = turns?.today ?: 0
    val digits = "%06d".format(total)
    val count = digits.length
    val g = bx.px(3)
    val hot = BoardColour.enabled
    val withSub = true
    val room = ih - bx.lh1(13) - bx.px(8) - (if (withSub) bx.lh1(12) + bx.px(8) else 0)
    val dw = (iw - (count - 1) * g) / count
    val dh = max(bx.px(12), min(room, min(Math.round(dw * 1.5f), bx.px(64))))
    val dwFit = min(dw, Math.round(dh / 1.32f))
    val fs = floor(dh * 0.62 / bx.scale.dp)
    val red = BoardColour.ink("odometer", Ink.white)
    val shadeLast = if (hot) BoardColour.color(BoardColour.mix(BoardColour.fam("odometer").ink, "#ffffff", 0.3)) else Ink.gd
    val shade = Ink.g3
    val sinceText = turns?.since?.let { "since " + java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).let { z -> z.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + " " + z.year } }
    WPad(w, hh, pad) {
        BHead(iw, "Pages turned", { Ic("book-open", 17, BoardColour.icon("odometer"), iconSw(2.25f)) })
        Flex()
        Row(Modifier.width(bx.d(iw)), horizontalArrangement = Arrangement.Center) {
            Row(horizontalArrangement = Arrangement.spacedBy(bx.d(g))) {
                digits.forEachIndexed { i, ch ->
                    val last = i == count - 1
                    Box(Modifier.size(bx.d(dwFit), bx.d(dh)).clip(RoundedCornerShape(bx.dd(3))).background(if (last) red else Ink.black), contentAlignment = Alignment.Center) {
                        BRect(dwFit, max(1, bx.px(1)), if (last) shadeLast else shade, modifier = Modifier.align(Alignment.TopStart).offset(y = bx.d(Math.round(dh * 0.16f))))
                        BRect(dwFit, max(1, bx.px(1)), if (last) shadeLast else shade, modifier = Modifier.align(Alignment.TopStart).offset(y = bx.d(Math.round(dh * 0.84f))))
                        if (!last) BText("$ch", fs, bold = true, color = Ink.white)
                        else {
                            val pitch = dh * 0.8f
                            val roll = if (today > 0) Math.round(pitch * 0.3f) else 0
                            val digit = ch.digitToInt()
                            Box(Modifier.offset(y = bx.d(-roll))) { BText("$digit", fs, bold = true, color = Ink.white) }
                            if (roll > 0) Box(Modifier.offset(y = bx.d(-roll + Math.round(pitch)))) { BText("${(digit + 1) % 10}", fs, bold = true, color = Ink.white) }
                        }
                    }
                }
            }
        }
        Flex()
        Row(Modifier.width(bx.d(iw))) {
            BRuns(listOf(Run("+${BoardData.count(today)}", b = true, color = Ink.black), Run(" today")), 12.0, color = Ink.g4)
            Flex()
            if (total > 0 && sinceText != null && iw > bx.px(200)) BText(sinceText, 12.0, color = Ink.g4)
        }
    }
}

// ============================================================ glyph of the day
class GlyphSpec(val cp: Int, val name: String, val font: Int?, val label: String, val sample: String? = null)

val GLYPHS = listOf(
    GlyphSpec(0x26, "Ampersand", R.font.notoserif_bolditalic, "Noto Serif Bold Italic", "Aa Bb Cc Dd"),
    GlyphSpec(0x66F8, "Book, to write", null, "Noto Sans CJK SC"),
    GlyphSpec(0xB6, "Pilcrow", null, "FreeSerif", "Aa Bb Cc Dd"),
    GlyphSpec(0x0639, "Ain", R.font.notonaskharabic_regular, "Noto Naskh Arabic"),
    GlyphSpec(0xDF, "Sharp s", R.font.notoserif_italic, "Noto Serif Italic", "Aa Bb Cc Dd"),
    GlyphSpec(0x0915, "Ka", R.font.notosansdevanagariui_bold, "Noto Sans Devanagari"),
    GlyphSpec(0xA7, "Section sign", R.font.notoserif_regular, "Noto Serif", "Aa Bb Cc Dd"),
    GlyphSpec(0x8AAD, "To read", null, "Noto Sans CJK SC"),
    GlyphSpec(0x0985, "A, in Bengali", R.font.notosansbengaliui_bold, "Noto Sans Bengali"),
    GlyphSpec(0x40, "At sign", R.font.notosans_bold, "Noto Sans Bold", "Aa Bb Cc Dd"),
    GlyphSpec(0x05D0, "Alef", null, "FreeSerif"),
    GlyphSpec(0xF0, "Eth", R.font.notoserif_bolditalic, "Noto Serif Bold Italic", "Aa Bb Cc Dd"),
    GlyphSpec(0x6587, "Writing", null, "Noto Sans CJK SC"),
    GlyphSpec(0x03A9, "Omega", R.font.notoserif_regular, "Noto Serif", "Aa Bb Cc Dd"),
    GlyphSpec(0x0416, "Zhe", R.font.notosans_bold, "Noto Sans Bold"),
    GlyphSpec(0x017F, "Long s", R.font.notoserif_italic, "Noto Serif Italic", "Aa Bb Cc Dd")
)

/** One a day, in order, counted in the reader's local days. */
fun glyphOfTheDay(today: LocalDate): GlyphSpec = GLYPHS[Math.floorMod(today.toEpochDay(), GLYPHS.size.toLong()).toInt()]

private fun glyphPath(ctx: Context, g: GlyphSpec): Pair<Path, androidx.compose.ui.geometry.Rect>? {
    val face = g.font?.let { runCatching { ctx.resources.getFont(it) }.getOrNull() } ?: Typeface.DEFAULT
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = face; textSize = 200f }
    val ap = android.graphics.Path()
    val s = String(Character.toChars(g.cp))
    paint.getTextPath(s, 0, s.length, 0f, 0f, ap)
    val b = android.graphics.RectF()
    ap.computeBounds(b, true)
    if (b.width() < 1f || b.height() < 1f) return null
    return ap.asComposePath() to androidx.compose.ui.geometry.Rect(b.left, b.top, b.right, b.bottom)
}

@Composable
private fun GlyphArt(g: GlyphSpec, maxW: Int, maxH: Int, color: Color) {
    val bx = LocalBx.current
    val ctx = LocalContext.current
    val shape = remember(g) { glyphPath(ctx, g) } ?: return
    val (path, b) = shape
    val sc = min(maxW / b.width, maxH / b.height)
    val dw = (b.width * sc).toInt(); val dh = (b.height * sc).toInt()
    Canvas(Modifier.size(bx.d(max(1, dw)), bx.d(max(1, dh)))) {
        withTransform({ scale(sc, sc, Offset.Zero); translate(-b.left, -b.top) }) { drawPath(path, color) }
    }
}

@Composable
fun GlyphWidget(c: BoardCtx, w: Int, hh: Int) {
    val bx = LocalBx.current
    val pad = bx.px(12)
    val iw = w - 2 * pad
    val ih = hh - 2 * pad
    val g = glyphOfTheDay(c.d.today)
    val cp = "U+%04X".format(g.cp)
    val col = BoardColour.ink("glyph", Ink.black)
    if (iw > ih * 1.5) {
        val gwMax = min(ih, Math.round(iw * 0.4f))
        val tw = iw - gwMax - bx.px(14)
        WPad(w, hh, pad) {
            Row(Modifier.width(bx.d(iw)).height(bx.d(ih)), horizontalArrangement = Arrangement.spacedBy(bx.dd(14)), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(bx.d(gwMax)).height(bx.d(ih)), contentAlignment = Alignment.Center) { GlyphArt(g, gwMax, ih - bx.px(4), col) }
                Column(Modifier.width(bx.d(tw))) {
                    BText("Glyph of the day", 12.0, color = Ink.g4, w = tw)
                    BText(g.name, 15.0, bold = true, w = tw)
                    BText(g.label, 12.0, w = tw)
                    BText(cp, 11.0, color = Ink.g4, w = tw)
                }
            }
        }
    } else {
        val foot = bx.lh1(15) + bx.lh1(12) + bx.px(10)
        WPad(w, hh, pad, Alignment.CenterHorizontally) {
            Flex()
            GlyphArt(g, iw - bx.px(20), ih - foot - bx.px(10), col)
            Flex()
            BText(g.name, 15.0, bold = true, w = iw, align = TextAlign.Center)
            BText("${g.label}  ·  $cp", 12.0, color = Ink.g4, w = iw, align = TextAlign.Center)
        }
    }
}

// ============================================================ tree rings
private class Wood(val bark: Color, val edge: Color, val ring: Color, val peak: Color, val line: Color, val pith: Color, val crack: Color)

private fun wood(): Wood = if (BoardColour.enabled) Wood(
    Color(0xFF6B4426), Color(0xFF3B2512), Color(0xFFF4E4C6), Color(0xFFE2B56A), Color(0xFFA37A52), Color(0xFF7A4F2C), Color(0xFF3B2512)
) else Wood(Ink.g4, Ink.black, Ink.white, Ink.gd, Ink.g6, Ink.g5, Ink.g2)

private fun DrawScope.drawRings(dia: Float, months: List<MonthMinutes>, seed: Int, wd: Wood, bx: Bx) {
    val rng = BoardRng(seed)
    val bark = max(3, bx.px(5)).toFloat()
    val R = (dia / 2 - bark - 1) / 1.07f
    val cx = dia / 2; val cy = dia / 2
    val maxM = max(1, months.maxOf { it.minutes })
    val widths = months.map { 0.12 + it.minutes.toDouble() / maxM }
    val sum = widths.sum()
    val pithR = R * 0.07f
    val bounds = ArrayList<Float>()
    var acc = 0.0
    for (wv in widths) { acc += wv; bounds += (pithR + (R - pithR) * acc / sum).toFloat() }
    val ph = doubleArrayOf(rng.next() * 6.28, rng.next() * 6.28, rng.next() * 6.28)
    val offX = -R * 0.07f; val offY = R * 0.05f
    val n = 96
    fun loop(rad: Float, extra: Float = 0f): Path {
        val t = rad / R
        val ox = cx + offX * (1 - t); val oy = cy + offY * (1 - t)
        val p = Path()
        for (i in 0 until n) {
            val a = i.toDouble() / n * 2 * PI
            val wob = 1 + (0.02 * sin(3 * a + ph[0]) + 0.014 * sin(5 * a + ph[1]) + 0.012 * sin(2 * a + ph[2])) * min(1f, t * 1.6f)
            val rr = rad * wob + extra
            val x = (ox + rr * cos(a)).toFloat(); val y = (oy + rr * sin(a)).toFloat()
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        p.close()
        return p
    }
    var peak = 0
    months.forEachIndexed { i, m -> if (m.minutes > months[peak].minutes) peak = i }
    val bp = loop(R, bark)
    drawPath(bp, wd.bark)
    drawPath(bp, wd.edge, style = Stroke(max(1, bx.px(1.25)).toFloat()))
    for (i in months.indices.reversed()) {
        val p = loop(bounds[i])
        drawPath(p, if (i == peak) wd.peak else wd.ring)
        drawPath(p, wd.line, style = Stroke(max(1, bx.px(if (i == months.lastIndex) 1.25 else 1.0)).toFloat()))
    }
    drawPath(loop(pithR * 0.9f), wd.pith)
    val ca = -0.62
    val x1 = (cx + (R + bark) * cos(ca)).toFloat(); val y1 = (cy + (R + bark) * sin(ca)).toFloat()
    val x2 = (cx + R * 0.45 * cos(ca + 0.05)).toFloat(); val y2 = (cy + R * 0.45 * sin(ca + 0.05)).toFloat()
    val nx = (-sin(ca) * bx.px(1.6)).toFloat(); val ny = (cos(ca) * bx.px(1.6)).toFloat()
    drawPath(Path().apply { moveTo(x1 - nx, y1 - ny); lineTo(x2, y2); lineTo(x1 + nx, y1 + ny); close() }, wd.crack)
}

private fun DrawScope.drawSapling(dia: Float, wd: Wood, bx: Bx) {
    val h = dia / 2
    drawCircle(wd.bark, h - 1, Offset(h, h)); drawCircle(wd.edge, h - 1, Offset(h, h), style = Stroke(max(1, bx.px(1)).toFloat()))
    drawCircle(wd.ring, h - max(3, bx.px(4)), Offset(h, h)); drawCircle(wd.line, h - max(3, bx.px(4)), Offset(h, h), style = Stroke(max(1, bx.px(1)).toFloat()))
    drawCircle(wd.pith, max(2f, dia * 0.08f), Offset(h, h))
}

@Composable
fun RingsWidget(c: BoardCtx, w: Int, hh: Int) {
    val bx = LocalBx.current
    val pad = bx.px(12)
    val iw = w - 2 * pad
    val ih = hh - 2 * pad
    val months = c.d.months
    val any = months.any { it.minutes > 0 }
    val wd = remember { wood() }
    fun monthName(m: Int, long: Boolean) = java.time.Month.of(m).getDisplayName(if (long) TextStyle.FULL else TextStyle.SHORT, Locale.ENGLISH)
    if (!any) {
        val dia = min(bx.px(56), ih - bx.lh1(13) - bx.px(12) - 2 * bx.lh(13))
        WPad(w, hh, pad) {
            BHead(iw, "Tree rings")
            VGap(bx.px(6))
            BText("A ring grows for every month you read.", 13.0, color = Ink.g4, lines = 3, w = iw)
            if (dia >= bx.px(28)) {
                Flex()
                Row(Modifier.width(bx.d(iw)), horizontalArrangement = Arrangement.Center) { Canvas(Modifier.size(bx.d(dia))) { drawSapling(dia.toFloat(), wd, bx) } }
                Flex()
            }
        }
        return
    }
    val peak = months.maxBy { it.minutes }
    fun hours(m: MonthMinutes) = "${(m.minutes / 60.0 + 0.5).toInt()} h"
    val capH = bx.lh1(12) + bx.px(8)
    val dia = max(1, min(iw, ih - bx.lh1(13) - bx.px(8) - capH))
    WPad(w, hh, pad, Alignment.CenterHorizontally) {
        BHead(iw, "Tree rings", { BText("12 months", 12.0, color = Ink.g4) })
        Flex()
        Canvas(Modifier.size(bx.d(dia))) { drawRings(dia.toFloat(), months, 3, wd, bx) }
        Flex()
        BRuns(listOf(Run("Widest ring "), Run("${monthName(peak.month, true)}, ${hours(peak)}", b = true, color = Ink.black)), 12.0, color = Ink.g4, w = iw, align = TextAlign.Center)
    }
}

