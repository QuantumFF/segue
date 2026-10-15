package com.quantumff.segue.feature.downloads

import com.quantumff.segue.core.model.StorageLimit
import androidx.compose.material3.Icon
import androidx.compose.foundation.background
import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quantumff.segue.core.designsystem.art.CoverArt
import com.quantumff.segue.core.designsystem.component.ContextMenuHeader
import com.quantumff.segue.core.designsystem.component.DownloadBadge
import com.quantumff.segue.core.designsystem.component.EmptyState
import com.quantumff.segue.core.designsystem.component.LocalShellContentPadding
import com.quantumff.segue.core.designsystem.component.MediaAction
import com.quantumff.segue.core.designsystem.component.MediaContextMenu
import com.quantumff.segue.core.designsystem.component.MediaRow
import com.quantumff.segue.core.designsystem.component.SectionHeader
import com.quantumff.segue.core.designsystem.component.SegueIconButton
import com.quantumff.segue.core.designsystem.component.SubScreenTopBar
import com.quantumff.segue.core.designsystem.icon.SegueIcons
import com.quantumff.segue.core.designsystem.model.toBadgeState
import com.quantumff.segue.core.designsystem.preview.PreviewSegueThemes
import com.quantumff.segue.core.designsystem.preview.SeguePreview
import com.quantumff.segue.core.designsystem.theme.SegueShapes
import com.quantumff.segue.core.designsystem.theme.SegueSizes
import com.quantumff.segue.core.designsystem.theme.SegueSpacing
import com.quantumff.segue.core.model.DownloadStatus
import com.quantumff.segue.core.model.PinKind
import com.quantumff.segue.core.model.PinnedDownload
import kotlin.math.roundToInt
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/** What the Downloads screen can do. Built once by the stateful wrappers. */
@Immutable
class DownloadsActions(
    val onPinClick: (PinnedDownload) -> Unit,
    val onPause: (PinnedDownload) -> Unit,
    val onResume: (PinnedDownload) -> Unit,
    val onRemove: (PinnedDownload) -> Unit,
    val onRemoveAll: () -> Unit,
    val onForceOfflineChange: (Boolean) -> Unit,
) {
    companion object {
        val None = DownloadsActions({}, {}, {}, {}, {}, {})
    }
}

/** Stateful Downloads screen, pushed from Settings > Storage. */
@Composable
fun DownloadsScreenRoute(
    onBackClick: () -> Unit,
    onOpen: (PinKind, String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DownloadsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val actions = rememberDownloadsActions(viewModel, onOpen)
    DownloadsScreen(state = state, actions = actions, onBackClick = onBackClick, modifier = modifier)
}

/** Stateful Downloads content without a top bar, for the Library's Downloads tab. */
@Composable
fun DownloadsTab(
    onOpen: (PinKind, String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DownloadsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val actions = rememberDownloadsActions(viewModel, onOpen)
    DownloadsContent(state = state, actions = actions, modifier = modifier, showRemoveAll = true)
}

@Composable
private fun rememberDownloadsActions(viewModel: DownloadsViewModel, onOpen: (PinKind, String) -> Unit) =
    remember(viewModel, onOpen) {
        DownloadsActions(
            onPinClick = { onOpen(it.kind, it.id) },
            onPause = viewModel::pause,
            onResume = viewModel::resume,
            onRemove = viewModel::remove,
            onRemoveAll = viewModel::removeAll,
            onForceOfflineChange = viewModel::setForceOffline,
        )
    }

/** Downloads (DESIGN.md §4.9): a secondary screen with a top bar. */
@Composable
fun DownloadsScreen(
    state: DownloadsUiState,
    actions: DownloadsActions,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmRemoveAll by rememberSaveable { mutableStateOf(false) }
    Column(modifier.fillMaxSize()) {
        SubScreenTopBar(title = stringResource(R.string.feature_downloads_title), onBackClick = onBackClick) {
            if (!state.isEmpty) {
                SegueIconButton(
                    icon = SegueIcons.Remove,
                    contentDescription = stringResource(R.string.feature_downloads_remove_all),
                    onClick = { confirmRemoveAll = true },
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        DownloadsContent(state = state, actions = actions, modifier = Modifier.weight(1f), showRemoveAll = false)
    }
    if (confirmRemoveAll) {
        RemoveAllDialog(onConfirm = actions.onRemoveAll, onDismissRequest = { confirmRemoveAll = false })
    }
}

/**
 * The storage bar and offline switch, then Downloading, Failed and Downloaded sections. With
 * [showRemoveAll] a "Remove all" row ends the list (the Library tab has no top bar for it).
 */
@Composable
fun DownloadsContent(
    state: DownloadsUiState,
    actions: DownloadsActions,
    modifier: Modifier = Modifier,
    showRemoveAll: Boolean = false,
) {
    var menuFor by remember { mutableStateOf<PinnedDownload?>(null) }
    var confirmRemoveAll by rememberSaveable { mutableStateOf(false) }
    val bottom = LocalShellContentPadding.current.calculateBottomPadding()
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottom)) {
        item(key = "storage", contentType = "storage") {
            StorageBar(state.usedBytes, state.freeBytes, state.storageLimit, state.storageFraction)
        }
        if (state.limitReached) {
            item(key = "limit", contentType = "limit") { StorageLimitBanner(state.storageLimit) }
        }
        item(key = "offline", contentType = "switch") {
            OfflineModeRow(state.forceOffline, state.isOffline, actions.onForceOfflineChange)
        }
        when {
            state.isLoading -> Unit

            state.isEmpty -> item(key = "empty", contentType = "empty") {
                EmptyState(
                    icon = SegueIcons.Download,
                    title = stringResource(R.string.feature_downloads_empty_title),
                    message = stringResource(R.string.feature_downloads_empty_message),
                    modifier = Modifier.padding(top = SegueSpacing.xxxl),
                )
            }

            else -> {
                pinSection("active", R.string.feature_downloads_section_active, state.active) { pin ->
                    ActiveRow(pin, actions)
                }
                pinSection("failed", R.string.feature_downloads_section_failed, state.failed) { pin ->
                    FailedRow(pin, actions)
                }
                pinSection("downloaded", R.string.feature_downloads_section_downloaded, state.downloaded) { pin ->
                    MediaRow(
                        title = pin.name,
                        subtitle = pinSummary(pin),
                        coverArtId = pin.coverArtId,
                        onClick = { actions.onPinClick(pin) },
                        onLongClick = { menuFor = pin },
                        onMoreClick = { menuFor = pin },
                        artShape = if (pin.kind == PinKind.Artist) SegueShapes.full else SegueShapes.sm,
                        placeholderIcon = pin.kind.icon,
                        download = pin.status.toBadgeState(),
                    )
                }
                if (showRemoveAll) {
                    item(key = "removeAll", contentType = "button") {
                        TextButton(
                            onClick = { confirmRemoveAll = true },
                            modifier = Modifier.padding(horizontal = SegueSpacing.sm, vertical = SegueSpacing.md),
                        ) {
                            Text(
                                text = stringResource(R.string.feature_downloads_remove_all),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }
    }
    menuFor?.let { pin ->
        MediaContextMenu(
            header = ContextMenuHeader(
                title = pin.name,
                subtitle = pinSummary(pin),
                coverArtId = pin.coverArtId,
                circularArt = pin.kind == PinKind.Artist,
            ),
            actions = persistentListOf(MediaAction.RemoveDownload),
            onAction = { actions.onRemove(pin) },
            onDismissRequest = { menuFor = null },
        )
    }
    if (confirmRemoveAll) {
        RemoveAllDialog(onConfirm = actions.onRemoveAll, onDismissRequest = { confirmRemoveAll = false })
    }
}

private fun LazyListScope.pinSection(
    key: String,
    title: Int,
    pins: ImmutableList<PinnedDownload>,
    row: @Composable (PinnedDownload) -> Unit,
) {
    if (pins.isEmpty()) return
    item(key = "$key-header", contentType = "sectionHeader") {
        SectionHeader(title = stringResource(title))
    }
    items(pins, key = { "$key-${it.kind}-${it.id}" }, contentType = { "$key-row" }) { row(it) }
}

/** "3.2 GB used · limit 10 GB" (or "· 20 GB free" without a limit) over an 8-tall bar. */
@Composable
private fun StorageBar(usedBytes: Long, freeBytes: Long, limit: StorageLimit, fraction: Float) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SegueSpacing.lg, vertical = SegueSpacing.md),
        verticalArrangement = Arrangement.spacedBy(SegueSpacing.sm),
    ) {
        val used = Formatter.formatShortFileSize(context, usedBytes)
        val limitBytes = limit.bytes
        Text(
            text = if (limitBytes == null) {
                stringResource(R.string.feature_downloads_storage, used, Formatter.formatShortFileSize(context, freeBytes))
            } else {
                stringResource(R.string.feature_downloads_storage_limit, used, Formatter.formatShortFileSize(context, limitBytes))
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(SegueSpacing.sm),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            gapSize = SegueSpacing.none,
            drawStopIndicator = {},
        )
    }
}

/** The storage limit is full: say why downloads stopped and how to continue (DESIGN.md §5.8 banner style). */
@Composable
private fun StorageLimitBanner(limit: StorageLimit) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SegueSpacing.lg, vertical = SegueSpacing.xs)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest, SegueShapes.lg)
            .padding(SegueSpacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = SegueIcons.Error,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(SegueSizes.icon),
        )
        Spacer(Modifier.width(SegueSpacing.md))
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(
                    R.string.feature_downloads_limit_reached_title,
                    limit.bytes?.let { Formatter.formatShortFileSize(context, it) }.orEmpty(),
                ),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.feature_downloads_limit_reached_message),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun OfflineModeRow(forceOffline: Boolean, isOffline: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(R.string.feature_downloads_offline_mode)) },
        supportingContent = {
            Text(
                stringResource(
                    when {
                        forceOffline -> R.string.feature_downloads_offline_mode_on
                        isOffline -> R.string.feature_downloads_offline_no_network
                        else -> R.string.feature_downloads_offline_mode_off
                    },
                ),
            )
        },
        trailingContent = { Switch(checked = forceOffline, onCheckedChange = null) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
        modifier = Modifier.toggleable(value = forceOffline, role = Role.Switch, onValueChange = onChange),
    )
}

/** Art, title, "12 of 20 songs · 45%" and a 2dp bar under the text; pause or resume, and cancel. */
@Composable
private fun ActiveRow(pin: PinnedDownload, actions: DownloadsActions) {
    val paused = pin.status == DownloadStatus.Paused
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(SegueSizes.mediaRow)
            .padding(start = SegueSpacing.lg, end = SegueSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverArt(
            coverArtId = pin.coverArtId,
            displaySize = SegueSizes.mediaRowArt,
            shape = if (pin.kind == PinKind.Artist) SegueShapes.full else SegueShapes.sm,
            modifier = Modifier.size(SegueSizes.mediaRowArt),
            placeholderIcon = pin.kind.icon,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = SegueSpacing.md),
            verticalArrangement = Arrangement.spacedBy(SegueSpacing.xxs),
        ) {
            Text(
                text = pin.name,
                style = MaterialTheme.typography.titleSmall,
                // Explicit: the Library tab has no Surface to provide a content color.
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = activeSummary(pin),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            LinearProgressIndicator(
                progress = { pin.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(SegueSpacing.xxs),
                color = if (paused) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.outlineVariant,
                gapSize = SegueSpacing.none,
                drawStopIndicator = {},
            )
        }
        SegueIconButton(
            icon = if (paused) SegueIcons.Play else SegueIcons.Pause,
            contentDescription = stringResource(
                if (paused) R.string.feature_downloads_resume else R.string.feature_downloads_pause,
            ),
            onClick = { if (paused) actions.onResume(pin) else actions.onPause(pin) },
        )
        SegueIconButton(
            icon = SegueIcons.Close,
            contentDescription = stringResource(R.string.feature_downloads_cancel),
            onClick = { actions.onRemove(pin) },
        )
    }
}

@Composable
private fun FailedRow(pin: PinnedDownload, actions: DownloadsActions) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        MediaRow(
            title = pin.name,
            subtitle = pluralStringResource(R.plurals.feature_downloads_failed_songs, pin.failed, pin.failed),
            coverArtId = pin.coverArtId,
            onClick = { actions.onPinClick(pin) },
            modifier = Modifier.weight(1f),
            placeholderIcon = pin.kind.icon,
        )
        SegueIconButton(
            icon = SegueIcons.Refresh,
            contentDescription = stringResource(R.string.feature_downloads_retry),
            onClick = { actions.onResume(pin) },
            tint = MaterialTheme.colorScheme.error,
        )
        SegueIconButton(
            icon = SegueIcons.Close,
            contentDescription = stringResource(R.string.feature_downloads_cancel),
            onClick = { actions.onRemove(pin) },
        )
        Spacer(Modifier.width(SegueSpacing.xs))
    }
}

@Composable
private fun RemoveAllDialog(onConfirm: () -> Unit, onDismissRequest: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(R.string.feature_downloads_remove_all_title)) },
        text = { Text(stringResource(R.string.feature_downloads_remove_all_message)) },
        confirmButton = {
            TextButton(onClick = {
                onConfirm()
                onDismissRequest()
            }) {
                Text(stringResource(R.string.feature_downloads_remove_all), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) { Text(stringResource(R.string.feature_downloads_keep)) }
        },
    )
}

/** "Album · 12 songs · 340 MB". */
@Composable
private fun pinSummary(pin: PinnedDownload): String {
    val context = LocalContext.current
    return listOf(
        stringResource(pin.kind.label),
        pluralStringResource(R.plurals.feature_downloads_songs, pin.songCount, pin.songCount),
        Formatter.formatShortFileSize(context, pin.bytes),
    ).joinToString(" · ")
}

/** "12 of 20 songs · 45%", or "Paused · 12 of 20 songs". */
@Composable
private fun activeSummary(pin: PinnedDownload): String {
    val count = stringResource(R.string.feature_downloads_progress, pin.completed, pin.songCount)
    return when (pin.status) {
        DownloadStatus.Paused -> stringResource(R.string.feature_downloads_paused, count)
        DownloadStatus.Queued -> stringResource(R.string.feature_downloads_queued, count)
        else -> "$count · ${(pin.progress * PERCENT).roundToInt()}%"
    }
}

private val PinKind.label: Int
    get() = when (this) {
        PinKind.Album -> R.string.feature_downloads_kind_album
        PinKind.Playlist -> R.string.feature_downloads_kind_playlist
        PinKind.Artist -> R.string.feature_downloads_kind_artist
        PinKind.Song -> R.string.feature_downloads_kind_song
    }

private val PinKind.icon
    get() = when (this) {
        PinKind.Album, PinKind.Song -> SegueIcons.Disc3
        PinKind.Playlist -> SegueIcons.Playlists
        PinKind.Artist -> SegueIcons.Artist
    }

private const val PERCENT = 100

internal val PreviewDownloadsState = DownloadsUiState(
    isLoading = false,
    active = persistentListOf(
        PinnedDownload(PinKind.Album, "a1", "In Rainbows", null, 10, 4, 0, 0, 0.45f, 42_000_000),
        PinnedDownload(PinKind.Playlist, "p1", "Road trip", null, 24, 3, 0, 21, 0.12f, 30_000_000),
    ),
    failed = persistentListOf(PinnedDownload(PinKind.Album, "a2", "Kid A", null, 11, 9, 2, 0, 0.8f, 80_000_000)),
    downloaded = persistentListOf(
        PinnedDownload(PinKind.Album, "a3", "OK Computer", null, 12, 12, 0, 0, 1f, 120_000_000),
        PinnedDownload(PinKind.Artist, "ar1", "Radiohead", null, 64, 64, 0, 0, 1f, 640_000_000),
    ),
    usedBytes = 912_000_000,
    freeBytes = 21_000_000_000,
)

@PreviewSegueThemes
@Composable
private fun DownloadsScreenPreview() {
    SeguePreview { DownloadsScreen(PreviewDownloadsState, DownloadsActions.None, onBackClick = {}) }
}

@PreviewSegueThemes
@Composable
private fun DownloadsEmptyPreview() {
    SeguePreview { DownloadsScreen(DownloadsUiState(isLoading = false), DownloadsActions.None, onBackClick = {}) }
}
