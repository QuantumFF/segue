# Segue: build plan

Segue is a modern, minimalist Android client for Navidrome, built on the Subsonic API with OpenSubsonic extensions.
Its main goals are a rich feature set, excellent performance, and good battery life.

- App ID: `com.quantumff.segue`. Min SDK 29, target/compile SDK 37. License GPL-3.0-or-later.
- Visual design: a hybrid of Feishin and Symfonium that leans toward Feishin. See `docs/DESIGN.md`.

## Stack
- Kotlin, Jetpack Compose, Material 3
- Media3 (ExoPlayer + `MediaLibraryService`): playback, media session, Android Auto
- Room as the single source of truth for library data. The UI reads only from Room; the network refreshes it.
- OkHttp + Retrofit + kotlinx.serialization for the Subsonic/OpenSubsonic API
- Coil 3 for images. Cover art is requested at the size it is displayed (`getCoverArt?size=`).
- Hilt for DI, DataStore for preferences, WorkManager for downloads and library sync, Paging 3 for large lists

## Modules
- `app`: entry point, navigation host, Hilt application
- `core:model`: plain Kotlin domain models
- `core:network`: Subsonic client, auth (token + salt), OpenSubsonic extension detection
- `core:database`: Room entities, DAOs, migrations
- `core:data`: repositories, library sync
- `core:datastore`: settings
- `core:designsystem`: theme, tokens, shared components
- `core:playback`: `MediaLibraryService`, player setup, `AlbumShuffleOrder`, queue sync, scrobbling
- `feature:*`: home, library (albums/artists/songs/genres), album, artist, playlist, search, nowplaying, queue, settings, login, downloads

## Signature features
### Server queue sync
- Uses `savePlayQueue` / `getPlayQueue`. When the server advertises the OpenSubsonic `indexBasedQueue` extension, use the index-based variants instead, because they handle duplicate tracks in the queue correctly.
- Upload is event-driven and debounced. It runs on track change, queue edit, pause, seek, and app backgrounding. There is never periodic polling during playback.
- Download happens on app start and when the app returns to the foreground. Compare the server's `changed`/`changedBy` with local state. If another client saved a newer queue, offer "Continue from <device>", or adopt it automatically if the user enabled that setting.
- Upload the queue in its effective play order, so other clients see what is actually playing.

### Album shuffle
- Shuffle modes: Off / Tracks / Albums.
- Albums mode groups the queue into album blocks, keyed by album ID. The album blocks are shuffled with Fisher-Yates. Tracks inside each block keep disc and track order. The current album stays first.
- Implemented as a custom Media3 `ShuffleOrder`, so the underlying queue is never reordered and unshuffle is lossless. Inserting and removing tracks must keep album blocks together.
- There is also a "Shuffle albums" entry point for the whole library, an artist, a genre, or a playlist.

## Performance
- Baseline Profiles, R8 full mode, Compose stability (immutable UI models, stable lambdas)
- Incremental library sync (`ifModifiedSince`, newest album lists)
- Paging for large libraries; bounded image cache

## Battery
- ExoPlayer audio offload when the screen is off (gapless-compatible)
- Burst-prefetch the current and next tracks into Media3 `SimpleCache`, so the radio can go idle
- No timer polling. Scrobbles are batched and queue sync is debounced.
- WorkManager constraints for downloads and sync. The service is foreground only while playing.

## Features (all phases)
- Gapless, ReplayGain (track/album), transcoding per network type, offline downloads (pin albums/playlists)
- Scrobbling via Navidrome, synced lyrics, stars/ratings, playlist CRUD, internet radio
- Instant mix (`getSimilarSongs`), sleep timer (including "end of album"), widgets, Android Auto, multi-server
- Later: Chromecast, optional crossfade, EQ

## Phases
1. Scaffold + design spec
2. Network/data layer ‖ design system + app shell
3. Playback core (album shuffle, queue sync) ‖ browse screens
4. Now playing / queue UI, login, downloads/offline, scrobbling, offload/caching
5. Lyrics, widgets, Android Auto, baseline profiles, polish, end-to-end test against a Navidrome Docker container
