package com.quantumff.segue.core.playback.replaygain

import com.quantumff.segue.core.model.ReplayGain
import com.quantumff.segue.core.model.ReplayGainMode
import kotlin.math.min
import kotlin.math.pow

/**
 * ReplayGain applied as player volume rather than as an audio processor.
 *
 * Why volume: an `AudioProcessor` in the render chain forces PCM output, which turns audio
 * offload off, and offload with the screen off is the biggest battery win (see docs/PLAN.md,
 * "Battery"). `AudioTrack` volume also works in offload mode. The costs: volume can only
 * attenuate, so positive gains (quiet tracks) are capped at unity, which also keeps them from
 * clipping; and the volume changes on the main thread at the track transition, a few milliseconds
 * late in a gapless transition. Most masters are loud, so most ReplayGain values are negative.
 *
 * OpenSubsonic `baseGain` is not added: decoders already apply header gains such as Opus output gain.
 */
object ReplayGainVolume {
    /** The linear player volume (0..1) for a track with [gain] in [mode], with [preampDb] added. */
    fun of(gain: ReplayGain?, mode: ReplayGainMode, preampDb: Float): Float {
        if (mode == ReplayGainMode.Off || gain == null) return UNITY
        val (db, peak) = when (mode) {
            ReplayGainMode.Track -> (gain.trackGain ?: gain.albumGain) to (gain.trackPeak ?: gain.albumPeak)
            ReplayGainMode.Album -> (gain.albumGain ?: gain.trackGain) to (gain.albumPeak ?: gain.trackPeak)
            ReplayGainMode.Off -> return UNITY
        }
        val totalDb = (db ?: gain.fallbackGain ?: return UNITY) + preampDb
        var linear = 10f.pow(totalDb / DB_PER_DECADE)
        if (peak != null && peak > 0f) linear = min(linear, UNITY / peak)
        return linear.coerceIn(0f, UNITY)
    }

    private const val UNITY = 1f
    private const val DB_PER_DECADE = 20f
}
