package com.quantumff.segue.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HeroPlaybackTest {
    @Test
    fun `a source opens only the page of its own kind and id`() {
        val album = QueueSource.Album("1", "In Rainbows")

        assertThat(album.opens(SourcePage.Album("1"))).isTrue()
        assertThat(album.opens(SourcePage.Album("2"))).isFalse()
        // IDs are unique per kind only: playlist 1 and artist 1 are other pages.
        assertThat(album.opens(SourcePage.Playlist("1"))).isFalse()
        assertThat(album.opens(SourcePage.Artist("1"))).isFalse()
        assertThat(QueueSource.Playlist("1", "Road trip").opens(SourcePage.Playlist("1"))).isTrue()
        assertThat(QueueSource.Artist("1", "Radiohead").opens(SourcePage.Artist("1"))).isTrue()
        // A genre's id is its name, but no source page is a genre.
        assertThat(QueueSource.Genre("1").opens(SourcePage.Album("1"))).isFalse()
        assertThat(QueueSource.Unknown.opens(SourcePage.Album("1"))).isFalse()
    }

    @Test
    fun `the hero plays or pauses only the page that is the queue source`() {
        val page = SourcePage.Album("1")
        val playing = PlayerUiState(isPlaying = true, queueSource = QueueSource.Album("1", "In Rainbows"))

        assertThat(playing.heroPlayback(page)).isEqualTo(HeroPlayback.Playing)
        assertThat(playing.copy(isPlaying = false).heroPlayback(page)).isEqualTo(HeroPlayback.Paused)
        assertThat(playing.copy(isPlaying = false, hasEnded = true).heroPlayback(page))
            .isEqualTo(HeroPlayback.NotSource)
        assertThat(playing.heroPlayback(SourcePage.Playlist("1"))).isEqualTo(HeroPlayback.NotSource)
    }
}
