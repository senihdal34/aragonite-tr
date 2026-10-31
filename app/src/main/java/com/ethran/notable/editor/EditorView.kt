package com.ethran.notable.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import com.ethran.notable.data.AppRepository
import com.ethran.notable.data.datastore.EditorSettingCacheManager
import com.ethran.notable.editor.state.EditorState
import com.ethran.notable.editor.state.History
import com.ethran.notable.editor.ui.EditorSidebar
import com.ethran.notable.editor.ui.EditorSurface
import com.ethran.notable.editor.ui.SIDEBAR_WIDTH
import com.ethran.notable.editor.ui.HorizontalScrollIndicator
import com.ethran.notable.editor.ui.InboxToolbar
import com.ethran.notable.editor.ui.PlannerWeekBar
import com.ethran.notable.editor.ui.PlannerDayBar
import com.ethran.notable.editor.ui.PlannerBackPill
import com.ethran.notable.planner.PlannerSync
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp
import com.ethran.notable.editor.canvas.CanvasEventBus
import androidx.compose.ui.Alignment
import com.ethran.notable.planner.PlannerService
import com.ethran.notable.planner.PlannerWeeks
import java.time.LocalDate
import com.ethran.notable.editor.ui.ScrollIndicator
import com.ethran.notable.editor.ui.SelectedBitmap
import com.ethran.notable.gestures.EditorGestureReceiver
import com.ethran.notable.io.ExportEngine
import com.ethran.notable.io.SyncState
import com.ethran.notable.io.VaultTagScanner
import com.ethran.notable.io.exportToLinkedFile
import com.ethran.notable.navigation.NavigationDestination
import com.ethran.notable.ui.LocalSnackContext
import com.ethran.notable.ui.SnackConf
import com.ethran.notable.ui.SnackState
import com.ethran.notable.ui.convertDpToPixel
import com.ethran.notable.ui.theme.InkaTheme
import com.ethran.notable.ui.views.LibraryDestination
import io.shipbook.shipbooksdk.ShipBook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val log = ShipBook.getLogger("EditorView")

object EditorDestination : NavigationDestination {
    override val route = "editor"

    const val PAGE_ID_ARG = "pageId"
    const val BOOK_ID_ARG = "bookId"

    // Unified route: editor/{pageId}?bookId={bookId}
    val routeWithArgs = "$route/{$PAGE_ID_ARG}?$BOOK_ID_ARG={$BOOK_ID_ARG}"

    /**
     * Helper to create the path. If bookId is null, it just won't be appended.
     */
    fun createRoute(pageId: String, bookId: String? = null): String {
        return "$route/$pageId" + if (bookId != null) "?$BOOK_ID_ARG=$bookId" else ""
    }
}


@Composable
fun EditorView(
    editorSettingCacheManager: EditorSettingCacheManager,
    exportEngine: ExportEngine,
    navController: NavController,
    appRepository: AppRepository,
    bookId: String?,
    pageId: String,
    onPageChange: (String) -> Unit
) {
    val context = LocalContext.current
    val snackManager = LocalSnackContext.current
    val scope = rememberCoroutineScope()

    var pageExists by remember(pageId) { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(pageId) {
        val exists = withContext(Dispatchers.IO) {
            appRepository.pageRepository.getById(pageId) != null
        }
        pageExists = exists

        if (!exists) {
            // TODO: check if it is correct, and remove exeption throwing
            throw Exception("Page does not exist")
            if (bookId != null) {
                // clean the book
                log.i("Could not find page, Cleaning book")
                SnackState.globalSnackFlow.tryEmit(
                    SnackConf(
                        text = "Could not find page, cleaning book",
                        duration = 4000
                    )
                )
                scope.launch(Dispatchers.IO) {
                    appRepository.bookRepository.removePage(bookId, pageId)
                }
            }
            navController.navigate(LibraryDestination.route)
        }
    }


    if (pageExists == null) return

    BoxWithConstraints {
        val height = convertDpToPixel(this.maxHeight, context).toInt()
        val width = convertDpToPixel(this.maxWidth, context).toInt()


        val page = remember {
            PageView(
                context = context,
                coroutineScope = scope,
                appRepository = appRepository,
                currentPageId = pageId,
                viewWidth = width,
                viewHeight = height,
                snackManager = snackManager,
            )
        }

        val editorState =
            remember {
                EditorState(
                    appRepository = appRepository,
                    bookId = bookId,
                    pageId = pageId,
                    pageView = page,
                    persistedEditorSettings = editorSettingCacheManager.getEditorSettings(),
                    onPageChange = onPageChange
                )
            }

        val history = remember {
            History(page)
        }
        val editorControlTower = remember {
            EditorControlTower(scope, page, history, editorState).apply { registerObservers() }
        }


        // Inbox mode detection — query DB since pageFromDb loads async
        var isInboxPage by remember { mutableStateOf(false) }
        var plannerMonday by remember(pageId) { mutableStateOf<LocalDate?>(null) }
        var plannerDay by remember(pageId) { mutableStateOf<LocalDate?>(null) }
        val selectedTags = remember { mutableStateListOf<String>() }
        // Read tags reactively — updates when VaultTagScanner.refreshCache() runs
        val suggestedTags = VaultTagScanner.cachedTags

        LaunchedEffect(pageId) {
            val pageData = withContext(Dispatchers.IO) {
                appRepository.pageRepository.getById(pageId)
            }
            val inbox = pageData?.background == "inbox"
            isInboxPage = inbox
            editorState.isInboxPage = inbox
            plannerMonday = pageData?.background?.let(PlannerWeeks::parseWeek)
            plannerDay = pageData?.background?.let(PlannerWeeks::parseDay)
        }

        // Week tool of the planner page: move to another week's page inside this notebook.
        val plannerService = remember { PlannerService(appRepository) }

        // Keeps a day's first lines equal on the week page and the day page: the page we leave updates its counterpart.
        suspend fun syncPlannerPage(fromPageId: String, monday: LocalDate?, day: LocalDate?) = withContext(Dispatchers.IO) {
            if (monday == null && day == null) return@withContext
            runCatching {
                kotlinx.coroutines.delay(300)   // strokes just drawn are saved a moment after the pen lifts
                val h = height.toFloat()
                val book = bookId?.let { appRepository.bookRepository.getById(it) } ?: return@runCatching
                val pages = book.pageIds.chunked(400).flatMap { appRepository.pageRepository.getByIds(it) }
                if (monday != null) {
                    val dayPages = pages.mapNotNull { p -> PlannerWeeks.parseDay(p.background)?.let { it to p.id } }
                        .filter { PlannerWeeks.mondayOf(it.first) == monday }.toMap()
                    PlannerSync.weekToDays(appRepository, context.filesDir, fromPageId, monday, h, dayPages)
                } else if (day != null) {
                    val week = pages.firstOrNull { PlannerWeeks.parseWeek(it.background) == PlannerWeeks.mondayOf(day) } ?: return@runCatching
                    PlannerSync.dayToWeek(appRepository, context.filesDir, fromPageId, day, week.id, h)
                }
            }
        }
        DisposableEffect(pageId, plannerMonday, plannerDay) {
            val pid = pageId; val m = plannerMonday; val d = plannerDay
            onDispose { kotlinx.coroutines.GlobalScope.launch { syncPlannerPage(pid, m, d) } }
        }
        // Ticked lines are drawn struck through; load them for the page and redraw once they are known.
        LaunchedEffect(plannerMonday, plannerDay) {
            val dates = plannerMonday?.let { m -> (0L..6L).map { m.plusDays(it) } } ?: plannerDay?.let { listOf(it) } ?: return@LaunchedEffect
            withContext(Dispatchers.IO) { runCatching { appRepository.plannerDone.load(dates) } }
            if (dates.any { com.ethran.notable.planner.PlannerDone.lines(it).isNotEmpty() }) {
                page.applyZoomAndRedraw(page.zoomLevel.value)
                CanvasEventBus.refreshUiImmediately.emit(Unit)
            }
        }
        fun toggleDone(date: LocalDate, line: Int) {
            scope.launch {
                withContext(Dispatchers.IO) { runCatching { appRepository.plannerDone.toggle(date, line) } }
                page.applyZoomAndRedraw(page.zoomLevel.value)
                CanvasEventBus.refreshUiImmediately.emit(Unit)
            }
        }
        fun goToDay(day: LocalDate) {
            scope.launch {
                // Create the day page first, so a page opened for the first time also gets the week row's ink.
                val target = withContext(Dispatchers.IO) { runCatching { plannerService.ensureDay(day) }.getOrNull() }
                syncPlannerPage(pageId, plannerMonday, plannerDay)
                if (target != null && target.pageId != pageId) CanvasEventBus.changePage.tryEmit(target.pageId)
            }
        }
        fun goToWeek(monday: LocalDate) {
            scope.launch {
                syncPlannerPage(pageId, plannerMonday, plannerDay)
                val target = withContext(Dispatchers.IO) { runCatching { plannerService.ensureWeek(monday) }.getOrNull() }
                if (target != null && target.pageId != pageId) CanvasEventBus.changePage.tryEmit(target.pageId)
            }
        }

        DisposableEffect(Unit) {
            onDispose {
                // finish selection operation
                editorState.selectionState.applySelectionDisplace(page)
                if (bookId != null)
                    exportToLinkedFile(exportEngine, bookId, appRepository.bookRepository)
                page.disposeOldPage()
            }
        }

        // TODO put in editorSetting class
        LaunchedEffect(
            editorState.isToolbarOpen,
            editorState.pen,
            editorState.penSettings,
            editorState.mode,
            editorState.isToolbarOpen,
            editorState.eraser
        ) {
            log.i("EditorView: saving")
            editorSettingCacheManager.setEditorSettings(
                EditorSettingCacheManager.EditorSettings(
                    isToolbarOpen = editorState.isToolbarOpen,
                    mode = editorState.mode,
                    pen = editorState.pen,
                    eraser = editorState.eraser,
                    penSettings = editorState.penSettings
                )
            )
        }



        InkaTheme {
            Row(modifier = Modifier.fillMaxSize()) {
                // Left-edge sidebar — physically outside the canvas SurfaceView
                // so finger taps always work even when Onyx SDK raw drawing is active
                EditorSidebar(exportEngine, navController, appRepository, editorState, editorControlTower)
                // Canvas area takes remaining space
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    EditorGestureReceiver(controlTower = editorControlTower)
                    EditorSurface(
                        appRepository = appRepository,
                        state = editorState,
                        page = page,
                        history = history
                    )
                    SelectedBitmap(
                        context = context,
                        controlTower = editorControlTower
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight()
                    ) {
                        Spacer(modifier = Modifier.weight(1f))
                        ScrollIndicator(state = editorState)
                    }
                    if (isInboxPage) {
                        InboxToolbar(
                            selectedTags = selectedTags,
                            suggestedTags = suggestedTags,
                            isExpanded = editorState.isInboxTagsExpanded,
                            isToolbarOpen = editorState.isToolbarOpen,
                            onToggleExpanded = {
                                editorState.isInboxTagsExpanded = !editorState.isInboxTagsExpanded
                            },
                            onToggleToolbar = {
                                editorState.isToolbarOpen = !editorState.isToolbarOpen
                            },
                            onTagAdd = { tag ->
                                if (tag !in selectedTags) selectedTags.add(tag)
                            },
                            onTagRemove = { tag -> selectedTags.remove(tag) },
                            onSave = {
                                SyncState.launchSync(
                                    appRepository, pageId, selectedTags.toList(), context, exportEngine
                                )
                                navController.popBackStack()
                            },
                            onDiscard = { navController.popBackStack() }
                        )
                    }
                    if (plannerMonday != null || plannerDay != null) {
                        fun toHome(section: com.ethran.notable.ui.viewmodels.HomeSection) {
                            com.ethran.notable.ui.viewmodels.HomeSectionMemory.last = section
                            navController.navigate(com.ethran.notable.ui.views.HomeDestination.route) {
                                popUpTo(com.ethran.notable.ui.views.HomeDestination.route) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
                            val density = androidx.compose.ui.platform.LocalDensity.current
                            with(density) {
                                Box(
                                    Modifier.offset(0.dp, com.ethran.notable.planner.PlannerLayout.HEADER_H.toDp())
                                        .fillMaxWidth().height(com.ethran.notable.planner.PlannerLayout.NAV_H.toDp()),
                                    contentAlignment = Alignment.Center
                                ) {
                                    com.ethran.notable.ui.components.MainTabs(
                                        active = com.ethran.notable.ui.components.MainTab.Week,
                                        onWeek = { goToWeek(PlannerWeeks.mondayOf(plannerDay ?: LocalDate.now())) },
                                        onNotes = { toHome(com.ethran.notable.ui.viewmodels.HomeSection.Notes) },
                                        onPinned = { toHome(com.ethran.notable.ui.viewmodels.HomeSection.Pinned) },
                                        onReading = { toHome(com.ethran.notable.ui.viewmodels.HomeSection.Reading) }
                                    )
                                }
                            }
                        }
                    }
                    plannerMonday?.let { monday ->
                        // The date number of each row is a tap zone: it opens that day's page.
                        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
                            val density = androidx.compose.ui.platform.LocalDensity.current
                            val heightPx = constraints.maxHeight.toFloat()
                            val layout = com.ethran.notable.planner.PlannerLayout.week(heightPx)
                            with(density) {
                                // Check boxes: a tap ticks the line (struck through, shared with that day's page).
                                for (i in 0 until 7) for (k in 1..layout.lines) {
                                    val zone = com.ethran.notable.planner.PlannerLayout.boxZone(layout.rowTop(i), k, layout.step)
                                    Box(
                                        Modifier
                                            .offset(zone.x.toDp(), zone.y.toDp())
                                            .size(zone.w.toDp(), zone.h.toDp())
                                            .clickable(
                                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                                indication = null
                                            ) { toggleDone(monday.plusDays(i.toLong()), k) }
                                    )
                                }
                                for (i in 0 until 7) {
                                    Box(
                                        Modifier
                                            .offset(20f.toDp(), com.ethran.notable.planner.PlannerLayout.numberTop(layout, i).toDp())
                                            .size((com.ethran.notable.planner.PlannerLayout.LABEL_W - 40f).toDp(), com.ethran.notable.planner.PlannerLayout.numberHeight(layout).toDp())
                                            .clickable(
                                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                                indication = null
                                            ) { goToDay(monday.plusDays(i.toLong())) }
                                    )
                                }
                            }
                        }
                        PlannerWeekBar(
                            isCurrentWeek = monday == PlannerWeeks.mondayOf(LocalDate.now()),
                            onPrevious = { goToWeek(monday.minusWeeks(1)) },
                            onThisWeek = { goToWeek(PlannerWeeks.mondayOf(LocalDate.now())) },
                            onNext = { goToWeek(monday.plusWeeks(1)) },
                            modifier = Modifier.align(Alignment.TopCenter)
                        )
                    }
                    plannerDay?.let { day ->
                        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
                            val density = androidx.compose.ui.platform.LocalDensity.current
                            val heightPx = constraints.maxHeight.toFloat()
                            val layout = com.ethran.notable.planner.PlannerLayout.week(heightPx)
                            val lines = com.ethran.notable.planner.PlannerLayout.dayLines(heightPx)
                            with(density) {
                                for (k in 1..lines) {
                                    val zone = com.ethran.notable.planner.PlannerLayout.boxZone(com.ethran.notable.planner.PlannerLayout.ROWS_TOP, k, layout.step)
                                    Box(
                                        Modifier
                                            .offset(zone.x.toDp(), zone.y.toDp())
                                            .size(zone.w.toDp(), zone.h.toDp())
                                            .clickable(
                                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                                indication = null
                                            ) { toggleDone(day, k) }
                                    )
                                }
                            }
                        }
                        PlannerBackPill(onClick = { goToWeek(PlannerWeeks.mondayOf(day)) }, modifier = Modifier.align(Alignment.TopStart))
                        PlannerDayBar(
                            onWeek = { goToWeek(PlannerWeeks.mondayOf(day)) },
                            onPrevious = { goToDay(day.minusDays(1)) },
                            onNext = { goToDay(day.plusDays(1)) },
                            modifier = Modifier.align(Alignment.TopCenter)
                        )
                    }
                    HorizontalScrollIndicator(state = editorState)
                }
            }
        }
    }
}


