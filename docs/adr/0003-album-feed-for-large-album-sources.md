# Queue large album sources lazily as an album feed

**Superseded by ADR-0008:** album plays now queue every song at once, and the album feed is gone.

"Play albums" and "Shuffle albums" on a large album source (the whole library, a big genre) would mean reading and queueing tens of thousands of songs up front, so the playback service queues it as an album feed: a first window of about 200 tracks of whole albums, then about 100 more whenever fewer than 30 remain after the current song. Only what is queued is ever loaded.

Shuffled feeds order albums by a hash of the seed and the album ID, not by a stored permutation. The order can be rebuilt from the seed, so the feed persists in Room as just source, order, seed, cursor and a fingerprint of the queue's song IDs. That's enough to survive process death, and albums added to the library later slot in without reshuffling the rest.

A feed ends for good on any queue change it did not make (add, remove, move, replace, clear), and on restore it resumes only if the restored queue still matches its fingerprint. The feed never queues around a user's edit: the edit wins and the feed stops. Queue sync needs nothing special: it uploads what is actually queued.
