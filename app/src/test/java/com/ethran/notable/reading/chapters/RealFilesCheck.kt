package com.ethran.notable.reading.chapters

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * An opt-in check against real books, not a unit test: it does nothing unless CHAPTERS_REAL_LIST names a text file with one
 * book path per line. It reads each one with the production readers and writes one JSON object per line to
 * CHAPTERS_REAL_OUT (path, kind, milliseconds, pages, entries), so another tool (PyMuPDF for PDFs) can compare.
 *
 *   CHAPTERS_REAL_LIST=/tmp/list.txt CHAPTERS_REAL_OUT=/tmp/out.jsonl ./gradlew test --tests '*RealFilesCheck*'
 */
class RealFilesCheck {
    @Test
    fun readRealBooks() {
        val list = System.getenv("CHAPTERS_REAL_LIST")
        val out = System.getenv("CHAPTERS_REAL_OUT")
        assumeTrue(list != null && out != null)
        val sink = File(out!!).bufferedWriter()
        sink.use { w ->
            for (line in File(list!!).readLines()) {
                val f = File(line.trim())
                if (!f.isFile) continue
                val t0 = System.nanoTime()
                val obj: JsonObject = when (f.extension.lowercase()) {
                    "pdf" -> when (val r = PdfOutline.read(f)) {
                        is PdfOutlineResult.Found -> buildJsonObject {
                            put("kind", "found"); put("pages", r.pages)
                            put("entries", JsonArray(r.bookmarks.map { b -> buildJsonArray { add(JsonPrimitive(b.depth)); add(JsonPrimitive(b.title)); add(JsonPrimitive(b.page + 1)) } }))
                        }
                        is PdfOutlineResult.NoOutline -> buildJsonObject { put("kind", "none") }
                        is PdfOutlineResult.Unreadable -> buildJsonObject { put("kind", "unreadable"); put("why", r.why) }
                    }
                    else -> when (val r = EpubChapters.read(f)) {
                        is ChapterResult.Found -> buildJsonObject {
                            put("kind", "found"); put("pages", 0)
                            put("entries", JsonArray(r.map.entries.map { e -> buildJsonArray { add(JsonPrimitive(e.depth)); add(JsonPrimitive(e.title)); add(JsonPrimitive(e.startFraction)) } }))
                        }
                        is ChapterResult.None -> buildJsonObject { put("kind", r.reason.name) }
                    }
                }
                val ms = (System.nanoTime() - t0) / 1_000_000
                w.write(JsonObject(obj + mapOf("path" to JsonPrimitive(f.path), "ms" to JsonPrimitive(ms))).toString())
                w.newLine()
            }
        }
    }
}
