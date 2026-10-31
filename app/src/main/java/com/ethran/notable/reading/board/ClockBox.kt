package com.ethran.notable.reading.board

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ethran.notable.reading.ReadingDecor
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/** A plain analog clock with the date under it. It shows the time of the last redraw, with no ticking. */
@Composable
fun ClockBox(modifier: Modifier = Modifier) {
    val time = remember { LocalTime.now() }
    val date = remember { LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH)) }
    val angles = remember(time) { ReadingDecor.clockAngles(time.hour, time.minute) }
    BoardBox(BoxKind.Line, modifier) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Canvas(Modifier.fillMaxWidth(0.8f).aspectRatio(1f)) {
                val c = center
                val r = size.minDimension / 2f - 2.dp.toPx()
                drawCircle(Ink, r, c, style = Stroke(2.dp.toPx()))
                for (i in 0 until 12) {
                    val a = Math.toRadians(i * 30.0)
                    val long = i % 3 == 0
                    val from = r * (if (long) 0.80f else 0.88f)
                    drawLine(
                        Ink, Offset(c.x + sin(a).toFloat() * from, c.y - cos(a).toFloat() * from),
                        Offset(c.x + sin(a).toFloat() * r * 0.96f, c.y - cos(a).toFloat() * r * 0.96f),
                        strokeWidth = (if (long) 3.dp else 1.5.dp).toPx(), cap = StrokeCap.Round
                    )
                }
                fun hand(deg: Float, length: Float, width: Float) {
                    val a = Math.toRadians(deg.toDouble())
                    drawLine(Ink, c, Offset(c.x + sin(a).toFloat() * r * length, c.y - cos(a).toFloat() * r * length), strokeWidth = width, cap = StrokeCap.Round)
                }
                hand(angles.hour, 0.5f, 5.dp.toPx())
                hand(angles.minute, 0.78f, 3.dp.toPx())
                drawCircle(Ink, 4.dp.toPx(), c)
            }
            Caps(date, Grey1, 11, Modifier.padding(top = 8.dp))
        }
    }
}
