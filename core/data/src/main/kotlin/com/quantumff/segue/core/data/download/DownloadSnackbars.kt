package com.quantumff.segue.core.data.download

import com.quantumff.segue.core.data.R
import com.quantumff.segue.core.data.snackbar.SnackbarAction
import com.quantumff.segue.core.data.snackbar.SnackbarText
import com.quantumff.segue.core.data.snackbar.Snackbars
import com.quantumff.segue.core.model.PinKind

/** Pins [target] and confirms it on [snackbars]: "Downloading <name>". */
suspend fun DownloadRepository.pinAndConfirm(target: PinTarget, snackbars: Snackbars) {
    pin(target)
    snackbars.post(SnackbarText.of(R.string.core_data_downloading_name, target.name))
}

/**
 * Unpins at once and posts "Removed <name>" with Undo on [snackbars]. Undo pins it again; the files
 * are deleted only once the snackbar is dismissed or times out ([DownloadRepository.unpinUndoably]).
 */
suspend fun DownloadRepository.unpinWithUndo(kind: PinKind, id: String, name: String, snackbars: Snackbars) {
    val removal = unpinUndoably(kind, id) ?: return
    snackbars.post(
        SnackbarText.of(R.string.core_data_download_removed, name),
        SnackbarAction.undo(onDismiss = { removal.commit() }) { removal.undo() },
    )
}
