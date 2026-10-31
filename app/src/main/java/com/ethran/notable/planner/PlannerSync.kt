package com.ethran.notable.planner

import androidx.compose.ui.geometry.Offset
import com.ethran.notable.data.AppRepository
import com.ethran.notable.data.PageDataManager
import com.ethran.notable.data.db.Stroke
import com.ethran.notable.editor.utils.offsetStroke
import java.io.File
import java.time.LocalDate
import java.util.UUID

/**
 * Keeps the first lines of a day page equal to that day's row on the week page. Both use the same line step and
 * the same horizontal layout, so a copy only moves the ink up or down. Syncing happens when a planner page is
 * left: the page you leave overwrites the same region of its counterpart, so erasing works too.
 */
object PlannerSync {
    /** Strokes whose vertical centre lies in [top, bottom). */
    fun inRegion(strokes: List<Stroke>, top: Float, bottom: Float): List<Stroke> =
        strokes.filter { val c = (it.top + it.bottom) / 2f; c >= top && c < bottom }

    /** Copies of [strokes] on [targetPageId], moved by [dy]; ids are derived, so syncing twice gives the same ids. */
    fun copies(strokes: List<Stroke>, dy: Float, targetPageId: String): List<Stroke> = strokes.map { s ->
        offsetStroke(s, Offset(0f, dy)).copy(
            id = UUID.nameUUIDFromBytes("${s.id}>$targetPageId".toByteArray()).toString(), pageId = targetPageId
        )
    }

    /** Replaces the region [dstTop, dstTop + height) of the target with the source region starting at [srcTop]. */
    suspend fun copyRegion(app: AppRepository, filesDir: File, sourceId: String, srcTop: Float, targetId: String, dstTop: Float, height: Float) {
        val source = app.pageRepository.getWithStrokeById(sourceId).strokes
        val target = app.pageRepository.getWithStrokeById(targetId).strokes
        val moved = copies(inRegion(source, srcTop, srcTop + height), dstTop - srcTop, targetId)
        val old = inRegion(target, dstTop, dstTop + height)
        if (old.isEmpty() && moved.isEmpty()) return
        if (old.isNotEmpty()) app.strokeRepository.deleteAll(old.map { it.id })
        if (moved.isNotEmpty()) app.strokeRepository.create(moved)
        // The target is shown from its saved preview and the memory cache: drop both so it is drawn from the strokes.
        File(filesDir, "pages/previews/full/$targetId").delete()
        File(filesDir, "pages/previews/thumbs/$targetId").delete()
        PageDataManager.removePage(targetId)
    }

    /** Leaving the week page: its seven rows go to the day pages that already exist. */
    suspend fun weekToDays(app: AppRepository, filesDir: File, weekPageId: String, monday: LocalDate, pageHeight: Float, dayPages: Map<LocalDate, String>) {
        val layout = PlannerLayout.week(pageHeight)
        for (i in 0..6) {
            val day = monday.plusDays(i.toLong())
            val dayPage = dayPages[day] ?: continue
            copyRegion(app, filesDir, weekPageId, layout.rowTop(i), dayPage, PlannerLayout.ROWS_TOP, layout.rowH)
        }
    }

    /** Leaving a day page: its first lines go back to the day's row on the week page. */
    suspend fun dayToWeek(app: AppRepository, filesDir: File, dayPageId: String, day: LocalDate, weekPageId: String, pageHeight: Float) {
        val layout = PlannerLayout.week(pageHeight)
        val i = day.dayOfWeek.value - 1
        copyRegion(app, filesDir, dayPageId, PlannerLayout.ROWS_TOP, weekPageId, layout.rowTop(i), layout.rowH)
    }
}
