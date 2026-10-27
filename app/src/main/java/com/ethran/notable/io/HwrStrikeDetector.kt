package com.ethran.notable.io

import com.ethran.notable.data.db.Stroke

data class InkBox(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top
    val centerY get() = (top + bottom) / 2f
}

/** Page strokes split into what the recognizer should read and what should not reach it. */
class StrokeSplit(
    /** Strokes sent to handwriting recognition. */
    val text: List<Stroke>,
    /** Lines drawn through text: the words under them are marked ~~struck~~. */
    val strikes: List<InkBox>,
    /** Underlines, overlines and divider rules: dropped, they only garble the recognition. */
    val ignored: List<Stroke>,
    /** One line per candidate with the numbers behind the decision (for the debug log). */
    val trace: List<String> = emptyList()
)

/**
 * Finds strike-through lines in handwriting.
 *
 * The recognizer reads a long line drawn across a text line as a row of letters, which ruins the
 * whole line. A stroke counts as a strike when it is long and flat, crosses several other strokes,
 * is clearly lower than the text it crosses and sits in the middle of the text band.
 */
object HwrStrikeDetector {
    const val MIN_LINE_WIDTH = 220f
    private const val MAX_ASPECT = 0.2f          // height / width of a "flat" stroke
    private const val MIN_CROSSED = 4            // other strokes it must cross to be over text
    private const val MAX_HEIGHT_VS_TEXT = 0.6f  // a word stroke is as tall as its text, a strike is not
    private const val MAX_EDGE_LINE_HEIGHT = 0.35f
    private const val DIVIDER_WIDTH = 400f
    private const val LONG_STRIKE_WIDTH = 600f
    private const val LONG_STRIKE_CROSSED = 10

    fun split(strokes: List<Stroke>): StrokeSplit {
        val lines = strokes.filter { it.width() >= MIN_LINE_WIDTH && it.height() <= it.width() * MAX_ASPECT }
        if (lines.isEmpty()) return StrokeSplit(strokes, emptyList(), emptyList())

        val lineSet = lines.toHashSet()
        val others = strokes.filter { it !in lineSet }
        val strikes = mutableListOf<InkBox>()
        val strikeStrokes = mutableListOf<Stroke>()
        val ignored = mutableListOf<Stroke>()
        val trace = mutableListOf<String>()

        for (line in lines) {
            val crossed = others.filter { o ->
                val cx = (o.left + o.right) / 2f
                cx in line.left..line.right && o.bottom >= line.top && o.top <= line.bottom
            }
            fun note(decision: String, extra: String = "") =
                trace.add("%s x=%.0f..%.0f y=%.0f..%.0f w=%.0f h=%.0f crossed=%d %s".format(
                    decision, line.left, line.right, line.top, line.bottom, line.width(), line.height(), crossed.size, extra))
            if (crossed.size < MIN_CROSSED) {
                note("NOT-OVER-TEXT")
                // Nothing under it: a divider rule is noise, a short flat stroke may be an arrow shaft.
                if (line.width() >= DIVIDER_WIDTH) ignored.add(line)
                continue
            }
            val bandTop = median(crossed.map { it.top })
            val bandBottom = median(crossed.map { it.bottom })
            val bandHeight = bandBottom - bandTop
            val textHeight = percentile(crossed.map { it.height() }, 0.75f)
            val centerY = (line.top + line.bottom) / 2f
            val textSize = maxOf(textHeight, bandHeight)
            val inMiddle = bandHeight > 0 &&
                centerY >= bandTop + 0.15f * bandHeight && centerY <= bandBottom - 0.15f * bandHeight
            val thin = line.height() <= MAX_EDGE_LINE_HEIGHT * textSize
            // A zigzag strike can be as tall as the letters, but nothing else in handwriting is this
            // long and crosses this many strokes, so length + crossings can stand in for flatness.
            val veryLong = line.width() >= LONG_STRIKE_WIDTH && crossed.size >= LONG_STRIKE_CROSSED &&
                centerY >= bandTop - 0.5f * bandHeight && centerY <= bandBottom + 0.5f * bandHeight
            val info = "band=%.0f..%.0f textH=%.0f centerY=%.0f".format(bandTop, bandBottom, textHeight, centerY)
            when {
                // Thin line at the top or bottom edge of the text: underline / overline.
                bandHeight > 0 && thin && !inMiddle -> { note("EDGE-LINE", info); ignored.add(line) }
                veryLong || (inMiddle && line.height() <= MAX_HEIGHT_VS_TEXT * textSize) -> {
                    note("STRIKE", info)
                    strikes.add(InkBox(line.left, line.top, line.right, line.bottom))
                    strikeStrokes.add(line)
                }
                // Anything else (a wide cursive word crossed by its own dots) stays text.
                else -> note("KEPT-AS-TEXT", info)
            }
        }

        val dropped = (ignored + strikeStrokes).toHashSet()
        return StrokeSplit(
            text = strokes.filter { it !in dropped },
            strikes = strikes,
            ignored = ignored,
            trace = trace
        )
    }

    private fun Stroke.width() = right - left
    private fun Stroke.height() = bottom - top

    private fun median(v: List<Float>) = percentile(v, 0.5f)
    private fun percentile(v: List<Float>, p: Float): Float {
        val s = v.sorted()
        return s[((s.size - 1) * p).toInt().coerceIn(0, s.size - 1)]
    }
}
