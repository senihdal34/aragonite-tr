package com.ethran.notable.reading.board

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ethran.notable.reading.ReadingData
import com.ethran.notable.reading.ReadingInsights
import com.ethran.notable.reading.ReadingText

/** Five greys, from no reading to 2 hours or more. */
internal val HeatRamp = listOf(FillGrey, Color(0xFFBBBBBB), Grey2, Grey1, Ink)

/** The last 16 weeks as a grid of days, one column per week (Sunday first), darker the more was read. */
@Composable
fun HeatmapBox(data: ReadingData, modifier: Modifier = Modifier) {
    val grid = remember(data.minutesByDay, data.now) { ReadingInsights.heatmap(data.minutesByDay, ReadingInsights.today(data)) }
    BoardBox(BoxKind.Line, modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Caps("Last ${ReadingInsights.HEAT_WEEKS} weeks", Grey1)
            Caps("${ReadingText.minutes(grid.totalMinutes)} · ${grid.daysRead} days", Ink)
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp)) {
            grid.monthLabels.forEach { label ->
                Box(Modifier.weight(1f)) {
                    if (label != null) Text(label, Modifier.wrapContentWidth(Alignment.Start, unbounded = true), fontFamily = Sans, fontSize = 11.sp, color = Grey1, maxLines = 1, overflow = TextOverflow.Clip, softWrap = false)
                }
            }
        }
        Canvas(Modifier.fillMaxWidth().padding(top = 3.dp).aspectRatio(ReadingInsights.HEAT_WEEKS / 7f)) {
            val gap = 2.dp.toPx()
            val cols = grid.weeks.size
            val cell = minOf((size.width - (cols - 1) * gap) / cols, (size.height - 6 * gap) / 7)
            val radius = CornerRadius(2.dp.toPx())
            grid.weeks.forEachIndexed { w, days ->
                days.forEachIndexed { d, minutes ->
                    if (minutes == null) return@forEachIndexed
                    val at = Offset(w * (cell + gap), d * (cell + gap))
                    drawRoundRect(HeatRamp[ReadingInsights.heatLevel(minutes)], at, Size(cell, cell), radius)
                }
            }
            // Today gets a ring, so it can be found even when nothing was read yet.
            val lastWeek = grid.weeks.last()
            val todayRow = lastWeek.indexOfLast { it != null }
            if (todayRow >= 0) {
                val at = Offset((cols - 1) * (cell + gap), todayRow * (cell + gap))
                drawRoundRect(Ink, at, Size(cell, cell), radius, style = Stroke(1.5.dp.toPx()))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            Text("Less", fontFamily = Sans, fontSize = 11.sp, color = Grey1)
            HeatRamp.forEach { Box(Modifier.padding(start = 3.dp).size(12.dp).background(it)) }
            Text("More", Modifier.padding(start = 6.dp), fontFamily = Sans, fontSize = 11.sp, color = Grey1)
        }
    }
}
