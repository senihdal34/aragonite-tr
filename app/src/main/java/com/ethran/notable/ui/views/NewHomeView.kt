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
import com.ethran.notable.data.db.Page
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
    onCreateNewPage: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()
    val selected by viewModel.selectedTopic.collectAsState()

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
        unpin = { id -> viewModel.togglePin(id, true) }
    )

    BoxWithConstraints(Modifier.fillMaxSize().background(Paper)) {
        val landscape = maxWidth > maxHeight
        val scroll = rememberScrollState()
        Column(
            Modifier.fillMaxSize().verticalScroll(scroll)
                .padding(horizontal = if (landscape) 50.dp else 56.dp, vertical = if (landscape) 34.dp else 44.dp)
        ) {
            Masthead(state, selected, landscape, actions, adding, input)
            if (landscape) {
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                    Column(Modifier.weight(1f).padding(end = 36.dp)) {
                        Stories(shown, state, selected, landscape = true, actions = actions)
                    }
                    Rail(state, selected, actions)
                }
            } else {
                Stories(shown, state, selected, landscape = false, actions = actions)
                if (state.pinnedPages.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    PinnedBox(state, actions, Modifier.fillMaxWidth().border(2.5.dp, Ink).padding(16.dp, 14.dp))
                }
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
    val unpin: (String) -> Unit
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
    state: HomeUiState, selected: String?, landscape: Boolean, a: HomeActions, adding: Boolean, input: String
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
        "Notlar", fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = if (landscape) 100.sp else 116.sp,
        letterSpacing = (-3).sp, textAlign = if (landscape) TextAlign.Start else TextAlign.Center,
        color = Ink, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    )
    Rule(5.dp); Spacer(Modifier.height(5.dp)); Rule()
    SectionNav(state, selected, a)
    if (adding) TopicInput(input, a)
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
private fun NavItem(label: String, on: Boolean, last: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.height(44.dp).tap(onClick)
            .background(if (on) Ink else Color.Transparent)
            .padding(horizontal = 22.dp),
        contentAlignment = Alignment.Center
    ) { Caps(label, 17, if (on) Color.White else Ink, FontWeight.SemiBold) }
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
        Box(Modifier.width(thumbW).height(thumbH).border(2.dp, Ink).tap { a.open(page.id) }) {
            PagePreview(Modifier.fillMaxSize(), page.id, state.thumbRevision, ContentScale.Crop, Alignment.TopCenter, Color.White)
        }
        Spacer(Modifier.width(if (landscape) 28.dp else 34.dp))
        Column(Modifier.weight(1f).tap { a.open(page.id) }) {
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
        Row(Modifier.fillMaxWidth().tap { a.open(page.id) }) {
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
    Column(Modifier.tap { a.open(page.id) }) {
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
private fun PinnedBox(state: HomeUiState, a: HomeActions, modifier: Modifier) {
    Column(modifier) {
        Caps("Sabitlenen", 14, Grey1, FontWeight.Bold, Modifier.padding(bottom = 10.dp))
        state.pinnedPages.take(3).forEach { p ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(70.dp).height(94.dp).border(1.5.dp, Ink).tap { a.open(p.id) }) {
                    PagePreview(Modifier.fillMaxSize(), p.id, state.thumbRevision, ContentScale.Crop, Alignment.TopCenter, Color.White)
                }
                Spacer(Modifier.width(18.dp))
                Column(Modifier.weight(1f).tap { a.open(p.id) }) {
                    Text(p.title.ifBlank { "İsimsiz Not" }, fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 30.sp,
                        color = Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Caps(formatDate(p.createdAt), 13, Grey1, modifier = Modifier.padding(top = 6.dp))
                }
                Caps("Kaldır", 13, Grey1, modifier = Modifier.tap { a.unpin(p.id) }.padding(12.dp))
            }
        }
    }
}

@Composable
private fun Rail(state: HomeUiState, selected: String?, a: HomeActions) {
    Row(Modifier.width(350.dp).fillMaxHeight()) {
        Box(Modifier.width(1.5.dp).fillMaxHeight().background(Ink))
        Column(Modifier.padding(start = 28.dp)) {
            if (state.pinnedPages.isNotEmpty()) {
                PinnedBox(state, a, Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 10.dp))
                Rule()
            }
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
