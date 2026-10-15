package com.quantumff.segue.feature.nowplaying.sheet

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.quantumff.segue.core.designsystem.icon.SegueIcons
import com.quantumff.segue.core.designsystem.preview.PreviewSegueThemes
import com.quantumff.segue.core.designsystem.preview.SeguePreview
import com.quantumff.segue.core.designsystem.theme.SegueShapes
import com.quantumff.segue.core.designsystem.theme.SegueSizes
import com.quantumff.segue.core.designsystem.theme.SegueSpacing
import com.quantumff.segue.core.designsystem.theme.tabularNumbers
import com.quantumff.segue.core.model.SleepTimer
import com.quantumff.segue.feature.nowplaying.R
import com.quantumff.segue.feature.nowplaying.SleepTimerChoice
import java.util.Date
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/**
 * Sleep timer (DESIGN.md §4.7 ⋮ menu): the running timer with Cancel, then presets, end of track,
 * end of album, and a custom length. Picking one starts it (or replaces the running one) and closes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SleepTimerSheet(
    active: SleepTimer?,
    onStart: (SleepTimerChoice) -> Unit,
    onCancel: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = SegueShapes.sheetTop,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = SegueSizes.noElevation,
    ) {
        SleepTimerContent(
            active = active,
            onStart = { onStart(it); onDismissRequest() },
            onCancel = { onCancel(); onDismissRequest() },
            modifier = Modifier.navigationBarsPadding(),
        )
    }
}

@Composable
internal fun SleepTimerContent(
    active: SleepTimer?,
    onStart: (SleepTimerChoice) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.feature_nowplaying_sleep_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .padding(horizontal = SegueSpacing.lg, vertical = SegueSpacing.sm)
                .semantics { heading() },
        )
        if (active != null) ActiveTimer(active, onCancel)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        PRESET_MINUTES.forEach { minutes ->
            SheetRow(
                icon = SegueIcons.Timer,
                label = presetLabel(minutes),
                onClick = { onStart(SleepTimerChoice.Minutes(minutes)) },
            )
        }
        SheetRow(
            icon = SegueIcons.Music,
            label = stringResource(R.string.feature_nowplaying_sleep_end_of_track),
            selected = active == SleepTimer.EndOfTrack,
            onClick = { onStart(SleepTimerChoice.EndOfTrack) },
        )
        SheetRow(
            icon = SegueIcons.Disc3,
            label = stringResource(R.string.feature_nowplaying_sleep_end_of_album),
            selected = active == SleepTimer.EndOfAlbum,
            onClick = { onStart(SleepTimerChoice.EndOfAlbum) },
        )
        CustomTimer(onStart = { onStart(SleepTimerChoice.Minutes(it)) })
        Spacer(Modifier.height(SegueSpacing.sm))
    }
}

/** The running timer: when it pauses and how long is left, with Cancel. */
@Composable
private fun ActiveTimer(active: SleepTimer, onCancel: () -> Unit) {
    val context = LocalContext.current
    val (title, subtitle) = when (active) {
        is SleepTimer.AtTime -> {
            // Refreshes once a minute while the sheet is open; nothing runs once it closes.
            val now by produceState(System.currentTimeMillis(), active) {
                while (true) {
                    delay(MILLIS_PER_MINUTE - value % MILLIS_PER_MINUTE)
                    value = System.currentTimeMillis()
                }
            }
            val minutesLeft = ((active.endsAtMillis - now).coerceAtLeast(0) + MILLIS_PER_MINUTE - 1) / MILLIS_PER_MINUTE
            stringResource(
                R.string.feature_nowplaying_sleep_active_at,
                DateFormat.getTimeFormat(context).format(Date(active.endsAtMillis)),
            ) to stringResource(R.string.feature_nowplaying_sleep_active_remaining, durationLabel(minutesLeft.toInt()))
        }

        SleepTimer.EndOfTrack -> stringResource(R.string.feature_nowplaying_sleep_active_end_of_track) to null

        SleepTimer.EndOfAlbum -> stringResource(R.string.feature_nowplaying_sleep_active_end_of_album) to null
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SegueSpacing.lg, vertical = SegueSpacing.sm)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest, SegueShapes.lg)
            .padding(start = SegueSpacing.lg, end = SegueSpacing.xs, top = SegueSpacing.sm, bottom = SegueSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = SegueIcons.Timer,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(SegueSizes.iconSmall),
        )
        Spacer(Modifier.width(SegueSpacing.md))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.tabularNumbers(),
                color = MaterialTheme.colorScheme.onSurface,
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall.tabularNumbers(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        TextButton(onClick = onCancel) {
            Text(stringResource(R.string.feature_nowplaying_sleep_cancel), color = MaterialTheme.colorScheme.error)
        }
    }
}

/** A slider from 5 minutes to 3 hours in 5-minute steps, and Start. */
@Composable
private fun CustomTimer(onStart: (Int) -> Unit) {
    var minutes by rememberSaveable { mutableFloatStateOf(DEFAULT_CUSTOM_MINUTES.toFloat()) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    SheetRow(
        icon = SegueIcons.Edit,
        label = stringResource(R.string.feature_nowplaying_sleep_custom),
        onClick = { expanded = !expanded },
    )
    if (!expanded) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SegueSpacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Slider(
            value = minutes,
            onValueChange = { minutes = it },
            valueRange = CUSTOM_MIN.toFloat()..CUSTOM_MAX.toFloat(),
            steps = (CUSTOM_MAX - CUSTOM_MIN) / CUSTOM_STEP - 1,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(SegueSpacing.md))
        Text(
            text = durationLabel(minutes.roundToInt()),
            style = MaterialTheme.typography.labelLarge.tabularNumbers(),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(CUSTOM_LABEL_WIDTH),
        )
        FilledTonalButton(onClick = { onStart(minutes.roundToInt()) }) {
            Text(stringResource(R.string.feature_nowplaying_sleep_start))
        }
    }
}

/** A 48-tall sheet row (DESIGN.md §5.7): 20 icon, `bodyLarge` label. Selected rows use `primary`. */
@Composable
internal fun SheetRow(icon: ImageVector, label: String, onClick: () -> Unit, selected: Boolean = false) {
    val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(SegueSizes.sheetRow)
            .clickable(onClick = onClick)
            .padding(horizontal = SegueSpacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) color else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(SegueSizes.iconSmall),
        )
        Spacer(Modifier.width(SegueSpacing.lg))
        Text(text = label, style = MaterialTheme.typography.bodyLarge, color = color, modifier = Modifier.weight(1f))
        if (selected) {
            Icon(
                imageVector = SegueIcons.Check,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(SegueSizes.iconSmall),
            )
        }
    }
}

@Composable
private fun presetLabel(minutes: Int): String = durationLabel(minutes)

/** "45 min", "1 h", "1 h 30 min". */
@Composable
private fun durationLabel(minutes: Int): String {
    val hours = minutes / MINUTES_PER_HOUR
    val rest = minutes % MINUTES_PER_HOUR
    return when {
        hours == 0 -> stringResource(R.string.feature_nowplaying_sleep_minutes, rest)
        rest == 0 -> stringResource(R.string.feature_nowplaying_sleep_hours, hours)
        else -> stringResource(R.string.feature_nowplaying_sleep_hours, hours) + " " +
            stringResource(R.string.feature_nowplaying_sleep_minutes, rest)
    }
}

private val PRESET_MINUTES = listOf(15, 30, 45, 60, 90)
private const val DEFAULT_CUSTOM_MINUTES = 20
private const val CUSTOM_MIN = 5
private const val CUSTOM_MAX = 180
private const val CUSTOM_STEP = 5
private const val MINUTES_PER_HOUR = 60
private const val MILLIS_PER_MINUTE = 60_000L
private val CUSTOM_LABEL_WIDTH = SegueSizes.touchTarget * 2

@PreviewSegueThemes
@Composable
private fun SleepTimerContentPreview() {
    SeguePreview {
        SleepTimerContent(active = SleepTimer.EndOfAlbum, onStart = {}, onCancel = {})
    }
}
