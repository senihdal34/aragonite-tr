package com.ethran.notable.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ethran.notable.data.db.AnnotationRepository
import com.ethran.notable.data.db.Page
import com.ethran.notable.data.db.PageRepository
import com.ethran.notable.data.db.PageTagRepository
import com.ethran.notable.data.db.PlannerTodoRepository
import com.ethran.notable.data.db.TodoItem
import com.ethran.notable.io.ExportedExcerpts
import com.ethran.notable.data.db.TagPriority
import com.ethran.notable.data.db.TagPriorityDao
import com.ethran.notable.data.datastore.GlobalAppSettings
import com.ethran.notable.io.ThumbnailGenerator
import com.ethran.notable.io.VaultTagScanner
import com.ethran.notable.planner.PlannerService
import com.ethran.notable.planner.PlannerWeeks
import java.io.File
import java.time.LocalDate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

enum class HomeSection { Week, Notes, Pinned }

/** Remembers the open Home tab while the app process lives (Home is rebuilt when coming back from a note). */
private object HomeSectionMemory { var last: HomeSection? = null }

data class WeekUi(
    val today: LocalDate,
    val monday: LocalDate,
    /** To-do items of each day of the week. */
    val todos: Map<LocalDate, List<TodoItem>> = emptyMap()
) {
    val isCurrentWeek get() = monday == PlannerWeeks.mondayOf(today)
}

data class TopicGroup(
    val tag: String,
    val pages: List<Page>
)

data class HomeUiState(
    val pinnedPages: List<Page> = emptyList(),
    val recentPages: List<Page> = emptyList(),
    val topicGroups: List<TopicGroup> = emptyList(),
    val isLoading: Boolean = true,
    /** Bumped whenever a missing thumbnail has been created, so cards reload their image. */
    val thumbRevision: Int = 0,
    /** Page id -> tags attached from Home (stored apart from the #tag boxes drawn in a note). */
    val pageTags: Map<String, List<String>> = emptyMap(),
    val week: WeekUi = WeekUi(LocalDate.now(), PlannerWeeks.mondayOf(LocalDate.now())),
    /** Page id -> first lines of its exported note (only pages that have been exported). */
    val excerpts: Map<String, String> = emptyMap()
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val pageRepository: PageRepository,
    private val annotationRepository: AnnotationRepository,
    private val tagPriorityDao: TagPriorityDao,
    private val thumbnailGenerator: ThumbnailGenerator,
    private val plannerService: PlannerService,
    private val pageTagRepository: PageTagRepository,
    private val todoRepository: PlannerTodoRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _section = MutableStateFlow(
        HomeSectionMemory.last ?: if (GlobalAppSettings.current.startInPlanner) HomeSection.Week else HomeSection.Notes
    )
    val section: StateFlow<HomeSection> = _section.asStateFlow()
    fun selectSection(s: HomeSection) { HomeSectionMemory.last = s; _section.value = s }

    // ---------- week page ----------

    private var weekJob: kotlinx.coroutines.Job? = null

    /** Moves the Week page by whole weeks; 0 returns to the current week. */
    fun shiftWeek(weeks: Int) {
        val today = LocalDate.now()
        val monday = if (weeks == 0) PlannerWeeks.mondayOf(today) else _uiState.value.week.monday.plusWeeks(weeks.toLong())
        _uiState.update { it.copy(week = WeekUi(today, monday)) }
        loadWeek()
    }

    private fun loadWeek() {
        weekJob?.cancel()
        weekJob = viewModelScope.launch(Dispatchers.IO) {
            val monday = _uiState.value.week.monday
            val todos = try { todoRepository.week(monday).mapValues { PlannerTodoRepository.withSlots(it.value) } } catch (e: Exception) { return@launch }
            _uiState.update { it.copy(week = WeekUi(LocalDate.now(), monday, todos)) }
        }
    }

    // ---------- to-do items of a day (saved automatically) ----------

    private fun editDay(day: LocalDate, change: (List<TodoItem>) -> List<TodoItem>) {
        val next = change(_uiState.value.week.todos[day] ?: PlannerTodoRepository.withSlots(emptyList()))
        _uiState.update { it.copy(week = it.week.copy(todos = it.week.todos + (day to next))) }
        todoRepository.save(day, next)
    }

    fun editTodo(day: LocalDate, id: String, text: String) =
        editDay(day) { list -> list.map { if (it.id == id) it.copy(text = text.replace("\n", " ")) else it } }

    fun toggleTodo(day: LocalDate, id: String) =
        editDay(day) { list -> list.map { if (it.id == id) it.copy(done = !it.done) else it } }

    /** Adds a few more empty lines at the bottom of the day. */
    fun addSlots(day: LocalDate) =
        editDay(day) { list -> list + List(PlannerTodoRepository.SLOTS_PER_ADD) { TodoItem() } }

    /** Topic the Home list is filtered by; null shows the latest notes. */
    private val _selectedTopic = MutableStateFlow<String?>(null)
    val selectedTopic: StateFlow<String?> = _selectedTopic.asStateFlow()

    fun selectTopic(tag: String?) { _selectedTopic.value = tag }

    private val _inputTag = MutableStateFlow("")
    val inputTag: StateFlow<String> = _inputTag.asStateFlow()

    init {
        loadData()
        loadWeek()
        viewModelScope.launch(Dispatchers.IO) { try { plannerService.removeEmptyLegacyPages() } catch (e: Exception) { /* keep going */ } }
    }

    fun setInputTag(value: String) {
        _inputTag.value = value
    }

    private fun loadData() {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    // Single-shot reads
                    val pinned = try { pageRepository.getPinnedPagesList() } catch (e: Exception) { emptyList() }
                    val recent = try { pageRepository.getRecentPagesList(10) } catch (e: Exception) { emptyList() }

                    // Combine annotation tags + manually added tags (TagPriority)
                    val annotTags = try { annotationRepository.getDistinctTags() } catch (e: Exception) { emptyList() }
                    val manualTags = try { tagPriorityDao.getAllSorted().map { it.tagName } } catch (e: Exception) { emptyList() }
                    val pageTags = try { pageTagRepository.all() } catch (e: Exception) { emptyMap() }
                    val vaultTags = try { VaultTagScanner.cachedTags } catch (e: Exception) { emptyList() }
                    val hidden = try { pageTagRepository.hidden() } catch (e: Exception) { emptySet() }
                    val allTags = (annotTags + manualTags + pageTags.values.flatten() + vaultTags)
                        .map { it.trim() }.filter { it.isNotEmpty() && it !in hidden }.distinct()
                    val sortedTags = try { tagPriorityDao.getAllSorted().associate { it.tagName to it.sortOrder } } catch (e: Exception) { emptyMap() }

                    val groups = allTags.map { tag ->
                        val pageIds = (try { annotationRepository.getPageIdsByTag(tag) } catch (e: Exception) { emptyList() } +
                            pageTags.filterValues { tag in it }.keys).distinct()
                        val pages = try { pageRepository.getByIds(pageIds) } catch (e: Exception) { emptyList() }
                        TopicGroup(tag = tag, pages = pages)
                    }.sortedBy { sortedTags[it.tag] ?: Int.MAX_VALUE }

                    val allPages = (pinned + recent + groups.flatMap { it.pages }).distinctBy { it.id }
                    val excerpts = try {
                        ExportedExcerpts.load(ExportedExcerpts.inboxDir(GlobalAppSettings.current.obsidianInboxPath), (pinned + recent + groups.flatMap { it.pages }).distinctBy { it.id })
                    } catch (e: Exception) { emptyMap() }
                    if (_selectedTopic.value != null && groups.none { it.tag == _selectedTopic.value }) _selectedTopic.value = null
                    _uiState.update {
                        it.copy(
                            pinnedPages = pinned, recentPages = recent, topicGroups = groups,
                            isLoading = false, pageTags = pageTags, excerpts = excerpts
                        )
                    }
                    generateMissingThumbnails((pinned + recent + groups.flatMap { it.pages }).map { it.id }.distinct())
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    private var thumbJob: kotlinx.coroutines.Job? = null

    private fun generateMissingThumbnails(pageIds: List<String>) {
        thumbJob?.cancel()
        thumbJob = viewModelScope.launch(Dispatchers.IO) {
            for (id in pageIds) {
                if (thumbnailGenerator.ensure(id)) {
                    _uiState.update { it.copy(thumbRevision = it.thumbRevision + 1) }
                }
            }
        }
    }

    fun addTag(tagName: String) {
        if (tagName.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                pageTagRepository.unhide(tagName)
                tagPriorityDao.upsert(TagPriority(tagName = tagName, sortOrder = 0))
                _inputTag.value = ""
                loadData()
            } catch (e: Exception) { /* ignore */ }
        }
    }

    /** Deletes a topic: off every note, out of the topic list. The notes stay. */
    fun removeTag(tagName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                tagPriorityDao.delete(tagName)
                pageTagRepository.deleteTopic(tagName)
                loadData()
            } catch (e: Exception) { /* ignore */ }
        }
    }

    fun togglePin(pageId: String, currentPinned: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            pageRepository.setPinned(pageId, !currentPinned)
            val pinned = pageRepository.getPinnedPagesList()
            val recent = pageRepository.getRecentPagesList(10)
            _uiState.value = _uiState.value.copy(pinnedPages = pinned, recentPages = recent)
        }
    }

    fun refresh() {
        viewModelScope.launch {
            loadData()
        }
        // The date may have changed overnight.
        if (_uiState.value.week.today != LocalDate.now()) shiftWeek(0)
    }

    // ---------- note actions: tags and delete ----------

    /** Adds the tag to the note, or removes it when the note already has it. */
    fun toggleTag(pageId: String, tag: String) {
        val clean = PageTagRepository.normalize(tag)
        if (clean.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            try { pageTagRepository.unhide(clean) } catch (e: Exception) { return@launch }
            val current = try { pageTagRepository.get(pageId) } catch (e: Exception) { return@launch }
            val next = if (clean in current) current - clean else current + clean
            try { pageTagRepository.set(pageId, next) } catch (e: Exception) { return@launch }
            loadData()
        }
    }

    /** Deletes the note with its strokes, tags and cached thumbnail. The exported Markdown file is not touched. */
    fun deletePage(pageId: String, thumbDir: File) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                pageRepository.delete(pageId)
                pageTagRepository.clear(pageId)
                File(thumbDir, pageId).delete()
            } catch (e: Exception) { return@launch }
            loadData()
        }
    }
}