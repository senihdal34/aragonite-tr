package com.ethran.notable.reading.board

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ethran.notable.reading.RBook
import com.ethran.notable.reading.ReadingData
import com.ethran.notable.reading.ReadingInsights
import com.ethran.notable.reading.ReadingText

/** "A year ago today you were reading ..." from the reading log, or a quiet note when there is nothing from back then. */
@Composable
fun YearAgoBox(data: ReadingData, modifier: Modifier = Modifier, onOpen: ((RBook) -> Unit)? = null) {
    val y = remember(data.booksByDay, data.books, data.now) { ReadingInsights.yearAgo(data, ReadingInsights.today(data)) }
    BoardBox(BoxKind.Fill, modifier, onClick = y?.let { a -> onOpen?.let { f -> { f(a.book) } } }) {
        Caps("A year ago today", Grey1)
        if (y == null) {
            Text("Nothing from a year ago yet. This fills in once you have read for a year.", fontFamily = Serif, fontStyle = FontStyle.Italic, fontSize = 18.sp, color = Grey1, modifier = Modifier.padding(top = 8.dp))
            return@BoardBox
        }
        Row(Modifier.padding(top = 8.dp)) {
            Cover(y.book, Modifier.width(48.dp).height(68.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("You were reading", fontFamily = Sans, fontSize = 13.sp, color = Grey1)
                Text(y.book.title, fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    ReadingInsights.dateLabel(y.date) + if (y.minutes > 0) " · ${ReadingText.minutes(y.minutes)}" else "",
                    fontFamily = Sans, fontSize = 13.sp, color = Grey1, modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}
