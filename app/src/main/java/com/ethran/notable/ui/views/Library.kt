package com.ethran.notable.ui.views

import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.ethran.notable.navigation.NavigationDestination

/**
 * Library destination — existing route for the book/folder list.
 * Retained for backward-compatibility; HomeView is the new default start screen.
 */
object LibraryDestination : NavigationDestination {
    override val route = "library"
    const val FOLDER_ID_ARG = "folderId"
    val routeWithArgs = "$route/{$FOLDER_ID_ARG}"
    fun createRoute(folderId: String?) = if (folderId != null) "$route/$folderId" else route
}

/**
 * Library composable — the original book/folder list screen.
 * Kept as a stub for navigation compatibility only.
 * Navigation to Library from editor toolbar still works (opens placeholder).
 */
@Composable
fun Library(
    navController: NavController,
    folderId: String?,
    goToPage: (String) -> Unit,
    onCreateNewQuickPage: (String?) -> Unit
) {
    Text(
        "Library",
        modifier = Modifier.padding(16.dp)
    )
}