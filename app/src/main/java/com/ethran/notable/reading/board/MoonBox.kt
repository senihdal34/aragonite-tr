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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ethran.notable.reading.MoonPhase
import com.ethran.notable.reading.ReadingDecor
import java.time.LocalDate

/** Today's moon phase, drawn as a lit white shape on a black disc. */
@Composable
fun MoonBox(modifier: Modifier = Modifier, today: LocalDate = remember { LocalDate.now() }) {
    val moon = remember(today) { ReadingDecor.moon(today) }
    BoardBox(BoxKind.Ink, modifier) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Caps("Moon", softColor(BoxKind.Ink), 12, Modifier.align(Alignment.Start))
            Canvas(Modifier.fillMaxWidth(0.55f).aspectRatio(1f).padding(top = 8.dp)) {
                val r = size.minDimension / 2f - 2.dp.toPx()
                drawCircle(Color.White, r, center, style = Stroke(1.5.dp.toPx()))
                drawPath(moonLit(moon, center, r), Color.White)
            }
            Text(moon.name, Modifier.padding(top = 8.dp), fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White)
            Text("${moon.litPercent}% lit · ${moon.next}", fontFamily = FontFamily.SansSerif, fontSize = 12.sp, color = softColor(BoxKind.Ink))
        }
    }
}

/**
 * The lit part: a half disc on the lit side plus the terminator, a half ellipse back to the top. The terminator
 * bulges into the lit side for a crescent (less lit) and into the dark side for a gibbous moon (more lit).
 */
internal fun moonLit(m: MoonPhase, c: Offset, r: Float): Path {
    val onRight = ReadingDecor.litOnRight(m)
    val crescent = m.phase < 0.25 || m.phase > 0.75
    val rx = (r * ReadingDecor.terminator(m)).toFloat()
    return Path().apply {
        arcTo(Rect(c.x - r, c.y - r, c.x + r, c.y + r), -90f, if (onRight) 180f else -180f, true)
        arcTo(Rect(c.x - rx, c.y - r, c.x + rx, c.y + r), 90f, if (onRight == crescent) -180f else 180f, false)
        close()
    }
}
