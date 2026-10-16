package com.quantumff.segue.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AlbumSourceRecordTest {
    private fun roundTrip(source: AlbumSource) = AlbumSourceRecord.of(source).toSource()

    @Test
    fun `library round-trips`() {
        assertThat(roundTrip(AlbumSource.Library)).isEqualTo(AlbumSource.Library)
    }

    @Test
    fun `albums round-trip with sort and every filter field`() {
        val filtered = AlbumSource.Albums(AlbumSortOrder.Artist, AlbumFilter(true, "Jazz", 1971, true))
        val unfiltered = AlbumSource.Albums(AlbumSortOrder.Name)

        assertThat(roundTrip(filtered)).isEqualTo(filtered)
        assertThat(roundTrip(unfiltered)).isEqualTo(unfiltered)
    }

    @Test
    fun `artist round-trips`() {
        assertThat(roundTrip(AlbumSource.Artist("ar1"))).isEqualTo(AlbumSource.Artist("ar1"))
    }

    @Test
    fun `genre round-trips`() {
        assertThat(roundTrip(AlbumSource.Genre("Rock"))).isEqualTo(AlbumSource.Genre("Rock"))
    }

    @Test
    fun `playlist round-trips`() {
        assertThat(roundTrip(AlbumSource.Playlist("p1"))).isEqualTo(AlbumSource.Playlist("p1"))
    }

    @Test
    fun `records match what album_feed rows already hold`() {
        assertThat(AlbumSourceRecord.of(AlbumSource.Library)).isEqualTo(AlbumSourceRecord("Library"))
        assertThat(AlbumSourceRecord.of(AlbumSource.Albums(AlbumSortOrder.Artist, AlbumFilter(true, "Jazz", 1971))))
            .isEqualTo(AlbumSourceRecord("Albums", sort = "Artist", starredOnly = true, genre = "Jazz", year = 1971))
        assertThat(AlbumSourceRecord.of(AlbumSource.Artist("ar1"))).isEqualTo(AlbumSourceRecord("Artist", id = "ar1"))
        assertThat(AlbumSourceRecord.of(AlbumSource.Genre("Rock"))).isEqualTo(AlbumSourceRecord("Genre", id = "Rock"))
        assertThat(AlbumSourceRecord.of(AlbumSource.Playlist("p1")))
            .isEqualTo(AlbumSourceRecord("Playlist", id = "p1"))
    }

    @Test
    fun `unreadable records decode to null`() {
        assertThat(AlbumSourceRecord("Mixtape").toSource()).isNull()
        assertThat(AlbumSourceRecord("Albums", sort = "Mood").toSource()).isNull()
        assertThat(AlbumSourceRecord("Artist").toSource()).isNull()
        assertThat(AlbumSourceRecord("Genre").toSource()).isNull()
        assertThat(AlbumSourceRecord("Playlist").toSource()).isNull()
    }
}
