package com.ethran.notable.io

import com.ethran.notable.data.db.Stroke

/**
 * Splits a page's strokes into small groups for handwriting recognition.
 *
 * Sending a whole long page (thousands of strokes) to the Onyx HWR service in one request
 * times out after 10s and can crash the service, which makes the export contain no text.
 * Groups are cut only at vertical gaps so a line of text is never split in the middle.
 */
object HwrChunker {
    const val DEFAULT_MAX_STROKES = 150

    fun chunkByLines(strokes: List<Stroke>, maxStrokes: Int = DEFAULT_MAX_STROKES): List<List<Stroke>> {
        if (strokes.isEmpty()) return emptyList()
        val sorted = strokes.sortedBy { it.top }
        val chunks = mutableListOf<List<Stroke>>()
        var current = mutableListOf<Stroke>()
        var currentBottom = Float.NEGATIVE_INFINITY

        for (stroke in sorted) {
            val startsBelowCurrent = stroke.top >= currentBottom
            // Close the chunk once it is big enough and the next stroke starts below everything in it.
            // Hard limit (2x) so a page with no vertical gaps still gets split.
            if (current.size >= maxStrokes && (startsBelowCurrent || current.size >= maxStrokes * 2)) {
                chunks.add(current)
                current = mutableListOf()
                currentBottom = Float.NEGATIVE_INFINITY
            }
            current.add(stroke)
            if (stroke.bottom > currentBottom) currentBottom = stroke.bottom
        }
        if (current.isNotEmpty()) chunks.add(current)
        return chunks
    }

    /**
     * Split one chunk in two for a retry, preferring the widest vertical gap near the middle
     * so a text line stays whole. Returns null when the chunk is too small to split.
     */
    fun splitInHalf(chunk: List<Stroke>, minSize: Int = 16): Pair<List<Stroke>, List<Stroke>>? {
        if (chunk.size < minSize) return null
        val sorted = chunk.sortedBy { it.top }
        val from = sorted.size / 4
        val to = sorted.size * 3 / 4
        var bestIndex = sorted.size / 2
        var bestGap = Float.NEGATIVE_INFINITY
        var runningBottom = sorted.take(from).maxOfOrNull { it.bottom } ?: Float.NEGATIVE_INFINITY
        for (i in from until to) {
            val gap = sorted[i].top - runningBottom
            if (gap > bestGap) { bestGap = gap; bestIndex = i }
            runningBottom = maxOf(runningBottom, sorted[i].bottom)
        }
        if (bestIndex <= 0 || bestIndex >= sorted.size) bestIndex = sorted.size / 2
        return sorted.subList(0, bestIndex) to sorted.subList(bestIndex, sorted.size)
    }
}
