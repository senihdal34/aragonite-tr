package com.ethran.notable.reading.board

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ethran.notable.reading.ReadingData
import com.ethran.notable.reading.ReadingInsights
import com.ethran.notable.reading.ReadingText

/** Time read this year and this month. */
@Composable
fun HoursBox(data: ReadingData, modifier: Modifier = Modifier) {
    val h = remember(data.minutesByDay, data.now) { ReadingInsights.hours(data.minutesByDay, ReadingInsights.today(data)) }
    BoardBox(BoxKind.Ink, modifier) {
        Caps("Hours read", softColor(BoxKind.Ink))
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Figure("${h.year}", ReadingText.minutes(h.yearMinutes), Modifier.weight(1f))
            Figure(h.month, ReadingText.minutes(h.monthMinutes), Modifier.weight(1f))
        }
    }
}

@Composable
private fun Figure(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(value, fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 34.sp, color = Color.White, maxLines = 1)
        Text(label, fontFamily = Sans, fontSize = 13.sp, color = softColor(BoxKind.Ink), maxLines = 1)
    }
}
