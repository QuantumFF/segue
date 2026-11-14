package com.quantumff.segue.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.quantumff.segue.core.designsystem.R
import com.quantumff.segue.core.designsystem.icon.SegueIcons
import com.quantumff.segue.core.designsystem.preview.PreviewSegueThemes
import com.quantumff.segue.core.designsystem.preview.SeguePreview
import com.quantumff.segue.core.designsystem.theme.SegueSizes
import com.quantumff.segue.core.designsystem.theme.SegueSpacing
import com.quantumff.segue.core.designsystem.theme.tabularNumbers
import com.quantumff.segue.core.model.SyncPhase
import com.quantumff.segue.core.model.SyncProgress
import java.text.NumberFormat

/**
 * Library sync status under a top bar (DESIGN.md §4.11): a thin `LinearProgressIndicator` with
 * "Syncing library · 1,240 / 5,600 albums" while running; a retry chip when it failed; nothing
 * when idle or done.
 */
@Composable
fun SyncStatusBar(progress: SyncProgress, onRetryClick: () -> Unit, modifier: Modifier = Modifier) {
    when (progress) {
        SyncProgress.Idle, SyncProgress.Succeeded -> Unit

        SyncProgress.Pending, is SyncProgress.Running -> Column(
            modifier
                .fillMaxWidth()
                .padding(horizontal = SegueSpacing.lg, vertical = SegueSpacing.xs),
        ) {
            val running = progress as? SyncProgress.Running
            if (running != null && running.total > 0) {
                LinearProgressIndicator(
                    progress = { (running.completed.toFloat() / running.total).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(SegueSizes.progressLine),
                    drawStopIndicator = {},
                )
            } else {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(SegueSizes.progressLine),
                )
            }
            Text(
                text = syncLabel(progress),
                style = MaterialTheme.typography.bodySmall.tabularNumbers(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = SegueSpacing.xs),
            )
        }

        is SyncProgress.Failed -> StatusChip(
            label = stringResource(R.string.ds_sync_failed),
            icon = SegueIcons.Error,
            onClickLabel = stringResource(R.string.ds_retry),
            onClick = onRetryClick,
            modifier = modifier.padding(horizontal = SegueSpacing.lg, vertical = SegueSpacing.xs),
        )
    }
}

/**
 * "Syncing library · 1,240 / 5,600 albums" against a known total, "Syncing library · 1,240 albums"
 * while the total is unknown, or just "Syncing library" before anything is counted. Pending means
 * WorkManager is holding the job for its network and battery constraints.
 */
@Composable
fun syncLabel(progress: SyncProgress): String {
    if (progress == SyncProgress.Pending) return stringResource(R.string.ds_sync_waiting)
    val running = progress as? SyncProgress.Running
    if (running == null || (running.total <= 0 && running.completed <= 0)) {
        return stringResource(R.string.ds_syncing_library)
    }
    val format = NumberFormat.getIntegerInstance()
    val known = running.total > 0
    val noun = pluralStringResource(
        when (running.phase) {
            SyncPhase.Artists -> R.plurals.ds_sync_phase_artists
            SyncPhase.Genres -> R.plurals.ds_sync_phase_genres
            SyncPhase.Albums -> R.plurals.ds_sync_phase_albums
            SyncPhase.Playlists -> R.plurals.ds_sync_phase_playlists
        },
        if (known) running.total else running.completed,
    )
    return if (known) {
        stringResource(
            R.string.ds_syncing_progress,
            format.format(running.completed),
            format.format(running.total),
            noun,
        )
    } else {
        stringResource(R.string.ds_syncing_count, format.format(running.completed), noun)
    }
}

@PreviewSegueThemes
@Composable
private fun SyncStatusBarPreview() {
    SeguePreview {
        Column {
            SyncStatusBar(SyncProgress.Running(SyncPhase.Albums, completed = 1240, total = 5600), onRetryClick = {})
            SyncStatusBar(SyncProgress.Pending, onRetryClick = {})
            SyncStatusBar(SyncProgress.Failed("timeout"), onRetryClick = {})
        }
    }
}
