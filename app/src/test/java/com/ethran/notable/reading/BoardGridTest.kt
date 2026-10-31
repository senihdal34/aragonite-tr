package com.ethran.notable.reading

import com.ethran.notable.reading.board.BoardGrid
import com.ethran.notable.reading.board.BoardGrid.Item
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BoardGridTest {
    private fun s(id: String) = Item(id, 1, 1)
    private fun at(p: BoardGrid.Packing, id: String) = p.placed.first { it.item.id == id }.let { it.x to it.y }

    @Test fun sizeCodesFollowTheColumns() {
        assertEquals(1 to 1, BoardGrid.sizeDims("S", 4))
        assertEquals(2 to 1, BoardGrid.sizeDims("M", 4))
        assertEquals(2 to 2, BoardGrid.sizeDims("L", 4))
        assertEquals(4 to 1, BoardGrid.sizeDims("W", 4))
        assertEquals(4 to 2, BoardGrid.sizeDims("XL", 4))
        // On a two-column board W is M and XL is L.
        assertEquals(2 to 1, BoardGrid.sizeDims("W", 2))
        assertEquals(2 to 2, BoardGrid.sizeDims("XL", 2))
        assertEquals(1 to 1, BoardGrid.sizeDims("M", 1))
    }

    @Test fun columnsAndMarginsFollowTheWidth() {
        assertEquals(2, BoardGrid.columnsFor(400)); assertEquals(4, BoardGrid.columnsFor(560)); assertEquals(6, BoardGrid.columnsFor(900))
        assertEquals(16, BoardGrid.marginFor(400)); assertEquals(20, BoardGrid.marginFor(600)); assertEquals(24, BoardGrid.marginFor(700)); assertEquals(28, BoardGrid.marginFor(1000))
    }

    @Test fun geometryFillsTheWidthExactlyBecauseTheLastColumnTakesTheRemainder() {
        for (w in listOf(411, 600, 834, 1000)) {
            val g = BoardGrid.geometry(w, 900)
            assertEquals(w - 2 * g.margin, g.width)
            val total = (0 until g.cols).sumOf { BoardGrid.spanWidth(g, it, 1) } + (g.cols - 1) * g.gap
            assertEquals(g.width, total)
            assertEquals(g.width, BoardGrid.cellX(g, g.cols - 1) + BoardGrid.spanWidth(g, g.cols - 1, 1))
            assertEquals(g.width, BoardGrid.spanWidth(g, 0, g.cols))
        }
    }

    @Test fun smallBoxesFillRowsLeftToRight() {
        val p = BoardGrid.pack(listOf(s("a"), s("b"), s("c")), 2)
        assertEquals(0 to 0, at(p, "a")); assertEquals(1 to 0, at(p, "b")); assertEquals(0 to 1, at(p, "c"))
        assertEquals(2, p.rows)
        assertEquals(1, p.holes)
    }

    @Test fun aSmallBoxFillsTheHoleLeftBesideATallOne() {
        // L takes 2x2 on a 3-column grid; the next small boxes go into the free column, then below.
        val p = BoardGrid.pack(listOf(Item("big", 2, 2), s("a"), s("b"), s("c")), 3)
        assertEquals(0 to 0, at(p, "big")); assertEquals(2 to 0, at(p, "a")); assertEquals(2 to 1, at(p, "b")); assertEquals(0 to 2, at(p, "c"))
        assertEquals(3, p.rows)
    }

    @Test fun aWideBoxMovesDownWhenItDoesNotFit() {
        val p = BoardGrid.pack(listOf(s("a"), Item("w", 2, 1), s("b")), 2)
        assertEquals(0 to 0, at(p, "a")); assertEquals(0 to 1, at(p, "w"))
        assertEquals(1 to 0, at(p, "b"))                 // first fit goes back up to the hole
        assertEquals(2, p.rows)
        assertEquals(0, p.holes)
    }

    @Test fun aBoxWiderThanTheGridIsClamped() {
        val p = BoardGrid.pack(listOf(Item("x", 6, 1)), 2)
        assertEquals(2, p.placed.single().cw)
        assertEquals(0, p.holes)
    }

    @Test fun anEmptyPageHasNoRows() {
        val p = BoardGrid.pack(emptyList(), 4)
        assertEquals(0, p.rows); assertEquals(0, p.holes); assertTrue(p.placed.isEmpty())
    }

    @Test fun rowHeightStretchesToFillTheScreen() {
        val g = BoardGrid.geometry(411, 900)
        val h = BoardGrid.rowHeight(g, 4)
        assertTrue(g.available - (4 * h + 3 * g.gap) in 0 until 4)
        assertEquals(BoardGrid.MAX_ROW, BoardGrid.rowHeight(g, 0))
    }

    @Test fun aPageThatFitsKeepsEverything() {
        val g = BoardGrid.geometry(411, 900)
        val base = listOf(Item("hero", 2, 2), Item("a", 1, 1), Item("b", 1, 1))
        val fit = BoardGrid.fitPage(g, base)
        assertEquals(base, fit.placed.map { it.item })
        assertTrue(fit.dropped.isEmpty())
        assertTrue(fit.rowHeight <= BoardGrid.MAX_ROW)
        assertEquals(fit.rows * fit.rowHeight + (fit.rows - 1) * g.gap, fit.gridHeight)
    }

    @Test fun aPageThatIsTooTallDropsBoxesFromTheEnd() {
        val g = BoardGrid.geometry(411, 700)
        val base = (1..14).map { Item("b$it", 2, 1) }
        val fit = BoardGrid.fitPage(g, base)
        assertTrue(fit.dropped.isNotEmpty())
        assertEquals(base.map { it.id }, (fit.placed.map { it.item } + fit.dropped).map { it.id }) // order kept
        assertEquals(base.first(), fit.placed.first().item)
        assertTrue(fit.rowHeight >= BoardGrid.MIN_ROW)
    }

    @Test fun theFirstBoxAlwaysStays() {
        val g = BoardGrid.geometry(411, 200)   // a screen with almost no room
        val fit = BoardGrid.fitPage(g, listOf(Item("a", 2, 2), Item("b", 2, 2)))
        assertEquals(listOf("a"), fit.placed.map { it.item.id })
        assertEquals(listOf("b"), fit.dropped.map { it.id })
    }

    @Test fun extrasFillHolesButDoNotAddRowsOnATightPage() {
        val g = BoardGrid.geometry(411, 900)
        // Two columns, three small boxes leave one hole in row 2: an extra small box fits there without a new row.
        val withRoom = BoardGrid.fitPage(g, listOf(s("a"), s("b"), s("c")), listOf(s("extra")))
        assertTrue(withRoom.placed.any { it.item.id == "extra" })
        assertEquals(2, withRoom.rows)
        assertEquals(0, withRoom.holes)
        // A second extra would start a third row and leave a hole: it stays out.
        val two = BoardGrid.fitPage(g, listOf(s("a"), s("b"), s("c")), listOf(s("e1"), s("e2")))
        assertTrue(two.placed.any { it.item.id == "e1" })
        assertTrue(two.placed.none { it.item.id == "e2" })
        assertEquals(2, two.rows)
    }

    @Test fun anExtraThatLeavesANewHoleIsRefused() {
        val g = BoardGrid.geometry(411, 900)
        val fit = BoardGrid.fitPage(g, listOf(Item("a", 2, 1)), listOf(s("e")))   // would sit alone on a new row
        assertTrue(fit.placed.none { it.item.id == "e" })
    }
}

class BoardGridPixelTest {
    private val s = com.ethran.notable.reading.board.BoardMetrics.scale(1860, 2480, 300f)
    private val g = BoardGrid.geometryPx({ s.px(it) }, 1860, 2480, s.layoutWidth(1860).toInt())

    @Test fun portraitTabletGeometryMatchesTheKoreaderBoard() {
        assertEquals(4, g.cols)
        assertEquals(s.px(24), g.margin)
        assertEquals(1860 - 2 * s.px(24), g.width)
        assertEquals(s.px(10), g.gap)
        assertEquals((g.width - 3 * g.gap) / 4, g.colWidth)
        assertEquals(2480 - s.px(6) - s.px(64) - s.px(8) - s.px(8) - s.px(40) - s.px(8), g.available)
        assertEquals(s.px(84), g.minRow)
    }

    @Test fun fullScreenPortraitHomeIsSevenRowsOfAbout280Pixels() {
        val tiles = com.ethran.notable.reading.board.BoardStore.starterPages(4)[0].tiles
        val fit = BoardGrid.fitPage(g, tiles.mapIndexed { i, t -> Item("$i", t.cw, t.rh) })
        assertEquals(7, fit.rows); assertTrue(fit.dropped.isEmpty())
        assertTrue(fit.rowHeight in 275..290)   // the KOReader screenshot's rows are 280 px
    }

    @Test fun aShorterAreaDropsBoxesFromTheEndInsteadOfShrinkingRows() {
        val short = BoardGrid.geometryPx({ s.px(it) }, 1860, 1500, 769)
        val tiles = com.ethran.notable.reading.board.BoardStore.starterPages(4)[0].tiles
        val fit = BoardGrid.fitPage(short, tiles.mapIndexed { i, t -> Item("$i", t.cw, t.rh) })
        assertTrue(fit.dropped.isNotEmpty())
        assertTrue(fit.rowHeight >= short.minRow)
    }

    // The Boox Go 10.3 (1860 x 2480, 300 dpi). The app's own header and tab bar take about 310 px of the height.
    private fun tablet(w: Int, h: Int): Pair<BoardGrid.Geometry, Boolean> {
        val sc = com.ethran.notable.reading.board.BoardMetrics.scale(1860, 2480, 300f)
        return BoardGrid.geometryPx({ sc.px(it) }, w, h, sc.layoutWidth(w).toInt()) to (w > h)
    }

    @Test fun landscapeTabletUsesSixColumnsAndPortraitFour() {
        assertEquals(6, tablet(2480, 1550).first.cols)
        assertEquals(4, tablet(1860, 2170).first.cols)
        assertEquals(28 , com.ethran.notable.reading.board.BoardGrid.marginFor(1025))
    }

    @Test fun everyPageKeepsAllItsBoxesAtRowsNoShorterThanTheMinimumInBothOrientations() {
        for ((w, h) in listOf(2480 to 1550, 1860 to 2170)) {
            val (geo, land) = tablet(w, h)
            val pages = com.ethran.notable.reading.board.BoardStore.starterPages(geo.cols, hasLight = false, landscape = land)
            for (page in pages) {
                val fit = BoardGrid.fitPage(geo, page.tiles.mapIndexed { i, t -> Item("$i", t.cw, t.rh) })
                assertTrue("${page.id} at ${w}x$h dropped ${fit.dropped.size} boxes", fit.dropped.isEmpty())
                assertTrue("${page.id} at ${w}x$h rows ${fit.rowHeight} < min ${geo.minRow}", fit.rowHeight >= geo.minRow)
                assertTrue("${page.id} at ${w}x$h grid ${fit.gridHeight} > ${geo.available}", fit.gridHeight <= geo.available)
                val wide = fit.placed.maxOf { it.x + it.cw }
                assertTrue("${page.id} at ${w}x$h has a box past the last column", wide <= geo.cols)
            }
        }
    }
}
