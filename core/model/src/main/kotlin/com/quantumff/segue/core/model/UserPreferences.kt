package com.quantumff.segue.core.model

import kotlin.math.abs

/** App settings. See docs/DESIGN.md, "Settings". */
data class UserPreferences(
    val themeMode: ThemeMode = ThemeMode.System,
    val accentSource: AccentSource = AccentSource.Fixed,
    val accentPreset: AccentPreset = AccentPreset.Blue,
    val wifiStreamingQuality: StreamingQuality = StreamingQuality.Original,
    val cellularStreamingQuality: StreamingQuality = StreamingQuality.Original,
    val queueSyncEnabled: Boolean = true,
    /** Adopt a newer server play queue without asking. See docs/adr/0004. */
    val adoptRemoteQueueAutomatically: Boolean = false,
    /**
     * With [adoptRemoteQueueAutomatically] off, play pressed outside the app offers a newer server
     * play queue in a "Continue from <device>" notification. Needs the notification permission from
     * Android 13. See docs/adr/0004.
     */
    val offerRemoteQueueInNotification: Boolean = true,
    val replayGainMode: ReplayGainMode = ReplayGainMode.Track,
    /** The loudness ReplayGain levels to. Only applies when [replayGainMode] is not Off. */
    val replayGainTarget: ReplayGainTarget = ReplayGainTarget.ReplayGain2,
    /**
     * Lets ReplayGain amplify quiet tracks. That takes an audio processor, so audio offload stays
     * off while this is on. Off: ReplayGain only attenuates, through player volume.
     */
    val replayGainBoost: Boolean = false,
    /**
     * Upper bound of the on-disk stream cache, in MB. One of [STREAM_CACHE_SIZES_MB]. Least
     * recently played tracks are evicted first.
     */
    val streamCacheSizeMb: Int = DEFAULT_STREAM_CACHE_MB,
    /** The playback service stops itself after being paused this long. */
    val stopWhenPausedMinutes: Int = DEFAULT_STOP_WHEN_PAUSED_MINUTES,
    /**
     * Resume the queue when a Bluetooth audio device (headphones, a car) connects, unless something
     * is already playing. Needs the Nearby devices permission from Android 12.
     */
    val playOnBluetoothConnect: Boolean = false,
    /** Look for a newer release on GitHub once a day when the app starts. */
    val checkForUpdates: Boolean = true,
    /** Card size of the Library's album grid, which pinching the grid zooms. */
    val albumGridSize: GridSize = GridSize.Default,
    /** Card size of the Library's artist grid, as [albumGridSize]. */
    val artistGridSize: GridSize = GridSize.Default,
) {
    companion object {
        const val DEFAULT_STREAM_CACHE_MB = 1024
        val STREAM_CACHE_SIZES_MB = listOf(256, 512, 1024, 2048, 4096)
        const val DEFAULT_STOP_WHEN_PAUSED_MINUTES = 10
        val STOP_WHEN_PAUSED_MINUTES = listOf(5, 10, 30, 60)
    }
}

/** Which ReplayGain value levels loudness. Each falls back to the other when its value is missing. */
enum class ReplayGainMode {
    Off,
    Track,
    Album,
}

/**
 * ReplayGain normalisation, after Symfonium's setting of the same name: the loudness every track is
 * levelled to. Navidrome reports all gains against the ReplayGain 2.0 reference of -18 LUFS (it
 * converts Opus R128 gains, which use -23 LUFS, by adding 5 dB), so each target is a fixed offset.
 */
enum class ReplayGainTarget(val lufs: Int) {
    /** -18 LUFS, the ReplayGain 2.0 reference: gains as Navidrome reports them. */
    ReplayGain2(lufs = -18),

    /** -23 LUFS, the EBU R128 broadcast target: 5 dB quieter, with more headroom. */
    Ebu(lufs = -23),
    ;

    /** dB added to every reported gain. */
    val offsetDb: Float get() = (lufs - REFERENCE_LUFS).toFloat()

    companion object {
        /** The loudness Navidrome's gains are referenced to. */
        const val REFERENCE_LUFS = -18

        /** The target closest to a preamp of [preampDb], from the setting this one replaced. */
        fun nearestTo(preampDb: Float): ReplayGainTarget = entries.minBy { abs(it.offsetDb - preampDb) }
    }
}

/**
 * A grid's card size, as the columns it has beyond its usual count for the screen width: more,
 * smaller cards when positive, fewer, larger ones when negative. [Default] is the usual count.
 */
@JvmInline
value class GridSize(val extraColumns: Int) {
    companion object {
        val Default = GridSize(0)
    }
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
