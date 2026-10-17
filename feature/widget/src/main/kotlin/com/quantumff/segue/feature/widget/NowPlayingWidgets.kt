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

/** 4x1, resizable: art, title and artist, previous/play/next. */
class NowPlayingStripWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    /** A typical 4x1 cell on a phone; the minimum height would shrink the art to a thumbnail. */
    override val previewSizeMode: PreviewSizeMode = SizeMode.Responsive(setOf(DpSize(340.dp, 88.dp)))

    override suspend fun provideGlance(context: Context, id: GlanceId) = provideNowPlaying(context, STRIP_ART_PX) {
            state,
            art,
            colors,
        ->
        Strip(state, art, colors)
    }

    override suspend fun providePreview(context: Context, widgetCategory: Int) = providePreview(context) {
            state,
            art,
            colors,
        ->
        Strip(state, art, colors)
    }
}

/** 4x2, resizable: larger art and previous/play/next. */
class NowPlayingCardWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    /** A typical 4x2 cell on a phone, rather than the minimum size, which leaves little room for art. */
    override val previewSizeMode: PreviewSizeMode = SizeMode.Responsive(setOf(DpSize(340.dp, 160.dp)))

    override suspend fun provideGlance(context: Context, id: GlanceId) = provideNowPlaying(context, CARD_ART_PX) {
            state,
            art,
            colors,
        ->
        Card(state, art, colors)
    }

    override suspend fun providePreview(context: Context, widgetCategory: Int) = providePreview(context) {
            state,
            art,
            colors,
        ->
        Card(state, art, colors)
    }
}

class NowPlayingStripWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NowPlayingStripWidget()
}

class NowPlayingCardWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NowPlayingCardWidget()
}

private typealias WidgetContent = @Composable (NowPlayingSnapshot, WidgetArt, WidgetColors) -> Unit

/**
 * Renders [content] from the stored snapshot. A running Glance session keeps following the store,
 * so an `update` while the session is open redraws with the new track too; art is (re)loaded when
 * the cover changes, and retried while it is missing.
 */
private suspend fun GlanceAppWidget.provideNowPlaying(context: Context, artPx: Int, content: WidgetContent): Nothing {
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
        val current = art.takeIf { it.coverArtId == state.coverArtId } ?: NoArt
        GlanceTheme(colors = colors.glance) { content(state, current, colors) }
    }
}

/** The widget picker's preview (API 35+, published by [WidgetUpdater]): a sample track without art. */
private suspend fun GlanceAppWidget.providePreview(context: Context, content: WidgetContent): Nothing {
    val sample = NowPlayingSnapshot(
        songId = PREVIEW_SONG_ID,
        title = context.getString(R.string.widget_preview_title),
        artist = context.getString(R.string.widget_preview_artist),
    )
    val colors = WidgetColors(context)
    provideContent {
        GlanceTheme(colors = colors.glance) { content(sample, NoArt, colors) }
    }
}

private val NoArt = WidgetArt(null, null, null, null)

// --- Layouts ---------------------------------------------------------------------------------------

/** The art fills the strip's height, flush with its left edge. */
@Composable
private fun Strip(state: NowPlayingSnapshot, art: WidgetArt, colors: WidgetColors) {
    val size = LocalSize.current
    // On a narrow strip the previous button gives way first, then the art, so the title keeps some room.
    val fixed = STRIP_GAP + STRIP_END_PADDING + STRIP_PLAY + TRANSPORT_SIZE + MIN_TITLES_WIDTH
    val showPrevious = size.width - size.height - fixed - TRANSPORT_SIZE >= 0.dp
    val showArt = showPrevious || size.width - size.height - fixed >= 0.dp
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
                if (showPrevious) {
                    Transport(
                        R.drawable.widget_skip_back,
                        R.string.widget_previous,
                        actionRunCallback<PreviousAction>(),
                        palette,
                    )
                }
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
                Spacer(GlanceModifier.height(2.dp))
                Titles(state, palette, titleSize = 17, artistSize = 14, modifier = GlanceModifier.fillMaxWidth())
                Spacer(GlanceModifier.defaultWeight())
                if (state.hasTrack) {
                    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Gap()
                        Transport(
                            R.drawable.widget_skip_back,
                            R.string.widget_previous,
                            actionRunCallback<PreviousAction>(),
                            palette,
                        )
                        Gap()
                        PlayPause(state, palette, size = 52.dp)
                        Gap()
                        Transport(
                            R.drawable.widget_skip_forward,
                            R.string.widget_next,
                            actionRunCallback<NextAction>(),
                            palette,
                        )
                        Gap()
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.Gap() = Spacer(GlanceModifier.defaultWeight())

/**
 * The widget background: the blurred cover under a dark scrim (DESIGN.md §4.7, "Blurred art"), or
 * the theme surface while there is none. A tap anywhere outside the buttons opens the app.
 */
@Composable
private fun WidgetSurface(
    state: NowPlayingSnapshot,
    art: WidgetArt,
    colors: WidgetColors,
    content: @Composable (WidgetPalette) -> Unit,
) {
    val context = LocalContext.current
    val backdrop = art.backdrop
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
        }
        content(if (backdrop == null) colors.plain else WidgetPalette.overArt(art.colors))
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
        Spacer(GlanceModifier.height(2.dp))
        Text(
            text = subtitle,
            maxLines = 1,
            style = TextStyle(color = palette.onSurfaceVariant, fontSize = artistSize.sp),
        )
    }
}

/** The primary action: a circle in the accent color. */
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

// --- Colors ----------------------------------------------------------------------------------------

/** The colors drawn over one background. */
private class WidgetPalette(
    val onSurface: ColorProvider,
    val onSurfaceVariant: ColorProvider,
    val placeholder: ColorProvider,
    val primary: ColorProvider,
    val onPrimary: ColorProvider,
) {
    companion object {
        /**
         * Over blurred art, text and icons are always light (DESIGN.md §4.7) and the play button
         * takes the art's accent; white when the art gave no colors.
         */
        fun overArt(colors: ArtColors?) = WidgetPalette(
            onSurface = ColorProvider(Color.White),
            onSurfaceVariant = ColorProvider(Color.White.copy(alpha = SECONDARY_TEXT_ALPHA)),
            placeholder = ColorProvider(Color.White.copy(alpha = PLACEHOLDER_ALPHA)),
            primary = ColorProvider(colors?.accentDark ?: Color.White),
            onPrimary = ColorProvider(Color.Black),
        )

        private const val SECONDARY_TEXT_ALPHA = 0.7f
        private const val PLACEHOLDER_ALPHA = 0.12f
    }
}

/**
 * Segue's neutrals with Material You accents (DESIGN.md §2.1), resolved for light and dark so the
 * launcher switches them with the system theme.
 */
private class WidgetColors(context: Context) {
    private val light: ColorScheme = segueColorScheme(context, dark = false)
    private val dark: ColorScheme = segueColorScheme(context, dark = true)

    val glance = ColorProviders(light = light, dark = dark)
    val background = role { surfaceContainer }

    /** The palette on [background]. */
    val plain = WidgetPalette(
        onSurface = role { onSurface },
        onSurfaceVariant = role { onSurfaceVariant },
        placeholder = role { surfaceContainerHigh },
        primary = role { primary },
        onPrimary = role { onPrimary },
    )

    /** One color role, switching with the system theme. */
    private fun role(color: ColorScheme.() -> Color) = ColorProvider(day = light.color(), night = dark.color())
}

/** Art requests use the app's cover size buckets (DESIGN.md §6), about the art's height in pixels. */
private const val STRIP_ART_PX = 256
private const val CARD_ART_PX = 512
private const val PREVIEW_SONG_ID = "preview"
private val TRANSPORT_SIZE = 48.dp
private val PLACEHOLDER_ICON = 32.dp
private val MIN_TITLES_WIDTH = 72.dp
private val STRIP_GAP = 12.dp
private val STRIP_END_PADDING = 4.dp
private val STRIP_PLAY = 44.dp
private val CARD_PADDING = 16.dp
private val CARD_GAP = 16.dp

/** Previous, play and next. */
private val CARD_CONTROLS_WIDTH = 148.dp
private val MIN_CARD_ART = 48.dp

/** The narrowest full-height art, as width over height: at most 15% of the cover is cropped. */
private const val MIN_FULL_HEIGHT_ASPECT = 0.85f

/** The launcher's widget corner radius where the platform has one (API 31+), else DESIGN.md `lg`. */
private val WIDGET_RADIUS = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 24.dp else 12.dp
