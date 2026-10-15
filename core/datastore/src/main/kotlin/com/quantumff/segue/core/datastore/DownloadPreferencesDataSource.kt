package com.quantumff.segue.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.quantumff.segue.core.model.DownloadPreferences
import com.quantumff.segue.core.model.StorageLimit
import com.quantumff.segue.core.model.StreamingQuality
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Download and offline settings, kept in the same preferences file as [UserPreferencesDataSource]. */
class DownloadPreferencesDataSource @Inject constructor(private val dataStore: DataStore<Preferences>) {
    val preferences: Flow<DownloadPreferences> = dataStore.data.map { prefs ->
        val defaults = DownloadPreferences()
        DownloadPreferences(
            quality = StreamingQuality(
                maxBitRateKbps = prefs[Keys.MaxBitRate] ?: defaults.quality.maxBitRateKbps,
                format = prefs[Keys.Format] ?: defaults.quality.format,
            ),
            wifiOnly = prefs[Keys.WifiOnly] ?: defaults.wifiOnly,
            chargingOnly = prefs[Keys.ChargingOnly] ?: defaults.chargingOnly,
            forceOffline = prefs[Keys.ForceOffline] ?: defaults.forceOffline,
            storageLimit = prefs[Keys.StorageLimit]
                ?.let { name -> StorageLimit.entries.firstOrNull { it.name == name } }
                ?: defaults.storageLimit,
        )
    }.distinctUntilChanged()

    suspend fun setQuality(quality: StreamingQuality) {
        dataStore.edit { prefs ->
            prefs[Keys.MaxBitRate] = quality.maxBitRateKbps
            val format = quality.format
            if (format == null) prefs.remove(Keys.Format) else prefs[Keys.Format] = format
        }
    }

    suspend fun setWifiOnly(enabled: Boolean) {
        dataStore.edit { it[Keys.WifiOnly] = enabled }
    }

    suspend fun setChargingOnly(enabled: Boolean) {
        dataStore.edit { it[Keys.ChargingOnly] = enabled }
    }

    suspend fun setForceOffline(enabled: Boolean) {
        dataStore.edit { it[Keys.ForceOffline] = enabled }
    }

    suspend fun setStorageLimit(limit: StorageLimit) {
        dataStore.edit { it[Keys.StorageLimit] = limit.name }
    }

    private object Keys {
        val MaxBitRate = intPreferencesKey("download_max_bit_rate_kbps")
        val Format = stringPreferencesKey("download_format")
        val WifiOnly = booleanPreferencesKey("download_wifi_only")
        val ChargingOnly = booleanPreferencesKey("download_charging_only")
        val ForceOffline = booleanPreferencesKey("force_offline")
        val StorageLimit = stringPreferencesKey("download_storage_limit")
    }
}
