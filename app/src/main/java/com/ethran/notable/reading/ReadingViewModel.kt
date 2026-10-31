package com.ethran.notable.reading

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.ethran.notable.reading.badges.BadgeBoard
import com.ethran.notable.reading.badges.BadgeEngine
import com.ethran.notable.reading.badges.BadgeFacts
import com.ethran.notable.reading.badges.PrefsBadgeStore
import java.io.File
import javax.inject.Inject

data class ReadingUi(
    val data: ReadingData = ReadingData.Empty,
    val loaded: Boolean = false,
    /** False when NeoReader's library could not be read (not installed, or its data changed). */
    val sourceAvailable: Boolean = true,
    /** The badges for this reading (null until there is a reading log to work them out from). */
    val badges: BadgeBoard? = null
)

@HiltViewModel
class ReadingViewModel @Inject constructor(@ApplicationContext private val context: Context) : ViewModel() {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private var goal = ReadingGoal.clamp(prefs.getInt(KEY_GOAL, ReadingGoal.DEFAULT))

    private val _ui = MutableStateFlow(ReadingUi(ReadingData.Empty.copy(goalBooks = goal)))
    val ui: StateFlow<ReadingUi> = _ui.asStateFlow()

    /** Reads NeoReader's data off the main thread; the last data stays on screen while it loads or if reading fails. */
    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            val books = NeoReaderSource.library(context)
            val events = NeoReaderSource.events(context)
            val highlights = NeoReaderSource.highlights(context)
            Log.i(TAG, "refresh: books=${books.size} events=${events.size} highlights=${highlights.size}")
            syncCovers(books)
            val coverDir = COVER_DIRS.map(::File).firstOrNull { it.isDirectory }
            if (books.isEmpty() && _ui.value.loaded) return@launch   // keep what we had
            val data = ReadingStats.build(books, events, highlights, System.currentTimeMillis(), goalBooks = goal, coverDir = coverDir)
            // Badges need the reading log as well as the library: without it the store would be filled from half the data.
            val badges = if (books.isNotEmpty() && events.isNotEmpty()) {
                runCatching { BadgeEngine.evaluate(BadgeFacts.from(data, ReadingInsights.today(data)), PrefsBadgeStore(prefs), data.now) }
                    .onFailure { Log.w(TAG, "badges failed: ${it.javaClass.simpleName}: ${it.message}") }.getOrNull()
            } else null
            _ui.value = ReadingUi(data, loaded = true, sourceAvailable = books.isNotEmpty(), badges = badges ?: _ui.value.badges)
        }
    }

    /** Changes the yearly goal: saved right away, and the board shows it without reading everything again. */
    fun setGoal(books: Int) {
        goal = ReadingGoal.clamp(books)
        prefs.edit().putInt(KEY_GOAL, goal).apply()
        _ui.value = _ui.value.let { it.copy(data = it.data.copy(goalBooks = goal)) }
    }

    /**
     * Covers made by the KOReader board are copied once into the board's own folder, so it does not depend on
     * KOReader's. Best effort: a missing folder, a full disk or a refused permission only means fewer covers.
     */
    private fun syncCovers(books: List<NrBook>) {
        try {
            val src = File(SOURCE_COVERS)
            if (!src.isDirectory) return
            val dest = File(OWN_COVERS)
            val names = ReadingCovers.toCopy(books.map { it.path }, dest.list()?.toSet().orEmpty(), src.list()?.toSet().orEmpty())
            val n = ReadingCovers.copy(src, dest, names)
            if (n > 0) Log.i(TAG, "copied $n covers into $OWN_COVERS")
        } catch (e: Exception) {
            Log.w(TAG, "could not copy covers: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private companion object {
        const val TAG = "ReadingViewModel"
        const val PREFS = "reading_board"
        const val KEY_GOAL = "goal_books"
        const val OWN_COVERS = "/sdcard/Documents/ReadingCovers"
        const val SOURCE_COVERS = "/sdcard/koreader/localboard-covers"
        /** The board's own folder first; the KOReader one is the fallback when the copy could not be made. */
        val COVER_DIRS = listOf(OWN_COVERS, SOURCE_COVERS)
    }
}
