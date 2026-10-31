package com.ethran.notable.reading.board

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import coil.size.Size
import com.ethran.notable.R
import com.ethran.notable.reading.RBook
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** Noto Sans and Noto Serif, the faces KOReader draws the board in (taken from its own APK). */
object BoardFonts {
    val Sans = FontFamily(
        Font(R.font.notosans_regular, FontWeight.Normal), Font(R.font.notosans_bold, FontWeight.Bold),
        Font(R.font.notosans_italic, FontWeight.Normal, FontStyle.Italic), Font(R.font.notosans_bolditalic, FontWeight.Bold, FontStyle.Italic)
    )
    val Serif = FontFamily(
        Font(R.font.notoserif_regular, FontWeight.Normal), Font(R.font.notoserif_bold, FontWeight.Bold),
        Font(R.font.notoserif_italic, FontWeight.Normal, FontStyle.Italic), Font(R.font.notoserif_bolditalic, FontWeight.Bold, FontStyle.Italic)
    )
    fun family(serif: Boolean) = if (serif) Serif else Sans
}

private val NoPadding = PlatformTextStyle(includeFontPadding = false)
private val LineStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

internal fun boardStyle(
    bx: Bx, s: Double, bold: Boolean = false, italic: Boolean = false, serif: Boolean = false, color: Color = Ink.black,
    align: TextAlign = TextAlign.Start, multiline: Boolean = false
) = TextStyle(
    fontFamily = BoardFonts.family(serif), fontSize = bx.sp(s), fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
    fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal, color = color, textAlign = align,
    platformStyle = NoPadding, lineHeight = if (multiline) bx.spPx(bx.lh(s)) else TextUnit.Unspecified, lineHeightStyle = LineStyle
)

/** Text of nominal size [s] (plugin dp). A single line is cut with an ellipsis at [w] pixels; [lines] > 1 wraps. */
@Composable
fun BText(
    text: String, s: Double = 14.0, bold: Boolean = false, italic: Boolean = false, serif: Boolean = false,
    color: Color = Ink.black, w: Int? = null, lines: Int = 1, align: TextAlign = TextAlign.Start, modifier: Modifier = Modifier
) {
    val bx = LocalBx.current
    var m = modifier
    if (w != null) m = m.width(bx.d(w))
    BasicText(
        text, m, boardStyle(bx, s, bold, italic, serif, color, align, lines > 1),
        maxLines = lines, overflow = TextOverflow.Ellipsis, softWrap = lines > 1
    )
}

/** A styled piece of a [BRuns] line. */
class Run(val t: String, val b: Boolean = false, val s: Double? = null, val color: Color? = null, val serif: Boolean = false, val i: Boolean = false)

/** One line of styled runs on a shared baseline, e.g. a bold number followed by a muted unit. */
@Composable
fun BRuns(parts: List<Run>, s: Double = 14.0, color: Color = Ink.black, w: Int? = null, align: TextAlign = TextAlign.Start, modifier: Modifier = Modifier) {
    val bx = LocalBx.current
    val text = remember(parts, s, color) {
        buildAnnotatedString {
            for (p in parts) {
                withStyle(
                    SpanStyle(
                        fontFamily = BoardFonts.family(p.serif), fontSize = bx.sp(p.s ?: s), fontWeight = if (p.b) FontWeight.Bold else FontWeight.Normal,
                        fontStyle = if (p.i) FontStyle.Italic else FontStyle.Normal, color = p.color ?: color
                    )
                ) { append(p.t) }
            }
        }
    }
    var m = modifier
    if (w != null) m = m.width(bx.d(w))
    BasicText(text, m, boardStyle(bx, s, color = color, align = align), maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
}

@Composable
fun HGap(px: Int) = Spacer(Modifier.width(LocalBx.current.d(px)))

@Composable
fun VGap(px: Int) = Spacer(Modifier.height(LocalBx.current.d(px)))

/** A progress bar: track, fill and rounded ends. */
@Composable
fun BBar(w: Int, h: Int, pct: Double, track: Color = Ink.gc, fill: Color = Ink.black, r: Int = 0, modifier: Modifier = Modifier) {
    val bx = LocalBx.current
    val p = pct.coerceIn(0.0, 100.0)
    var fw = Math.floor(w * p / 100 + 0.5).toInt()
    if (p > 0) fw = maxOf(fw, maxOf(1, bx.px(1.5)))
    val shape = RoundedCornerShape(bx.d(r))
    Box(modifier.size(bx.d(w), bx.d(h)).clip(shape).background(track)) {
        if (fw > 0) Box(Modifier.width(bx.d(minOf(fw, w))).fillMaxHeight().clip(shape).background(fill))
    }
}

/** A dot: filled, or an outlined ring. */
@Composable
fun BDot(size: Int, on: Boolean, color: Color = Ink.black, off: Color = Ink.ga) {
    val bx = LocalBx.current
    val m = Modifier.size(bx.d(size)).clip(CircleShape)
    Box(if (on) m.background(color) else m.border(bx.d(maxOf(1, bx.px(1.25))), off, CircleShape))
}

/** A rectangle of solid colour. */
@Composable
fun BRect(w: Int, h: Int, color: Color, r: Int = 0, modifier: Modifier = Modifier) {
    val bx = LocalBx.current
    Box(modifier.size(bx.d(maxOf(0, w)), bx.d(maxOf(0, h))).background(color, RoundedCornerShape(bx.d(r))))
}

/** A circle of diameter [d] with content centred in it. */
@Composable
fun BCircle(d: Int, bg: Color? = null, border: Int = 0, borderCol: Color = Ink.black, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val bx = LocalBx.current
    var m = modifier.size(bx.d(d)).clip(CircleShape)
    if (bg != null) m = m.background(bg)
    if (border > 0) m = m.border(bx.d(border), borderCol, CircleShape)
    Box(m, contentAlignment = Alignment.Center) { content() }
}

/** An icon of nominal size [size] (plugin dp). */
@Composable
fun Ic(name: String, size: Int, color: Color = Ink.black, sw: Float = 2f, fill: Boolean = false, modifier: Modifier = Modifier) {
    val bx = LocalBx.current
    BIcon(name, bx.dd(size), color, sw, fill, modifier)
}

/** An icon of [px] device pixels. */
@Composable
fun IcPx(name: String, px: Int, color: Color = Ink.black, sw: Float = 2f, fill: Boolean = false, modifier: Modifier = Modifier) {
    val bx = LocalBx.current
    BIcon(name, bx.d(px), color, sw, fill, modifier)
}

/** The stroke width of a widget's header icon. The plugin draws a third heavier only on a reported e-ink screen, which this build is not. */
fun iconSw(sw: Float): Float = sw

// ------------------------------------------------------------------ tiles
enum class Chrome { Line, Fill, Ink }

/** The tile: chrome around a widget's content, built at exactly [w] x [h] pixels. */
@Composable
fun BTile(w: Int, h: Int, chrome: Chrome, key: String, heroFill: Color? = null, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val bx = LocalBx.current
    val shape = RoundedCornerShape(bx.dd(12))
    val bg = when (chrome) {
        Chrome.Ink -> heroFill ?: Ink.black
        Chrome.Fill -> if (key == "highlight" && BoardColour.enabled) BoardColour.art(key, Ink.ge, "wash") else Ink.ge
        Chrome.Line -> Ink.white
    }
    var m = modifier.size(bx.d(w), bx.d(h)).clip(shape).background(bg)
    if (chrome == Chrome.Line) m = m.border(bx.d(maxOf(1, bx.px(1))), Ink.g8, shape)
    Box(m) { content() }
}

// ------------------------------------------------------------------ fit
data class TitleFit(val s: Double, val lines: Int)

/**
 * The largest of [sizes] at which [text] fits [maxLines] lines (and [maxH] pixels), as BoardUI.fitTitle does: a size
 * that would split a word goes to the next one down.
 */
fun fitTitle(
    measurer: TextMeasurer, bx: Bx, text: String, bold: Boolean, serif: Boolean, width: Int, sizes: List<Double>, maxLines: Int, maxH: Int? = null
): TitleFit {
    for ((index, s) in sizes.withIndex()) {
        val style = boardStyle(bx, s, bold = bold, serif = serif, multiline = true)
        val r = measurer.measure(AnnotatedString(text), style, constraints = Constraints(maxWidth = maxOf(1, width)))
        val lines = maxOf(1, r.lineCount)
        val words = if (index == sizes.lastIndex || lines == 1) 0 else text.split(Regex("\\s+")).filter { it.isNotEmpty() }.maxOfOrNull { word ->
            measurer.measure(AnnotatedString(word), boardStyle(bx, s, bold = bold, serif = serif), maxLines = 1, softWrap = false).size.width
        } ?: 0
        if (lines <= maxLines && (maxH == null || lines * bx.lh(s) <= maxH) && (index == sizes.lastIndex || lines == 1 || words <= width)) return TitleFit(s, lines)
    }
    val s = sizes.last()
    val lines = if (maxH != null) maxH / maxOf(1, bx.lh(s)) else maxLines
    return TitleFit(s, lines.coerceIn(1, maxLines))
}

/** The largest size from [start] down to [min] at which [text] fits one line of [w] pixels. */
fun fitSize(measurer: TextMeasurer, bx: Bx, text: String, bold: Boolean, serif: Boolean, w: Int, start: Int, min: Int): Int {
    var size = start
    while (size > min) {
        val r = measurer.measure(AnnotatedString(text), boardStyle(bx, size.toDouble(), bold = bold, serif = serif), maxLines = 1, softWrap = false)
        if (r.size.width <= w) break
        size--
    }
    return size
}

@Composable
fun rememberMeasurer(): TextMeasurer = rememberTextMeasurer()

// ------------------------------------------------------------------ head
/** The label row every tile starts with: a bold label on the left, an optional widget on the right. */
@Composable
fun BHead(iw: Int, label: String, right: (@Composable () -> Unit)? = null, color: Color = Ink.black, rightW: Int = 0) {
    val bx = LocalBx.current
    val measurer = rememberMeasurer()
    // A label too long for a narrow tile steps down a size or two (to 11) before it is cut.
    val room = if (right != null && rightW > 0) iw - rightW - bx.px(4) else iw
    val size = if (room >= iw) 13 else fitSize(measurer, bx, label, true, false, room, 13, 11)
    Row(Modifier.width(bx.d(iw)).height(bx.d(bx.lh1(13))), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) { BText(label, size.toDouble(), bold = true, color = color, modifier = Modifier) }
        if (right != null) {
            HGap(bx.px(4))
            right()
        }
    }
}

/** A chevron-bearing action in a header: bold text then a chevron ("All 2 >", "See all >"). */
@Composable
fun BHeadAction(label: String, onClick: (() -> Unit)? = null) {
    val bx = LocalBx.current
    Row(if (onClick != null) Modifier.tapOrHold(onClick) else Modifier, verticalAlignment = Alignment.CenterVertically) {
        BText(label, 13.0, bold = true)
        HGap(bx.px(2))
        Ic("chevron-right", 15, Ink.black, 2.5f)
    }
}

// ------------------------------------------------------------------ covers
private val aspects = ConcurrentHashMap<String, Float>()

/** The cover's native width over height, read from the file header once. */
fun coverAspect(file: File): Float? = aspects[file.path] ?: run {
    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, o)
    if (o.outWidth > 0 && o.outHeight > 0) (o.outWidth.toFloat() / o.outHeight).also { aspects[file.path] = it } else null
}

/**
 * A book cover in a box of [w] x [h]: cropped to fill when its shape is within 12% of the box, fitted (standing on the
 * bottom edge) otherwise, with a hairline of [border] pixels. A book without an image gets a quiet title card.
 */
@Composable
fun BCover(book: RBook?, w: Int, h: Int, border: Int = -1, borderCol: Color = Ink.gb, modifier: Modifier = Modifier, topAligned: Boolean = false) {
    val bx = LocalBx.current
    val edge = if (border < 0) maxOf(1, bx.px(0.75)) else border
    val file = book?.cover
    val aspect = remember(file) { file?.let { coverAspect(it) } }
    if (file == null || aspect == null) {
        BCoverPlaceholder(book?.title.orEmpty(), w, h, modifier)
        return
    }
    val boxAspect = w.toFloat() / h
    val crop = Math.abs(aspect - boxAspect) / boxAspect <= 0.12f
    val ctx = LocalContext.current
    val dw: Int
    val dh: Int
    if (crop) { dw = w; dh = h } else {
        val sc = minOf(w / aspect, h.toFloat())      // height that fits
        dh = Math.round(sc); dw = Math.round(sc * aspect)
    }
    val request = remember(file, dw, dh) { ImageRequest.Builder(ctx).data(file).size(Size(maxOf(1, dw), maxOf(1, dh))).crossfade(false).build() }
    val painter = rememberAsyncImagePainter(request)
    Box(modifier.size(bx.d(w), bx.d(h))) {
        Image(
            painter, book.title, Modifier.align(if (topAligned) Alignment.TopCenter else Alignment.BottomCenter).size(bx.d(dw), bx.d(dh))
                .background(Ink.ge).border(bx.d(edge), borderCol),
            contentScale = if (crop) ContentScale.Crop else ContentScale.Fit
        )
    }
}

@Composable
private fun BCoverPlaceholder(title: String, w: Int, h: Int, modifier: Modifier) {
    val bx = LocalBx.current
    val frame = maxOf(1, bx.px(1))
    val pad = bx.px(8)
    val measurer = rememberMeasurer()
    val tw = w - 2 * pad - 2 * frame
    val size = remember(title, tw) {
        listOf(14.0, 13.0, 12.0, 11.0, 10.0, 9.0).firstOrNull { s ->
            title.split(Regex("[\\s-]+")).filter { it.isNotEmpty() }.all {
                measurer.measure(AnnotatedString(it), boardStyle(bx, s, bold = true, serif = true), maxLines = 1, softWrap = false).size.width <= tw
            }
        } ?: 9.0
    }
    val lines = minOf(4, (h - 2 * frame - 2 * pad) / maxOf(1, bx.lh(size)))
    Box(modifier.size(bx.d(w), bx.d(h)).background(Ink.ge).border(bx.d(frame), Ink.black), contentAlignment = Alignment.Center) {
        if (w > bx.px(48) && lines >= 1) BText(title, size, bold = true, serif = true, lines = lines, w = tw, align = TextAlign.Center)
    }
}

@Composable
fun VCenter(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) { content() }
}
