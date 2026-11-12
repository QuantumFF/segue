package com.quantumff.segue.feature.nowplaying.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.quantumff.segue.core.data.snackbar.SnackbarText
import com.quantumff.segue.core.designsystem.icon.SegueIcons
import com.quantumff.segue.core.designsystem.theme.SegueShapes
import com.quantumff.segue.core.designsystem.theme.SegueSizes
import com.quantumff.segue.core.designsystem.theme.SegueSpacing
import com.quantumff.segue.core.designsystem.theme.SegueTheme
import com.quantumff.segue.core.model.SleepTimer
import com.quantumff.segue.feature.nowplaying.R

/**
 * The sleep timer chip in Now Playing's top bar (DESIGN.md §4.7): "23 min" left, ticking each minute,
 * or "End of track". A 48 tall target around a 28 tall opaque dark pill, so its light text reads on any
 * art. Screen readers hear "Sleep timer: 23 min", and hear it again when it changes.
 *
 * @param clock wall-clock epoch millis, for tests.
 */
@Composable
internal fun SleepTimerChip(
    timer: SleepTimer,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    clock: () -> Long = System::currentTimeMillis,
) {
    // Only a timer at a time counts down; the others need no clock.
    val now by rememberMinuteClock(ticking = timer is SleepTimer.AtTime, clock = clock)
    val label = timer.chipText(now).resolve(LocalResources.current)
    val description = stringResource(R.string.feature_nowplaying_sleep_chip_description, label)
    val onArt = SegueTheme.colors.onArt
    Box(
        modifier = modifier
            .height(SegueSizes.touchTarget)
            .semantics {
                contentDescription = description
                liveRegion = LiveRegionMode.Polite
            }.clickable(
                onClickLabel = stringResource(R.string.feature_nowplaying_sleep_chip_open),
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .height(SegueSizes.pill)
                .background(SegueTheme.colors.artContainers.surfaceContainerHigh, SegueShapes.full)
                .padding(start = SegueSpacing.sm, end = SegueSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(SegueSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(SegueIcons.Timer, contentDescription = null, tint = onArt, modifier = Modifier.size(CHIP_ICON))
            Text(label, style = MaterialTheme.typography.labelMedium, color = onArt, maxLines = 1)
        }
    }
}

/**
 * What the sleep timer chip says at [nowMillis]: the minutes left, like the sheet ("23 min", "1 h",
 * "1 h 30 min", part of a minute counting as a whole one), or "End of track" or "End of album".
 */
internal fun SleepTimer.chipText(nowMillis: Long): SnackbarText = when (this) {
    is SleepTimer.AtTime -> {
        val minutes = minutesLeft(endsAtMillis, nowMillis)
        val hours = minutes / MINUTES_PER_HOUR
        val rest = minutes % MINUTES_PER_HOUR
        when {
            hours == 0 -> SnackbarText.of(R.string.feature_nowplaying_sleep_minutes, rest)
            rest == 0 -> SnackbarText.of(R.string.feature_nowplaying_sleep_hours, hours)
            else -> SnackbarText.of(R.string.feature_nowplaying_sleep_hours_minutes, hours, rest)
        }
    }

    SleepTimer.EndOfTrack -> SnackbarText.of(R.string.feature_nowplaying_sleep_end_of_track)

    SleepTimer.EndOfAlbum -> SnackbarText.of(R.string.feature_nowplaying_sleep_end_of_album)
}

private val CHIP_ICON = 16.dp
private const val MINUTES_PER_HOUR = 60
