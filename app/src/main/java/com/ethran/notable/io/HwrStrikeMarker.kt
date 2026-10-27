package com.ethran.notable.io

/** Bounding box in the recognizer's own unit (millimetres). */
data class MmBox(val x: Float, val y: Float, val width: Float, val height: Float)

/**
 * Wraps words that lie under a strike-through line in ~~ ~~ (markdown strikethrough).
 *
 * The recognizer reports word boxes in millimetres relative to its own origin. The ink's pixel
 * rectangle and the union of the word boxes describe the same writing, so one maps onto the other;
 * if the two disagree about the scale, nothing is marked (better plain text than a wrong strike).
 */
object HwrStrikeMarker {

    fun struckFlags(words: List<HwrWord>, ink: InkBox, strikes: List<InkBox>): BooleanArray {
        val flags = BooleanArray(words.size)
        if (strikes.isEmpty()) return flags
        val boxes = words.mapNotNull { it.box }
        if (boxes.isEmpty() || ink.width <= 0f || ink.height <= 0f) return flags

        val ux = boxes.minOf { it.x }
        val uy = boxes.minOf { it.y }
        val uw = boxes.maxOf { it.x + it.width } - ux
        val uh = boxes.maxOf { it.y + it.height } - uy
        if (uw <= 0f || uh <= 0f) return flags
        val sx = ink.width / uw
        val sy = ink.height / uh
        if (kotlin.math.abs(sx - sy) > 0.15f * maxOf(sx, sy)) return flags

        for ((i, w) in words.withIndex()) {
            val b = w.box ?: continue
            val left = ink.left + (b.x - ux) * sx
            val right = left + b.width * sx
            val top = ink.top + (b.y - uy) * sy
            val bottom = top + b.height * sy
            val h = bottom - top
            val cy = (top + bottom) / 2f
            flags[i] = strikes.any { s ->
                val overlap = minOf(right, s.right) - maxOf(left, s.left)
                overlap >= 0.5f * (right - left) && cy >= s.top - 0.3f * h && cy <= s.bottom + 0.3f * h
            }
        }
        return flags
    }

    /** Joins the (already corrected) word texts, putting ~~ around each run of struck words. */
    fun render(texts: List<String>, struck: BooleanArray): String {
        val sb = StringBuilder()
        var open = false
        fun blank(i: Int) = texts[i].isBlank()
        fun nextWordOnSameLine(from: Int): Int? {
            var j = from
            while (j < texts.size) {
                if (texts[j].contains('\n')) return null
                if (!blank(j)) return j
                j++
            }
            return null
        }
        for (i in texts.indices) {
            val t = texts[i]
            if (blank(i)) {
                if (open && t.contains('\n')) { sb.append("~~"); open = false }
                else if (open) {
                    val next = nextWordOnSameLine(i + 1)
                    if (next == null || !struck[next]) { sb.append("~~"); open = false }
                }
                sb.append(t)
                continue
            }
            if (struck[i] && !open) { sb.append("~~"); open = true }
            if (!struck[i] && open) { sb.append("~~"); open = false }
            sb.append(t)
        }
        if (open) sb.append("~~")
        return sb.toString()
    }
}
