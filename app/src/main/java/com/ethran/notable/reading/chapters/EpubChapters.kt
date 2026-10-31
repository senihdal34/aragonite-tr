package com.ethran.notable.reading.chapters

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipException
import java.util.zip.ZipFile

/**
 * Chapters of an EPUB: META-INF/container.xml -> the OPF package -> the contents (the EPUB 3 `nav` document, or the
 * EPUB 2 `toc.ncx`), each entry matched to the spine item it points into.
 *
 * ## How exact are the positions?
 * Not very, and the code says so on purpose. NeoReader lays an EPUB out into its own pages and does not publish how, so the
 * app cannot know which page a chapter starts on. The position of a chapter is estimated as the share of the book's text
 * (the size of the spine files, uncompressed) that comes before it, plus, for entries that point to an `id` inside a file
 * shared with other entries, where that id sits in the file. Text size is a good stand-in for page count in running prose,
 * and a poor one around images, tables and code. Expect a chapter boundary to be off by a few percent of the book.
 *
 * Reads only the zip directory, the three small package files and, when several entries share one file, that file.
 * Blocking I/O: call it on Dispatchers.IO.
 */
object EpubChapters {
    private const val MAX_XML = 8 * 1024 * 1024
    private const val MAX_TEXT = 12 * 1024 * 1024

    /** Reads the contents of [file]; never throws. */
    fun read(file: File): ChapterResult = try {
        ZipFile(file).use { read(it) }
    } catch (e: ZipException) {
        ChapterResult.None(NoChapters.Unreadable)
    } catch (e: java.io.IOException) {
        ChapterResult.None(NoChapters.Unreadable)
    } catch (e: RuntimeException) {
        ChapterResult.None(NoChapters.Unreadable)
    }

    private class Item(val id: String, val path: String, val type: String, val props: String)

    private fun read(zip: ZipFile): ChapterResult {
        val names = HashMap<String, String>()                          // lower case -> real name, for sloppy hrefs
        for (e in zip.entries()) names.putIfAbsent(e.name.lowercase(), e.name)
        fun entry(path: String) = zip.getEntry(path) ?: names[path.lowercase()]?.let { zip.getEntry(it) }
        fun bytes(path: String, max: Int): ByteArray? {
            val e = entry(path) ?: return null
            if (e.size > max) return null
            return zip.getInputStream(e).use { ins ->
                val out = ByteArrayOutputStream(maxOf(1024, e.size.toInt().coerceAtLeast(0)))
                val buf = ByteArray(16 * 1024)
                while (true) {
                    val n = ins.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    if (out.size() > max) return null
                }
                out.toByteArray()
            }
        }

        val container = bytes("META-INF/container.xml", MAX_XML) ?: return ChapterResult.None(NoChapters.Unreadable)
        val opfPath = rootfile(MiniMarkup.parse(MiniMarkup.decodeBytes(container))) ?: return ChapterResult.None(NoChapters.Unreadable)
        val opfBytes = bytes(opfPath, MAX_XML) ?: return ChapterResult.None(NoChapters.Unreadable)
        val opfDir = opfPath.substringBeforeLast('/', "")
        val opf = MiniMarkup.parse(MiniMarkup.decodeBytes(opfBytes))

        val items = LinkedHashMap<String, Item>()
        val spine = ArrayList<String>()
        var tocId: String? = null
        for (t in opf) {
            if (t !is Markup.Open) continue
            when (t.name) {
                "item" -> {
                    val id = t.attrs["id"] ?: continue
                    val href = t.attrs["href"] ?: continue
                    items[id] = Item(id, resolve(opfDir, href), (t.attrs["media-type"] ?: "").lowercase(), (t.attrs["properties"] ?: "").lowercase())
                }
                "spine" -> tocId = t.attrs["toc"]
                "itemref" -> t.attrs["idref"]?.let { spine.add(it) }
            }
        }
        val spineItems = spine.mapNotNull { items[it] }
        if (spineItems.isEmpty()) return ChapterResult.None(NoChapters.Unreadable)

        // The weight of each spine file is its uncompressed size; the position of a file is the weight before it.
        val weights = spineItems.map { maxOf(1L, entry(it.path)?.size?.takeIf { s -> s >= 0 } ?: 1L) }
        val total = weights.sum().toDouble()
        val before = DoubleArray(spineItems.size)
        var run = 0.0
        for (i in spineItems.indices) { before[i] = run; run += weights[i] }
        val spineIndex = HashMap<String, Int>()
        spineItems.forEachIndexed { i, it -> spineIndex.putIfAbsent(it.path, i) }

        // The contents: the EPUB 3 nav document, then the EPUB 2 ncx.
        val navItem = items.values.firstOrNull { "nav" in it.props.split(' ') }
        val ncxItem = tocId?.let { items[it] } ?: items.values.firstOrNull { it.type == "application/x-dtbncx+xml" }
        var raw: List<RawEntry> = emptyList()
        if (navItem != null) {
            bytes(navItem.path, MAX_XML)?.let { raw = parseNav(MiniMarkup.parse(MiniMarkup.decodeBytes(it)), navItem.path.substringBeforeLast('/', "")) }
        }
        if (raw.isEmpty() && ncxItem != null) {
            bytes(ncxItem.path, MAX_XML)?.let { raw = parseNcx(MiniMarkup.parse(MiniMarkup.decodeBytes(it)), ncxItem.path.substringBeforeLast('/', "")) }
        }
        if (raw.isEmpty()) return ChapterResult.None(NoChapters.NoOutline)

        // Entries that point into the spine, with where in the file they point.
        class Hit(val e: RawEntry, val index: Int)
        val hits = raw.mapNotNull { e -> spineIndex[e.path]?.let { Hit(e, it) } }
        if (hits.isEmpty()) return ChapterResult.None(NoChapters.NoOutline)

        // Several entries in one file (a whole book in one xhtml, or "Part I" and its first chapter): place them by their id.
        val within = DoubleArray(hits.size)
        val groups = hits.indices.groupBy { hits[it].index }
        for ((spineI, members) in groups) {
            if (members.size < 2) continue
            val text = if (weights[spineI] <= MAX_TEXT) bytes(spineItems[spineI].path, MAX_TEXT)?.let { MiniMarkup.decodeBytes(it) } else null
            var prev = 0.0
            for ((k, m) in members.withIndex()) {
                val frag = hits[m].e.fragment
                val found = if (text != null && frag.isNotEmpty()) offsetOfId(text, frag) else -1
                val frac = if (found >= 0 && text!!.isNotEmpty()) found.toDouble() / text.length else if (k == 0 && frag.isEmpty()) 0.0 else prev
                prev = maxOf(prev, frac)
                within[m] = prev
            }
        }

        val chapters = hits.mapIndexedNotNull { i, h ->
            val title = TitleText.clean(MiniMarkup.decodeEntities(h.e.title))   // a second pass: some books escape their ampersands twice
            if (title.isEmpty()) null
            else Chapter(title, h.e.depth, ((before[h.index] + within[i] * weights[h.index]) / total).coerceIn(0.0, 1.0))
        }
        if (chapters.isEmpty()) return ChapterResult.None(NoChapters.NoOutline)
        return ChapterResult.Found(ChapterMap(ChapterSource.Epub, 0, chapters.sortedBy { it.startFraction }))
    }

    private fun offsetOfId(text: String, id: String): Int {
        val q = Regex.escape(id)
        val m = Regex("""\b(?:id|name)\s*=\s*["']$q["']""").find(text) ?: return -1
        return m.range.first
    }

    private fun rootfile(tokens: List<Markup>): String? {
        var first: String? = null
        for (t in tokens) {
            if (t is Markup.Open && t.name == "rootfile") {
                val p = t.attrs["full-path"] ?: continue
                if (first == null) first = p
                if ((t.attrs["media-type"] ?: "").contains("oebps-package")) return resolve("", p)
            }
        }
        return first?.let { resolve("", it) }
    }

    internal class RawEntry(val title: String, val path: String, val fragment: String, val depth: Int)

    /** The entries of an EPUB 3 navigation document: the links of the `toc` nav, nested by list depth. */
    internal fun parseNav(tokens: List<Markup>, baseDir: String): List<RawEntry> {
        val navs = tokens.withIndex().filter { (_, t) -> t is Markup.Open && t.name == "nav" }
        val start = navs.firstOrNull { (_, t) -> ((t as Markup.Open).attrs["type"] ?: "").split(' ').contains("toc") }
            ?: navs.firstOrNull { (_, t) -> (t as Markup.Open).attrs["type"] == null }
        var i = if (start != null) start.index + 1 else 0
        val out = ArrayList<RawEntry>()
        var depth = 0
        while (i < tokens.size) {
            val t = tokens[i]
            when {
                t is Markup.Close && t.name == "nav" && start != null -> break
                t is Markup.Open && (t.name == "ol" || t.name == "ul") && !t.selfClosing -> depth++
                t is Markup.Close && (t.name == "ol" || t.name == "ul") -> depth = maxOf(0, depth - 1)
                t is Markup.Open && t.name == "a" && !t.selfClosing -> {
                    val href = t.attrs["href"]
                    val sb = StringBuilder()
                    var j = i + 1
                    while (j < tokens.size) {
                        val u = tokens[j]
                        if (u is Markup.Close && u.name == "a") break
                        if (u is Markup.Text) sb.append(u.text)
                        j++
                    }
                    if (href != null && href.isNotBlank() && !href.contains("://")) {
                        val full = resolve(baseDir, href.substringBefore('#').ifEmpty { "" })
                        val frag = if (href.contains('#')) percentDecode(href.substringAfter('#')) else ""
                        if (href.substringBefore('#').isNotEmpty()) out.add(RawEntry(sb.toString(), full, frag, maxOf(1, depth)))
                    }
                    i = j
                }
            }
            i++
        }
        return out
    }

    /** The entries of an EPUB 2 `toc.ncx`: its nested `navPoint`s in document order. */
    internal fun parseNcx(tokens: List<Markup>, baseDir: String): List<RawEntry> {
        class Point(var title: String, var src: String?, val depth: Int)
        val all = ArrayList<Point>()
        val stack = ArrayList<Point>()
        var inLabel = false
        var inText = false
        for (t in tokens) {
            when {
                t is Markup.Open && t.name == "navpoint" && !t.selfClosing -> { val p = Point("", null, stack.size + 1); all.add(p); stack.add(p) }
                t is Markup.Close && t.name == "navpoint" -> if (stack.isNotEmpty()) stack.removeAt(stack.size - 1)
                t is Markup.Open && t.name == "navlabel" -> inLabel = true
                t is Markup.Close && t.name == "navlabel" -> { inLabel = false; inText = false }
                t is Markup.Open && t.name == "text" && inLabel -> inText = true
                t is Markup.Close && t.name == "text" -> inText = false
                t is Markup.Text && inText && stack.isNotEmpty() -> stack.last().let { if (it.title.isEmpty()) it.title = t.text else it.title += t.text }
                t is Markup.Open && t.name == "content" && stack.isNotEmpty() -> stack.last().let { if (it.src == null) it.src = t.attrs["src"] }
            }
        }
        return all.mapNotNull { p ->
            val src = p.src ?: return@mapNotNull null
            if (src.contains("://") || src.substringBefore('#').isEmpty()) return@mapNotNull null
            RawEntry(p.title, resolve(baseDir, src.substringBefore('#')), if (src.contains('#')) percentDecode(src.substringAfter('#')) else "", p.depth)
        }
    }

    /** [href] (possibly with %20 escapes, `..` and a leading slash) relative to [baseDir] inside the zip. */
    internal fun resolve(baseDir: String, href: String): String {
        val clean = percentDecode(href.substringBefore('#').substringBefore('?'))
        val parts = ArrayList<String>()
        if (!clean.startsWith("/") && baseDir.isNotEmpty()) parts.addAll(baseDir.split('/').filter { it.isNotEmpty() })
        for (seg in clean.split('/')) {
            when (seg) {
                "", "." -> {}
                ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.size - 1)
                else -> parts.add(seg)
            }
        }
        return parts.joinToString("/")
    }

    internal fun percentDecode(s: String): String {
        if (s.indexOf('%') < 0) return s
        val sb = StringBuilder()
        val pending = ByteArrayOutputStream()
        fun flush() { if (pending.size() > 0) { sb.append(String(pending.toByteArray(), Charsets.UTF_8)); pending.reset() } }
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '%' && i + 2 < s.length && hex(s[i + 1]) >= 0 && hex(s[i + 2]) >= 0) {
                pending.write(hex(s[i + 1]) * 16 + hex(s[i + 2])); i += 3
            } else {
                flush(); sb.append(c); i++
            }
        }
        flush()
        return sb.toString()
    }

    private fun hex(c: Char): Int = when (c) { in '0'..'9' -> c - '0'; in 'a'..'f' -> c - 'a' + 10; in 'A'..'F' -> c - 'A' + 10; else -> -1 }
}
