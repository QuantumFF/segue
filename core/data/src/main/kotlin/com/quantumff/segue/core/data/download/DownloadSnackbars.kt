package com.quantumff.segue.core.data.download

import com.quantumff.segue.core.data.R
import com.quantumff.segue.core.data.snackbar.SnackbarAction
import com.quantumff.segue.core.data.snackbar.SnackbarText
import com.quantumff.segue.core.data.snackbar.Snackbars
import com.quantumff.segue.core.model.PinKind

/** Pins [target] and confirms it on [snackbars]: "Downloading <name>". */
suspend fun DownloadRepository.pinAndConfirm(target: PinTarget, snackbars: Snackbars) {
    pin(target)
    snackbars.confirmDownloading(listOf(target))
}

/** Confirms [targets], just pinned: "Downloading <name>" for one, "Downloading 3 items" for more, nothing for none. */
fun Snackbars.confirmDownloading(targets: List<PinTarget>) {
    val text = when (targets.size) {
        0 -> return
        1 -> SnackbarText.of(R.string.core_data_downloading_name, targets.single().name)
        else -> SnackbarText.plural(R.plurals.core_data_downloading_items, targets.size, targets.size)
    }
    post(text)
}

/**
 * Unpins at once and posts "Removed <name>" with Undo on [snackbars]. Undo pins it again; the files
 * are deleted only once the snackbar is dismissed or times out ([DownloadRepository.unpinUndoably]).
 */
suspend fun DownloadRepository.unpinWithUndo(kind: PinKind, id: String, name: String, snackbars: Snackbars) =
    unpinWithUndo(listOf(PinTarget(kind, id, name, coverArtId = null)), snackbars)

/**
 * [unpinWithUndo] of each of [targets] that is pinned, under one snackbar: "Removed <name>" for
 * one, "Removed 3 downloads" for more. Its Undo pins them all again.
 */
suspend fun DownloadRepository.unpinWithUndo(targets: List<PinTarget>, snackbars: Snackbars) {
    val removed = targets.mapNotNull { target -> unpinUndoably(target.kind, target.id)?.let { target to it } }
    val text = when (removed.size) {
        0 -> return
        1 -> SnackbarText.of(R.string.core_data_download_removed, removed.single().first.name)
        else -> SnackbarText.plural(R.plurals.core_data_downloads_removed, removed.size, removed.size)
    }
    val removals = removed.map { it.second }
    snackbars.post(
        text,
        SnackbarAction.undo(onDismiss = { removals.forEach { it.commit() } }) { removals.forEach { it.undo() } },
    )
}
