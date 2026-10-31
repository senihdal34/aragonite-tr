package com.ethran.notable.reading.badges

import android.content.SharedPreferences

/** When a badge was first seen earned. [backlog] marks one found when the store was first filled, which is not news. */
data class EarnedRecord(val at: Long, val backlog: Boolean = false)

/** Where the first time each badge was seen earned is kept, so a date never changes or disappears. */
interface BadgeStore {
    fun load(): Map<String, EarnedRecord>
    fun save(records: Map<String, EarnedRecord>)
}

/** A store in memory, for tests. */
class InMemoryBadgeStore(initial: Map<String, EarnedRecord> = emptyMap()) : BadgeStore {
    var records: Map<String, EarnedRecord> = initial
        private set
    var saves = 0
        private set

    override fun load(): Map<String, EarnedRecord> = records
    override fun save(records: Map<String, EarnedRecord>) { this.records = records.toMap(); saves++ }
}

/** The records as one text, a line each ("key|ms|0 or 1"): plain enough to read, and nothing that needs Android to parse. */
object BadgeStoreCodec {
    fun encode(records: Map<String, EarnedRecord>): String =
        records.entries.joinToString("\n") { (key, r) -> "$key|${r.at}|${if (r.backlog) 1 else 0}" }

    /** Lines that cannot be read are skipped, so one bad line never loses the others. */
    fun decode(text: String?): Map<String, EarnedRecord> {
        if (text.isNullOrBlank()) return emptyMap()
        val out = LinkedHashMap<String, EarnedRecord>()
        for (line in text.lineSequence()) {
            val parts = line.split('|')
            if (parts.size != 3 || parts[0].isBlank()) continue
            val at = parts[1].toLongOrNull() ?: continue
            out[parts[0]] = EarnedRecord(at, parts[2] == "1")
        }
        return out
    }
}

/** The records in SharedPreferences (the board's own file), under one key. */
class PrefsBadgeStore(private val prefs: SharedPreferences, private val key: String = KEY) : BadgeStore {
    override fun load(): Map<String, EarnedRecord> = BadgeStoreCodec.decode(prefs.getString(key, null))
    override fun save(records: Map<String, EarnedRecord>) { prefs.edit().putString(key, BadgeStoreCodec.encode(records)).apply() }

    companion object {
        const val KEY = "badges_earned"
    }
}
