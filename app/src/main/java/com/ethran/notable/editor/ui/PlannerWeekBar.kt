package com.ethran.notable.editor.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Pill = RoundedCornerShape(50)

/**
 * Week tool on a planner page: previous week, this week, next week. It sits in the free middle of the page's
 * header band, so it does not cover anything you write.
 */
@Composable
fun PlannerWeekBar(
    isCurrentWeek: Boolean,
    onPrevious: () -> Unit,
    onThisWeek: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier.padding(top = 10.dp).clip(Pill).background(Color.White).border(BorderStroke(1.5.dp, Color.Black), Pill).padding(horizontal = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically
    ) {
        BarButton("‹ Önceki", false, onPrevious)
        BarButton("Bu hafta", isCurrentWeek, onThisWeek)
        BarButton("Sonraki ›", false, onNext)
    }
}

@Composable
private fun BarButton(label: String, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(Pill).background(if (on) Color.Black else Color.Transparent)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label.uppercase(), fontFamily = FontFamily.SansSerif, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp, color = if (on) Color.White else Color.Black)
    }
}
