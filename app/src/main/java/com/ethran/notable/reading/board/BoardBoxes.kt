package com.ethran.notable.reading.board

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.ethran.notable.reading.RBook
import com.ethran.notable.reading.ReadingData
import com.ethran.notable.reading.ReadingText
import java.time.LocalDate
import java.util.Locale

// E-ink look: black, white and two greys, no shadows. Boxes follow the BookOrbit Board chrome:
// ink = solid black, fill = light grey, line = thin frame, 12 dp corners.
internal val Ink = Color(0xFF111111)
internal val Grey1 = Color(0xFF555555)
internal val Grey2 = Color(0xFF999999)
internal val FillGrey = Color(0xFFE8E8E4)
internal val Serif = FontFamily.Serif
internal val Sans = FontFamily.SansSerif
private val Corner = RoundedCornerShape(12.dp)

enum class BoxKind { Ink, Fill, Line }

@Composable
internal fun BoardBox(
    kind: BoxKind, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, onLongClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    var m = modifier.clip(Corner)
    m = when (kind) {
        BoxKind.Ink -> m.background(Ink)
        BoxKind.Fill -> m.background(FillGrey)
        BoxKind.Line -> m.border(1.5.dp, Ink, Corner)
    }
    if (onClick != null) m = m.tapOrHold(onClick, onLongClick)
    Column(m.padding(16.dp), content = content)
}

internal fun onColor(kind: BoxKind) = if (kind == BoxKind.Ink) Color.White else Ink
internal fun softColor(kind: BoxKind) = if (kind == BoxKind.Ink) Color(0xFFCCCCCC) else Grey1

@Composable
internal fun Caps(text: String, color: Color = Grey1, size: Int = 12, modifier: Modifier = Modifier) =
    Text(text.uppercase(Locale("tr", "TR")), fontFamily = Sans, fontSize = size.sp, fontWeight = FontWeight.Bold,
        letterSpacing = (size * 0.14f).sp, color = color, modifier = modifier)

@Composable
internal fun Bar(fraction: Float, kind: BoxKind, modifier: Modifier = Modifier, height: Dp = 10.dp) {
    val fg = onColor(kind)
    Box(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(50)).border(1.dp, fg, RoundedCornerShape(50))) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0f, 1f)).background(fg))
    }
}

@Composable
internal fun Pill(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(50)).border(1.5.dp, Ink, RoundedCornerShape(50))
            .clickable(interactionSource = MutableInteractionSource(), indication = null, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 9.dp), contentAlignment = Alignment.Center
    ) { Caps(label, Ink, 12) }
}

/** Book cover from the cover file, or a plain title card when there is none. */
@Composable
internal fun Cover(book: RBook, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(6.dp)
    if (book.cover != null) {
        Image(
            painter = rememberAsyncImagePainter(model = book.cover), contentDescription = book.title,
            contentScale = ContentScale.Crop, modifier = modifier.clip(shape).border(1.5.dp, Ink, shape).background(Color.White)
        )
    } else {
        Box(modifier.clip(shape).border(1.5.dp, Ink, shape).background(FillGrey).padding(8.dp), contentAlignment = Alignment.Center) {
            Text(book.title, fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Ink, maxLines = 5, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ---------- boxes ----------

@Composable
fun HeroBox(book: RBook?, discover: RBook?, onOpen: (RBook) -> Unit, modifier: Modifier = Modifier, onInfo: ((RBook) -> Unit)? = null) {
    val b = book ?: discover
    BoardBox(BoxKind.Fill, modifier, onClick = b?.let { { onOpen(it) } }, onLongClick = b?.let { x -> onInfo?.let { f -> { f(x) } } }) {
        if (b == null) {
            Caps("Continue reading", Grey1)
            Text("No books yet. Put books in /sdcard/Books.", fontFamily = Serif, fontStyle = FontStyle.Italic, fontSize = 22.sp, color = Ink, modifier = Modifier.padding(top = 8.dp))
            return@BoardBox
        }
        Row {
            Cover(b, Modifier.width(120.dp).height(170.dp))
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Caps(if (book != null) "Continue reading" else "Start reading", Grey1)
                Text(b.title, fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 31.sp, color = Ink,
                    maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                if (b.author.isNotBlank()) Text(b.author, fontFamily = Serif, fontStyle = FontStyle.Italic, fontSize = 18.sp, color = Grey1, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.weight(1f, fill = true).height(10.dp))
                if (book != null) {
                    Bar((book.progress / 100.0).toFloat(), BoxKind.Fill)
                    Text(
                        "${book.progress.toInt()}%" + (book.minutesLeft?.let { " · ${ReadingText.minutes(it)} left" } ?: ""),
                        fontFamily = Sans, fontSize = 14.sp, color = Grey1, modifier = Modifier.padding(top = 6.dp, bottom = 10.dp)
                    )
                }
                Pill(if (book != null) "Continue" else "Open") { onOpen(b) }
            }
        }
    }
}

@Composable
fun StreakBox(data: ReadingData, modifier: Modifier = Modifier) {
    val letters = listOf("S", "M", "T", "W", "T", "F", "S")
    BoardBox(BoxKind.Ink, modifier) {
        Caps("Streak", softColor(BoxKind.Ink))
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 4.dp)) {
            Text("${data.streakDays}", fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 56.sp, color = Color.White)
            Text(if (data.streakDays == 1) "day" else "days", fontFamily = Sans, fontSize = 16.sp, color = softColor(BoxKind.Ink), modifier = Modifier.padding(start = 8.dp, bottom = 10.dp))
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            for (i in 0..6) {
                val read = data.weekDots.getOrElse(i) { false }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(20.dp).clip(RoundedCornerShape(50))
                            .background(if (read) Color.White else Color.Transparent)
                            .border(1.5.dp, Color.White, RoundedCornerShape(50))
                    )
                    Text(letters[i], fontFamily = Sans, fontSize = 12.sp, fontWeight = if (i == data.todayIndex) FontWeight.Bold else FontWeight.Normal,
                        color = Color.White, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

@Composable
fun WeekBox(data: ReadingData, modifier: Modifier = Modifier) {
    val letters = listOf("S", "M", "T", "W", "T", "F", "S")
    val max = (data.weekMinutes.maxOrNull() ?: 0).coerceAtLeast(30)
    BoardBox(BoxKind.Line, modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Caps("This week", Grey1)
            Caps(ReadingText.minutes(data.readThisWeekMinutes), Ink)
        }
        // Sunday first; days that have not come yet stay empty.
        Row(Modifier.fillMaxWidth().height(96.dp).padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
            for (i in 0..6) {
                val m = data.weekMinutes.getOrElse(i) { 0 }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                    Box(
                        Modifier.fillMaxWidth().height((70f * m / max).coerceAtLeast(3f).dp)
                            .clip(RoundedCornerShape(3.dp)).background(if (m > 0) Ink else Grey2)
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (i in 0..6) {
                Text(letters[i], Modifier.weight(1f), fontFamily = Sans, fontSize = 12.sp, color = if (i == data.todayIndex) Ink else Grey1,
                    fontWeight = if (i == data.todayIndex) FontWeight.Bold else FontWeight.Normal, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
    }
}

@Composable
fun GoalBox(data: ReadingData, modifier: Modifier = Modifier, onSetGoal: ((Int) -> Unit)? = null) {
    val today = LocalDate.now()
    val done = data.finishedThisYear.size
    var editing by remember { mutableStateOf(false) }
    BoardBox(BoxKind.Line, modifier) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Caps("${today.year} goal", Grey1, modifier = Modifier.weight(1f))
            if (onSetGoal != null) SmallPill(if (editing) "Done" else "Goal") { editing = !editing }
        }
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 4.dp)) {
            Text("$done", fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 48.sp, color = Ink)
            Text("/ ${data.goalBooks} books", fontFamily = Sans, fontSize = 16.sp, color = Grey1, modifier = Modifier.padding(start = 8.dp, bottom = 8.dp))
        }
        Bar(if (data.goalBooks > 0) done.toFloat() / data.goalBooks else 0f, BoxKind.Line)
        Text(ReadingText.pace(done, data.goalBooks, today.dayOfYear, today.lengthOfYear()), fontFamily = Sans, fontSize = 14.sp, color = Grey1, modifier = Modifier.padding(top = 8.dp))
        if (editing && onSetGoal != null) GoalStepper(data.goalBooks, onSetGoal, Modifier.padding(top = 10.dp))
    }
}

@Composable
fun AlsoReadingBox(data: ReadingData, onOpen: (RBook) -> Unit, modifier: Modifier = Modifier, onInfo: ((RBook) -> Unit)? = null) {
    BoardBox(BoxKind.Line, modifier) {
        Caps("Also reading", Grey1)
        val list = data.alsoReading.take(3)
        if (list.isEmpty()) Text("Nothing else in progress", fontFamily = Serif, fontStyle = FontStyle.Italic, fontSize = 18.sp, color = Grey1, modifier = Modifier.padding(top = 8.dp))
        list.forEach { b ->
            Row(
                Modifier.fillMaxWidth().padding(top = 10.dp).tapOrHold({ onOpen(b) }, onInfo?.let { f -> { f(b) } }),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Cover(b, Modifier.width(36.dp).height(50.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(b.title, fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Ink)
                    Bar((b.progress / 100.0).toFloat(), BoxKind.Line, Modifier.padding(top = 4.dp), 6.dp)
                }
            }
        }
    }
}

@Composable
fun ShelfBox(title: String, books: List<RBook>, onOpen: (RBook) -> Unit, modifier: Modifier = Modifier, onInfo: ((RBook) -> Unit)? = null) {
    BoardBox(BoxKind.Line, modifier) {
        Caps(title, Grey1)
        if (books.isEmpty()) Text("Nothing here yet", fontFamily = Serif, fontStyle = FontStyle.Italic, fontSize = 18.sp, color = Grey1, modifier = Modifier.padding(top = 8.dp))
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            books.take(4).forEach { b ->
                Column(Modifier.weight(1f).tapOrHold({ onOpen(b) }, onInfo?.let { f -> { f(b) } })) {
                    Cover(b, Modifier.fillMaxWidth().height(150.dp))
                    Text(b.title, fontFamily = Serif, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, color = Ink, modifier = Modifier.padding(top = 4.dp))
                }
            }
            repeat((4 - books.take(4).size).coerceAtLeast(0)) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
fun LinkBox(label: String, value: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    BoardBox(BoxKind.Fill, modifier, onClick = onClick) {
        Caps(label, Grey1)
        Text(value, fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 40.sp, color = Ink, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
fun HighlightBox(data: ReadingData, modifier: Modifier = Modifier) {
    val h = data.highlight
    BoardBox(BoxKind.Ink, modifier) {
        Caps("Highlight of the day", softColor(BoxKind.Ink))
        if (h == null) {
            Text("Highlights you make in NeoReader show up here.", fontFamily = Serif, fontStyle = FontStyle.Italic, fontSize = 20.sp, color = Color.White, modifier = Modifier.padding(top = 8.dp))
        } else {
            Text("“${h.quote}”", fontFamily = Serif, fontStyle = FontStyle.Italic, fontSize = 20.sp, lineHeight = 27.sp, color = Color.White,
                maxLines = 7, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
            h.book?.let { Text("— ${it.title}", fontFamily = Sans, fontSize = 14.sp, color = softColor(BoxKind.Ink), modifier = Modifier.padding(top = 8.dp)) }
        }
    }
}
