package com.quantumff.segue.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class UserPreferencesDataSource @Inject constructor(private val dataStore: DataStore<Preferences>) {
    val preferences: Flow<UserPreferences> = dataStore.data.map { prefs ->
        UserPreferences(
            adoptRemoteQueueAutomatically = prefs[Keys.AdoptRemoteQueue] ?: false,
        )
    }

    suspend fun setAdoptRemoteQueueAutomatically(enabled: Boolean) {
        dataStore.edit { it[Keys.AdoptRemoteQueue] = enabled }
    }

    private object Keys {
        val AdoptRemoteQueue = booleanPreferencesKey("adopt_remote_queue_automatically")
    }
}
