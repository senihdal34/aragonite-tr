package com.ethran.notable.ui.views

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.material.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ethran.notable.data.db.Page
import com.ethran.notable.navigation.NavigationDestination
import com.ethran.notable.ui.components.PagePreview
import com.ethran.notable.ui.viewmodels.HomeUiState
import com.ethran.notable.ui.viewmodels.HomeViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object HomeDestination : NavigationDestination {
    override val route = "home"
}

@Composable
fun HomeView(
    viewModel: HomeViewModel,
    onOpenPage: (String) -> Unit,
    onSettings: () -> Unit,
    onCreateNewPage: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8F6F2))
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Notlar", fontSize = 20.sp, color = Color(0xFF1A1A1A))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("➕", modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onCreateNewPage() })
                Text("⚙️", modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onSettings() })
            }
        }

        Spacer(Modifier.height(16.dp))

        // Pinned Section
        SectionTitle("📌 Sabitlenenler")
        if (state.pinnedPages.isNotEmpty()) {
            PinnedSection(
                pages = state.pinnedPages,
                onOpenPage = onOpenPage,
                onUnpin = { pageId -> viewModel.togglePin(pageId, true) }
            )
        } else {
            Text(
                "Sabitlenmiş not yok",
                fontSize = 12.sp,
                color = Color(0xFFAAAAAA),
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        // Recents Section
        Spacer(Modifier.height(16.dp))
        SectionTitle("🕐 Son Notlar")
        state.recentPages.forEach { page ->
            NoteRow(
                page = page,
                onClick = { onOpenPage(page.id) }
            )
        }

        // Topics Section
        if (state.topicGroups.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            SectionTitle("# Konular")
            // Add tag input
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val input by viewModel.inputTag.collectAsState()
                TopicInput(value = input, onValueChange = { viewModel.setInputTag(it) })
                Text("➕", modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { viewModel.addTag(input) })
            }
            state.topicGroups.forEach { group ->
                TopicGroupHeader(tag = group.tag, count = group.pages.size, onRemove = { viewModel.removeTag(group.tag) })
                group.pages.forEach { page ->
                    NoteRow(
                        page = page,
                        onClick = { onOpenPage(page.id) }
                    )
                }
            }
        }

        // Loading state
        if (state.isLoading) {
            Text(
                "Yükleniyor...",
                fontSize = 12.sp,
                color = Color(0xFFAAAAAA),
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        fontSize = 10.sp,
        color = Color(0xFFAAAAAA),
        letterSpacing = 0.6.sp,
        modifier = Modifier.padding(bottom = 4.dp)
    )
}

@Composable
private fun PinnedSection(
    pages: List<Page>,
    onOpenPage: (String) -> Unit,
    onUnpin: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        pages.forEach { page ->
            PinnedPageCard(
                page = page,
                onClick = { onOpenPage(page.id) },
                onUnpin = { onUnpin(page.id) }
            )
        }
    }
}

@Composable
private fun PinnedPageCard(
    page: Page,
    onClick: () -> Unit,
    onUnpin: () -> Unit
) {
    Column(modifier = Modifier.width(110.dp)) {
        // Thumbnail area (3:4 ratio, ~147dp tall)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .border(1.dp, Color(0xFFD4CCC4), RectangleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onClick() }
        ) {
            PagePreview(
                modifier = Modifier.fillMaxSize(),
                pageId = page.id
            )
            // Page number top-right
            Text(
                "1",
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .background(Color.Black)
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                color = Color.White,
                fontSize = 9.sp
            )
            // Unpin button top-left (touch target >= 40dp)
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(2.dp)
                    .size(18.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0x99000000))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onUnpin() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "✕",
                    color = Color.White,
                    fontSize = 11.sp
                )
            }
        }
        // Title below thumbnail
        Text(
            text = page.title.ifBlank { "İsimsiz Not" },
            fontSize = 11.sp,
            color = Color(0xFF333333),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, start = 2.dp, end = 2.dp)
        )
    }
}

@Composable
private fun NoteRow(
    page: Page,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .border(1.dp, Color(0xFFE8E4DF), RoundedCornerShape(6.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Small thumbnail
        Box(
            modifier = Modifier
                .width(50.dp)
                .height(60.dp)
                .border(1.dp, Color(0xFFDDD6CE), RoundedCornerShape(3.dp))
                .clip(RoundedCornerShape(3.dp))
        ) {
            PagePreview(
                modifier = Modifier.fillMaxSize(),
                pageId = page.id
            )
        }
        // Info column
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = page.title.ifBlank { "İsimsiz Not" },
                fontSize = 13.sp,
                color = Color(0xFF1A1A1A),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = formatDate(page.createdAt),
                fontSize = 10.sp,
                color = Color(0xFFBBBBBB)
            )
        }
    }
}

@Composable
private fun TopicGroupHeader(tag: String, count: Int, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("#", fontSize = 14.sp, color = Color(0xFFAAAAAA))
        Spacer(Modifier.width(4.dp))
        Text(tag, fontSize = 12.sp, color = Color(0xFF555555))
        Spacer(Modifier.width(8.dp))
        Text("$count not", fontSize = 10.sp, color = Color(0xFFAAAAAA))
        Spacer(Modifier.weight(1f))
        Text("✕", modifier = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null
        ) { onRemove() }, fontSize = 12.sp, color = Color(0xFFCCCCCC))
    }
}

@Composable
private fun TopicInput(value: String, onValueChange: (String) -> Unit) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text("yeni konu...", fontSize = 12.sp, color = Color(0xFFBBBBBB)) },
        modifier = Modifier
            .height(36.dp)
            .weight(1f),
        singleLine = true,
        colors = TextFieldDefaults.textFieldColors(
            textColor = Color(0xFF333333),
            backgroundColor = Color(0xFFF0EEEC),
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent
        ),
        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp)
    )
}

/** Format a Date as "dd MMM HH:mm" (e.g. "13 Eyl 14:30") */
private fun formatDate(date: Date): String {
    val fmt = SimpleDateFormat("d MMM HH:mm", Locale("tr", "TR"))
    return fmt.format(date)
}