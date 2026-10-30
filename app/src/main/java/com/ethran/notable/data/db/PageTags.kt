package com.ethran.notable.data.db

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tags the user attaches to a note from the Home screen (separate from the #tag boxes drawn inside a note).
 * Stored in the key-value table as `pagetags:<pageId>` = one tag per line, so no schema change is needed.
 * They end up in the front matter of the exported Markdown note.
 */
@Singleton
class PageTagRepository @Inject constructor(private val dao: KvDao) {

    suspend fun get(pageId: String): List<String> = dao.get(KEY + pageId)?.value?.let(::decode).orEmpty()

    suspend fun all(): Map<String, List<String>> =
        dao.getWithPrefix(KEY).associate { it.key.removePrefix(KEY) to decode(it.value) }.filterValues { it.isNotEmpty() }

    suspend fun set(pageId: String, tags: List<String>) {
        val clean = tags.map(::normalize).filter { it.isNotEmpty() }.distinct()
        if (clean.isEmpty()) dao.delete(KEY + pageId) else dao.set(Kv(KEY + pageId, clean.joinToString("\n")))
    }

    suspend fun clear(pageId: String) = dao.delete(KEY + pageId)

    // ---------- deleting a topic ----------

    /** Topics the user deleted. A #tag box drawn inside a note cannot be removed from here, so the topic is hidden instead. */
    suspend fun hidden(): Set<String> = dao.get(HIDDEN_KEY)?.value?.let(::decode)?.toSet().orEmpty()

    suspend fun unhide(tag: String) {
        val clean = normalize(tag)
        val now = hidden()
        if (clean in now) dao.set(Kv(HIDDEN_KEY, (now - clean).joinToString("\n")))
    }

    /** Takes the tag off every note and hides the topic on Home. The notes themselves stay. */
    suspend fun deleteTopic(tag: String) {
        for ((pageId, tags) in all()) if (tag in tags) set(pageId, tags - tag)
        dao.set(Kv(HIDDEN_KEY, (hidden() + tag).joinToString("\n")))
    }

    companion object {
        private const val KEY = "pagetags:"
        private const val HIDDEN_KEY = "hiddentopics"
        private fun decode(value: String) = value.lines().map { it.trim() }.filter { it.isNotEmpty() }

        /** "#Yeni Fikir" -> "Yeni-Fikir": one word, no hash, as it appears in the note's front matter. */
        fun normalize(tag: String) = tag.trim().removePrefix("#").trim().replace(Regex("\\s+"), "-")
    }
}
