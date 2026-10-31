package com.ethran.notable.reading.board

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ethran.notable.reading.ReadingGoal

/** A small pill for a box header: "Goal" opens the stepper, "Done" closes it. */
@Composable
internal fun SmallPill(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(50)).border(1.5.dp, Ink, RoundedCornerShape(50))
            .tapOrHold(onClick).padding(horizontal = 12.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) { Caps(label, Ink, 11) }
}

/** The yearly goal with − and + buttons. Every press saves, so there is no separate confirm. */
@Composable
internal fun GoalStepper(goal: Int, onSet: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StepButton("−", enabled = goal > ReadingGoal.MIN) { onSet(ReadingGoal.step(goal, -1)) }
        Text("$goal", Modifier.width(48.dp), fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 28.sp, color = Ink, textAlign = TextAlign.Center)
        StepButton("+", enabled = goal < ReadingGoal.MAX) { onSet(ReadingGoal.step(goal, 1)) }
        Caps("books a year", Grey1, 11)
    }
}

@Composable
private fun StepButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(RoundedCornerShape(50)).border(1.5.dp, if (enabled) Ink else Grey2, RoundedCornerShape(50))
            .tapOrHold({ if (enabled) onClick() }),
        contentAlignment = Alignment.Center
    ) { Text(label, fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 24.sp, color = if (enabled) Ink else Grey2) }
}
