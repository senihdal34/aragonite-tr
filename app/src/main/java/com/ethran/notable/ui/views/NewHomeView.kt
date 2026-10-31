package com.ethran.notable.ui.views

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.ethran.notable.data.datastore.GlobalAppSettings
import com.ethran.notable.data.db.Page
import com.ethran.notable.data.db.PageTagRepository
import com.ethran.notable.planner.PlannerLaunch
import com.ethran.notable.utils.RelativeTime
import compose.icons.FeatherIcons
import compose.icons.feathericons.Tag
import compose.icons.feathericons.Trash
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material.Icon
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import com.ethran.notable.ui.showHint
import com.ethran.notable.ui.viewmodels.HomeSection
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import com.ethran.notable.navigation.NavigationDestination
import com.ethran.notable.ui.components.MainTab
import com.ethran.notable.ui.components.MainTabs
import com.ethran.notable.ui.components.PagePreview
import com.ethran.notable.ui.viewmodels.HomeUiState
import com.ethran.notable.ui.viewmodels.HomeViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object HomeDestination : NavigationDestination {
    override val route = "home"
}

// Newspaper-style Home for a 10.3" e-ink screen: black, white and two greys, no shadows, no animation.
private val Ink = Color(0xFF111111)
private val Grey1 = Color(0xFF555555)
private val Grey2 = Color(0xFF999999)
private val Paper = Color(0xFFF6F6F2)
private val Sans = FontFamily.SansSerif
private val Serif = FontFamily.Serif
private val TR = Locale("tr", "TR")
private val Pill = androidx.compose.foundation.shape.RoundedCornerShape(50)

@Composable
fun HomeView(
    viewModel: HomeViewModel,
    onOpenPage: (String) -> Unit,
    onSettings: () -> Unit,
    onCreateNewPage: () -> Unit = {},
    onOpenPlanner: (bookId: String, pageId: String) -> Unit = { _, _ -> }
) {
    val state by viewModel.uiState.collectAsState()
    val selected by viewModel.selectedTopic.collectAsState()
    val section by viewModel.section.collectAsState()
    val hintScope = rememberCoroutineScope()
    val appContext = LocalContext.current.applicationContext

    // The weekly planner is the start page: open it once per app start.
    LaunchedEffect(Unit) {
        if (GlobalAppSettings.current.startInPlanner && !PlannerLaunch.done) {
            PlannerLaunch.done = true
            viewModel.openPlanner(onOpenPlanner)
        }
    }

    // Refresh data when returning from the editor (on resume)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) { viewModel.refresh(); com.ethran.notable.reading.NeoReaderSource.probe(appContext) }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val shown: List<Page> = if (selected == null) state.recentPages
    else state.topicGroups.firstOrNull { it.tag == selected }?.pages.orEmpty()
    var adding by remember { mutableStateOf(false) }
    val input by viewModel.inputTag.collectAsState()
    // Note card actions: which card has its tag picker open ("select:<id>" or "add:<id>"), which asks to delete.
    var tagsOpen by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf<String?>(null) }
    var pickerInput by remember { mutableStateOf("") }
    var deletingTopic by remember { mutableStateOf<String?>(null) }
    val thumbDir = File(LocalContext.current.filesDir, "pages/previews/thumbs")

    val actions = HomeActions(
        open = onOpenPage,
        settings = onSettings,
        newNote = onCreateNewPage,
        select = viewModel::selectTopic,
        toggleAdd = { adding = !adding },
        setInput = viewModel::setInputTag,
        addTopic = { viewModel.addTag(input); adding = false },
        removeTopic = { tag -> deletingTopic = null; viewModel.removeTag(tag) },
        unpin = { id -> viewModel.togglePin(id, true) },
        section = viewModel::selectSection,
        planner = { viewModel.openPlanner(onOpenPlanner) },
        deletingTopic = deletingTopic,
        askDeleteTopic = { tag -> deletingTopic = tag },
        tagsOpen = tagsOpen,
        openTags = { key -> tagsOpen = if (key == tagsOpen) null else key; pickerInput = "" },
        confirmDelete = confirmDelete,
        askDelete = { id -> confirmDelete = id; tagsOpen = null },
        deletePage = { id -> confirmDelete = null; viewModel.deletePage(id, thumbDir) },
        toggleTag = viewModel::toggleTag,
        pickerInput = pickerInput,
        setPickerInput = { pickerInput = it },
        addPickerTag = { id ->
            val tag = PageTagRepository.normalize(pickerInput)
            if (tag.isNotEmpty() && tag !in state.pageTags[id].orEmpty()) viewModel.toggleTag(id, tag)
            pickerInput = ""; tagsOpen = "select:$id"
        },
        togglePin = { page ->
            val pinned = state.pinnedPages.any { it.id == page.id }
            viewModel.togglePin(page.id, pinned)
            showHint(if (pinned) "Sabitten kaldırıldı" else "Sabitlendi", hintScope, 1200)
        }
    )

    BoxWithConstraints(Modifier.fillMaxSize().background(Paper)) {
        val landscape = maxWidth > maxHeight
        val scroll = rememberScrollState()
        Column(
            Modifier.fillMaxSize().verticalScroll(scroll)
                .padding(horizontal = 56.dp, vertical = 14.dp)
        ) {
            Masthead(state, selected, section, landscape, actions, adding, input)
            if (section == HomeSection.Pinned) {
                PinnedPage(state, actions)
                NewNoteButton(actions, Modifier.align(Alignment.CenterHorizontally).padding(top = 26.dp, bottom = 20.dp))
            } else if (landscape) {
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                    Column(Modifier.weight(1f).padding(end = 36.dp)) {
                        Stories(shown, state, selected, landscape = true, actions = actions)
                    }
                    Rail(state, selected, actions)
                }
            } else {
                Stories(shown, state, selected, landscape = false, actions = actions)
                NewNoteButton(actions, Modifier.align(Alignment.CenterHorizontally).padding(top = 26.dp, bottom = 20.dp))
            }
        }
    }
}

private class HomeActions(
    val open: (String) -> Unit,
    val settings: () -> Unit,
    val newNote: () -> Unit,
    val select: (String?) -> Unit,
    val toggleAdd: () -> Unit,
    val setInput: (String) -> Unit,
    val addTopic: () -> Unit,
    val removeTopic: (String) -> Unit,
    val unpin: (String) -> Unit,
    val section: (HomeSection) -> Unit,
    val planner: () -> Unit,
    val deletingTopic: String?,
    val askDeleteTopic: (String?) -> Unit,
    val tagsOpen: String?,
    val openTags: (String) -> Unit,
    val confirmDelete: String?,
    val askDelete: (String?) -> Unit,
    val deletePage: (String) -> Unit,
    val toggleTag: (pageId: String, tag: String) -> Unit,
    val pickerInput: String,
    val setPickerInput: (String) -> Unit,
    val addPickerTag: (String) -> Unit,
    val togglePin: (Page) -> Unit
)

/** Tap opens the note, a long press pins or unpins it. */
@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.openOrPin(a: HomeActions, page: Page) = combinedClickable(
    interactionSource = MutableInteractionSource(), indication = null,
    onClick = { a.open(page.id) }, onLongClick = { a.togglePin(page) }
)

private fun Modifier.tap(onClick: () -> Unit) = clickable(
    interactionSource = MutableInteractionSource(), indication = null, onClick = onClick
)

@Composable
private fun Caps(text: String, size: Int = 14, color: Color = Grey1, weight: FontWeight = FontWeight.SemiBold, modifier: Modifier = Modifier) =
    Text(
        text = text.uppercase(TR), fontFamily = Sans, fontSize = size.sp, fontWeight = weight,
        letterSpacing = (size * 0.14f).sp, color = color, modifier = modifier
    )

@Composable
private fun Rule(thickness: Dp = 1.5.dp, color: Color = Ink) =
    Box(Modifier.fillMaxWidth().height(thickness).background(color))

// ---------- masthead: dateline, title, double rule, topic sections ----------

@Composable
private fun Masthead(
    state: HomeUiState, selected: String?, section: HomeSection, landscape: Boolean,
    a: HomeActions, adding: Boolean, input: String
) {
    val date = remember { SimpleDateFormat("EEEE, d MMMM yyyy", TR).format(Date()) }
    val total = (state.recentPages + state.pinnedPages).distinctBy { it.id }.size
    // Same header as the Weekly Planner page: logo and small title on the left, a small summary block on the right.
    Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(com.ethran.notable.R.drawable.logo_deusnote),
            contentDescription = "DeusNote", modifier = Modifier.size(52.dp)
        )
        Spacer(Modifier.width(18.dp))
        Text(
            if (section == HomeSection.Pinned) "Favorite Notes" else "Personal Notes", fontFamily = Serif, fontWeight = FontWeight.Bold,
            fontSize = 36.sp, letterSpacing = (-1).sp, color = Ink, maxLines = 1, modifier = Modifier.weight(1f)
        )
        Column(horizontalAlignment = Alignment.End) {
            Caps(date, 12, Grey1, FontWeight.Bold)
            Caps("$total not · ${state.pinnedPages.size} favori", 12, Grey1, FontWeight.Bold, modifier = Modifier.padding(top = 2.dp))
            Caps("Ayarlar", 12, Ink, FontWeight.Bold, Modifier.tap(a.settings).padding(top = 2.dp, start = 12.dp))
        }
    }
    Rule(5.dp)
    MainNav(section, a)
    if (section == HomeSection.Notes) {
        PinnedStrip(state, a)
        SectionNav(state, selected, a)
        if (adding) TopicInput(input, a)
    }
}

/** HAFTA opens the weekly planner page; NOTLAR and SABİT switch the list below. */
@Composable
private fun MainNav(section: HomeSection, a: HomeActions) {
    MainTabs(
        active = if (section == HomeSection.Pinned) MainTab.Pinned else MainTab.Notes,
        onWeek = a.planner, onNotes = { a.section(HomeSection.Notes) }, onPinned = { a.section(HomeSection.Pinned) },
        modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp)
    )
    Rule()
}

/** Pinned notes in one row under the tabs: always visible, one tap to open, hold a card to unpin. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PinnedStrip(state: HomeUiState, a: HomeActions) {
    if (state.pinnedPages.isEmpty()) return
    Row(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically
    ) {
        Caps("Sabit", 12, Grey1, FontWeight.Bold, Modifier.width(52.dp))
        state.pinnedPages.take(4).forEach { p ->
            Row(
                Modifier.weight(1f).combinedClickable(
                    interactionSource = MutableInteractionSource(), indication = null,
                    onClick = { a.open(p.id) }, onLongClick = { a.togglePin(p) }
                ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.width(56.dp).height(75.dp).border(1.5.dp, Ink)) {
                    PagePreview(Modifier.fillMaxSize(), p.id, state.thumbRevision, ContentScale.FillWidth, Alignment.TopCenter, Color.White)
                }
                Spacer(Modifier.width(10.dp))
                Text(p.title.ifBlank { "İsimsiz Not" }, fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 20.sp,
                    lineHeight = 22.sp, color = Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
    Rule()
}

/** The "★ Sabit" page: every pinned note as a row. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PinnedPage(state: HomeUiState, a: HomeActions) {
    if (state.pinnedPages.isEmpty()) {
        Text("Sabit not yok. Bir notu sabitlemek için kartına basılı tut.", fontFamily = Serif, fontStyle = FontStyle.Italic,
            fontSize = 26.sp, color = Grey1, modifier = Modifier.padding(vertical = 40.dp))
        return
    }
    state.pinnedPages.forEach { p ->
        Row(
            Modifier.fillMaxWidth().padding(vertical = 14.dp).combinedClickable(
                interactionSource = MutableInteractionSource(), indication = null,
                onClick = { a.open(p.id) }, onLongClick = { a.togglePin(p) }
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.width(110.dp).height(147.dp).border(2.dp, Ink)) {
                PagePreview(Modifier.fillMaxSize(), p.id, state.thumbRevision, ContentScale.FillWidth, Alignment.TopCenter, Color.White)
            }
            Spacer(Modifier.width(24.dp))
            Column(Modifier.weight(1f)) {
                Text(p.title.ifBlank { "İsimsiz Not" }, fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 38.sp,
                    letterSpacing = (-0.5).sp, color = Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Caps(RelativeTime.format(p.createdAt), 14, Grey1, modifier = Modifier.padding(top = 8.dp))
                state.excerpts[p.id]?.let {
                    Text(it, fontFamily = Serif, fontStyle = FontStyle.Italic, fontSize = 22.sp, lineHeight = 29.sp,
                        color = Ink, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
                }
                Spacer(Modifier.height(12.dp))
                NoteActions(p, state, a)
            }
            Caps("Kaldır", 14, Grey1, modifier = Modifier.tap { a.unpin(p.id) }.padding(14.dp))
        }
        Rule(1.5.dp, Grey2)
    }
}

@Composable
private fun SectionNav(state: HomeUiState, selected: String?, a: HomeActions) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
    ) {
        NavItem("Tümü", selected == null, last = false) { a.select(null) }
        state.topicGroups.forEach { g -> NavItem(g.tag, selected == g.tag, last = false) { a.select(g.tag) } }
        NavItem("+", false, last = true) { a.toggleAdd() }
    }
    if (selected != null) {
        // Deleting the selected topic: a line of its own, so it is easy to find.
        Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Text("#$selected", fontFamily = Serif, fontSize = 22.sp, color = Ink)
            Spacer(Modifier.width(18.dp))
            if (a.deletingTopic == selected) {
                Caps("Konu silinsin mi?", 12, Ink, FontWeight.Bold)
                Spacer(Modifier.width(10.dp))
                ActionChip("Sil", on = true) { a.removeTopic(selected) }
                Spacer(Modifier.width(8.dp))
                ActionChip("Vazgeç") { a.askDeleteTopic(null) }
            } else {
                ActionChip("Konuyu sil") { a.askDeleteTopic(selected) }
            }
        }
    }
    Rule()
}

@Composable
private fun NavItem(label: String, on: Boolean, last: Boolean, big: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.height(if (big) 54.dp else 44.dp).tap(onClick)
            .background(if (on) Ink else Color.Transparent)
            .padding(horizontal = if (big) 34.dp else 22.dp),
        contentAlignment = Alignment.Center
    ) { Caps(label, if (big) 20 else 17, if (on) Color.White else Ink, FontWeight.SemiBold) }
    if (!last && !on) Box(Modifier.width(1.5.dp).height(26.dp).background(Grey2))
}

@Composable
private fun TopicInput(value: String, a: HomeActions) {
    Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        BasicTextField(
            value = value, onValueChange = a.setInput, singleLine = true,
            textStyle = TextStyle(fontFamily = Serif, fontSize = 24.sp, color = Ink),
            cursorBrush = SolidColor(Ink),
            modifier = Modifier.weight(1f).border(2.dp, Ink).padding(14.dp, 12.dp),
            decorationBox = { inner ->
                if (value.isEmpty()) Text("yeni konu…", fontFamily = Serif, fontStyle = FontStyle.Italic, fontSize = 24.sp, color = Grey1)
                inner()
            }
        )
        Spacer(Modifier.width(14.dp))
        Box(Modifier.background(Ink).tap(a.addTopic).padding(horizontal = 26.dp, vertical = 15.dp)) {
            Caps("Ekle", 16, Color.White, FontWeight.Bold)
        }
    }
}

// ---------- lead story and columns ----------

@Composable
private fun Stories(
    pages: List<Page>, state: HomeUiState, selected: String?, landscape: Boolean, actions: HomeActions
) {
    if (pages.isEmpty()) {
        Text(
            if (selected == null) "Henüz not yok. Aşağıdaki düğmeyle ilk notunu yaz."
            else "Bu konuda not yok. Notun içine #$selected yazınca burada görünür.",
            fontFamily = Serif, fontStyle = FontStyle.Italic, fontSize = 26.sp, color = Grey1,
            modifier = Modifier.padding(vertical = 40.dp)
        )
        return
    }
    Lead(pages.first(), state, selected, landscape, actions)
    val rest = pages.drop(1).take(if (landscape) 2 else 3)
    if (rest.isNotEmpty()) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            rest.forEachIndexed { i, p ->
                if (i > 0) {
                    Spacer(Modifier.width(22.dp))
                    Box(Modifier.width(1.5.dp).fillMaxHeight().background(Grey2))
                    Spacer(Modifier.width(22.dp))
                }
                Column(Modifier.weight(1f).padding(top = 20.dp, bottom = 22.dp)) { Story(p, state, actions, landscape) }
            }
        }
        Rule()
    }
}

@Composable
private fun Lead(page: Page, state: HomeUiState, selected: String?, landscape: Boolean, a: HomeActions) {
    val thumbW = if (landscape) 300.dp else 350.dp
    val thumbH = if (landscape) 380.dp else 470.dp
    Row(Modifier.fillMaxWidth().padding(vertical = if (landscape) 20.dp else 26.dp)) {
        Box(Modifier.width(thumbW).height(thumbH).border(2.dp, Ink).openOrPin(a, page)) {
            PagePreview(Modifier.fillMaxSize(), page.id, state.thumbRevision, ContentScale.FillWidth, Alignment.TopCenter, Color.White)
        }
        Spacer(Modifier.width(if (landscape) 28.dp else 34.dp))
        Column(Modifier.weight(1f).openOrPin(a, page)) {
            Caps(if (selected == null) "Son not · ${RelativeTime.format(page.createdAt)}" else "#$selected · ${RelativeTime.format(page.createdAt)}", 14, Grey1, FontWeight.Bold)
            Text(
                page.title.ifBlank { "İsimsiz Not" }, fontFamily = Serif, fontWeight = FontWeight.Bold,
                fontSize = if (landscape) 56.sp else 64.sp, lineHeight = if (landscape) 58.sp else 66.sp,
                letterSpacing = (-1.5).sp, color = Ink, maxLines = 3, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 14.dp, bottom = 14.dp)
            )
            val excerpt = state.excerpts[page.id]
            if (!excerpt.isNullOrBlank()) {
                Text(excerpt, fontFamily = Serif, fontStyle = FontStyle.Italic, fontSize = 26.sp, lineHeight = 35.sp,
                    color = Ink, maxLines = if (landscape) 6 else 7, overflow = TextOverflow.Ellipsis)
            }
            Text("1 sayfa", fontFamily = Sans, fontSize = 16.sp, color = Grey1, modifier = Modifier.padding(top = 18.dp, bottom = 14.dp))
            NoteActions(page, state, a)
        }
    }
    Rule()
}

@Composable
private fun Story(page: Page, state: HomeUiState, a: HomeActions, landscape: Boolean) {
    if (landscape) {
        // Wide card: small page on the left, headline and first lines on the right.
        Row(Modifier.fillMaxWidth().openOrPin(a, page)) {
            Box(Modifier.width(128.dp).height(171.dp).border(1.5.dp, Ink)) {
                PagePreview(Modifier.fillMaxSize(), page.id, state.thumbRevision, ContentScale.FillWidth, Alignment.TopCenter, Color.White)
            }
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    page.title.ifBlank { "İsimsiz Not" }, fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 30.sp,
                    lineHeight = 33.sp, letterSpacing = (-0.5).sp, color = Ink, maxLines = 2, overflow = TextOverflow.Ellipsis
                )
                Caps(RelativeTime.format(page.createdAt), 13, Grey1, modifier = Modifier.padding(top = 6.dp, bottom = 8.dp))
                state.excerpts[page.id]?.let {
                    Text(it, fontFamily = Serif, fontStyle = FontStyle.Italic, fontSize = 20.sp, lineHeight = 27.sp,
                        color = Ink, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(12.dp))
                NoteActions(page, state, a)
            }
        }
        return
    }
    Column(Modifier) {
      Column(Modifier.openOrPin(a, page)) {
        Box(Modifier.fillMaxWidth().height(200.dp).border(1.5.dp, Ink)) {
            PagePreview(Modifier.fillMaxSize(), page.id, state.thumbRevision, ContentScale.FillWidth, Alignment.TopCenter, Color.White)
        }
        Text(
            page.title.ifBlank { "İsimsiz Not" }, fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 30.sp,
            lineHeight = 33.sp, letterSpacing = (-0.5).sp, color = Ink, maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 12.dp, bottom = 6.dp)
        )
        Caps(RelativeTime.format(page.createdAt), 13, Grey1, modifier = Modifier.padding(bottom = 10.dp))
      }
      NoteActions(page, state, a)
    }
}

// ---------- pinned notes, topic index, new note ----------

@Composable
private fun Rail(state: HomeUiState, selected: String?, a: HomeActions) {
    Row(Modifier.width(350.dp).fillMaxHeight()) {
        Box(Modifier.width(1.5.dp).fillMaxHeight().background(Ink))
        Column(Modifier.padding(start = 28.dp)) {
            Caps("Konular", 14, Grey1, FontWeight.Bold, Modifier.padding(top = 16.dp, bottom = 8.dp))
            Rule()
            if (state.topicGroups.isEmpty()) {
                Text("Henüz konu yok", fontFamily = Serif, fontStyle = FontStyle.Italic, fontSize = 22.sp, color = Grey1,
                    modifier = Modifier.padding(vertical = 12.dp))
            }
            state.topicGroups.forEach { g ->
                Row(
                    Modifier.fillMaxWidth().tap { a.select(if (selected == g.tag) null else g.tag) }
                        .background(if (selected == g.tag) Color(0xFFE3E3E0) else Color.Transparent)
                        .padding(vertical = 12.dp, horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("#${g.tag}", fontFamily = Serif, fontSize = 28.sp, color = Ink)
                    Caps("${g.pages.size}", 17, Grey1)
                }
                Rule(1.dp, Grey2)
            }
            NewNoteButton(a, Modifier.padding(top = 34.dp, bottom = 20.dp).align(Alignment.CenterHorizontally))
        }
    }
}

@Composable
private fun NewNoteButton(a: HomeActions, modifier: Modifier = Modifier) {
    Box(modifier.border(BorderStroke(2.5.dp, Ink)).tap(a.newNote).padding(horizontal = 36.dp, vertical = 16.dp)) {
        Caps("+ Yeni not yaz", 18, Ink, FontWeight.Bold)
    }
}

// ---------- note card actions: delete, pick a tag, add a tag ----------

@Composable
private fun IconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, on: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.size(34.dp).clip(Pill).border(1.5.dp, Ink, Pill).background(if (on) Ink else Color.Transparent).tap(onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, contentDescription = label, tint = if (on) Color.White else Ink, modifier = Modifier.size(18.dp)) }
}

@Composable
private fun ActionChip(label: String, on: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.height(34.dp).clip(Pill).border(1.5.dp, Ink, Pill).background(if (on) Ink else Color.Transparent).tap(onClick).padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) { Caps(label, 11, if (on) Color.White else Ink, FontWeight.Bold) }
}

/** Under a note: trash, "select tag", "add tag", the tags it has, and the tag picker or delete confirmation. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NoteActions(page: Page, state: HomeUiState, a: HomeActions) {
    val tags = state.pageTags[page.id].orEmpty()
    val selectOpen = a.tagsOpen == "select:${page.id}"
    val addOpen = a.tagsOpen == "add:${page.id}"
    Column(Modifier.fillMaxWidth()) {
        if (a.confirmDelete == page.id) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.height(34.dp), contentAlignment = Alignment.CenterStart) { Caps("Not silinsin mi?", 14, Ink, FontWeight.Bold) }
                ActionChip("Sil", on = true) { a.deletePage(page.id) }
                ActionChip("Vazgeç") { a.askDelete(null) }
            }
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                IconButton(FeatherIcons.Trash, "Notu sil") { a.askDelete(page.id) }
                ActionChip("# Seç", on = selectOpen) { a.openTags("select:${page.id}") }
                ActionChip("+ Etiket", on = addOpen) { a.openTags("add:${page.id}") }
            }
        }
        if (tags.isNotEmpty()) {
            FlowRow(
                Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) { tags.forEach { Text("#$it", fontFamily = Serif, fontSize = 20.sp, color = Ink) } }
        }
        if (selectOpen) TagSelect(page, state, a)
        if (addOpen) TagAdd(page, a)
    }
}

/** Every known tag as a toggle: black = the note has it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagSelect(page: Page, state: HomeUiState, a: HomeActions) {
    val own = state.pageTags[page.id].orEmpty()
    val all = (state.topicGroups.map { it.tag } + own).distinct()
    Column(Modifier.fillMaxWidth().padding(top = 12.dp).border(1.5.dp, Ink).padding(12.dp)) {
        if (all.isEmpty()) {
            Text("Henüz etiket yok. “+ Etiket” ile ilkini ekle.", fontFamily = Serif, fontStyle = FontStyle.Italic, fontSize = 20.sp, color = Grey1)
        } else {
            Caps("Etiket seç", 12, Grey1, FontWeight.Bold, Modifier.padding(bottom = 8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                all.forEach { tag -> ActionChip("#$tag", on = tag in own) { a.toggleTag(page.id, tag) } }
            }
        }
    }
}

/** Text box for a new tag; it is added to the note and becomes a topic on Home. */
@Composable
private fun TagAdd(page: Page, a: HomeActions) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(page.id) { runCatching { focus.requestFocus() } }
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        BasicTextField(
            value = a.pickerInput, onValueChange = a.setPickerInput, singleLine = true,
            textStyle = TextStyle(fontFamily = Serif, fontSize = 22.sp, color = Ink), cursorBrush = SolidColor(Ink),
            modifier = Modifier.weight(1f).height(40.dp).clip(Pill).border(1.5.dp, Ink, Pill).padding(16.dp, 9.dp).focusRequester(focus),
            decorationBox = { inner ->
                if (a.pickerInput.isEmpty()) Text("yeni etiket…", fontFamily = Serif, fontStyle = FontStyle.Italic, fontSize = 22.sp, color = Grey1)
                inner()
            }
        )
        Spacer(Modifier.width(10.dp))
        Box(Modifier.height(40.dp).clip(Pill).background(Ink).tap { a.addPickerTag(page.id) }.padding(horizontal = 22.dp), contentAlignment = Alignment.Center) {
            Caps("Ekle", 15, Color.White, FontWeight.Bold)
        }
    }
}
