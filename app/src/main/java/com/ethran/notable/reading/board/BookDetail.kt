package com.ethran.notable.reading.board

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ethran.notable.reading.RBook
import com.ethran.notable.reading.RHighlight
import com.ethran.notable.reading.ReadingText

/** The detail view of one book: cover, facts, an "Open in NeoReader" pill and the highlights made in it. */
@Composable
fun BookDetail(book: RBook, highlights: List<RHighlight>, now: Long, onOpen: (RBook) -> Unit, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Pill("← Back", onClick = onBack)
        Spacer(Modifier.width(16.dp))
        Caps("Book", Grey1, 13)
    }
    BoardBox(BoxKind.Fill, Modifier.fillMaxWidth()) {
        Row {
            Cover(book, Modifier.width(150.dp).height(212.dp))
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f)) {
                Text(book.title, fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 32.sp, color = Ink)
                if (book.author.isNotBlank()) Text(book.authors.joinToString(", "), fontFamily = Serif, fontStyle = FontStyle.Italic, fontSize = 19.sp, color = Grey1, modifier = Modifier.padding(top = 4.dp))
                book.series?.let { Caps(it, Grey1, 12, Modifier.padding(top = 8.dp)) }
                Spacer(Modifier.height(14.dp))
                Bar((book.progress / 100.0).toFloat(), BoxKind.Fill)
                Text(
                    if (book.finishedAt != null) "Finished" else "${book.progress.toInt()}% read",
                    fontFamily = Sans, fontSize = 14.sp, color = Grey1, modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
        Column(Modifier.padding(top = 16.dp)) {
            Fact("Pages", if (book.pages > 0) "${book.pages}" else "–")
            Fact("Last read", ReadingText.lastRead(book.lastReadAt, now))
            Fact("Time left", book.minutesLeft?.let { ReadingText.minutes(it) } ?: if (book.finishedAt != null) "Done" else "–")
        }
        Pill("Open in NeoReader", Modifier.padding(top = 16.dp)) { onOpen(book) }
    }
    Caps("Highlights · ${highlights.size}", Grey1, 13, Modifier.padding(top = 20.dp, bottom = 6.dp))
    if (highlights.isEmpty()) {
        Text("No highlights in this book yet.", fontFamily = Serif, fontStyle = FontStyle.Italic, fontSize = 18.sp, color = Grey1)
    }
    highlights.forEach { h ->
        Text("“${h.quote}”", fontFamily = Serif, fontStyle = FontStyle.Italic, fontSize = 19.sp, lineHeight = 26.sp, color = Ink, modifier = Modifier.padding(vertical = 10.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Grey2))
    }
}

@Composable
private fun Fact(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontFamily = Sans, fontSize = 15.sp, color = Grey1)
        Text(value, fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Ink)
    }
}
