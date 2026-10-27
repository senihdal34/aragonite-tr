package com.ethran.notable.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import java.io.File

/**
 * Page thumbnail. [revision] changes when a missing thumbnail has just been generated;
 * the cache key also contains the file's modification time, so an edited page never shows
 * a stale image.
 */
@Composable
fun PagePreview(
    modifier: Modifier = Modifier,
    pageId: String,
    revision: Int = 0,
    contentScale: ContentScale = ContentScale.FillWidth,
    alignment: Alignment = Alignment.Center,
    backgroundColor: Color = Color.LightGray
) {
    val isPreview = LocalInspectionMode.current
    val context = LocalContext.current

    // If we are in the Android Studio Preview, just draw a gray placeholder
    if (isPreview) {
        androidx.compose.foundation.layout.Box(
            modifier = modifier.background(Color.LightGray)
        )
        return
    }

    val imgFile = remember(pageId) {
        File(context.filesDir, "pages/previews/thumbs/$pageId")
    }
    val modified = if (revision >= 0) imgFile.lastModified() else 0L

    val request = remember(pageId, modified, revision) {
        ImageRequest.Builder(context)
            .data(imgFile)
            .memoryCacheKey("thumb-$pageId-$modified")
            .build()
    }

    Image(
        painter = rememberAsyncImagePainter(model = request),
        contentDescription = "Page Preview",
        contentScale = contentScale,
        alignment = alignment,
        modifier = modifier.background(backgroundColor)
    )
}
