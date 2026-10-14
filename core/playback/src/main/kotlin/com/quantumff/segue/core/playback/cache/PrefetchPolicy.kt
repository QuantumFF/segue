package com.quantumff.segue.core.playback.cache

enum class NetworkType {
    Unmetered,
    Metered,
    None,
}

/**
 * Which upcoming tracks to download whole into the stream cache, so the radio can sleep between
 * bursts instead of trickling data. Wi-Fi and other unmetered networks fetch two tracks ahead;
 * cellular fetches only the next one, so skipping wastes at most one track of data.
 */
object PrefetchPolicy {
    private const val UNMETERED_LOOKAHEAD = 2
    private const val METERED_LOOKAHEAD = 1

    /** [upNext] is the song IDs after the current one, in play order. */
    fun targets(upNext: List<String>, network: NetworkType): List<String> = when (network) {
        NetworkType.Unmetered -> upNext.take(UNMETERED_LOOKAHEAD)
        NetworkType.Metered -> upNext.take(METERED_LOOKAHEAD)
        NetworkType.None -> emptyList()
    }
}
