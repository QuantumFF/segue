package com.quantumff.segue.core.playback.service

import android.content.Context
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.data.RemoteException
import com.quantumff.segue.core.model.MediaKind
import com.quantumff.segue.core.playback.media.isStarred
import com.quantumff.segue.core.playback.media.toMediaItem
import com.quantumff.segue.core.testing.data.TestData
import com.quantumff.segue.core.testing.repository.FakeStarRatingRepository
import java.io.IOException
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FavoriteToggleTest {
    private val exo = ExoPlayer.Builder(ApplicationProvider.getApplicationContext<Context>()).build()
    private val player = SeguePlayer(exo)
    private val starRatings = FakeStarRatingRepository()
    private var changes = 0

    @After
    fun tearDown() = exo.release()

    private fun TestScope.toggle() =
        FavoriteToggle(player, starRatings, callScope = backgroundScope, playerScope = backgroundScope) { changes++ }

    /** a, b, a again; the first a plays. */
    private fun queue() = player.setMediaItems(listOf("a", "b", "a").map { TestData.song(it).toMediaItem() }, 0, 0)

    private fun hearts() = List(exo.mediaItemCount) { exo.getMediaItemAt(it).isStarred }

    @Test
    fun `the heart changes at once on every item of the song, and the server call follows`() = runTest {
        queue()

        toggle().toggle()

        assertThat(hearts()).containsExactly(true, false, true).inOrder()
        assertThat(changes).isEqualTo(1)
        runCurrent()
        assertThat(starRatings.starred[MediaKind.Song to "a"]).isTrue()
        assertThat(hearts()).containsExactly(true, false, true).inOrder()
    }

    @Test
    fun `toggling a favorite unstars it`() = runTest {
        queue()
        val favorite = toggle()
        favorite.toggle()
        runCurrent()

        favorite.toggle()
        runCurrent()

        assertThat(hearts()).containsExactly(false, false, false)
        assertThat(starRatings.starred[MediaKind.Song to "a"]).isFalse()
    }

    @Test
    fun `a failed server call puts the hearts back`() = runTest {
        queue()
        starRatings.error = RemoteException.Unreachable(IOException("offline"))

        toggle().toggle()
        assertThat(hearts()).containsExactly(true, false, true).inOrder()
        runCurrent()

        assertThat(hearts()).containsExactly(false, false, false)
        assertThat(changes).isEqualTo(2)
    }

    @Test
    fun `playback carries on through a toggle`() = runTest {
        queue()
        player.seekTo(0, 1_234)

        toggle().toggle()

        assertThat(exo.currentMediaItemIndex).isEqualTo(0)
        assertThat(exo.currentPosition).isEqualTo(1_234)
    }

    @Test
    fun `nothing queued, nothing to toggle`() = runTest {
        toggle().toggle()
        runCurrent()

        assertThat(starRatings.starred).isEmpty()
        assertThat(changes).isEqualTo(0)
    }
}
