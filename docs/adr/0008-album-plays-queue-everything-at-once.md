# Album plays queue every song at once

"Play albums", "Shuffle albums" and "Shuffle all" put every song of their album source that can play into the queue at once: all the albums, each whole in disc and track order (in the source's order, or shuffled), or every song in a tracks shuffle. Users asked for the whole library in the queue, and an album feed that stopped growing as soon as they used Play next or Add to queue didn't give them that. This supersedes ADR-0003.

Three things make a queue the size of the library work. The playback service reads the songs from Room itself, since a library's worth of media items would not fit through the session's binder; the app sends only the request. Queue sync posts the queue's song IDs as a form (OpenSubsonic `formPost`, ADR-0004), since they are far too long for a URL. And the running feed's state in Room (the `album_feed` table) is gone, along with restoring a feed after process death: the queue snapshot already holds everything.

The cost is size: every queue save carries the whole queue. On each song change, pause or seek (debounced), the local snapshot rewrites one row per song and the upload posts every song ID, about 26 bytes each.

## Considered options

- **Keep the album feed (ADR-0003)**: a first window of about 200 tracks, then about 100 more near the end. Cheap for any library size, but any edit the feed did not make ended it for good, so the rest of the library never came, and the queue never showed what was coming.
- **Keep the feed, but let it survive the user's edits.** Still never the whole library in the queue.
