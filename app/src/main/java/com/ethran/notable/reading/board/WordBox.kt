package com.ethran.notable.reading.board

import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ethran.notable.reading.ReadingData
import com.ethran.notable.reading.ReadingInsights
import com.ethran.notable.reading.ReadingWords

/** One English word and its meaning, a new one each day. */
@Composable
fun WordBox(data: ReadingData, modifier: Modifier = Modifier) {
    val w = remember(data.now) { ReadingWords.of(ReadingInsights.today(data)) }
    BoardBox(BoxKind.Ink, modifier) {
        Caps("Word of the day", softColor(BoxKind.Ink))
        Text(w.word, fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 34.sp, color = Color.White, modifier = Modifier.padding(top = 6.dp))
        Text(w.meaning, fontFamily = Serif, fontStyle = FontStyle.Italic, fontSize = 18.sp, lineHeight = 24.sp, color = Color.White, modifier = Modifier.padding(top = 4.dp))
    }
}
