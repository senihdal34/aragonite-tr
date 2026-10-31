package com.ethran.notable.reading.chapters

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest

/** What the board knows about the chapters of the book it shows. */
sealed interface ChapterState {
    /** Nothing asked for yet (or no book). */
    data object Idle : ChapterState
    data class Loading(val bookId: String) : ChapterState
    data class Ready(val bookId: String, val map: ChapterMap) : ChapterState
    data class Empty(val bookId: String, val reason: NoChapters) : ChapterState

    val bookIdOrNull: String?
        get() = when (this) {
            is Idle -> null
            is Loading -> bookId
            is Ready -> bookId
            is Empty -> bookId
        }
}

@Serializable
internal data class CachedChapter(val t: String, val d: Int, val f: Double, val p: Int = 0)

@Serializable
internal data class CachedResult(
    val version: Int, val path: String, val size: Long, val mtime: Long,
    /** "found", or the [NoChapters] name. */
    val status: String, val source: String = "", val pages: Int = 0, val entries: List<CachedChapter> = emptyList()
)

/**
 * Chapter maps of books, read from the book files once and kept as JSON in [dir] (one small file per book, named by a hash
 * of its path, size and modification time, so a changed file is read again and an unchanged one never is). Also remembers
 * results in memory, "no chapters" included, so a PDF without bookmarks is not opened again on every refresh of the board.
 *
 * Blocking file I/O: call [load] on Dispatchers.IO. Safe to call from several threads.
 */
class ChapterRepository(private val dir: File, private val reader: (File) -> ChapterResult = ChapterReader::read) {
    private val memory = HashMap<String, ChapterResult>()
    private val json = Json { ignoreUnknownKeys = true }

    @Synchronized
    fun load(file: File): ChapterResult {
        val size = file.length()
        val mtime = file.lastModified()
        val key = keyOf(file.path, size, mtime)
        memory[key]?.let { return it }
        readCache(key, file.path, size, mtime)?.let { memory[key] = it; return it }
        val result = try { reader(file) } catch (e: RuntimeException) { ChapterResult.None(NoChapters.Unreadable) }
        memory[key] = result
        writeCache(key, file.path, size, mtime, result)
        return result
    }

    private fun readCache(key: String, path: String, size: Long, mtime: Long): ChapterResult? = try {
        val f = File(dir, "$key.json")
        if (!f.isFile) null
        else {
            val c = json.decodeFromString<CachedResult>(f.readText())
            if (c.version != VERSION || c.path != path || c.size != size || c.mtime != mtime) null
            else if (c.status == "found") {
                val source = ChapterSource.entries.firstOrNull { it.name == c.source }
                if (source == null || c.entries.isEmpty()) null
                else ChapterResult.Found(ChapterMap(source, c.pages, c.entries.map { Chapter(it.t, it.d, it.f, it.p.takeIf { p -> p > 0 }) }))
            } else NoChapters.entries.firstOrNull { it.name == c.status }?.let { ChapterResult.None(it) }
        }
    } catch (e: Exception) {
        null   // a damaged cache file is read from the book again
    }

    private fun writeCache(key: String, path: String, size: Long, mtime: Long, r: ChapterResult) {
        try {
            dir.mkdirs()
            val c = when (r) {
                is ChapterResult.Found -> CachedResult(
                    VERSION, path, size, mtime, "found", r.map.source.name, r.map.pages,
                    r.map.entries.map { CachedChapter(it.title, it.depth, it.startFraction, it.startPage ?: 0) }
                )
                is ChapterResult.None -> CachedResult(VERSION, path, size, mtime, r.reason.name)
            }
            val tmp = File(dir, "$key.json.tmp")
            tmp.writeText(json.encodeToString(c))
            if (!tmp.renameTo(File(dir, "$key.json"))) tmp.delete()
            prune()
        } catch (e: Exception) {
            // A full disk or a read-only folder only means the book is read again next time.
        }
    }

    private fun prune() {
        val files = dir.listFiles { f -> f.name.endsWith(".json") } ?: return
        if (files.size <= KEEP) return
        files.sortedByDescending { it.lastModified() }.drop(KEEP).forEach { it.delete() }
    }

    companion object {
        /** Bump when the readers change what they find, so old results are read again. */
        const val VERSION = 1
        const val KEEP = 80

        fun keyOf(path: String, size: Long, mtime: Long): String =
            MessageDigest.getInstance("SHA-1").digest("$path|$size|$mtime".toByteArray()).joinToString("") { "%02x".format(it) }

        /** The [ChapterState] for a book once its [result] is in. */
        fun stateOf(bookId: String, result: ChapterResult): ChapterState = when (result) {
            is ChapterResult.Found -> ChapterState.Ready(bookId, result.map)
            is ChapterResult.None -> ChapterState.Empty(bookId, result.reason)
        }
    }
}
