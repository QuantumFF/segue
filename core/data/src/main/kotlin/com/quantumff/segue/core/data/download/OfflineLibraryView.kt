package com.quantumff.segue.core.data.download

import androidx.paging.PagingData
import com.quantumff.segue.core.data.repository.ArtistRepository
import com.quantumff.segue.core.data.repository.LibraryRepository
import com.quantumff.segue.core.data.repository.PlaylistRepository
import com.quantumff.segue.core.model.AlbumFilter
import com.quantumff.segue.core.model.Artist
import com.quantumff.segue.core.model.DownloadStatus
import com.quantumff.segue.core.model.DownloadedLibrary
import com.quantumff.segue.core.model.PinKind
import com.quantumff.segue.core.model.Playlist
import com.quantumff.segue.core.model.Song
import com.quantumff.segue.core.model.SongAvailability
import javax.inject.Inject
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * The library as browse screens may show it (DESIGN.md §1: offline is first-class). Online
 * everything is shown and plays. Offline (no network, or the "Offline mode" switch) lists narrow
 * to downloaded music and pinned playlists, and rows of songs that are not downloaded are disabled.
 * Screens read their lists through this module instead of choosing a source per offline state.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OfflineLibraryView @Inject constructor(
    offlineMode: OfflineModeRepository,
    private val downloads: DownloadRepository,
    private val artistRepository: ArtistRepository,
    private val libraryRepository: LibraryRepository,
    private val playlistRepository: PlaylistRepository,
) {
    val isOffline: StateFlow<Boolean> = offlineMode.isOffline

    /** What is downloaded while offline, and null online: what album lists and search narrow to. */
    val downloadedWhileOffline: Flow<DownloadedLibrary?> = offlineMode.downloadedWhileOffline(downloads)

    /** Pin status of every pinned album, artist, playlist and song, for badges. */
    val pinStatuses: Flow<PinStatuses> = downloads.overview.map { overview ->
        PinStatuses(
            overview.pins.groupBy { it.kind }
                .mapValues { (_, pins) -> pins.associate { it.id to it.status }.toImmutableMap() },
        )
    }

    /** The playlists to list: offline only the pinned ones. Pins are observed only while offline. */
    val playlists: Flow<ImmutableList<Playlist>> = combine(
        playlistRepository.playlists,
        isOffline.flatMapLatest { offline ->
            if (offline) pinStatuses.map { it[PinKind.Playlist].keys } else flowOf(null)
        },
    ) { playlists, pinned ->
        if (pinned == null) playlists else playlists.filter { it.id in pinned }.toImmutableList()
    }

    /** The Artists tab: offline the artists credited on downloaded music. */
    val artists: Flow<PagingData<Artist>> = isOffline.flatMapLatest { offline ->
        if (offline) artistRepository.downloadedArtists() else artistRepository.artists()
    }

    /** How many [artists] there are; null offline, where downloaded artists are not counted. */
    val artistCount: Flow<Int?> = isOffline.flatMapLatest { offline ->
        if (offline) flowOf(null) else artistRepository.artistCount
    }

    /** The Songs tab: offline the downloaded songs. */
    val songs: Flow<PagingData<Song>> = isOffline.flatMapLatest { offline ->
        if (offline) libraryRepository.downloadedSongs() else libraryRepository.songs()
    }

    /** How many [songs] there are. */
    val songCount: Flow<Int> = isOffline.flatMapLatest { offline ->
        if (offline) libraryRepository.downloadedSongCount else libraryRepository.songCount
    }

    /** Which track rows are enabled, with the downloads behind their badges. */
    val songAvailability: Flow<SongAvailability> = combine(isOffline, downloads.songDownloads, ::SongAvailability)

    /** [filter] as the Albums list applies it, following offline mode: downloaded only while offline. */
    fun albumFilter(filter: Flow<AlbumFilter>): Flow<AlbumFilter> =
        combine(filter, isOffline, ::shownFilter).distinctUntilChanged()

    /** [filter] as the Albums list applies it right now. */
    fun albumFilter(filter: AlbumFilter): AlbumFilter = shownFilter(filter, isOffline.value)

    private fun shownFilter(filter: AlbumFilter, offline: Boolean): AlbumFilter =
        if (offline) filter.copy(downloadedOnly = true) else filter
}

/** Pin status by ID for each [PinKind]; empty for a kind with no pins. */
class PinStatuses(private val byKind: Map<PinKind, ImmutableMap<String, DownloadStatus>>) {
    operator fun get(kind: PinKind): ImmutableMap<String, DownloadStatus> = byKind[kind] ?: persistentMapOf()
}
