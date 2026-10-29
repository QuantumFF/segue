# Segue: design spec

Implementation agents follow this file exactly. If something is not covered here, use the closest pattern in this file and stay consistent with it. Do not invent new visual styles.
Values are in dp/sp unless noted. "Feishin" values come from its v1.17.0 renderer source (theme tokens, CSS modules).

## 1. Principles

- **Content first, chrome last.** Album art and titles carry the color. Neutral gray surfaces, no decorative gradients, no card borders, no shadows on list content.
- **Feishin's look, Symfonium's hands.** Visual language comes from Feishin (dark neutral palette, heavy 800-weight titles, album-art hero headers, blurred-art player, table-like track lists). Mobile ergonomics come from Symfonium (gesture-driven mini player and Now Playing, swipe actions in the queue, a customisable home, dense readable lists).
- **Dense but calm.** 56dp rows, 1-line metadata, muted secondary text. One accent color, used sparingly: the active track, the primary action, selected state.
- **Cheap to draw.** No real-time blur, no shadows inside scrolling lists, cover art fetched at display size. Animate with transform and alpha only.
- **Offline is first-class.** Every screen renders from Room. Network problems show up as a small status chip, not as a blocking error (ADR-0001).

| Taken from Feishin | Taken from Symfonium |
|---|---|
| Palette (#0C0C0C bg, #141414 surface, #3574FC accent), Inter font | Mini player: swipe to skip, drag up to expand |
| Hero header: art + 800-weight title, tinted with the art color or blurred art behind it | Now Playing art gestures; swipe up opens the queue |
| Header bar that fades in on scroll, with title and play button | Queue: swipe right = play next, swipe left = remove, with haptics |
| Feature carousel on Home with blurred art backdrop | Home sections can be reordered and toggled, with a size per section |
| Fullscreen player with Up Next / Lyrics / Related panels | Compact-header option, fast scroller with letter bubble |
| Table-style track list with columns (tablet), pills for genres | Bottom navigation, bottom-sheet context menus |
| Lyrics: active line full opacity, others dimmed and scaled | |

## 2. Tokens (`core:designsystem`)

### 2.1 Color

Neutrals are pure gray in every mode and never tinted by dynamic color. That is the Feishin look.

| Role (M3 `ColorScheme`) | Dark (default) | Black (AMOLED) | Light |
|---|---|---|---|
| `background` / `surface` | `#0C0C0C` | `#000000` | `#F5F5F5` |
| `surfaceContainerLowest` | `#080808` | `#000000` | `#FFFFFF` |
| `surfaceContainerLow` | `#101010` | `#050505` | `#EFEFEF` |
| `surfaceContainer` (nav bar, mini player) | `#141414` | `#0A0A0A` | `#E9E9E9` |
| `surfaceContainerHigh` (sheets, menus, placeholders) | `#1C1C1C` | `#121212` | `#E1E1E1` |
| `surfaceContainerHighest` (banners, inputs, selected chip) | `#242424` | `#1A1A1A` | `#D8D8D8` |
| `onSurface` / `onBackground` | `#E1E1E1` | `#E1E1E1` | `#191919` |
| `onSurfaceVariant` (muted text, icons) | `#969696` | `#8C8C8C` | `#505050` |
| `outline` | `#3B3B3B` | `#2E2E2E` | `#BDBDBD` |
| `outlineVariant` (dividers, pill borders) | `#FFFFFF` @ 10% | `#FFFFFF` @ 8% | `#000000` @ 6% |
| `primary` (fixed default accent) | `#3574FC` | `#3574FC` | `#0066E0` |
| `onPrimary` | `#FFFFFF` | `#FFFFFF` | `#FFFFFF` |
| `primaryContainer` | primary @ 16% over surface | same | primary @ 12% over surface |
| `error` | `#E5484D` | `#E5484D` | `#D93025` |
| `success` (extended: download done) | `#3DCC5C` | `#3DCC5C` | `#1E8E3E` |
| `scrim` | `#000000` @ 60% | @ 70% | `#000000` @ 40% |

- Theme setting: System (default) / Light / Dark / Black. "Black" replaces Dark when the system is in dark mode.
- **Accent source** setting:
  - **Fixed** (default). Presets: Blue `#3574FC`, Teal `#14B8A6`, Violet `#8B5CF6`, Pink `#EC4899`, Orange `#F97316`, Green `#22C55E`. Light theme uses each preset darkened until it reaches 4.5:1 on `#F5F5F5`.
  - **Material You** (API 31+). Take only `primary`, `secondary`, `tertiary` and their `on*`/container roles from `dynamicDark/LightColorScheme`. Keep the neutral table above.
  - **Album art**. `primary` follows the current track's art color (see below), animated over 300ms.
- **Art color** (used for hero tint, Now Playing, and the "Album art" accent): run `androidx.palette` on the 32px pre-blur bitmap (§6). Pick vibrant, then dominant, then muted swatches. Clamp HSL as follows:
  - Hero/Now Playing tint: S ≤ 0.55, L in [0.18, 0.30] for dark themes, L in [0.82, 0.90] for light.
  - Accent use: L in [0.55, 0.70] for dark, L in [0.35, 0.45] for light.
  - Fallback: `surfaceContainerHigh`.
  - Cache per `coverArtId` in a memory LRU (200 entries).

### 2.2 Typography

Inter variable font (OFL), bundled at `core/designsystem/src/main/res/font/inter_variable.ttf`. Use `FontVariation.weight`. Fallback is the system sans, which covers CJK.

| M3 style | Size / line | Weight | Use |
|---|---|---|---|
| `displaySmall` | 34/40 | 800 | Hero title on expanded width |
| `headlineMedium` | 28/34 | 800 | Hero title on phone (Feishin: 1.75 to 2.75rem, weight 800) |
| `headlineSmall` | 24/30 | 800 | Top-level screen titles ("Home", "Library") |
| `titleLarge` | 20/26 | 700 | Section headers, Now Playing title (22/28 there) |
| `titleMedium` | 16/22 | 600 | Sheet headers, dialog titles |
| `titleSmall` | 14/20 | 500 | Card title, track row title, mini player title |
| `bodyLarge` | 16/24 | 400 | Body text, Now Playing artist |
| `bodyMedium` | 14/20 | 400 | Settings summaries, biography |
| `bodySmall` | 13/18 | 400 | Secondary line in rows and cards (muted) |
| `labelLarge` | 14/20 | 600 | Buttons, tabs |
| `labelMedium` | 12/16 | 500 | Chips, pills, durations |
| `labelSmall` | 11/14 | 600 | Overlines ("ALBUM"), uppercase, +0.08em letter spacing |

Durations, times, track numbers and counts use `fontFeatureSettings = "tnum"`. Titles truncate to 1 line with an ellipsis. The only marquee (`basicMarquee`) is the Now Playing title.

### 2.3 Spacing, size, radius

| Spacing token | `xxs` | `xs` | `sm` | `md` | `lg` | `xl` | `xxl` | `xxxl` |
|---|---|---|---|---|---|---|---|---|
| dp | 2 | 4 | 8 | 12 | 16 | 24 | 32 | 48 |

- Screen side margin: 16 on compact width, 24 on medium and expanded. Grid gutter: 12. Gap between home sections: 24.
- Minimum touch target: 48. Icons: 24 by default, 20 inside rows, 28 for the Now Playing transport buttons.
- Icons: **Lucide** (ISC, same set as Feishin), stroke 2. Vendor the ImageVectors that are used, or use `com.composables:icons-lucide`. Use filled variants for active play, pause and favorite.

| Radius token | dp | Use |
|---|---|---|
| `xs` | 2 | Progress tracks, explicit badge |
| `sm` | 4 | Row thumbnails, mini player art |
| `md` | 6 | Album cards, hero art, feature carousel items |
| `lg` | 12 | Now Playing art, banners, cards on Settings/Login |
| `xl` | 16 | Bottom-sheet top corners, dialogs |
| `full` | 50% | Chips, pills, play buttons, artist images |

### 2.4 Elevation and blur

- Use flat tonal surfaces only. M3 `tonalElevation = 0` everywhere. Use the container roles above.
- Only three shadows are allowed, all outside scrolling lists:
  - Hero art: 8dp shadow, black 50%.
  - Now Playing art: 24dp shadow, black 30%, y offset 8dp.
  - Feature carousel art: 20dp shadow, black 50%.
- Blur is always a **pre-blurred low-res bitmap** (§6). Never use `Modifier.blur` or `RenderEffect` on live content.
- To stop gradient banding, every art backdrop gets a tiled 64px noise PNG overlay at 4% alpha (Feishin does the same).

### 2.5 Motion

| Token | Duration | Easing | Use |
|---|---|---|---|
| `short` | 100ms | `standard` | Press states, icon toggles |
| `medium` | 200ms | `standard` | Fades, image crossfade, chip select, card press scale 0.97 |
| `long` | 300ms | `emphasizedDecel` in, `emphasizedAccel` out | Screen transitions, panels, sheets, accent change |
| `lyric` | 600ms | `expoOut` | Lyric line opacity and scale |
| `player` | spring | dampingRatio 0.85, stiffness 400 | Mini player to Now Playing, settle after drag |

- Curves:
  - `standard` = `CubicBezier(0.2, 0, 0, 1)`
  - `emphasizedDecel` = `(0.05, 0.7, 0.1, 1)`
  - `emphasizedAccel` = `(0.3, 0, 0.8, 0.15)`
  - `expoOut` = `(0.16, 1, 0.3, 1)`
- Navigation: shared-axis X (fade plus 30dp slide, `long`) for forward and back. The fade goes through rather than across: the leaving screen fades out over the first 100ms (`standard`), then the entering screen fades in over the last 200ms (`emphasizedDecel`), so the two are never both visible. Bottom-tab switches crossfade (`medium`).
- When `ANIMATOR_DURATION_SCALE == 0` or the reduce-motion setting is on, every transition becomes a `medium` crossfade.

## 3. App shell

| Width class | Navigation | Player |
|---|---|---|
| Compact (< 600) | `NavigationBar`, 64 tall, `surfaceContainer`, no tonal elevation | Mini player docked above the bar |
| Medium (600 to 839) | `NavigationRail`, 80 wide | Mini player across the content area, at the bottom |
| Expanded (≥ 840) | Permanent sidebar, 240 wide, Feishin-style: destinations, then a "Playlists" list | Player bar, 80 tall, full width (§5.5) |

- **Destinations:** Home, Library, Playlists, Search (four tabs). Settings opens from the settings icon in the Home, Library and Playlists top bars. Downloads is a Library tab and is also linked from Settings > Storage.
- **Back stacks:** each tab keeps its own back stack, saved and restored on tab switches (Home included). Album, artist and playlist detail, Settings and Downloads push onto the current tab's stack. Re-selecting the current tab returns to its root. Back at the root of another tab goes to Home; back at Home's root leaves the app.
- **Playlists tab:** the playlist list (rows as in §4.2 Playlists), with a "+" in the top bar to create a playlist. Library keeps its Playlists tab too.
- **Secondary screens** (Settings, Downloads, Queue) use an opaque 56 top bar: back arrow, then the title in `titleMedium` (optional muted subtitle such as "Playing from <source>"), then actions.
- Bar icons: Lucide outline when unselected, filled or bold when selected. Labels always shown. Indicator pill uses `primary` @ 16%.
- **Mini player** (compact and medium): 64 tall, full width, `surfaceContainer`. A 2dp progress line sits at its top edge (`primary`, track `outlineVariant`).
  - Hidden when the queue is empty and while the IME is visible (the nav bar hides then too). Open Now Playing covers it; it stays drawn underneath.
  - Content scrolls under it: lists add bottom padding equal to the height of mini player + nav bar.
- Foldables: tabletop posture puts Now Playing art in the top half and controls in the bottom half. Book posture uses the Expanded layout.
- Edge-to-edge everywhere. Hero headers and Now Playing draw behind the status bar. Use light status-bar icons over art, regardless of theme. A Tint hero is not art: its icons follow the theme, because the light-theme tint is light (L 0.82 to 0.90).

## 4. Screens

### 4.1 Home
- Top bar (not pinned, scrolls away): "Home" in `headlineSmall`. On the right, a settings icon that opens Settings and shows the server name in a tooltip.
- **Feature carousel** (Feishin): a `HorizontalPager` of 10 random albums. Each item:
  - Card: 200 tall, radius `md`, page spacing 12, peek 16.
  - Backdrop: pre-blurred art at 80% opacity, plus a bottom gradient to black 60%.
  - Content: 120dp art on the left, then the overline "FEATURED", title `titleLarge` in white, artist, year · track count, and a 48dp play button.
  - No auto-advance (battery). Pager dots below, 6dp.
- Default section order: Recently played (`getAlbumList2 type=recent`), Recently added (`newest`), Most played (`frequent`), Explore (`random`, with a refresh icon in the header), Playlists, Genres (pills).
- Section = section header + `LazyRow` with contentPadding 16, spacing 12, snap fling, 20 items, and a trailing "See all" tile.
- Settings > Home lets the user reorder and toggle sections, and set a size per section: S 112 / M 144 (default) / L 176 card width (Symfonium).

```
┌──────────────────────────────────┐
│ Home                         (◉) │
│ ┌──────────────────────────────┐ │
│ │░░blurred art░░░░░░░░░░░░░░░░░│ │
│ │ ┌──────┐ FEATURED            │ │
│ │ │ art  │ In Rainbows         │ │
│ │ │120dp │ Radiohead           │ │
│ │ └──────┘ 2007 · 10 tracks (▶)│ │
│ └──────────────────────────────┘ │
│              • ○ ○               │
│ Recently played               ›  │
│ ┌────┐ ┌────┐ ┌────┐ ┌──         │
│ │    │ │    │ │    │ │           │
│ └────┘ └────┘ └────┘ └──         │
│ Title  Title  Title  Ti          │
│ Artist Artist Artist Ar          │
│ Recently added                ›  │
│ ┌────┐ ┌────┐ ┌────┐ ┌──         │
├──────────────────────────────────┤
│▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔ progress          │
│[▣] Song title          (▶)  ⏭   │
│    Artist                        │
├──────────────────────────────────┤
│ ⌂Home ▤Library ♫Playlists ⌕Search│
└──────────────────────────────────┘
```

### 4.2 Library
- Top bar: "Library" in `headlineSmall`, plus actions: view toggle (grid/list; Albums and Artists each keep their own across launches), settings. No sort icon: each tab that sorts has a sort chip in its filter row.
- Tabs: `PrimaryScrollableTabRow`, text only, `labelLarge`, 2dp `primary` indicator, divider `outlineVariant`. Order: Albums · Artists · Songs · Playlists · Genres · Downloads. Remember the last tab across launches, and show no tab until it is known.
- Filter row, 48 tall: a sort chip ("Recently added ▾" opens a bottom sheet), filter chips (Favorites, Downloaded; Year and Genre for Albums), and the item count on the right in `labelMedium` muted.
  - Each tab keeps its own sort (across launches) and filters: changing Albums' sort or filters leaves Artists' as they were, and the other way round. Changing the sort or a filter scrolls the tab back to the top.
  - The row fits any width: the chips scroll sideways within 16 margins, and the count keeps its own slot at the end. While more chips lie past it, the chip row fades out over its last 24 before the count. When everything fits, nothing scrolls or fades.
  - Albums sort by name, artist, recently added or year. Artists sort by name, recently added (the newest added date among the artist's albums) or album count. Songs sort by title, artist, album, recently added (the server's added date), year (newest first) or play count (most played first); the artist, album, recently added and year sorts keep each album's songs together in track order.
  - Favorites and Downloaded cycle on each tap: the first keeps what the chip names, the second inverts it (albums, artists or songs that are not favorites, or have no downloaded song), the third turns it off. Offline holds Downloaded on. An artist counts as downloaded with a downloaded song of theirs, or a downloaded song on one of their albums.
- **Albums:** grid `GridCells.Adaptive(150)` with album cards, or a list of 64-tall rows with 48 art. Paging 3.
  - **Play / Shuffle albums** (§5.9) float at the bottom start, just above the mini player, whenever the tab has albums. They queue exactly the albums shown: current sort, all filters, downloaded only while offline. The list adds the buttons' height plus margins to its bottom padding, so the last row scrolls clear of them.
  - The genre album grid (Genres → a genre) gets the same buttons for its albums, sorted by name.
- **Artists:** list rows with a 48 circular image, name, and "N albums". The grid toggle switches to circular cards. Filter row: sort chip, Favorites, Downloaded. Offline it lists only artists of downloaded music.
- **Pinch to resize** the Albums and Artists grids, from one column up to eight (or the usual count, where more fit). The usual count is what `GridCells.Adaptive` gives (150 for albums, 112 for artists), and the kept size is the columns added to it, so it carries across screen widths.
  - The cards follow the fingers' spread continuously, and the row under the fingers stays under them (moving both fingers scrolls). When the card width comes nearer another column count's width (in proportion, with a 4% margin against flicker), the grid reflows with a haptic tick, and the cards glide from their old places to their new ones (`player`). Cards wider than their row overflow both sides alike until then.
  - On release, the cards spring to fill the row (`player`) and the column count is kept. With reduce motion, the cards still follow the fingers but neither glide nor spring.
  - Each tab keeps its own size across launches. Accessibility services get "Larger cards" and "Smaller cards" as custom actions on the grid, each a column, with the same motion.
- **Songs:** track rows (§5.2), paged. Filter row: "Shuffle all" (first, so it stays in view), sort chip, Favorites, Downloaded. Offline it lists only downloaded songs. "Shuffle all" shows while the tab has songs; it replaces the queue with every song in the library at once, in a tracks shuffle (downloaded songs only while offline).
- **Playlists:** 64-tall rows with 48 art, name, and "N tracks · 1 h 12 m". A "+" in the top bar creates a playlist.
- **Genres:** 2-column tiles, 72 tall, radius `md`. Background is a color hashed from the genre name, clamped like a hero tint. Name in `titleSmall`, counts in `bodySmall`.
- Name sorts get a fast scroller: a 4dp thumb on the right and a 56dp letter bubble while dragging.
- **Multi-select** (Albums, Artists and Songs tabs, grid or list; §5.10):
  - Long-press an item (with a haptic) to start selecting with it. Here long-press selects instead of opening the context sheet; the sheet stays on the rows' ⋮ and on the selection's ⋮.
  - **Drag to select** (Google Photos): after a long-press, keep the finger down and drag. Every item from the long-pressed one to the item under the finger, in list order, takes the long-pressed item's new state: picked, or dropped if the long-press dropped it. Dragging back returns the items left behind to how they were. Each move that changes items gives a light tick (`SegmentFrequentTick`); an item still loading is picked once it loads. Within 64 of the list's top edge, or of the mini player and navigation bar at the bottom, the list scrolls on its own, faster toward the edge (up to 16 per frame). This works in grids and lists, on any of the three tabs.
  - While selecting, a tap picks or drops an item instead of opening or playing it, and long-press does the same. Unavailable songs (offline, not downloaded) can't be picked, as they can't be played. Dropping the last pick, the close button, Back, or another tab ends selecting. Sort and filter changes keep the picks, even ones the new filter hides: they still count and are still queued.
  - The top bar becomes the selection bar: close · "N selected" · Play · Add to queue · ⋮. Rows hide their ⋮, and Play / Shuffle albums hide.
  - ⋮ with one pick opens that item's own context sheet, so long-press then ⋮ reaches everything long-press used to. With several it opens a sheet headed "N albums" (their names below, the first pick's art) with: Play · Shuffle · Shuffle albums (albums only) · Play next · Add to queue · Add to playlist (not artists) · Favorite · Download. Favorite and Download become Unfavorite and Remove download once every pick has them (songs keep Download, as on their own menu).
  - Picks are queued in the order they were picked, each album whole and in track order, each artist as their Play queues them. Shuffle shuffles all their tracks together; Shuffle albums queues the picked albums whole in a fresh album order. Favorite and Download skip the picks already starred or pinned.
  - Any action ends selecting.

### 4.3 Album detail (Feishin hero)
- Hero header (§5.4): 220 centered art, the overline "ALBUM", title, artist link, then "2019 · 12 tracks · 48 min".
- Action row, centered, gap 16: Play (56 circle, `primary`) · Shuffle · Favorite · Download · ⋮. Shuffle replaces the queue with the tracks in a fresh shuffled order, as does every "Shuffle" action (there is no player shuffle mode, §4.7). Shuffle long-press opens "Shuffle albums" options where relevant.
- While the album is the queue source and plays, Play shows Pause and a tap pauses. While it is the paused queue source, Play resumes the queue where it was. On any other page, Play replaces the queue with the page. Compare the kind and the id of the queue source: ids are unique per kind only. Shuffle always replaces the queue.
- Genre pills below the actions (§5.6).
- Track list: rows in album mode (number column, no art). For multi-disc albums, add a sticky "Disc N · subtitle" header (`labelLarge`, 40 tall).
- Footer: "Released 3 Oct 2019 · 12 tracks, 48:21" in `bodySmall` muted, then a "More by <artist>" carousel.
- On scroll: the top bar starts transparent with a back arrow and ⋮. Once the hero title scrolls under it, it fades to the art-tint color (`medium`) and shows the title in `titleMedium` plus a 36 play button (Feishin page header), which pauses and resumes like the hero's Play.
- The "Compact headers" setting (Symfonium) switches to 120 art on the left with text on the right, total height about 168.

```
┌──────────────────────────────────┐
│ ←                             ⋮  │ ← transparent bar
│░░░░░░░ art-tint gradient ░░░░░░░░│
│░░░░░░░░┌──────────────┐░░░░░░░░░│
│░░░░░░░░│              │░░░░░░░░░│
│░░░░░░░░│  album art   │░░░░░░░░░│
│░░░░░░░░│    220dp     │░░░░░░░░░│
│░░░░░░░░└──────────────┘░░░░░░░░░│
│              ALBUM               │
│           In Rainbows            │
│            Radiohead             │
│    2007 · 10 tracks · 42 min     │
│     (▶)   ⤨   ♡   ⤓   ⋮          │
│      (Alternative) (Rock)        │
│──────────────────────────────────│
│  1  15 Step               3:57 ⋮ │
│  2  Bodysnatchers         4:02 ⋮ │
│ ▮▮  Nude (playing, accent) 4:15 ⋮│
│  4  Weird Fishes          5:18 ⋮ │
└──────────────────────────────────┘
```

### 4.4 Artist detail
- Hero: follows the "Hero style" setting like albums (Tint by default). With Blurred art, the background is the pre-blurred artist image (or the art of the artist's newest album) at 70% opacity under a gradient to `background`. On top: a 160 circular image, the overline "ARTIST", the name, and "N albums · N tracks".
- Actions: Play (56) · Shuffle · **Shuffle albums** · Instant mix (`getSimilarSongs2`) · Favorite · ⋮. Play and the collapsed top bar's play pause and resume while the artist is the queue source, as on albums (§4.3): the artist's Play, a top song, and their "Play / Shuffle albums" all make the artist the queue source.
- **Play / Shuffle albums** (§5.9) float at the bottom start whenever the artist has albums. They queue the artist's albums in the order the page lists them: their own albums (newest first), then "Appears on". The list adds the buttons' inset to its bottom padding.
- Sections, in this order:
  1. Top songs: 5 rows, with "See all".
  2. Album carousels grouped by OpenSubsonic `releaseTypes`: Albums, EPs & Singles, Compilations, Appears on. Without that extension, show one "Albums" carousel sorted by year descending.
  3. Similar artists: a carousel of 96 circles.
  4. About: biography, 3 lines with "More".

### 4.5 Playlist detail
- Same hero as the album, with the overline "PLAYLIST", title, comment (2 lines), and "owner · N tracks · 2 h 4 m · Public".
- Actions: Play · Shuffle · Shuffle albums · Download · ⋮ (Edit, Rename, Delete). Play and the collapsed top bar's play pause and resume while the playlist is the queue source, as on albums (§4.3): its Play, Shuffle, a track tap and its "Shuffle albums" all make it the queue source.
- Track rows show art. In edit mode (top-bar pencil), each row gets a drag handle on the right and supports swipe-left to remove. Save and Cancel go in the top bar. Save stays disabled until the order differs from the saved one. Back with unsaved changes asks "Discard changes?" (Discard / Keep editing); without changes it just leaves edit mode.
- A track's ⋮ "Remove from playlist" (outside edit mode) hides the row at once and shows "Removed <title>" with Undo (§5.7). The server write waits until the snackbar has gone, so Undo sends nothing; a newer removal takes down the snackbar of the one before, as in the queue (§4.8). Starting edit mode takes the Undo away too, so the removal is written before the new order. Delete asks first, then shows "Deleted <name>" once the screen has closed.

### 4.6 Search
- Search field pinned at the top: 48 tall, `surfaceContainerHighest`, radius `full`, leading search icon, trailing clear. It auto-focuses when the tab is opened.
- Empty query: recent searches (rows with a history icon (Lucide `rotate-ccw-clock`), swipe to delete), then "Browse genres" pills.
- Results: `search3`, debounced 300ms. While offline, search Room FTS and show an "Offline results" chip.
  - Filter chips: All · Songs · Albums · Artists · Playlists.
  - "All" shows Artists (a row of 72 circles), Albums (a carousel of 128 cards), then Songs (up to 20 track rows, plus "See all").

### 4.7 Now Playing (full screen)
- Opens from the mini player with a `player` spring. The art morphs from the mini thumbnail to full size (shared bounds). Drag down anywhere, or tap the chevron, to collapse.
- It is an overlay over the whole shell, not a destination (ADR-0007). The screen it collapses to, with its chrome and mini player, stays drawn underneath, so a drag down or the back gesture reveals it.
- Background, set by the "Now Playing background" setting:
  - **Blurred art** (default): pre-blurred bitmap, scaled to cover, plus a gradient from black 30% at the top to black 85% at the bottom (Feishin `overlay-header`), plus noise.
  - **Art tint**: a solid tint at the top fading into `background`.
  - **Plain**: `background` only. Black theme defaults to Plain.
- Text and icons on this screen are always light (`#FFFFFF` at 100% for primary, 70% for secondary), except with Plain on the Light theme.
- Layout, top to bottom:
  - Top bar, 56 tall: chevron-down · an overline such as "PLAYING FROM ALBUM" (`labelSmall`) over the source name (`bodySmall`) · ⋮. Both name the queue source (see **Source copy** below).
  - Art: square, width = screen − 48, radius `lg`, shadow.
  - Metadata row: the title in `titleLarge` at 22sp (marquee), then artists in `bodyLarge` at 70%, each one a link. The album name is a link too. A favorite heart (48 target) sits on the right.
  - Seekbar: a 4 tall track (white @ 24%, active white), and a 12 thumb that grows to 20 while dragging. Below it, times in `labelMedium` tnum: elapsed on the left, total on the right (tap to switch to remaining). Centered between them: a quality pill describing what is actually playing: the stream format the player decoded or requested, so a server transcode reads "OPUS · 128" and a downloaded song describes the downloaded file. Lossless streams show bit depth and sample rate ("FLAC · 16/44.1"), lossy ones the bit rate. Until the stream format is known it describes the library song's file.
  - Transport row: an empty 48 slot · previous (28 icon, 56 target) · play/pause (72 white circle, icon in black) · next · repeat (Off, All, One). The empty slot balances repeat, so play/pause stays centered.
    While the stream buffers, play/pause keeps its icon (Pause, if playback was asked for) and a buffering ring circles it: an 80 ring, 2 clear of the 72 circle, in the light on-art color. See **Buffering ring** in §5.5.
  - Bottom row of 4 icon buttons, each 48 tall with a label, spread evenly: Lyrics · Queue · Related · ⋮ (Sleep timer, Speed, Add to playlist, Save queue as playlist, Go to album or artist, Info, Clear queue). The sleep timer shows a dot badge on ⋮ while it is active.
- **Source copy.** The top bar names the queue source: what the last play intent that replaced the queue played. Play next and Add to queue keep it, so it does not change as songs are added.

  | Queue source | Overline | Name | Tapping the name opens |
  |---|---|---|---|
  | Album | PLAYING FROM ALBUM | the album | the album |
  | Playlist | PLAYING FROM PLAYLIST | the playlist | the playlist |
  | Artist (top songs, Play albums, Shuffle albums) | PLAYING FROM ARTIST | the artist | the artist |
  | Genre (Play albums, Shuffle albums) | PLAYING FROM GENRE | the genre | the genre |
  | Library, Shuffle all or shuffled albums | SHUFFLED LIBRARY | none | nothing |
  | Library, Play albums in order | PLAYING FROM LIBRARY | none | nothing |
  | Instant mix | INSTANT MIX | the seed song or artist | nothing |
  | Another device's queue (adoption) | CONTINUED FROM | the device, such as "Feishin" | nothing |
  | Anything else (search results, a single song, Related, Android Auto) | PLAYING FROM QUEUE | none | nothing |

  When the name opens a page, the whole overline-and-name block is the tap target (48 tall), and Now Playing collapses before it navigates. A queue that empties forgets its source.

  The source is saved with the saved queue, so after a restart (or the system's "resume playback") Now Playing names it as before. An adopted queue, whether taken from *Continue from <device>* or adopted automatically, reads "CONTINUED FROM" over the device's name, such as "Feishin". Undo brings back the replaced queue's source. A queue saved by an older version has no source: PLAYING FROM QUEUE.
- **No shuffle button.** Music is shuffled only when it is queued: every "Shuffle" action writes its songs into the queue in shuffled order. A running queue is never reshuffled. The notification, Android Auto and the widgets offer no shuffle control either (ADR-0002).
- **Repeat control:** Lucide `repeat` (or `repeat-1`), white @ 50% when off, white with a 4dp dot under it when on.
- **Panels:** Lyrics, Queue (§4.8) and Related replace the art and metadata area with a `long` slide-up. A compact header stays visible: 48 art, title and artist. Seekbar and transport stay pinned at the bottom.
  - Swiping horizontally inside a panel moves between panels.
  - Tapping the active bottom button, or the compact header, returns to the art.
  - Dragging down anywhere while a panel is open, or past the top of the panel's list, pulls the panel down (fading to 50% over its height) instead of collapsing Now Playing. Release past 25% of the panel height or 1000dp/s and the panel slides out the bottom (`long` exit) while the art comes in from above. Otherwise it settles back with the `player` spring.
- **Gestures:**
  - Art swipe left/right: next/previous. Threshold 30% of the width or 1000dp/s. The art slides out and the next one slides in. Haptic tick at the threshold.
  - Tap on art: switch to the Lyrics panel.
  - Long-press on art: track context sheet.
  - Swipe up anywhere outside a panel: open Queue.
  - Long-press play/pause: stop.
- **Lyrics** (synced):
  - Text: 24sp, weight 700, line height 1.25, left-aligned with 24 side padding.
  - The active line is at 100% opacity and scale 1.0. Other lines are at 35% and scale 0.96 (`lyric` motion). The active line is held at 35% of the panel height.
  - When the user scrolls manually, auto-follow pauses until they tap the "Sync" chip (bottom-right) or 5s pass.
  - Tapping a line seeks to it. Unsynced lyrics: 18sp, weight 500, 100% opacity. No lyrics: empty state.
- **Related:** `getSimilarSongs2` / `getTopSongs` as track rows, with "Play all" and "Add to queue" at the top.
- **Playback errors:** when a song fails to play, a snackbar names it and says why, with its actions on their own line: Skip (plays the next item) and, for network errors only, Retry (prepares the song again and plays it). It shows in the app's one snackbar host (§5.7): above the mini player, or over Now Playing while it is open. It goes away when playback moves to another item or a retry plays.

  | Kind | Copy | Actions |
  |---|---|---|
  | Network (no connection, timeout, server error) | "Couldn't play <title>. Check your connection." | Skip · Retry |
  | Unsupported (the device can't parse or decode it) | "Couldn't play <title>. This device can't play its format." | Skip |
  | Not found (404 or 410 from the server, or a missing download) | "Couldn't play <title>. Its file is missing." | Skip |
  | Other | "Couldn't play <title>." | Skip |
- Landscape and Expanded: art on the left at 45% width, and the active panel on the right as tabs (Queue · Lyrics · Related). Both are visible at the same time, and controls run under the art.

```
┌──────────────────────────────────┐
│ ⌄    PLAYING FROM ALBUM        ⋮ │
│          In Rainbows             │
│ ░░░░░░░░ blurred art ░░░░░░░░░░░ │
│  ┌────────────────────────────┐  │
│  │                            │  │
│  │                            │  │
│  │      album art (sq)        │  │
│  │      swipe ◀ ▶ = skip      │  │
│  │                            │  │
│  └────────────────────────────┘  │
│  Nude                        ♡   │
│  Radiohead · In Rainbows         │
│  ━━━━━━━━━━●──────────────────   │
│  1:52     FLAC 16/44.1     4:15  │
│         ⏮     ( ▶ )     ⏭    ↻   │
│                                  │
│  ♪ Lyrics  ≡ Queue  ✦ Related  ⋮ │
└──────────────────────────────────┘
```

### 4.8 Queue
- The queue is Now Playing's Queue panel (§4.7); there is no separate queue screen. The wide player bar's queue button opens Now Playing, where Queue is the first tab.
- Header row above the list: "N tracks" in `bodyMedium` muted on the left. On the right: jump to current track (Lucide `locate-fixed`, shown only when the current track is off-screen), Upload queue and Download queue (cloud icons). Save queue as playlist and Clear queue (with a confirm) live in Now Playing's ⋮ menu, so there is one ⋮. The download's dialog uses the app's own colors, not the on-art ones. Upload and Download save to or take the server's queue at once, whatever the queue sync setting; Download over a queue with tracks asks "Replace the queue?" first.
- No shuffle or repeat controls here. A queue is never reshuffled; the queue order is the play order. Repeat lives on the Now Playing transport (§4.7) and the player bar. To add shuffled albums to a queue, use "Shuffle albums → Add to queue" (§5.9).
- **Continue from another device** banner, shown when the server queue is newer and `changedBy` is a different client:
  - Card: radius `lg`, `surfaceContainerHighest`, 16 padding, Lucide `monitor-smartphone` icon.
  - Text: "Continue from **Feishin**" in `titleSmall`, then "Nude · 2:31 · 5 min ago" in `bodySmall` muted.
  - Buttons: `FilledTonalButton` "Continue" and `TextButton` "Dismiss".
  - Placement: at the top of the queue. On app start, a compact one-line version also docks above the mini player. When the local queue is empty it replaces the mini player. It stays until acted on or until local playback starts. Not shown if auto-adopt is on; show a snackbar "Continued from Feishin · Undo" instead.
- **Continue from another device** notification, for a newer queue found by play pressed outside the app while auto-adopt is off (ADR-0004): title "Continue from Feishin", text "Nude · 2:31" and the save time as the notification's own time. One action, **Continue**, which takes the queue over and plays it. A tap opens the app, where the banner shows the same offer; swiping it away dismisses the offer. It goes whenever the offer does. Playing on does not decline it, unlike the banner, because the local queue was already playing when it was offered. It is not posted while the app is showing; the banner shows the offer there. Its own low-importance channel, so it makes no sound over the music, and the waveform small icon.
- List:
  - Collapsed history row at the top: "N played", tap to expand.
  - The current track: `primaryContainer` background, animated 3-bar equalizer over its art (paused = static bars).
  - Then the upcoming tracks, each naming its album ("Artist · Album").
- Row actions:
  - Drag handle (Lucide `grip-vertical`) on the right to reorder. Long-press anywhere on a row also starts a drag.
  - Swipe right: play next (icon `list-start`, `primary`).
  - Swipe left: remove (icon `trash`, `error`), with a "Removed <title>" snackbar and Undo. It stays when Now Playing switches panels or collapses. Undo puts back only the last removal, so a newer removal takes down the snackbar of the one before.
  - Both swipes trigger at 40% of the width with haptic feedback.

### 4.9 Downloads (Library tab)
- Storage bar at the top: an 8-tall `LinearProgressIndicator` labelled "3.2 GB used · limit 10 GB", plus an "Offline mode" switch row.
- Sections:
  - Active: rows with art, title, a 2dp progress bar under the text, and pause/cancel.
  - Pinned: albums and playlists as 64 rows with a status badge and size.
  - Failed: shows a retry action.
- Download state badge, used everywhere art appears:
  - 16dp circle at the bottom-right of the art (cards) or after the title (rows).
  - Queued: outline `arrow-down`. In progress: ring progress. Done: filled check in `success`.

### 4.10 Settings
- `LazyColumn` of M3 `ListItem`s: 56 tall for one line, 72 for two. Section headers in `labelLarge` `primary`, with 24 top padding.
- Groups:
  - **Servers:** list, add, switch, remove. Tapping a server switches to it and says "Switched to <server>" (§5.7); tapping the active one does nothing. Removing asks first, and when the server is the active one the dialog names the server that takes over (the first one left), or says the user will be signed out.
  - **Appearance:** Theme, Accent source with preset swatches, Now Playing background, Hero style (Tint / Blurred art), Compact headers, Dense rows (48), Home sections.
  - **Playback:** gapless; ReplayGain mode (Off, Track, Album), ReplayGain normalisation (−18 LUFS ReplayGain 2.0, the default, or −23 LUFS EBU; a picker), and "Boost quiet tracks" (a switch, off by default; its summary says it uses more battery). Normalisation and boost are disabled while the mode is Off. "Play when Bluetooth connects" (a switch, off by default): a Bluetooth audio device connecting resumes the queue, or this device's saved queue, unless something is already playing; turning it on asks for the Nearby devices permission, and losing that permission turns it off. Audio offload, crossfade (later).
  - **Streaming:** max bitrate and format for Wi-Fi and for mobile data.
  - **Downloads & storage:** download quality, cache size, storage limit.
  - **Queue sync:** "Sync play queue"; "Continue automatically" (auto-continue from other devices); "Ask in a notification" (a switch, on by default, disabled while Continue automatically is on): play pressed outside the app offers a newer queue in the *Continue from another device* notification (§4.8). Turning it on asks for the notification permission. Without that permission it reads off, but the choice is kept, so granting the permission later brings it back. A `bodySmall` note under the section says what play pressed outside the app does.
  - **Lyrics**, **Scrobbling**.
  - **About:** version; "Check for updates" (asks GitHub for the latest release and opens its page when it is newer; the summary says "Up to date" or that the check failed); "Check for updates automatically" (a switch, on by default: once a day on app start, a newer release shows a snackbar with **View**); licenses.
- Pickers open as bottom sheets with radio rows. Do not use dialogs for pickers.

### 4.11 Login / server setup
- One centered column, max width 400, 24 padding.
- Content, top to bottom:
  - "Segue" wordmark in `displaySmall`, weight 800, with "Connect to your Navidrome server" in `bodyMedium` muted.
  - Fields: M3 `OutlinedTextField`, radius `sm`. Server URL (prefix hint `https://`, URL keyboard), Username, Password (with a visibility toggle).
  - A collapsed "Advanced" section: plaintext legacy auth, self-signed certificate, custom headers.
  - "Connect" button: full width, 48 tall.
- Steps: the `ping` and `getOpenSubsonicExtensions` checks run inline as the button changes to "Checking…" (with a spinner). Errors show under the relevant field ("Server not reachable", "Wrong username or password").
- On success, go to Home immediately. The first sync shows a thin `LinearProgressIndicator` under the Home top bar with "Syncing library · 1,240 / 5,600 albums".

## 5. Components

### 5.1 Album card
- Width W comes from the grid cell or the section size. Art is a W×W square, radius `md`, with a `surfaceContainerHigh` placeholder showing a 24 Lucide `disc-3` icon in muted color.
- Below the art (Feishin "poster" style: no card surface, no padding): 8 gap, title in `titleSmall` (1 line), 2 gap, subtitle in `bodySmall` muted (1 line). The subtitle is the artist, or the year on artist pages.
- Artist variant: circular art, centered text.
- Press: scale 0.97 (`medium`). Long-press: context sheet with a haptic (in the Library it starts multi-select, §4.2). Download badge per §4.9.

### 5.2 Track row
- 56 tall, 48 in dense mode. Horizontal padding 16. The row is the touch target; tap plays.

| Slot | Phone | Expanded (table, Feishin) |
|---|---|---|
| Leading | 40 art radius `sm`, **or** track # (32 wide, `labelMedium` tnum, muted) in album context | # · 40 art |
| Main | Title in `titleSmall` 1 line; secondary in `bodySmall` muted: "Artist · Album" (just "Artist" in album context) | Title/artist column (flex 2) |
| — | — | Album (flex 1.5) · Genre (optional) · Plays (optional) |
| Badges | Explicit "E" (14 square, radius `xs`), download badge | same |
| Duration | `labelMedium` muted tnum | Duration column |
| Trailing | ⋮ 40 icon button | ♡ · ⋮ |

- Expanded width gets a sticky column header row: 36 tall, `labelSmall` uppercase muted, `outlineVariant` bottom divider. Columns can be configured via the header's ⋮.
- Playing: title and number in `primary`, and the equalizer bars replace the number or overlay the art. Unavailable offline: 38% alpha, not clickable.
- **Swipes** (Symfonium, like the queue's §4.8): swipe right for Play next (Lucide `list-start`), left for Add to queue (`list-end`), both `primary`, revealed on `surfaceContainerHigh`. Both trigger at 40% of the width with a haptic tick, then the row springs back (`player`); there is no snackbar: the haptic tick and the revealed row are the feedback, unlike the menu's Play next (§5.7). They apply to album, artist (top songs), playlist, search and Library Songs rows. They do not apply in the queue (which has its own), in playlist edit mode, to unavailable rows, or while the Library is selecting. Accessibility services get both as custom actions.

### 5.3 Section header
- 48 tall, horizontal padding 16. Title in `titleLarge`. Trailing: a `TextButton` "See all" (muted) or a 24 chevron. Optional extra icon (Explore refresh).

### 5.4 Hero header
- Height wraps its content. Top padding = status bar + 56. Bottom padding 16.
- Background, set by the "Hero style" setting:
  - **Tint** (default): a vertical gradient from the art tint at 100% to `background`, spanning hero height + 64.
  - **Blurred art** (Feishin `albumBackground`): pre-blurred bitmap at 70% opacity under `background`-colored gradients (top 5% to bottom 100%).
- Art: 220 on compact width, 250 on medium and wider, radius `md`, shadow per §2.4.
- Compact width is centered: art, then 16, overline, title (max 3 lines, `headlineMedium`), metadata. Medium and wider follows the Feishin desktop grid: art on the left, text bottom-aligned on the right, title in `displaySmall`, min height 340.

### 5.5 Player bar (expanded) and mini player
- **Mini player:** see §3.
  - Layout: 16 start padding, 48 art (radius `sm`), 12 gap, title `titleSmall` / artist `bodySmall` muted, play/pause (48), next (48).
  - Tap: expand. Drag up: interactive expand, where progress 0..1 drives the morph.
  - Horizontal swipe on the text area: next/previous. The text pages like a pager to reveal the adjacent track. Threshold 30% or 1000dp/s, with a haptic.
  - Long-press: track context sheet.
- **Player bar:** 80 tall, `surfaceContainer`, top border `outlineVariant`. Grid 1fr : 2fr : 1fr (Feishin).
  - Left: 56 art, title, artists.
  - Center: transport (prev, 40 play circle, next, repeat; no shuffle, see §4.7) above a seekbar with times.
  - Right: lyrics, queue, volume slider (96 wide), ⋮.
- **Buffering ring:** while the stream buffers, an indeterminate `CircularProgressIndicator` (stroke 2) circles play/pause. It shows only once buffering has lasted 300ms, so quick rebuffers don't flash it, and goes the moment buffering ends. The button keeps its icon: Pause while playback was asked for, so a tap pauses. The ring never moves the layout.
  - Mini player: 40, `onSurface`, around the 24 icon inside its 48 target.
  - Player bar: 48, `onSurface`, 2 clear of the 40 play circle.
  - Now Playing: 80 around the 72 circle (§4.7).

### 5.6 Chips and pills
- **Filter chip:** M3 `FilterChip`, 32 tall, radius `full`, `labelMedium`. Unselected: transparent with an `outlineVariant` border. Selected: `surfaceContainerHighest`, no border, leading check. An inverted chip (it hides what it names) looks selected with a leading ✕ instead.
- **Pill** (genres, metadata on the hero), Feishin pill: 28 tall, radius `full`, `background` @ 50% fill, 1dp `outlineVariant` border, `labelMedium`, 12 horizontal padding. Tapping opens the genre.

### 5.7 Context menus and sheets
- Phone: `ModalBottomSheet`, `surfaceContainerHigh`, top radius `xl`, with a drag handle.
- Header: 48 art + title (`titleSmall`) + subtitle (muted), then a divider.
- Rows: 48 tall, 20 icon, `bodyLarge` label.
- Order for a song: Play next · Add to queue · Instant mix · Add to playlist · Favorite · Go to album · Go to artist · Download · Info.
- Albums, artists and playlists add Play, Shuffle and **Shuffle albums** at the top. Destructive items use `error`.
- Expanded width: `DropdownMenu` anchored at the press point, radius `lg`, same items.
- Snackbars: `inverseSurface`, radius `sm`, placed above the mini player, and over Now Playing while it is open. The app has one host for them, whatever screen raised them: they show one at a time in order, stay up for the long duration when they have an action and the short one otherwise, and screen readers announce each.
- **Feedback:** every action started from a menu or button says what happened, and every destructive one without a confirm dialog offers Undo instead.
  - Confirm: Play next and Add to queue ("Playing next: <title>", "Added 12 songs to the queue"), Add to playlist ("Added 3 songs to Road trip", and how many were already there, with View), Download from a hero or menu ("Downloading <name>", or "Downloading 3 items" for a selection), the sleep timer ("Pausing in 30 min", with Cancel), switching servers ("Switched to <server>"), and deleting a playlist ("Deleted <name>", after its confirm dialog).
  - Undo: removing a song from the queue (§4.8) or from a playlist, an instant mix that replaced a queue, cancelling or removing a download (one Undo for a selection's, "Removed 3 downloads"), and an automatic adoption (§4.8). The server write or the file deletion waits until the snackbar has gone.
  - Silent: playing now (the music starting is the feedback) and the track-row swipes (§5.2).
  - Clear queue and Remove all downloads keep their confirm dialogs, with no Undo.

### 5.8 Empty, loading, error
- **Empty:** centered, 48 Lucide icon muted, 16 gap, `titleMedium`, `bodyMedium` muted (max 2 lines), optional `FilledTonalButton`. Example: "No downloads yet / Pin albums or playlists to listen offline".
- **Loading:**
  - Skeleton placeholders using the real layout: `surfaceContainerHigh` blocks with an alpha pulse 0.5 to 1.0 (1000ms, reverse). No shimmer gradient.
  - Show skeletons only when Room has no data and the load takes longer than 150ms.
  - Inline spinners: 20 `CircularProgressIndicator`, stroke 2.
- **Error:**
  - Room has data and the server can't be reached: show it, plus an "Offline · showing cached library" chip under the top bar (`surfaceContainerHighest`, `cloud-off` icon). Tapping the chip retries.
  - Room has data and the server answered with an error (signed out, refused, server error): show it without the chip. It is not offline.
  - Offline, lists narrowed to downloaded music get the same chip with "Offline · showing downloads" (the Library shows it under the tab row). A Downloaded filter chip is then locked on: a leading lock, the state description "On while offline", and taps do nothing.
  - No data: the empty state with `error`-tinted icon, message, and a "Retry" button.
  - Image load failure: keep the placeholder. Never show a broken-image icon.

### 5.9 Play / Shuffle albums buttons
- Two split pills in a row with an 8 gap, floating at the bottom start of an album list. Bottom offset: shell chrome (mini player + navigation bar + system bar, `LocalShellContentPadding`) + 16. Side margin: 16 on compact width, 24 on medium and wider. On expanded width they sit at the bottom start of the content area, above the player bar.
- Each pill: 48 tall, radius `full`, no shadow (flat tonal, §2.4).
  - Main part: 16 start padding, a 20 icon, 8 gap, a `labelLarge` label, 12 end padding.
  - Then a 1 × 20 divider in the content color at 24%.
  - Then a chevron part: a 20 `chevron-down`, 8 start padding, 12 end padding.
- **Play** (filled `play` icon): `primary` fill, `onPrimary` content. **Shuffle** (shuffle-albums icon): `surfaceContainerHighest` fill, `onSurface` content, 1dp `outlineVariant` border. The visible labels are "Play" and "Shuffle"; content descriptions are "Play albums" and "Shuffle albums".
- Tap: play now. The queue is replaced and playback starts. Play keeps the albums in the order shown; Shuffle queues them in a fresh album order. Either way every album is written into the queue at once, in that order (no player shuffle mode), each whole, in disc and track order.
- Long-press (with a haptic), or tap the chevron: a `DropdownMenu` anchored to the pill (radius `lg`, `surfaceContainerHigh`) with 48-tall rows: **Play next** (`list-start`) and **Add to queue** (`list-end`).
- While the list scrolls down, the labels collapse so only icons and chevrons show. They come back on scroll up. The label animates with expand/shrink + fade (`medium`), or a plain fade with reduce motion.
- Hidden while the keyboard is up and when the list is empty. Fades in and out with `medium`.

```
│ ┌────┐ ┌────┐ ┌────┐            │
│ └────┘ └────┘ └────┘            │
│ (▶ Play │⌄)  (⤨ Shuffle │⌄)     │
├──────────────────────────────────┤
│[▣] Song title          (▶)  ⏭   │ ← mini player
```

### 5.10 Multi-select
- **Selection bar:** replaces the top-level bar at the same 56 height, so the content does not move. 4 side padding, `background`. Close (Lucide `x`, "Clear selection"), then "N selected" in `titleMedium`, then 48 icon buttons in `onSurface`.
- **Selected card** (album, artist): `scrim` over the art in the art's shape, with a 24 `primary` circle holding a 16 `onPrimary` check in the middle.
- **Selected row** (media row, track row): `primary` @ 16% fill across the row, and the same mark over its art. A track row with a number instead of art shows a 20 `primary` check in place of the number.
- Unselected items look as usual while selecting. Selected items report the selected state to accessibility services, and long-press is announced as "Select".

## 6. Performance constraints that shape the design

- **No real-time blur** (ADR-0006). Every "blurred art" is built like this:
  1. Fetch `getCoverArt?size=128`. Reuse the row-size cache entry, so there is no extra request.
  2. Downscale to 32×32 and apply a CPU box blur (radius 3, 2 passes) in a Coil `Transformation` off the main thread.
  3. Cache under key `blur:<coverArtId>` in a memory LRU of 24 entries, and in the disk cache.
  4. Draw it scaled to fill with `FilterQuality.Low`. Bilinear upscaling of a 32px image looks blurred enough.
- Palette extraction (§2.1) uses the same 32px bitmap.
- **Cover sizes:** request `size` = the smallest bucket in {128, 256, 512, 1024} that is ≥ 0.9 × (display dp × density). The fixed set keeps Navidrome's resize cache hot and gives Coil only a few keys per image.

| Component | Display dp | `size` @ 2.625x (typical) |
|---|---|---|
| Track row, mini player, queue header, sheet header | 32–48 | 128 |
| Artist list circle, search circles | 48–96 | 128 / 256 |
| Album card (grid 150–190, carousel 112–176) | 112–190 | 256 / 512 |
| Feature carousel art, compact hero | 120 | 512 |
| Hero art | 220–250 | 1024 |
| Now Playing art | 312–400 | 1024 |
| Blur / palette source | 32 px | 128 (reused) |

- Coil config: memory cache 20% of the app heap, disk cache 512MB. Use `crossfade(200)` in lists, but no crossfade for Now Playing art (the pager handles that). Prefetch the next track's 1024 art when the track changes.
- Lists:
  - Fixed-height rows, stable `key` and `contentType`.
  - No `Modifier.shadow`, `alpha` layers or `clip` on nested layouts inside items. Clip only the image.
  - Equalizer bars run only for the visible playing row.
- Position ticker (battery): it runs only while a player surface is composed and at least `STARTED`. Mini player: 1Hz. Now Playing seekbar: 4Hz with `animateFloat` interpolation between ticks. Synced lyrics: next-line timer. Never poll from the background.
- Animate with `graphicsLayer` (translation, scale, alpha) only. Never animate layout size in lists. Lyric lines are a `LazyColumn`, and only the 2 lines around the active line animate.
- Marquee: only on the Now Playing title, and only while the screen is resumed.

## 7. App icon and brand

The mark is **Wave on vinyl**: a dark, grooved record with a seven-bar waveform riding across it. The bars shade from white into `primary`, which is the segue: one track handing over to the next.

- **Sources:** `docs/brand/icon.svg` (circle-masked, for docs and the README), `docs/brand/icon-full-bleed.svg` and `docs/brand/icon-512.png` (unmasked 108 canvas, for store listings, which apply their own mask). `docs/brand/generate.py` writes the SVGs and the vector drawables from the geometry below, so change it there and re-run it.
- **Adaptive icon** (`app/src/main/res/mipmap-anydpi/ic_launcher.xml`), on the 108 canvas, centered on (54, 54):

| Layer | Content |
|---|---|
| Background | `#0C0C0C` (Dark `background`) |
| Record | Circle r 31, `#171C29`. Grooves: rings r 26, 21, 16, 1 wide, white @ 6% |
| Waveform | 7 bars, 5.5 wide with fully rounded ends, 8 apart from x 30 to 78. Centers y 54, 44, 42, 54, 66, 64, 54; lengths 14, 22, 28, 30, 28, 22, 14 |
| Bar colors, left to right | `#FFFFFF` `#DDE7FF` `#BBCFFE` `#98B6FE` `#769EFD` `#5487FC` `#3574FC` |
| Monochrome (themed icons) | Same shapes, alpha only: record @ 16%, grooves @ 20%, bars @ 100% |

- Everything stays inside the 66 safe circle, so no launcher mask clips it.
- **Status bar:** the media notification's small icon (`media3_notification_small_icon`, in `app` so it outranks Media3's own alias) is the waveform alone, white, with no record. The record would read as a blob at 24.
- The download notification keeps the system download icon, because it says what the notification is about. The *Continue from another device* notification uses the waveform.
