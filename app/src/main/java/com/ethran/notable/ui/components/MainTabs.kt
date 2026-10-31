package com.ethran.notable.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class MainTab { Week, Notes, Pinned, Reading }

/** The Planner | Notes | ★ Favorites | Reading bar (title case). The same bar sits under the header on Home and on the planner pages. */
@Composable
fun MainTabs(active: MainTab?, onWeek: () -> Unit, onNotes: () -> Unit, onPinned: () -> Unit, onReading: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Tab("Planner", active == MainTab.Week, last = false, onClick = onWeek)
        Tab("Notes", active == MainTab.Notes, last = false, onClick = onNotes)
        Tab("★ Favorites", active == MainTab.Pinned, last = false, onClick = onPinned)
        Tab("Reading", active == MainTab.Reading, last = true, onClick = onReading)
    }
}

@Composable
private fun Tab(label: String, on: Boolean, last: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.height(54.dp)
            .clickable(interactionSource = MutableInteractionSource(), indication = null, onClick = onClick)
            .background(if (on) Color(0xFF111111) else Color.Transparent).padding(horizontal = 26.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label, fontFamily = FontFamily.SansSerif, fontSize = 22.sp, fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.4.sp, color = if (on) Color.White else Color(0xFF111111)
        )
    }
    if (!last && !on) Box(Modifier.width(1.5.dp).height(26.dp).background(Color(0xFF999999)))
}
