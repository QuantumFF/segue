package com.quantumff.segue.feature.widget

import android.content.Context
import android.graphics.Bitmap
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
import com.quantumff.segue.core.designsystem.theme.segueColorScheme
import com.quantumff.segue.core.playback.nowplaying.NowPlayingSnapshot

/** 4x1: art, title and artist, play/pause, next. */
class NowPlayingStripWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) = provideNowPlaying(context, STRIP_ART_PX) {
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

    override suspend fun provideGlance(context: Context, id: GlanceId) = provideNowPlaying(context, CARD_ART_PX) {
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

/**
 * Renders [content] from the stored snapshot. A running Glance session keeps following the store,
 * so an `update` while the session is open redraws with the new track too; art is (re)loaded only
 * when the cover changes.
 */
private suspend fun GlanceAppWidget.provideNowPlaying(
    context: Context,
    artPx: Int,
    content: @Composable (NowPlayingSnapshot, Bitmap?, WidgetColors) -> Unit,
): Nothing {
    val store = context.widgetEntryPoint().stateStore()
    val initial = store.read()
    val initialArt = WidgetArt(initial.coverArtId, loadWidgetArt(context, initial.coverArtId, artPx))
    val colors = WidgetColors(context)
    provideContent {
        val state by store.snapshots.collectAsState(initial)
        var art by remember { mutableStateOf(initialArt) }
        LaunchedEffect(state.coverArtId) {
            if (art.coverArtId != state.coverArtId) {
                art = WidgetArt(state.coverArtId, loadWidgetArt(context, state.coverArtId, artPx))
            }
        }
        val bitmap = art.bitmap.takeIf { art.coverArtId == state.coverArtId }
        GlanceTheme(colors = colors.glance) { content(state, bitmap, colors) }
    }
}

private class WidgetArt(val coverArtId: String?, val bitmap: Bitmap?)

// --- Layouts ---------------------------------------------------------------------------------------

@Composable
private fun Strip(state: NowPlayingSnapshot, art: Bitmap?, colors: WidgetColors) {
    WidgetSurface(state, colors) {
        Row(
            modifier = GlanceModifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Art(art, colors, size = 48.dp)
            Spacer(GlanceModifier.width(12.dp))
            Titles(state, colors, titleSize = 14, modifier = GlanceModifier.defaultWeight())
            if (state.hasTrack) {
                PlayPause(state, colors, filled = false)
                ControlButton(
                    R.drawable.widget_skip_forward,
                    R.string.widget_next,
                    actionRunCallback<NextAction>(),
                    colors.onSurface,
                )
            }
        }
    }
}

@Composable
private fun Card(state: NowPlayingSnapshot, art: Bitmap?, colors: WidgetColors) {
    val height = LocalSize.current.height
    val artSize = (height - CARD_PADDING * 2).coerceIn(MIN_CARD_ART, MAX_CARD_ART)
    WidgetSurface(state, colors) {
        Row(
            modifier = GlanceModifier.fillMaxSize().padding(CARD_PADDING),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Art(art, colors, size = artSize)
            Spacer(GlanceModifier.width(16.dp))
            Column(modifier = GlanceModifier.defaultWeight().fillMaxHeight()) {
                Spacer(GlanceModifier.height(4.dp))
                Titles(state, colors, titleSize = 16, modifier = GlanceModifier.fillMaxWidth())
                Spacer(GlanceModifier.defaultWeight())
                if (state.hasTrack) {
                    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Spacer(GlanceModifier.defaultWeight())
                        ControlButton(
                            R.drawable.widget_skip_back,
                            R.string.widget_previous,
                            actionRunCallback<PreviousAction>(),
                            colors.onSurface,
                        )
                        PlayPause(state, colors, filled = true)
                        ControlButton(
                            R.drawable.widget_skip_forward,
                            R.string.widget_next,
                            actionRunCallback<NextAction>(),
                            colors.onSurface,
                        )
                    }
                }
            }
        }
    }
}

/** The widget background; a tap anywhere outside the buttons opens the app. */
@Composable
private fun WidgetSurface(state: NowPlayingSnapshot, colors: WidgetColors, content: @Composable () -> Unit) {
    val context = LocalContext.current
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(colors.background)
            .cornerRadius(WIDGET_RADIUS)
            .clickable(actionStartActivity(openAppIntent(context, nowPlaying = state.hasTrack))),
    ) {
        content()
    }
}

@Composable
private fun Art(art: Bitmap?, colors: WidgetColors, size: Dp) {
    val context = LocalContext.current
    if (art != null) {
        Image(
            provider = ImageProvider(art),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = GlanceModifier.size(size).cornerRadius(ART_RADIUS),
        )
    } else {
        // Placeholder: the disc icon on a raised surface (DESIGN.md §5.1).
        Box(
            modifier = GlanceModifier.size(size).background(colors.placeholder).cornerRadius(ART_RADIUS),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                provider = ImageProvider(R.drawable.widget_disc),
                contentDescription = context.getString(R.string.widget_no_art),
                colorFilter = ColorFilter.tint(colors.onSurfaceVariant),
                modifier = GlanceModifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun Titles(
    state: NowPlayingSnapshot,
    colors: WidgetColors,
    titleSize: Int,
    modifier: GlanceModifier = GlanceModifier,
) {
    val context = LocalContext.current
    val title = if (state.hasTrack) state.title.orEmpty() else context.getString(R.string.widget_tap_to_start)
    val subtitle = if (state.hasTrack) state.artist.orEmpty() else context.getString(R.string.widget_app_name)
    Column(modifier = modifier) {
        Text(
            text = title,
            maxLines = 1,
            style = TextStyle(color = colors.onSurface, fontSize = titleSize.sp, fontWeight = FontWeight.Bold),
        )
        Text(
            text = subtitle,
            maxLines = 1,
            style = TextStyle(color = colors.onSurfaceVariant, fontSize = 13.sp),
        )
    }
}

@Composable
private fun PlayPause(state: NowPlayingSnapshot, colors: WidgetColors, filled: Boolean) {
    val icon = if (state.isPlaying) R.drawable.widget_pause else R.drawable.widget_play
    val label = if (state.isPlaying) R.string.widget_pause else R.string.widget_play
    if (!filled) {
        ControlButton(icon, label, actionRunCallback<PlayPauseAction>(), colors.onSurface)
        return
    }
    // The card's primary action: a 44 circle in the accent color.
    Box(
        modifier = GlanceModifier
            .size(44.dp)
            .cornerRadius(22.dp)
            .background(colors.primary)
            .clickable(actionRunCallback<PlayPauseAction>()),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(icon),
            contentDescription = LocalContext.current.getString(label),
            colorFilter = ColorFilter.tint(colors.onPrimary),
            modifier = GlanceModifier.size(22.dp),
        )
    }
}

@Composable
private fun ControlButton(@DrawableRes icon: Int, @StringRes label: Int, onClick: Action, tint: ColorProvider) {
    Box(
        modifier = GlanceModifier.size(44.dp).cornerRadius(22.dp).clickable(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(icon),
            contentDescription = LocalContext.current.getString(label),
            colorFilter = ColorFilter.tint(tint),
            modifier = GlanceModifier.size(22.dp),
        )
    }
}

// --- Colors ----------------------------------------------------------------------------------------

/**
 * Segue's neutrals with Material You accents (DESIGN.md §2.1), resolved for light and dark so the
 * launcher switches them with the system theme.
 */
private class WidgetColors(context: Context) {
    private val light: ColorScheme = segueColorScheme(context, dark = false)
    private val dark: ColorScheme = segueColorScheme(context, dark = true)

    val glance = ColorProviders(light = light, dark = dark)
    val background = role { surfaceContainer }
    val placeholder = role { surfaceContainerHigh }
    val onSurface = role { onSurface }
    val onSurfaceVariant = role { onSurfaceVariant }
    val primary = role { primary }
    val onPrimary = role { onPrimary }

    /** One color role, switching with the system theme. */
    private fun role(color: ColorScheme.() -> Color) = ColorProvider(day = light.color(), night = dark.color())
}

/** Art requests use the app's cover size buckets (DESIGN.md §6). */
private const val STRIP_ART_PX = 128
private const val CARD_ART_PX = 256
private val CARD_PADDING = 12.dp
private val MIN_CARD_ART = 56.dp
private val MAX_CARD_ART = 160.dp
private val ART_RADIUS = 6.dp

/** The launcher's widget corner radius where the platform has one (API 31+), else DESIGN.md `lg`. */
private val WIDGET_RADIUS = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 24.dp else 12.dp
