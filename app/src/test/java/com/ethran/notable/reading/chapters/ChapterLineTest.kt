package com.ethran.notable.reading.chapters

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.ceil

class ChapterLineTest {
    private fun map(vararg e: Triple<String, Int, Double>, source: ChapterSource = ChapterSource.Epub, pages: Int = 0) =
        ChapterMap(source, pages, e.map { Chapter(it.first, it.second, it.third) })

    /** [n] chapters at depth 1, evenly spread over the book. */
    private fun even(n: Int) = map(*Array(n) { Triple("Chapter ${it + 1}", 1, it.toDouble() / n) })

    // ------------------------------------------------------------------------------------------ stations
    @Test fun stationsAreTheChaptersAsPercent() {
        val stops = requireNotNull(ChapterStops.stations(even(10)))
        assertEquals(10, stops.size)
        assertEquals(0.0, stops[0].pct, 1e-9)
        assertEquals(50.0, stops[5].pct, 1e-9)
        assertEquals("Chapter 10", stops.last().title)
    }

    @Test fun theShallowestLevelWithSixEntriesIsUsed() {
        // 3 parts at depth 1 and 9 chapters at depth 2: three parts are too few to read as chapters, so depth 2 is used.
        // Each part starts where its first chapter does, so the part folds into the chapter (no span of its own).
        val m = map(
            Triple("Part 1", 1, 0.0), Triple("C1", 2, 0.0), Triple("C2", 2, 0.1), Triple("C3", 2, 0.2),
            Triple("Part 2", 1, 0.3), Triple("C4", 2, 0.3), Triple("C5", 2, 0.4), Triple("C6", 2, 0.5),
            Triple("Part 3", 1, 0.6), Triple("C7", 2, 0.6), Triple("C8", 2, 0.7), Triple("C9", 2, 0.8),
        )
        assertEquals((1..9).map { "C$it" }, ChapterStops.stations(m)?.map { it.title })
        // A book whose depth 1 alone has six entries keeps only depth 1.
        val flat = map(*Array(6) { Triple("T$it", 1, it / 6.0) }, Triple("Sub", 2, 0.5))
        assertEquals((0..5).map { "T$it" }, ChapterStops.stations(flat)?.map { it.title })
    }

    @Test fun shortEntriesFoldIntoTheNextAndTheLastAlwaysStays() {
        // "Title page" and "Copyright" take under 1% of the book each; the last entry is kept however short.
        val m = map(
            Triple("Title page", 1, 0.000), Triple("Copyright", 1, 0.004), Triple("One", 1, 0.009), Triple("Two", 1, 0.3),
            Triple("Three", 1, 0.6), Triple("Four", 1, 0.8), Triple("Five", 1, 0.999),
        )
        assertEquals(listOf("One", "Two", "Three", "Four", "Five"), ChapterStops.stations(m)?.map { it.title })
    }

    @Test fun fewerThanTwoStopsIsNoLine() {
        assertNull(ChapterStops.stations(even(1)))
        assertNull(ChapterStops.stations(map(Triple("A", 1, 0.5), Triple("B", 1, 0.5))))   // the first folds into the second
        assertNull(ChapterStops.stations(ChapterMap(ChapterSource.Pdf, 10, emptyList())))
    }

    // ------------------------------------------------------------------------------------------ the line
    /** Ten stops at 0, 10, ... 90 percent; the reader at [pos] percent with [left] minutes to go. */
    private fun line(pos: Double, left: Int? = 100, n: Int = 10) = ChapterLine(requireNotNull(ChapterStops.stations(even(n))), pos, left)

    @Test fun theCurrentStopIsTheLastOneAtOrBeforeTheReader() {
        assertEquals(0, line(0.0).cur)
        assertEquals(0, line(9.9).cur)
        assertEquals(1, line(10.0).cur)
        assertEquals(4, line(49.0).cur)
        assertEquals(9, line(95.0).cur)
        assertEquals(3, line(25.0).next)
    }

    @Test fun nextStopAndMinutesAtTheForecastPace() {
        // At 25%: 100 minutes over the 75% left is 1.333 min per percent. The next stop (30%) is 5% away: 6.67, so 7 min.
        val l = line(25.0, left = 100)
        assertEquals(2, l.cur)
        assertEquals(3, l.next)
        assertEquals(7, l.minutesToNext)
        assertEquals(7, l.stopsLeft)
        assertEquals(33, l.minutes(5))                  // stop 5 at 50%: 25% * 1.333 = 33.3
    }

    @Test fun minutesAreAtLeastOneAndNullWithoutAPace() {
        assertEquals(1, line(29.9, left = 10).minutesToNext)
        assertNull(line(25.0, left = null).minutesToNext)
        assertNull(line(25.0, left = null).minutes(5))
    }

    @Test fun inTheLastChapterThereIsNoNextStopOnlyTheEnd() {
        val l = line(95.0, left = 12)
        assertTrue(l.atLastStop)
        assertEquals(l.cur, l.next)
        assertNull(l.minutesToNext)
        assertEquals(0, l.stopsLeft)
        assertEquals(0.0, l.trainFraction, 1e-9)
        assertFalse(line(10.0).atLastStop)
    }

    @Test fun beforeTheFirstChapterTheTrainWaitsAtTheFirstStop() {
        val l = ChapterLine(listOf(Stop("A", 5.0), Stop("B", 50.0)), pos = 1.0, minutesLeft = 60)
        assertEquals(0, l.cur)
        assertEquals(0.0, l.trainFraction, 1e-9)
    }

    @Test fun trainFractionIsHowFarToTheNextStop() {
        assertEquals(0.5, line(15.0).trainFraction, 1e-9)
        assertEquals(0.0, line(10.0).trainFraction, 1e-9)
    }

    @Test fun aLineNeedsTwoStops() {
        assertTrue(runCatching { ChapterLine(listOf(Stop("A", 0.0)), 0.0, null) }.isFailure)
    }

    // -------------------------------------------------------------------------------- where the reader is
    @Test fun aPdfReaderIsPlacedByPageSoAChapterOnTheNextPageIsNotYetReached() {
        // 100 pages; progress "61/100" means page 61, which starts at (61 - 1) / 100 = 60%.
        val m = map(Triple("A", 1, 0.0), Triple("B", 1, 0.60), Triple("C", 1, 0.61), source = ChapterSource.Pdf, pages = 100)
        assertEquals(60.0, ChapterLines.position(m, 61.0, 100), 1e-9)
        val stops = listOf(Stop("A", 0.0), Stop("B", 60.0), Stop("C", 61.0))
        assertEquals(1, ChapterLine(stops, ChapterLines.position(m, 61.0, 100), null).cur)   // reading page 61: in B, C starts on page 62
        assertEquals(2, ChapterLine(stops, ChapterLines.position(m, 62.0, 100), null).cur)
    }

    @Test fun aFinishedPdfIsOnItsLastPage() {
        val m = map(Triple("A", 1, 0.0), source = ChapterSource.Pdf, pages = 200)
        assertEquals(99.5, ChapterLines.position(m, 100.0, 200), 1e-9)
    }

    @Test fun anEpubOrAMismatchedPdfUsesTheProgressPercent() {
        assertEquals(37.5, ChapterLines.position(even(8), 37.5, 235), 1e-9)
        val pdf = map(Triple("A", 1, 0.0), source = ChapterSource.Pdf, pages = 100)
        assertEquals(37.5, ChapterLines.position(pdf, 37.5, 120), 1e-9)     // NeoReader counts 120 pages, the file has 100
        assertEquals(37.5, ChapterLines.position(pdf, 37.5, 0), 1e-9)       // page count unknown
    }

    @Test fun forBookBuildsTheLineAndMarksEpubAsApproximate() {
        val l = requireNotNull(ChapterLines.forBook(even(10), 25.0, 235, 100))
        assertTrue(l.approximate)
        assertEquals(2, l.cur)
        val pdf = ChapterMap(ChapterSource.Pdf, 100, List(10) { Chapter("C$it", 1, it / 10.0, it * 10 + 1) })
        assertFalse(requireNotNull(ChapterLines.forBook(pdf, 25.0, 100, 100)).approximate)
        assertNull(ChapterLines.forBook(even(1), 25.0, 100, 100))
    }

    // ------------------------------------------------------------------------------------- presentation
    @Test fun timetableListsTheNextStopsThenTheEnd() {
        val t = ChapterLayout.timetableRows(line(25.0), 4)          // at stop 2 of 0..9
        assertEquals(listOf(2, 3, 4, 9), t.indices)
        assertTrue(t.gap)
        val all = ChapterLayout.timetableRows(line(75.0), 6)        // at stop 7: stops 7, 8, 9 fit, no gap
        assertEquals(listOf(7, 8, 9), all.indices)
        assertFalse(all.gap)
        assertEquals(4, ChapterLayout.timetableRowCount(4 * 17 + 12, 17))
    }

    @Test fun labelsGoLeftToRightWithoutCollidingAndTheNextOneIsBold() {
        val width = 600
        val placed = ChapterLayout.placeLabels(line(5.0), width, maxLabels = 3, gap = 8, center = { 9.0 + (width - 18.0) * it / 9 }, labelWidth = { 90 }, endWidth = 30)
        assertEquals(ChapterLayout.END_LABEL, placed[0].index)
        assertTrue(placed[0].bold)
        assertEquals(listOf(ChapterLayout.END_LABEL, 1, 3), placed.map { it.index })   // stop 2 collides with stop 1 and is skipped
        assertTrue(placed[1].bold)
        assertFalse(placed[2].bold)
    }

    @Test fun placedLabelsNeverOverlap() {
        val placed = ChapterLayout.placeLabels(line(5.0), 600, 6, 8, { 30.0 + 60.0 * it }, { 100 }, 30)
        val stops = placed.map { it.index }.filter { it >= 0 }
        assertEquals(1, stops.first())
        fun range(p: PlacedLabel) = p.x until p.x + (if (p.index < 0) 30 else 100)
        for (a in placed.indices) for (b in a + 1 until placed.size) {
            val ra = range(placed[a]); val rb = range(placed[b])
            assertTrue("labels $a and $b overlap", ra.last + 8 <= rb.first || rb.last + 8 <= ra.first)
        }
    }

    @Test fun ifTheNextStopsLabelCannotBePlacedNothingFartherAlongIsShown() {
        // "End" is wide and takes the room where the next stop's label would go.
        val l = ChapterLine(listOf(Stop("A", 0.0), Stop("B", 50.0), Stop("C", 100.0)), 1.0, null)
        val placed = ChapterLayout.placeLabels(l, 100, 6, 8, { 10.0 + 40.0 * it }, { 90 }, 80)
        assertEquals(listOf(ChapterLayout.END_LABEL), placed.map { it.index })
    }

    @Test fun inTheLastChapterOnlyEndIsLabelled() {
        val placed = ChapterLayout.placeLabels(line(95.0), 600, 6, 8, { 30.0 + 60.0 * it }, { 50 }, 30)
        assertEquals(listOf(ChapterLayout.END_LABEL), placed.map { it.index })
    }

    @Test fun wideBoxesGetSixLabelsNarrowOnesThree() {
        assertEquals(3, ChapterLayout.maxLabels(500.0))
        assertEquals(6, ChapterLayout.maxLabels(500.1))
    }

    @Test fun labelTextCutsLongTitlesAndAddsTheTimeWithATildeForEpub() {
        val l = line(25.0, left = 100)
        assertEquals("Chapter 4 · 7m", ChapterLayout.labelText(l, 3) { "${it}m" })
        val approx = ChapterLine(l.stops, 25.0, 100, approximate = true)
        assertEquals("Chapter 4 · ~7m", ChapterLayout.labelText(approx, 3) { "${it}m" })
        val long = ChapterLine(listOf(Stop("An extraordinarily long chapter title that never ends", 0.0), Stop("B", 100.0)), 0.0, null)
        assertTrue(ChapterLayout.labelText(long, 0) { "" }.length <= TitleText.LABEL_MAX)
    }

    // --------------------------------------------------------------------------------------- layout plan
    /** 2.5 pixels per plugin dp, as on the 10.3 inch tablet. */
    private val metrics = object : LayoutMetrics {
        override fun px(dp: Double) = ceil(dp * 2.5).toInt()
        override fun lh1(dp: Double) = ceil(1.362 * px(dp)).toInt()
    }

    /** The plan for a box [wDp] by [hDp] dp (12 dp of padding each side). */
    private fun plan(line: ChapterLine, wDp: Double, hDp: Double) =
        ChapterPlans.choose(metrics, line, metrics.px(wDp - 24), metrics.px(hDp - 24))

    @Test fun aTallNarrowBoxGetsATimetable() {
        val p = plan(line(25.0), wDp = 260.0, hDp = 330.0)
        assertTrue("got $p", p is ChapterPlan.Timetable && p.rows >= 3)
    }

    @Test fun aWideBoxGetsTheNextStopOverTheLineAndSmallerOnesLoseLabelsThenFooter() {
        assertEquals(ChapterPlan.Big(16.0, true, true), plan(line(25.0), 520.0, 160.0))
        assertEquals(ChapterPlan.Big(16.0, false, true), plan(line(25.0), 520.0, 108.0))
        assertEquals(ChapterPlan.Big(15.0, false, false), plan(line(25.0), 520.0, 92.0))
    }

    @Test fun aSmallBoxGetsTheMiniatureThenJustTheTime() {
        assertEquals(ChapterPlan.Mini(true), plan(line(25.0), 200.0, 110.0))
        assertEquals(ChapterPlan.Mini(false), plan(line(25.0), 200.0, 94.0))
        assertEquals(ChapterPlan.Minimal, plan(line(25.0), 200.0, 60.0))
    }

    @Test fun theLastChapterNeverGetsATimetable() {
        assertFalse(plan(line(95.0), 260.0, 330.0) is ChapterPlan.Timetable)
    }

    @Test fun signatureChangesWithTheStopOrTheTime() {
        assertFalse(line(25.0).signature == line(35.0).signature)
        assertEquals(line(25.0).signature, line(25.0).signature)
    }

    // ------------------------------------------------------------------------------------------ titles
    @Test fun titlesAreCleaned() {
        assertEquals("Chapter 1 Fine", TitleText.clean("  Chapter 1​\n\t Fine  "))
        assertEquals("Software", TitleText.clean("Soft­ware"))
        assertEquals("", TitleText.clean(" \u0000 \n"))
        assertTrue(TitleText.clean("x".repeat(500)).length <= TitleText.MAX_STORED)
    }

    @Test fun titlesAreShortenedAtAWordBoundary() {
        assertEquals("Short", TitleText.shorten("Short", 22))
        val s = TitleText.shorten("The Psychology of Money and Markets", 22)
        assertEquals("The Psychology of…", s)
        assertTrue(s.length <= 22)
        // One long word is cut inside the word.
        val w = TitleText.shorten("Supercalifragilisticexpialidocious", 12)
        assertEquals(12, w.length)
        assertTrue(w.endsWith("…"))
    }
}
