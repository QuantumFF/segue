package com.quantumff.segue.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.quantumff.segue.core.database.model.AlbumEntity
import com.quantumff.segue.core.database.model.ArtistEntity
import com.quantumff.segue.core.database.model.SongEntity

fun inMemoryDatabase(): SegueDatabase =
    Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SegueDatabase::class.java)
        .allowMainThreadQueries()
        .build()

fun artist(id: String, name: String = id, serverId: String = "s1") =
    ArtistEntity(serverId = serverId, id = id, name = name, sortName = name.lowercase())

fun album(id: String, name: String = id, artistId: String? = null, artist: String? = null, serverId: String = "s1") =
    AlbumEntity(
        serverId = serverId,
        id = id,
        name = name,
        sortName = name.lowercase(),
        artist = artist,
        artistId = artistId,
    )

fun song(
    id: String,
    title: String = id,
    albumId: String? = null,
    disc: Int? = null,
    track: Int? = null,
    artist: String? = null,
    serverId: String = "s1",
) = SongEntity(
    serverId = serverId,
    id = id,
    title = title,
    sortName = title.lowercase(),
    albumId = albumId,
    artist = artist,
    discNumber = disc,
    track = track,
)

/** This song with the artist and album sort keys the sync derives from its names. */
fun SongEntity.withSortKeys() = copy(artistSortName = artist?.lowercase(), albumSortName = album?.lowercase())
