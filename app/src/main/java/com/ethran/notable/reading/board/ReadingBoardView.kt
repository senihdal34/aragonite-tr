package com.ethran.notable.reading.board

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ethran.notable.reading.RBook
import com.ethran.notable.reading.ReadingData
import com.ethran.notable.reading.ReadingSearch
import com.ethran.notable.reading.ReadingText
import com.ethran.notable.reading.ReadingUi
import java.time.LocalTime

private enum class Sub { Library, Authors, Series, OnDevice }
private val PAGES = listOf("Home", "Library", "Insights", "Decor")

/**
 * The Reading board: pages of boxes (Home, Library, Insights, Decor), page dots at the bottom, a search field on top,
 * a list view behind the Library boxes and a detail view for one book (long press on a book, or its Info pill).
 * Tapping a book opens it in NeoReader. Portrait stacks the boxes; landscape uses two columns.
 */
@Composable
fun ReadingBoardView(ui: ReadingUi, onOpen: (RBook) -> Unit, modifier: Modifier = Modifier, onSetGoal: ((Int) -> Unit)? = null) {
    var page by remember { mutableStateOf(0) }
    var sub by remember { mutableStateOf<Sub?>(null) }
    var detailId by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    val data = ui.data
    val detail = detailId?.let { id -> data.books.firstOrNull { it.id == id } }
    val info: (RBook) -> Unit = { detailId = it.id }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val landscape = maxWidth > maxHeight
        Column(Modifier.fillMaxWidth()) {
            if (detail != null) {
                BookDetail(detail, data.highlights.filter { it.book?.id == detail.id }, data.now.takeIf { it > 0L } ?: System.currentTimeMillis(), onOpen) { detailId = null }
            } else if (sub != null) {
                SubList(sub!!, data, onOpen, info) { sub = null }
            } else {
                Greeting(data)
                SearchField(query, { query = it }, Modifier.padding(bottom = 14.dp))
                if (!ui.sourceAvailable && ui.loaded) {
                    Text("NeoReader's library could not be read, so there is nothing to show yet.", fontFamily = FontFamily.Serif,
                        fontStyle = FontStyle.Italic, fontSize = 20.sp, color = Grey1, modifier = Modifier.padding(vertical = 24.dp))
                } else if (query.isNotBlank()) {
                    SearchResults(data, query, onOpen, info)
                } else {
                    when (page) {
                        0 -> HomePage(data, landscape, onOpen, info, onSetGoal)
                        1 -> LibraryPage(data, landscape, onOpen, info) { sub = it }
                        2 -> InsightsPage(data, landscape, onOpen)
                        else -> DecorPage(data, landscape)
                    }
                    Spacer(Modifier.height(18.dp))
                    PageDots(page) { page = it }
                }
            }
        }
    }
}

@Composable
private fun Greeting(data: ReadingData) {
    val hour = remember { LocalTime.now().hour }
    Row(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 14.dp), verticalAlignment = Alignment.Bottom) {
        Text(ReadingText.greeting(hour, data.name), fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 30.sp, color = Ink, modifier = Modifier.weight(1f))
        Caps("${data.totalBooks} books", Grey1)
    }
}

@Composable
private fun HomePage(data: ReadingData, landscape: Boolean, onOpen: (RBook) -> Unit, onInfo: (RBook) -> Unit, onSetGoal: ((Int) -> Unit)?) {
    val hero = data.continueReading.firstOrNull()
    val gap = 12.dp
    if (landscape) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(gap)) {
                HeroBox(hero, data.discover.firstOrNull(), onOpen, Modifier.fillMaxWidth(), onInfo)
                ShelfBox("Discover", data.discover, onOpen, Modifier.fillMaxWidth(), onInfo)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(gap)) {
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    StreakBox(data, Modifier.weight(1f).fillMaxHeight()); WeekBox(data, Modifier.weight(1f).fillMaxHeight())
                }
                GoalBox(data, Modifier.fillMaxWidth(), onSetGoal)
                AlsoReadingBox(data, onOpen, Modifier.fillMaxWidth(), onInfo)
            }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            HeroBox(hero, data.discover.firstOrNull(), onOpen, Modifier.fillMaxWidth(), onInfo)
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(gap)) {
                StreakBox(data, Modifier.weight(1f).fillMaxHeight()); WeekBox(data, Modifier.weight(1f).fillMaxHeight())
            }
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(gap)) {
                GoalBox(data, Modifier.weight(1f).fillMaxHeight(), onSetGoal); AlsoReadingBox(data, onOpen, Modifier.weight(1f).fillMaxHeight(), onInfo)
            }
            ShelfBox("Discover", data.discover, onOpen, Modifier.fillMaxWidth(), onInfo)
        }
    }
}

@Composable
private fun LibraryPage(data: ReadingData, landscape: Boolean, onOpen: (RBook) -> Unit, onInfo: (RBook) -> Unit, openSub: (Sub) -> Unit) {
    val gap = 12.dp
    val links: @Composable (Modifier) -> Unit = { m ->
        Column(m, verticalArrangement = Arrangement.spacedBy(gap)) {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(gap)) {
                LinkBox("Library", "${data.totalBooks}", Modifier.weight(1f).fillMaxHeight()) { openSub(Sub.Library) }
                LinkBox("Authors", "${data.authorCount}", Modifier.weight(1f).fillMaxHeight()) { openSub(Sub.Authors) }
            }
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(gap)) {
                LinkBox("Series", "${data.seriesCount}", Modifier.weight(1f).fillMaxHeight()) { openSub(Sub.Series) }
                LinkBox("On device", "${data.totalBooks}", Modifier.weight(1f).fillMaxHeight()) { openSub(Sub.OnDevice) }
            }
        }
    }
    if (landscape) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(gap)) {
                ShelfBox("Recently added", data.recentlyAdded, onOpen, Modifier.fillMaxWidth(), onInfo)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(gap)) {
                links(Modifier.fillMaxWidth()); HighlightBox(data, Modifier.fillMaxWidth())
            }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            ShelfBox("Recently added", data.recentlyAdded, onOpen, Modifier.fillMaxWidth(), onInfo)
            links(Modifier.fillMaxWidth())
            HighlightBox(data, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun PageDots(page: Int, onPage: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Caps("‹", Ink, 22, Modifier.clickable(interactionSource = MutableInteractionSource(), indication = null) { onPage((page - 1).coerceAtLeast(0)) }.padding(14.dp))
        PAGES.forEachIndexed { i, name ->
            Row(
                Modifier.clip(RoundedCornerShape(50)).background(if (i == page) Ink else Color.Transparent)
                    .clickable(interactionSource = MutableInteractionSource(), indication = null) { onPage(i) }
                    .padding(horizontal = 18.dp, vertical = 8.dp)
            ) { Caps(name, if (i == page) Color.White else Ink, 13) }
        }
        Caps("›", Ink, 22, Modifier.clickable(interactionSource = MutableInteractionSource(), indication = null) { onPage((page + 1).coerceAtMost(PAGES.lastIndex)) }.padding(14.dp))
    }
}

@Composable
private fun SubList(sub: Sub, data: ReadingData, onOpen: (RBook) -> Unit, onInfo: (RBook) -> Unit, back: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Pill("← Back", onClick = back)
        Spacer(Modifier.width(16.dp))
        Text(
            when (sub) { Sub.Library -> "Library"; Sub.Authors -> "Authors"; Sub.Series -> "Series"; Sub.OnDevice -> "On device" },
            fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 30.sp, color = Ink
        )
    }
    val groups: List<Pair<String?, List<RBook>>> = when (sub) {
        Sub.Library, Sub.OnDevice -> listOf(null to data.books.sortedBy { it.title.lowercase() })
        Sub.Authors -> data.books.groupBy { it.author.ifBlank { "Unknown" } }.toList().sortedBy { it.first.lowercase() }
        Sub.Series -> data.books.filter { it.series != null }.groupBy { it.series!! }.toList().sortedBy { it.first.lowercase() }
    }
    groups.forEach { (header, books) ->
        if (header != null) Caps("$header · ${books.size}", Grey1, 13, Modifier.padding(top = 14.dp, bottom = 4.dp))
        books.forEach { b -> BookRow(b, onOpen, onInfo) }
    }
}

@Composable
internal fun BookRow(b: RBook, onOpen: (RBook) -> Unit, onInfo: (RBook) -> Unit) {
    Row(
        Modifier.fillMaxWidth().tapOrHold({ onOpen(b) }, { onInfo(b) }).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Cover(b, Modifier.width(44.dp).height(62.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(b.title, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 20.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, color = Ink)
            if (b.author.isNotBlank()) Text(b.author, fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic, fontSize = 15.sp, color = Grey1, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Caps(if (b.finishedAt != null) "Done" else if (b.progress > 0) "${b.progress.toInt()}%" else "", Grey1, 12)
        Spacer(Modifier.width(12.dp))
        SmallPill("Info") { onInfo(b) }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(Grey2))
}

@Composable
private fun SearchResults(data: ReadingData, query: String, onOpen: (RBook) -> Unit, onInfo: (RBook) -> Unit) {
    val found = remember(data.books, query) { ReadingSearch.filter(data.books, query) }
    Caps("${found.size} ${if (found.size == 1) "result" else "results"}", Grey1, 13, Modifier.padding(bottom = 4.dp))
    if (found.isEmpty()) Text("No book matches “${query.trim()}”.", fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic, fontSize = 20.sp, color = Grey1, modifier = Modifier.padding(vertical = 12.dp))
    found.forEach { BookRow(it, onOpen, onInfo) }
}

/** Two boxes side by side with equal height. */
@Composable
private fun SideBySide(
    gap: Dp, left: @Composable (Modifier) -> Unit, right: @Composable (Modifier) -> Unit, modifier: Modifier = Modifier.fillMaxWidth()
) {
    Row(modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(gap)) {
        left(Modifier.weight(1f).fillMaxHeight()); right(Modifier.weight(1f).fillMaxHeight())
    }
}

@Composable
private fun InsightsPage(data: ReadingData, landscape: Boolean, onOpen: (RBook) -> Unit) {
    val gap = 12.dp
    if (landscape) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(gap)) {
                HeatmapBox(data, Modifier.fillMaxWidth()); RecordsBox(data, Modifier.fillMaxWidth()); YearAgoBox(data, Modifier.fillMaxWidth(), onOpen)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(gap)) {
                ForecastBox(data, Modifier.fillMaxWidth())
                SideBySide(gap, { HoursBox(data, it) }, { WordBox(data, it) })
            }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            HeatmapBox(data, Modifier.fillMaxWidth())
            ForecastBox(data, Modifier.fillMaxWidth())
            SideBySide(gap, { HoursBox(data, it) }, { WordBox(data, it) })
            RecordsBox(data, Modifier.fillMaxWidth())
            YearAgoBox(data, Modifier.fillMaxWidth(), onOpen)
        }
    }
}

@Composable
private fun DecorPage(data: ReadingData, landscape: Boolean) {
    val gap = 12.dp
    if (landscape) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
            BuddyBox(data, Modifier.weight(1f))
            SideBySide(gap, { ClockBox(it) }, { MoonBox(it) }, Modifier.weight(1f))
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            BuddyBox(data, Modifier.fillMaxWidth())
            SideBySide(gap, { ClockBox(it) }, { MoonBox(it) })
        }
    }
}
