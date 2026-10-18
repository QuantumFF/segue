# Now Playing is an overlay over the shell, not a navigation destination

Now Playing (`NowPlayingOverlay`) draws over the whole shell, including the nav host, the navigation chrome and the mini player. Whether it's open is the saved `SegueAppState.isNowPlayingOpen` flag, not a route on the back stack. Dragging Now Playing down, or the predictive back gesture, has to show the screen it will collapse to. `NavHost` only composes the screen behind the current destination while a transition runs, so a drag outside a transition showed an empty black window. As an overlay, the screen behind stays composed and drawn the whole time. Screen readers skip it while Now Playing is open.

As a result, opening Now Playing doesn't touch the back stack. Its own back handlers close an open panel and then collapse it, and going to an album or artist from Now Playing collapses it first and then navigates. Its ViewModels are scoped to the activity; their flows stop with `WhileSubscribed` once it closes.

## Considered options

- **Keep the route and scrub the nav host's pop transition from the drag** (by feeding synthetic back events to the dispatcher). The transition's easing and fade don't track the finger one to one, and it fights Now Playing's own predictive back handler.
- **A `dialog` destination,** which keeps the screen below drawn. It runs in a separate window, which brings its own edge-to-edge, system bar and transition problems, and the chrome hidden for the dialog route would be missing from the screen revealed under the drag.
