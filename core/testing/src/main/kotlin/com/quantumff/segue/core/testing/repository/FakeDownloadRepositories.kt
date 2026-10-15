package com.quantumff.segue.core.testing.repository

import com.quantumff.segue.core.data.download.DownloadRepository
import com.quantumff.segue.core.data.download.OfflineModeRepository
import com.quantumff.segue.core.data.download.PinTarget
import com.quantumff.segue.core.model.DownloadOverview
import com.quantumff.segue.core.model.DownloadPreferences
import com.quantumff.segue.core.model.DownloadStatus
import com.quantumff.segue.core.model.PinKind
import com.quantumff.segue.core.model.SongDownload
import com.quantumff.segue.core.model.StreamingQuality
import java.io.File
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class FakeDownloadRepository : DownloadRepository {
    override val preferences = MutableStateFlow(DownloadPreferences())
    override val overview = MutableStateFlow(DownloadOverview())
    override val songDownloads = MutableStateFlow<ImmutableMap<String, SongDownload>>(persistentMapOf())
    override val downloadedSongIds = MutableStateFlow<ImmutableSet<String>>(persistentSetOf())

    /** Pins by kind and ID, with the status [pinStatus] reports. */
    val pins = MutableStateFlow<Map<Pair<PinKind, String>, DownloadStatus>>(emptyMap())
    val files = mutableMapOf<String, File>()
    val paused = mutableListOf<Pair<PinKind, String>>()
    val resumed = mutableListOf<Pair<PinKind, String>>()
    var removedAll = false

    override fun pinStatus(kind: PinKind, id: String): Flow<DownloadStatus?> = pins.map { it[kind to id] }

    override suspend fun pin(target: PinTarget) = pins.update { it + ((target.kind to target.id) to DownloadStatus.Queued) }

    override suspend fun unpin(kind: PinKind, id: String) = pins.update { it - (kind to id) }

    override suspend fun pause(kind: PinKind, id: String) {
        paused += kind to id
    }

    override suspend fun resume(kind: PinKind, id: String) {
        resumed += kind to id
    }

    override suspend fun removeAll() {
        removedAll = true
        pins.value = emptyMap()
    }

    override suspend fun localFile(songId: String): File? = files[songId]

    override suspend fun setQuality(quality: StreamingQuality) = preferences.update { it.copy(quality = quality) }

    override suspend fun setWifiOnly(enabled: Boolean) = preferences.update { it.copy(wifiOnly = enabled) }

    override suspend fun setChargingOnly(enabled: Boolean) = preferences.update { it.copy(chargingOnly = enabled) }

    override suspend fun setForceOffline(enabled: Boolean) = preferences.update { it.copy(forceOffline = enabled) }
}

class FakeOfflineModeRepository(offline: Boolean = false) : OfflineModeRepository {
    override val isOffline = MutableStateFlow(offline)
}
