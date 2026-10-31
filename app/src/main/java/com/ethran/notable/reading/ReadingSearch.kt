package com.ethran.notable.reading

import java.text.Normalizer
import java.util.Locale

/** Search over the board's books by title, author and series. Pure, so it is unit tested. */
object ReadingSearch {
    private val MARKS = Regex("\\p{M}+")

    /** Lower case without accents, so "istanbul" finds "İstanbul" and "cafe" finds "Café". */
    fun fold(s: String): String =
        Normalizer.normalize(s.lowercase(Locale.ROOT).replace('ı', 'i'), Normalizer.Form.NFD).replace(MARKS, "")

    /**
     * Books where every word of [query] appears in the title, an author or the series. A title that starts with the
     * query comes first, then titles that contain all the words, then the rest; each group in title order.
     * A blank query matches nothing.
     */
    fun filter(books: List<RBook>, query: String): List<RBook> {
        val words = fold(query).split(' ', '\t').filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()
        val q = words.joinToString(" ")
        return books.mapNotNull { b ->
            val title = fold(b.title)
            val all = title + "\n" + fold(b.authors.joinToString("\n")) + "\n" + fold(b.series.orEmpty())
            if (words.all { it in all }) {
                val rank = when {
                    title.startsWith(q) -> 0
                    words.all { it in title } -> 1
                    else -> 2
                }
                Triple(rank, title, b)
            } else null
        }.sortedWith(compareBy({ it.first }, { it.second })).map { it.third }
    }
}
