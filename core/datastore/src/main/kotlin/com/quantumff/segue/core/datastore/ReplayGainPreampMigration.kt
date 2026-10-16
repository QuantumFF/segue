package com.quantumff.segue.core.datastore

import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences
import com.quantumff.segue.core.datastore.UserPreferencesDataSource.Keys
import com.quantumff.segue.core.model.ReplayGainTarget

/**
 * Replaces the free ReplayGain preamp (-15..+15 dB) with the nearest normalisation target: 0 dB
 * for -18 LUFS, -5 dB for -23 LUFS. Positive preamps map to -18 LUFS and do not turn boost on,
 * since boost costs battery. A target already set wins.
 */
internal object ReplayGainPreampMigration : DataMigration<Preferences> {
    override suspend fun shouldMigrate(currentData: Preferences) = Keys.LegacyReplayGainPreamp in currentData

    override suspend fun migrate(currentData: Preferences): Preferences = currentData.toMutablePreferences().apply {
        val preamp = remove(Keys.LegacyReplayGainPreamp) ?: return@apply
        if (Keys.ReplayGainTarget !in this) this[Keys.ReplayGainTarget] = ReplayGainTarget.nearestTo(preamp).name
    }.toPreferences()

    override suspend fun cleanUp() = Unit
}
