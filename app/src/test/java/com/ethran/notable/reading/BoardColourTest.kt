package com.ethran.notable.reading

import com.ethran.notable.reading.board.BoardColour
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BoardColourTest {
    @Test fun mixBlendsLinearly() {
        assertEquals("#000000", BoardColour.mix("#000000", "#ffffff", 0.0))
        assertEquals("#ffffff", BoardColour.mix("#000000", "#ffffff", 1.0))
        assertEquals("#808080", BoardColour.mix("#000000", "#ffffff", 0.5))
    }

    @Test fun boostOfOneChangesNothingAndMoreSaturates() {
        assertEquals("#de4127", BoardColour.boost("#de4127", 1.0))
        val boosted = BoardColour.boost("#de4127", 1.4)
        assertTrue(boosted.substring(1, 3).toInt(16) >= 0xde)      // red goes up (clamped at 255)
        assertEquals("#808080", BoardColour.boost("#808080", 1.4)) // a grey has no chroma to push
    }

    @Test fun theBoardUsesThePlainFamilyColoursOfTheKoreaderScreenshots() {
        assertEquals(1.0, BoardColour.PANEL_BOOST, 0.0)
        assertEquals("#de4127", BoardColour.FAM.getValue("red").ink)
    }

    @Test fun hlsRoundTripsPrimaries() {
        assertEquals("#ff0000", BoardColour.hlsHex(0.0, 0.5, 1.0))
        assertEquals("#00ff00", BoardColour.hlsHex(1.0 / 3, 0.5, 1.0))
        val (h, l, s) = BoardColour.rgbToHls(0.0, 0.0, 1.0)
        assertEquals(2.0 / 3, h, 1e-9); assertEquals(0.5, l, 1e-9); assertEquals(1.0, s, 1e-9)
    }

    @Test fun anOrangeJacketGivesAnOrangeFamilyAndAGreyOneGivesNone() {
        val orange = IntArray(24 * 36) { 0xEE8410 }
        val fam = BoardColour.familyFromPixels(orange)
        assertNotNull(fam)
        val (h, _, _) = fam!!.deep.let { BoardColour.rgbToHls(it.substring(1, 3).toInt(16) / 255.0, it.substring(3, 5).toInt(16) / 255.0, it.substring(5, 7).toInt(16) / 255.0) }
        assertEquals(0.085, h, 0.03)   // orange hue
        assertNull(BoardColour.familyFromPixels(IntArray(24 * 36) { 0x777777 }))
        assertNull(BoardColour.familyFromPixels(IntArray(0)))
    }

    @Test fun everyWidgetHasAColourFamily() {
        for ((_, fam) in BoardColour.HUE) assertTrue(fam == "cover" || BoardColour.FAM.containsKey(fam))
    }
}
