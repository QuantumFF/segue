# Queue sync is event-driven and never polls

Queue sync saves the queue with `savePlayQueue` on events (song change, queue edit, pause, seek, app backgrounding), debounced, and reads the server's saved queue only on app start and on return to the foreground. Nothing polls during playback. A live sync would need a timer that keeps the radio awake while music plays from the buffer, and Segue's battery goal rules that out. The cost is that a queue saved by another client is noticed only on the next start or return, as a *Continue from &lt;device&gt;* offer or an automatic adoption, never mid-session.

The queue is uploaded as it is, because the queue order is the play order (ADR-0002), so other clients see what is actually playing. When the server advertises the OpenSubsonic `indexBasedQueue` extension, the index-based variants are used instead, because they handle duplicate songs in the queue correctly.
