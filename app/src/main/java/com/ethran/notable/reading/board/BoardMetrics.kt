package com.ethran.notable.reading.board

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sqrt

/**
 * The size unit of the KOReader board, ported from lb_board_metrics.lua. Every size on the board is given in the
 * plugin's own "dp" and converted here: dp = (short side / 600) * densityAdjust * userScale. On the 10.3 inch,
 * 300 dpi tablet that is about 2.54 pixels per plugin dp. Pure, so it is unit tested.
 */
object BoardMetrics {
    val UI_SCALES = listOf(1.0, 1.05, 1.1, 1.15, 1.2)
    const val DEFAULT_UI_SCALE = 1.05

    /** The height of a Noto face as a multiple of its size (ascent + descent). */
    const val FACE_HEIGHT_EM = 1.362

    private fun clamp(v: Double, lo: Double, hi: Double) = if (v < lo) lo else if (v > hi) hi else v

    /** The density step: the square root of how much shorter or longer the short side is than a 3.6 inch one. */
    fun densityAdjust(shortPx: Int, dpi: Float): Double {
        if (shortPx <= 0 || dpi <= 0f) return 1.0
        val inches = shortPx / dpi.toDouble()
        val adj = clamp(sqrt(3.6 / inches), 0.78, 1.12)
        return floor(adj * 100 + 0.5) / 100
    }

    /** One screen: [dp] is pixels per plugin dp, [layoutDp] the same at interface size 1 (for choosing columns). */
    data class Scale(val screenWidth: Int, val screenHeight: Int, val adj: Double, val userScale: Double) {
        private val base = minOf(screenWidth, screenHeight) / 600.0
        val dp: Double = base * adj * userScale
        val layoutDp: Double = base * adj

        /** The width layout choices are made on, in plugin dp at interface size 1. */
        fun layoutWidth(widthPx: Int): Double = floor(widthPx / layoutDp * 1000 + 0.5) / 1000

        /** Plugin dp to device pixels, rounded up so a positive size never collapses to 0. */
        fun px(n: Double): Int = if (n <= 0.0) 0 else ceil(n * dp - 1e-7).toInt()
        fun px(n: Int): Int = px(n.toDouble())

        /** Single-line text height (TextWidget) for nominal size n. */
        fun lh1(n: Double): Int = ceil(FACE_HEIGHT_EM * px(n) - 1e-7).toInt()
        fun lh1(n: Int): Int = lh1(n.toDouble())

        /** Line height of a multi-line text (TextBoxWidget) for nominal size n. */
        fun lh(n: Double): Int = floor(1.3 * px(n) + 0.5).toInt()
        fun lh(n: Int): Int = lh(n.toDouble())

        /** Height of an n-line text of nominal size: lines of round(1.3 em) plus the overflow of the last glyph box. */
        fun textBoxHeight(lines: Int, n: Double): Int {
            if (lines <= 1) return lh1(n)
            val line = lh(n)
            val face = FACE_HEIGHT_EM * px(n)
            return lines * line + maxOf(0, -floor(line - face).toInt())
        }
    }

    fun scale(screenWidth: Int, screenHeight: Int, dpi: Float, userScale: Double = DEFAULT_UI_SCALE): Scale =
        Scale(screenWidth, screenHeight, densityAdjust(minOf(screenWidth, screenHeight), dpi), userScale)
}

/** The board's pixel scale together with the Compose density, so sizes in pixels can become Dp and TextUnit. */
class Bx(val scale: BoardMetrics.Scale, val density: Float, val fontScale: Float) {
    fun px(n: Double) = scale.px(n)
    fun px(n: Int) = scale.px(n)
    fun lh1(n: Int) = scale.lh1(n)
    fun lh1(n: Double) = scale.lh1(n)
    fun lh(n: Int) = scale.lh(n)
    fun lh(n: Double) = scale.lh(n)

    /** Pixels to Dp. */
    fun d(px: Int): Dp = (px / density).dp

    /** Pixels to Dp, for a plugin size. */
    fun dd(n: Double): Dp = d(px(n))
    fun dd(n: Int): Dp = d(px(n))

    /** A font of nominal size n (plugin dp) as a text unit: its pixel size is n * dp. */
    fun sp(n: Double): TextUnit = TextUnit((px(n) / density) / fontScale, androidx.compose.ui.unit.TextUnitType.Sp)
    fun sp(n: Int): TextUnit = sp(n.toDouble())
    fun spPx(pixels: Int): TextUnit = TextUnit((pixels / density) / fontScale, androidx.compose.ui.unit.TextUnitType.Sp)
}

val LocalBx = staticCompositionLocalOf<Bx> { error("no board metrics") }
