package com.ethran.notable.io

import com.ethran.notable.data.db.Stroke
import com.ethran.notable.data.db.StrokePoint
import com.ethran.notable.editor.utils.Pen
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HwrStrikeTest {
    private fun stroke(l: Float, t: Float, r: Float, b: Float) = Stroke(
        size = 3f, pen = Pen.BALLPEN, left = l, top = t, right = r, bottom = b,
        points = listOf(StrokePoint(l, t), StrokePoint(r, b)), pageId = "p"
    )

    /** A text line: letters 60 px tall, 40 px wide, from x=100. */
    private fun textLine(top: Float, letters: Int = 12) =
        (0 until letters).map { stroke(100f + it * 50f, top, 140f + it * 50f, top + 60f) }

    @Test fun `long flat line through the middle of text is a strike`() {
        val text = textLine(1000f)
        val line = stroke(90f, 1022f, 700f, 1040f)             // crosses the letters at mid height
        val split = HwrStrikeDetector.split(text + line)
        assertEquals(1, split.strikes.size)
        assertEquals(text.size, split.text.size)               // the line itself is not recognized
    }

    @Test fun `underline is dropped but not marked as strike`() {
        val text = textLine(1000f)
        val underline = stroke(90f, 1058f, 700f, 1072f)        // at the bottom edge of the text
        val split = HwrStrikeDetector.split(text + underline)
        assertTrue(split.strikes.isEmpty())
        assertEquals(1, split.ignored.size)
        assertEquals(text.size, split.text.size)
    }

    @Test fun `a wide flat word stroke as tall as its text is not a strike`() {
        val dots = (0 until 6).map { stroke(110f + it * 50f, 1000f, 120f + it * 50f, 1012f) }  // i-dots, t-bars
        val word = stroke(100f, 1000f, 420f, 1060f)            // cursive word: aspect 0.19, as tall as the text
        val split = HwrStrikeDetector.split(dots + word)
        assertTrue(split.strikes.isEmpty())
        assertTrue(word in split.text)
    }

    @Test fun `long zigzag strike as tall as the letters is still a strike`() {
        val text = (0 until 30).map { stroke(100f + it * 30f, 1000f, 125f + it * 30f, 1040f) }   // 900 px of letters
        val zigzag = stroke(90f, 1000f, 1000f, 1080f)           // h=80, w=910, crossing 30 strokes
        val split = HwrStrikeDetector.split(text + zigzag)
        assertEquals(1, split.strikes.size)
        assertEquals(text.size, split.text.size)
    }

    @Test fun `divider rule with no text on it is dropped`() {
        val split = HwrStrikeDetector.split(textLine(500f) + stroke(100f, 900f, 1500f, 915f))
        assertEquals(1, split.ignored.size)
        assertTrue(split.strikes.isEmpty())
    }

    @Test fun `short flat stroke such as an arrow shaft stays in the text`() {
        val arrow = stroke(100f, 2000f, 330f, 2030f)
        val split = HwrStrikeDetector.split(listOf(arrow))
        assertTrue(arrow in split.text)
    }

    // --- marker ---

    private val strikes = listOf(InkBox(100f, 1018f, 700f, 1040f))

    private fun w(label: String, x: Float) = HwrWord(label, box = MmBox(x, 0f, 10f, 5f))

    @Test fun `words under the line are wrapped in strikethrough`() {
        val strikes = listOf(InkBox(100f, 1018f, 400f, 1040f))
        // ink 100..820 px wide and 1000..1060 tall; word boxes in mm map onto it (scale 6 px/mm)
        val ink = InkBox(100f, 1000f, 820f, 1060f)
        val words = listOf(w("Vekil", 0f), HwrWord(" "), w("hesapları", 20f), HwrWord(" "), w("ile", 80f), HwrWord(" "), w("konuş", 110f))
        // last box ends at 120mm -> scale 720/120 = 6; height 5mm*6=30 (vs ink 60 -> mismatch) so give tall boxes
        val tall = words.map { if (it.box != null) it.copy(box = it.box.copy(height = 10f)) else it }
        val flags = HwrStrikeMarker.struckFlags(tall, ink, strikes)
        assertEquals(listOf(true, false, true, false, false, false, false), flags.toList())   // "ile" starts at x=580 > strike end
        val out = HwrStrikeMarker.render(tall.map { it.label }, flags)
        assertEquals("~~Vekil hesapları~~ ile konuş", out)
    }

    @Test fun `no strike line means text unchanged`() {
        val words = listOf(w("a", 0f), HwrWord(" "), w("b", 20f))
        val flags = HwrStrikeMarker.struckFlags(words, InkBox(0f, 0f, 100f, 50f), emptyList())
        assertEquals("a b", HwrStrikeMarker.render(words.map { it.label }, flags))
    }

    @Test fun `scale mismatch between ink and words marks nothing`() {
        val ink = InkBox(100f, 1000f, 820f, 1500f)             // way taller than the words say
        val words = listOf(w("Vekil", 0f), HwrWord(" "), w("x", 100f))
        val flags = HwrStrikeMarker.struckFlags(words, ink, strikes)
        assertTrue(flags.none { it })
    }

    @Test fun `strike run does not cross a line break`() {
        val texts = listOf("one", " ", "two", "\n", "three")
        val out = HwrStrikeMarker.render(texts, booleanArrayOf(true, false, true, false, true))
        assertEquals("~~one two~~\n~~three~~", out)
    }

    @Test fun `punctuation word inside a strike keeps the run open`() {
        val out = HwrStrikeMarker.render(listOf("a", " ", "b"), booleanArrayOf(true, false, true))
        assertEquals("~~a b~~", out)
    }
}
