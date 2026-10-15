package com.quantumff.segue.core.playback.browse

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaConstants
import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.data.repository.AlbumShuffleRepository
import com.quantumff.segue.core.model.AlbumShuffleSource
import com.quantumff.segue.core.model.Genre
import com.quantumff.segue.core.model.ShuffleMode
import com.quantumff.segue.core.model.Song
import com.quantumff.segue.core.testing.data.TestData
import kotlin.random.Random
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BrowseTreeTest {
    private val repo = FakeMediaBrowseRepository()
    private val shuffle = object : AlbumShuffleRepository {
        override suspend fun albumIds(source: AlbumShuffleSource) = repo.albums.map { it.id }

        override suspend fun songs(source: AlbumShuffleSource, albumIds: List<String>): List<Song> =
            repo.songs.filter { it.albumId in albumIds }
    }
    private val tree = BrowseTree(
        repository = repo,
        albumShuffle = shuffle,
        labels = BrowseLabels(
            home = "Home",
            library = "Library",
            downloads = "Downloads",
            shuffleAlbums = "Shuffle albums",
            recentlyPlayed = "Recently played",
            recentlyAdded = "Recently added",
            starred = "Starred",
            albums = "Albums",
            artists = "Artists",
            playlists = "Playlists",
            genres = "Genres",
        ),
        artwork = object : BrowseArtwork {
            override fun cover(coverArtId: String): Uri = Uri.parse("content://art/cover/$coverArtId")

            override fun icon(icon: BrowseIcon): Uri = Uri.parse("android.resource://pkg/${icon.name}")
        },
        random = Random(1),
    )

    private suspend fun ids(parent: BrowseId, page: Int = 0, pageSize: Int = 100) =
        tree.children(parent.encode(), page, pageSize)!!.map { BrowseId.parse(it.mediaId) }

    private val MediaItem.extras get() = mediaMetadata.extras!!

    @Test
    fun `root is browsable and defaults to list style`() {
        val root = tree.root()

        assertThat(root.mediaId).isEqualTo(BrowseId.Root.encode())
        assertThat(root.mediaMetadata.isBrowsable).isTrue()
        assertThat(root.mediaMetadata.isPlayable).isFalse()
        assertThat(BrowseTree.rootExtras().getInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE))
            .isEqualTo(MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM)
    }

    @Test
    fun `root children are the browsable tabs, with icons`() = runTest {
        val children = tree.children(BrowseId.Root.encode(), 0, 100)!!

        assertThat(children.map { BrowseId.parse(it.mediaId) })
            .containsExactly(BrowseId.Home, BrowseId.Library, BrowseId.Downloads).inOrder()
        assertThat(children.all { it.mediaMetadata.isBrowsable == true && it.mediaMetadata.isPlayable == false })
            .isTrue()
        assertThat(children.all { it.mediaMetadata.artworkUri != null }).isTrue()
    }

    @Test
    fun `home starts with shuffle albums, then the album lists and starred songs`() = runTest {
        val children = tree.children(BrowseId.Home.encode(), 0, 100)!!

        assertThat(children.map { BrowseId.parse(it.mediaId) }).containsExactly(
            BrowseId.ShuffleAlbums,
            BrowseId.RecentlyPlayed,
            BrowseId.RecentlyAdded,
            BrowseId.Starred,
        ).inOrder()
        assertThat(children.first().mediaMetadata.isPlayable).isTrue()
        assertThat(children.first().mediaMetadata.isBrowsable).isFalse()
    }

    @Test
    fun `library lists albums, artists, playlists and genres`() = runTest {
        assertThat(ids(BrowseId.Library))
            .containsExactly(BrowseId.Albums, BrowseId.Artists, BrowseId.Playlists, BrowseId.Genres).inOrder()
    }

    @Test
    fun `albums are paged in the database with page and page size`() = runTest {
        repo.albums = (1..5).map { TestData.album("al-$it") }

        assertThat(ids(BrowseId.Albums, page = 1, pageSize = 2))
            .containsExactly(BrowseId.Album("al-3"), BrowseId.Album("al-4")).inOrder()
        assertThat(repo.pagedReads.last()).isEqualTo(Triple("albums", 2, 2))
        assertThat(ids(BrowseId.Albums, page = 2, pageSize = 2)).containsExactly(BrowseId.Album("al-5"))
        assertThat(ids(BrowseId.Albums, page = 3, pageSize = 2)).isEmpty()
    }

    @Test
    fun `a bad page request reads nothing`() = runTest {
        repo.albums = listOf(TestData.album("al-1"))

        assertThat(tree.children(BrowseId.Albums.encode(), page = -1, pageSize = 10)).isEmpty()
        assertThat(tree.children(BrowseId.Albums.encode(), page = 0, pageSize = 0)).isEmpty()
        assertThat(repo.pagedReads).isEmpty()
    }

    @Test
    fun `album items are browsable and playable grid items with small cover art`() = runTest {
        repo.albums = listOf(TestData.album("al-1", name = "In Rainbows", artist = "Radiohead"))

        val album = tree.children(BrowseId.Albums.encode(), 0, 10)!!.single()

        assertThat(album.mediaMetadata.title).isEqualTo("In Rainbows")
        assertThat(album.mediaMetadata.artist).isEqualTo("Radiohead")
        assertThat(album.mediaMetadata.isBrowsable).isTrue()
        assertThat(album.mediaMetadata.isPlayable).isTrue()
        assertThat(album.mediaMetadata.mediaType).isEqualTo(MediaMetadata.MEDIA_TYPE_ALBUM)
        assertThat(album.mediaMetadata.artworkUri.toString()).isEqualTo("content://art/cover/al-al-1")
        // An album lists its tracks as a list.
        assertThat(album.extras.getInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE))
            .isEqualTo(MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM)
    }

    @Test
    fun `nodes that list albums ask for a grid, nodes that list tracks for a list`() = runTest {
        val grid = MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM
        val list = MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM
        val home = tree.children(BrowseId.Home.encode(), 0, 10)!!.associateBy { BrowseId.parse(it.mediaId) }
        val library = tree.children(BrowseId.Library.encode(), 0, 10)!!.associateBy { BrowseId.parse(it.mediaId) }

        fun MediaItem.browsableStyle() = extras.getInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE)
        fun MediaItem.playableStyle() = extras.getInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE)

        assertThat(home.getValue(BrowseId.RecentlyPlayed).browsableStyle()).isEqualTo(grid)
        assertThat(home.getValue(BrowseId.RecentlyAdded).browsableStyle()).isEqualTo(grid)
        assertThat(library.getValue(BrowseId.Albums).browsableStyle()).isEqualTo(grid)
        assertThat(home.getValue(BrowseId.Starred).playableStyle()).isEqualTo(list)
        assertThat(library.getValue(BrowseId.Artists).browsableStyle()).isEqualTo(list)
    }

    @Test
    fun `album tracks carry the album and their position`() = runTest {
        repo.songs = listOf(
            TestData.song("b", albumId = "al-1", track = 2),
            TestData.song("a", albumId = "al-1", track = 1),
        )

        assertThat(ids(BrowseId.Album("al-1"), page = 0, pageSize = 1))
            .containsExactly(BrowseId.Track("a", BrowseId.Album("al-1"), 0))
        assertThat(ids(BrowseId.Album("al-1"), page = 1, pageSize = 1))
            .containsExactly(BrowseId.Track("b", BrowseId.Album("al-1"), 1))
    }

    @Test
    fun `starred tracks are paged in the database and indexed across pages`() = runTest {
        repo.songs = (1..4).map { TestData.song("s$it", starredAt = it.toLong()) }

        assertThat(ids(BrowseId.Starred, page = 1, pageSize = 3))
            .containsExactly(BrowseId.Track("s4", BrowseId.Starred, 3))
        assertThat(repo.pagedReads.last()).isEqualTo(Triple("starred", 3, 3))
    }

    @Test
    fun `artists open their albums, genres open albums in the genre`() = runTest {
        repo.artists = listOf(TestData.artist("ar-1"))
        repo.albums = listOf(TestData.album("al-1", artistId = "ar-1"), TestData.album("al-2", artistId = "other"))
        repo.genres = listOf(Genre("Rock", songCount = 10, albumCount = 1))
        repo.genreAlbumIds = mapOf("Rock" to listOf("al-2"))

        assertThat(ids(BrowseId.Artists)).containsExactly(BrowseId.Artist("ar-1"))
        assertThat(ids(BrowseId.Artist("ar-1"))).containsExactly(BrowseId.Album("al-1"))
        assertThat(ids(BrowseId.Genres)).containsExactly(BrowseId.Genre("Rock"))
        assertThat(ids(BrowseId.Genre("Rock"))).containsExactly(BrowseId.Album("al-2"))
    }

    @Test
    fun `playlists are browsable and playable and list their tracks`() = runTest {
        repo.playlists = listOf(TestData.playlist("pl-1"))
        repo.songs = listOf(TestData.song("a"), TestData.song("b"))
        repo.playlistSongIds = mapOf("pl-1" to listOf("b", "a", "b"))

        val playlist = tree.children(BrowseId.Playlists.encode(), 0, 10)!!.single()
        assertThat(playlist.mediaMetadata.isPlayable).isTrue()
        assertThat(playlist.mediaMetadata.isBrowsable).isTrue()
        // A song twice in a playlist still gets two distinct IDs.
        assertThat(ids(BrowseId.Playlist("pl-1"))).containsExactly(
            BrowseId.Track("b", BrowseId.Playlist("pl-1"), 0),
            BrowseId.Track("a", BrowseId.Playlist("pl-1"), 1),
            BrowseId.Track("b", BrowseId.Playlist("pl-1"), 2),
        ).inOrder()
    }

    @Test
    fun `downloads list completed downloads`() = runTest {
        repo.songs = listOf(TestData.song("a"), TestData.song("b"))
        repo.downloadedIds = listOf("b")

        assertThat(ids(BrowseId.Downloads)).containsExactly(BrowseId.Track("b", BrowseId.Downloads, 0))
    }

    @Test
    fun `tracks and unknown ids have no children`() = runTest {
        assertThat(tree.children(BrowseId.Track("a", null, 0).encode(), 0, 10)).isNull()
        assertThat(tree.children(BrowseId.ShuffleAlbums.encode(), 0, 10)).isNull()
        assertThat(tree.children("song-id", 0, 10)).isNull()
    }

    @Test
    fun `item looks up a single node`() = runTest {
        repo.albums = listOf(TestData.album("al-1", name = "In Rainbows"))
        repo.songs = listOf(TestData.song("a", title = "Nude"))

        assertThat(tree.item(BrowseId.Album("al-1").encode())!!.mediaMetadata.title).isEqualTo("In Rainbows")
        assertThat(tree.item(BrowseId.Track("a", null, 0).encode())!!.mediaMetadata.title).isEqualTo("Nude")
        assertThat(tree.item(BrowseId.Library.encode())!!.mediaMetadata.title).isEqualTo("Library")
        assertThat(tree.item(BrowseId.Album("missing").encode())).isNull()
    }

    // --- Playback ---------------------------------------------------------------------------------

    @Test
    fun `a track plays its whole album from that track`() = runTest {
        repo.songs = listOf(
            TestData.song("a", albumId = "al-1", track = 1),
            TestData.song("b", albumId = "al-1", track = 2),
            TestData.song("c", albumId = "al-1", track = 3),
        )

        val request = tree.playRequest(BrowseId.Track("b", BrowseId.Album("al-1"), 1).encode())!!

        assertThat(request.songs.map { it.id }).containsExactly("a", "b", "c").inOrder()
        assertThat(request.startIndex).isEqualTo(1)
        assertThat(request.shuffleMode).isNull()
    }

    @Test
    fun `a track whose position moved still starts on that track`() = runTest {
        repo.songs = listOf(TestData.song("a"), TestData.song("b"))
        repo.playlistSongIds = mapOf("pl-1" to listOf("a", "b"))

        val request = tree.playRequest(BrowseId.Track("b", BrowseId.Playlist("pl-1"), 0).encode())!!

        assertThat(request.startIndex).isEqualTo(1)
    }

    @Test
    fun `a track without a list plays alone`() = runTest {
        repo.songs = listOf(TestData.song("a"))

        val request = tree.playRequest(BrowseId.Track("a", null, 0).encode())!!

        assertThat(request.songs.map { it.id }).containsExactly("a")
    }

    @Test
    fun `an album or playlist plays from the start`() = runTest {
        repo.songs = listOf(TestData.song("a", albumId = "al-1", track = 1), TestData.song("b", albumId = "al-1", track = 2))
        repo.playlistSongIds = mapOf("pl-1" to listOf("b", "a"))

        assertThat(tree.playRequest(BrowseId.Album("al-1").encode())!!.songs.map { it.id }).containsExactly("a", "b")
        val playlist = tree.playRequest(BrowseId.Playlist("pl-1").encode())!!
        assertThat(playlist.songs.map { it.id }).containsExactly("b", "a").inOrder()
        assertThat(playlist.startIndex).isEqualTo(0)
    }

    @Test
    fun `shuffle albums queues whole albums in disc and track order, shuffle off`() = runTest {
        repo.albums = listOf(TestData.album("x"), TestData.album("y"))
        repo.songs = listOf(
            TestData.song("x2", albumId = "x", track = 2),
            TestData.song("y1", albumId = "y", track = 1),
            TestData.song("x1", albumId = "x", track = 1),
        )

        val request = tree.playRequest(BrowseId.ShuffleAlbums.encode())!!

        val ids = request.songs.map { it.id }
        assertThat(ids).containsExactly("x1", "x2", "y1")
        assertThat(ids.indexOf("x2")).isEqualTo(ids.indexOf("x1") + 1)
        assertThat(request.shuffleMode).isEqualTo(ShuffleMode.Off)
    }

    @Test
    fun `nothing to play gives no request`() = runTest {
        assertThat(tree.playRequest(BrowseId.ShuffleAlbums.encode())).isNull()
        assertThat(tree.playRequest(BrowseId.Album("missing").encode())).isNull()
        assertThat(tree.playRequest(BrowseId.Library.encode())).isNull()
        assertThat(tree.playRequest("plain-song-id")).isNull()
    }
}
