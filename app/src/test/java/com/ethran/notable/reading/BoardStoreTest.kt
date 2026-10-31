package com.ethran.notable.reading

import com.ethran.notable.reading.board.BoardGrid
import com.ethran.notable.reading.board.BoardStore
import com.ethran.notable.reading.board.Chrome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BoardStoreTest {
    private fun pack(cols: Int, landscape: Boolean = false, page: Int) =
        BoardGrid.pack(BoardStore.starterPages(cols, landscape = landscape)[page].tiles.mapIndexed { i, t -> BoardGrid.Item("$i", t.cw, t.rh) }, cols)

    @Test fun thePagesAreHomeInsightsLibraryDecor() {
        for (cols in listOf(2, 4, 6)) assertEquals(listOf("Home", "Insights", "Library", "Decor"), BoardStore.starterPages(cols).map { it.name })
    }

    @Test fun portraitHomeIsTheReferencePage() {
        val home = BoardStore.starterPages(4)[0].tiles
        assertEquals(listOf("hero", "streak", "toggles", "week", "goalpace", "also", "buddy", "highlight", "shelf", "shelf", "link", "link", "link", "link"), home.map { it.type })
        assertEquals(Chrome.Ink, home[0].chrome); assertEquals(2 to 2, home[0].cw to home[0].rh)
        assertEquals("random", home[8].kind); assertEquals("recently-added", home[9].kind)
        assertEquals(listOf("library", "authors", "series", "device"), home.drop(10).map { it.kind })
        assertEquals(Chrome.Fill, home[7].chrome)
    }

    @Test fun portraitHomeTakesSevenRowsWithNoHoles() {
        val p = pack(4, page = 0)
        assertEquals(7, p.rows); assertEquals(0, p.holes)
    }

    @Test fun noBoxAppearsOnTwoPages() {
        for (cols in listOf(2, 4, 6)) {
            val keys = BoardStore.starterPages(cols).flatMap { p -> p.tiles.map { it.key } }
            // Shelves and links are told apart by their source; a widget type is never on two pages.
            assertEquals("cols=$cols", keys.size, keys.toSet().size)
        }
    }

    @Test fun libraryPageSwapsTheCalendarForALightWhereThereIsAFrontlight() {
        assertTrue(BoardStore.starterPages(4, hasLight = false)[2].tiles.any { it.type == "calendar" })
        assertTrue(BoardStore.starterPages(4, hasLight = true)[2].tiles.any { it.type == "light" })
    }

    @Test fun keysSeparateLinksAndShelves() {
        val t = BoardStore.starterPages(4)[0].tiles
        assertEquals("link:library", t[10].key); assertEquals("shelf:random", t[8].key); assertEquals("hero", t[0].key)
    }

    @Test fun landscapeSixColumnsFillsRowsToo() {
        for (page in 0..3) assertEquals("page $page", 0, pack(6, page = page).holes)
    }
}
