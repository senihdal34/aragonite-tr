package com.ethran.notable.reading.chapters

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class EpubChaptersTest {
    @get:Rule val tmp = TemporaryFolder()

    /** An xhtml chapter of exactly [size] bytes (the position maths counts bytes), optionally with named anchors at given offsets. */
    private fun xhtml(size: Int, anchors: Map<Int, String> = emptyMap()): String {
        val sb = StringBuilder("<html><body>")
        for ((at, id) in anchors.toSortedMap()) {
            while (sb.length < at) sb.append('x')
            sb.append("<a id=\"$id\"/>")
        }
        while (sb.length < size - "</body></html>".length) sb.append('x')
        sb.append("</body></html>")
        check(sb.length == size) { "built ${sb.length}, wanted $size" }
        return sb.toString()
    }

    private fun epub(vararg files: Pair<String, String>): File {
        val f = tmp.newFile("book-${System.nanoTime()}.epub")
        ZipOutputStream(f.outputStream()).use { z ->
            z.putNextEntry(ZipEntry("mimetype")); z.write("application/epub+zip".toByteArray()); z.closeEntry()
            for ((name, body) in files) { z.putNextEntry(ZipEntry(name)); z.write(body.toByteArray(Charsets.UTF_8)); z.closeEntry() }
        }
        return f
    }

    private val container = "META-INF/container.xml" to """<?xml version="1.0"?>
        <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
          <rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles>
        </container>"""

    private fun opf(spine: List<String>, items: String, tocAttr: String = "") = "OEBPS/content.opf" to """<?xml version="1.0" encoding="UTF-8"?>
        <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
          <metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>T</dc:title></metadata>
          <manifest>$items</manifest>
          <spine $tocAttr>${spine.joinToString("") { "<itemref idref=\"$it\"/>" }}</spine>
        </package>"""

    private fun entries(r: ChapterResult) = (r as? ChapterResult.Found ?: error("expected chapters, got $r")).map.entries

    @Test fun epub3NavGivesChaptersPlacedByFileSize() {
        // Four files of 1000, 3000, 1000 and 5000 bytes: chapters start at 0, 10%, 40% and 50% of the text.
        val r = EpubChapters.read(
            epub(
                container,
                opf(
                    listOf("c1", "c2", "c3", "c4"),
                    """<item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
                       <item id="c1" href="c1.xhtml" media-type="application/xhtml+xml"/><item id="c2" href="c2.xhtml" media-type="application/xhtml+xml"/>
                       <item id="c3" href="c3.xhtml" media-type="application/xhtml+xml"/><item id="c4" href="c4.xhtml" media-type="application/xhtml+xml"/>"""
                ),
                "OEBPS/nav.xhtml" to """<!DOCTYPE html><html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops"><body>
                    <nav epub:type="landmarks"><ol><li><a href="c1.xhtml">Start here</a></li></ol></nav>
                    <nav epub:type="toc"><ol>
                      <li><a href="c1.xhtml">One</a></li>
                      <li><a href="c2.xhtml">Two &amp; a half&nbsp;ish</a>
                        <ol><li><a href="c3.xhtml">Two point one</a></li></ol></li>
                      <li><a href="c4.xhtml"><span>Four</span></a></li>
                    </ol></nav></body></html>""",
                "OEBPS/c1.xhtml" to xhtml(1000), "OEBPS/c2.xhtml" to xhtml(3000), "OEBPS/c3.xhtml" to xhtml(1000), "OEBPS/c4.xhtml" to xhtml(5000),
            )
        )
        val e = entries(r)
        assertEquals(listOf("One", "Two & a half ish", "Two point one", "Four"), e.map { it.title })
        assertEquals(listOf(1, 1, 2, 1), e.map { it.depth })
        assertEquals(listOf(0.0, 0.1, 0.4, 0.5), e.map { it.startFraction })
        assertEquals(ChapterSource.Epub, (r as ChapterResult.Found).map.source)
        assertEquals(0, r.map.pages)
    }

    @Test fun epub2NcxWithNestedNavPointsAndEscapedPaths() {
        val r = EpubChapters.read(
            epub(
                container,
                opf(
                    listOf("a", "b"),
                    """<item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
                       <item id="a" href="Text/Chapter%201.xhtml" media-type="application/xhtml+xml"/>
                       <item id="b" href="Text/b.xhtml" media-type="application/xhtml+xml"/>""",
                    tocAttr = "toc=\"ncx\""
                ),
                "OEBPS/toc.ncx" to """<?xml version="1.0" encoding="UTF-8"?>
                    <!DOCTYPE ncx PUBLIC "-//NISO//DTD ncx 2005-1//EN" "http://www.daisy.org/z3986/2005/ncx-2005-1.dtd">
                    <ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1"><navMap>
                      <navPoint id="p1" playOrder="1"><navLabel><text>Part I</text></navLabel><content src="Text/Chapter%201.xhtml"/>
                        <navPoint id="p2" playOrder="2"><navLabel><text>  First
                           chapter </text></navLabel><content src="Text/Chapter%201.xhtml#c1"/></navPoint>
                      </navPoint>
                      <navPoint id="p3" playOrder="3"><navLabel><text>Second</text></navLabel><content src="Text/b.xhtml"/></navPoint>
                    </navMap></ncx>""",
                "OEBPS/Text/Chapter 1.xhtml" to xhtml(2000, mapOf(500 to "c1")), "OEBPS/Text/b.xhtml" to xhtml(2000),
            )
        )
        val e = entries(r)
        assertEquals(listOf("Part I", "First chapter", "Second"), e.map { it.title })
        assertEquals(listOf(1, 2, 1), e.map { it.depth })
        // "Part I" starts the first file; "First chapter" is the anchor at byte 500 of it; "Second" starts the second file.
        assertEquals(0.0, e[0].startFraction, 1e-9)
        assertEquals(500.0 / 2000 * 0.5, e[1].startFraction, 0.01)
        assertEquals(0.5, e[2].startFraction, 1e-9)
    }

    @Test fun aWholeBookInOneFileIsPlacedByItsAnchors() {
        val r = EpubChapters.read(
            epub(
                container,
                opf(listOf("all"), """<item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/><item id="all" href="all.xhtml" media-type="application/xhtml+xml"/>"""),
                "OEBPS/nav.xhtml" to """<html><body><nav epub:type="toc" xmlns:epub="x"><ol>
                    <li><a href="all.xhtml#a">A</a></li><li><a href="all.xhtml#b">B</a></li><li><a href="all.xhtml#c">C</a></li></ol></nav></body></html>""",
                "OEBPS/all.xhtml" to xhtml(10_000, mapOf(0 to "a", 2500 to "b", 7500 to "c")),
            )
        )
        val f = entries(r).map { it.startFraction }
        assertEquals(0.0, f[0], 0.01)
        assertEquals(0.25, f[1], 0.01)
        assertEquals(0.75, f[2], 0.01)
    }

    @Test fun entriesOutOfBookOrderArePutInOrder() {
        val r = EpubChapters.read(
            epub(
                container,
                opf(listOf("a", "b"), """<item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/><item id="a" href="a.xhtml" media-type="application/xhtml+xml"/><item id="b" href="b.xhtml" media-type="application/xhtml+xml"/>"""),
                "OEBPS/nav.xhtml" to """<nav epub:type="toc"><ol><li><a href="b.xhtml">Later</a></li><li><a href="a.xhtml">Earlier</a></li></ol></nav>""",
                "OEBPS/a.xhtml" to xhtml(1000), "OEBPS/b.xhtml" to xhtml(1000),
            )
        )
        assertEquals(listOf("Earlier", "Later"), entries(r).map { it.title })
    }

    @Test fun linksOutsideTheSpineAndWebLinksAreSkipped() {
        val r = EpubChapters.read(
            epub(
                container,
                opf(listOf("a"), """<item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/><item id="a" href="a.xhtml" media-type="application/xhtml+xml"/>"""),
                "OEBPS/nav.xhtml" to """<nav epub:type="toc"><ol><li><a href="a.xhtml">Real</a></li><li><a href="http://example.com/x">Web</a></li><li><a href="missing.xhtml">Missing</a></li></ol></nav>""",
                "OEBPS/a.xhtml" to xhtml(500),
            )
        )
        assertEquals(listOf("Real"), entries(r).map { it.title })
    }

    @Test fun aBookWithoutContentsHasNone() {
        val r = EpubChapters.read(epub(container, opf(listOf("a"), """<item id="a" href="a.xhtml" media-type="application/xhtml+xml"/>"""), "OEBPS/a.xhtml" to xhtml(500)))
        assertEquals(ChapterResult.None(NoChapters.NoOutline), r)
    }

    @Test fun notAnEpubIsUnreadable() {
        val f = tmp.newFile("x.epub").apply { writeText("not a zip") }
        assertEquals(ChapterResult.None(NoChapters.Unreadable), EpubChapters.read(f))
        assertEquals(ChapterResult.None(NoChapters.Unreadable), EpubChapters.read(epub("a.txt" to "hello")))
    }

    @Test fun readerPicksByExtension() {
        val mobi = tmp.newFile("b.mobi").apply { writeText("x") }
        assertEquals(ChapterResult.None(NoChapters.UnsupportedFormat), ChapterReader.read(mobi))
        assertEquals(ChapterResult.None(NoChapters.Unreadable), ChapterReader.read(File(tmp.root, "missing.pdf")))
        val pdf = tmp.newFile("c.pdf").apply { writeBytes(PdfTestFiles.classic(PdfTestFiles.bodies())) }
        assertTrue(ChapterReader.read(pdf) is ChapterResult.Found)
        val epubFile = epub(container, opf(listOf("a"), """<item id="a" href="a.xhtml" media-type="application/xhtml+xml"/>"""), "OEBPS/a.xhtml" to xhtml(500))
        assertEquals(ChapterResult.None(NoChapters.NoOutline), ChapterReader.read(epubFile))
    }

    @Test fun pathAndEntityHelpers() {
        assertEquals("OEBPS/Text/a b.xhtml", EpubChapters.resolve("OEBPS", "Text/a%20b.xhtml#x"))
        assertEquals("OEBPS/c.xhtml", EpubChapters.resolve("OEBPS/Text", "../c.xhtml"))
        assertEquals("c.xhtml", EpubChapters.resolve("OEBPS/Text", "/c.xhtml"))
        assertEquals("café", EpubChapters.percentDecode("caf%C3%A9"))
        assertEquals("100%", EpubChapters.percentDecode("100%"))
    }
}
