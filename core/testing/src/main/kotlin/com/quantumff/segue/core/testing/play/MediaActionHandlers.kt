package com.quantumff.segue.core.testing.play

import com.quantumff.segue.core.data.download.DownloadRepository
import com.quantumff.segue.core.data.play.MediaActionHandler
import com.quantumff.segue.core.data.play.PlayIntents
import com.quantumff.segue.core.data.play.PlayTargetSongs
import com.quantumff.segue.core.data.repository.AlbumRepository
import com.quantumff.segue.core.data.repository.ArtistRepository
import com.quantumff.segue.core.data.repository.PlaylistRepository
import com.quantumff.segue.core.data.repository.StarRatingRepository
import com.quantumff.segue.core.testing.repository.FakeAlbumRepository
import com.quantumff.segue.core.testing.repository.FakeArtistRepository
import com.quantumff.segue.core.testing.repository.FakeDownloadRepository
import com.quantumff.segue.core.testing.repository.FakePlaylistRepository
import com.quantumff.segue.core.testing.repository.FakeStarRatingRepository

/** The real [MediaActionHandler] over fakes; pass the ones a test drives. */
fun mediaActionHandler(
    playIntents: PlayIntents = RecordingPlayIntents(),
    downloads: DownloadRepository = FakeDownloadRepository(),
    stars: StarRatingRepository = FakeStarRatingRepository(),
    albums: AlbumRepository = FakeAlbumRepository(),
    playlists: PlaylistRepository = FakePlaylistRepository(),
    artists: ArtistRepository = FakeArtistRepository(),
) = MediaActionHandler(playIntents, PlayTargetSongs(albums, playlists, artists), downloads, stars)
