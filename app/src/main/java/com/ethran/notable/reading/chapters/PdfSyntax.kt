package com.ethran.notable.reading.chapters

import java.io.ByteArrayOutputStream
import java.io.RandomAccessFile

/*
 * The parts of the PDF file syntax needed to find a document's bookmarks: objects, the lexer and a byte source.
 * Nothing here draws or decodes page content. Pure Kotlin (no Android calls), so it is unit tested on the JVM.
 */

internal class PdfName(val value: String) {
    override fun equals(other: Any?) = other is PdfName && other.value == value
    override fun hashCode() = value.hashCode()
    override fun toString() = "/$value"
}

internal class PdfString(val bytes: ByteArray)

internal data class PdfRef(val num: Int, val gen: Int)

internal class PdfKeyword(val value: String)

internal class PdfDict(val map: Map<String, Any?>) {
    operator fun get(key: String): Any? = map[key]
}

/** A stream: its dictionary and its raw (still encoded) bytes. */
internal class PdfStream(val dict: PdfDict, val raw: ByteArray)

/** The file is not a PDF the reader can follow (damaged, or a feature it does not handle). */
internal open class PdfFormatException(message: String) : Exception(message)

/** The PDF is encrypted: its strings cannot be read without decrypting them. */
internal class PdfEncryptedException : PdfFormatException("encrypted")

/** The lexer ran into the end of its window in the middle of an object: read a bigger window and try again. */
internal class PdfEndOfData : PdfFormatException("end of data")

/** Random access to the bytes of a file (or of an array, for objects read out of an object stream and for tests). */
internal interface PdfSource {
    val size: Long

    /** Up to [len] bytes from [pos]; fewer at the end of the file, empty past it. */
    fun read(pos: Long, len: Int): ByteArray
}

internal class ByteArraySource(private val data: ByteArray) : PdfSource {
    override val size: Long get() = data.size.toLong()
    override fun read(pos: Long, len: Int): ByteArray {
        if (pos < 0 || pos >= data.size || len <= 0) return ByteArray(0)
        val end = minOf(data.size.toLong(), pos + len).toInt()
        return data.copyOfRange(pos.toInt(), end)
    }
}

internal class FileSource(private val file: RandomAccessFile) : PdfSource {
    override val size: Long = file.length()
    override fun read(pos: Long, len: Int): ByteArray {
        if (pos < 0 || pos >= size || len <= 0) return ByteArray(0)
        val n = minOf(len.toLong(), size - pos).toInt()
        val buf = ByteArray(n)
        file.seek(pos)
        file.readFully(buf)
        return buf
    }
}

/** Reads PDF objects from [buf] starting at [pos]. [atEof] says the window ends where the file does. */
internal class PdfLexer(val buf: ByteArray, var pos: Int = 0, private val atEof: Boolean = true) {

    companion object {
        fun isWhite(b: Int) = b == 0 || b == 9 || b == 10 || b == 12 || b == 13 || b == 32
        fun isDelim(b: Int) = b == '('.code || b == ')'.code || b == '<'.code || b == '>'.code || b == '['.code || b == ']'.code ||
            b == '{'.code || b == '}'.code || b == '/'.code || b == '%'.code
        fun isRegular(b: Int) = !isWhite(b) && !isDelim(b)
        private const val MAX_DEPTH = 40
    }

    private fun peekByte(): Int = if (pos < buf.size) buf[pos].toInt() and 0xFF else -1

    private fun needMore(): Nothing = if (atEof) throw PdfFormatException("unexpected end of file") else throw PdfEndOfData()

    fun skipWhite() {
        while (pos < buf.size) {
            val b = buf[pos].toInt() and 0xFF
            if (isWhite(b)) pos++
            else if (b == '%'.code) { while (pos < buf.size && buf[pos].toInt() != 10 && buf[pos].toInt() != 13) pos++ }
            else return
        }
    }

    /** True when the next token (after white space) starts with [word]. Does not move. */
    fun lookingAt(word: String): Boolean {
        skipWhite()
        if (pos + word.length > buf.size) { if (!atEof) needMore(); return false }
        for (i in word.indices) if (buf[pos + i].toInt() != word[i].code) return false
        return true
    }

    /** Skips [word] when it is next, and says whether it was. */
    fun accept(word: String): Boolean {
        if (!lookingAt(word)) return false
        pos += word.length
        return true
    }

    /** The next object, or a [PdfKeyword] for a bare word such as `obj`, `stream` or `endobj`. */
    fun readObject(depth: Int = 0): Any? {
        if (depth > MAX_DEPTH) throw PdfFormatException("nesting too deep")
        skipWhite()
        if (pos >= buf.size) needMore()
        val b = buf[pos].toInt() and 0xFF
        return when {
            b == '/'.code -> readName()
            b == '('.code -> readLiteralString()
            b == '<'.code -> if (pos + 1 < buf.size && buf[pos + 1].toInt() == '<'.code) readDict(depth) else readHexString()
            b == '['.code -> readArray(depth)
            b == ']'.code || b == '>'.code || b == ')'.code || b == '{'.code || b == '}'.code -> throw PdfFormatException("unexpected '${b.toChar()}' at $pos")
            else -> readNumberOrKeyword()
        }
    }

    private fun readName(): PdfName {
        pos++ // '/'
        val out = StringBuilder()
        while (pos < buf.size) {
            val b = buf[pos].toInt() and 0xFF
            if (!isRegular(b)) break
            if (b == '#'.code && pos + 2 < buf.size && hexVal(buf[pos + 1].toInt()) >= 0 && hexVal(buf[pos + 2].toInt()) >= 0) {
                out.append((hexVal(buf[pos + 1].toInt()) * 16 + hexVal(buf[pos + 2].toInt())).toChar())
                pos += 3
            } else { out.append(b.toChar()); pos++ }
        }
        if (pos >= buf.size && !atEof) needMore()
        return PdfName(out.toString())
    }

    private fun hexVal(c: Int): Int = when (c) {
        in '0'.code..'9'.code -> c - '0'.code
        in 'a'.code..'f'.code -> c - 'a'.code + 10
        in 'A'.code..'F'.code -> c - 'A'.code + 10
        else -> -1
    }

    private fun readLiteralString(): PdfString {
        pos++ // '('
        val out = ByteArrayOutputStream()
        var depth = 1
        while (true) {
            if (pos >= buf.size) needMore()
            var b = buf[pos++].toInt() and 0xFF
            when (b) {
                '('.code -> { depth++; out.write(b) }
                ')'.code -> { depth--; if (depth == 0) return PdfString(out.toByteArray()) else out.write(b) }
                '\\'.code -> {
                    if (pos >= buf.size) needMore()
                    b = buf[pos++].toInt() and 0xFF
                    when (b) {
                        'n'.code -> out.write(10)
                        'r'.code -> out.write(13)
                        't'.code -> out.write(9)
                        'b'.code -> out.write(8)
                        'f'.code -> out.write(12)
                        13 -> { if (pos < buf.size && buf[pos].toInt() == 10) pos++ }
                        10 -> {}
                        in '0'.code..'7'.code -> {
                            var v = b - '0'.code
                            var n = 1
                            while (n < 3 && pos < buf.size && buf[pos].toInt() in '0'.code..'7'.code) { v = v * 8 + (buf[pos].toInt() - '0'.code); pos++; n++ }
                            out.write(v and 0xFF)
                        }
                        else -> out.write(b)
                    }
                }
                else -> out.write(b)
            }
        }
    }

    private fun readHexString(): PdfString {
        pos++ // '<'
        val out = ByteArrayOutputStream()
        var hi = -1
        while (true) {
            if (pos >= buf.size) needMore()
            val b = buf[pos++].toInt() and 0xFF
            if (b == '>'.code) break
            val v = hexVal(b)
            if (v < 0) continue
            if (hi < 0) hi = v else { out.write(hi * 16 + v); hi = -1 }
        }
        if (hi >= 0) out.write(hi * 16)
        return PdfString(out.toByteArray())
    }

    private fun readArray(depth: Int): List<Any?> {
        pos++ // '['
        val list = ArrayList<Any?>()
        while (true) {
            skipWhite()
            if (pos >= buf.size) needMore()
            if (buf[pos].toInt() == ']'.code) { pos++; return list }
            val o = readObject(depth + 1)
            if (o is PdfKeyword) throw PdfFormatException("unexpected '${o.value}' in array")
            list.add(o)
        }
    }

    private fun readDict(depth: Int): PdfDict {
        pos += 2 // '<<'
        val map = LinkedHashMap<String, Any?>()
        while (true) {
            skipWhite()
            if (pos >= buf.size) needMore()
            if (buf[pos].toInt() == '>'.code) {
                if (pos + 1 >= buf.size) needMore()
                if (buf[pos + 1].toInt() == '>'.code) { pos += 2; return PdfDict(map) }
                throw PdfFormatException("stray '>' in dictionary")
            }
            val key = readObject(depth + 1)
            if (key !is PdfName) {
                if (key is PdfKeyword) throw PdfFormatException("unexpected '${key.value}' in dictionary")
                continue // a stray value: skip it
            }
            skipWhite()
            if (pos >= buf.size) needMore()
            if (buf[pos].toInt() == '>'.code) { map[key.value] = null; continue }
            val v = readObject(depth + 1)
            if (v is PdfKeyword) throw PdfFormatException("unexpected '${v.value}' in dictionary")
            map[key.value] = v
        }
    }

    private fun readNumberOrKeyword(): Any? {
        val start = pos
        while (pos < buf.size && isRegular(buf[pos].toInt() and 0xFF)) pos++
        if (pos >= buf.size && !atEof) needMore()
        if (pos == start) { pos++; throw PdfFormatException("bad byte at $start") }
        val word = String(buf, start, pos - start, Charsets.ISO_8859_1)
        when (word) {
            "true" -> return true
            "false" -> return false
            "null" -> return null
        }
        val num = parseNumber(word) ?: return PdfKeyword(word)
        if (num is Long && num >= 0) {
            // "12 0 R" is a reference; otherwise the 12 stands alone.
            val save = pos
            skipWhite()
            val s2 = pos
            while (pos < buf.size && buf[pos].toInt() in '0'.code..'9'.code) pos++
            if (pos > s2 && pos - s2 <= 6) {
                val gen = String(buf, s2, pos - s2, Charsets.ISO_8859_1).toInt()
                skipWhite()
                if (pos < buf.size && buf[pos].toInt() == 'R'.code && (pos + 1 >= buf.size || !isRegular(buf[pos + 1].toInt() and 0xFF))) {
                    pos++
                    if (num <= Int.MAX_VALUE) return PdfRef(num.toInt(), gen)
                } else if (pos >= buf.size && !atEof) needMore()
            } else if (pos >= buf.size && !atEof) needMore()
            pos = save
        }
        return num
    }

    private fun parseNumber(w: String): Number? {
        if (w.isEmpty()) return null
        var i = 0
        if (w[0] == '+' || w[0] == '-') i++
        if (i >= w.length) return null
        var dots = 0
        var digits = 0
        for (k in i until w.length) {
            val c = w[k]
            when {
                c in '0'..'9' -> digits++
                c == '.' -> dots++
                // Some writers emit "--5" or "5-3"; reject them (they are not numbers we need).
                else -> return null
            }
        }
        if (digits == 0 || dots > 1) return null
        return if (dots == 0) w.toLongOrNull() else w.toDoubleOrNull()
    }
}

/** Numbers of either kind as Int, or null. */
internal fun Any?.asInt(): Int? = when (this) {
    is Long -> if (this in Int.MIN_VALUE..Int.MAX_VALUE) toInt() else null
    is Double -> toInt()
    else -> null
}
