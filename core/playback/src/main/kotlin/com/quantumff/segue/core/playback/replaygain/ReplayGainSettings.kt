package com.quantumff.segue.core.playback.replaygain

import com.quantumff.segue.core.model.ReplayGainMode
import com.quantumff.segue.core.model.ReplayGainTarget
import com.quantumff.segue.core.model.UserPreferences

/** The ReplayGain preferences playback needs. */
data class ReplayGainSettings(
    val mode: ReplayGainMode = ReplayGainMode.Track,
    val target: ReplayGainTarget = ReplayGainTarget.ReplayGain2,
    val boost: Boolean = false,
) {
    companion object {
        fun from(preferences: UserPreferences) = ReplayGainSettings(
            mode = preferences.replayGainMode,
            target = preferences.replayGainTarget,
            boost = preferences.replayGainBoost,
        )
    }
}
