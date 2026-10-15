package com.quantumff.segue.feature.library

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.data.RemoteException
import com.quantumff.segue.core.designsystem.component.MediaAction
import com.quantumff.segue.core.model.AlbumFilter
import com.quantumff.segue.core.model.AlbumShuffleSource
import com.quantumff.segue.core.model.AlbumSortOrder
import com.quantumff.segue.core.model.MediaKind
import com.quantumff.segue.core.model.PlayActions
import com.quantumff.segue.core.model.Song
import com.quantumff.segue.core.testing.MainDispatcherRule
import com.quantumff.segue.core.testing.data.TestData
import com.quantumff.segue.core.testing.repository.FakeAlbumRepository
import com.quantumff.segue.core.testing.repository.FakeArtistRepository
import com.quantumff.segue.core.testing.repository.FakeLibraryRepository
import com.quantumff.segue.core.testing.repository.FakeLibrarySyncRepository
import com.quantumff.segue.core.testing.repository.FakePlaylistRepository
import com.quantumff.segue.core.testing.repository.FakeServerRepository
import com.quantumff.segue.core.testing.repository.FakeStarRatingRepository
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class LibraryViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val albums = FakeAlbumRepository()
    private val artists = FakeArtistRepository()
    private val library = FakeLibraryRepository()
    private val playlists = FakePlaylistRepository()
    private val sync = FakeLibrarySyncRepository()
    private val stars = FakeStarRatingRepository()
    private val servers = FakeServerRepository()
    private val savedState = SavedStateHandle()
    private val play = RecordingPlayActions()
    private lateinit var viewModel: LibraryViewModel

    @Before
    fun setUp() {
        albums.albumsState.value = listOf(
            TestData.album("1", genre = "Rock", year = 2007, starredAt = 1L),
            TestData.album("2", genre = "Jazz", year = 2001),
            TestData.album("3", genre = "Rock", year = 2001),
        )
        artists.artistsState.value = listOf(TestData.artist("artist-1"))
        library.songsState.value = listOf(TestData.song("s1"), TestData.song("s2"))
        library.genresState.value = listOf(TestData.genre("Jazz"), TestData.genre("Rock"))
        servers.serversState.value = listOf(TestData.server)
        servers.activeId.value = TestData.server.id
        viewModel = LibraryViewModel(savedState, albums, artists, library, playlists, sync, stars, servers)
    }

    @Test
    fun stateCarriesCountsServerNameAndBrowseLists() = runTest {
        viewModel.uiState.test {
            val state = expectMostRecentItem()
            assertThat(state.albumCount).isEqualTo(3)
            assertThat(state.artistCount).isEqualTo(1)
            assertThat(state.songCount).isEqualTo(2)
            assertThat(state.serverName).isEqualTo(TestData.server.name)
            assertThat(state.years).containsExactly(2007, 2001).inOrder()
            assertThat(state.genres.map { it.name }).containsExactly("Jazz", "Rock")
            assertThat(state.playlists).isEmpty()
        }
    }

    @Test
    fun favoritesFilterNarrowsTheCount() = runTest {
        viewModel.uiState.test {
            viewModel.toggleFavoritesFilter()
            val state = expectMostRecentItem()
            assertThat(state.filter).isEqualTo(AlbumFilter(starredOnly = true))
            assertThat(state.albumCount).isEqualTo(1)
        }
    }

    @Test
    fun genreAndYearFiltersCombineAndClear() = runTest {
        viewModel.uiState.test {
            viewModel.setGenreFilter("Rock")
            viewModel.setYearFilter(2001)
            assertThat(expectMostRecentItem().albumCount).isEqualTo(1)

            viewModel.clearFilters()
            val cleared = expectMostRecentItem()
            assertThat(cleared.filter.isActive).isFalse()
            assertThat(cleared.albumCount).isEqualTo(3)
        }
    }

    @Test
    fun sortTabAndViewModeSurviveInSavedState() = runTest {
        viewModel.uiState.test {
            viewModel.setSort(AlbumSortOrder.Year)
            viewModel.selectTab(LibraryTab.Genres)
            viewModel.toggleViewMode()
            val state = expectMostRecentItem()
            assertThat(state.sort).isEqualTo(AlbumSortOrder.Year)
            assertThat(state.tab).isEqualTo(LibraryTab.Genres)
            assertThat(state.viewMode).isEqualTo(LibraryViewMode.List)
        }
        assertThat(savedState.get<LibraryTab>(LibraryViewModel.KEY_TAB)).isEqualTo(LibraryTab.Genres)
    }

    @Test
    fun shuffleAllPlaysRandomSongsInServerOrder() = runTest {
        library.randomSongs = listOf(TestData.song("r1"), TestData.song("r2"))

        viewModel.shuffleAll(play.actions)

        assertThat(play.played.single()).isEqualTo(Triple(listOf("r1", "r2"), 0, false))
    }

    @Test
    fun albumPlayAndShuffleLoadItsSongs() = runTest {
        albums.songsByAlbum.value = mapOf("1" to listOf(TestData.song("a"), TestData.song("b")))
        val album = albums.albumsState.value.first()

        viewModel.onAlbumAction(album, MediaAction.Play, play.actions) {}
        viewModel.onAlbumAction(album, MediaAction.Shuffle, play.actions) {}

        assertThat(play.played).containsExactly(
            Triple(listOf("a", "b"), 0, false),
            Triple(listOf("a", "b"), 0, true),
        ).inOrder()
    }

    @Test
    fun albumAddToPlaylistHandsOverSongIds() = runTest {
        albums.songsByAlbum.value = mapOf("1" to listOf(TestData.song("a"), TestData.song("b")))
        var added: List<String>? = null

        viewModel.onAlbumAction(albums.albumsState.value.first(), MediaAction.AddToPlaylist, play.actions) {
            added = it
        }

        assertThat(added).containsExactly("a", "b").inOrder()
    }

    @Test
    fun albumLoadFailureShowsMessageUntilShown() = runTest {
        albums.refreshError = RemoteException.Unreachable(IOException("offline"))
        viewModel.uiState.test {
            viewModel.onAlbumAction(albums.albumsState.value.first(), MediaAction.Play, play.actions) {}
            assertThat(expectMostRecentItem().message).isEqualTo(LibraryMessage.LoadFailed)
            assertThat(play.played).isEmpty()

            viewModel.onMessageDismiss()
            assertThat(expectMostRecentItem().message).isNull()
        }
    }

    @Test
    fun favoriteFailureShowsMessage() = runTest {
        stars.error = RemoteException.Unreachable(IOException("offline"))
        viewModel.uiState.test {
            viewModel.onAlbumAction(albums.albumsState.value[1], MediaAction.Favorite, play.actions) {}
            assertThat(expectMostRecentItem().message).isEqualTo(LibraryMessage.FavoriteFailed)
        }
    }

    @Test
    fun favoriteStarsTheAlbum() = runTest {
        viewModel.onAlbumAction(albums.albumsState.value[1], MediaAction.Favorite, play.actions) {}

        assertThat(stars.starred[MediaKind.Album to "2"]).isTrue()
    }

    @Test
    fun shuffleArtistAlbumsUsesTheAlbumArtist() = runTest {
        viewModel.onAlbumAction(albums.albumsState.value.first(), MediaAction.ShuffleArtistAlbums, play.actions) {}
        viewModel.onArtistAction(TestData.artist("x"), MediaAction.ShuffleAlbums, play.actions)

        assertThat(play.shuffledAlbums).containsExactly(
            AlbumShuffleSource.Artist("artist-1"),
            AlbumShuffleSource.Artist("x"),
        ).inOrder()
    }

    @Test
    fun songMenuRoutesQueueActions() = runTest {
        val song = TestData.song("s1")

        viewModel.onSongAction(song, MediaAction.PlayNext, play.actions) {}
        viewModel.onSongAction(song, MediaAction.AddToQueue, play.actions) {}
        viewModel.onSongAction(song, MediaAction.InstantMix, play.actions) {}

        assertThat(play.playedNext).containsExactly(listOf("s1"))
        assertThat(play.queued).containsExactly(listOf("s1"))
        assertThat(play.mixes).containsExactly("s1")
    }

    @Test
    fun refreshRequestsAnIncrementalSync() = runTest {
        viewModel.refresh()

        assertThat(sync.requests).containsExactly(false)
    }
}

/** Records every play intent as song IDs. */
internal class RecordingPlayActions {
    val played = mutableListOf<Triple<List<String>, Int, Boolean>>()
    val shuffledAlbums = mutableListOf<AlbumShuffleSource>()
    val playedNext = mutableListOf<List<String>>()
    val queued = mutableListOf<List<String>>()
    val mixes = mutableListOf<String>()

    val actions = PlayActions(
        onPlaySongs = { songs, start, shuffle -> played += Triple(songs.ids(), start, shuffle) },
        onShuffleAlbums = { shuffledAlbums += it },
        onPlayNext = { playedNext += it.ids() },
        onAddToQueue = { queued += it.ids() },
        onStartInstantMix = { mixes += it.id },
    )

    private fun List<Song>.ids() = map { it.id }
}
