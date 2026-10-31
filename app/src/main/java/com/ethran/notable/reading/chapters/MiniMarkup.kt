package com.ethran.notable.reading.chapters

/**
 * A forgiving reader of the XML and XHTML found in EPUB files: tags with their attributes, and text. It is not a
 * parser. It never loads a DTD (no network, no entity files), accepts HTML entities such as `&nbsp;`, ignores
 * namespaces (`epub:type` is read as the attribute `type`, `dc:title` as the tag `title`) and does not stop at
 * markup mistakes, which real books are full of. Pure Kotlin, so it runs the same in unit tests and on the device.
 */
internal sealed interface Markup {
    /** [name] and attribute names are lower case without a namespace prefix. */
    class Open(val name: String, val attrs: Map<String, String>, val selfClosing: Boolean) : Markup
    class Close(val name: String) : Markup
    class Text(val text: String) : Markup
}

internal object MiniMarkup {

    fun parse(text: String): List<Markup> {
        val out = ArrayList<Markup>()
        var i = 0
        val n = text.length
        while (i < n) {
            val lt = text.indexOf('<', i)
            if (lt < 0) { addText(out, text.substring(i)); break }
            if (lt > i) addText(out, text.substring(i, lt))
            i = lt
            when {
                text.startsWith("<!--", i) -> { val e = text.indexOf("-->", i + 4); i = if (e < 0) n else e + 3 }
                text.startsWith("<![CDATA[", i) -> {
                    val e = text.indexOf("]]>", i + 9)
                    addRaw(out, text.substring(i + 9, if (e < 0) n else e)); i = if (e < 0) n else e + 3
                }
                text.startsWith("<?", i) -> { val e = text.indexOf("?>", i + 2); i = if (e < 0) n else e + 2 }
                text.startsWith("<!", i) -> i = skipDeclaration(text, i)
                text.startsWith("</", i) -> {
                    val e = text.indexOf('>', i + 2)
                    val end = if (e < 0) n else e
                    val name = localName(text.substring(i + 2, end).trim().takeWhile { !it.isWhitespace() })
                    if (name.isNotEmpty()) out.add(Markup.Close(name))
                    i = if (e < 0) n else e + 1
                }
                i + 1 < n && (text[i + 1].isLetter() || text[i + 1] == '_' || text[i + 1] == ':') -> i = readTag(text, i, out)
                else -> { addText(out, "<"); i++ }
            }
        }
        return out
    }

    private fun skipDeclaration(text: String, from: Int): Int {
        // <!DOCTYPE ...> may hold an internal subset in [ ... ].
        var depth = 0
        var i = from + 2
        while (i < text.length) {
            when (text[i]) {
                '[' -> depth++
                ']' -> depth--
                '>' -> if (depth <= 0) return i + 1
            }
            i++
        }
        return text.length
    }

    private fun readTag(text: String, from: Int, out: MutableList<Markup>): Int {
        var i = from + 1
        val n = text.length
        val nameStart = i
        while (i < n && !text[i].isWhitespace() && text[i] != '>' && text[i] != '/') i++
        val name = localName(text.substring(nameStart, i))
        val attrs = LinkedHashMap<String, String>()
        var selfClosing = false
        while (i < n) {
            while (i < n && text[i].isWhitespace()) i++
            if (i >= n) break
            val c = text[i]
            if (c == '>') { i++; break }
            if (c == '/') { selfClosing = true; i++; continue }
            val aStart = i
            while (i < n && !text[i].isWhitespace() && text[i] != '=' && text[i] != '>' && text[i] != '/') i++
            val key = localName(text.substring(aStart, i))
            while (i < n && text[i].isWhitespace()) i++
            var value = ""
            if (i < n && text[i] == '=') {
                i++
                while (i < n && text[i].isWhitespace()) i++
                if (i < n && (text[i] == '"' || text[i] == '\'')) {
                    val q = text[i]
                    val e = text.indexOf(q, i + 1)
                    val end = if (e < 0) n else e
                    value = text.substring(i + 1, end)
                    i = if (e < 0) n else e + 1
                } else {
                    val vStart = i
                    while (i < n && !text[i].isWhitespace() && text[i] != '>') i++
                    value = text.substring(vStart, i)
                }
            }
            if (key.isNotEmpty() && key !in attrs) attrs[key] = decodeEntities(value)
        }
        out.add(Markup.Open(name, attrs, selfClosing))
        // The content of <script> and <style> is not markup.
        if (!selfClosing && (name == "script" || name == "style")) {
            val close = Regex("</$name\\s*>", RegexOption.IGNORE_CASE).find(text, i)
            if (close != null) { i = close.range.last + 1; out.add(Markup.Close(name)) }
        }
        return i
    }

    private fun localName(s: String): String = s.substringAfterLast(':').lowercase()

    private fun addText(out: MutableList<Markup>, raw: String) {
        if (raw.isEmpty()) return
        out.add(Markup.Text(decodeEntities(raw)))
    }

    private fun addRaw(out: MutableList<Markup>, raw: String) {
        if (raw.isNotEmpty()) out.add(Markup.Text(raw))
    }

    private val NAMED = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to "\u00A0", "ndash" to "\u2013", "mdash" to "\u2014",
        "lsquo" to "\u2018", "rsquo" to "\u2019", "ldquo" to "\u201C", "rdquo" to "\u201D", "hellip" to "\u2026", "copy" to "\u00A9",
        "reg" to "\u00AE", "trade" to "\u2122", "shy" to "", "middot" to "\u00B7", "bull" to "\u2022", "laquo" to "\u00AB", "raquo" to "\u00BB",
        "eacute" to "\u00E9", "egrave" to "\u00E8", "agrave" to "\u00E0", "aacute" to "\u00E1", "uuml" to "\u00FC", "ouml" to "\u00F6",
        "auml" to "\u00E4", "szlig" to "\u00DF", "ccedil" to "\u00E7", "ntilde" to "\u00F1", "thinsp" to "\u2009", "ensp" to "\u2002", "emsp" to "\u2003"
    )

    fun decodeEntities(s: String): String {
        if (s.indexOf('&') < 0) return s
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '&') {
                val e = s.indexOf(';', i + 1)
                if (e in (i + 2)..(i + 10)) {
                    val body = s.substring(i + 1, e)
                    val rep = when {
                        body.startsWith("#x") || body.startsWith("#X") -> body.substring(2).toIntOrNull(16)?.let(::codePoint)
                        body.startsWith("#") -> body.substring(1).toIntOrNull()?.let(::codePoint)
                        else -> NAMED[body]
                    }
                    if (rep != null) { sb.append(rep); i = e + 1; continue }
                }
            }
            sb.append(c)
            i++
        }
        return sb.toString()
    }

    private fun codePoint(cp: Int): String? =
        if (cp in 1..0x10FFFF && cp !in 0xD800..0xDFFF) String(Character.toChars(cp)) else null

    /** Decodes bytes of an XML file: a byte order mark or the declared encoding, else UTF-8. */
    fun decodeBytes(b: ByteArray): String {
        if (b.size >= 3 && b[0] == 0xEF.toByte() && b[1] == 0xBB.toByte() && b[2] == 0xBF.toByte()) return String(b, 3, b.size - 3, Charsets.UTF_8)
        if (b.size >= 2 && b[0] == 0xFE.toByte() && b[1] == 0xFF.toByte()) return String(b, 2, b.size - 2, Charsets.UTF_16BE)
        if (b.size >= 2 && b[0] == 0xFF.toByte() && b[1] == 0xFE.toByte()) return String(b, 2, b.size - 2, Charsets.UTF_16LE)
        val head = String(b, 0, minOf(b.size, 200), Charsets.ISO_8859_1)
        val declared = Regex("encoding\\s*=\\s*[\"']([A-Za-z0-9_.:-]+)[\"']").find(head)?.groupValues?.get(1)
        val cs = declared?.let { runCatching { java.nio.charset.Charset.forName(it) }.getOrNull() } ?: Charsets.UTF_8
        return String(b, cs)
    }
}
