package com.quantumff.segue.core.designsystem.util

import androidx.compose.ui.platform.UriHandler

/** Opens [url] in the browser; does nothing on a device with no app for web links. */
fun UriHandler.openLink(url: String) {
    try {
        openUri(url)
    } catch (_: IllegalArgumentException) {
        // AndroidUriHandler reports a missing browser this way.
    }
}
