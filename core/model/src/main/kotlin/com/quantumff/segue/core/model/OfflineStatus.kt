package com.quantumff.segue.core.model

/**
 * Why a screen shows less than the server has (DESIGN.md §5.8). Anything but [Online] shows a
 * status chip under the top bar, with copy for its case.
 */
enum class OfflineStatus {
    Online,

    /** The server can't be reached while the network is fine: Room's cached library shows. */
    ShowingCachedLibrary,

    /** Offline: only downloaded songs are available, so only downloaded music shows. */
    ShowingDownloads,
}
