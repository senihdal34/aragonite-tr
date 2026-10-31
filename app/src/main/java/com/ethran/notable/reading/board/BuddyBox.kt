package com.ethran.notable.reading.board

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ethran.notable.reading.BuddyPose
import com.ethran.notable.reading.ReadingData
import com.ethran.notable.reading.ReadingDecor
import com.ethran.notable.reading.ReadingInsights
import java.time.LocalTime

/** A pixel cat that reacts to your reading: asleep when nothing was read today, partying on streak milestones. Drawn once, never animated. */
@Composable
fun BuddyBox(data: ReadingData, modifier: Modifier = Modifier) {
    val pose = remember(data.now, data.continueReading, data.minutesByDay, data.streakDays, data.books) {
        val today = ReadingInsights.today(data)
        ReadingDecor.buddyPose(
            hasBookInProgress = data.continueReading.isNotEmpty(), streak = data.streakDays,
            longestStreak = ReadingInsights.longestStreak(data.minutesByDay), todayMinutes = ReadingInsights.minutesOn(data, today),
            finishedToday = ReadingInsights.finishedToday(data.books, if (data.now > 0L) data.now else System.currentTimeMillis()),
            hour = LocalTime.now().hour
        )
    }
    val (headline, second) = remember(pose, data.now, data.streakDays, data.minutesByDay) {
        ReadingDecor.buddyLines(
            pose, data.streakDays, ReadingInsights.longestStreak(data.minutesByDay),
            ReadingInsights.minutesOn(data, ReadingInsights.today(data))
        )
    }
    BoardBox(BoxKind.Fill, modifier) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            PixelBuddy(pose, Modifier.width(112.dp).height(128.dp))
            Spacer(Modifier.width(16.dp))
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(Color.White)
                    .border(1.5.dp, Ink, RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Text(headline, fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Ink)
                Text(second, fontFamily = Sans, fontSize = 14.sp, color = Grey1, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}

/** The sprite as solid squares: black cat, white holes for eyes, nothing else. */
@Composable
internal fun PixelBuddy(pose: BuddyPose, modifier: Modifier = Modifier) {
    val rows = remember(pose) { ReadingDecor.buddySprite(pose) }
    Canvas(modifier) {
        // Whole pixels only, so the edges stay sharp on the panel.
        val cell = minOf(size.width / ReadingDecor.SPRITE_COLS, size.height / ReadingDecor.SPRITE_ROWS).toInt().coerceAtLeast(1).toFloat()
        val dx = (size.width - cell * ReadingDecor.SPRITE_COLS) / 2f
        val dy = size.height - cell * ReadingDecor.SPRITE_ROWS
        rows.forEachIndexed { r, line ->
            line.forEachIndexed { c, ch ->
                val color = when (ch) { '#' -> Ink; 'w' -> Color.White; else -> return@forEachIndexed }
                drawRect(color, Offset(dx + c * cell, dy + r * cell), Size(cell, cell))
            }
        }
    }
}
