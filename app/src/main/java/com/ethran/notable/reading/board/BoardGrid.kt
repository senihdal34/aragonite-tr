package com.ethran.notable.reading.board

/**
 * Pure layout for a board page, ported from the KOReader board (lb_board_layout.lua): the grid a screen gets, the
 * dense first-fit packing of a page's boxes, and how a page fits the screen. All sizes are in dp. Nothing here
 * touches Compose, so it is unit tested. The pages in [ReadingBoardView] are still laid out by hand; this is the
 * building block for moving them onto a grid.
 */
object BoardGrid {
    const val MIN_ROW = 84
    const val COMFY_ROW = 104
    const val MAX_ROW = 150
    const val GAP = 10
    const val TOP = 6
    const val HEADER = 64
    const val HEADER_GAP = 8
    const val FOOTER = 40
    const val FOOTER_GAP = 8
    const val BOTTOM = 8

    /** A box that wants [cw] columns and [rh] rows. */
    data class Item(val id: String, val cw: Int, val rh: Int)

    /** An [item] placed at column [x], row [y]; [cw] is its width in columns after clamping to the grid. */
    data class Placed(val item: Item, val x: Int, val y: Int, val cw: Int)

    data class Packing(
        val placed: List<Placed>, val rows: Int, val holes: Int,
        /** Set by [fitPage]: the row height to draw at, the height of the whole grid, and the boxes left out for room. */
        val rowHeight: Int = 0, val gridHeight: Int = 0, val dropped: List<Item> = emptyList()
    )

    data class Geometry(
        val width: Int, val margin: Int, val cols: Int, val gap: Int, val colWidth: Int,
        /** Height left for the grid under the header and above the page dots. */
        val available: Int
    )

    /** Size codes: S 1x1, M 2x1, L 2x2, W full width x1, XL full width x2. On a two-column board W is M and XL is L. */
    fun sizeDims(code: String, cols: Int): Pair<Int, Int> = when (code) {
        "S" -> 1 to 1
        "M" -> minOf(2, cols) to 1
        "L" -> minOf(2, cols) to 2
        "W" -> cols to 1
        else -> cols to 2
    }

    fun columnsFor(widthDp: Int): Int = when {
        widthDp >= 900 -> 6
        widthDp >= 560 -> 4
        else -> 2
    }

    fun marginFor(widthDp: Int): Int = when {
        widthDp >= 900 -> 28
        widthDp >= 700 -> 24
        widthDp >= 560 -> 20
        else -> 16
    }

    fun geometry(screenWidth: Int, screenHeight: Int): Geometry {
        val margin = marginFor(screenWidth)
        val width = screenWidth - 2 * margin
        val cols = columnsFor(screenWidth)
        return Geometry(
            width, margin, cols, GAP, (width - (cols - 1) * GAP) / cols,
            screenHeight - TOP - HEADER - HEADER_GAP - FOOTER_GAP - FOOTER - BOTTOM
        )
    }

    /** Width of a box spanning [cw] columns from column [x]; the last column takes the rounding remainder. */
    fun spanWidth(g: Geometry, x: Int, cw: Int): Int =
        if (x + cw == g.cols) g.width - x * (g.colWidth + g.gap) else cw * g.colWidth + (cw - 1) * g.gap

    fun cellX(g: Geometry, x: Int): Int = x * (g.colWidth + g.gap)

    /** First-fit dense packing, row-major: each box goes to the first spot where it fits. */
    fun pack(items: List<Item>, cols: Int): Packing {
        val occupied = ArrayList<BooleanArray>()
        fun line(r: Int): BooleanArray { while (occupied.size <= r) occupied.add(BooleanArray(cols)); return occupied[r] }
        fun free(r: Int, c: Int, w: Int, h: Int) = (r until r + h).all { y -> (c until c + w).none { x -> line(y)[x] } }
        var rows = 0
        val placed = ArrayList<Placed>()
        for (it in items) {
            val cw = it.cw.coerceIn(1, cols)
            val rh = it.rh.coerceAtLeast(1)
            var r = 0
            search@ while (true) {
                for (x in 0..cols - cw) {
                    if (free(r, x, cw, rh)) {
                        for (y in r until r + rh) for (xx in x until x + cw) line(y)[xx] = true
                        rows = maxOf(rows, r + rh)
                        placed += Placed(it, x, r, cw)
                        break@search
                    }
                }
                r++
            }
        }
        val holes = (0 until rows).sumOf { y -> line(y).count { !it } }
        return Packing(placed, rows, holes)
    }

    /** Height of one row when [rows] rows fill the available height exactly. */
    fun rowHeight(g: Geometry, rows: Int): Int =
        if (rows <= 0) MAX_ROW else (g.available - (rows - 1) * g.gap) / rows

    /**
     * Fits one page: drops boxes from the end until rows are at least [MIN_ROW] high, then adds [extras] that cost no
     * new row (or keep rows comfortable) and leave no new holes. The first box always stays. The result carries
     * the row height to draw at (at most [MAX_ROW]) and the boxes dropped for room.
     */
    fun fitPage(g: Geometry, base: List<Item>, extras: List<Item> = emptyList()): Packing {
        val items = base.toMutableList()
        val dropped = ArrayList<Item>()
        while (items.size > 1 && rowHeight(g, pack(items, g.cols).rows) < MIN_ROW) dropped.add(0, items.removeAt(items.lastIndex))
        for (ex in extras) {
            val before = pack(items, g.cols)
            val after = pack(items + ex, g.cols)
            if ((after.rows == before.rows || rowHeight(g, after.rows) >= COMFY_ROW) && after.holes <= before.holes) items += ex
        }
        val packed = pack(items, g.cols)
        val row = minOf(MAX_ROW, rowHeight(g, packed.rows))
        return packed.copy(rowHeight = row, gridHeight = packed.rows * row + maxOf(0, packed.rows - 1) * g.gap, dropped = dropped)
    }
}
