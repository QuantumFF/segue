package com.quantumff.segue.core.playback.replaygain

import com.quantumff.segue.core.model.ReplayGain
import com.quantumff.segue.core.model.ReplayGainMode
import kotlin.math.log10
import kotlin.math.min
import kotlin.math.pow

/**
 * How loud one track plays: the linear [volume] for the player and the linear [gain] for
 * [ReplayGainProcessor]. At most one of them is not 1.
 *
 * Without boost, ReplayGain is player volume. `AudioTrack` volume works in offload mode, so the
 * DSP keeps decoding with the screen off (docs/PLAN.md, "Battery"). Volume cannot exceed 1, so
 * positive gains are capped at unity. The volume changes on the main thread at the track
 * transition, a few milliseconds late in a gapless transition.
 *
 * With boost, the processor applies the gain, so it can amplify. Processors need PCM output, so
 * audio offload stays off. A known peak caps the gain so the loudest sample just reaches full
 * scale; without one, an amplified track goes through [PeakLimiter] ([limit]).
 *
 * OpenSubsonic `baseGain` is not added: decoders already apply header gains such as Opus output gain.
 */
data class ReplayGainLevel(val volume: Float, val gain: Float, val limit: Boolean) {
    companion object {
        val Unity = ReplayGainLevel(volume = UNITY, gain = UNITY, limit = false)

        /** The most boost applies, against wrong tags and noise floors on very quiet tracks. */
        const val MAX_BOOST_DB = 15f

        fun of(gain: ReplayGain?, settings: ReplayGainSettings): ReplayGainLevel {
            if (gain == null) return Unity
            val (db, peak) = when (settings.mode) {
                ReplayGainMode.Off -> return Unity
                ReplayGainMode.Track -> (gain.trackGain ?: gain.albumGain) to (gain.trackPeak ?: gain.albumPeak)
                ReplayGainMode.Album -> (gain.albumGain ?: gain.trackGain) to (gain.albumPeak ?: gain.trackPeak)
            }
            val totalDb = min((db ?: gain.fallbackGain ?: return Unity) + settings.target.offsetDb, MAX_BOOST_DB)
            var linear = 10f.pow(totalDb / DB_PER_DECADE)
            val knownPeak = peak?.takeIf { it > 0f }
            if (knownPeak != null) linear = min(linear, UNITY / knownPeak)
            return if (settings.boost) {
                ReplayGainLevel(volume = UNITY, gain = linear, limit = linear > UNITY && knownPeak == null)
            } else {
                ReplayGainLevel(volume = min(linear, UNITY), gain = UNITY, limit = false)
            }
        }

        private const val UNITY = 1f
        private const val DB_PER_DECADE = 20f

        /** [linear] in dB, for logs. */
        internal fun decibels(linear: Float) = DB_PER_DECADE * log10(linear)
    }
}

/** Logcat tag for ReplayGain decisions: per-track volume and per-stream processor gain. */
internal const val REPLAY_GAIN_TAG = "SegueReplayGain"
