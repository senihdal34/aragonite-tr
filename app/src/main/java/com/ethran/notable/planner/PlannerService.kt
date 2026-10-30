package com.ethran.notable.planner

import com.ethran.notable.data.AppRepository
import com.ethran.notable.data.db.Notebook
import com.ethran.notable.data.db.Page
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
 * Keeps the "Planlayıcı" notebook up to date: for this week and the next [PlannerWeeks.WEEKS_AHEAD]
 * weeks there is a planner page and a to-do page. They are ordinary pages, so the editor's pen, eraser
 * and page swipe work on them unchanged.
 */
@Singleton
class PlannerService @Inject constructor(private val appRepository: AppRepository) {
    data class Target(val bookId: String, val pageId: String)

    private val mutex = Mutex()

    suspend fun openCurrentWeek(today: LocalDate = LocalDate.now()): Target = mutex.withLock {
        val books = appRepository.bookRepository
        val pages = appRepository.pageRepository

        var book = books.findByTitle(PlannerWeeks.NOTEBOOK_TITLE)
        if (book == null) {
            book = Notebook(title = PlannerWeeks.NOTEBOOK_TITLE, defaultBackground = "blank")
            books.createEmpty(book)
        }

        val existing = pages.getByIds(book.pageIds)
        val byBackground = existing.associateBy { it.background }
        val ids = book.pageIds.toMutableList()
        var changed = false

        for (monday in PlannerWeeks.weeksToEnsure(today)) {
            val created = Date.from(monday.atStartOfDay(ZoneId.systemDefault()).toInstant())
            for ((bg, title) in listOf(
                PlannerWeeks.weekBackground(monday) to "Hafta ${PlannerWeeks.weekNumber(monday)}",
                PlannerWeeks.todoBackground(monday) to "Yapılacaklar ${PlannerWeeks.weekNumber(monday)}"
            )) {
                if (bg in byBackground) continue
                val page = Page(
                    notebookId = book.id, background = bg, backgroundType = "native",
                    createdAt = created, title = title
                )
                pages.create(page)
                ids.add(page.id)
                changed = true
            }
        }
        if (changed) books.update(book.copy(pageIds = ids))

        val thisWeek = PlannerWeeks.weekBackground(PlannerWeeks.mondayOf(today))
        val currentId = (existing + pages.getByIds(ids)).firstOrNull { it.background == thisWeek }?.id
            ?: ids.first()
        Target(book.id, currentId)
    }
}
