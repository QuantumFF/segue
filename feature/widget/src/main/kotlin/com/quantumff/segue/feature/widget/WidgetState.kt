package com.quantumff.segue.feature.widget

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.quantumff.segue.core.model.ShuffleMode
import com.quantumff.segue.core.playback.nowplaying.NowPlayingSnapshot
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** The last now-playing snapshot, kept on disk so widgets render it after the service or process is gone. */
@Singleton
class WidgetStateStore @Inject constructor(@ApplicationContext context: Context) {
    private val store: DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile(FILE_NAME) }

    val snapshots: Flow<NowPlayingSnapshot> = store.data.map(NowPlayingPreferences::read).distinctUntilChanged()

    suspend fun read(): NowPlayingSnapshot = snapshots.first()

    suspend fun write(snapshot: NowPlayingSnapshot) {
        store.edit { NowPlayingPreferences.write(it, snapshot) }
    }

    private companion object {
        const val FILE_NAME = "widget_now_playing"
    }
}

/** The preferences codec for [NowPlayingSnapshot]. */
internal object NowPlayingPreferences {
    val SONG_ID = stringPreferencesKey("songId")
    val TITLE = stringPreferencesKey("title")
    val ARTIST = stringPreferencesKey("artist")
    val COVER_ART_ID = stringPreferencesKey("coverArtId")
    val PLAYING = booleanPreferencesKey("playing")
    val SHUFFLE = stringPreferencesKey("shuffle")

    fun read(prefs: Preferences) = NowPlayingSnapshot(
        songId = prefs[SONG_ID],
        title = prefs[TITLE],
        artist = prefs[ARTIST],
        coverArtId = prefs[COVER_ART_ID],
        isPlaying = prefs[PLAYING] ?: false,
        shuffleMode = ShuffleMode.entries.firstOrNull { it.name == prefs[SHUFFLE] } ?: ShuffleMode.Off,
    )

    fun write(prefs: MutablePreferences, snapshot: NowPlayingSnapshot) {
        prefs.set(SONG_ID, snapshot.songId)
        prefs.set(TITLE, snapshot.title)
        prefs.set(ARTIST, snapshot.artist)
        prefs.set(COVER_ART_ID, snapshot.coverArtId)
        prefs[PLAYING] = snapshot.isPlaying
        prefs[SHUFFLE] = snapshot.shuffleMode.name
    }

    private fun <T> MutablePreferences.set(key: Preferences.Key<T>, value: T?) {
        if (value == null) remove(key) else this[key] = value
    }
}
