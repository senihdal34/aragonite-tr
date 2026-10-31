package com.ethran.notable.reading.board

/** One box on a page: its widget type, size in columns and rows, chrome, and the shelf source or link it shows. */
data class Tile(
    val type: String, val cw: Int, val rh: Int, val chrome: Chrome = Chrome.Line,
    /** A shelf's source ("random", "recently-added", ...) or a link's name ("library", "authors", ...). */
    val kind: String? = null
) {
    /** The key the box's colour is looked up by (Colour.keyFor). */
    val key: String get() = when (type) {
        "link" -> "link:$kind"
        "shelf" -> "shelf:$kind"
        else -> type
    }
}

class BoardPage(val id: String, val name: String, val tiles: List<Tile>)

/**
 * What is on each page of the board, ported from BoardStore.starterPages in lb_board_store.lua: Home is now, Insights
 * is the longer view of the reading, Library is shelves and browsing, Decor is the pictures. No box appears on two
 * pages. The portrait 4-column, the landscape 6-column and the 2-column layouts each have their own pages.
 */
object BoardStore {
    val PAGE_IDS = listOf("home", "insights", "library", "decor")
    private val NAMES = mapOf("home" to "Home", "insights" to "Insights", "library" to "Library", "decor" to "Decor")

    private fun t(type: String, cw: Int, rh: Int, chrome: Chrome = Chrome.Line) = Tile(type, cw, rh, chrome)
    private fun shelf(kind: String, cw: Int, rh: Int) = Tile("shelf", cw, rh, Chrome.Line, kind)
    private fun links(vararg kinds: String) = kinds.map { Tile("link", 1, 1, Chrome.Line, it) }

    /** The four starter pages for a layout of [cols] columns. [hasLight] swaps Library's calendar for a Light box. */
    fun starterPages(cols: Int, hasLight: Boolean = false, landscape: Boolean = false): List<BoardPage> {
        fun deviceRow(cw: Int) = listOf(t("clock", cw, 1, Chrome.Ink), if (hasLight) t("light", cw, 1) else t("calendar", cw, 1))
        val home: List<Tile>
        val insights: List<Tile>
        val library: List<Tile>
        val decor: List<Tile>
        when {
            cols == 2 -> {
                home = listOf(t("hero", 2, 2, Chrome.Ink), t("streak", 1, 1), t("week", 1, 1), t("goalpace", 1, 1), t("toggles", 1, 1), t("also", 2, 2),
                    t("buddy", 2, 1), shelf("random", 2, 2), t("highlight", 2, 1, Chrome.Fill)) + links("library", "authors", "series", "device") + shelf("recently-added", 2, 2)
                insights = listOf(t("heatmap", 2, 2), t("forecast", 2, 1), t("hours", 2, 1), t("records", 2, 2), shelf("finished", 2, 2), t("yearago", 2, 1), t("word", 2, 1))
                library = listOf(shelf("pick-back-up", 2, 2), shelf("up-next-in-series", 2, 2), shelf("want-to-read", 2, 2)) + links("collections", "scopes", "search") +
                    t("sync", 1, 1) + deviceRow(2)
                decor = listOf(t("chapters", 2, 2), t("analog", 1, 1, Chrome.Ink), t("moon", 1, 1, Chrome.Ink), t("badge", 2, 1), t("odometer", 2, 1),
                    t("dust", 2, 1), t("rings", 2, 1), t("glyph", 2, 1))
            }
            cols == 6 -> {
                home = listOf(t("hero", 2, 2, Chrome.Ink), t("streak", 1, 1), t("week", 1, 1), t("also", 2, 2), t("goalpace", 1, 1), t("toggles", 1, 1),
                    t("buddy", 2, 1), t("highlight", 2, 1, Chrome.Fill), shelf("random", 2, 2), shelf("recently-added", 2, 2)) +
                    links("library", "authors", "series", "collections", "scopes", "device")
                insights = listOf(t("heatmap", 4, 2), t("records", 2, 2), t("forecast", 2, 1), t("hours", 2, 1), t("word", 2, 1), shelf("finished", 4, 2), t("yearago", 2, 2))
                library = listOf(shelf("pick-back-up", 4, 2), shelf("up-next-in-series", 2, 2), shelf("want-to-read", 4, 2)) + links("search") +
                    listOf(t("sync", 1, 1), t("clock", 2, 1, Chrome.Ink))
                decor = listOf(t("badge", 2, 2), t("rings", 2, 2), t("glyph", 2, 2), t("chapters", 6, 1), t("analog", 1, 1, Chrome.Ink), t("odometer", 2, 1),
                    t("moon", 1, 1, Chrome.Ink), t("dust", 2, 1))
            }
            landscape -> {
                home = listOf(t("hero", 2, 2, Chrome.Ink), t("streak", 1, 1), t("week", 1, 1), t("goalpace", 1, 1), t("toggles", 1, 1), t("also", 2, 2),
                    shelf("random", 2, 2)) + links("library", "authors", "series", "device")
                insights = listOf(t("heatmap", 4, 2), t("forecast", 2, 1), t("hours", 2, 1), t("records", 2, 2), t("yearago", 2, 2))
                library = listOf(shelf("pick-back-up", 4, 2), shelf("up-next-in-series", 2, 2), shelf("want-to-read", 2, 2)) + links("collections", "scopes", "search") + t("sync", 1, 1)
                decor = listOf(t("buddy", 2, 2), t("chapters", 2, 2), t("analog", 1, 1, Chrome.Ink), t("odometer", 2, 1), t("moon", 1, 1, Chrome.Ink),
                    t("badge", 2, 2), t("dust", 2, 1), t("glyph", 2, 1))
            }
            else -> {
                home = listOf(t("hero", 2, 2, Chrome.Ink), t("streak", 1, 1), t("toggles", 1, 1), t("week", 1, 1), t("goalpace", 1, 1), t("also", 2, 2),
                    t("buddy", 2, 1), t("highlight", 2, 1, Chrome.Fill), shelf("random", 2, 2), shelf("recently-added", 2, 2)) + links("library", "authors", "series", "device")
                insights = listOf(t("heatmap", 4, 2), t("forecast", 2, 1), t("hours", 2, 1), t("records", 2, 2), t("yearago", 2, 2), shelf("finished", 2, 2), t("word", 2, 2))
                library = listOf(shelf("pick-back-up", 4, 2), shelf("up-next-in-series", 2, 2), shelf("want-to-read", 2, 2)) + links("collections", "scopes", "search") +
                    t("sync", 1, 1) + deviceRow(2)
                decor = listOf(t("chapters", 2, 2), t("badge", 2, 2), t("analog", 1, 1, Chrome.Ink), t("odometer", 2, 1), t("moon", 1, 1, Chrome.Ink),
                    t("rings", 2, 2), t("dust", 2, 1), t("glyph", 2, 1))
            }
        }
        val pages = mapOf("home" to home, "insights" to insights, "library" to library, "decor" to decor)
        return PAGE_IDS.map { BoardPage(it, NAMES.getValue(it), pages.getValue(it)) }
    }

    fun pageName(id: String): String = NAMES[id] ?: NAMES.getValue("home")
}
