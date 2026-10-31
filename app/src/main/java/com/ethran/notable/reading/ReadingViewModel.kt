package com.ethran.notable.reading

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class ReadingUi(
    val data: ReadingData = ReadingData.Empty,
    val loaded: Boolean = false,
    /** False when NeoReader's library could not be read (not installed, or its data changed). */
    val sourceAvailable: Boolean = true
)

@HiltViewModel
class ReadingViewModel @Inject constructor(@ApplicationContext private val context: Context) : ViewModel() {
    private val _ui = MutableStateFlow(ReadingUi())
    val ui: StateFlow<ReadingUi> = _ui.asStateFlow()

    /** Reads NeoReader's data off the main thread; the last data stays on screen while it loads or if reading fails. */
    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            val books = NeoReaderSource.library(context)
            val events = NeoReaderSource.events(context)
            val highlights = NeoReaderSource.highlights(context)
            val coverDir = COVER_DIRS.map(::File).firstOrNull { it.isDirectory }
            if (books.isEmpty() && _ui.value.loaded) return@launch   // keep what we had
            val data = ReadingStats.build(books, events, highlights, System.currentTimeMillis(), coverDir = coverDir)
            _ui.value = ReadingUi(data, loaded = true, sourceAvailable = books.isNotEmpty())
        }
    }

    private companion object {
        /** Covers made earlier; the first folder that exists is used. */
        val COVER_DIRS = listOf("/sdcard/Documents/ReadingCovers", "/sdcard/koreader/localboard-covers")
    }
}
