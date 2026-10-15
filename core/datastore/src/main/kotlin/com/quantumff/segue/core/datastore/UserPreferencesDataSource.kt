package com.quantumff.segue.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.quantumff.segue.core.model.AccentPreset
import com.quantumff.segue.core.model.AccentSource
import com.quantumff.segue.core.model.ReplayGainMode
import com.quantumff.segue.core.model.ShuffleMode
import com.quantumff.segue.core.model.StreamingQuality
import com.quantumff.segue.core.model.ThemeMode
import com.quantumff.segue.core.model.UserPreferences
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class UserPreferencesDataSource @Inject constructor(private val dataStore: DataStore<Preferences>) {
    val preferences: Flow<UserPreferences> = dataStore.data.map { prefs ->
        val defaults = UserPreferences()
        UserPreferences(
            themeMode = prefs.enum(Keys.ThemeMode, defaults.themeMode),
            accentSource = prefs.enum(Keys.AccentSource, defaults.accentSource),
            accentPreset = prefs.enum(Keys.AccentPreset, defaults.accentPreset),
            defaultShuffleMode = prefs.enum(Keys.DefaultShuffleMode, defaults.defaultShuffleMode),
            wifiStreamingQuality = StreamingQuality(
                maxBitRateKbps = prefs[Keys.WifiMaxBitRate] ?: defaults.wifiStreamingQuality.maxBitRateKbps,
                format = prefs[Keys.WifiFormat] ?: defaults.wifiStreamingQuality.format,
            ),
            cellularStreamingQuality = StreamingQuality(
                maxBitRateKbps = prefs[Keys.CellularMaxBitRate] ?: defaults.cellularStreamingQuality.maxBitRateKbps,
                format = prefs[Keys.CellularFormat] ?: defaults.cellularStreamingQuality.format,
            ),
            queueSyncEnabled = prefs[Keys.QueueSyncEnabled] ?: defaults.queueSyncEnabled,
            adoptRemoteQueueAutomatically = prefs[Keys.AdoptRemoteQueue] ?: defaults.adoptRemoteQueueAutomatically,
            replayGainMode = prefs.enum(Keys.ReplayGainMode, defaults.replayGainMode),
            replayGainPreampDb = prefs[Keys.ReplayGainPreamp] ?: defaults.replayGainPreampDb,
            streamCacheSizeMb = prefs[Keys.StreamCacheSizeMb] ?: defaults.streamCacheSizeMb,
            stopWhenPausedMinutes = prefs[Keys.StopWhenPausedMinutes] ?: defaults.stopWhenPausedMinutes,
        )
    }.distinctUntilChanged()

    /** Recent search queries, newest first. */
    val recentSearches: Flow<List<String>> = dataStore.data
        .map { prefs -> prefs[Keys.RecentSearches]?.split(RECENT_SEPARATOR)?.filter { it.isNotEmpty() }.orEmpty() }
        .distinctUntilChanged()

    suspend fun setThemeMode(mode: ThemeMode) = set(Keys.ThemeMode, mode.name)

    suspend fun setAccentSource(source: AccentSource) = set(Keys.AccentSource, source.name)

    suspend fun setAccentPreset(preset: AccentPreset) = set(Keys.AccentPreset, preset.name)

    suspend fun setDefaultShuffleMode(mode: ShuffleMode) = set(Keys.DefaultShuffleMode, mode.name)

    suspend fun setWifiStreamingQuality(quality: StreamingQuality) {
        dataStore.edit { it.putQuality(Keys.WifiMaxBitRate, Keys.WifiFormat, quality) }
    }

    suspend fun setCellularStreamingQuality(quality: StreamingQuality) {
        dataStore.edit { it.putQuality(Keys.CellularMaxBitRate, Keys.CellularFormat, quality) }
    }

    suspend fun setQueueSyncEnabled(enabled: Boolean) = set(Keys.QueueSyncEnabled, enabled)

    suspend fun setAdoptRemoteQueueAutomatically(enabled: Boolean) = set(Keys.AdoptRemoteQueue, enabled)

    suspend fun setReplayGainMode(mode: ReplayGainMode) = set(Keys.ReplayGainMode, mode.name)

    suspend fun setReplayGainPreampDb(preampDb: Float) = set(Keys.ReplayGainPreamp, preampDb)

    suspend fun setStreamCacheSizeMb(sizeMb: Int) = set(Keys.StreamCacheSizeMb, sizeMb)

    suspend fun setStopWhenPausedMinutes(minutes: Int) = set(Keys.StopWhenPausedMinutes, minutes)

    /** Moves [query] to the front of the recent searches, keeping at most [MAX_RECENT_SEARCHES]. */
    suspend fun addRecentSearch(query: String) {
        val clean = query.trim().replace(RECENT_SEPARATOR, " ")
        if (clean.isEmpty()) return
        dataStore.edit { prefs ->
            val current = prefs[Keys.RecentSearches]?.split(RECENT_SEPARATOR).orEmpty()
            val updated = (listOf(clean) + current.filterNot { it.equals(clean, ignoreCase = true) })
                .filter { it.isNotEmpty() }
                .take(MAX_RECENT_SEARCHES)
            prefs[Keys.RecentSearches] = updated.joinToString(RECENT_SEPARATOR)
        }
    }

    suspend fun removeRecentSearch(query: String) {
        dataStore.edit { prefs ->
            val current = prefs[Keys.RecentSearches]?.split(RECENT_SEPARATOR).orEmpty()
            prefs[Keys.RecentSearches] = current.filterNot { it == query }.joinToString(RECENT_SEPARATOR)
        }
    }

    /**
     * A random ID for this install, created on first call and then stable. Queue sync uses it to
     * tell this device's saves apart from other clients'.
     */
    suspend fun deviceId(): String {
        var id: String? = null
        dataStore.edit { prefs ->
            id = prefs[Keys.DeviceId] ?: UUID.randomUUID().toString().also { prefs[Keys.DeviceId] = it }
        }
        return checkNotNull(id)
    }

    private suspend fun <T> set(key: Preferences.Key<T>, value: T) {
        dataStore.edit { it[key] = value }
    }

    private fun androidx.datastore.preferences.core.MutablePreferences.putQuality(
        bitRateKey: Preferences.Key<Int>,
        formatKey: Preferences.Key<String>,
        quality: StreamingQuality,
    ) {
        this[bitRateKey] = quality.maxBitRateKbps
        val format = quality.format
        if (format == null) remove(formatKey) else this[formatKey] = format
    }

    private inline fun <reified E : Enum<E>> Preferences.enum(key: Preferences.Key<String>, default: E): E =
        this[key]?.let { stored -> enumValues<E>().firstOrNull { it.name == stored } } ?: default

    private object Keys {
        val ThemeMode = stringPreferencesKey("theme_mode")
        val AccentSource = stringPreferencesKey("accent_source")
        val AccentPreset = stringPreferencesKey("accent_preset")
        val DefaultShuffleMode = stringPreferencesKey("default_shuffle_mode")
        val WifiMaxBitRate = intPreferencesKey("wifi_max_bit_rate_kbps")
        val WifiFormat = stringPreferencesKey("wifi_format")
        val CellularMaxBitRate = intPreferencesKey("cellular_max_bit_rate_kbps")
        val CellularFormat = stringPreferencesKey("cellular_format")
        val QueueSyncEnabled = booleanPreferencesKey("queue_sync_enabled")
        val AdoptRemoteQueue = booleanPreferencesKey("adopt_remote_queue_automatically")
        val DeviceId = stringPreferencesKey("device_id")
        val ReplayGainMode = stringPreferencesKey("replay_gain_mode")
        val ReplayGainPreamp = floatPreferencesKey("replay_gain_preamp_db")
        val StreamCacheSizeMb = intPreferencesKey("stream_cache_size_mb")
        val StopWhenPausedMinutes = intPreferencesKey("stop_when_paused_minutes")
        val RecentSearches = stringPreferencesKey("recent_searches")
    }

    private companion object {
        const val RECENT_SEPARATOR = "\n"
        const val MAX_RECENT_SEARCHES = 10
    }
}
