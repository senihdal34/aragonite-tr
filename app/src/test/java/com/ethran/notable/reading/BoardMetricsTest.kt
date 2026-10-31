package com.ethran.notable.reading

import com.ethran.notable.reading.board.BoardGrid
import com.ethran.notable.reading.board.BoardMetrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BoardMetricsTest {
    // The Boox tablet: 1860 x 2480 px at 300 dpi, a 6.2 inch short side.
    private val tablet = BoardMetrics.scale(1860, 2480, 300f)

    @Test fun densityStepIsClampedBetweenTheLimits() {
        assertEquals(0.78, BoardMetrics.densityAdjust(1860, 300f), 1e-9)   // 6.2 in: sqrt(3.6 / 6.2) = 0.76, clamped up
        assertEquals(1.0, BoardMetrics.densityAdjust(1080, 300f), 1e-9)    // 3.6 in
        assertEquals(1.12, BoardMetrics.densityAdjust(600, 300f), 1e-9)    // 2 in: sqrt(1.8) = 1.34, clamped down
        assertEquals(1.0, BoardMetrics.densityAdjust(0, 300f), 1e-9)
        assertEquals(1.0, BoardMetrics.densityAdjust(1080, 0f), 1e-9)
    }

    @Test fun oneDpOnTheTabletIsAboutTwoAndAHalfPixels() {
        // dp = (short / 600) * adj * userScale = 3.1 * 0.78 * 1.05
        assertEquals(3.1 * 0.78 * 1.05, tablet.dp, 1e-9)
        assertEquals(2.54, tablet.dp, 0.01)
        assertEquals(3, tablet.px(1))
        assertEquals(31, tablet.px(12))
        assertEquals(0, tablet.px(0))
    }

    @Test fun aPositiveSizeNeverCollapsesToZero() {
        val tiny = BoardMetrics.scale(300, 400, 300f)
        assertTrue(tiny.px(0.1) >= 1)
    }

    @Test fun lineHeightsFollowTheNotoFace() {
        // A one-line text is ceil(1.362 em) high, a multi-line text line is round(1.3 em).
        assertEquals(Math.ceil(1.362 * tablet.px(13)).toInt(), tablet.lh1(13))
        assertEquals(Math.round(1.3 * tablet.px(14)).toInt(), tablet.lh(14))
        assertEquals(tablet.lh1(14), tablet.textBoxHeight(1, 14.0))
        assertTrue(tablet.textBoxHeight(3, 14.0) >= 3 * tablet.lh(14))
    }

    @Test fun theTabletGetsFourColumnsInPortraitAndSixInLandscape() {
        val portrait = BoardMetrics.scale(1860, 2480, 300f).layoutWidth(1860).toInt()
        val landscape = BoardMetrics.scale(2480, 1860, 300f).layoutWidth(2480).toInt()
        assertEquals(4, BoardGrid.columnsFor(portrait))
        assertEquals(24, BoardGrid.marginFor(portrait))
        assertEquals(6, BoardGrid.columnsFor(landscape))
        assertEquals(28, BoardGrid.marginFor(landscape))
    }

    @Test fun userScaleGrowsTheUnitButNotTheLayoutWidth() {
        val big = BoardMetrics.scale(1860, 2480, 300f, 1.2)
        assertTrue(big.dp > tablet.dp)
        assertEquals(tablet.layoutWidth(1860), big.layoutWidth(1860), 1e-9)
    }
}
