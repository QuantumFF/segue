package com.quantumff.segue.core.data.model

import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.database.model.AlbumArtistCrossRef
import com.quantumff.segue.core.database.model.ReplayGainEntity
import com.quantumff.segue.core.model.ArtistRef
import com.quantumff.segue.core.model.DiscTitle
import com.quantumff.segue.core.network.model.NetworkAlbum
import com.quantumff.segue.core.network.model.NetworkArtist
import com.quantumff.segue.core.network.model.NetworkArtistRef
import com.quantumff.segue.core.network.model.NetworkChild
import com.quantumff.segue.core.network.model.NetworkDiscTitle
import com.quantumff.segue.core.network.model.NetworkPlaylist
import com.quantumff.segue.core.network.model.NetworkReplayGain
import org.junit.Test

class NetworkMappersTest {
    @Test
    fun `song prefers the OpenSubsonic display artist and keeps multi-artist credits`() {
        val song = NetworkChild(
            id = "s",
            title = "Bodysnatchers",
            artist = "Radiohead",
            displayArtist = "Radiohead feat. Someone",
            artists = listOf(NetworkArtistRef("a", "Radiohead"), NetworkArtistRef("b", "Someone")),
            albumArtists = listOf(NetworkArtistRef("a", "Radiohead")),
            sortName = "bodysnatchers sort",
            bpm = 120,
            explicitStatus = "explicit",
            played = "2024-05-01T08:00:00Z",
            starred = "0001-01-01T00:00:00Z",
        ).asEntity("srv")

        assertThat(song.serverId).isEqualTo("srv")
        assertThat(song.artist).isEqualTo("Radiohead feat. Someone")
        assertThat(song.artists.items).containsExactly(ArtistRef("a", "Radiohead"), ArtistRef("b", "Someone")).inOrder()
        assertThat(song.albumArtists.items).containsExactly(ArtistRef("a", "Radiohead"))
        assertThat(song.sortName).isEqualTo("bodysnatchers sort")
        assertThat(song.bpm).isEqualTo(120)
        assertThat(song.explicitStatus).isEqualTo("explicit")
        assertThat(song.playedAt).isEqualTo(1_714_550_400_000)
        assertThat(song.starredAt).isNull()
    }

    @Test
    fun `plain Subsonic songs fall back to legacy fields`() {
        val song = NetworkChild(id = "s", title = "Airbag", artist = "Radiohead").asEntity("srv")

        assertThat(song.artist).isEqualTo("Radiohead")
        assertThat(song.sortName).isEqualTo("airbag")
        assertThat(song.artists.items).isEmpty()
    }

    @Test
    fun `zero values that mean unknown become null`() {
        val song = NetworkChild(id = "s", title = "t", bpm = 0, userRating = 0, track = 0, year = 0).asEntity("srv")

        assertThat(song.bpm).isNull()
        assertThat(song.userRating).isNull()
        assertThat(song.track).isNull()
        assertThat(song.year).isNull()
    }

    @Test
    fun `ReplayGain is kept when tagged and dropped when empty`() {
        val tagged = NetworkChild(
            id = "s",
            replayGain = NetworkReplayGain(trackGain = -7.5, albumPeak = 1.0),
        ).asEntity("srv")
        val untagged = NetworkChild(id = "s", replayGain = NetworkReplayGain()).asEntity("srv")

        assertThat(tagged.replayGain).isEqualTo(ReplayGainEntity(trackGain = -7.5f, albumPeak = 1f))
        assertThat(untagged.replayGain).isNull()
    }

    @Test
    fun `directories are not songs`() {
        val children = listOf(NetworkChild(id = "dir", isDir = true), NetworkChild(id = "song"))

        assertThat(children.songEntities("srv").map { it.id }).containsExactly("song")
    }

    @Test
    fun `album maps disc titles, dates, and artist credits`() {
        val network = NetworkAlbum(
            id = "al",
            name = "In Rainbows",
            artist = "Radiohead",
            artistId = "a",
            displayArtist = "Radiohead & Friends",
            artists = listOf(NetworkArtistRef("a", "Radiohead"), NetworkArtistRef("b", "Friend")),
            discTitles = listOf(NetworkDiscTitle(1, ""), NetworkDiscTitle(2, "Bonus")),
            created = "2024-05-01T08:00:00Z",
            isCompilation = true,
            songCount = 10,
            duration = 2564,
        )

        val album = network.asEntity("srv")

        assertThat(album.artist).isEqualTo("Radiohead & Friends")
        assertThat(album.discTitles.items).containsExactly(DiscTitle(1, ""), DiscTitle(2, "Bonus")).inOrder()
        assertThat(album.createdAt).isEqualTo(1_714_550_400_000)
        assertThat(album.isCompilation).isTrue()
        assertThat(album.sortName).isEqualTo("in rainbows")
        assertThat(network.artistCrossRefs("srv")).containsExactly(
            AlbumArtistCrossRef("srv", "al", "a", 0),
            AlbumArtistCrossRef("srv", "al", "b", 1),
        )
    }

    @Test
    fun `artist and playlist map their fields`() {
        val artist = NetworkArtist(id = "a", name = "Röyksopp", albumCount = 3, sortName = "royksopp").asEntity("srv")
        val playlist = NetworkPlaylist(
            id = "p",
            name = "Mix",
            public = true,
            changed = "2024-05-01T08:00:00Z",
        ).asEntity("srv")

        assertThat(artist.sortName).isEqualTo("royksopp")
        assertThat(artist.albumCount).isEqualTo(3)
        assertThat(playlist.isPublic).isTrue()
        assertThat(playlist.changedAt).isEqualTo(1_714_550_400_000)
    }
}
