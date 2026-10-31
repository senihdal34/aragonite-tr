package com.ethran.notable.reading.board

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.text.style.TextAlign
import com.ethran.notable.reading.badges.Badge
import com.ethran.notable.reading.badges.BadgeBoard
import com.ethran.notable.reading.badges.BadgeEngine
import com.ethran.notable.reading.badges.Rarity
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// ============================================================ the medal
/** A rarity's shades. They are the same in every colour scheme of the board (BookOrbit's rarities: green, blue, violet, gold). */
private class RarityPaint(val ink: Color, val deep: Color, val tint: Color, val ribbon: Color)

private fun rarityPaint(r: Rarity): RarityPaint? {
    if (!BoardColour.enabled) return null
    val f = BoardColour.fam(r.family)
    return RarityPaint(BoardColour.color(f.ink), BoardColour.color(f.deep), BoardColour.color(f.tint), BoardColour.color(BoardColour.fam("red").ink))
}

private val RingGrey = Color(0xFFDDDDDD)
private val RibbonGrey = Color(0xFF555555)

/**
 * The medal of lb_board_decor.lua: the rim carries the rarity (plain, ringed, beaded, sunburst). A badge in progress wears
 * a ring that fills as it nears its threshold; an earned one has a filled face and hangs on two ribbons. Lines and flat
 * fills only, no shadow and no gradient, so it draws cleanly on e-ink.
 */
private fun DrawScope.drawMedal(bx: Bx, b: Badge, earned: Boolean, paint: RarityPaint?) {
    val d0 = size.minDimension
    val cx = d0 / 2
    val ringW = if (earned) 0f else max(3, bx.px(5)).toFloat()
    val gap = if (earned) 0f else bx.px(4).toFloat()
    val ribbonH = if (earned) d0 * 0.3f else 0f
    val r = (d0 - ribbonH) / 2 - ringW - gap - 1
    val legendary = b.rarity == Rarity.Legendary
    val cy = if (earned) r + bx.px(2) + (if (legendary) r * 0.1f else 0f) else d0 / 2
    val sw = max(1, bx.px(1.5)).toFloat()
    if (earned) {
        val wr = r * 0.34f
        for (sx in listOf(-1f, 1f)) {
            val x0 = cx + sx * r * 0.18f; val x1 = cx + sx * r * 0.78f
            val y0 = cy + r * 0.3f; val y1 = cy + r + ribbonH
            val tail = Path().apply {
                moveTo(x0 - wr / 2, y0); lineTo(x0 + wr / 2, y0); lineTo(x1 + wr / 2, y1); lineTo(x1, y1 - wr * 0.45f); lineTo(x1 - wr / 2, y1); close()
            }
            drawPath(tail, paint?.ribbon ?: RibbonGrey)
            drawPath(tail, Color.Black, style = Stroke(max(1, bx.px(1.25)).toFloat(), join = StrokeJoin.Round))
        }
    } else {
        val pr = r + gap + ringW / 2
        val center = Offset(cx, cy)
        drawCircle(paint?.tint ?: RingGrey, pr, center, style = Stroke(ringW))
        val p = (b.fraction ?: 0.0).coerceIn(0.0, 0.9999)
        if (p > 0.005) {
            drawArc(
                paint?.ink ?: Color.Black, -90f, (p * 360).toFloat(), false, Offset(cx - pr, cy - pr), Size(2 * pr, 2 * pr),
                style = Stroke(ringW, cap = StrokeCap.Round)
            )
        }
    }
    val face = if (earned) (paint?.deep ?: Color.Black) else Color.White
    val ink = if (earned) Color.White else Color.Black
    val center = Offset(cx, cy)
    when {
        legendary -> {
            val star = Path()
            for (i in 0..31) {
                val a = i * PI / 16
                val rr = if (i % 2 == 1) r * 0.9 else r * 1.06
                val x = (cx + rr * sin(a)).toFloat(); val y = (cy - rr * cos(a)).toFloat()
                if (i == 0) star.moveTo(x, y) else star.lineTo(x, y)
            }
            star.close()
            drawPath(star, paint?.ink ?: Color.Black)
            drawCircle(face, r * 0.84f, center)
            drawCircle(if (earned) Color.White else Color.Black, r * 0.84f, center, style = Stroke(sw))
        }
        else -> {
            drawCircle(face, r, center)
            drawCircle(Color.Black, r, center, style = Stroke(sw))
            if (b.rarity == Rarity.Rare) {
                drawCircle(ink, r - bx.px(3.5), center, style = Stroke(max(1, bx.px(1)).toFloat()))
            } else if (b.rarity == Rarity.Epic) {
                val rr = r - bx.px(4)
                for (i in 0..19) {
                    val a = i * 2 * PI / 20
                    drawCircle(ink, max(1, bx.px(1.15)).toFloat(), Offset((cx + rr * sin(a)).toFloat(), (cy - rr * cos(a)).toFloat()))
                }
            }
        }
    }
    val isz = r * 0.95f
    val icon = if (BoardIcons.has(b.icon)) b.icon else "award"
    inset(cx - isz / 2, cy - isz / 2, d0 - (cx + isz / 2), d0 - (cy + isz / 2)) {
        with(BoardIcons) { drawIcon(icon, ink, 1.9f, false) }
    }
}

/** A badge's medal, [dia] pixels across. */
@Composable
fun BadgeMedal(b: Badge, dia: Int, modifier: Modifier = Modifier) {
    val bx = LocalBx.current
    // A secret badge is drawn in greys until it is earned.
    val paint = if (b.secret) null else rarityPaint(b.rarity)
    Canvas(modifier.size(bx.d(dia))) { drawMedal(bx, b, b.earned, paint) }
}

// ============================================================ the Next badge box
private fun countText(v: Double?): String = BoardData.count(floor(v ?: 0.0).toInt().coerceAtLeast(0))

@Composable
private fun ProgressLine(b: Badge, whenText: String?, s: Double, w: Int? = null, align: TextAlign = TextAlign.Start) {
    if (b.earned) {
        BText(whenText.orEmpty(), s, color = Ink.g4, w = w, align = align)
    } else {
        BRuns(listOf(Run(countText(b.progress), b = true, color = Ink.black), Run(" of ${countText(b.threshold?.toDouble())}")), s, Ink.g4, w, align)
    }
}

@Composable
private fun HintBox(w: Int, hh: Int, hint: String) {
    val bx = LocalBx.current
    val pad = bx.px(12)
    val iw = w - 2 * pad
    WPad(w, hh, pad) {
        BHead(iw, "Next badge", { Ic("award", 17, BoardColour.icon("badge"), iconSw(2.25f)) })
        VGap(bx.px(6))
        BText(hint, 13.0, color = Ink.g4, lines = 3, w = iw)
    }
}

/**
 * Next badge (Decor.badge in lb_board_decor.lua): the badge closest to done with a ring that shows how far it is, or the
 * badge just earned hanging on its ribbons. Tap it for the list of all badges. The layout is chosen by the room: the tall
 * box shows the next badge and, underneath, the one earned last; a wide box puts the medal beside its words; a small one
 * keeps the medal and a name.
 */
@Composable
fun BadgeWidget(c: BoardCtx, w: Int, hh: Int) {
    val bx = LocalBx.current
    val measurer = rememberMeasurer()
    val board = c.badges
    if (board == null) { HintBox(w, hh, "Badges show up once your reading has loaded."); return }
    if (board.next == null && board.latest == null) { HintBox(w, hh, "Read a little and your first badge shows up here."); return }
    val pad = bx.px(12)
    val iw = w - 2 * pad
    val ih = hh - 2 * pad
    val tall = ih > bx.px(150)
    val earned = board.next == null || (board.fresh && !tall)
    val b = (if (earned) board.latest else board.next) ?: return
    val label = if (earned) (if (board.fresh) "New badge" else "Latest badge") else "Next badge"
    val whenText = BadgeEngine.earnedLabel(b.earnedAt, c.nowMs, ZoneId.systemDefault())
    val thin = max(1, bx.px(0.75))

    if (tall && !earned) {
        for (withLast in listOf(true, false)) {
            val last = board.latest.takeIf { withLast }
            val footH = if (last != null) thin + bx.px(10) + bx.px(36) else 0
            val room = ih - bx.lh1(13) - bx.px(12) - footH - (if (footH > 0) bx.px(10) else 0)
            val dia = min(Math.round(iw * 0.44f), room)
            val tw = iw - dia - bx.px(14)
            if (dia < bx.px(90) || tw < bx.px(120)) continue
            WPad(w, hh, pad) {
                BHead(iw, label, { BText("${board.earned} of ${board.total}", 12.0, color = Ink.g4) })
                VGap(bx.px(12))
                Row(Modifier.width(bx.d(iw)).height(bx.d(room)), verticalAlignment = Alignment.CenterVertically) {
                    BadgeMedal(b, dia)
                    HGap(bx.px(14))
                    Column(Modifier.width(bx.d(tw))) {
                        BText(b.rarity.label, 12.0, color = Ink.g4, w = tw)
                        BText(b.name, 16.0, bold = true, lines = 2, w = tw)
                        BText(b.description, 13.0, color = Ink.g4, lines = 2, w = tw)
                        VGap(bx.px(8))
                        ProgressLine(b, whenText, 13.0)
                        board.eta?.let { BText(it, 12.0, color = Ink.g4, lines = 2, w = tw) }
                    }
                }
                if (last != null) {
                    Flex()
                    BRect(iw, thin, Ink.gc)
                    VGap(bx.px(10))
                    Row(Modifier.width(bx.d(iw)).height(bx.d(bx.px(36))), verticalAlignment = Alignment.CenterVertically) {
                        BadgeMedal(last, bx.px(36))
                        HGap(bx.px(10))
                        Column(Modifier.width(bx.d(iw - bx.px(46)))) {
                            BText(last.name, 13.0, bold = true, w = iw - bx.px(46))
                            BText(BadgeEngine.earnedLabel(last.earnedAt, c.nowMs, ZoneId.systemDefault()).orEmpty(), 12.0, color = Ink.g4, w = iw - bx.px(46))
                        }
                    }
                }
            }
            return
        }
    }

    if (iw > ih * 1.4) {
        val dia = min(ih, Math.round(iw * 0.36f))
        val tw = iw - dia - bx.px(14)
        if (tw >= bx.px(110)) {
            // Name size, description lines, time to go, the label: a short box loses the label and the description before the
            // medal loses its words.
            val variants = listOf(
                listOf(16, 2, 1, 1), listOf(15, 1, 1, 1), listOf(15, 1, 0, 1), listOf(14, 1, 0, 1), listOf(14, 1, 0, 0), listOf(14, 0, 1, 0), listOf(14, 0, 0, 0)
            )
            val v = variants.firstOrNull { (nameS, descLines, eta, lab) ->
                val etaLine = if (eta == 1 && !earned && board.eta != null) bx.lh1(12) else 0
                val h = (if (lab == 1) bx.lh1(12) else 0) + bx.lh1(nameS) + descLines * bx.lh(12) + bx.px(4) + bx.lh1(12) + etaLine
                h <= ih
            } ?: variants.last()
            val nameS = fitSize(measurer, bx, b.name, true, false, tw, v[0], 12)
            WPad(w, hh, pad) {
                Row(Modifier.width(bx.d(iw)).height(bx.d(ih)), verticalAlignment = Alignment.CenterVertically) {
                    BadgeMedal(b, dia)
                    HGap(bx.px(14))
                    Column(Modifier.width(bx.d(tw)), verticalArrangement = Arrangement.Center) {
                        if (v[3] == 1) BText(label, 12.0, color = Ink.g4, w = tw)
                        BText(b.name, nameS.toDouble(), bold = true, w = tw)
                        if (v[1] > 0) BText(b.description, 12.0, color = Ink.g4, lines = v[1], w = tw)
                        VGap(bx.px(4))
                        ProgressLine(b, whenText, 12.0)
                        if (v[2] == 1 && !earned) board.eta?.let { BText(it, 12.0, color = Ink.g4, w = tw) }
                    }
                }
            }
            return
        }
    }

    // A small or narrow box: the medal with its name and count under it, or the medal alone.
    val capH = bx.lh1(12) + bx.lh1(12) + bx.px(6)
    val withName = ih - capH >= bx.px(44)
    val dia = max(1, if (withName) min(iw, ih - capH) else min(iw, ih))
    WPad(w, hh, pad, Alignment.CenterHorizontally) {
        Flex()
        BadgeMedal(b, dia)
        if (withName) {
            VGap(bx.px(6))
            val nameS = fitSize(measurer, bx, b.name, true, false, iw, 12, 10)
            BText(b.name, nameS.toDouble(), bold = true, w = iw, align = TextAlign.Center)
            ProgressLine(b, whenText, 12.0, iw, TextAlign.Center)
        }
        Flex()
    }
}

// ============================================================ the list of all badges
@Composable
private fun SectionHeader(title: String, count: Int) {
    val bx = LocalBx.current
    Row(Modifier.padding(top = bx.dd(14), bottom = bx.dd(2))) { BText(title, 13.0, bold = true); HGap(bx.px(6)); BText("$count", 13.0, color = Ink.g4) }
}

@Composable
private fun BadgeRow(b: Badge, nowMs: Long, zone: ZoneId) {
    val bx = LocalBx.current
    Row(Modifier.fillMaxWidth().padding(vertical = bx.dd(8)), verticalAlignment = Alignment.CenterVertically) {
        BadgeMedal(b, bx.px(54))
        HGap(bx.px(12))
        Column(Modifier.weight(1f)) {
            BText(b.name, 15.0, bold = true, lines = 2)
            BText(b.description, 12.0, color = Ink.g4, lines = 2)
            val f = b.fraction
            if (f != null && (b.progress ?: 0.0) > 0.0) {
                VGap(bx.px(5))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BBar(bx.px(110), bx.px(5), f * 100, Ink.gc, if (BoardColour.enabled) BoardColour.color(BoardColour.fam(b.rarity.family).ink) else Ink.black, bx.px(3))
                    HGap(bx.px(8))
                    ProgressLine(b, null, 12.0)
                }
            }
        }
        HGap(bx.px(10))
        Column(horizontalAlignment = Alignment.End) {
            BText(if (b.secret) "Secret" else b.rarity.label, 12.0, color = Ink.g4)
            if (b.earned) BText(BadgeEngine.earnedLabel(b.earnedAt, nowMs, zone).orEmpty().removePrefix("Earned "), 12.0, bold = true)
        }
    }
    BRect(1, max(1, bx.px(0.75)), Ink.gc, modifier = Modifier.fillMaxWidth())
}

/** All badges, grouped as earned (newest first), in progress (closest first) and locked. */
@Composable
internal fun BadgeListScreen(board: BadgeBoard?, nowMs: Long, zone: ZoneId, back: () -> Unit) {
    val bx = LocalBx.current
    ScreenTitle("Badges", back, right = board?.let { { BText("${it.earned} of ${it.total}", 13.0, color = Ink.g4) } })
    if (board == null) {
        BText("Badges show up once your reading has loaded.", 14.0, italic = true, serif = true, color = Ink.g4, lines = 3)
        return
    }
    val s = BadgeEngine.sections(board.badges)
    if (s.earned.isNotEmpty()) { SectionHeader("Earned", s.earned.size); s.earned.forEach { BadgeRow(it, nowMs, zone) } }
    if (s.inProgress.isNotEmpty()) { SectionHeader("In progress", s.inProgress.size); s.inProgress.forEach { BadgeRow(it, nowMs, zone) } }
    if (s.locked.isNotEmpty()) { SectionHeader("Locked", s.locked.size); s.locked.forEach { BadgeRow(it, nowMs, zone) } }
    VGap(bx.px(12))
}
