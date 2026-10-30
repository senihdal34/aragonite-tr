package com.ethran.notable.editor.drawing

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.geometry.Offset
import com.ethran.notable.planner.PlannerLayout
import com.ethran.notable.planner.PlannerWeeks
import java.time.LocalDate

// Paper-planner look for the e-ink screen: black rules, a day column on the left, check boxes.
// All sizes are in page pixels (device pixels at zoom 1); the screen is about 1860 x 2480.

private const val HEADER_H = 150f
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
private val titleP = paint(Color.BLACK, size = 44f, face = serifBold)
private val summary = paint(Color.DKGRAY, size = 26f, face = sansBold).apply { textAlign = Paint.Align.RIGHT; letterSpacing = 0.08f }
private val summaryBold = paint(Color.BLACK, size = 30f, face = sansBold).apply { textAlign = Paint.Align.RIGHT; letterSpacing = 0.1f }
private val subP = paint(Color.DKGRAY, size = 34f, face = sansBold).apply { textAlign = Paint.Align.RIGHT; letterSpacing = 0.08f }
private val dayName = paint(Color.BLACK, size = 30f, face = sansBold).apply { textAlign = Paint.Align.CENTER; letterSpacing = 0.12f }
private val dayNameInv = paint(Color.WHITE, size = 30f, face = sansBold).apply { textAlign = Paint.Align.CENTER; letterSpacing = 0.12f }
private val dayNum = paint(Color.BLACK, size = 80f, face = serifBold).apply { textAlign = Paint.Align.CENTER }
private val dayNumInv = paint(Color.WHITE, size = 80f, face = serifBold).apply { textAlign = Paint.Align.CENTER }
private val sectionP = paint(Color.BLACK, size = 52f, face = serifBold)

private fun checkbox(canvas: Canvas, x: Float, bottom: Float, size: Float = 34f) {
    canvas.drawRect(x, bottom - size, x + size, bottom, box)
}

// Logo in the header, loaded once.
private var logoBitmap: android.graphics.Bitmap? = null
private fun logo(context: android.content.Context): android.graphics.Bitmap? {
    logoBitmap?.let { return it }
    return runCatching {
        val src = android.graphics.BitmapFactory.decodeResource(context.resources, com.ethran.notable.R.drawable.logo_deusnote)
        android.graphics.Bitmap.createScaledBitmap(src, 96, 96, true)
    }.getOrNull().also { logoBitmap = it }
}

private fun drawHeader(context: android.content.Context, canvas: Canvas, w: Float, oy: Float, title: String, line1: String, line2: String, percent: Int) {
    logo(context)?.let { canvas.drawBitmap(it, 60f, 26f + oy, null) }
    canvas.drawText(title, 180f, 90f + oy, titleP)
    canvas.drawText(line1, w - 60f, 50f + oy, summaryBold)
    canvas.drawText(line2, w - 60f, 84f + oy, summary)
    canvas.drawText("YIL %$percent TAMAMLANDI", w - 60f, 118f + oy, summary)
    val barW = 300f
    canvas.drawRect(w - 60f - barW, 128f + oy, w - 60f, 138f + oy, box)
    canvas.drawRect(w - 60f - barW, 128f + oy, w - 60f - barW + barW * percent / 100f, 138f + oy, fillInk)
    canvas.drawRect(0f, PlannerLayout.HEADER_H - 8f + oy, w, PlannerLayout.HEADER_H + oy, fillInk)
}

/**
 * Weekly planner page: seven day rows that exactly fill the screen (the page does not scroll), every row with at
 * least five check-box lines. Tapping a date opens that day's page. Only the black label block marks today.
 */
fun drawPlannerWeekBg(
    context: android.content.Context, canvas: Canvas, scale: Float, monday: LocalDate, today: LocalDate = LocalDate.now()
) {
    val w = canvas.width / scale
    val h = canvas.height / scale
    canvas.drawColor(Color.WHITE)
    drawHeader(
        context, canvas, w, 0f, "Weekly Planner",
        "HAFTA ${PlannerWeeks.weekOfYear(monday)}", "${PlannerWeeks.rangeLabel(monday)} · ${monday.plusDays(3).year}".uppercase(TR),
        PlannerWeeks.yearPercent(today)
    )
    val layout = PlannerLayout.week(h)
    val rowH = layout.rowH
    val step = layout.step
    val labelW = PlannerLayout.LABEL_W
    for (i in 0 until 7) {
        val day = monday.plusDays(i.toLong())
        val y0 = layout.rowTop(i)
        val y1 = y0 + rowH
        val isToday = day == today
        if (isToday) canvas.drawRect(0f, y0, labelW, y1, fillInk)
        canvas.drawText(PlannerWeeks.DAY_NAMES[i].uppercase(TR), labelW / 2f, y0 + rowH * 0.26f, if (isToday) dayNameInv else dayName)
        canvas.drawText(day.dayOfMonth.toString(), labelW / 2f, y0 + rowH * 0.66f, if (isToday) dayNumInv else dayNum)
        canvas.drawText(PlannerWeeks.monthName(day).uppercase(TR), labelW / 2f, y1 - rowH * 0.1f, if (isToday) dayNameInv else dayName)
        canvas.drawLine(0f, y1, w, y1, ink)
        for (k in 1..layout.lines) {
            val ly = y0 + k * step
            checkbox(canvas, labelW + 28f, ly - step * 0.2f, step * 0.46f)
            if (k < layout.lines) canvas.drawLine(labelW + 28f, ly, w - 40f, ly, guide)
        }
    }
    canvas.drawLine(labelW, PlannerLayout.HEADER_H, labelW, PlannerLayout.HEADER_H + 7 * rowH, ink)
}

/** A day opened from the week page: a long check-box list that fills the screen (no scrolling). */
fun drawPlannerDayBg(
    context: android.content.Context, canvas: Canvas, scale: Float, day: LocalDate, today: LocalDate = LocalDate.now()
) {
    val w = canvas.width / scale
    val h = canvas.height / scale
    canvas.drawColor(Color.WHITE)
    val idx = day.dayOfWeek.value - 1
    drawHeader(
        context, canvas, w, 0f, PlannerWeeks.DAY_FULL[idx],
        "${day.dayOfMonth} ${PlannerWeeks.monthName(day)}".uppercase(TR), "HAFTA ${PlannerWeeks.weekOfYear(PlannerWeeks.mondayOf(day))} · ${day.year}",
        PlannerWeeks.yearPercent(today)
    )
    val lines = PlannerLayout.dayLines(h)
    val step = (h - PlannerLayout.HEADER_H - PlannerLayout.BOTTOM_MARGIN) / lines
    for (k in 1..lines) {
        val ly = PlannerLayout.HEADER_H + k * step
        checkbox(canvas, 70f, ly - step * 0.2f, step * 0.42f)
        canvas.drawLine(170f, ly, w - 40f, ly, guide)
    }
    canvas.drawLine(150f, PlannerLayout.HEADER_H, 150f, PlannerLayout.HEADER_H + lines * step, ink)
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
