package com.ethran.notable.gestures

import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PalmGuardTest {
    private val h = 2480f
    private val density = 1.875f   // Boox Go 10.3, 300 dpi

    @Test fun `finger in the bottom strip is ignored`() =
        assertTrue(PalmGuard.shouldIgnoreTouch(true, y = 2400f, heightPx = h, density = density, nowMs = 10_000, lastPenMs = 0))

    @Test fun `finger above the strip is accepted when the pen is away`() =
        assertFalse(PalmGuard.shouldIgnoreTouch(true, y = 1200f, heightPx = h, density = density, nowMs = 10_000, lastPenMs = 0))

    @Test fun `any finger is ignored while the pen was just used`() =
        assertTrue(PalmGuard.shouldIgnoreTouch(true, y = 1200f, heightPx = h, density = density, nowMs = 10_000, lastPenMs = 9_000))

    @Test fun `pen grace period ends`() =
        assertFalse(PalmGuard.shouldIgnoreTouch(true, y = 1200f, heightPx = h, density = density, nowMs = 12_000, lastPenMs = 9_000))

    @Test fun `guard switched off ignores nothing`() =
        assertFalse(PalmGuard.shouldIgnoreTouch(false, y = 2470f, heightPx = h, density = density, nowMs = 10_000, lastPenMs = 9_900))

    @Test fun `dead zone is 96 dp tall`() {
        assertTrue(PalmGuard.inBottomDeadZone(h - 96 * density + 1, h, density))
        assertFalse(PalmGuard.inBottomDeadZone(h - 96 * density - 5, h, density))
    }

    @Test fun `button click toggles once, held button does not repeat`() {
        val e = PenButtonEdge()
        assertFalse(e.pressed(0, 0))
        assertTrue(e.pressed(PenButtonEdge.STYLUS_PRIMARY, 1_000))
        assertFalse(e.pressed(PenButtonEdge.STYLUS_PRIMARY, 1_100))   // still held
        assertFalse(e.pressed(0, 1_200))
        assertTrue(e.pressed(PenButtonEdge.STYLUS_PRIMARY, 2_000))    // second click
    }

    @Test fun `bounce within the debounce window is one click`() {
        val e = PenButtonEdge()
        assertTrue(e.pressed(PenButtonEdge.STYLUS_SECONDARY, 5_000))
        assertFalse(e.pressed(0, 5_050))
        assertFalse(e.pressed(PenButtonEdge.STYLUS_SECONDARY, 5_100))
        assertFalse(e.pressed(0, 5_200))
    }
}
