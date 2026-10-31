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
import com.ethran.notable.reading.ReadingData
import com.ethran.notable.reading.ReadingInsights
import com.ethran.notable.reading.ReadingText

/** "At this pace you finish this book on ...", from the minutes left and the last two weeks of reading. */
@Composable
fun ForecastBox(data: ReadingData, modifier: Modifier = Modifier) {
    val f = remember(data.continueReading, data.minutesByDay, data.now) {
        ReadingInsights.forecast(data.continueReading, data.minutesByDay, ReadingInsights.today(data))
    }
    BoardBox(BoxKind.Line, modifier) {
        Caps("Finish forecast", Grey1)
        if (f == null) {
            Text("Read a little and a finish date shows up here.", fontFamily = Serif, fontStyle = FontStyle.Italic, fontSize = 18.sp, color = Grey1, modifier = Modifier.padding(top = 8.dp))
            return@BoardBox
        }
        Row(Modifier.padding(top = 8.dp)) {
            Cover(f.book, Modifier.width(54.dp).height(76.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("At this pace you finish ${f.book.title} on", fontFamily = Sans, fontSize = 13.sp, color = Grey1, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(ReadingInsights.finishLabel(f), fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 29.sp, color = Ink, modifier = Modifier.padding(top = 2.dp))
                Text("${ReadingText.minutes(f.minutesLeft)} left at ~${f.perDay} min a day", fontFamily = Sans, fontSize = 13.sp, color = Grey1, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}
