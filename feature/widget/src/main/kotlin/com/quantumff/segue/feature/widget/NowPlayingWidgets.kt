package com.quantumff.segue.feature.widget

import android.content.Context
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.PreviewSizeMode
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.RowScope
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.material3.ColorProviders
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.quantumff.segue.core.designsystem.art.ArtColors
import com.quantumff.segue.core.designsystem.theme.segueColorScheme
import com.quantumff.segue.core.playback.nowplaying.NowPlayingSnapshot

/**
 * A now-playing widget: [Content] drawn from the stored snapshot. A running Glance session keeps
 * following the store, so an `update` while the session is open redraws with the new track too;
 * art is (re)loaded when the cover changes, and retried while it is missing.
 */
abstract class NowPlayingWidget internal constructor(
    /** The cover size to request, about the art's height in pixels (DESIGN.md §6 buckets). */
    private val artPx: Int,
) : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    @Composable
    internal abstract fun Content(state: NowPlayingSnapshot, art: WidgetArt, colors: WidgetColors)

    override suspend fun provideGlance(context: Context, id: GlanceId): Nothing {
        val store = context.widgetEntryPoint().stateStore()
        val initial = store.read()
        val initialArt = loadWidgetArt(context, initial.coverArtId, artPx)
        val colors = WidgetColors(context)
        provideContent {
            val state by store.snapshots.collectAsState(initial)
            var art by remember { mutableStateOf(initialArt) }
            LaunchedEffect(state) {
                if (art.coverArtId != state.coverArtId || art.cover == null) {
                    art = loadWidgetArt(context, state.coverArtId, artPx)
                }
            }
            val current = art.takeIf { it.coverArtId == state.coverArtId } ?: WidgetArt.None
            GlanceTheme(colors = colors.glance) { Content(state, current, colors) }
        }
    }

    /** The widget picker's preview (API 35+, published by [WidgetUpdater]): a sample track without art. */
    override suspend fun providePreview(context: Context, widgetCategory: Int): Nothing {
        val sample = NowPlayingSnapshot(
            songId = PREVIEW_SONG_ID,
            title = context.getString(R.string.widget_preview_title),
            artist = context.getString(R.string.widget_preview_artist),
        )
        val colors = WidgetColors(context)
        provideContent {
            GlanceTheme(colors = colors.glance) { Content(sample, WidgetArt.None, colors) }
        }
    }
}

/** 4x1, resizable: art, title and artist, previous/play/next. */
class NowPlayingStripWidget : NowPlayingWidget(artPx = 256) {
    /** A typical 4x1 cell on a phone; the minimum height would shrink the art to a thumbnail. */
    override val previewSizeMode: PreviewSizeMode = SizeMode.Responsive(setOf(DpSize(340.dp, 88.dp)))

    @Composable
    override fun Content(state: NowPlayingSnapshot, art: WidgetArt, colors: WidgetColors) = Strip(state, art, colors)
}

/** 4x2, resizable: larger art and previous/play/next. */
class NowPlayingCardWidget : NowPlayingWidget(artPx = 512) {
    /** A typical 4x2 cell on a phone, rather than the minimum size, which leaves little room for art. */
    override val previewSizeMode: PreviewSizeMode = SizeMode.Responsive(setOf(DpSize(340.dp, 160.dp)))

    @Composable
    override fun Content(state: NowPlayingSnapshot, art: WidgetArt, colors: WidgetColors) = Card(state, art, colors)
}

class NowPlayingStripWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NowPlayingStripWidget()
}

class NowPlayingCardWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NowPlayingCardWidget()
}

// --- Layouts ---------------------------------------------------------------------------------------

/** The art fills the strip's height, flush with its left edge. */
@Composable
private fun Strip(state: NowPlayingSnapshot, art: WidgetArt, colors: WidgetColors) {
    val size = LocalSize.current
    // Previous, play and next always show. On a narrow strip the art gives way so the title keeps some room.
    val titlesWidthWithArt = size.width - size.height - STRIP_GAP - STRIP_CONTROLS_WIDTH - STRIP_END_PADDING
    val showArt = titlesWidthWithArt >= MIN_TITLES_WIDTH
    WidgetSurface(state, art, colors) { palette ->
        Row(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(start = if (showArt) 0.dp else STRIP_GAP, end = STRIP_END_PADDING),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showArt) {
                Cover(art, palette, GlanceModifier.fillMaxHeight().width(size.height))
                Spacer(GlanceModifier.width(STRIP_GAP))
            }
            Titles(state, palette, titleSize = 15, artistSize = 13, modifier = GlanceModifier.defaultWeight())
            if (state.hasTrack) {
                Transport(
                    R.drawable.widget_skip_back,
                    R.string.widget_previous,
                    actionRunCallback<PreviousAction>(),
                    palette,
                )
                PlayPause(state, palette, size = STRIP_PLAY)
                Transport(
                    R.drawable.widget_skip_forward,
                    R.string.widget_next,
                    actionRunCallback<NextAction>(),
                    palette,
                )
            }
        }
    }
}

/** The art fills the card's height, flush with its left edge, as far as the controls leave room. */
@Composable
private fun Card(state: NowPlayingSnapshot, art: WidgetArt, colors: WidgetColors) {
    val size = LocalSize.current
    // The art gives way to the controls on a narrow card, and drops out when it would be a thumbnail.
    val roomForArt = size.width - CARD_GAP - CARD_PADDING - CARD_CONTROLS_WIDTH
    val artWidth = minOf(size.height, roomForArt).takeIf { it >= MIN_CARD_ART }
    // Nearly square art still fills the height, cropping its sides a little. It fills rather than
    // taking LocalSize's height, which can fall a few dp short of the launcher's cell.
    val fullHeight = artWidth != null && artWidth >= size.height * MIN_FULL_HEIGHT_ASPECT
    WidgetSurface(state, art, colors) { palette ->
        Row(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            if (artWidth != null) {
                val height = if (fullHeight) GlanceModifier.fillMaxHeight() else GlanceModifier.height(artWidth)
                Cover(art, palette, height.width(artWidth))
            }
            Column(
                modifier = GlanceModifier
                    .defaultWeight()
                    .fillMaxHeight()
                    .padding(start = if (artWidth != null) CARD_GAP else CARD_PADDING)
                    .padding(vertical = CARD_PADDING)
                    .padding(end = CARD_PADDING),
            ) {
                Spacer(GlanceModifier.height(TITLES_GAP))
                Titles(state, palette, titleSize = 17, artistSize = 14, modifier = GlanceModifier.fillMaxWidth())
                Spacer(GlanceModifier.defaultWeight())
                if (state.hasTrack) {
                    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        FlexSpacer()
                        Transport(
                            R.drawable.widget_skip_back,
                            R.string.widget_previous,
                            actionRunCallback<PreviousAction>(),
                            palette,
                        )
                        FlexSpacer()
                        PlayPause(state, palette, size = CARD_PLAY)
                        FlexSpacer()
                        Transport(
                            R.drawable.widget_skip_forward,
                            R.string.widget_next,
                            actionRunCallback<NextAction>(),
                            palette,
                        )
                        FlexSpacer()
                    }
                }
            }
        }
    }
}

/** Takes an equal share of the row's free width. */
@Composable
private fun RowScope.FlexSpacer() = Spacer(GlanceModifier.defaultWeight())

/**
 * The widget background: the blurred cover under a dark scrim and noise (DESIGN.md §2.4, §4.7
 * "Blurred art"), or the theme surface while there is none. A tap anywhere outside the buttons
 * opens the app.
 */
@Composable
private fun WidgetSurface(
    state: NowPlayingSnapshot,
    art: WidgetArt,
    colors: WidgetColors,
    content: @Composable (WidgetPalette) -> Unit,
) {
    val context = LocalContext.current
    val backdrop = art.backdrop?.bitmap
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(if (backdrop == null) colors.background else ColorProvider(Color.Black))
            .cornerRadius(WIDGET_RADIUS)
            .clickable(actionStartActivity(openAppIntent(context, nowPlaying = state.hasTrack))),
    ) {
        if (backdrop != null) {
            Image(
                provider = ImageProvider(backdrop),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = GlanceModifier.fillMaxSize(),
            )
            Image(
                provider = ImageProvider(R.drawable.widget_scrim),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = GlanceModifier.fillMaxSize(),
            )
            // FillBounds gives the drawable the whole view, which its tile mode then repeats over.
            Image(
                provider = ImageProvider(R.drawable.widget_noise),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = GlanceModifier.fillMaxSize(),
            )
        }
        content(if (backdrop == null) colors.plain else WidgetPalette.overArt(art.backdrop.colors))
    }
}

/**
 * The cover in [modifier]'s bounds (about square), cropped to fill and unrounded: it sits against
 * the widget's edge, whose corners clip it.
 */
@Composable
private fun Cover(art: WidgetArt, palette: WidgetPalette, modifier: GlanceModifier = GlanceModifier) {
    val context = LocalContext.current
    val cover = art.cover
    if (cover != null) {
        Image(
            provider = ImageProvider(cover),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
    } else {
        // Placeholder: the disc icon on a raised surface (DESIGN.md §5.1).
        Box(
            modifier = modifier.background(palette.placeholder),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                provider = ImageProvider(R.drawable.widget_disc),
                contentDescription = context.getString(R.string.widget_no_art),
                colorFilter = ColorFilter.tint(palette.onSurfaceVariant),
                modifier = GlanceModifier.size(PLACEHOLDER_ICON),
            )
        }
    }
}

@Composable
private fun Titles(
    state: NowPlayingSnapshot,
    palette: WidgetPalette,
    titleSize: Int,
    artistSize: Int,
    modifier: GlanceModifier = GlanceModifier,
) {
    val context = LocalContext.current
    val title = if (state.hasTrack) state.title.orEmpty() else context.getString(R.string.widget_tap_to_start)
    val subtitle = if (state.hasTrack) state.artist.orEmpty() else context.getString(R.string.widget_app_name)
    Column(modifier = modifier) {
        Text(
            text = title,
            maxLines = 1,
            style = TextStyle(color = palette.onSurface, fontSize = titleSize.sp, fontWeight = FontWeight.Bold),
        )
        Spacer(GlanceModifier.height(TITLES_GAP))
        Text(
            text = subtitle,
            maxLines = 1,
            style = TextStyle(color = palette.onSurfaceVariant, fontSize = artistSize.sp),
        )
    }
}

/** The primary action: a circle in the accent color, at least a 48dp touch target (DESIGN.md §2.3). */
@Composable
private fun PlayPause(state: NowPlayingSnapshot, palette: WidgetPalette, size: Dp) {
    val icon = if (state.isPlaying) R.drawable.widget_pause else R.drawable.widget_play
    val label = if (state.isPlaying) R.string.widget_pause else R.string.widget_play
    Box(
        modifier = GlanceModifier
            .size(size)
            .cornerRadius(size / 2)
            .background(palette.primary)
            .clickable(actionRunCallback<PlayPauseAction>()),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(icon),
            contentDescription = LocalContext.current.getString(label),
            colorFilter = ColorFilter.tint(palette.onPrimary),
            modifier = GlanceModifier.size(size / 2),
        )
    }
}

/** Previous or next: a bare icon on a 48dp touch target (DESIGN.md §2.3). */
@Composable
private fun Transport(@DrawableRes icon: Int, @StringRes label: Int, onClick: Action, palette: WidgetPalette) {
    Box(
        modifier = GlanceModifier.size(TRANSPORT_SIZE).cornerRadius(TRANSPORT_SIZE / 2).clickable(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(icon),
            contentDescription = LocalContext.current.getString(label),
            colorFilter = ColorFilter.tint(palette.onSurface),
            modifier = GlanceModifier.size(24.dp),
        )
    }
}

/** Any song id: the preview only needs the sample to count as a track. */
private const val PREVIEW_SONG_ID = "preview"

/** The minimum touch target (DESIGN.md §2.3), also the previous/next button size. */
private val TRANSPORT_SIZE = 48.dp
private val PLACEHOLDER_ICON = 32.dp
private val TITLES_GAP = 2.dp
private val MIN_TITLES_WIDTH = 72.dp
private val STRIP_GAP = 12.dp
private val STRIP_END_PADDING = 4.dp
private val STRIP_PLAY = 48.dp
private val STRIP_CONTROLS_WIDTH = TRANSPORT_SIZE * 2 + STRIP_PLAY
private val CARD_PADDING = 16.dp
private val CARD_GAP = 16.dp
private val CARD_PLAY = 52.dp
private val CARD_CONTROLS_WIDTH = TRANSPORT_SIZE * 2 + CARD_PLAY
private val MIN_CARD_ART = 48.dp

/** The narrowest full-height art, as width over height: at most 15% of the cover is cropped. */
private const val MIN_FULL_HEIGHT_ASPECT = 0.85f

/** The launcher's widget corner radius where the platform has one (API 31+), else DESIGN.md `lg`. */
private val WIDGET_RADIUS = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 24.dp else 12.dp
