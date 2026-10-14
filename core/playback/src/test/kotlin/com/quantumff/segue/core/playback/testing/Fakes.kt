package com.quantumff.segue.core.playback.testing

import com.quantumff.segue.core.data.repository.PlayQueueRemoteRepository
import com.quantumff.segue.core.data.repository.QueueSnapshotRepository
import com.quantumff.segue.core.data.repository.UserPreferencesRepository
import com.quantumff.segue.core.model.AccentPreset
import com.quantumff.segue.core.model.AccentSource
import com.quantumff.segue.core.model.QueueSnapshot
import com.quantumff.segue.core.model.RemotePlayQueue
import com.quantumff.segue.core.model.ReplayGainMode
import com.quantumff.segue.core.model.ShuffleMode
import com.quantumff.segue.core.model.StreamingQuality
import com.quantumff.segue.core.model.ThemeMode
import com.quantumff.segue.core.model.UserPreferences
import com.quantumff.segue.core.playback.sync.QueueState
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class FakePlayQueueRemoteRepository(private val clientName: String) : PlayQueueRemoteRepository {
    var queue: RemotePlayQueue? = null
    var fetchError: Exception? = null
    var saveError: Exception? = null
    var fetches = 0
    val saves = mutableListOf<QueueState>()

    override suspend fun fetch(): RemotePlayQueue? {
        fetches++
        fetchError?.let { throw it }
        return queue
    }

    override suspend fun save(songIds: List<String>, currentIndex: Int, positionMillis: Long) {
        saveError?.let { throw it }
        saves += QueueState(songIds, currentIndex, positionMillis)
    }

    override suspend fun deviceClientName(): String = clientName
}

/** Snapshots saved through [save] get `updatedAt` 0, so any timestamped remote queue is newer. */
class FakeQueueSnapshotRepository : QueueSnapshotRepository {
    override val snapshot = MutableStateFlow<QueueSnapshot?>(null)
    val saves = mutableListOf<QueueState>()

    override suspend fun save(songIds: List<String>, currentIndex: Int, positionMillis: Long) {
        saves += QueueState(songIds, currentIndex, positionMillis)
        snapshot.value = QueueSnapshot(songIds.toImmutableList(), currentIndex, positionMillis, updatedAt = 0)
    }
}

class FakeUserPreferencesRepository(initial: UserPreferences = UserPreferences()) : UserPreferencesRepository {
    override val preferences = MutableStateFlow(initial)

    fun update(block: (UserPreferences) -> UserPreferences) = preferences.update(block)

    override suspend fun setThemeMode(mode: ThemeMode) = update { it.copy(themeMode = mode) }

    override suspend fun setAccentSource(source: AccentSource) = update { it.copy(accentSource = source) }

    override suspend fun setAccentPreset(preset: AccentPreset) = update { it.copy(accentPreset = preset) }

    override suspend fun setDefaultShuffleMode(mode: ShuffleMode) = update { it.copy(defaultShuffleMode = mode) }

    override suspend fun setWifiStreamingQuality(quality: StreamingQuality) =
        update { it.copy(wifiStreamingQuality = quality) }

    override suspend fun setCellularStreamingQuality(quality: StreamingQuality) =
        update { it.copy(cellularStreamingQuality = quality) }

    override suspend fun setQueueSyncEnabled(enabled: Boolean) = update { it.copy(queueSyncEnabled = enabled) }

    override suspend fun setAdoptRemoteQueueAutomatically(enabled: Boolean) =
        update { it.copy(adoptRemoteQueueAutomatically = enabled) }

    override suspend fun setReplayGainMode(mode: ReplayGainMode) = update { it.copy(replayGainMode = mode) }

    override suspend fun setReplayGainPreampDb(preampDb: Float) = update { it.copy(replayGainPreampDb = preampDb) }

    override suspend fun setStreamCacheSizeMb(sizeMb: Int) = update { it.copy(streamCacheSizeMb = sizeMb) }

    override suspend fun setStopWhenPausedMinutes(minutes: Int) = update { it.copy(stopWhenPausedMinutes = minutes) }
}
