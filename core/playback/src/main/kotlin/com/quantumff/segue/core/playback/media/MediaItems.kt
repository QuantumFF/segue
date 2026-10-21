package com.quantumff.segue.core.playback.media

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.HeartRating
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.quantumff.segue.core.model.QueueItem
import com.quantumff.segue.core.model.ReplayGain
import com.quantumff.segue.core.model.Song

/**
 * Media URIs that do not carry credentials. They are resolved to authenticated server URLs only
 * when loaded (see [com.quantumff.segue.core.playback.cache.SegueUriResolver]), so the queue held
 * by the session, the notification, and other controllers never contains a token, and the stream
 * quality follows the network type at load time.
 */
object SegueUris {
    const val SCHEME = "segue"
    const val SONG = "song"
    const val COVER = "cover"
    const val SIZE = "size"

    fun song(songId: String): Uri = Uri.Builder().scheme(SCHEME).authority(SONG).appendPath(songId).build()

    fun cover(coverArtId: String, size: Int): Uri = Uri.Builder().scheme(SCHEME).authority(COVER)
        .appendPath(coverArtId)
        .appendQueryParameter(SIZE, size.toString())
        .build()
}

private object Extras {
    const val ALBUM_ID = "segue.albumId"
    const val ARTIST_ID = "segue.artistId"
    const val COVER_ART_ID = "segue.coverArtId"
    const val STARRED = "segue.starred"
    const val TRACK_GAIN = "segue.rg.trackGain"
    const val ALBUM_GAIN = "segue.rg.albumGain"
    const val TRACK_PEAK = "segue.rg.trackPeak"
    const val ALBUM_PEAK = "segue.rg.albumPeak"
    const val FALLBACK_GAIN = "segue.rg.fallbackGain"
}

/** Artwork size requested for the notification and lock screen. */
private const val ARTWORK_SIZE = 512
private const val MILLIS_PER_SECOND = 1_000L

/**
 * A playable item for [this] song. Metadata extras carry what the player needs later: the album ID
 * for "end of album", ReplayGain values, and the starred state.
 */
fun Song.toMediaItem(): MediaItem {
    val extras = Bundle().apply {
        albumId?.let { putString(Extras.ALBUM_ID, it) }
        artistId?.let { putString(Extras.ARTIST_ID, it) }
        coverArtId?.let { putString(Extras.COVER_ART_ID, it) }
        putBoolean(Extras.STARRED, isStarred)
        replayGain?.let { gain ->
            gain.trackGain?.let { putFloat(Extras.TRACK_GAIN, it) }
            gain.albumGain?.let { putFloat(Extras.ALBUM_GAIN, it) }
            gain.trackPeak?.let { putFloat(Extras.TRACK_PEAK, it) }
            gain.albumPeak?.let { putFloat(Extras.ALBUM_PEAK, it) }
            gain.fallbackGain?.let { putFloat(Extras.FALLBACK_GAIN, it) }
        }
    }
    val metadata = MediaMetadata.Builder()
        .setTitle(title)
        .setArtist(artist)
        .setAlbumTitle(album)
        .setAlbumArtist(displayAlbumArtist ?: artist)
        .setTrackNumber(track)
        .setDiscNumber(discNumber)
        .setReleaseYear(year)
        .setGenre(genre)
        .setDurationMs(durationSeconds.takeIf { it > 0 }?.let { it * MILLIS_PER_SECOND })
        .setArtworkUri(coverArtId?.let { SegueUris.cover(it, ARTWORK_SIZE) })
        .setUserRating(HeartRating(isStarred))
        .setIsBrowsable(false)
        .setIsPlayable(true)
        .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
        .setExtras(extras)
        .build()
    return MediaItem.Builder()
        .setMediaId(id)
        .setUri(SegueUris.song(id))
        .setMediaMetadata(metadata)
        .build()
}

/** Restores the `segue://` URI that a controller may strip when it sends items to the session. */
fun MediaItem.withPlayableUri(): MediaItem =
    if (localConfiguration != null) this else buildUpon().setUri(SegueUris.song(mediaId)).build()

/** The item's album ID; null for a track without an album. */
val MediaItem.albumKey: String? get() = mediaMetadata.extras?.getString(Extras.ALBUM_ID)

val MediaItem.coverArtId: String? get() = mediaMetadata.extras?.getString(Extras.COVER_ART_ID)

val MediaItem.isStarred: Boolean get() = mediaMetadata.extras?.getBoolean(Extras.STARRED) ?: false

/** The same item with its starred state changed, for the favorite button. */
fun MediaItem.withStarred(starred: Boolean): MediaItem {
    val extras = Bundle(mediaMetadata.extras ?: Bundle()).apply { putBoolean(Extras.STARRED, starred) }
    val metadata = mediaMetadata.buildUpon().setUserRating(HeartRating(starred)).setExtras(extras).build()
    return buildUpon().setMediaMetadata(metadata).build()
}

val MediaItem.replayGain: ReplayGain?
    get() {
        val extras = mediaMetadata.extras ?: return null
        fun float(key: String) = if (extras.containsKey(key)) extras.getFloat(key) else null
        val gain = ReplayGain(
            trackGain = float(Extras.TRACK_GAIN),
            albumGain = float(Extras.ALBUM_GAIN),
            trackPeak = float(Extras.TRACK_PEAK),
            albumPeak = float(Extras.ALBUM_PEAK),
            fallbackGain = float(Extras.FALLBACK_GAIN),
        )
        return gain.takeUnless { it == ReplayGain() }
    }

fun MediaItem.toQueueItem(timelineIndex: Int): QueueItem = QueueItem(
    songId = mediaId,
    timelineIndex = timelineIndex,
    title = mediaMetadata.title?.toString().orEmpty(),
    artist = mediaMetadata.artist?.toString(),
    album = mediaMetadata.albumTitle?.toString(),
    albumId = albumKey,
    coverArtId = mediaMetadata.extras?.getString(Extras.COVER_ART_ID),
    durationMillis = mediaMetadata.durationMs ?: 0,
    isStarred = isStarred,
)
