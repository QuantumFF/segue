package com.quantumff.segue.core.designsystem.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.unit.dp

/**
 * Lucide icons (ISC, third_party/lucide) used by Segue. Stroke 2, round caps and joins, like
 * Feishin. `*Filled` variants are for active play, pause and favorite; `*Selected` variants
 * (stroke 2.5) are for selected navigation destinations (DESIGN.md §2.3, §3).
 *
 * The vectors only carry shape: [androidx.compose.material3.Icon] tints them.
 * To add an icon, convert its SVG into [LucidePaths] and add a `by lazy` entry here.
 */
object SegueIcons {
    val Home by lazy { lucide("house", LucidePaths.House) }
    val HomeSelected by lazy { lucide("house", LucidePaths.House, strokeWidth = SELECTED_STROKE) }
    val Library by lazy { lucide("library-big", LucidePaths.LibraryBig) }
    val LibrarySelected by lazy { lucide("library-big", LucidePaths.LibraryBig, strokeWidth = SELECTED_STROKE) }
    val Playlists by lazy { lucide("list-music", LucidePaths.ListMusic) }
    val PlaylistsSelected by lazy { lucide("list-music", LucidePaths.ListMusic, strokeWidth = SELECTED_STROKE) }
    val Search by lazy { lucide("search", LucidePaths.Search) }
    val SearchSelected by lazy { lucide("search", LucidePaths.Search, strokeWidth = SELECTED_STROKE) }
    val Account by lazy { lucide("circle-user-round", LucidePaths.CircleUserRound) }

    val Play by lazy { lucide("play", LucidePaths.Play) }
    val PlayFilled by lazy { lucide("play", LucidePaths.Play, filled = true) }
    val Pause by lazy { lucide("pause", LucidePaths.Pause) }
    val PauseFilled by lazy { lucide("pause", LucidePaths.Pause, filled = true) }
    val SkipNext by lazy { lucide("skip-forward", LucidePaths.SkipForward) }
    val SkipPrevious by lazy { lucide("skip-back", LucidePaths.SkipBack) }
    val Shuffle by lazy { lucide("shuffle", LucidePaths.Shuffle) }
    val ShuffleAlbums by lazy { shuffleAlbums() }
    val Repeat by lazy { lucide("repeat", LucidePaths.Repeat) }
    val RepeatOne by lazy { lucide("repeat-1", LucidePaths.Repeat1) }
    val Heart by lazy { lucide("heart", LucidePaths.Heart) }
    val HeartFilled by lazy { lucide("heart", LucidePaths.Heart, filled = true) }
    val Star by lazy { lucide("star", LucidePaths.Star) }
    val StarFilled by lazy { lucide("star", LucidePaths.Star, filled = true) }
    val Lyrics by lazy { lucide("mic-vocal", LucidePaths.MicVocal) }
    val UpNext by lazy { lucide("list-ordered", LucidePaths.ListOrdered) }
    val Related by lazy { lucide("sparkles", LucidePaths.Sparkles) }
    val Timer by lazy { lucide("timer", LucidePaths.Timer) }
    val Volume by lazy { lucide("volume-2", LucidePaths.Volume2) }

    val Disc3 by lazy { lucide("disc-3", LucidePaths.Disc3) }
    val Music by lazy { lucide("music", LucidePaths.Music) }
    val Artist by lazy { lucide("user-round", LucidePaths.UserRound) }

    val MoreVertical by lazy { lucide("ellipsis-vertical", LucidePaths.EllipsisVertical) }
    val ChevronRight by lazy { lucide("chevron-right", LucidePaths.ChevronRight) }
    val ChevronDown by lazy { lucide("chevron-down", LucidePaths.ChevronDown) }
    val Back by lazy { lucide("arrow-left", LucidePaths.ArrowLeft) }
    val Close by lazy { lucide("x", LucidePaths.X) }
    val Check by lazy { lucide("check", LucidePaths.Check) }
    val Plus by lazy { lucide("plus", LucidePaths.Plus) }
    val Edit by lazy { lucide("pencil", LucidePaths.Pencil) }
    val Sort by lazy { lucide("arrow-up-down", LucidePaths.ArrowUpDown) }
    val GridView by lazy { lucide("layout-grid", LucidePaths.LayoutGrid) }
    val ListView by lazy { lucide("list", LucidePaths.List) }
    val Settings by lazy { lucide("settings", LucidePaths.Settings) }
    val Info by lazy { lucide("info", LucidePaths.Info) }
    val Server by lazy { lucide("server", LucidePaths.Server) }
    val Visible by lazy { lucide("eye", LucidePaths.Eye) }
    val Hidden by lazy { lucide("eye-off", LucidePaths.EyeOff) }
    val Refresh by lazy { lucide("refresh-cw", LucidePaths.RefreshCw) }
    val History by lazy { lucide("rotate-ccw-clock", LucidePaths.RotateCcwClock) }

    val PlayNext by lazy { lucide("list-start", LucidePaths.ListStart) }
    val AddToQueue by lazy { lucide("list-end", LucidePaths.ListEnd) }
    val AddToPlaylist by lazy { lucide("list-plus", LucidePaths.ListPlus) }
    val DragHandle by lazy { lucide("grip-vertical", LucidePaths.GripVertical) }
    val Remove by lazy { lucide("trash", LucidePaths.Trash) }
    val ScrollToCurrent by lazy { lucide("locate-fixed", LucidePaths.LocateFixed) }

    val Download by lazy { lucide("download", LucidePaths.Download) }
    val DownloadQueued by lazy { lucide("arrow-down", LucidePaths.ArrowDown) }
    val DownloadDone by lazy { lucide("circle-check", LucidePaths.CircleCheck) }
    val OtherDevice by lazy { lucide("monitor-smartphone", LucidePaths.MonitorSmartphone) }
    val Offline by lazy { lucide("cloud-off", LucidePaths.CloudOff) }
    val Error by lazy { lucide("circle-alert", LucidePaths.CircleAlert) }
}

private const val VIEWPORT = 24f
private const val STROKE = 2f
private const val SELECTED_STROKE = 2.5f

/** Shape-only paint; [androidx.compose.material3.Icon] replaces it with its tint. */
private val IconPaint = SolidColor(Color.Black)

private fun ImageVector.Builder.lucidePaths(paths: List<String>, strokeWidth: Float, filled: Boolean) {
    paths.forEach { data ->
        addPath(
            pathData = addPathNodes(data),
            fill = if (filled) IconPaint else null,
            stroke = IconPaint,
            strokeLineWidth = strokeWidth,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }
}

private fun iconBuilder(name: String) = ImageVector.Builder(
    name = "lucide.$name",
    defaultWidth = VIEWPORT.dp,
    defaultHeight = VIEWPORT.dp,
    viewportWidth = VIEWPORT,
    viewportHeight = VIEWPORT,
)

private fun lucide(name: String, paths: List<String>, strokeWidth: Float = STROKE, filled: Boolean = false) =
    iconBuilder(name).apply { lucidePaths(paths, strokeWidth, filled) }.build()

/**
 * Lucide `shuffle` scaled to the top-left, plus a small disc badge at the bottom-right: the
 * "Shuffle albums" icon (DESIGN.md §4.7).
 */
private fun shuffleAlbums(): ImageVector = iconBuilder("shuffle-albums").apply {
    group(scaleX = SHUFFLE_SCALE, scaleY = SHUFFLE_SCALE) {
        lucidePaths(LucidePaths.Shuffle, strokeWidth = STROKE / SHUFFLE_SCALE * SHUFFLE_STROKE_RATIO, filled = false)
    }
    addPath(
        pathData = addPathNodes(DISC_BADGE),
        fill = IconPaint,
        pathFillType = PathFillType.EvenOdd,
    )
}.build()

private const val SHUFFLE_SCALE = 0.8f
private const val SHUFFLE_STROKE_RATIO = 0.9f

/** A filled circle (r 4.5 at 19,19) with a hole (r 1.5): a tiny record. */
private const val DISC_BADGE =
    "M14.5 19a4.5 4.5 0 1 0 9 0a4.5 4.5 0 1 0 -9 0z M17.5 19a1.5 1.5 0 1 0 3 0a1.5 1.5 0 1 0 -3 0z"
