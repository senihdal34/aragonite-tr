package com.ethran.notable.io

import com.ethran.notable.data.db.Stroke
import com.ethran.notable.data.db.StrokePoint
import com.ethran.notable.editor.utils.Pen
import org.junit.Test
import java.io.File
import java.nio.file.Files
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Regression tests for the "export to PC loses text and image" bugs:
 *  - a long page was sent to handwriting recognition in one request (timeout, empty text)
 *  - the image embed used Obsidian-only syntax and unsafe file names
 *  - re-syncing a page left stale duplicates behind
 */
class InboxExportRegressionTest {

    private fun stroke(top: Float, bottom: Float = top + 20f) = Stroke(
        size = 3f, pen = Pen.BALLPEN, top = top, bottom = bottom, left = 10f, right = 200f,
        points = listOf(StrokePoint(10f, top), StrokePoint(200f, bottom)), pageId = "p"
    )

    // --- HwrChunker ---

    @Test
    fun `long page is split into several small chunks`() {
        // 40 text lines x 50 strokes = 2000 strokes, like the 1960-stroke note that came out empty
        val strokes = (0 until 2000).map { stroke(top = (it / 50) * 100f + (it % 50)) }
        val chunks = HwrChunker.chunkByLines(strokes)
        assertTrue(chunks.size > 5, "expected many chunks, got ${chunks.size}")
        assertTrue(chunks.all { it.size <= HwrChunker.DEFAULT_MAX_STROKES * 2 })
        assertEquals(2000, chunks.sumOf { it.size }, "no stroke may be lost")
    }

    @Test
    fun `chunks are cut only at vertical gaps and keep reading order`() {
        val strokes = (0 until 600).map { stroke(top = it * 10f, bottom = it * 10f + 8f) }.shuffled()
        val chunks = HwrChunker.chunkByLines(strokes)
        for (i in 1 until chunks.size) {
            val prevBottom = chunks[i - 1].maxOf { it.bottom }
            val nextTop = chunks[i].minOf { it.top }
            assertTrue(nextTop >= prevBottom, "chunk $i overlaps the previous one")
        }
    }

    @Test
    fun `stroke taller than chunk size does not stop splitting`() {
        val strokes = listOf(stroke(0f, 5000f)) + (1..400).map { stroke(it * 5f, it * 5f + 4f) }
        val chunks = HwrChunker.chunkByLines(strokes)
        assertEquals(401, chunks.sumOf { it.size })
        assertTrue(chunks.size >= 2)
    }

    @Test
    fun `no strokes means no chunks`() {
        assertTrue(HwrChunker.chunkByLines(emptyList()).isEmpty())
    }

    @Test
    fun `failed chunk splits at the widest gap near the middle`() {
        // two text blocks of 20 strokes each, big gap between them
        val strokes = (0 until 20).map { stroke(it * 10f) } + (0 until 20).map { stroke(2000f + it * 10f) }
        val (a, b) = HwrChunker.splitInHalf(strokes)!!
        assertEquals(20, a.size)
        assertEquals(20, b.size)
        assertTrue(a.all { it.top < 1000f } && b.all { it.top >= 2000f })
    }

    @Test
    fun `tiny chunk is not split`() {
        assertEquals(null, HwrChunker.splitInHalf((0 until 5).map { stroke(it * 10f) }))
    }

    // --- Markdown / file names ---

    @Test
    fun `file name loses characters that break wiki and markdown links`() {
        val clean = InboxSyncEngine.sanitizeFileName("A[[b]] #c ^d |e %f")
        for (bad in listOf("[", "]", "#", "^", "|", "%")) assertFalse(clean.contains(bad), "still contains $bad")
    }

    @Test
    fun `image embed is standard markdown with encoded relative path`() {
        val md = InboxSyncEngine.generateMarkdown(
            createdDate = "2026-09-14", tags = emptyList(), content = "x",
            baseName = "Gün 1- 14 Eylül-2026-09-14-10-46-16"
        )
        assertContains(md, "![page 1](jpg_archive/G%C3%BCn%201-%2014%20Eyl%C3%BCl-2026-09-14-10-46-16-page-1.jpg)")
        assertFalse(md.contains("![["), "Obsidian-only embed must not be used")
    }

    @Test
    fun `empty recognition result is explained instead of silently blank`() {
        val md = InboxSyncEngine.generateMarkdown("2026-09-14", emptyList(), "  ")
        assertContains(md, "could not be recognized")
    }

    // --- Stale file cleanup ---

    @Test
    fun `re-sync removes earlier export of the same page but keeps other notes`() {
        val dir = Files.createTempDirectory("inbox").toFile()
        try {
            val ts = "2026-09-14-10-46-16"
            fun touch(rel: String) = File(dir, rel).also { it.parentFile.mkdirs(); it.writeText("x") }
            val oldMd = touch("Eski baslik-$ts.md")
            val oldJpg = touch("jpg_archive/Eski baslik-$ts-page-1.jpg")
            val oldSb1 = touch("sb1_archive/Eski baslik-$ts-page-1.sb1")
            val newMd = touch("Yeni baslik-$ts.md")
            val newJpg = touch("jpg_archive/Yeni baslik-$ts-page-1.jpg")
            val other = touch("Baska not-2026-10-12-13-18-38.md")
            val otherJpg = touch("jpg_archive/Baska not-2026-10-12-13-18-38-page-1.jpg")

            InboxSyncEngine.removePreviousExports(dir, ts, "Yeni baslik-$ts")

            assertFalse(oldMd.exists()); assertFalse(oldJpg.exists()); assertFalse(oldSb1.exists())
            assertTrue(newMd.exists()); assertTrue(newJpg.exists())
            assertTrue(other.exists()); assertTrue(otherJpg.exists())
        } finally {
            dir.deleteRecursively()
        }
    }
}
