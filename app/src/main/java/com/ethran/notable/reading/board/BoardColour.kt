package com.ethran.notable.reading.board

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** The sixteen grays of an e-ink panel (INK in lb_board_metrics.lua). */
object Ink {
    val black = Color(0xFF000000)
    val g1 = Color(0xFF111111); val g2 = Color(0xFF222222); val g3 = Color(0xFF333333); val g4 = Color(0xFF444444)
    val g5 = Color(0xFF555555); val g6 = Color(0xFF666666); val g7 = Color(0xFF777777); val g8 = Color(0xFF888888)
    val g9 = Color(0xFF999999); val ga = Color(0xFFAAAAAA); val gb = Color(0xFFBBBBBB); val gc = Color(0xFFCCCCCC)
    val gd = Color(0xFFDDDDDD); val ge = Color(0xFFEEEEEE); val white = Color(0xFFFFFFFF)
}

/** The shades of one colour family: ink for bars and icons, deep for a filled tile, tint for tracks, wash for a tile. */
data class Family(val ink: String, val deep: String, val tint: String, val wash: String)

/**
 * Colour of the board, ported from lb_board_colour.lua (the "accents" scheme): each widget's marks (bars, dots, chart
 * cells, header icon) take the widget's own colour. Text stays black or white. The panel is Kaleido, which shows
 * colour at about half the saturation, so every shade has its chroma pushed out ([boost]).
 */
object BoardColour {
    /** False draws the greys only, as on a black-and-white panel. */
    var enabled = true

    /**
     * The plugin pushes chroma out (1.4) when KOReader reports an e-ink screen, but on this Boox build it does not,
     * and its screenshots show the plain family colours (a red glyph is #de4127). The board matches those.
     */
    const val PANEL_BOOST = 1.0

    val FAM: Map<String, Family> = mapOf(
        "red" to Family("#de4127", "#8c2716", "#f8c9bd", "#fbdcd3"),
        "orange" to Family("#ee8410", "#8a4806", "#fbd6a6", "#fde5c6"),
        "gold" to Family("#d9a40c", "#6b5006", "#f6e3a0", "#faedc2"),
        "green" to Family("#1e9857", "#135a37", "#c2e8d1", "#d9f1e2"),
        "teal" to Family("#12908b", "#0c5653", "#bfe6e3", "#d7f0ee"),
        "blue" to Family("#2b61d6", "#1b397d", "#c8d7f6", "#dde6f9"),
        "violet" to Family("#7848c8", "#45277c", "#dbcff2", "#e8e0f7"),
        "rose" to Family("#d0386f", "#801f46", "#f4c8d8", "#f8dbe5"),
        "brown" to Family("#99602f", "#57381c", "#e8d3bf", "#f1e4d7"),
        "yellow" to Family("#e9b800", "#6b5500", "#f9e57f", "#fcefae"),
        "night" to Family("#7f8bd6", "#1c2147", "#c9cdee", "#dfe1f5")
    )

    /** Which family each widget wears. */
    val HUE: Map<String, String> = mapOf(
        "hero" to "cover", "also" to "blue", "streak" to "orange", "week" to "green", "today" to "green",
        "goalpace" to "rose", "toggles" to "violet", "highlight" to "yellow", "heatmap" to "green", "forecast" to "blue",
        "hours" to "violet", "records" to "gold", "yearago" to "blue", "word" to "rose", "sync" to "orange",
        "clock" to "blue", "light" to "gold", "calendar" to "red", "buddy" to "violet", "analog" to "blue",
        "moon" to "night", "odometer" to "red", "glyph" to "red", "rings" to "brown", "chapters" to "cover",
        "badge" to "violet", "dust" to "green",
        "link:library" to "orange", "link:authors" to "rose", "link:series" to "violet", "link:device" to "gold",
        "link:collections" to "orange", "link:scopes" to "teal", "link:search" to "blue",
        "shelf:random" to "brown", "shelf:recently-added" to "teal", "shelf:pick-back-up" to "gold",
        "shelf:up-next-in-series" to "violet", "shelf:want-to-read" to "red", "shelf:finished" to "teal",
        "shelf:in-progress" to "blue"
    )

    // ------------------------------------------------------------ colour math
    private fun rgb(hex: String): Triple<Int, Int, Int> =
        Triple(hex.substring(1, 3).toInt(16), hex.substring(3, 5).toInt(16), hex.substring(5, 7).toInt(16))

    private fun byte(v: Double): Int = floor(v + 0.5).toInt().coerceIn(0, 255)
    private fun hex(r: Double, g: Double, b: Double) = "#%02x%02x%02x".format(byte(r), byte(g), byte(b))

    fun mix(a: String, b: String, t: Double): String {
        val (ar, ag, ab) = rgb(a)
        val (br, bg, bb) = rgb(b)
        return hex(ar + (br - ar) * t, ag + (bg - ag) * t, ab + (bb - ab) * t)
    }

    /** Hue, lightness and saturation in 0..1, as Python's colorsys has them. */
    fun rgbToHls(r: Double, g: Double, b: Double): Triple<Double, Double, Double> {
        val maxc = max(r, max(g, b))
        val minc = min(r, min(g, b))
        val l = (minc + maxc) / 2
        if (maxc == minc) return Triple(0.0, l, 0.0)
        val s = if (l <= 0.5) (maxc - minc) / (maxc + minc) else (maxc - minc) / (2 - maxc - minc)
        val rc = (maxc - r) / (maxc - minc)
        val gc = (maxc - g) / (maxc - minc)
        val bc = (maxc - b) / (maxc - minc)
        val h = when {
            r == maxc -> bc - gc
            g == maxc -> 2 + rc - bc
            else -> 4 + gc - rc
        }
        return Triple(((h / 6) % 1 + 1) % 1, l, s)
    }

    private fun hueValue(m1: Double, m2: Double, hue: Double): Double {
        val h = ((hue % 1) + 1) % 1
        return when {
            h < 1.0 / 6 -> m1 + (m2 - m1) * h * 6
            h < 0.5 -> m2
            h < 2.0 / 3 -> m1 + (m2 - m1) * (2.0 / 3 - h) * 6
            else -> m1
        }
    }

    fun hlsHex(h: Double, l: Double, s: Double): String {
        if (s == 0.0) return hex(l * 255, l * 255, l * 255)
        val m2 = if (l <= 0.5) l * (1 + s) else l + s - l * s
        val m1 = 2 * l - m2
        return hex(hueValue(m1, m2, h + 1.0 / 3) * 255, hueValue(m1, m2, h) * 255, hueValue(m1, m2, h - 1.0 / 3) * 255)
    }

    /** Chroma pushed out from the colour's own luma. */
    fun boost(hex: String, k: Double): String {
        val (r, g, b) = rgb(hex)
        val y = 0.2126 * r + 0.7152 * g + 0.0722 * b
        return hex(y + (r - y) * k, y + (g - y) * k, y + (b - y) * k)
    }

    // ------------------------------------------------------- cover colours
    /**
     * One family per cover: pixels that are nearly grey, nearly black or nearly white are dropped and the rest vote by
     * hue, weighted by saturation; the winning hue gives the shades. A jacket with too few coloured pixels has none.
     * [pixels] are packed 0xRRGGBB.
     */
    fun familyFromPixels(pixels: IntArray): Family? {
        val bins = 36
        val votes = DoubleArray(bins)
        val sats = Array(bins) { ArrayList<Double>() }
        if (pixels.isEmpty()) return null
        for (p in pixels) {
            val (h, l, s) = rgbToHls(((p shr 16) and 0xFF) / 255.0, ((p shr 8) and 0xFF) / 255.0, (p and 0xFF) / 255.0)
            if (s >= 0.28 && l >= 0.12 && l <= 0.9) {
                val k = floor(h * bins).toInt().mod(bins)
                votes[k] += s * (1 - abs(l - 0.5))
                sats[k].add(s)
            }
        }
        var best = 0
        var bestV = -1.0
        for (i in 0 until bins) {
            val v = votes[(i - 1 + bins) % bins] * 0.5 + votes[i] + votes[(i + 1) % bins] * 0.5
            if (v > bestV) { best = i; bestV = v }
        }
        if (bestV / pixels.size < 0.06) return null
        val hue = (best + 0.5) / bins
        val list = sats[best].sorted()
        val sat = if (list.isNotEmpty()) list[list.size / 2] else 0.5
        val tint = hlsHex(hue, 0.85, 0.7)
        return Family(
            deep = hlsHex(hue, 0.22, min(0.7, max(0.4, sat))),
            ink = hlsHex(hue, 0.44, min(0.85, max(0.6, sat))),
            tint = tint,
            wash = mix(tint, "#ffffff", 0.45)
        )
    }

    private fun forPanel(f: Family): Family =
        Family(boost(f.ink, PANEL_BOOST), boost(f.deep, PANEL_BOOST), boost(f.tint, PANEL_BOOST), boost(f.wash, PANEL_BOOST))

    private val boosted = HashMap<String, Family>()

    /** The family of a widget key or a family name; [cover] is the lead book's family for the keys that take it. */
    fun fam(key: String, cover: Family? = null): Family {
        if (key == "hero" || key == "chapters") return forPanel(cover ?: FAM.getValue("blue"))
        return boosted.getOrPut(key) { forPanel(FAM[key] ?: FAM.getValue(HUE[key] ?: "blue")) }
    }

    fun color(hex: String): Color {
        val (r, g, b) = rgb(hex)
        return Color(r, g, b)
    }

    // ----------------------------------------------------- what is painted
    fun ink(key: String, grey: Color): Color = if (!enabled) grey else color(fam(key).ink)
    fun deep(key: String, grey: Color): Color = if (!enabled) grey else color(fam(key).deep)
    fun tint(key: String, grey: Color): Color = if (!enabled) grey else color(fam(key).tint)

    /** Between tint and ink: the other days beside today's full-colour bar. */
    fun mid(key: String, grey: Color): Color = if (!enabled) grey else fam(key).let { color(mix(it.tint, it.ink, 0.45)) }

    /** A bar's empty part: the pale shade on white. */
    fun track(key: String, grey: Color): Color = tint(key, grey)

    /** A five-step ramp from none to most (Year in reading). */
    fun ramp(key: String, greys: List<Color>): List<Color> {
        if (!enabled) return greys
        val f = fam(key)
        return listOf(greys[0], color(f.tint), color(mix(f.tint, f.ink, 0.5)), color(f.ink), color(f.deep))
    }

    /** An icon's colour: the widget's, or [grey]. */
    fun icon(key: String, grey: Color = Ink.black): Color = if (!enabled) grey else color(fam(key).ink)

    /** Art colour: the shade ("ink", "deep", "tint", "wash") of the widget's family, or [grey]. */
    fun art(key: String, grey: Color, shade: String = "ink"): Color {
        if (!enabled) return grey
        val f = fam(key)
        return color(when (shade) { "deep" -> f.deep; "tint" -> f.tint; "wash" -> f.wash; else -> f.ink })
    }

    /** The fill of Continue reading: its cover's deep shade, or black for a jacket without colour. */
    fun heroFill(cover: Family?): Color = if (!enabled || cover == null) Ink.black else color(forPanel(cover).deep)

    /** Quiet text on the hero tile: the grey on black, a pale shade on colour. */
    fun onInk(cover: Family?, grey: Color): Color =
        if (!enabled || cover == null) grey else forPanel(cover).let { color(mix(it.tint, "#ffffff", 0.35)) }

    /** A bar's track on the hero tile. */
    fun inkTrack(cover: Family?, grey: Color): Color =
        if (!enabled || cover == null) grey else color(mix(forPanel(cover).deep, "#ffffff", 0.3))

    /** Pixels that colour screens show: the same in 0..255, kept for tests. */
    fun luma(hex: String): Int { val (r, g, b) = rgb(hex); return (0.2126 * r + 0.7152 * g + 0.0722 * b).roundToInt() }
}
