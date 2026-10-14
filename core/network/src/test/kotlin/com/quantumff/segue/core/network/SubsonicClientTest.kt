package com.quantumff.segue.core.network

import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.model.AlbumListType
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class SubsonicClientTest {
    @get:Rule
    val rule = SubsonicServerRule()

    @Test
    fun `ping reports the server identity`() = runTest {
        rule.enqueueFixture("extensions.json")

        val info = rule.client.ping()

        assertThat(info.openSubsonic).isTrue()
        assertThat(info.type).isEqualTo("navidrome")
        assertThat(info.serverVersion).isEqualTo("0.58.0 (fe1b0a2)")
        assertThat(info.version).isEqualTo("1.16.1")
    }

    @Test
    fun `extensions decode names and versions`() = runTest {
        rule.enqueueFixture("extensions.json")

        val extensions = rule.client.getOpenSubsonicExtensions()

        assertThat(rule.takeRequest().url.encodedPath).endsWith("/rest/getOpenSubsonicExtensions.view")
        assertThat(extensions.associate { it.name to it.versions })
            .containsAtLeast("indexBasedQueue", listOf(1), "songLyrics", listOf(1, 2), "transcodeOffset", listOf(1))
    }

    @Test
    fun `getAlbum decodes OpenSubsonic album and song fields`() = runTest {
        rule.enqueueFixture("album.json")

        val album = rule.client.getAlbum("3AFHPHxJIYHtKsRfXkpbrF")

        assertThat(rule.takeRequest().param("id")).isEqualTo("3AFHPHxJIYHtKsRfXkpbrF")
        assertThat(album.name).isEqualTo("In Rainbows")
        assertThat(album.displayArtist).isEqualTo("Radiohead")
        assertThat(album.played).isEqualTo("2024-09-10T20:15:00.5+02:00")
        assertThat(album.discTitles.map { it.disc to it.title }).containsExactly(1 to "", 2 to "Bonus").inOrder()
        assertThat(album.musicBrainzId).isEqualTo("6e335887-60ba-38f0-95af-fae7774336bf")
        assertThat(album.isCompilation).isFalse()

        val first = album.song.first()
        assertThat(first.isDir).isFalse()
        assertThat(first.bpm).isEqualTo(189)
        assertThat(first.explicitStatus).isEqualTo("explicit")
        assertThat(first.played).isEqualTo("2024-09-10T20:15:00Z")
        assertThat(first.replayGain?.trackGain).isWithin(0.001).of(-7.12)
        assertThat(first.replayGain?.albumPeak).isWithin(0.001).of(1.0)
        assertThat(first.replayGain?.baseGain).isNull()

        val second = album.song[1]
        assertThat(second.discNumber).isEqualTo(2)
        assertThat(second.displayArtist).isEqualTo("Radiohead feat. Someone")
        assertThat(second.artists.map { it.name }).containsExactly("Radiohead", "Someone").inOrder()
        assertThat(second.albumArtists.map { it.id }).containsExactly("4Z8W4fKeB5YxbusRsdQVPb")
        assertThat(second.replayGain?.trackGain).isNull()
    }

    @Test
    fun `getArtists tolerates a null artist list and sends ifModifiedSince`() = runTest {
        rule.enqueueFixture("artists.json")

        val index = rule.client.getArtists(ifModifiedSince = 1_700_000_000_000)

        assertThat(rule.takeRequest().param("ifModifiedSince")).isEqualTo("1700000000000")
        assertThat(index.lastModified).isEqualTo(1_726_000_000_000)
        assertThat(index.index.flatMap { it.artist }.map { it.name }).containsExactly("Radiohead", "Röyksopp")
        assertThat(index.index.first().artist).isEmpty()
        assertThat(index.index[1].artist.first().starred).isEqualTo("2023-11-02T09:00:00Z")
    }

    @Test
    fun `getAlbumList2 sends the type and paging`() = runTest {
        rule.enqueueBody("""{"subsonic-response":{"status":"ok","version":"1.16.1","albumList2":{}}}""")

        val albums = rule.client.getAlbumList2(AlbumListType.Newest, size = 500, offset = 1000)

        val request = rule.takeRequest()
        assertThat(request.url.encodedPath).endsWith("/rest/getAlbumList2.view")
        assertThat(request.param("type")).isEqualTo("newest")
        assertThat(request.param("size")).isEqualTo("500")
        assertThat(request.param("offset")).isEqualTo("1000")
        assertThat(albums).isEmpty()
    }

    @Test
    fun `every album list type uses its API name`() = runTest {
        AlbumListType.entries.forEach { type ->
            rule.enqueueOk()
            rule.client.getAlbumList2(type, size = 10, offset = 0)
            assertThat(rule.takeRequest().param("type")).isEqualTo(type.apiValue)
        }
        assertThat(AlbumListType.entries.map { it.apiValue })
            .containsAtLeast("newest", "recent", "frequent", "random", "alphabeticalByName", "alphabeticalByArtist")
    }

    @Test
    fun `album lists by year and genre send their filters`() = runTest {
        rule.enqueueOk()
        rule.enqueueOk()

        rule.client.getAlbumList2ByYear(fromYear = 1990, toYear = 1999, size = 50, offset = 0)
        rule.client.getAlbumList2ByGenre(genre = "Jazz", size = 50, offset = 50)

        rule.takeRequest().let {
            assertThat(it.param("type")).isEqualTo("byYear")
            assertThat(it.param("fromYear")).isEqualTo("1990")
            assertThat(it.param("toYear")).isEqualTo("1999")
        }
        rule.takeRequest().let {
            assertThat(it.param("type")).isEqualTo("byGenre")
            assertThat(it.param("genre")).isEqualTo("Jazz")
            assertThat(it.param("offset")).isEqualTo("50")
        }
    }

    @Test
    fun `search3 sends every count and offset and tolerates missing sections`() = runTest {
        rule.enqueueFixture("search3.json")

        val result = rule.client.search3(
            "radio",
            artistCount = 5,
            artistOffset = 0,
            albumCount = 10,
            albumOffset = 10,
            songCount = 20,
            songOffset = 40,
        )

        val request = rule.takeRequest()
        assertThat(request.param("query")).isEqualTo("radio")
        assertThat(request.param("artistCount")).isEqualTo("5")
        assertThat(request.param("albumOffset")).isEqualTo("10")
        assertThat(request.param("songOffset")).isEqualTo("40")
        assertThat(result.artist.single().name).isEqualTo("Radiohead")
        assertThat(result.album.single().name).isEqualTo("In Rainbows")
        assertThat(result.song).isEmpty()
    }

    @Test
    fun `getLyricsBySongId decodes synced and unsynced lyrics`() = runTest {
        rule.enqueueFixture("lyrics.json")

        val lyrics = rule.client.getLyricsBySongId("5pTq9vU2mHB4kGiMDw1z1T")

        assertThat(rule.takeRequest().param("id")).isEqualTo("5pTq9vU2mHB4kGiMDw1z1T")
        assertThat(lyrics).hasSize(2)
        assertThat(lyrics[0].synced).isTrue()
        assertThat(lyrics[0].offset).isEqualTo(-100)
        assertThat(lyrics[0].line.map { it.start }).containsExactly(0L, 3001L).inOrder()
        assertThat(lyrics[1].line.single().start).isNull()
    }

    @Test
    fun `getPlaylist decodes entries and the changed time`() = runTest {
        rule.enqueueFixture("playlist.json")

        val playlist = rule.client.getPlaylist("pl-1")

        assertThat(playlist.changed).isEqualTo("2024-09-01T12:00:00Z")
        assertThat(playlist.public).isTrue()
        assertThat(playlist.entry.map { it.id }).hasSize(2)
    }

    @Test
    fun `playlist mutations send repeated parameters`() = runTest {
        rule.enqueueFixture("playlist.json")
        rule.enqueueOk()
        rule.enqueueOk()

        rule.client.createPlaylist(name = "New", songIds = listOf("a", "b"))
        rule.client.updatePlaylist(
            id = "pl-1",
            name = "Renamed",
            songIdsToAdd = listOf("c"),
            songIndexesToRemove = listOf(0, 3),
        )
        rule.client.deletePlaylist("pl-1")

        rule.takeRequest().let {
            assertThat(it.url.encodedPath).endsWith("/rest/createPlaylist.view")
            assertThat(it.param("name")).isEqualTo("New")
            assertThat(it.params("songId")).containsExactly("a", "b").inOrder()
        }
        rule.takeRequest().let {
            assertThat(it.param("playlistId")).isEqualTo("pl-1")
            assertThat(it.param("name")).isEqualTo("Renamed")
            assertThat(it.params("songIdToAdd")).containsExactly("c")
            assertThat(it.params("songIndexToRemove")).containsExactly("0", "3").inOrder()
            assertThat(it.param("comment")).isNull()
        }
        assertThat(rule.takeRequest().param("id")).isEqualTo("pl-1")
    }

    @Test
    fun `star, rating, and batched scrobbles send their parameters`() = runTest {
        repeat(3) { rule.enqueueOk() }

        rule.client.star(songIds = listOf("s1"), albumIds = listOf("al1"), artistIds = emptyList())
        rule.client.setRating(id = "s1", rating = 4)
        rule.client.scrobble(songIds = listOf("s1", "s2"), timesMillis = listOf(1_000L, 2_000L), submission = true)

        rule.takeRequest().let {
            assertThat(it.url.encodedPath).endsWith("/rest/star.view")
            assertThat(it.params("id")).containsExactly("s1")
            assertThat(it.params("albumId")).containsExactly("al1")
            assertThat(it.params("artistId")).isEmpty()
        }
        rule.takeRequest().let {
            assertThat(it.param("rating")).isEqualTo("4")
        }
        rule.takeRequest().let {
            assertThat(it.params("id")).containsExactly("s1", "s2").inOrder()
            assertThat(it.params("time")).containsExactly("1000", "2000").inOrder()
            assertThat(it.param("submission")).isEqualTo("true")
        }
    }

    @Test
    fun `music folders accept numeric ids and genres read the value key`() = runTest {
        rule.enqueueFixture("misc.json")
        rule.enqueueFixture("misc.json")
        rule.enqueueFixture("misc.json")
        rule.enqueueFixture("misc.json")

        assertThat(rule.client.getMusicFolders().single().id).isEqualTo("1")
        val genres = rule.client.getGenres()
        assertThat(genres.map { it.value }).containsExactly("Alternative", "").inOrder()
        assertThat(genres.first().albumCount).isEqualTo(11)
        assertThat(rule.client.getLicense().valid).isTrue()
        assertThat(
            rule.client.getInternetRadioStations().single().streamUrl,
        ).isEqualTo("https://stream.radioparadise.com/flac")
    }

    @Test
    fun `song lists decode random, genre, similar, and top songs`() = runTest {
        val songs = """{"subsonic-response":{"status":"ok","version":"1.16.1",""" +
            """"randomSongs":{"song":[{"id":"r","isDir":false,"title":"R"}]},""" +
            """"songsByGenre":{"song":[{"id":"g","isDir":false,"title":"G"}]},""" +
            """"similarSongs2":{"song":[{"id":"s","isDir":false,"title":"S"}]},""" +
            """"topSongs":{"song":[{"id":"t","isDir":false,"title":"T"}]},""" +
            """"song":{"id":"one","isDir":false,"title":"One"}}}"""
        repeat(5) { rule.enqueueBody(songs) }

        assertThat(rule.client.getRandomSongs(size = 50).single().id).isEqualTo("r")
        assertThat(rule.client.getSongsByGenre("Jazz", count = 10, offset = 0).single().id).isEqualTo("g")
        assertThat(rule.client.getSimilarSongs2(artistId = "ar", count = 20).single().id).isEqualTo("s")
        assertThat(
            rule.client.getTopSongs(artistName = "Radiohead", artistId = "ar", count = 10).single().id,
        ).isEqualTo("t")
        assertThat(rule.client.getSong("one").title).isEqualTo("One")

        assertThat(rule.takeRequest().param("size")).isEqualTo("50")
        assertThat(rule.takeRequest().param("genre")).isEqualTo("Jazz")
        assertThat(rule.takeRequest().param("id")).isEqualTo("ar")
        rule.takeRequest().let {
            assertThat(it.param("artist")).isEqualTo("Radiohead")
            assertThat(it.param("id")).isEqualTo("ar")
        }
    }

    @Test
    fun `getArtist decodes its albums`() = runTest {
        rule.enqueueBody(
            """{"subsonic-response":{"status":"ok","version":"1.16.1","artist":{"id":"ar","name":"Radiohead",""" +
                """"albumCount":1,"album":[{"id":"al","name":"In Rainbows","songCount":10,"duration":2564,"created":"2024-03-01T10:20:30Z"}]}}}""",
        )

        val artist = rule.client.getArtist("ar")

        assertThat(artist.album.single().id).isEqualTo("al")
    }
}
