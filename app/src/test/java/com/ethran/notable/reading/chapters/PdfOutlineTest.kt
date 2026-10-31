package com.ethran.notable.reading.chapters

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Assert.assertNotNull

class PdfOutlineTest {
    private fun found(r: PdfOutlineResult): PdfOutlineResult.Found = r as? PdfOutlineResult.Found ?: error("expected bookmarks, got $r")

    private fun resource(name: String): ByteArray = requireNotNull(javaClass.getResourceAsStream("/chapters/$name")) { "missing $name" }.readBytes()

    private fun summary(f: PdfOutlineResult.Found) = f.bookmarks.map { Triple(it.depth, it.title, it.page + 1) }

    /** What [PdfTestFiles.bodies] states, in outline order. Page 0 stands for "no page" (page -1 in the bookmarks). */
    private val expected = listOf(
        Triple(1, "Intro", 1),
        Triple(1, "Çür", 2),                  // the UTF-16BE title <FEFF00C700FC0072>
        Triple(2, "Chapter A1", 3),                      // a GoTo action
        Triple(1, "Named", 4),                           // a name tree entry
        Triple(1, "Old name", 5),                        // an entry of the old /Dests dictionary
        Triple(1, "By number", 3),                       // a page index where a page object belongs
        Triple(1, "Lost", 0),                            // a page that is not there
    )

    @Test fun classicFileWithEveryKindOfDestination() {
        val f = found(PdfOutline.read(PdfTestFiles.classic(PdfTestFiles.bodies())))
        assertEquals(5, f.pages)
        assertEquals(expected, summary(f))
    }

    @Test fun offsetsCountFromTheHeaderWhenJunkComesFirst() {
        val f = found(PdfOutline.read(PdfTestFiles.classic(PdfTestFiles.bodies(), prefix = "junk before the header\n")))
        assertEquals(expected, summary(f))
    }

    @Test fun aWrongStartxrefIsRepairedByScanning() {
        val good = PdfTestFiles.classic(PdfTestFiles.bodies())
        val text = String(good, Charsets.ISO_8859_1)
        val broken = text.replace(Regex("startxref\\n\\d+"), "startxref\n7").toByteArray(Charsets.ISO_8859_1)
        assertEquals(expected, summary(found(PdfOutline.read(broken))))
    }

    @Test fun aDamagedXrefTableIsRepairedByScanning() {
        val good = String(PdfTestFiles.classic(PdfTestFiles.bodies()), Charsets.ISO_8859_1)
        val broken = good.replace("xref\n0 17", "xref\n0 99999999").toByteArray(Charsets.ISO_8859_1)
        assertEquals(expected, summary(found(PdfOutline.read(broken))))
    }

    @Test fun objectStreamsAndXrefStreamsWithPngPrediction() {
        val f = found(PdfOutline.read(PdfTestFiles.withObjectStream()))
        assertEquals(5, f.pages)
        assertEquals(expected, summary(f))
    }

    @Test fun objectStreamFileWithABrokenXrefIsRepaired() {
        val good = PdfTestFiles.withObjectStream()
        val broken = String(good, Charsets.ISO_8859_1).replace(Regex("startxref\\n\\d+"), "startxref\n3").toByteArray(Charsets.ISO_8859_1)
        assertEquals(expected, summary(found(PdfOutline.read(broken))))
    }

    @Test fun anIncrementalUpdateWinsOverTheOriginal() {
        val original = PdfTestFiles.classic(PdfTestFiles.bodies())
        val prev = Regex("startxref\\n(\\d+)").findAll(String(original, Charsets.ISO_8859_1)).last().groupValues[1].toInt()
        val updated = PdfTestFiles.withUpdate(original, "Introduction", prev)
        val titles = summary(found(PdfOutline.read(updated))).map { it.second }
        assertEquals("Introduction", titles.first())
        assertEquals(7, titles.size)
    }

    @Test fun aPagesNodeWithOneKidThatIsAPagesNodeIsNotMistakenForAPage() {
        // Two pages: the second one sits inside a one-kid node whose /Count is 1, which is also how many kids it has.
        val bodies = linkedMapOf(
            1 to "<< /Type /Catalog /Pages 2 0 R /Outlines 6 0 R >>",
            2 to "<< /Type /Pages /Kids [3 0 R 4 0 R] /Count 2 >>",
            3 to "<< /Type /Page /Parent 2 0 R >>",
            4 to "<< /Type /Pages /Parent 2 0 R /Kids [5 0 R] /Count 1 >>",
            5 to "<< /Type /Page /Parent 4 0 R >>",
            6 to "<< /Type /Outlines /First 7 0 R /Last 8 0 R >>",
            7 to "<< /Title (One) /Parent 6 0 R /Next 8 0 R /Dest [3 0 R /Fit] >>",
            8 to "<< /Title (Two) /Parent 6 0 R /Prev 7 0 R /Dest [5 0 R /Fit] >>",
        )
        assertEquals(listOf(Triple(1, "One", 1), Triple(1, "Two", 2)), summary(found(PdfOutline.read(PdfTestFiles.classic(bodies)))))
    }

    @Test fun aLoopInTheOutlineEndsInsteadOfHanging() {
        val bodies = PdfTestFiles.bodies()
        bodies[16] = "<< /Title (Loop) /Parent 9 0 R /Prev 15 0 R /Next 10 0 R /Dest [3 0 R /Fit] >>"
        val f = found(PdfOutline.read(PdfTestFiles.classic(bodies)))
        assertEquals(7, f.bookmarks.size)
    }

    @Test fun aFileWithoutOutlinesHasNone() {
        val bodies = PdfTestFiles.bodies()
        bodies[1] = "<< /Type /Catalog /Pages 2 0 R >>"
        assertEquals(PdfOutlineResult.NoOutline, PdfOutline.read(PdfTestFiles.classic(bodies)))
    }

    @Test fun anEmptyOutlineHasNone() {
        val bodies = PdfTestFiles.bodies()
        bodies[9] = "<< /Type /Outlines /Count 0 >>"
        assertEquals(PdfOutlineResult.NoOutline, PdfOutline.read(PdfTestFiles.classic(bodies)))
    }

    @Test fun garbageNeverThrows() {
        for (bytes in listOf(ByteArray(0), "hello".toByteArray(), ByteArray(5000) { (it * 31).toByte() }, "%PDF-1.4\nstartxref\n0\n%%EOF".toByteArray())) {
            assertTrue(PdfOutline.read(bytes) is PdfOutlineResult.Unreadable)
        }
    }

    @Test fun aTruncatedFileNeverThrows() {
        val good = PdfTestFiles.withObjectStream()
        for (cut in listOf(10, 100, good.size / 2, good.size - 30)) {
            val r = PdfOutline.read(good.copyOf(cut))
            assertTrue("cut $cut: $r", r is PdfOutlineResult.Unreadable || r is PdfOutlineResult.Found || r is PdfOutlineResult.NoOutline)
        }
    }

    // ---- files written by MuPDF (PyMuPDF), the same 12 pages and 8 bookmarks in each
    private val mupdf = listOf(
        Triple(1, "Preface", 1), Triple(1, "Part One", 2), Triple(2, "Chapter 1: Çıkış – İçindekiler", 3),
        Triple(2, "Chapter 2", 5), Triple(3, "Section 2.1", 6), Triple(1, "Part Two", 8), Triple(2, "Chapter 3", 9), Triple(1, "Index", 12),
    )

    private fun checkMupdf(name: String) {
        val f = found(PdfOutline.read(resource(name)))
        assertEquals("$name pages", 12, f.pages)
        assertEquals(name, mupdf, summary(f))
    }

    @Test fun mupdfPlain() = checkMupdf("outline-plain.pdf")
    @Test fun mupdfObjectStreams() = checkMupdf("outline-objstm.pdf")
    @Test fun mupdfRc4With40BitKey() = checkMupdf("outline-rc4-40.pdf")
    @Test fun mupdfRc4With128BitKey() = checkMupdf("outline-rc4-128.pdf")
    @Test fun mupdfAes128() = checkMupdf("outline-aes-128.pdf")
    @Test fun mupdfAes256() = checkMupdf("outline-aes-256.pdf")
    @Test fun mupdfAes256WithObjectStreams() = checkMupdf("outline-aes-256-objstm.pdf")

    @Test fun aFileThatNeedsARealPasswordIsUnreadableNotWrong() {
        val r = PdfOutline.read(resource("outline-needs-password.pdf"))
        assertNotNull(r)
        assertEquals(PdfOutlineResult.Unreadable("encrypted"), r)
    }

    // ---- bookmarks into chapters
    @Test fun chaptersDropUnresolvedPagesAndKeepPageOrder() {
        val r = PdfOutlineResult.Found(
            100, listOf(PdfBookmark("B", 49, 1), PdfBookmark("Lost", -1, 1), PdfBookmark("A", 9, 1), PdfBookmark("", 20, 1), PdfBookmark("C", 49, 2))
        )
        val map = (PdfChapters.fromOutline(r) as ChapterResult.Found).map
        assertEquals(ChapterSource.Pdf, map.source)
        assertEquals(100, map.pages)
        assertEquals(listOf("A", "B", "C"), map.entries.map { it.title })
        assertEquals(listOf(10, 50, 50), map.entries.map { it.startPage })
        assertEquals(0.09, map.entries[0].startFraction, 1e-9)
        assertEquals(listOf(1, 1, 2), map.entries.map { it.depth })
    }

    @Test fun noUsablePageMeansNoChapters() {
        val r = PdfOutlineResult.Found(10, listOf(PdfBookmark("Lost", -1, 1)))
        assertEquals(ChapterResult.None(NoChapters.NoOutline), PdfChapters.fromOutline(r))
        assertEquals(ChapterResult.None(NoChapters.Unreadable), PdfChapters.fromOutline(PdfOutlineResult.Unreadable("x")))
        assertEquals(ChapterResult.None(NoChapters.NoOutline), PdfChapters.fromOutline(PdfOutlineResult.NoOutline))
    }

    @Test fun pdfDocEncodingTitles() {
        // 0x80 is a bullet and 0x93 the fi ligature in PDFDocEncoding; 0xE9 is e-acute as in Latin-1.
        assertEquals("• ﬁ é", PdfText.decode(byteArrayOf(0x80.toByte(), 0x20, 0x93.toByte(), 0x20, 0xE9.toByte())))
        assertEquals("AÇ", PdfText.decode(byteArrayOf(0xFE.toByte(), 0xFF.toByte(), 0, 0x41, 0, 0xC7.toByte())))
        assertEquals("café", PdfText.decode(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "café".toByteArray()))
    }

    @Test fun rc4MatchesTheKnownVector() {
        // RFC 6229 style check: key "Key", plaintext "Plaintext" -> BBF316E8D940AF0AD3.
        val out = PdfCrypto.rc4("Key".toByteArray(), "Plaintext".toByteArray())
        assertEquals("bbf316e8d940af0ad3", out.joinToString("") { "%02x".format(it) })
    }
}
