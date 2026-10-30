package com.ethran.notable.editor.drawing

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.geometry.Offset
import com.ethran.notable.planner.PlannerWeeks
import java.time.LocalDate

// Paper-planner look for the e-ink screen: black rules, a day column on the left, check boxes.
// All sizes are in page pixels (device pixels at zoom 1); the screen is about 1860 x 2480.

private const val HEADER_H = 150f
private const val LABEL_W = 250f
private const val LINES_PER_DAY = 4
private const val BOTTOM_MARGIN = 80f   // keeps the last row clear of the scroll indicator
private val TR = java.util.Locale("tr", "TR")

private val serifBold = Typeface.create(Typeface.SERIF, Typeface.BOLD)
private val sansBold = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)

private fun paint(color: Int, width: Float = 1f, style: Paint.Style = Paint.Style.FILL, size: Float = 0f, face: Typeface? = null) =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color; strokeWidth = width; this.style = style
        if (size > 0f) textSize = size
        if (face != null) typeface = face
    }

private val ink = paint(Color.BLACK, 3f)
private val inkThin = paint(Color.BLACK, 2f)
private val guide = paint(Color.GRAY, 1.5f)
private val box = paint(Color.BLACK, 3f, Paint.Style.STROKE)
private val fillInk = paint(Color.BLACK)
private val titleP = paint(Color.BLACK, size = 66f, face = serifBold)
private val subP = paint(Color.DKGRAY, size = 34f, face = sansBold).apply { textAlign = Paint.Align.RIGHT; letterSpacing = 0.08f }
private val dayName = paint(Color.BLACK, size = 36f, face = sansBold).apply { textAlign = Paint.Align.CENTER; letterSpacing = 0.12f }
private val dayNameInv = paint(Color.WHITE, size = 36f, face = sansBold).apply { textAlign = Paint.Align.CENTER; letterSpacing = 0.12f }
private val dayNum = paint(Color.BLACK, size = 96f, face = serifBold).apply { textAlign = Paint.Align.CENTER }
private val dayNumInv = paint(Color.WHITE, size = 96f, face = serifBold).apply { textAlign = Paint.Align.CENTER }
private val sectionP = paint(Color.BLACK, size = 52f, face = serifBold)

private fun checkbox(canvas: Canvas, x: Float, bottom: Float, size: Float = 34f) {
    canvas.drawRect(x, bottom - size, x + size, bottom, box)
}

/** Old handwritten week page (kept so pages that already have ink still draw). Seven rows, one per day. Today's row is inverted; the date comes from the device clock. */
fun drawPlannerWeekBg(canvas: Canvas, scroll: Offset, scale: Float, monday: LocalDate, today: LocalDate = LocalDate.now()) {
    val w = canvas.width / scale
    val h = canvas.height / scale
    canvas.drawColor(Color.WHITE)
    val oy = -scroll.y

    canvas.drawText("Haftalık plan", 60f, 100f + oy, titleP)
    canvas.drawText(PlannerWeeks.weekLabel(monday).uppercase(TR), w - 60f, 98f + oy, subP)
    canvas.drawRect(0f, HEADER_H - 8f + oy, w, HEADER_H + oy, fillInk)

    val rowH = ((h - HEADER_H - BOTTOM_MARGIN) / 7f).coerceAtLeast(240f)
    for (i in 0 until 7) {
        val day = monday.plusDays(i.toLong())
        val y0 = HEADER_H + i * rowH + oy
        val y1 = y0 + rowH
        val isToday = day == today
        if (isToday) canvas.drawRect(0f, y0, LABEL_W, y1, fillInk)
        canvas.drawText(PlannerWeeks.DAY_NAMES[i].uppercase(TR), LABEL_W / 2f, y0 + 56f, if (isToday) dayNameInv else dayName)
        canvas.drawText(day.dayOfMonth.toString(), LABEL_W / 2f, y0 + rowH * 0.5f + 58f, if (isToday) dayNumInv else dayNum)
        if (i < 6) canvas.drawLine(0f, y1, w, y1, ink)

        // writing lines with a check box in front of each
        val step = rowH / LINES_PER_DAY
        for (k in 1..LINES_PER_DAY) {
            val ly = y0 + k * step
            checkbox(canvas, LABEL_W + 28f, ly - 10f)
            if (k < LINES_PER_DAY) canvas.drawLine(LABEL_W + 28f, ly, w - 40f, ly, guide)
        }
    }
    canvas.drawLine(LABEL_W, HEADER_H + oy, LABEL_W, HEADER_H + 7 * rowH + oy, ink)
}

/** Second page of the week: a long check-box list and a ruled notes area (like the paper planner). */
fun drawPlannerTodoBg(canvas: Canvas, scroll: Offset, scale: Float, monday: LocalDate) {
    val w = canvas.width / scale
    val h = canvas.height / scale
    canvas.drawColor(Color.WHITE)
    val oy = -scroll.y

    canvas.drawText("Yapılacaklar", 60f, 100f + oy, titleP)
    canvas.drawText("${PlannerWeeks.weekNumber(monday)}. HAFTA", w - 60f, 98f + oy, subP)
    canvas.drawRect(0f, HEADER_H - 8f + oy, w, HEADER_H + oy, fillInk)

    val step = 112f
    val items = 14
    for (k in 0 until items) {
        val ly = HEADER_H + (k + 1) * step + oy
        checkbox(canvas, 70f, ly - 22f, 38f)
        canvas.drawLine(170f, ly, w - 40f, ly, guide)
    }
    val listEnd = HEADER_H + items * step + oy
    canvas.drawLine(150f, HEADER_H + oy, 150f, listEnd, ink)

    val notesTop = listEnd + 90f
    canvas.drawRect(0f, notesTop - 62f, w, notesTop - 54f, fillInk)
    canvas.drawText("Notlar", 60f, notesTop, sectionP)
    var y = notesTop + 80f
    while (y < h + 80f) { canvas.drawLine(60f, y, w - 40f, y, guide); y += 80f }
}
