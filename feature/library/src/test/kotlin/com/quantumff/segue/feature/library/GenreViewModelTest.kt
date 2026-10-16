package com.quantumff.segue.feature.library

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.model.AlbumFilter
import com.quantumff.segue.core.model.AlbumOrder
import com.quantumff.segue.core.model.AlbumPlayRequest
import com.quantumff.segue.core.model.AlbumSortOrder
import com.quantumff.segue.core.model.AlbumSource
import com.quantumff.segue.core.model.QueuePlacement
import com.quantumff.segue.core.testing.MainDispatcherRule
import com.quantumff.segue.core.testing.data.TestData
import com.quantumff.segue.core.testing.repository.FakeAlbumRepository
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class GenreViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun countsOnlyTheGenresAlbums() = runTest {
        val albums = FakeAlbumRepository().apply {
            albumsState.value = listOf(
                TestData.album("1", genre = "Jazz"),
                TestData.album("2", genre = "Rock"),
                TestData.album("3", genre = "Jazz"),
            )
        }
        val viewModel = GenreViewModel(SavedStateHandle(mapOf(GenreViewModel.KEY_GENRE to "Jazz")), albums)

        viewModel.uiState.test {
            val state = expectMostRecentItem()
            assertThat(state.genre).isEqualTo("Jazz")
            assertThat(state.albumCount).isEqualTo(2)
        }
    }

    @Test
    fun playAlbumsQueuesTheGenreGridInItsOrder() {
        val viewModel =
            GenreViewModel(SavedStateHandle(mapOf(GenreViewModel.KEY_GENRE to "Jazz")), FakeAlbumRepository())
        val play = RecordingPlayActions()

        viewModel.playAlbums(AlbumOrder.Shuffled, QueuePlacement.End, play.actions)

        assertThat(play.albumRequests).containsExactly(
            AlbumPlayRequest(
                AlbumSource.Albums(AlbumSortOrder.Name, AlbumFilter(genre = "Jazz")),
                AlbumOrder.Shuffled,
                QueuePlacement.End,
            ),
        )
    }

    @Test
    fun genreTintStaysInHeroTintRanges() {
        listOf("Rock", "Jazz", "Electronic", "Hip-Hop", "").forEach { name ->
            val dark = hsl(genreTint(name, isDark = true))
            val light = hsl(genreTint(name, isDark = false))
            assertThat(dark.second).isAtMost(0.551f)
            assertThat(dark.third).isIn(com.google.common.collect.Range.closed(0.179f, 0.301f))
            assertThat(light.third).isIn(com.google.common.collect.Range.closed(0.819f, 0.901f))
        }
        assertThat(genreTint("Rock", true)).isEqualTo(genreTint("rock", true))
    }

    /** (hue, saturation, lightness) of an sRGB color. */
    private fun hsl(color: androidx.compose.ui.graphics.Color): Triple<Float, Float, Float> {
        val max = maxOf(color.red, color.green, color.blue)
        val min = minOf(color.red, color.green, color.blue)
        val lightness = (max + min) / 2f
        val delta = max - min
        val saturation = if (delta == 0f) 0f else delta / (1f - kotlin.math.abs(2f * lightness - 1f))
        return Triple(0f, saturation, lightness)
    }
}
