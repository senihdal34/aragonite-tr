package com.ethran.notable.gestures

import android.os.SystemClock

/**
 * Keeps a resting palm from triggering finger gestures (QuickNav, undo, page swipe, tool change).
 *
 * Two rules, both cheap and predictable:
 *  - while the pen is hovering or was used in the last [PEN_GRACE_MS], finger touches are ignored;
 *  - a finger that lands in the bottom strip of the screen (where the writing hand rests) is ignored.
 */
object PalmGuard {
    const val PEN_GRACE_MS = 1500L
    const val BOTTOM_DEAD_ZONE_DP = 96f

    @Volatile private var lastPenMs = 0L

    /** Call whenever the pen is seen: hover, touch or any raw drawing callback. */
    fun penSeen(nowMs: Long = SystemClock.uptimeMillis()) { lastPenMs = nowMs }

    fun penRecentlyActive(nowMs: Long, lastMs: Long = lastPenMs): Boolean =
        lastMs != 0L && nowMs - lastMs < PEN_GRACE_MS

    fun inBottomDeadZone(y: Float, heightPx: Float, density: Float): Boolean =
        heightPx > 0f && y >= heightPx - BOTTOM_DEAD_ZONE_DP * density

    /** True when a finger touch at [y] should be ignored by gesture handlers. */
    fun shouldIgnoreTouch(
        enabled: Boolean, y: Float, heightPx: Float, density: Float,
        nowMs: Long = SystemClock.uptimeMillis(), lastPenMs: Long = this.lastPenMs
    ): Boolean = enabled && (penRecentlyActive(nowMs, lastPenMs) || inBottomDeadZone(y, heightPx, density))
}

/**
 * Detects a press of the pen's side button (rising edge) with a short debounce, so one click
 * flips pen/eraser once even if the system reports the button state on every hover event.
 */
class PenButtonEdge(private val debounceMs: Long = 350L) {
    private var wasDown = false
    private var lastToggleMs = Long.MIN_VALUE / 2

    /** [buttonState] is MotionEvent.getButtonState(); returns true exactly once per click. */
    fun pressed(buttonState: Int, nowMs: Long): Boolean {
        val down = buttonState and (STYLUS_PRIMARY or STYLUS_SECONDARY) != 0
        val edge = down && !wasDown && nowMs - lastToggleMs >= debounceMs
        wasDown = down
        if (edge) lastToggleMs = nowMs
        return edge
    }

    companion object {
        const val STYLUS_PRIMARY = 32      // MotionEvent.BUTTON_STYLUS_PRIMARY
        const val STYLUS_SECONDARY = 64    // MotionEvent.BUTTON_STYLUS_SECONDARY
    }
}
