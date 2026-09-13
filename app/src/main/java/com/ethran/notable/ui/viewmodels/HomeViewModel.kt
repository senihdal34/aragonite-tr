package com.ethran.notable.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ethran.notable.data.db.AnnotationRepository
import com.ethran.notable.data.db.Page
import com.ethran.notable.data.db.PageRepository
import com.ethran.notable.data.db.TagPriorityDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class TopicGroup(
    val tag: String,
    val pages: List<Page>
)

data class HomeUiState(
    val pinnedPages: List<Page> = emptyList(),
    val recentPages: List<Page> = emptyList(),
    val topicGroups: List<TopicGroup> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val pageRepository: PageRepository,
    private val annotationRepository: AnnotationRepository,
    private val tagPriorityDao: TagPriorityDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    // Single-shot reads: no observers needed
                    val pinned = try { pageRepository.getPinnedPages().value } catch (e: Exception) { null } ?: emptyList()
                    val recent = try { pageRepository.getRecentPages(10).value } catch (e: Exception) { null } ?: emptyList()
                    val tags = try { annotationRepository.getDistinctTags() } catch (e: Exception) { emptyList() }
                    val sortedTags = try { tagPriorityDao.getAllSorted().associate { it.tagName to it.sortOrder } } catch (e: Exception) { emptyMap() }

                    val groups = tags.map { tag ->
                        val pageIds = try { annotationRepository.getPageIdsByTag(tag) } catch (e: Exception) { emptyList() }
                        val pages = try { pageRepository.getByIds(pageIds) } catch (e: Exception) { emptyList() }
                        TopicGroup(tag = tag, pages = pages)
                    }.sortedBy { sortedTags[it.tag] ?: Int.MAX_VALUE }

                    _uiState.value = HomeUiState(
                        pinnedPages = pinned,
                        recentPages = recent,
                        topicGroups = groups,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.value = HomeUiState(isLoading = false)
            }
        }
    }

    fun togglePin(pageId: String, currentPinned: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            pageRepository.setPinned(pageId, !currentPinned)
            // Reload data after pin change
            val pinned = pageRepository.getPinnedPages().value ?: emptyList()
            val recent = pageRepository.getRecentPages(10).value ?: emptyList()
            _uiState.value = _uiState.value.copy(pinnedPages = pinned, recentPages = recent)
        }
    }

    fun refresh() {
        viewModelScope.launch {
            loadData()
        }
    }
}