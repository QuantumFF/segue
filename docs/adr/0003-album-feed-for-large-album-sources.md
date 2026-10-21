# Queue large album sources lazily as an album feed

"Play albums" and "Shuffle albums" on a large album source (the whole library, a big genre) would mean reading and queueing tens of thousands of songs up front, so the playback service queues it as an album feed: a first window of about 200 tracks of whole albums, then about 100 more whenever fewer than 30 remain after the current song. Only what is queued is ever loaded.

"Shuffle all" (a tracks shuffle of every song in the library) is deliberately not a feed: it queues every song that can play at once, so the whole library is in the queue and the user's own edits (play next, add to queue) don't cut it short the way they end a feed. The playback service reads the songs from Room itself, because a list that long would not fit through the session's binder, and queue sync posts the queue as a form instead of putting the IDs in the URL. The cost is that every queue save, local and to the server, carries the whole library.

Shuffled feeds order albums by a hash of the seed and the album ID, not by a stored permutation. The order can be rebuilt from the seed, so the feed persists in Room as just source, order, seed, cursor and a fingerprint of the queue's song IDs. That's enough to survive process death, and albums added to the library later slot in without reshuffling the rest.

A feed ends for good on any queue change it did not make (add, remove, move, replace, clear), and on restore it resumes only if the restored queue still matches its fingerprint. The feed never queues around a user's edit: the edit wins and the feed stops. Queue sync needs nothing special: it uploads what is actually queued.
