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
- `core:playback`: `MediaLibraryService`, player setup, album feeds (enqueue-time album shuffle), queue sync, scrobbling
- `feature:*`: home, library (albums/artists/songs/genres), album, artist, playlist, search, nowplaying, queue, settings, login, downloads

## Signature features
### Server queue sync
- Uses `savePlayQueue` / `getPlayQueue`. When the server advertises the OpenSubsonic `indexBasedQueue` extension, use the index-based variants instead, because they handle duplicate tracks in the queue correctly.
- Upload is event-driven and debounced. It runs on track change, queue edit, pause, seek, and app backgrounding. There is never periodic polling during playback.
- Download happens on app start and when the app returns to the foreground. Compare the server's `changed`/`changedBy` with local state. If another client saved a newer queue, offer "Continue from <device>", or adopt it automatically if the user enabled that setting.
- Upload the queue as it is: the queue order is the play order (shuffling happens only when music is queued), so other clients see what is actually playing.
- The queue screen's ⋮ also has manual "Upload queue" and "Download queue". They skip the debounce, the newer-than checks and the queue sync setting. A manual upload declines any offered queue. A manual download takes whatever the server holds, this device's own save included, and the result counts as accounted for, so it is not offered again.

### Album shuffle (at enqueue time)
- Shuffling happens only when music is added to the queue, never to a running queue.
  - Every "Shuffle" action writes its songs into the queue already shuffled. **Tracks** shuffle means a shuffled track order. **Albums** shuffle means a shuffled album order, with each album's tracks in disc and track order.
  - The player has no shuffle mode. There is no shuffle button in Now Playing, the player bar, the notification, Android Auto or the widgets. There is no "default shuffle mode" setting.
- Why: users start a queue and then listen to it; they do not want it reshuffled underneath them. They do want to add shuffled albums to it ("Shuffle albums → Add to queue"). Because the queue order is always the play order:
  - The snapshot and server queue sync store and upload the plain queue.
  - The queue screen edits exactly what plays.
  - There is no in-place shuffle state to persist, restore or map between orders.
- This replaces the earlier Off / Tracks / Albums player modes and the custom Media3 `ShuffleOrder` (album blocks, play-order mapping, shuffle persistence in the snapshot, album-block queue headers), which are deleted.
- "Play albums" and "Shuffle albums" queue whole albums from a source: the Library albums view as sorted and filtered, a genre's album grid, an artist (in the artist page's order), a genre (always the albums tagged with it, as its grid lists them), a playlist, or the whole library (Android Auto's "Shuffle albums" and a voice "play some music" with an empty queue).
  - Ordered keeps the source order; shuffled uses a seeded album order. Each album keeps disc and track order.
  - Placement is replace (play now), play next, or add to queue (DESIGN.md §5.9).
- Large libraries are queued lazily as an **album feed**, run by the playback service:
  - The first window is whole albums totalling about 200 tracks. When fewer than 30 tracks remain after the current one, the feed appends about 100 more. Album IDs are cheap to list. Songs are read 8 albums at a time, so only what is queued is ever loaded.
  - The shuffle sorts albums by a hash of the seed and the album ID. The order can therefore be rebuilt from the seed instead of being stored, and new albums slot in without reshuffling the rest. Albums already in the queue are skipped.
  - The feed is persisted in Room (`album_feed`: source, order, seed, cursor, and a fingerprint of the queue's song IDs), so it survives process death. On restore it resumes only if the restored queue still matches its fingerprint.
  - Any change the feed did not make ends it for good: add, remove, move, replace or clear. A "Play next" that lands before other upcoming tracks queues one window and does not extend.
  - Queue sync needs nothing special: it uploads what is actually queued.

## Performance
- Baseline Profiles, R8 full mode, Compose stability (immutable UI models, stable lambdas)
- Incremental library sync (`ifModifiedSince`, newest album lists)
- Paging for large libraries; bounded image cache

## Battery
- ExoPlayer audio offload when the screen is off (gapless-compatible)
- ReplayGain is player volume by default, which offload honors. The opt-in "Boost quiet tracks"
  needs an `AudioProcessor`, which offload skips, so offload stays off for as long as boost is on
  (one switch per setting change, never per track, so gapless transitions stay intact)
- Burst-prefetch the current and next tracks into Media3 `SimpleCache`, so the radio can go idle
- No timer polling. Scrobbles are batched and queue sync is debounced.
- WorkManager constraints for downloads and sync. The service is foreground only while playing.

## Features (all phases)
- Gapless, ReplayGain (track/album, see "ReplayGain"), transcoding per network type, offline downloads (pin albums/playlists)
- Scrobbling via Navidrome, synced lyrics, stars/ratings, playlist CRUD, internet radio
- Instant mix (`getSimilarSongs`), sleep timer (including "end of album"), widgets, Android Auto, multi-server
- Later: Chromecast, optional crossfade, EQ

## ReplayGain
- **Source:** Off, Track or Album. A missing value falls back to the other one, then to
  OpenSubsonic `fallbackGain`; a track without any plays at unity. A 0 dB gain whose peak is
  absent, 0 or exactly 1 counts as missing: Navidrome's scanner up to 0.54 stored absent tags as
  gain 0 and peak 1, and libraries scanned then still serve them.
- **Normalisation** mirrors Symfonium's "Replay gain normalisation": the loudness every track is
  levelled to. −18 LUFS (ReplayGain 2.0, the default) applies Navidrome's gains as they are;
  −23 LUFS (EBU R128) is 5 dB quieter. Symfonium also has "Disabled", which skips its conversion
  between Opus R128 tags (−23 LUFS reference) and ReplayGain tags (−18 LUFS). Navidrome already
  converts R128 gains to the −18 LUFS reference (+5 dB) when it scans, so "Disabled" would equal
  −18 LUFS here and is left out. The old free preamp migrates to the nearest target.
- **Without boost** (default): player volume, which offload honors. Volume can only attenuate, so
  positive gains stop at unity. The volume changes on the main thread at the track transition, a
  few milliseconds late in a gapless transition.
- **Boost quiet tracks:** an `AudioProcessor` in the audio sink applies the gain, up to +15 dB.
  The sink flushes processors at every track change, gapless ones included, with the new track's
  timeline period, so each track gets its own gain in sync with its audio. A known peak caps the
  gain so the loudest sample just reaches full scale; without one, a peak limiter (instant attack,
  100 ms release) holds amplified audio below −0.2 dBFS. Offload stays off while boost is on.
  The processor stays in the PCM chain with boost off, copying buffers through at unity: the sink
  only re-reads which processors are active when it flushes (a track change or a real seek), so a
  processor that left the chain could not pick up boost mid-track. Switching boost during a track
  briefly plays the audio already buffered at the old level.

## Phases
1. Scaffold + design spec
2. Network/data layer ‖ design system + app shell
3. Playback core (album shuffle, queue sync) ‖ browse screens
4. Now playing / queue UI, login, downloads/offline, scrobbling, offload/caching
5. Lyrics, widgets, Android Auto, baseline profiles, polish, end-to-end test against a Navidrome Docker container
