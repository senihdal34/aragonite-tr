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
import com.ethran.notable.planner.PlannerLaunch
import com.ethran.notable.ui.showHint
import com.ethran.notable.ui.viewmodels.HomeSection
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import com.ethran.notable.navigation.NavigationDestination
import com.ethran.notable.ui.components.PagePreview
import com.ethran.notable.ui.viewmodels.HomeUiState
import com.ethran.notable.ui.viewmodels.HomeViewModel
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
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val shown: List<Page> = if (selected == null) state.recentPages
    else state.topicGroups.firstOrNull { it.tag == selected }?.pages.orEmpty()
    var adding by remember { mutableStateOf(false) }
    val input by viewModel.inputTag.collectAsState()

    val actions = HomeActions(
        open = onOpenPage,
        settings = onSettings,
        newNote = onCreateNewPage,
        select = viewModel::selectTopic,
        toggleAdd = { adding = !adding },
        setInput = viewModel::setInputTag,
        addTopic = { viewModel.addTag(input); adding = false },
        removeTopic = { tag -> viewModel.removeTag(tag) },
        unpin = { id -> viewModel.togglePin(id, true) },
        planner = { viewModel.openPlanner(onOpenPlanner) },
        section = viewModel::selectSection,
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
                .padding(horizontal = if (landscape) 50.dp else 56.dp, vertical = if (landscape) 34.dp else 44.dp)
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
    val planner: () -> Unit,
    val section: (HomeSection) -> Unit,
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
    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically) {
        Caps(date, 14, Grey1, FontWeight.SemiBold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            val total = (state.recentPages + state.pinnedPages).distinctBy { it.id }.size
            Caps("$total not · ${state.pinnedPages.size} sabit", 14)
            Spacer(Modifier.width(22.dp))
            Caps("Ayarlar", 15, Ink, FontWeight.Bold, Modifier.tap(a.settings).padding(vertical = 10.dp, horizontal = 4.dp))
        }
    }
    Rule()
    Text(
        if (section == HomeSection.Pinned) "Sabit" else "Notlar", fontFamily = Serif, fontWeight = FontWeight.Bold,
        fontSize = if (landscape) 100.sp else 116.sp,
        letterSpacing = (-3).sp, textAlign = if (landscape) TextAlign.Start else TextAlign.Center,
        color = Ink, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    )
    Rule(5.dp); Spacer(Modifier.height(5.dp)); Rule()
    MainNav(section, a)
    if (section == HomeSection.Notes) {
        PinnedStrip(state, a)
        SectionNav(state, selected, a)
        if (adding) TopicInput(input, a)
    }
}

/** HAFTA opens the weekly planner; NOTLAR and SABİT switch the list below. */
@Composable
private fun MainNav(section: HomeSection, a: HomeActions) {
    Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically) {
        NavItem("Hafta", false, last = false, big = true) { a.planner() }
        NavItem("Notlar", section == HomeSection.Notes, last = false, big = true) { a.section(HomeSection.Notes) }
        NavItem("★ Sabit", section == HomeSection.Pinned, last = true, big = true) { a.section(HomeSection.Pinned) }
    }
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
                    PagePreview(Modifier.fillMaxSize(), p.id, state.thumbRevision, ContentScale.Crop, Alignment.TopCenter, Color.White)
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
                PagePreview(Modifier.fillMaxSize(), p.id, state.thumbRevision, ContentScale.Crop, Alignment.TopCenter, Color.White)
            }
            Spacer(Modifier.width(24.dp))
            Column(Modifier.weight(1f)) {
                Text(p.title.ifBlank { "İsimsiz Not" }, fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 38.sp,
                    letterSpacing = (-0.5).sp, color = Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Caps(formatDate(p.createdAt), 14, Grey1, modifier = Modifier.padding(top = 8.dp))
                state.excerpts[p.id]?.let {
                    Text(it, fontFamily = Serif, fontStyle = FontStyle.Italic, fontSize = 22.sp, lineHeight = 29.sp,
                        color = Ink, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
                }
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
        if (selected != null) {
            Spacer(Modifier.width(20.dp))
            Caps("Konuyu kaldır", 13, Grey1, modifier = Modifier.tap { a.removeTopic(selected) }.padding(10.dp))
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
            PagePreview(Modifier.fillMaxSize(), page.id, state.thumbRevision, ContentScale.Crop, Alignment.TopCenter, Color.White)
        }
        Spacer(Modifier.width(if (landscape) 28.dp else 34.dp))
        Column(Modifier.weight(1f).openOrPin(a, page)) {
            Caps(if (selected == null) "Son not · ${formatDate(page.createdAt)}" else "#$selected · ${formatDate(page.createdAt)}", 14, Grey1, FontWeight.Bold)
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
            Text("1 sayfa", fontFamily = Sans, fontSize = 16.sp, color = Grey1, modifier = Modifier.padding(top = 18.dp))
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
                PagePreview(Modifier.fillMaxSize(), page.id, state.thumbRevision, ContentScale.Crop, Alignment.TopCenter, Color.White)
            }
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    page.title.ifBlank { "İsimsiz Not" }, fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 30.sp,
                    lineHeight = 33.sp, letterSpacing = (-0.5).sp, color = Ink, maxLines = 2, overflow = TextOverflow.Ellipsis
                )
                Caps(formatDate(page.createdAt), 13, Grey1, modifier = Modifier.padding(top = 6.dp, bottom = 8.dp))
                state.excerpts[page.id]?.let {
                    Text(it, fontFamily = Serif, fontStyle = FontStyle.Italic, fontSize = 20.sp, lineHeight = 27.sp,
                        color = Ink, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        return
    }
    Column(Modifier.openOrPin(a, page)) {
        Box(Modifier.fillMaxWidth().height(200.dp).border(1.5.dp, Ink)) {
            PagePreview(Modifier.fillMaxSize(), page.id, state.thumbRevision, ContentScale.Crop, Alignment.TopCenter, Color.White)
        }
        Text(
            page.title.ifBlank { "İsimsiz Not" }, fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 30.sp,
            lineHeight = 33.sp, letterSpacing = (-0.5).sp, color = Ink, maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 12.dp, bottom = 6.dp)
        )
        Caps(formatDate(page.createdAt), 13, Grey1)
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

/** "13 Eki 14:30" */
private fun formatDate(date: Date): String = SimpleDateFormat("d MMM · HH:mm", TR).format(date)
