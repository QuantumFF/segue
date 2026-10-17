package com.quantumff.segue.feature.search

import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.data.RemoteException
import com.quantumff.segue.core.data.play.MediaActionHost
import com.quantumff.segue.core.data.repository.SearchRepository
import com.quantumff.segue.core.model.DownloadedLibrary
import com.quantumff.segue.core.model.MediaAction
import com.quantumff.segue.core.model.MediaActionFailure
import com.quantumff.segue.core.model.MediaKind
import com.quantumff.segue.core.model.PinKind
import com.quantumff.segue.core.model.PlayTarget
import com.quantumff.segue.core.model.SearchResult
import com.quantumff.segue.core.testing.MainDispatcherRule
import com.quantumff.segue.core.testing.data.TestData
import com.quantumff.segue.core.testing.play.RecordingPlayIntents
import com.quantumff.segue.core.testing.play.mediaActionHandler
import com.quantumff.segue.core.testing.repository.FakeDownloadRepository
import com.quantumff.segue.core.testing.repository.FakeLibraryRepository
import com.quantumff.segue.core.testing.repository.FakeOfflineModeRepository
import com.quantumff.segue.core.testing.repository.FakeStarRatingRepository
import com.quantumff.segue.core.testing.repository.FakeUserPreferencesRepository
import com.quantumff.segue.core.testing.repository.offlineLibraryView
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher())

    private val search = ScriptedSearchRepository()
    private val preferences = FakeUserPreferencesRepository()
    private val starRating = FakeStarRatingRepository()
    private val library = FakeLibraryRepository()
    private val downloads = FakeDownloadRepository()
    private val offline = FakeOfflineModeRepository()
    private val play = RecordingPlayIntents()

    private val song = TestData.song("s1")
    private val local = SearchResult(
        artists = persistentListOf(TestData.artist("a1")),
        albums = persistentListOf(),
        songs = persistentListOf(song),
        includesServerResults = false,
    )
    private val server = local.copy(
        albums = persistentListOf(TestData.album("al1")),
        includesServerResults = true,
    )

    private fun TestScope.viewModel(): SearchViewModel {
        val viewModel = SearchViewModel(
            search,
            preferences,
            library,
            offlineLibraryView(offline, downloads),
            play,
            mediaActionHandler(play, downloads, starRating),
        )
        backgroundScope.launch { viewModel.uiState.collect {} }
        return viewModel
    }

    @Test
    fun offlineShowsDownloadedLocalResultsAndSkipsTheServer() = runTest(mainDispatcherRule.testDispatcher) {
        offline.isOffline.value = true
        downloads.downloadedLibrary.value = DownloadedLibrary(songIds = setOf("s2"), artistIds = setOf("a1"))
        search.script["rad"] = listOf(
            local.copy(songs = persistentListOf(song, TestData.song("s2"))),
            server,
        )
        val viewModel = viewModel()
        viewModel.onQueryChange("rad")
        advanceUntilIdle()

        val results = viewModel.uiState.value.results as SearchResults.Ready
        assertThat(search.queries).isEmpty()
        assertThat(results.offline).isTrue()
        assertThat(results.serverPending).isFalse()
        assertThat(results.result.songs.map { it.id }).containsExactly("s2")
        assertThat(results.result.artists.map { it.id }).containsExactly("a1")
    }

    @Test
    fun fastTypingIsDebouncedIntoOneQuery() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = viewModel()
        viewModel.onQueryChange("r")
        advanceTimeBy(100)
        viewModel.onQueryChange("ra")
        advanceTimeBy(100)
        viewModel.onQueryChange("rad")
        advanceUntilIdle()

        assertThat(search.queries).containsExactly("rad")
    }

    @Test
    fun blankQueryShowsRecentSearchesAndGenres() = runTest(mainDispatcherRule.testDispatcher) {
        preferences.recentSearches.value = persistentListOf("radiohead")
        library.genresState.value = listOf(TestData.genre("Rock"))
        val viewModel = viewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.results).isEqualTo(SearchResults.Idle)
        assertThat(state.recentSearches).containsExactly("radiohead")
        assertThat(state.genres.map { it.name }).containsExactly("Rock")
        assertThat(search.queries).isEmpty()
    }

    @Test
    fun everyKeystrokeSearchesLocallyAtOnce() = runTest(mainDispatcherRule.testDispatcher) {
        search.script["ra"] = listOf(local)
        val viewModel = viewModel()
        viewModel.onQueryChange("r")
        runCurrent()
        viewModel.onQueryChange("ra")
        runCurrent()

        assertThat(search.localQueries).containsExactly("r", "ra").inOrder()
        assertThat(search.queries).isEmpty()
        assertThat((viewModel.uiState.value.results as SearchResults.Ready).result).isEqualTo(local)
    }

    @Test
    fun localResultsShowFirstThenServerResults() = runTest(mainDispatcherRule.testDispatcher) {
        search.script["rad"] = listOf(local, server)
        val viewModel = viewModel()
        viewModel.onQueryChange("rad")
        runCurrent()

        val first = viewModel.uiState.value.results as SearchResults.Ready
        assertThat(first.result).isEqualTo(local)
        assertThat(first.serverPending).isTrue()

        advanceUntilIdle()
        val second = viewModel.uiState.value.results as SearchResults.Ready
        assertThat(second.result).isEqualTo(server)
        assertThat(second.serverPending).isFalse()
        assertThat(second.offline).isFalse()
    }

    @Test
    fun serverFailureLeavesOfflineLocalResults() = runTest(mainDispatcherRule.testDispatcher) {
        search.script["rad"] = listOf(local)
        val viewModel = viewModel()
        viewModel.onQueryChange("rad")
        advanceUntilIdle()

        val ready = viewModel.uiState.value.results as SearchResults.Ready
        assertThat(ready.result).isEqualTo(local)
        assertThat(ready.offline).isTrue()
        assertThat(ready.serverPending).isFalse()
    }

    @Test
    fun filterChipChangesAndResetsWhenQueryIsCleared() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = viewModel()
        viewModel.onQueryChange("rad")
        viewModel.onFilterChange(SearchFilter.Songs)
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.filter).isEqualTo(SearchFilter.Songs)

        viewModel.onQueryChange("")
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.filter).isEqualTo(SearchFilter.All)
        assertThat(viewModel.uiState.value.results).isEqualTo(SearchResults.Idle)
    }

    @Test
    fun submitAndOpeningAResultSaveTheQuery() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = viewModel()
        viewModel.onQueryChange("  radiohead ")
        viewModel.onSubmit()
        viewModel.onQueryChange("nude")
        viewModel.onResultOpened()
        advanceUntilIdle()

        assertThat(preferences.recentSearches.value).containsExactly("nude", "radiohead").inOrder()
    }

    @Test
    fun recentSearchCanBeReusedAndRemoved() = runTest(mainDispatcherRule.testDispatcher) {
        preferences.recentSearches.value = persistentListOf("radiohead", "bjork")
        val viewModel = viewModel()
        viewModel.onRecentSearchClick("bjork")
        viewModel.onRemoveRecentSearch("radiohead")
        advanceUntilIdle()

        assertThat(viewModel.query.value).isEqualTo("bjork")
        assertThat(search.queries).containsExactly("bjork")
        assertThat(viewModel.uiState.value.recentSearches).containsExactly("bjork")
    }

    @Test
    fun playIntentIndexesIntoTheResultSongs() = runTest(mainDispatcherRule.testDispatcher) {
        val other = TestData.song("s2")
        search.script["rad"] = listOf(server.copy(songs = listOf(song, other).toImmutableList()))
        val viewModel = viewModel()
        viewModel.onQueryChange("rad")
        advanceUntilIdle()

        // The screen plays uiState.songs from the tapped index.
        assertThat(viewModel.uiState.value.songs.map { it.id }).containsExactly("s1", "s2").inOrder()
    }

    @Test
    fun favoriteToggleUpdatesResultsAndRollsBackOnFailure() = runTest(mainDispatcherRule.testDispatcher) {
        search.script["rad"] = listOf(server)
        val viewModel = viewModel()
        viewModel.onQueryChange("rad")
        advanceUntilIdle()

        viewModel.onSongAction(song, MediaAction.Favorite, MediaActionHost())
        advanceUntilIdle()
        assertThat(starRating.starred[MediaKind.Song to "s1"]).isTrue()
        assertThat(viewModel.uiState.value.songs.single().isStarred).isTrue()

        starRating.error = RemoteException.NotSignedIn()
        viewModel.onSongAction(viewModel.uiState.value.songs.single(), MediaAction.Unfavorite, MediaActionHost())
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.songs.single().isStarred).isTrue()
        assertThat(viewModel.uiState.value.message).isEqualTo(MediaActionFailure.FavoriteFailed)

        viewModel.onMessageDismiss()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.message).isNull()
    }

    @Test
    fun tappingASongPlaysTheResultsFromItAndDownloadPinsIt() = runTest(mainDispatcherRule.testDispatcher) {
        search.script["rad"] = listOf(server)
        val viewModel = viewModel()
        viewModel.onQueryChange("rad")
        advanceUntilIdle()

        viewModel.onSongClick(0)
        viewModel.onSongAction(song, MediaAction.Download, MediaActionHost())
        advanceUntilIdle()

        assertThat(play.plays.single().target).isEqualTo(PlayTarget.Songs(listOf(song), 0))
        assertThat(downloads.pins.value.keys).containsExactly(PinKind.Song to "s1")
    }

    /** Emits a script per query, one result per second: local first, then the server's. */
    private class ScriptedSearchRepository : SearchRepository {
        val script = mutableMapOf<String, List<SearchResult>>()
        val queries = mutableListOf<String>()
        val localQueries = mutableListOf<String>()

        override fun searchLocal(query: String): Flow<SearchResult> = flow {
            localQueries += query
            emit(script[query]?.firstOrNull() ?: SearchResult.Empty)
        }

        override fun search(query: String): Flow<SearchResult> = flow {
            queries += query
            script[query].orEmpty().forEachIndexed { index, result ->
                if (index > 0) delay(1_000)
                emit(result)
            }
        }
    }

    private companion object {
        const val DEBOUNCE = 300L
    }
}
