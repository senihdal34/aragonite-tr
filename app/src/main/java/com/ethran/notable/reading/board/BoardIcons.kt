package com.ethran.notable.reading.board

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp

/**
 * Line icons of the board, ported from lb_board_icons.lua: Lucide (ISC licence) shapes on a 24 x 24 grid, drawn with
 * round caps and joins. [sw] is the stroke width on that grid, so an icon with sw 2.25 at 17 dp has a 1.6 dp line.
 */
object BoardIcons {
    private class Node(val path: Path, val fill: Boolean)

    private val cache = HashMap<String, List<Node>>()

    private fun attrs(s: String): Map<String, String> =
        s.split(';').filter { it.contains('=') }.associate { it.substringBefore('=') to it.substringAfter('=') }

    private fun nodes(name: String): List<Node> = cache.getOrPut(name) {
        val raw = BoardIconData.RAW[name] ?: return@getOrPut emptyList()
        raw.split('~').mapNotNull { n ->
            val kind = n.substringBefore('|')
            val a = attrs(n.substringAfter('|'))
            fun f(k: String) = a[k]?.toFloatOrNull() ?: 0f
            val path = when (kind) {
                "path" -> PathParser().parsePathString(a["d"].orEmpty()).toPath()
                "circle" -> Path().apply { addOval(Rect(f("cx") - f("r"), f("cy") - f("r"), f("cx") + f("r"), f("cy") + f("r"))) }
                "rect" -> Path().apply {
                    val rx = a["rx"]?.toFloatOrNull() ?: 0f
                    addRoundRect(androidx.compose.ui.geometry.RoundRect(f("x"), f("y"), f("x") + f("width"), f("y") + f("height"),
                        androidx.compose.ui.geometry.CornerRadius(rx, a["ry"]?.toFloatOrNull() ?: rx)))
                }
                "line" -> Path().apply { moveTo(f("x1"), f("y1")); lineTo(f("x2"), f("y2")) }
                else -> return@mapNotNull null
            }
            Node(path, a["fill"] != null)
        }
    }

    fun has(name: String) = BoardIconData.RAW.containsKey(name)

    /** Draws icon [name] into the whole of this scope. */
    fun DrawScope.drawIcon(name: String, color: Color, sw: Float, fill: Boolean) {
        val s = size.minDimension / 24f
        withTransform({ scale(s, s, Offset.Zero) }) {
            for (n in nodes(name)) {
                if (fill || n.fill) drawPath(n.path, color)
                drawPath(n.path, color, style = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}

/** An icon of [size] (a pixel size turned into Dp by the caller). */
@Composable
fun BIcon(name: String, size: Dp, color: Color = Ink.black, sw: Float = 2f, fill: Boolean = false, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        with(BoardIcons) { drawIcon(name, color, sw, fill) }
    }
}
