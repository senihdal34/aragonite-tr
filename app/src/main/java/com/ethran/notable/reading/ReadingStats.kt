package com.ethran.notable.reading

import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Random

/** Turns NeoReader's library, reading log and highlights into what the board shows. Pure: no Android calls, so it is unit tested. */
object ReadingStats {
    /** A page left open for hours is not reading: one page counts for at most this long. */
    const val PAGE_CAP_MS = 3 * 60_000L
    /** The reading day starts at 04:00, so a late night session stays on one day. */
    const val DAY_START_HOUR = 4
    const val FINISHED_AT = 99.0

    fun dayOf(timeMs: Long, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(timeMs).atZone(zone).minusHours(DAY_START_HOUR.toLong()).toLocalDate()

    private fun norm(path: String) = path.replace("/storage/emulated/0", "/sdcard")

    fun coverKey(path: String): String =
        MessageDigest.getInstance("MD5").digest(norm(path).toByteArray()).joinToString("") { "%02x".format(it) }

    fun build(
        books: List<NrBook>, events: List<NrEvent>, highlights: List<NrHighlight>, now: Long,
        zone: ZoneId = ZoneId.systemDefault(), name: String = "Senih", goalBooks: Int = 15,
        booksDir: String = "/sdcard/Books", exists: (String) -> Boolean = { File(it).exists() },
        coverDir: File? = null, thumbnails: Map<String, File> = emptyMap(), seed: Long = 0L
    ): ReadingData {
        val inBooks = books.filter { norm(it.path).startsWith("$booksDir/") && exists(it.path) }
        val byPath = inBooks.associateBy { norm(it.path) }

        // Events carry the file path only on their "opened" rows; docId links the rest to it.
        val pathOfDoc = HashMap<String, String>()
        for (e in events) if (e.docId != null && e.path != null) pathOfDoc[e.docId] = norm(e.path)
        fun pathOf(e: NrEvent) = e.path?.let(::norm) ?: e.docId?.let { pathOfDoc[it] }

        val pageEvents = events.filter { it.type == 1 && pathOf(it)?.let(byPath::containsKey) == true }
        val minutesByDay = HashMap<Long, Int>()
        val msByDay = HashMap<LocalDate, Long>()
        val msByDayBook = HashMap<LocalDate, HashMap<String, Long>>()
        for (e in pageEvents) {
            val d = dayOf(e.time, zone)
            val ms = e.durationMs.coerceIn(0L, PAGE_CAP_MS)
            msByDay[d] = (msByDay[d] ?: 0L) + ms
            val path = pathOf(e) ?: continue
            val perBook = msByDayBook.getOrPut(d) { HashMap() }
            perBook[path] = (perBook[path] ?: 0L) + ms
        }
        val booksByDay = msByDayBook.entries.associate { (d, perBook) ->
            d.toEpochDay() to perBook.entries.sortedByDescending { it.value }.map { it.key }
        }
        for ((d, ms) in msByDay) minutesByDay[d.toEpochDay()] = (ms / 60_000L).toInt()
        val readDays = msByDay.filter { it.value >= 30_000L }.keys

        val today = dayOf(now, zone)
        val sortedPages = pageEvents.sortedBy { it.time }
        val hours = hoursRead(sortedPages, now, zone)
        val records = sessionRecords(sortedPages)
        val pageTurns = if (sortedPages.isEmpty()) null else PageTurns(
            sortedPages.size, sortedPages.count { dayOf(it.time, zone) == today }, sortedPages.first().time
        )
        var streak = 0
        var cursor = if (today in readDays) today else today.minusDays(1)
        while (cursor in readDays) { streak++; cursor = cursor.minusDays(1) }

        // Sunday-first week, as the board draws it: S M T W T F S.
        val sunday = today.minusDays((today.dayOfWeek.value % 7).toLong())
        val weekDays = (0..6).map { sunday.plusDays(it.toLong()) }
        val weekDots = weekDays.map { it in readDays }
        val weekMinutes = weekDays.map { if (it > today) 0 else minutesByDay[it.toEpochDay()] ?: 0 }

        // Per book: last read time, progress snapshot, average seconds per page.
        val lastRead = HashMap<String, Long>()
        val snapshot = HashMap<String, Double>()
        val pagesRead = HashMap<String, Int>()
        val msRead = HashMap<String, Long>()
        for (e in events) {
            val p = pathOf(e)?.takeIf(byPath::containsKey) ?: continue
            if (e.type == 1 || e.type == 0 || e.type == 5) lastRead[p] = maxOf(lastRead[p] ?: 0L, e.time)
            if (e.type == 5) snapshot[p] = e.progressPct
            if (e.type == 1) { pagesRead[p] = (pagesRead[p] ?: 0) + 1; msRead[p] = (msRead[p] ?: 0L) + e.durationMs.coerceIn(0L, PAGE_CAP_MS) }
        }

        val rbooks = inBooks.map { b ->
            val key = norm(b.path)
            val pct = when {
                b.progressTotal > 0 -> 100.0 * b.progressPage / b.progressTotal
                else -> snapshot[key] ?: 0.0
            }
            val finished = b.finished || pct >= FINISHED_AT
            val last = maxOf(b.lastAccess, lastRead[key] ?: 0L).takeIf { it > 0L && (pct > 0.0 || it == (lastRead[key] ?: -1L)) }
            val secPerPage = if ((pagesRead[key] ?: 0) >= 5) (msRead[key] ?: 0L) / 1000.0 / pagesRead[key]!! else 60.0
            val left = if (b.progressTotal > 0 && !finished) ((b.progressTotal - b.progressPage) * secPerPage / 60.0).toInt().coerceAtLeast(1) else null
            val cover = coverDir?.let { File(it, coverKey(b.path) + ".jpg") }?.takeIf { it.exists() } ?: thumbnails[key]
            RBook(
                id = key, path = b.path, title = b.title, authors = b.authors, series = b.series,
                progress = if (finished) 100.0 else pct, pages = b.progressTotal, lastReadAt = last,
                finishedAt = if (finished) (last ?: b.lastAccess) else null, addedAt = b.addedAt, cover = cover, minutesLeft = left
            )
        }

        val reading = rbooks.filter { it.reading }.sortedByDescending { it.lastReadAt ?: 0L }
        val year = today.year
        val finishedYear = rbooks.filter { b -> b.finishedAt != null && dayOf(b.finishedAt, zone).year == year }
            .sortedByDescending { it.finishedAt }
        val unread = rbooks.filter { it.progress == 0.0 && it.finishedAt == null }
        val discover = unread.shuffled(Random(seed + today.toEpochDay())).take(6)
        val recent = rbooks.sortedByDescending { it.addedAt }.take(6)

        val hl = highlights.mapNotNull { h ->
            val p = h.docId?.let { pathOfDoc[it] }
            RHighlight(h.quote, p?.let { path -> rbooks.firstOrNull { it.id == path } }, h.time)
        }
        val todays = hl.takeIf { it.isNotEmpty() }?.let { it[((today.toEpochDay() + seed) % it.size).toInt().let { i -> if (i < 0) i + it.size else i }] }

        return ReadingData(
            now = now, name = name, goalBooks = goalBooks, books = rbooks, continueReading = reading, discover = discover,
            recentlyAdded = recent, finishedThisYear = finishedYear, streakDays = streak, weekMinutes = weekMinutes,
            weekDots = weekDots, todayIndex = today.dayOfWeek.value % 7, minutesByDay = minutesByDay,
            authorCount = rbooks.flatMap { it.authors }.distinct().size, seriesCount = rbooks.mapNotNull { it.series }.distinct().size,
            highlight = todays, highlights = hl, booksByDay = booksByDay, hours = hours, records = records, pageTurns = pageTurns
        )
    }

    /** A gap of more than this between two page turns ends a reading session. */
    const val SESSION_GAP_MS = 10 * 60_000L
    const val HOURS_DAYS = 90

    /** Minutes read in each local hour (0..23) over the last [HOURS_DAYS] days. */
    fun hoursRead(sortedPages: List<NrEvent>, now: Long, zone: ZoneId): List<Int> {
        val ms = LongArray(24)
        val since = now - HOURS_DAYS * 24 * 3_600_000L
        for (e in sortedPages) {
            if (e.time < since) continue
            ms[Instant.ofEpochMilli(e.time).atZone(zone).hour] += e.durationMs.coerceIn(0L, PAGE_CAP_MS)
        }
        return ms.map { (it / 60_000L).toInt() }
    }

    /** Sessions from page turns in time order: count, longest and average length in minutes, and pages an hour. */
    fun sessionRecords(sortedPages: List<NrEvent>): SessionRecords? {
        if (sortedPages.isEmpty()) return null
        val secs = ArrayList<Long>()
        val pages = ArrayList<Int>()
        var curMs = 0L
        var curPages = 0
        var last = Long.MIN_VALUE
        fun close() { if (curPages > 0 && curMs >= 60_000L) { secs += curMs / 1000; pages += curPages } }
        for (e in sortedPages) {
            if (last != Long.MIN_VALUE && e.time - last > SESSION_GAP_MS) { close(); curMs = 0L; curPages = 0 }
            curMs += e.durationMs.coerceIn(0L, PAGE_CAP_MS); curPages++; last = e.time
        }
        close()
        if (secs.isEmpty()) return null
        val total = secs.sum()
        return SessionRecords(
            sessions = secs.size, longestMin = Math.round(secs.max() / 60.0).toInt(), avgMin = Math.round(total / secs.size / 60.0).toInt(),
            pagesPerHour = if (total > 0) Math.round(pages.sum() * 3600.0 / total).toInt() else 0
        )
    }
}
