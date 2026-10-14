package com.quantumff.segue.core.model

/** App settings. See docs/DESIGN.md, "Settings". */
data class UserPreferences(
    val themeMode: ThemeMode = ThemeMode.System,
    val accentSource: AccentSource = AccentSource.Fixed,
    val accentPreset: AccentPreset = AccentPreset.Blue,
    val defaultShuffleMode: ShuffleMode = ShuffleMode.Off,
    val wifiStreamingQuality: StreamingQuality = StreamingQuality.Original,
    val cellularStreamingQuality: StreamingQuality = StreamingQuality.Original,
    val queueSyncEnabled: Boolean = true,
    /** Adopt a newer server play queue without asking. See PLAN.md, "Server queue sync". */
    val adoptRemoteQueueAutomatically: Boolean = false,
    val replayGainMode: ReplayGainMode = ReplayGainMode.Off,
    /** Added to the ReplayGain adjustment, in dB. Only applies when [replayGainMode] is not Off. */
    val replayGainPreampDb: Float = 0f,
    /** Upper bound of the on-disk stream cache. Least recently played tracks are evicted first. */
    val streamCacheSizeMb: Int = DEFAULT_STREAM_CACHE_MB,
    /** The playback service stops itself after being paused this long. */
    val stopWhenPausedMinutes: Int = DEFAULT_STOP_WHEN_PAUSED_MINUTES,
) {
    companion object {
        const val DEFAULT_STREAM_CACHE_MB = 1024
        const val DEFAULT_STOP_WHEN_PAUSED_MINUTES = 10
    }
}

/** Which ReplayGain value levels loudness. Each falls back to the other when its value is missing. */
enum class ReplayGainMode {
    Off,
    Track,
    Album,
}

enum class ThemeMode {
    System,
    Light,
    Dark,

    /** Replaces Dark when the system is in dark mode. */
    Black,
}

enum class AccentSource {
    Fixed,
    MaterialYou,
    AlbumArt,
}

/** Fixed accent presets. Colors live in the design system. */
enum class AccentPreset {
    Blue,
    Teal,
    Violet,
    Pink,
    Orange,
    Green,
}

/**
 * A `stream` request's transcoding parameters. [maxBitRateKbps] 0 and a null [format] stream the
 * original file.
 */
data class StreamingQuality(val maxBitRateKbps: Int, val format: String?) {
    companion object {
        val Original = StreamingQuality(maxBitRateKbps = 0, format = null)
    }
}
