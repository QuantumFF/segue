package com.quantumff.segue.core.datastore

import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.quantumff.segue.core.model.RecentSearches
import com.quantumff.segue.core.model.StreamingQuality
import com.quantumff.segue.core.model.UserPreferences
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class UserPreferencesDataSource @Inject constructor(private val dataStore: DataStore<Preferences>) {
    val preferences: Flow<UserPreferences> = dataStore.data.map { it.toUserPreferences() }.distinctUntilChanged()

    /** Recent search queries, newest first. */
    val recentSearches: Flow<List<String>> = dataStore.data
        .map { it.recentSearches() }
        .distinctUntilChanged()

    /**
     * Applies [transform] to the stored preferences in one transaction. Only fields it changes are
     * written, so untouched settings keep following the model defaults.
     */
    suspend fun update(transform: (UserPreferences) -> UserPreferences) {
        dataStore.edit { prefs ->
            val old = prefs.toUserPreferences()
            val new = transform(old)
            prefs.putIfChanged(Keys.ThemeMode, old.themeMode, new.themeMode) { it.name }
            prefs.putIfChanged(Keys.AccentSource, old.accentSource, new.accentSource) { it.name }
            prefs.putIfChanged(Keys.AccentPreset, old.accentPreset, new.accentPreset) { it.name }
            if (new.wifiStreamingQuality != old.wifiStreamingQuality) {
                prefs.putQuality(Keys.WifiMaxBitRate, Keys.WifiFormat, new.wifiStreamingQuality)
            }
            if (new.cellularStreamingQuality != old.cellularStreamingQuality) {
                prefs.putQuality(Keys.CellularMaxBitRate, Keys.CellularFormat, new.cellularStreamingQuality)
            }
            prefs.putIfChanged(Keys.QueueSyncEnabled, old.queueSyncEnabled, new.queueSyncEnabled)
            prefs.putIfChanged(
                Keys.AdoptRemoteQueue,
                old.adoptRemoteQueueAutomatically,
                new.adoptRemoteQueueAutomatically,
            )
            prefs.putIfChanged(Keys.ReplayGainMode, old.replayGainMode, new.replayGainMode) { it.name }
            prefs.putIfChanged(Keys.ReplayGainTarget, old.replayGainTarget, new.replayGainTarget) { it.name }
            prefs.putIfChanged(Keys.ReplayGainBoost, old.replayGainBoost, new.replayGainBoost)
            prefs.putIfChanged(Keys.StreamCacheSizeMb, old.streamCacheSizeMb, new.streamCacheSizeMb)
            prefs.putIfChanged(Keys.StopWhenPausedMinutes, old.stopWhenPausedMinutes, new.stopWhenPausedMinutes)
            prefs.putIfChanged(Keys.CheckForUpdates, old.checkForUpdates, new.checkForUpdates)
        }
    }

    /** Moves [query] to the front of the recent searches. See [RecentSearches.add]. */
    suspend fun addRecentSearch(query: String) = editRecentSearches { RecentSearches.add(it, query) }

    suspend fun removeRecentSearch(query: String) = editRecentSearches { RecentSearches.remove(it, query) }

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

    /** When the automatic update check last reached GitHub, in epoch milliseconds; null if never. */
    suspend fun lastUpdateCheckMillis(): Long? = dataStore.data.first()[Keys.LastUpdateCheck]

    suspend fun setLastUpdateCheckMillis(millis: Long) {
        dataStore.edit { it[Keys.LastUpdateCheck] = millis }
    }

    private suspend fun editRecentSearches(transform: (List<String>) -> List<String>) {
        dataStore.edit { prefs ->
            prefs[Keys.RecentSearches] = transform(prefs.recentSearches()).joinToString(RecentSearches.SEPARATOR)
        }
    }

    private fun Preferences.recentSearches(): List<String> =
        this[Keys.RecentSearches]?.split(RecentSearches.SEPARATOR)?.filter { it.isNotEmpty() }.orEmpty()

    private fun Preferences.toUserPreferences(): UserPreferences {
        val defaults = UserPreferences()
        return UserPreferences(
            themeMode = enum(Keys.ThemeMode, defaults.themeMode),
            accentSource = enum(Keys.AccentSource, defaults.accentSource),
            accentPreset = enum(Keys.AccentPreset, defaults.accentPreset),
            wifiStreamingQuality = StreamingQuality(
                maxBitRateKbps = this[Keys.WifiMaxBitRate] ?: defaults.wifiStreamingQuality.maxBitRateKbps,
                format = this[Keys.WifiFormat] ?: defaults.wifiStreamingQuality.format,
            ),
            cellularStreamingQuality = StreamingQuality(
                maxBitRateKbps = this[Keys.CellularMaxBitRate] ?: defaults.cellularStreamingQuality.maxBitRateKbps,
                format = this[Keys.CellularFormat] ?: defaults.cellularStreamingQuality.format,
            ),
            queueSyncEnabled = this[Keys.QueueSyncEnabled] ?: defaults.queueSyncEnabled,
            adoptRemoteQueueAutomatically = this[Keys.AdoptRemoteQueue] ?: defaults.adoptRemoteQueueAutomatically,
            replayGainMode = enum(Keys.ReplayGainMode, defaults.replayGainMode),
            replayGainTarget = enum(Keys.ReplayGainTarget, defaults.replayGainTarget),
            replayGainBoost = this[Keys.ReplayGainBoost] ?: defaults.replayGainBoost,
            streamCacheSizeMb = this[Keys.StreamCacheSizeMb] ?: defaults.streamCacheSizeMb,
            stopWhenPausedMinutes = this[Keys.StopWhenPausedMinutes] ?: defaults.stopWhenPausedMinutes,
            checkForUpdates = this[Keys.CheckForUpdates] ?: defaults.checkForUpdates,
        )
    }

    private fun <T> MutablePreferences.putIfChanged(key: Preferences.Key<T>, old: T, new: T) {
        if (new != old) this[key] = new
    }

    private inline fun <T, S> MutablePreferences.putIfChanged(
        key: Preferences.Key<S>,
        old: T,
        new: T,
        encode: (T) -> S,
    ) {
        if (new != old) this[key] = encode(new)
    }

    private fun MutablePreferences.putQuality(
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

    internal object Keys {
        val ThemeMode = stringPreferencesKey("theme_mode")
        val AccentSource = stringPreferencesKey("accent_source")
        val AccentPreset = stringPreferencesKey("accent_preset")
        val WifiMaxBitRate = intPreferencesKey("wifi_max_bit_rate_kbps")
        val WifiFormat = stringPreferencesKey("wifi_format")
        val CellularMaxBitRate = intPreferencesKey("cellular_max_bit_rate_kbps")
        val CellularFormat = stringPreferencesKey("cellular_format")
        val QueueSyncEnabled = booleanPreferencesKey("queue_sync_enabled")
        val AdoptRemoteQueue = booleanPreferencesKey("adopt_remote_queue_automatically")
        val DeviceId = stringPreferencesKey("device_id")
        val ReplayGainMode = stringPreferencesKey("replay_gain_mode")
        val ReplayGainTarget = stringPreferencesKey("replay_gain_target")
        val ReplayGainBoost = booleanPreferencesKey("replay_gain_boost")

        /** The free preamp ReplayGainTarget replaced; see [ReplayGainPreampMigration]. */
        val LegacyReplayGainPreamp = floatPreferencesKey("replay_gain_preamp_db")
        val StreamCacheSizeMb = intPreferencesKey("stream_cache_size_mb")
        val StopWhenPausedMinutes = intPreferencesKey("stop_when_paused_minutes")
        val RecentSearches = stringPreferencesKey("recent_searches")
        val CheckForUpdates = booleanPreferencesKey("check_for_updates")
        val LastUpdateCheck = longPreferencesKey("last_update_check_millis")
    }

    companion object {
        /** Run by the preferences DataStore before its first read. */
        val MIGRATIONS: List<DataMigration<Preferences>> = listOf(ReplayGainPreampMigration)
    }
}
