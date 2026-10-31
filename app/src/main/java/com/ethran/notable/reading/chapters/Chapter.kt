package com.ethran.notable.reading.chapters

/** Where a [ChapterMap] came from. */
enum class ChapterSource { Epub, Pdf }

/**
 * One entry of a book's table of contents.
 *
 * [startFraction] is where the entry starts, 0..1 of the whole book. For a PDF it is exact: `(startPage - 1) / pages`.
 * For an EPUB it is an approximation: the share of the book's text files (by size) that come before the entry. NeoReader
 * paginates an EPUB itself and does not tell anyone how, so the app cannot know the page an EPUB chapter starts on.
 */
data class Chapter(val title: String, val depth: Int, val startFraction: Double, val startPage: Int? = null)

/**
 * A book's table of contents as read from the file. [pages] is the PDF's page count (0 for an EPUB, which has no fixed
 * pages). Entries keep the order of the contents and are never empty.
 */
data class ChapterMap(val source: ChapterSource, val pages: Int, val entries: List<Chapter>)

/** Why a book has no chapter map. Only used for the honest empty-state text and the logs. */
enum class NoChapters(val short: String) {
    /** The file has no table of contents (a PDF without bookmarks, an EPUB without nav or ncx). */
    NoOutline("no table of contents"),
    /** MOBI, AZW, DjVu, text... only PDF and EPUB are read. */
    UnsupportedFormat("format not supported"),
    /** The file is missing, damaged or uses a feature the reader does not handle (an encrypted PDF). */
    Unreadable("could not read the file"),
}

/** The outcome of reading one book: a map, or the reason there is none. */
sealed interface ChapterResult {
    data class Found(val map: ChapterMap) : ChapterResult
    data class None(val reason: NoChapters) : ChapterResult
}
