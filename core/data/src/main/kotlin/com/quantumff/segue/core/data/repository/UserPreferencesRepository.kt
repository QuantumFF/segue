package com.quantumff.segue.core.data.repository

import com.quantumff.segue.core.datastore.UserPreferencesDataSource
import com.quantumff.segue.core.model.AccentPreset
import com.quantumff.segue.core.model.AccentSource
import com.quantumff.segue.core.model.ReplayGainMode
import com.quantumff.segue.core.model.ShuffleMode
import com.quantumff.segue.core.model.StreamingQuality
import com.quantumff.segue.core.model.ThemeMode
import com.quantumff.segue.core.model.UserPreferences
import javax.inject.Inject
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** App settings, so features do not depend on core:datastore. */
interface UserPreferencesRepository {
    val preferences: Flow<UserPreferences>

    suspend fun setThemeMode(mode: ThemeMode)

    suspend fun setAccentSource(source: AccentSource)

    suspend fun setAccentPreset(preset: AccentPreset)

    suspend fun setDefaultShuffleMode(mode: ShuffleMode)

    suspend fun setWifiStreamingQuality(quality: StreamingQuality)

    suspend fun setCellularStreamingQuality(quality: StreamingQuality)

    suspend fun setQueueSyncEnabled(enabled: Boolean)

    suspend fun setAdoptRemoteQueueAutomatically(enabled: Boolean)

    suspend fun setReplayGainMode(mode: ReplayGainMode)

    /** Clamped to [UserPreferences.REPLAY_GAIN_PREAMP_RANGE]. */
    suspend fun setReplayGainPreampDb(db: Float)

    suspend fun setAudioCacheSizeMb(megabytes: Int)

    /** Recent search queries, newest first. */
    val recentSearches: Flow<ImmutableList<String>>

    suspend fun addRecentSearch(query: String)

    suspend fun removeRecentSearch(query: String)
}

internal class DataStoreUserPreferencesRepository @Inject constructor(private val source: UserPreferencesDataSource) :
    UserPreferencesRepository {
    override val preferences: Flow<UserPreferences> = source.preferences

    override suspend fun setThemeMode(mode: ThemeMode) = source.setThemeMode(mode)

    override suspend fun setAccentSource(source: AccentSource) = this.source.setAccentSource(source)

    override suspend fun setAccentPreset(preset: AccentPreset) = source.setAccentPreset(preset)

    override suspend fun setDefaultShuffleMode(mode: ShuffleMode) = source.setDefaultShuffleMode(mode)

    override suspend fun setWifiStreamingQuality(quality: StreamingQuality) = source.setWifiStreamingQuality(quality)

    override suspend fun setCellularStreamingQuality(quality: StreamingQuality) =
        source.setCellularStreamingQuality(quality)

    override suspend fun setQueueSyncEnabled(enabled: Boolean) = source.setQueueSyncEnabled(enabled)

    override suspend fun setAdoptRemoteQueueAutomatically(enabled: Boolean) =
        source.setAdoptRemoteQueueAutomatically(enabled)

    override suspend fun setReplayGainMode(mode: ReplayGainMode) = source.setReplayGainMode(mode)

    override suspend fun setReplayGainPreampDb(db: Float) =
        source.setReplayGainPreampDb(db.coerceIn(UserPreferences.REPLAY_GAIN_PREAMP_RANGE))

    override suspend fun setAudioCacheSizeMb(megabytes: Int) = source.setAudioCacheSizeMb(megabytes)

    override val recentSearches: Flow<ImmutableList<String>> = source.recentSearches.map { it.toImmutableList() }

    override suspend fun addRecentSearch(query: String) = source.addRecentSearch(query)

    override suspend fun removeRecentSearch(query: String) = source.removeRecentSearch(query)
}
