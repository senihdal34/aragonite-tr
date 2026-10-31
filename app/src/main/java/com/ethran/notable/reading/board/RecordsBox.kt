package com.ethran.notable.reading.board

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ethran.notable.reading.ReadingData
import com.ethran.notable.reading.ReadingText
import com.ethran.notable.reading.ReadingInsights

/** Longest streak, best day, and books finished per year. */
@Composable
fun RecordsBox(data: ReadingData, modifier: Modifier = Modifier) {
    val r = remember(data.minutesByDay, data.books) { ReadingInsights.records(data.minutesByDay, data.books) }
    BoardBox(BoxKind.Fill, modifier) {
        Caps("Records", Grey1)
        Line("Longest streak", if (r.longestStreak == 0) "–" else "${r.longestStreak} ${if (r.longestStreak == 1) "day" else "days"}")
        Line("Best day", r.bestDay?.let { "${ReadingText.minutes(r.bestDayMinutes)} · ${ReadingInsights.shortDate(it)}" } ?: "–")
        Line("Books a year", if (r.booksPerYear.isEmpty()) "–" else r.booksPerYear.take(3).joinToString("   ") { "${it.first}: ${it.second}" })
    }
}

@Composable
private fun Line(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
        Text(label, fontFamily = Sans, fontSize = 14.sp, color = Grey1)
        Text(value, fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Ink, maxLines = 1)
    }
}
