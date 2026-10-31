package com.ethran.notable.reading

import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

/** Opens a book in NeoReader, the reader the data comes from. */
object ReadingLauncher {
    private const val TAG = "ReadingLauncher"
    private const val NEOREADER = "com.onyx.kreader"

    fun mimeOf(path: String): String = when (path.substringAfterLast('.', "").lowercase()) {
        "pdf" -> "application/pdf"
        "epub" -> "application/epub+zip"
        "mobi", "azw3" -> "application/x-mobipocket-ebook"
        "txt" -> "text/plain"
        else -> "*/*"
    }

    fun open(context: Context, path: String): Boolean {
        val file = File(path)
        return try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mimeOf(path)).setPackage(NEOREADER)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.w(TAG, "could not open $path: ${e.javaClass.simpleName}: ${e.message}")
            Toast.makeText(context, "Could not open the book in NeoReader", Toast.LENGTH_SHORT).show()
            false
        }
    }
}
