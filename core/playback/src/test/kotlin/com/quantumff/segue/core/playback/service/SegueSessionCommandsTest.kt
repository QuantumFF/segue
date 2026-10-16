package com.quantumff.segue.core.playback.service

import android.os.Bundle
import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.model.AlbumFilter
import com.quantumff.segue.core.model.AlbumOrder
import com.quantumff.segue.core.model.AlbumPlayRequest
import com.quantumff.segue.core.model.AlbumSortOrder
import com.quantumff.segue.core.model.AlbumSource
import com.quantumff.segue.core.model.QueuePlacement
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
}
