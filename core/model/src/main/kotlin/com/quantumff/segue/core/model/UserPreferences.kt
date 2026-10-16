package com.quantumff.segue.core.model

import kotlin.math.abs

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
