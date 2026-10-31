package com.ethran.notable.reading.chapters

import java.io.File

/** Reads a book file's chapters: PDF bookmarks and EPUB contents. Every other format has none. */
object ChapterReader {

    /** Blocking file I/O: call it on Dispatchers.IO. Never throws. */
    fun read(file: File): ChapterResult {
        if (!file.isFile) return ChapterResult.None(NoChapters.Unreadable)
        return when (file.extension.lowercase()) {
            "epub" -> EpubChapters.read(file)
            "pdf" -> PdfChapters.read(file)
            else -> ChapterResult.None(NoChapters.UnsupportedFormat)
        }
    }
}

/** PDF bookmarks turned into chapters. */
object PdfChapters {
    fun read(file: File): ChapterResult = fromOutline(PdfOutline.read(file))

    fun read(bytes: ByteArray): ChapterResult = fromOutline(PdfOutline.read(bytes))

    internal fun fromOutline(r: PdfOutlineResult): ChapterResult = when (r) {
        is PdfOutlineResult.NoOutline -> ChapterResult.None(NoChapters.NoOutline)
        is PdfOutlineResult.Unreadable -> ChapterResult.None(NoChapters.Unreadable)
        is PdfOutlineResult.Found -> {
            val pages = r.pages
            if (pages <= 0) ChapterResult.None(NoChapters.Unreadable)
            else {
                val entries = r.bookmarks.mapNotNull { b ->
                    if (b.page !in 0 until pages || b.title.isEmpty()) null
                    else Chapter(b.title, b.depth, b.page.toDouble() / pages, b.page + 1)
                }
                // A contents list out of page order is put in page order; entries on one page keep their order.
                if (entries.isEmpty()) ChapterResult.None(NoChapters.NoOutline)
                else ChapterResult.Found(ChapterMap(ChapterSource.Pdf, pages, entries.sortedBy { it.startFraction }))
            }
        }
    }
}
