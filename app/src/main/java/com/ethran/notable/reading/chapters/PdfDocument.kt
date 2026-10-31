package com.ethran.notable.reading.chapters

import java.io.ByteArrayOutputStream
import java.util.zip.Inflater

/**
 * The structure of a PDF: its cross-reference data (classic tables, xref streams, hybrids and incremental updates, with a
 * scan of the whole file as a last resort), indirect objects and object streams. It reads only the objects asked for, so
 * a 600-page book costs a few small reads, not a pass over its page images.
 *
 * Not supported on purpose: encryption ([PdfEncryptedException]) and stream filters other than Flate.
 */
internal class PdfDocument private constructor(private val src: PdfSource) {

    private sealed interface Loc
    private class InFile(val pos: Long) : Loc
    private class InObjStm(val stream: Int, val index: Int) : Loc

    private var shift = 0L                 // bytes before "%PDF-" (offsets in the file count from the header)
    private val xref = HashMap<Int, Loc>()
    private var trailer: PdfDict = PdfDict(emptyMap())
    private val cache = HashMap<Int, Any?>()
    private val objStms = LinkedHashMap<Int, ObjStm>()
    private var crypto: PdfCrypto? = null
    private var encryptObj = -1               // the object number of /Encrypt when it is indirect: it is never decrypted

    private class ObjStm(val data: ByteArray, val nums: IntArray, val offsets: IntArray, val first: Int)

    val root: PdfDict? get() = resolve(trailer["Root"]) as? PdfDict

    companion object {
        const val MAX_WINDOW = 16 * 1024 * 1024
        const val MAX_STREAM = 64 * 1024 * 1024
        /** A damaged file is scanned whole to find its objects; a bigger one than this is given up on. */
        const val MAX_SCAN = 400L * 1024 * 1024

        /** Opens [src], following its xref data; when that is damaged, rebuilds it by scanning. Throws [PdfFormatException]. */
        fun open(src: PdfSource): PdfDocument {
            val doc = PdfDocument(src)
            doc.findHeader()
            var ok = false
            try {
                doc.loadXrefChain()
                doc.setupCrypto()
                ok = doc.sane()
            } catch (e: PdfEncryptedException) {
                throw e
            } catch (e: PdfFormatException) {
                ok = false
            } catch (e: RuntimeException) {
                ok = false
            }
            if (!ok) {
                doc.xref.clear(); doc.cache.clear(); doc.objStms.clear(); doc.crypto = null
                doc.rebuild()
                if (!doc.sane()) throw PdfFormatException("no catalog")
            }
            return doc
        }
    }

    /** Sets up decryption when the trailer has /Encrypt (an empty user password only), and forgets what was read before it. */
    private fun setupCrypto() {
        val e = trailer["Encrypt"] ?: return
        encryptObj = (e as? PdfRef)?.num ?: -1
        val dict = resolve(e) as? PdfDict ?: throw PdfEncryptedException()
        val id = ((resolve(trailer["ID"]) as? List<*>)?.firstOrNull()?.let { resolve(it) } as? PdfString)?.bytes ?: ByteArray(0)
        crypto = PdfCrypto.open(dict, id) { resolve(it) }
        cache.clear(); objStms.clear()
    }

    private fun sane(): Boolean = try {
        val r = root
        r != null && r["Pages"] != null
    } catch (e: PdfEncryptedException) { throw e } catch (e: Exception) { false }

    // ----------------------------------------------------------------------------------------- reading at a place
    /** Runs [block] on a lexer at [pos], widening the window until the object fits. [block] gets the window start too. */
    private fun <T> at(pos: Long, initial: Int = 8192, block: (PdfLexer, Long) -> T): T {
        var len = initial
        while (true) {
            val buf = src.read(pos, len)
            if (buf.isEmpty()) throw PdfFormatException("read past the end at $pos")
            val atEof = pos + buf.size >= src.size
            try {
                return block(PdfLexer(buf, 0, atEof), pos)
            } catch (e: PdfEndOfData) {
                if (len >= MAX_WINDOW) throw PdfFormatException("object too large at $pos")
                len = minOf(MAX_WINDOW, len * 4)
            }
        }
    }

    private fun findHeader() {
        val head = src.read(0, 1024)
        val i = indexOf(head, "%PDF-".toByteArray(), 0)
        shift = if (i > 0) i.toLong() else 0L
    }

    // --------------------------------------------------------------------------------------------- xref
    private fun loadXrefChain() {
        val tail = src.read(maxOf(0L, src.size - 2048), 2048)
        val k = lastIndexOf(tail, "startxref".toByteArray())
        if (k < 0) throw PdfFormatException("no startxref")
        val first = PdfLexer(tail, k + 9, true).readObject() as? Long ?: throw PdfFormatException("bad startxref")
        val seen = HashSet<Long>()
        var next: Long? = first
        var trailers = 0
        while (next != null && seen.add(next)) {
            val pos = next + shift
            if (pos < 0 || pos >= src.size) throw PdfFormatException("xref offset out of file")
            next = loadXrefSection(pos)
            if (++trailers > 400) break
        }
        if (trailer.map.isEmpty()) throw PdfFormatException("no trailer")
    }

    /** Reads one xref section at [pos], adds what is new, and returns the /Prev offset. */
    private fun loadXrefSection(pos: Long): Long? {
        val isTable = at(pos, 64) { lx, _ -> lx.lookingAt("xref") }
        val dict: PdfDict
        if (isTable) dict = loadXrefTable(pos) else dict = loadXrefStream(pos)
        mergeTrailer(dict)
        dict["XRefStm"].asInt()?.let { stm ->
            // A hybrid file: objects stored in object streams are listed in a stream the table points at.
            try { loadXrefStream(stm.toLong() + shift) } catch (e: PdfFormatException) { }
        }
        return (dict["Prev"] as? Number)?.toLong()
    }

    private fun mergeTrailer(d: PdfDict) {
        val merged = LinkedHashMap(trailer.map)
        for ((k, v) in d.map) if (k !in merged) merged[k] = v   // the newest section wins
        trailer = PdfDict(merged)
    }

    private fun loadXrefTable(pos: Long): PdfDict {
        var cursor = pos
        // "xref" then subsections "start count" followed by count entries.
        val header = at(cursor, 256) { lx, base -> lx.accept("xref"); base + lx.pos }
        cursor = header
        while (true) {
            // Read the next subsection header, or the trailer.
            val step = at(cursor, 256) { lx, base ->
                if (lx.accept("trailer")) return@at Triple(-1, 0, base + lx.pos)
                val start = lx.readObject() as? Long ?: throw PdfFormatException("bad xref subsection")
                val count = lx.readObject() as? Long ?: throw PdfFormatException("bad xref subsection")
                Triple(start.toInt(), count.toInt(), base + lx.pos)
            }
            if (step.first < 0) {
                cursor = step.third
                break
            }
            val start = step.first
            val count = step.second
            if (count < 0 || count > 50_000_000) throw PdfFormatException("bad xref count")
            val entriesAt = step.third
            // Entries are 20 bytes; read generously and parse by tokens so short or odd line endings still work.
            val window = minOf(src.size - entriesAt, count.toLong() * 22 + 64).toInt()
            val buf = src.read(entriesAt, window)
            val lx = PdfLexer(buf, 0, true)
            var n = 0
            while (n < count) {
                val off = lx.readObject() as? Long ?: throw PdfFormatException("bad xref entry")
                val gen = lx.readObject() as? Long ?: throw PdfFormatException("bad xref entry")
                val kw = lx.readObject() as? PdfKeyword ?: throw PdfFormatException("bad xref entry")
                if (kw.value == "n" && off > 0) xref.putIfAbsent(start + n, InFile(off + shift))
                n++
            }
            cursor = entriesAt + lx.pos
        }
        return at(cursor, 4096) { lx, _ -> lx.readObject() as? PdfDict ?: throw PdfFormatException("no trailer dictionary") }
    }

    private fun loadXrefStream(pos: Long): PdfDict {
        val obj = readIndirectAt(pos, null, decrypt = false) as? PdfStream ?: throw PdfFormatException("xref stream expected")
        val d = obj.dict
        val w = (d["W"] as? List<*>)?.map { it.asInt() ?: throw PdfFormatException("bad /W") } ?: throw PdfFormatException("no /W")
        if (w.size < 3 || w.any { it < 0 || it > 8 }) throw PdfFormatException("bad /W")
        val size = d["Size"].asInt() ?: 0
        val index = (d["Index"] as? List<*>)?.map { it.asInt() ?: 0 } ?: listOf(0, size)
        val data = decode(obj)
        val rowLen = w.sum()
        if (rowLen == 0) throw PdfFormatException("empty xref rows")
        var p = 0
        var s = 0
        while (s + 1 < index.size) {
            val start = index[s]
            val count = index[s + 1]
            for (i in 0 until count) {
                if (p + rowLen > data.size) break
                fun field(width: Int, default: Long): Long {
                    if (width == 0) return default
                    var v = 0L
                    repeat(width) { v = (v shl 8) or (data[p++].toLong() and 0xFF) }
                    return v
                }
                val type = field(w[0], 1)
                val f2 = field(w[1], 0)
                val f3 = field(w[2], 0)
                when (type) {
                    1L -> if (f2 > 0) xref.putIfAbsent(start + i, InFile(f2 + shift))
                    2L -> xref.putIfAbsent(start + i, InObjStm(f2.toInt(), f3.toInt()))
                }
            }
            s += 2
        }
        return d
    }

    /** Rebuilds the xref data by finding every "n g obj" in the file (and the objects inside object streams). */
    private fun rebuild() {
        if (src.size > MAX_SCAN) throw PdfFormatException("damaged and too large to scan")
        val chunk = 1 shl 20
        var pos = 0L
        var carry = ByteArray(0)
        var carryStart = 0L
        val found = ArrayList<Pair<Int, Long>>()      // object number, offset (later ones win)
        val trailers = ArrayList<PdfDict>()
        while (pos < src.size) {
            val data = src.read(pos, chunk)
            if (data.isEmpty()) break
            val buf = carry + data
            val base = carryStart
            scanObjects(buf, base, found)
            scanTrailers(buf, base, trailers)
            // Keep the tail so a marker split across two chunks is still seen.
            val keep = minOf(buf.size, 64)
            carry = buf.copyOfRange(buf.size - keep, buf.size)
            carryStart = base + buf.size - keep
            pos += data.size
        }
        for ((num, off) in found) xref[num] = InFile(off)
        val nums = found.map { it.first }.distinct()
        // The trailer: the last one that names a catalog; cross-reference streams carry /Root, /Encrypt and /ID too.
        trailer = trailers.lastOrNull { it["Root"] != null } ?: PdfDict(emptyMap())
        for (num in nums) {
            val loc = xref[num] as? InFile ?: continue
            if (indexOf(src.read(loc.pos, 400), "/XRef".toByteArray(), 0) < 0) continue
            try {
                val o = readObject(num) as? PdfStream ?: continue
                val merged = LinkedHashMap(trailer.map)
                for (k in listOf("Root", "Encrypt", "ID")) if (k !in merged && o.dict[k] != null) merged[k] = o.dict[k]
                trailer = PdfDict(merged)
            } catch (e: PdfFormatException) { }
        }
        setupCrypto()
        // Object streams: list what is inside each one (objects in the file itself still win).
        for (num in nums) {
            val loc = xref[num] as? InFile ?: continue
            if (indexOf(src.read(loc.pos, 400), "/ObjStm".toByteArray(), 0) < 0) continue
            try {
                val os = loadObjStm(num)
                for ((i, n) in os.nums.withIndex()) if (!xref.containsKey(n)) xref[n] = InObjStm(num, i)
            } catch (e: PdfEncryptedException) { throw e } catch (e: Exception) { }
        }
        // No trailer named a catalog: look for one.
        if (trailer["Root"] == null) {
            for (num in xref.keys.sorted().reversed()) {
                val o = try { readObject(num) } catch (e: PdfEncryptedException) { throw e } catch (e: Exception) { null }
                if (o is PdfDict && (o["Type"] as? PdfName)?.value == "Catalog") {
                    trailer = PdfDict(trailer.map + ("Root" to PdfRef(num, 0)))
                    break
                }
            }
        }
    }

    private fun scanObjects(buf: ByteArray, base: Long, out: MutableList<Pair<Int, Long>>) {
        val obj = "obj".toByteArray()
        var i = indexOf(buf, obj, 0)
        while (i >= 0) {
            // "endobj" and "obj" inside words do not count: a number, a generation and "obj" at a token start.
            if (i + 3 >= buf.size || !PdfLexer.isRegular(buf[i + 3].toInt() and 0xFF)) {
                var p = i - 1
                while (p >= 0 && PdfLexer.isWhite(buf[p].toInt() and 0xFF)) p--
                val genEnd = p
                while (p >= 0 && buf[p].toInt() in '0'.code..'9'.code) p--
                val genStart = p + 1
                if (genStart <= genEnd && p >= 0 && PdfLexer.isWhite(buf[p].toInt() and 0xFF)) {
                    while (p >= 0 && PdfLexer.isWhite(buf[p].toInt() and 0xFF)) p--
                    val numEnd = p
                    while (p >= 0 && buf[p].toInt() in '0'.code..'9'.code) p--
                    val numStart = p + 1
                    if (numStart <= numEnd && numEnd - numStart < 9 && (p < 0 || !PdfLexer.isRegular(buf[p].toInt() and 0xFF))) {
                        val num = String(buf, numStart, numEnd - numStart + 1, Charsets.ISO_8859_1).toInt()
                        out.add(num to (base + numStart))
                    }
                }
            }
            i = indexOf(buf, obj, i + 3)
        }
    }

    private fun scanTrailers(buf: ByteArray, base: Long, out: MutableList<PdfDict>) {
        val kw = "trailer".toByteArray()
        var i = indexOf(buf, kw, 0)
        while (i >= 0) {
            try {
                val d = PdfLexer(buf, i + 7, false).readObject()
                if (d is PdfDict) out.add(d)
            } catch (e: PdfFormatException) { }
            i = indexOf(buf, kw, i + 7)
        }
    }

    // ------------------------------------------------------------------------------------------ objects
    /** Follows references (a few levels) to the object itself. */
    fun resolve(o: Any?): Any? {
        var cur = o
        var hops = 0
        while (cur is PdfRef && hops++ < 16) cur = readObject(cur.num)
        return if (cur is PdfRef) null else cur
    }

    fun readObject(num: Int): Any? {
        if (cache.containsKey(num)) return cache[num]
        val loc = xref[num] ?: return null
        val value = when (loc) {
            is InFile -> readIndirectAt(loc.pos, num)
            is InObjStm -> readFromObjStm(loc, num)
        }
        if (cache.size < 50_000 && value !is PdfStream) cache[num] = value
        return value
    }

    /** The indirect object at [pos] ("n g obj ... endobj"), with its stream if it has one; decrypted when the file is encrypted. */
    private fun readIndirectAt(pos: Long, expected: Int?, decrypt: Boolean = true): Any? {
        var streamAt = -1L
        var dict: PdfDict? = null
        var objNum = 0
        var objGen = 0
        val value: Any? = at(pos, 4096) { lx, base ->
            val n = lx.readObject()
            val g = lx.readObject()
            val kw = lx.readObject()
            if (n !is Long || g !is Long || kw !is PdfKeyword || kw.value != "obj") throw PdfFormatException("no object at $pos")
            if (expected != null && n.toInt() != expected) throw PdfFormatException("object $n found where $expected was expected")
            objNum = n.toInt(); objGen = g.toInt()
            val v = lx.readObject()
            if (v is PdfKeyword) return@at null
            if (v is PdfDict && lx.lookingAt("stream")) {
                lx.accept("stream")
                // The data starts after the end of the line that follows "stream".
                if (lx.pos < lx.buf.size && lx.buf[lx.pos].toInt() == 13) lx.pos++
                if (lx.pos < lx.buf.size && lx.buf[lx.pos].toInt() == 10) lx.pos++
                streamAt = base + lx.pos
                dict = v
            }
            v
        }
        val c = crypto?.takeIf { decrypt && objNum != encryptObj }
        val d = dict
        if (d == null) return if (c != null) decryptValue(c, value, objNum, objGen) else value
        // /Length may be a reference: it is looked up here, after the window is done with.
        val length = (resolve(d["Length"]) as? Number)?.toLong() ?: -1L
        var raw = readStreamBytes(streamAt, length)
        val type = (d["Type"] as? PdfName)?.value
        if (c != null && type != "XRef") raw = c.decryptStream(raw, objNum, objGen)
        return PdfStream(if (c != null) decryptValue(c, d, objNum, objGen) as PdfDict else d, raw)
    }

    private fun decryptValue(c: PdfCrypto, v: Any?, num: Int, gen: Int): Any? = when (v) {
        is PdfString -> PdfString(c.decryptString(v.bytes, num, gen))
        is PdfDict -> PdfDict(v.map.mapValues { decryptValue(c, it.value, num, gen) })
        is List<*> -> v.map { decryptValue(c, it, num, gen) }
        else -> v
    }

    private fun readStreamBytes(start: Long, declared: Long): ByteArray {
        if (declared in 0..MAX_STREAM) {
            val data = src.read(start, declared.toInt())
            if (data.size.toLong() == declared) {
                // Trust the length when "endstream" follows it.
                val after = src.read(start + declared, 24)
                val k = indexOf(after, "endstream".toByteArray(), 0)
                if (k >= 0 && after.take(k).all { PdfLexer.isWhite(it.toInt() and 0xFF) }) return data
            }
        }
        // A wrong or missing /Length: look for "endstream".
        val out = ByteArrayOutputStream()
        var pos = start
        val marker = "endstream".toByteArray()
        var tail = ByteArray(0)
        while (pos < src.size && out.size() < MAX_STREAM) {
            val chunk = src.read(pos, 1 shl 16)
            if (chunk.isEmpty()) break
            val buf = tail + chunk
            val k = indexOf(buf, marker, 0)
            if (k >= 0) {
                out.write(buf, 0, k)
                var bytes = out.toByteArray()
                var end = bytes.size
                if (end > 0 && bytes[end - 1].toInt() == 10) end--
                if (end > 0 && bytes[end - 1].toInt() == 13) end--
                return bytes.copyOf(end)
            }
            val keep = minOf(buf.size, marker.size)
            out.write(buf, 0, buf.size - keep)
            tail = buf.copyOfRange(buf.size - keep, buf.size)
            pos += chunk.size
        }
        throw PdfFormatException("unterminated stream")
    }

    // --------------------------------------------------------------------------------- object streams
    private fun loadObjStm(num: Int): ObjStm {
        objStms[num]?.let { return it }
        val s = readObject(num) as? PdfStream ?: throw PdfFormatException("object stream $num missing")
        val data = decode(s)
        val n = resolve(s.dict["N"]).asInt() ?: throw PdfFormatException("no /N")
        val first = resolve(s.dict["First"]).asInt() ?: throw PdfFormatException("no /First")
        if (n < 0 || n > 1_000_000 || first < 0 || first > data.size) throw PdfFormatException("bad object stream")
        val lx = PdfLexer(data, 0, true)
        val nums = IntArray(n)
        val offs = IntArray(n)
        for (i in 0 until n) {
            nums[i] = lx.readObject().asInt() ?: throw PdfFormatException("bad object stream header")
            offs[i] = lx.readObject().asInt() ?: throw PdfFormatException("bad object stream header")
        }
        val os = ObjStm(data, nums, offs, first)
        if (objStms.size >= 6) objStms.remove(objStms.keys.first())
        objStms[num] = os
        return os
    }

    private fun readFromObjStm(loc: InObjStm, num: Int): Any? {
        val os = loadObjStm(loc.stream)
        var i = loc.index
        if (i !in os.nums.indices || os.nums[i] != num) i = os.nums.indexOf(num)
        if (i < 0) return null
        val lx = PdfLexer(os.data, os.first + os.offsets[i], true)
        return lx.readObject().let { if (it is PdfKeyword) null else it }
    }

    // --------------------------------------------------------------------------------------- decoding
    /** The decoded bytes of a stream. Only Flate (with PNG predictors) and none are understood. */
    fun decode(s: PdfStream): ByteArray {
        val filters = when (val f = resolve(s.dict["Filter"] ?: s.dict["F"])) {
            null -> emptyList()
            is PdfName -> listOf(f.value)
            is List<*> -> f.map { (resolve(it) as? PdfName)?.value ?: throw PdfFormatException("bad /Filter") }
            else -> throw PdfFormatException("bad /Filter")
        }
        val parms: List<PdfDict?> = when (val p = resolve(s.dict["DecodeParms"] ?: s.dict["DP"])) {
            null -> emptyList()
            is PdfDict -> listOf(p)
            is List<*> -> p.map { resolve(it) as? PdfDict }
            else -> emptyList()
        }
        var data = s.raw
        for ((i, f) in filters.withIndex()) {
            data = when (f) {
                "FlateDecode", "Fl" -> predictor(inflate(data), parms.getOrNull(i))
                else -> throw PdfFormatException("filter $f not supported")
            }
        }
        return data
    }

    private fun inflate(data: ByteArray): ByteArray {
        val inf = Inflater()
        inf.setInput(data)
        val out = ByteArrayOutputStream(maxOf(256, data.size * 3))
        val buf = ByteArray(16 * 1024)
        try {
            while (!inf.finished()) {
                val n = inf.inflate(buf)
                if (n > 0) out.write(buf, 0, n)
                else if (inf.needsInput() || inf.needsDictionary()) break
                if (out.size() > MAX_STREAM) throw PdfFormatException("stream too large")
            }
        } catch (e: java.util.zip.DataFormatException) {
            // A damaged tail: keep what was decoded so far.
            if (out.size() == 0) throw PdfFormatException("bad Flate data")
        } finally {
            inf.end()
        }
        return out.toByteArray()
    }

    private fun predictor(data: ByteArray, p: PdfDict?): ByteArray {
        val pred = resolve(p?.get("Predictor")).asInt() ?: 1
        if (pred < 10) {
            if (pred == 2) throw PdfFormatException("TIFF predictor not supported")
            return data
        }
        val colors = resolve(p?.get("Colors")).asInt() ?: 1
        val bpc = resolve(p?.get("BitsPerComponent")).asInt() ?: 8
        val columns = resolve(p?.get("Columns")).asInt() ?: 1
        val bpp = maxOf(1, colors * bpc / 8)
        val rowLen = (colors * bpc * columns + 7) / 8
        if (rowLen <= 0) throw PdfFormatException("bad predictor")
        val rows = data.size / (rowLen + 1)
        val out = ByteArray(rows * rowLen)
        for (r in 0 until rows) {
            val ft = data[r * (rowLen + 1)].toInt()
            val inOff = r * (rowLen + 1) + 1
            val outOff = r * rowLen
            for (i in 0 until rowLen) {
                val x = data[inOff + i].toInt() and 0xFF
                val a = if (i >= bpp) out[outOff + i - bpp].toInt() and 0xFF else 0
                val b = if (r > 0) out[outOff - rowLen + i].toInt() and 0xFF else 0
                val c = if (r > 0 && i >= bpp) out[outOff - rowLen + i - bpp].toInt() and 0xFF else 0
                val v = when (ft) {
                    0 -> x
                    1 -> x + a
                    2 -> x + b
                    3 -> x + (a + b) / 2
                    4 -> {
                        val pp = a + b - c
                        val pa = Math.abs(pp - a); val pb = Math.abs(pp - b); val pc = Math.abs(pp - c)
                        x + (if (pa <= pb && pa <= pc) a else if (pb <= pc) b else c)
                    }
                    else -> x
                }
                out[outOff + i] = v.toByte()
            }
        }
        return out
    }
}

internal fun indexOf(hay: ByteArray, needle: ByteArray, from: Int): Int {
    if (needle.isEmpty()) return from
    var i = maxOf(0, from)
    val last = hay.size - needle.size
    outer@ while (i <= last) {
        if (hay[i] != needle[0]) { i++; continue }
        for (j in 1 until needle.size) if (hay[i + j] != needle[j]) { i++; continue@outer }
        return i
    }
    return -1
}

internal fun lastIndexOf(hay: ByteArray, needle: ByteArray): Int {
    var i = hay.size - needle.size
    outer@ while (i >= 0) {
        for (j in needle.indices) if (hay[i + j] != needle[j]) { i--; continue@outer }
        return i
    }
    return -1
}
