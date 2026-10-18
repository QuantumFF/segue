# Room is the single source of truth for library data

Screens read library data only from Room; the network refreshes Room and never feeds a screen directly. We chose this over network-first screens with a cache because offline is first-class: a screen that renders from Room works the same online, offline and while the server is down, and network trouble shows up as a status chip instead of a blocking error (DESIGN.md §5.8). The cost is that every server field a screen shows needs a column, a migration and a sync path, so a new screen can't just call the API.

Lists only the server can compute, such as `search3` results and similar songs, are the exception: they are fetched on demand, and offline the user gets what Room already holds (full-text search) or nothing (instant mix).
