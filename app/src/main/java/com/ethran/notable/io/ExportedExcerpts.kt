package com.ethran.notable.io

import android.os.Environment
import com.ethran.notable.data.db.Page
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Short preview text for the Home screen, taken from the markdown that Inbox Sync already wrote
 * for a page. Nothing extra is stored in the database: no export yet means no preview.
 */
object ExportedExcerpts {
    private const val MAX_CHARS = 170

    fun inboxDir(inboxPath: String): File =
        if (inboxPath.startsWith("/")) File(inboxPath)
        else File(Environment.getExternalStorageDirectory(), inboxPath)

    /** Page id -> excerpt, for the pages that have an exported note with more than a title line. */
    fun load(dir: File, pages: List<Page>): Map<String, String> {
        val files = dir.listFiles { f -> f.isFile && f.extension == "md" } ?: return emptyMap()
        val stamp = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.US)
        val result = mutableMapOf<String, String>()
        for (page in pages) {
            val suffix = stamp.format(page.createdAt) + ".md"
            val file = files.firstOrNull { it.name == suffix || it.name.endsWith("-$suffix") } ?: continue
            val text = try { file.readText() } catch (_: Exception) { continue }
            excerptFrom(text).takeIf { it.isNotBlank() }?.let { result[page.id] = it }
        }
        return result
    }

    /** Text lines after the first one (the headline), without markdown noise. */
    internal fun excerptFrom(markdown: String): String {
        val lines = markdown.lines()
        var i = 0
        if (lines.firstOrNull()?.trim() == "---") {                  // skip front matter
            i = lines.drop(1).indexOfFirst { it.trim() == "---" }.let { if (it < 0) 0 else it + 2 }
        }
        val body = lines.drop(i).takeWhile { it.trim() != "---" }.map { it.trim() }.filter { it.isNotEmpty() }
        val rest = body.drop(1)                                      // first line is the headline
        val clean = rest.joinToString("  ") { line ->
            line.replace("~~", "").replace("[[", "").replace("]]", "")
                .replace(Regex("^[-•*]\\s+"), "").replace(Regex("^_.*could not be recognized.*_$"), "")
        }.replace(Regex("\\s+"), " ").trim()
        return if (clean.length <= MAX_CHARS) clean else clean.take(MAX_CHARS).trimEnd() + "…"
    }
}
