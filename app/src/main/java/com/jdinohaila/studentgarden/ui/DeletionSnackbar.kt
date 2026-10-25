package com.jdinohaila.studentgarden.ui

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import kotlinx.coroutines.withTimeoutOrNull

/** Bound the undo prompt; the item remains recoverable from Trash afterwards. */
internal suspend fun showDeletionSnackbar(host: SnackbarHostState, message: String, undo: String): Boolean =
    withTimeoutOrNull(3_000L) {
        host.showSnackbar(message, undo, withDismissAction = true, duration = SnackbarDuration.Indefinite)
    } == SnackbarResult.ActionPerformed
