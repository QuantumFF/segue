package com.quantumff.segue.core.playback.nowplaying

import com.quantumff.segue.core.model.ShuffleMode
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import javax.inject.Inject
import javax.inject.Singleton

/** What surfaces outside the app (home-screen widgets) show about playback. */
data class NowPlayingSnapshot(
    val songId: String? = null,
    val title: String? = null,
    val artist: String? = null,
    val coverArtId: String? = null,
    /** The user's intent (play when ready), so buffering does not flicker the play button. */
    val isPlaying: Boolean = false,
    val shuffleMode: ShuffleMode = ShuffleMode.Off,
) {
    val hasTrack: Boolean get() = songId != null

    companion object {
        val Empty = NowPlayingSnapshot()
    }
}

/** Hears now-playing changes from the playback service, on the main thread. */
fun interface NowPlayingListener {
    fun onNowPlayingChanged(snapshot: NowPlayingSnapshot)
}

/**
 * Pushes the service's player state to [NowPlayingListener]s (contributed with `@IntoSet`). Driven
 * by player events only; repeated identical snapshots are dropped, so listeners see real changes.
 */
@Singleton
class NowPlayingPublisher @Inject constructor(private val listeners: Set<@JvmSuppressWildcards NowPlayingListener>) {
    private var last: NowPlayingSnapshot? = null

    fun publish(snapshot: NowPlayingSnapshot) {
        if (snapshot == last) return
        last = snapshot
        listeners.forEach { it.onNowPlayingChanged(snapshot) }
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal interface NowPlayingModule {
    /** Empty unless a module (the widgets) contributes listeners. */
    @Multibinds
    fun listeners(): Set<NowPlayingListener>
}
