package com.ethran.notable.reading.board

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

/** A tap, and optionally a long press, with no ripple (e-ink). Long press is where a book's detail view opens. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun Modifier.tapOrHold(onTap: () -> Unit, onHold: (() -> Unit)? = null): Modifier {
    val source = remember { MutableInteractionSource() }
    return combinedClickable(interactionSource = source, indication = null, onClick = onTap, onLongClick = onHold)
}
