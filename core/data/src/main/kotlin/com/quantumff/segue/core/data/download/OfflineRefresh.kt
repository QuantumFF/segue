package com.quantumff.segue.core.data.download

import com.quantumff.segue.core.data.snackbar.SnackbarText
import com.quantumff.segue.core.data.snackbar.Snackbars
import com.quantumff.segue.core.data.snackbar.offline

/**
 * A server refresh the user asked for: pull to refresh, Retry, or the offline chip's tap. Online it
 * runs [refresh]; Offline the server is not asked, and "You're offline" says why.
 */
fun OfflineLibraryView.requestRefresh(snackbars: Snackbars, refresh: () -> Unit) {
    if (isOffline.value) snackbars.post(SnackbarText.offline) else refresh()
}

/**
 * A screen's silent refresh as it opens: skipped while Offline, where the downloaded music is what
 * there is and the chip says so.
 */
fun OfflineLibraryView.refreshIfOnline(refresh: () -> Unit) {
    if (!isOffline.value) refresh()
}
