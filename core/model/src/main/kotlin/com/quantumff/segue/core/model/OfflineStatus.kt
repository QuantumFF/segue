package com.quantumff.segue.core.model

/**
 * Why a screen shows less than the server has (DESIGN.md §5.8). Anything but [Online] shows a
 * status chip under the top bar, with copy for its case. Tapping the chip retries, except in
 * [ShowingDownloadsInOfflineMode], where it opens the Offline mode setting: see [opensOfflineModeSetting].
 */
enum class OfflineStatus {
    Online,

    /** The server can't be reached while the network is fine: Room's cached library shows. */
    ShowingCachedLibrary,

    /** Offline without a validated network: only downloaded songs are available, so only downloaded music shows. */
    ShowingDownloads,

    /** Offline because the user turned on Offline mode: only downloaded music shows until they turn it off. */
    ShowingDownloadsInOfflineMode,
    ;

    /** Whether lists are narrowed to downloaded music: Offline, for either reason. */
    val isShowingDownloads: Boolean
        get() = this == ShowingDownloads || this == ShowingDownloadsInOfflineMode

    /** A tap on the chip opens Settings at Downloads & storage, where Offline mode is turned off, rather than retrying. */
    val opensOfflineModeSetting: Boolean
        get() = this == ShowingDownloadsInOfflineMode

    /**
     * This status once a screen's last server refresh is known: online, a refresh that could not reach
     * the server ([serverUnreachable]) makes it [ShowingCachedLibrary]. Offline it stays as it is.
     */
    fun withServerUnreachable(serverUnreachable: Boolean): OfflineStatus =
        if (this == Online && serverUnreachable) ShowingCachedLibrary else this
}
