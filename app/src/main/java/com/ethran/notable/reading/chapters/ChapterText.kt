package com.ethran.notable.reading.chapters

/** Chapter titles as the board shows them. */
object TitleText {
    /** A title longer than this is cut when the file is read (nobody reads a 400 character bookmark). */
    const val MAX_STORED = 160

    /** The longest title a label under the line may have before it is shortened to a word boundary. */
    const val LABEL_MAX = 22

    /**
     * A title cleaned up for display: control characters, zero-width and soft-hyphen characters dropped, every kind of
     * space (including non-breaking ones) folded into one plain space, ends trimmed, and cut at [MAX_STORED].
     */
    fun clean(raw: String): String {
        val sb = StringBuilder(raw.length)
        var lastSpace = true
        for (ch in raw) {
            val c = ch.code
            when {
                c == 0xAD || c in 0x200B..0x200F || c == 0xFEFF || c == 0x2060 -> {}
                ch.isWhitespace() || c == 0xA0 || c in 0x2000..0x200A || c == 0x202F || c == 0x3000 || c < 0x20 || c == 0x7F -> {
                    if (!lastSpace) { sb.append(' '); lastSpace = true }
                }
                else -> { sb.append(ch); lastSpace = false }
            }
        }
        val s = sb.toString().trimEnd()
        return if (s.length > MAX_STORED) shorten(s, MAX_STORED) else s
    }

    /**
     * [title] cut to at most [max] characters at a word boundary, with an ellipsis. A single word longer than [max] is cut
     * inside the word. A title that already fits is returned as it is.
     */
    fun shorten(title: String, max: Int = LABEL_MAX): String {
        if (max < 2 || title.length <= max) return title
        val room = max - 1
        val cut = title.lastIndexOf(' ', room)
        // Cut at the last space, unless that would throw away more than half of the room.
        val end = if (cut >= room / 2) cut else room
        return title.substring(0, end).trimEnd(' ', ',', ';', ':', '-', '–', '—', '.') + "…"
    }
}
