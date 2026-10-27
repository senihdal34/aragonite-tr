package com.ethran.notable.io

import android.content.Context
import com.ethran.notable.editor.utils.persistBitmapThumbnail
import dagger.hilt.android.qualifiers.ApplicationContext
import io.shipbook.shipbooksdk.ShipBook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private val log = ShipBook.getLogger("ThumbnailGenerator")

/**
 * Creates the Home-screen thumbnail of a page that has none yet.
 *
 * Thumbnails are normally written when a page is closed in the editor, so notes that were
 * imported, restored from a backup or never reopened showed an empty grey box on Home.
 */
@Singleton
class ThumbnailGenerator @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val exportEngine: ExportEngine
) {
    // One page at a time: a long page renders to a ~30 MB bitmap.
    private val mutex = Mutex()

    fun thumbFile(pageId: String) = File(context.filesDir, "pages/previews/thumbs/$pageId")

    /** Returns true when a new thumbnail was written. */
    suspend fun ensure(pageId: String): Boolean = mutex.withLock {
        if (thumbFile(pageId).exists()) return false
        withContext(Dispatchers.IO) {
            try {
                val bitmap = exportEngine.renderBitmapForPage(pageId)
                try {
                    persistBitmapThumbnail(context, bitmap, pageId)
                } finally {
                    bitmap.recycle()
                }
                thumbFile(pageId).exists()
            } catch (e: Exception) {
                log.e("Thumbnail for page $pageId failed: ${e.message}")
                false
            }
        }
    }
}
