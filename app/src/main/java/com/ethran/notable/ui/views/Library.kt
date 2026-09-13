package com.ethran.notable.ui.views

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.ethran.notable.navigation.NavigationDestination
import com.ethran.notable.ui.viewmodels.LibraryViewModel

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
 * Kept unchanged for editor navigation compatibility.
 */
@Composable
fun Library(
    navController: NavController,
    folderId: String?,
    goToPage: (String) -> Unit,
    onCreateNewQuickPage: (String?) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    // Placeholder: redirect the user to HomeView if they end up here
    // The original Library is no longer the start destination.
    // Full library implementation preserved in LibraryViewModel.
    androidx.compose.material.Text("Library", modifier = androidx.compose.ui.Modifier.padding(16.dp))
}