package com.quantumff.segue.core.playback.service

import android.os.Bundle
import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.model.AlbumFilter
import com.quantumff.segue.core.model.AlbumOrder
import com.quantumff.segue.core.model.AlbumPlayRequest
import com.quantumff.segue.core.model.AlbumSortOrder
import com.quantumff.segue.core.model.AlbumSource
import com.quantumff.segue.core.model.QueuePlacement
import com.quantumff.segue.core.model.Song
import com.quantumff.segue.core.playback.media.albumKey
import com.quantumff.segue.core.playback.media.toMediaItem
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SegueSessionCommandsTest {
    @Test
    fun `album play requests survive the trip to the service`() {
        val requests = listOf(
            AlbumPlayRequest(
                AlbumSource.Albums(AlbumSortOrder.Artist, AlbumFilter(true, "Jazz", 1971, true)),
                AlbumOrder.InOrder,
                QueuePlacement.Next,
            ),
            AlbumPlayRequest(AlbumSource.Albums(AlbumSortOrder.Name), AlbumOrder.Shuffled, QueuePlacement.End),
            AlbumPlayRequest(AlbumSource.Library, AlbumOrder.Shuffled),
            AlbumPlayRequest(AlbumSource.Artist("ar1"), AlbumOrder.Shuffled),
            AlbumPlayRequest(AlbumSource.Genre("Rock"), AlbumOrder.Shuffled),
            AlbumPlayRequest(AlbumSource.Playlist("p1"), AlbumOrder.InOrder),
        )
        for (request in requests) {
            val args = SegueSessionCommands.playAlbumsArgs(request)
            assertThat(SegueSessionCommands.playAlbums(args)).isEqualTo(request)
        }
        assertThat(SegueSessionCommands.playAlbums(Bundle.EMPTY)).isNull()
    }

    @Test
    fun `queued songs survive the trip to the service, metadata included`() {
        val songs = (1..3).map {
            Song(id = "s$it", title = "t$it", albumId = "al", album = "A", artist = null, artistId = null)
        }
        val request = SegueSessionCommands.QueueSongs(
            songs.map { it.toMediaItem() },
            QueuePlacement.Next,
            startIndex = 2,
            shuffle = true,
        )

        val received = SegueSessionCommands.queueSongs(SegueSessionCommands.queueSongsArgs(request))!!

        assertThat(received.items.map { it.mediaId }).containsExactly("s1", "s2", "s3").inOrder()
        assertThat(received.items.map { it.albumKey }).containsExactly("al", "al", "al")
        assertThat(received.copy(items = request.items)).isEqualTo(request)
        assertThat(SegueSessionCommands.queueSongs(Bundle.EMPTY)).isNull()
    }
}
