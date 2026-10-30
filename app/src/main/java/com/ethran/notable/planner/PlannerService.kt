package com.ethran.notable.planner

import com.ethran.notable.data.AppRepository
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The first planner version kept handwritten week and to-do pages in a "Planlayıcı" notebook. The Week screen
 * on Home replaced them. This removes those pages when nothing was written on them (and the notebook when it
 * ends up empty); a page that has ink or images stays, so nothing the user drew is ever deleted.
 */
@Singleton
class PlannerService @Inject constructor(private val appRepository: AppRepository) {

    suspend fun removeEmptyLegacyPages() = withContext(NonCancellable) {
        val books = appRepository.bookRepository
        val pages = appRepository.pageRepository
        val book = books.findByTitle(PlannerWeeks.NOTEBOOK_TITLE) ?: return@withContext
        val keep = mutableListOf<String>()
        for (p in book.pageIds.chunked(400).flatMap { pages.getByIds(it) }) {
            val legacy = PlannerWeeks.parseLegacyWeek(p.background) != null || PlannerWeeks.parseTodo(p.background) != null
            val empty = legacy && pages.getWithStrokeById(p.id).strokes.isEmpty() && pages.getWithImageById(p.id).images.isEmpty()
            if (empty) pages.delete(p.id) else keep.add(p.id)
        }
        if (keep.isEmpty()) books.delete(book.id) else if (keep.size != book.pageIds.size) books.update(book.copy(pageIds = keep))
    }
}
