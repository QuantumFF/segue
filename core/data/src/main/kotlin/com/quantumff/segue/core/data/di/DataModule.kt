package com.quantumff.segue.core.data.di

import android.content.Context
import androidx.work.WorkManager
import com.quantumff.segue.core.data.image.ActiveServerCoverArtUrlResolver
import com.quantumff.segue.core.data.image.CoverArtUrlResolver
import com.quantumff.segue.core.data.repository.AccountServerRepository
import com.quantumff.segue.core.data.repository.AlbumRepository
import com.quantumff.segue.core.data.repository.AlbumShuffleRepository
import com.quantumff.segue.core.data.repository.ArtistRepository
import com.quantumff.segue.core.data.repository.CachedServerCapabilitiesRepository
import com.quantumff.segue.core.data.repository.DataStoreUserPreferencesRepository
import com.quantumff.segue.core.data.repository.DeviceClientName
import com.quantumff.segue.core.data.repository.LibraryRepository
import com.quantumff.segue.core.data.repository.LibrarySyncRepository
import com.quantumff.segue.core.data.repository.LyricsRepository
import com.quantumff.segue.core.data.repository.MediaBrowseRepository
import com.quantumff.segue.core.data.repository.MediaUrlRepository
import com.quantumff.segue.core.data.repository.OfflineFirstAlbumRepository
import com.quantumff.segue.core.data.repository.OfflineFirstArtistRepository
import com.quantumff.segue.core.data.repository.OfflineFirstLibraryRepository
import com.quantumff.segue.core.data.repository.OfflineFirstPlaylistRepository
import com.quantumff.segue.core.data.repository.OfflineFirstSearchRepository
import com.quantumff.segue.core.data.repository.OptimisticStarRatingRepository
import com.quantumff.segue.core.data.repository.PendingScrobbleRepository
import com.quantumff.segue.core.data.repository.PlayQueueRemoteRepository
import com.quantumff.segue.core.data.repository.PlaylistRepository
import com.quantumff.segue.core.data.repository.PreferencesDeviceClientName
import com.quantumff.segue.core.data.repository.QueueSnapshotRepository
import com.quantumff.segue.core.data.repository.RoomAlbumShuffleRepository
import com.quantumff.segue.core.data.repository.RoomMediaBrowseRepository
import com.quantumff.segue.core.data.repository.RoomQueueSnapshotRepository
import com.quantumff.segue.core.data.repository.ScrobbleRepository
import com.quantumff.segue.core.data.repository.SearchRepository
import com.quantumff.segue.core.data.repository.ServerCapabilitiesRepository
import com.quantumff.segue.core.data.repository.ServerRepository
import com.quantumff.segue.core.data.repository.StarRatingRepository
import com.quantumff.segue.core.data.repository.SubsonicLyricsRepository
import com.quantumff.segue.core.data.repository.SubsonicMediaUrlRepository
import com.quantumff.segue.core.data.repository.SubsonicPlayQueueRemoteRepository
import com.quantumff.segue.core.data.repository.UserPreferencesRepository
import com.quantumff.segue.core.data.repository.WorkManagerLibrarySyncRepository
import com.quantumff.segue.core.data.session.AccountClientProvider
import com.quantumff.segue.core.data.session.SubsonicClientCreator
import com.quantumff.segue.core.data.session.SubsonicClientProvider
import com.quantumff.segue.core.data.sync.ScrobbleScheduler
import com.quantumff.segue.core.data.sync.SyncScheduler
import com.quantumff.segue.core.data.sync.WorkManagerScrobbleScheduler
import com.quantumff.segue.core.data.sync.WorkManagerSyncScheduler
import com.quantumff.segue.core.data.util.Clock
import com.quantumff.segue.core.data.util.SystemClock
import com.quantumff.segue.core.network.SubsonicClientFactory
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal interface DataModule {
    @Binds
    fun serverRepository(impl: AccountServerRepository): ServerRepository

    @Binds
    fun serverCapabilitiesRepository(impl: CachedServerCapabilitiesRepository): ServerCapabilitiesRepository

    @Binds
    fun librarySyncRepository(impl: WorkManagerLibrarySyncRepository): LibrarySyncRepository

    @Binds
    fun libraryRepository(impl: OfflineFirstLibraryRepository): LibraryRepository

    @Binds
    fun albumRepository(impl: OfflineFirstAlbumRepository): AlbumRepository

    @Binds
    fun artistRepository(impl: OfflineFirstArtistRepository): ArtistRepository

    @Binds
    fun playlistRepository(impl: OfflineFirstPlaylistRepository): PlaylistRepository

    @Binds
    fun mediaBrowseRepository(impl: RoomMediaBrowseRepository): MediaBrowseRepository

    @Binds
    fun searchRepository(impl: OfflineFirstSearchRepository): SearchRepository

    @Binds
    fun starRatingRepository(impl: OptimisticStarRatingRepository): StarRatingRepository

    @Binds
    fun lyricsRepository(impl: SubsonicLyricsRepository): LyricsRepository

    @Binds
    fun playQueueRemoteRepository(impl: SubsonicPlayQueueRemoteRepository): PlayQueueRemoteRepository

    @Binds
    fun queueSnapshotRepository(impl: RoomQueueSnapshotRepository): QueueSnapshotRepository

    @Binds
    fun userPreferencesRepository(impl: DataStoreUserPreferencesRepository): UserPreferencesRepository

    @Binds
    fun mediaUrlRepository(impl: SubsonicMediaUrlRepository): MediaUrlRepository

    @Binds
    fun scrobbleRepository(impl: PendingScrobbleRepository): ScrobbleRepository

    @Binds
    fun albumShuffleRepository(impl: RoomAlbumShuffleRepository): AlbumShuffleRepository

    @Binds
    fun scrobbleScheduler(impl: WorkManagerScrobbleScheduler): ScrobbleScheduler

    @Binds
    fun coverArtUrlResolver(impl: ActiveServerCoverArtUrlResolver): CoverArtUrlResolver

    @Binds
    fun clientProvider(impl: AccountClientProvider): SubsonicClientProvider

    @Binds
    fun syncScheduler(impl: WorkManagerSyncScheduler): SyncScheduler

    @Binds
    fun deviceClientName(impl: PreferencesDeviceClientName): DeviceClientName

    @Binds
    fun clock(impl: SystemClock): Clock

    companion object {
        @Provides
        fun workManager(@ApplicationContext context: Context): WorkManager = WorkManager.getInstance(context)

        @Provides
        fun clientCreator(factory: SubsonicClientFactory): SubsonicClientCreator =
            SubsonicClientCreator(factory::create)
    }
}
