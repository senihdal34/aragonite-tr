package com.ethran.notable.reading.chapters

import java.io.File
import java.io.RandomAccessFile

/** A bookmark as the file states it: its title, the 0-based page it points to (-1 when unresolved) and its depth (1 = top). */
data class PdfBookmark(val title: String, val page: Int, val depth: Int)

/** What reading a PDF's bookmarks found. */
sealed interface PdfOutlineResult {
    /** [pages] pages and these bookmarks, in the order of the outline. Never empty. */
    data class Found(val pages: Int, val bookmarks: List<PdfBookmark>) : PdfOutlineResult
    /** A readable PDF without bookmarks. */
    data object NoOutline : PdfOutlineResult
    /** Encrypted, damaged, or built with something the reader does not handle. */
    data class Unreadable(val why: String) : PdfOutlineResult
}

/**
 * Reads the bookmarks (the document outline) of a PDF: /Root -> /Outlines -> /First, /Next, with each item's /Dest or
 * GoTo action resolved to a page index through the page tree (and the named-destination tables when the target is a name).
 *
 * Purpose written instead of a PDF library: the bookmarks of a book are a few kilobytes of structure, and the app should
 * not carry (or load) a renderer to read them. Pure Kotlin, tested on hand-made files and, off the device, against every
 * PDF found on the developer's Mac with PyMuPDF as the reference.
 */
object PdfOutline {
    private const val MAX_ITEMS = 20_000
    private const val MAX_DEPTH = 12
    private const val MAX_PAGES = 200_000

    /** Reads [file]; never throws. Safe to call on any thread, but it does blocking file I/O: call it on Dispatchers.IO. */
    fun read(file: File): PdfOutlineResult = try {
        RandomAccessFile(file, "r").use { read(FileSource(it)) }
    } catch (e: java.io.IOException) {
        PdfOutlineResult.Unreadable("io: ${e.message}")
    } catch (e: SecurityException) {
        PdfOutlineResult.Unreadable("security: ${e.message}")
    }

    fun read(bytes: ByteArray): PdfOutlineResult = read(ByteArraySource(bytes))

    internal fun read(src: PdfSource): PdfOutlineResult {
        return try {
            val doc = PdfDocument.open(src)
            readOutline(doc)
        } catch (e: PdfEncryptedException) {
            PdfOutlineResult.Unreadable("encrypted")
        } catch (e: PdfFormatException) {
            PdfOutlineResult.Unreadable(e.message ?: "format")
        } catch (e: RuntimeException) {
            // A reader must not take the app down for one odd file.
            PdfOutlineResult.Unreadable("${e.javaClass.simpleName}: ${e.message}")
        } catch (e: OutOfMemoryError) {
            PdfOutlineResult.Unreadable("out of memory")
        }
    }

    private fun readOutline(doc: PdfDocument): PdfOutlineResult {
        val root = doc.root ?: return PdfOutlineResult.Unreadable("no catalog")
        val outlines = doc.resolve(root["Outlines"]) as? PdfDict ?: return PdfOutlineResult.NoOutline
        val first = outlines["First"] ?: return PdfOutlineResult.NoOutline
        val pagesRoot = doc.resolve(root["Pages"]) as? PdfDict ?: return PdfOutlineResult.Unreadable("no page tree")

        val raw = ArrayList<RawItem>()
        val seen = HashSet<Int>()
        walk(doc, first, 1, raw, seen)
        if (raw.isEmpty()) return PdfOutlineResult.NoOutline

        val pageTree = PageTree(doc, pagesRoot, root["Pages"])
        val names = NamedDests(doc, root)
        val out = ArrayList<PdfBookmark>(raw.size)
        for (item in raw) {
            val page = destinationPage(doc, item.dict, pageTree, names)
            out.add(PdfBookmark(item.title, page, item.depth))
        }
        val total = pageTree.count
        return PdfOutlineResult.Found(total, out)
    }

    private class RawItem(val title: String, val dict: PdfDict, val depth: Int)

    private fun walk(doc: PdfDocument, start: Any?, depth: Int, out: MutableList<RawItem>, seen: MutableSet<Int>) {
        var cur = start
        while (cur is PdfRef && out.size < MAX_ITEMS && seen.add(cur.num)) {
            val d = doc.resolve(cur) as? PdfDict ?: return
            val title = PdfText.decode((doc.resolve(d["Title"]) as? PdfString)?.bytes ?: ByteArray(0))
            out.add(RawItem(title, d, depth))
            val child = d["First"]
            if (child is PdfRef && depth < MAX_DEPTH) walk(doc, child, depth + 1, out, seen)
            cur = d["Next"]
        }
    }

    /** The 0-based page an outline item goes to, or -1. */
    private fun destinationPage(doc: PdfDocument, item: PdfDict, pages: PageTree, names: NamedDests): Int {
        var dest = doc.resolve(item["Dest"])
        if (dest == null) {
            val action = doc.resolve(item["A"]) as? PdfDict
            val kind = (doc.resolve(action?.get("S")) as? PdfName)?.value
            if (action != null && kind == "GoTo") dest = doc.resolve(action["D"])
        }
        // A name or string stands for an entry of the document's named destinations.
        if (dest is PdfName) dest = names.lookup(dest.value)
        else if (dest is PdfString) dest = names.lookup(String(dest.bytes, Charsets.ISO_8859_1))
        if (dest is PdfDict) dest = doc.resolve(dest["D"])
        val array = dest as? List<*> ?: return -1
        return when (val target = array.firstOrNull()) {
            is PdfRef -> pages.indexOf(target.num)
            // Some generators (wkhtmltopdf) write the page number, 0-based, where a page object belongs; readers accept it.
            is Long -> if (target in 0 until pages.count) target.toInt() else -1
            else -> -1
        }
    }

    /** Page object number -> page index, from the page tree. Flat trees are not even opened page by page. */
    private class PageTree(private val doc: PdfDocument, private val root: PdfDict, private val rootRef: Any?) {
        private val index = HashMap<Int, Int>()
        val count: Int
        init {
            var n = 0
            // Depth-first, in page order. A node whose /Count equals its number of kids has only pages below it.
            val seen = HashSet<Int>()
            fun visit(node: PdfDict, nodeNum: Int?) {
                if (n >= MAX_PAGES) return
                val kids = doc.resolve(node["Kids"]) as? List<*>
                if (kids == null) { if (nodeNum != null) index[nodeNum] = n; n++; return }
                val declared = doc.resolve(node["Count"]).asInt()
                // Only pages below it when /Count says so and the first, middle and last kid really are pages (a one-kid
                // "Pages" node wrapping another has /Count 1 too).
                val allPages = declared != null && declared == kids.size && listOf(0, kids.size / 2, kids.size - 1).distinct().all { i ->
                    val ref = kids[i] as? PdfRef
                    val d = ref?.let { doc.resolve(it) as? PdfDict }
                    d != null && d["Kids"] == null
                }
                for (k in kids) {
                    if (n >= MAX_PAGES) return
                    val ref = k as? PdfRef ?: continue
                    if (!seen.add(ref.num)) continue
                    if (allPages) { index[ref.num] = n; n++; continue }
                    val child = doc.resolve(ref) as? PdfDict ?: continue
                    visit(child, ref.num)
                }
            }
            visit(root, (rootRef as? PdfRef)?.num)
            count = n
        }
        fun indexOf(objNum: Int): Int = index[objNum] ?: -1
    }

    /** The document's named destinations: the old /Dests dictionary and the /Names /Dests name tree, read on first use. */
    private class NamedDests(private val doc: PdfDocument, private val root: PdfDict) {
        private var table: Map<String, Any?>? = null

        fun lookup(name: String): Any? {
            val t = table ?: load().also { table = it }
            return doc.resolve(t[name])
        }

        private fun load(): Map<String, Any?> {
            val map = HashMap<String, Any?>()
            (doc.resolve(root["Dests"]) as? PdfDict)?.let { d -> for ((k, v) in d.map) map[k] = v }
            val names = doc.resolve(root["Names"]) as? PdfDict
            val tree = doc.resolve(names?.get("Dests")) as? PdfDict
            if (tree != null) collect(tree, map, HashSet(), 0)
            return map
        }

        private fun collect(node: PdfDict, out: MutableMap<String, Any?>, seen: MutableSet<Int>, depth: Int) {
            if (depth > 16 || out.size > 300_000) return
            (doc.resolve(node["Names"]) as? List<*>)?.let { list ->
                var i = 0
                while (i + 1 < list.size) {
                    val key = doc.resolve(list[i]) as? PdfString
                    if (key != null) out[String(key.bytes, Charsets.ISO_8859_1)] = list[i + 1]
                    i += 2
                }
            }
            (doc.resolve(node["Kids"]) as? List<*>)?.forEach { kid ->
                val ref = kid as? PdfRef
                if (ref != null && !seen.add(ref.num)) return@forEach
                (doc.resolve(kid) as? PdfDict)?.let { collect(it, out, seen, depth + 1) }
            }
        }
    }
}

/** PDF text strings: UTF-16BE or UTF-8 with a byte order mark, otherwise PDFDocEncoding. */
internal object PdfText {
    /** The characters PDFDocEncoding puts at 0x80..0x9F that differ from Latin-1. */
    private val HIGH = charArrayOf(
        '•', '†', '‡', '…', '—', '–', 'ƒ', '⁄', '‹', '›', '−', '‰', '„', '“', '”', '‘',
        '’', '‚', '™', 'ﬁ', 'ﬂ', 'Ł', 'Œ', 'Š', 'Ÿ', 'Ž', 'ı', 'ł', 'œ', 'š', 'ž', '�'
    )

    fun decode(b: ByteArray): String {
        val s = when {
            b.size >= 2 && b[0] == 0xFE.toByte() && b[1] == 0xFF.toByte() -> String(b, 2, b.size - 2, Charsets.UTF_16BE)
            b.size >= 2 && b[0] == 0xFF.toByte() && b[1] == 0xFE.toByte() -> String(b, 2, b.size - 2, Charsets.UTF_16LE)
            b.size >= 3 && b[0] == 0xEF.toByte() && b[1] == 0xBB.toByte() && b[2] == 0xBF.toByte() -> String(b, 3, b.size - 3, Charsets.UTF_8)
            else -> buildString {
                for (x in b) {
                    val c = x.toInt() and 0xFF
                    append(if (c in 0x80..0x9F) HIGH[c - 0x80] else c.toChar())
                }
            }
        }
        return TitleText.clean(s)
    }
}
