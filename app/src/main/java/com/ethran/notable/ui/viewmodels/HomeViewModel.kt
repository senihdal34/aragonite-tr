package com.ethran.notable.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ethran.notable.data.db.AnnotationRepository
import com.ethran.notable.data.db.Page
import com.ethran.notable.data.db.PageRepository
import com.ethran.notable.data.db.TagPriority
import com.ethran.notable.data.db.TagPriorityDao
import com.ethran.notable.data.datastore.GlobalAppSettings
import com.ethran.notable.io.ExportedExcerpts
import com.ethran.notable.io.ThumbnailGenerator
import com.ethran.notable.planner.PlannerService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

enum class HomeSection { Notes, Pinned }

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
    /** Page id -> first lines of its exported note (only pages that have been exported). */
    val excerpts: Map<String, String> = emptyMap()
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val pageRepository: PageRepository,
    private val annotationRepository: AnnotationRepository,
    private val tagPriorityDao: TagPriorityDao,
    private val thumbnailGenerator: ThumbnailGenerator,
    private val plannerService: PlannerService
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _section = MutableStateFlow(HomeSection.Notes)
    val section: StateFlow<HomeSection> = _section.asStateFlow()
    fun selectSection(s: HomeSection) { _section.value = s }

    /** Creates the planner pages for this and the coming weeks if needed, then hands back the current week. */
    fun openPlanner(onReady: (bookId: String, pageId: String) -> Unit) {
        viewModelScope.launch {
            val target = try {
                withContext(Dispatchers.IO) { plannerService.openCurrentWeek() }
            } catch (e: Exception) { null }
            if (target != null) onReady(target.bookId, target.pageId)
        }
    }

    /** Topic the Home list is filtered by; null shows the latest notes. */
    private val _selectedTopic = MutableStateFlow<String?>(null)
    val selectedTopic: StateFlow<String?> = _selectedTopic.asStateFlow()

    fun selectTopic(tag: String?) { _selectedTopic.value = tag }

    private val _inputTag = MutableStateFlow("")
    val inputTag: StateFlow<String> = _inputTag.asStateFlow()

    init {
        loadData()
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
                    val allTags = (annotTags + manualTags).map { it.trim() }.filter { it.isNotEmpty() }.distinct()
                    val sortedTags = try { tagPriorityDao.getAllSorted().associate { it.tagName to it.sortOrder } } catch (e: Exception) { emptyMap() }

                    val groups = allTags.map { tag ->
                        val pageIds = try { annotationRepository.getPageIdsByTag(tag) } catch (e: Exception) { emptyList() }
                        val pages = try { pageRepository.getByIds(pageIds) } catch (e: Exception) { emptyList() }
                        TopicGroup(tag = tag, pages = pages)
                    }.sortedBy { sortedTags[it.tag] ?: Int.MAX_VALUE }

                    val allPages = (pinned + recent + groups.flatMap { it.pages }).distinctBy { it.id }
                    val excerpts = try {
                        ExportedExcerpts.load(ExportedExcerpts.inboxDir(GlobalAppSettings.current.obsidianInboxPath), allPages)
                    } catch (e: Exception) { emptyMap() }
                    if (_selectedTopic.value != null && groups.none { it.tag == _selectedTopic.value }) _selectedTopic.value = null
                    _uiState.value = HomeUiState(
                        pinnedPages = pinned,
                        recentPages = recent,
                        topicGroups = groups,
                        isLoading = false,
                        thumbRevision = _uiState.value.thumbRevision,
                        excerpts = excerpts
                    )
                    generateMissingThumbnails((pinned + recent + groups.flatMap { it.pages }).map { it.id }.distinct())
                }
            } catch (e: Exception) {
                _uiState.value = HomeUiState(isLoading = false)
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
                tagPriorityDao.upsert(TagPriority(tagName = tagName, sortOrder = 0))
                _inputTag.value = ""
                loadData()
            } catch (e: Exception) { /* ignore */ }
        }
    }

    fun removeTag(tagName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                tagPriorityDao.delete(tagName)
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
    }
}