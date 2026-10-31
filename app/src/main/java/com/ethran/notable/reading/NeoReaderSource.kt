package com.ethran.notable.reading

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.util.Log

/** One book as NeoReader's library knows it. */
data class NrBook(
    val path: String,
    val title: String,
    val authors: List<String>,
    val series: String?,
    val progressPage: Int,
    val progressTotal: Int,
    val lastAccess: Long,
    val addedAt: Long,
    val finished: Boolean,
    val hash: String?
)

/** One row of NeoReader's reading log. type 1 = a page read (durationMs on that page), type 5 = progress snapshot. */
data class NrEvent(
    val type: Int, val time: Long, val docId: String?, val md5: String?, val durationMs: Long, val page: Int,
    val progressPct: Double, val sid: String?, val path: String?
)

/** A highlight the reader made. */
data class NrHighlight(val quote: String, val time: Long, val md5: String?, val docId: String?)

/**
 * Reads NeoReader's own library, reading log and highlights. Every call returns what it could read: a missing provider
 * or a changed column gives an empty list and a log line, never a crash.
 */
object NeoReaderSource {
    private const val TAG = "NeoReaderSource"
    private val LIBRARY = Uri.parse("content://com.onyx.content.database.ContentProvider/Metadata")
    private val ANNOTATION = Uri.parse("content://com.onyx.content.database.ContentProvider/Annotation")
    private val STATS = Uri.parse("content://com.onyx.kreader.statistics.provider/OnyxStatisticsModel")

    private fun Cursor.str(name: String): String? = getColumnIndex(name).takeIf { it >= 0 }?.let { if (isNull(it)) null else getString(it) }
    private fun Cursor.lng(name: String): Long = str(name)?.toLongOrNull() ?: 0L

    private inline fun <T> query(context: Context, uri: Uri, selection: String? = null, map: (Cursor) -> T?): List<T> =
        try {
            context.contentResolver.query(uri, null, selection, null, null)?.use { c ->
                buildList { while (c.moveToNext()) map(c)?.let(::add) }
            } ?: emptyList()
        } catch (e: Exception) {
            Log.w(TAG, "query $uri failed: ${e.javaClass.simpleName}: ${e.message}")
            emptyList()
        }

    fun library(context: Context): List<NrBook> = query(context, LIBRARY) { c ->
        val path = c.str("nativeAbsolutePath") ?: return@query null
        val progress = c.str("progress")?.split("/")
        val page = progress?.getOrNull(0)?.toIntOrNull() ?: 0
        val total = progress?.getOrNull(1)?.toIntOrNull() ?: 0
        NrBook(
            path = path,
            title = c.str("title")?.takeIf { it.isNotBlank() } ?: c.str("name").orEmpty().substringBeforeLast('.'),
            authors = c.str("authors")?.split(",", ";", "\n")?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty(),
            series = c.str("series")?.takeIf { it.isNotBlank() },
            progressPage = page, progressTotal = total,
            lastAccess = c.lng("lastAccess"), addedAt = c.lng("createdAt"),
            finished = c.str("readingStatus") == "2" || (total > 0 && page >= total),
            hash = c.str("hashTag")
        )
    }

    fun events(context: Context): List<NrEvent> = query(context, STATS) { c ->
        if (c.str("action") == "delete") return@query null
        NrEvent(
            type = c.str("type")?.toIntOrNull() ?: return@query null,
            time = c.lng("eventTime"), docId = c.str("docId"), md5 = c.str("md5"),
            durationMs = c.lng("durationTime"), page = c.str("currPage")?.toIntOrNull() ?: 0,
            progressPct = c.str("readingProgress")?.toDoubleOrNull() ?: 0.0, sid = c.str("sid"), path = c.str("path")
        )
    }

    fun highlights(context: Context): List<NrHighlight> = query(context, ANNOTATION) { c ->
        val q = c.str("quote")?.trim()?.takeIf { it.length > 20 } ?: return@query null
        NrHighlight(q, c.lng("createdAt"), c.str("md5"), c.str("docId"))
    }

    /** Spike: logs what could be read, so a run on the tablet shows whether another app may use these providers. */
    fun probe(context: Context) {
        val b = library(context); val e = events(context); val h = highlights(context)
        Log.i(TAG, "probe: library=${b.size} events=${e.size} (pages=${e.count { it.type == 1 }}) highlights=${h.size}")
    }
}
