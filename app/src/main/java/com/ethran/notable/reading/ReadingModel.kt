package com.ethran.notable.reading

import java.io.File

/** A book on the board. progress is 0..100, times are epoch milliseconds. */
data class RBook(
    val id: String,
    val path: String,
    val title: String,
    val authors: List<String>,
    val series: String? = null,
    val progress: Double = 0.0,
    val pages: Int = 0,
    val lastReadAt: Long? = null,
    val finishedAt: Long? = null,
    val addedAt: Long = 0L,
    val cover: File? = null,
    val minutesLeft: Int? = null
) {
    val author: String get() = authors.firstOrNull().orEmpty()
    val reading: Boolean get() = progress > 0.0 && finishedAt == null
}

data class RHighlight(val quote: String, val book: RBook?, val time: Long)

/** Everything the board draws, already computed. Built from NeoReader's data by [ReadingStats]. */
data class ReadingData(
    val now: Long,
    val name: String,
    val goalBooks: Int,
    val books: List<RBook>,
    val continueReading: List<RBook>,
    val discover: List<RBook>,
    val recentlyAdded: List<RBook>,
    val finishedThisYear: List<RBook>,
    val streakDays: Int,
    /** Minutes read on each of the last 7 days, oldest first; the last entry is today. */
    val weekMinutes: List<Int>,
    /** Days of the current week (Sunday first) on which something was read. */
    val weekDots: List<Boolean>,
    val todayIndex: Int,
    val minutesByDay: Map<Long, Int>,
    val authorCount: Int,
    val seriesCount: Int,
    val highlight: RHighlight?,
    val highlights: List<RHighlight>,
    /** For each reading day (epoch day), the ids of the books read that day, most read first. */
    val booksByDay: Map<Long, List<String>> = emptyMap()
) {
    val alsoReading: List<RBook> get() = continueReading.drop(1)
    val totalBooks: Int get() = books.size
    val readThisWeekMinutes: Int get() = weekMinutes.sum()

    companion object {
        val Empty = ReadingData(
            now = 0L, name = "", goalBooks = 15, books = emptyList(), continueReading = emptyList(), discover = emptyList(),
            recentlyAdded = emptyList(), finishedThisYear = emptyList(), streakDays = 0, weekMinutes = List(7) { 0 },
            weekDots = List(7) { false }, todayIndex = 0, minutesByDay = emptyMap(), authorCount = 0, seriesCount = 0,
            highlight = null, highlights = emptyList()
        )
    }
}
