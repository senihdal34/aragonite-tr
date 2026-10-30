package com.ethran.notable.planner

import com.ethran.notable.data.AppRepository
import com.ethran.notable.data.db.Notebook
import com.ethran.notable.data.db.Page
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/** True once the planner has been opened automatically in this app process (start-up only). */
object PlannerLaunch {
    @Volatile var done = false
}

/**
 * Keeps the "Planlayıcı" notebook up to date: one handwritten page per week. They are ordinary pages, so the
 * editor's pen, eraser and page swipe work on them; the week tool in the editor moves to the previous or next week.
 */
@Singleton
class PlannerService @Inject constructor(private val appRepository: AppRepository) {
    data class Target(val bookId: String, val pageId: String)

    /** Opens the page of this week (and makes sure the next weeks exist). */
    suspend fun openCurrentWeek(today: LocalDate = LocalDate.now()): Target {
        val weeks = PlannerWeeks.weeksToEnsure(today)
        return ensureWeeks(weeks).getValue(weeks.first())
    }

    /** The page of the week starting on [monday], created when missing. */
    suspend fun ensureWeek(monday: LocalDate): Target = ensureWeeks(listOf(PlannerWeeks.mondayOf(monday))).getValue(PlannerWeeks.mondayOf(monday))

    private suspend fun ensureWeeks(mondays: List<LocalDate>): Map<LocalDate, Target> = withContext(NonCancellable) {
        mutex.withLock {
            val books = appRepository.bookRepository
            val pages = appRepository.pageRepository

            var book = books.findByTitle(PlannerWeeks.NOTEBOOK_TITLE)
            if (book == null) {
                book = Notebook(title = PlannerWeeks.NOTEBOOK_TITLE, defaultBackground = "blank")
                books.createEmpty(book)
            }
            val existing = book.pageIds.chunked(400).flatMap { pages.getByIds(it) }.toMutableList()
            val ids = book.pageIds.toMutableList()
            var changed = false

            // To-do pages of the first version: remove the ones nothing was written on.
            for (p in existing.toList()) {
                if (PlannerWeeks.parseTodo(p.background) == null) continue
                if (pages.getWithStrokeById(p.id).strokes.isEmpty() && pages.getWithImageById(p.id).images.isEmpty()) {
                    pages.delete(p.id); ids.remove(p.id); existing.remove(p); changed = true
                }
            }

            val byBackground = existing.associateBy { it.background }.toMutableMap()
            val result = mutableMapOf<LocalDate, Target>()
            for (monday in mondays) {
                val bg = PlannerWeeks.weekBackground(monday)
                val page = byBackground[bg] ?: Page(
                    notebookId = book.id, background = bg, backgroundType = "native",
                    createdAt = Date.from(monday.atStartOfDay(ZoneId.systemDefault()).toInstant()),
                    title = "Hafta ${PlannerWeeks.weekOfYear(monday)}"
                ).also { pages.create(it); ids.add(it.id); byBackground[bg] = it; changed = true }
                result[monday] = Target(book.id, page.id)
            }
            if (changed) {
                val background = byBackground.values.associate { it.id to it.background }
                val sorted = ids.sortedBy { id -> background[id]?.let(PlannerWeeks::sortKey) ?: Long.MAX_VALUE }
                books.update(book.copy(pageIds = sorted))
            }
            result
        }
    }

    private companion object {
        // One lock for every instance: the editor's week tool and Home both create pages.
        val mutex = Mutex()
    }
}
