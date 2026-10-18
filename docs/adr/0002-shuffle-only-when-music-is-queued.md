# Shuffle only when music is queued; the player has no shuffle mode

Every "Shuffle" action writes its songs into the queue already shuffled (tracks: a shuffled song order; albums: a shuffled album order with each album in disc and track order), and a running queue is never reshuffled. So the queue order is always the play order. Users start a queue and listen to it; they don't want it reshuffled underneath them, but they do want to add shuffled albums to it ("Shuffle albums → Add to queue"). Because the two orders are the same, the saved queue, queue sync and the Queue panel all deal with the plain queue, and there is no shuffle state to persist, restore or map between orders.

As a result there is no shuffle button in Now Playing, the player bar, the notification, Android Auto or the widgets, and no "default shuffle mode" setting. Don't add one back without revisiting this decision.

## Considered options

- **Player shuffle modes (Off / Tracks / Albums) on a custom Media3 `ShuffleOrder`.** This was built first: album blocks, play-order mapping in the controller, player and queue sync, shuffle columns in the saved queue, and album-block queue headers. It was removed (6e27c60) because users didn't want a running queue reshuffled, and every queue consumer had to map between queue order and play order.
