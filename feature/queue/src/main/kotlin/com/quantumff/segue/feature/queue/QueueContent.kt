package com.quantumff.segue.feature.queue

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quantumff.segue.core.designsystem.component.ContinueFromDeviceBanner
import com.quantumff.segue.core.designsystem.component.ContinueFromDeviceState
import com.quantumff.segue.core.designsystem.component.EmptyState
import com.quantumff.segue.core.designsystem.component.SegueIconButton
import com.quantumff.segue.core.designsystem.component.SegueSnackbarHost
import com.quantumff.segue.core.designsystem.component.ShuffleModeControl
import com.quantumff.segue.core.designsystem.component.TrackRowState
import com.quantumff.segue.core.designsystem.component.rememberDelayedVisibility
import com.quantumff.segue.core.designsystem.icon.SegueIcons
import com.quantumff.segue.core.designsystem.preview.PreviewSegueThemes
import com.quantumff.segue.core.designsystem.preview.SeguePreview
import com.quantumff.segue.core.designsystem.theme.SegueSpacing
import com.quantumff.segue.core.designsystem.util.formatDuration
import com.quantumff.segue.core.model.RemoteQueueOffer
import com.quantumff.segue.core.model.RepeatMode
import com.quantumff.segue.core.model.ShuffleMode
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/** What the queue list can ask for. Grouped so the list takes one stable parameter. */
@Immutable
data class QueueActions(
    val onTrackClick: (QueueListItem.Track) -> Unit = {},
    val onMove: (QueueMove) -> Unit = {},
    val onPlayNext: (QueueListItem.Track) -> Unit = {},
    val onRemove: (QueueListItem.Track) -> Unit = {},
    val onShuffleModeSelect: (ShuffleMode) -> Unit = {},
    val onRepeatClick: () -> Unit = {},
    val onHistoryClick: () -> Unit = {},
    val onContinueFromDevice: () -> Unit = {},
    val onDismissDevice: () -> Unit = {},
)

internal fun QueueViewModel.actions() = QueueActions(
    onTrackClick = ::skipTo,
    onMove = ::move,
    onPlayNext = ::playNext,
    onRemove = ::remove,
    onShuffleModeSelect = ::setShuffleMode,
    onRepeatClick = ::cycleRepeatMode,
    onHistoryClick = ::toggleHistory,
    onContinueFromDevice = ::continueFromOtherDevice,
    onDismissDevice = ::dismissOtherDevice,
)

/**
 * The queue, ready to embed (for example in Now Playing's Up next panel): shuffle control, the
 * "Continue from <device>" banner, and the reorderable list, with its own undo snackbar unless
 * [snackbarHostState] is given (then the host shows it). Clear and Save as playlist live in the
 * full-screen queue's menu.
 */
@Composable
fun QueueContent(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    snackbarHostState: SnackbarHostState? = null,
    viewModel: QueueViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val hostState = snackbarHostState ?: remember { SnackbarHostState() }
    val actions = remember(viewModel) { viewModel.actions() }
    QueueMessages(state.message, hostState, onUndo = viewModel::undoRemove, onShow = viewModel::onMessageShown)
    Box(modifier) {
        QueueContent(state = state, actions = actions, contentPadding = contentPadding)
        if (snackbarHostState == null) SegueSnackbarHost(hostState, Modifier.align(Alignment.BottomCenter))
    }
}

/** Stateless queue: see the stateful overload. [listState] is hoisted for the top bar's scroll-to-current. */
@Composable
fun QueueContent(
    state: QueueUiState,
    actions: QueueActions,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(),
) {
    Column(modifier.fillMaxSize()) {
        QueueControls(state.shuffleMode, state.repeatMode, actions)
        if (state.items.isEmpty() && state.remoteOffer == null) {
            if (!state.isLoading && rememberDelayedVisibility()) {
                EmptyState(
                    icon = SegueIcons.UpNext,
                    title = stringResource(R.string.feature_queue_empty_title),
                    message = stringResource(R.string.feature_queue_empty_message),
                    modifier = Modifier.padding(contentPadding),
                )
            }
        } else {
            QueueList(state, actions, listState, contentPadding)
        }
    }
}

/** The shuffle segmented control (Off | Tracks | Albums) with the repeat toggle to its right. */
@Composable
private fun QueueControls(shuffleMode: ShuffleMode, repeatMode: RepeatMode, actions: QueueActions) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = SegueSpacing.lg, end = SegueSpacing.xs, top = SegueSpacing.sm, bottom = SegueSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShuffleModeControl(
            selected = shuffleMode,
            onSelect = actions.onShuffleModeSelect,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(SegueSpacing.xs))
        SegueIconButton(
            icon = if (repeatMode == RepeatMode.One) SegueIcons.RepeatOne else SegueIcons.Repeat,
            contentDescription = stringResource(
                when (repeatMode) {
                    RepeatMode.Off -> R.string.feature_queue_repeat_off
                    RepeatMode.All -> R.string.feature_queue_repeat_all
                    RepeatMode.One -> R.string.feature_queue_repeat_one
                },
            ),
            onClick = actions.onRepeatClick,
            tint = if (repeatMode == RepeatMode.Off) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.primary
            },
        )
    }
}

/** A drag in progress: the rows as they were ([origin]) and as dragged so far ([local]). */
private data class DragSession(
    val origin: List<QueueListItem>,
    val local: List<QueueListItem>,
    val key: Any,
    val dropped: Boolean = false,
)

@Composable
private fun QueueList(
    state: QueueUiState,
    actions: QueueActions,
    listState: LazyListState,
    contentPadding: PaddingValues,
) {
    val haptics = LocalHapticFeedback.current
    var drag by remember { mutableStateOf<DragSession?>(null) }
    // After a drop, keep the dragged order on screen until the player reports the new queue.
    LaunchedEffect(state.items) { if (drag?.dropped == true) drag = null }
    val rows = drag?.local ?: state.items
    val latestState by rememberUpdatedState(state)

    val reorderState = rememberReorderableLazyListState(listState, contentPadding) { from, to ->
        val session = drag ?: DragSession(latestState.items, latestState.items, from.key)
        val moved = QueueList.moveLocally(session.local, from.key, to.key)
        if (moved !== session.local) {
            drag = session.copy(local = moved)
            haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
        }
    }
    fun startDrag(key: Any) {
        haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
        drag = DragSession(latestState.items, latestState.items, key)
    }
    fun endDrag() {
        val session = drag ?: return
        val move = QueueList.dropMove(session.origin, session.local, session.key)
        if (move == null) {
            drag = null
        } else {
            drag = session.copy(dropped = true)
            actions.onMove(move)
        }
    }

    AutoScrollToCurrent(listState, state, hasBanner = state.remoteOffer != null, rows = rows, dragging = drag != null)

    LazyColumn(state = listState, contentPadding = contentPadding, modifier = Modifier.fillMaxSize()) {
        state.remoteOffer?.let { offer ->
            item(key = BANNER_KEY, contentType = BANNER_KEY) {
                ContinueFromDeviceBanner(
                    state = offer.toBannerState(),
                    onContinueClick = actions.onContinueFromDevice,
                    onDismissClick = actions.onDismissDevice,
                    modifier = Modifier.padding(horizontal = SegueSpacing.lg, vertical = SegueSpacing.sm),
                )
            }
        }
        items(rows, key = { it.key }, contentType = { it::class }) { row ->
            when (row) {
                is QueueListItem.History -> HistoryRow(
                    history = row,
                    onClick = actions.onHistoryClick,
                    modifier = Modifier.animateItem(),
                )

                is QueueListItem.AlbumHeader -> ReorderableItem(reorderState, key = row.key) { isDragging ->
                    AlbumBlockHeader(
                        header = row,
                        isDragging = isDragging,
                        handleModifier = Modifier.draggableHandle(
                            onDragStarted = { startDrag(row.key) },
                            onDragStopped = { endDrag() },
                        ),
                    )
                }

                is QueueListItem.Track -> ReorderableItem(reorderState, key = row.key) { isDragging ->
                    QueueSwipeBox(
                        onPlayNext = { actions.onPlayNext(row) },
                        onRemove = { actions.onRemove(row) },
                        enabled = !isDragging,
                    ) {
                        QueueTrackRow(
                            state = row.row,
                            isDragging = isDragging,
                            onClick = { actions.onTrackClick(row) },
                            modifier = Modifier.longPressDraggableHandle(
                                onDragStarted = { startDrag(row.key) },
                                onDragStopped = { endDrag() },
                            ),
                            handleModifier = Modifier.draggableHandle(
                                onDragStarted = { startDrag(row.key) },
                                onDragStopped = { endDrag() },
                            ),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Keeps the current track in view, with the row above it (its album header in Albums shuffle):
 * on first show, on a shuffle mode change, and when the track changes while the previous current
 * track was on screen (so browsing further down is not interrupted). A newly offered queue from
 * another device scrolls its banner into view.
 */
@Composable
private fun AutoScrollToCurrent(
    listState: LazyListState,
    state: QueueUiState,
    hasBanner: Boolean,
    rows: List<QueueListItem>,
    dragging: Boolean,
) {
    var previousKey by remember { mutableStateOf<String?>(null) }
    val latestRows by rememberUpdatedState(rows)
    LaunchedEffect(state.currentKey, state.shuffleMode) {
        val key = state.currentKey ?: return@LaunchedEffect
        val previous = previousKey
        previousKey = key
        if (dragging) return@LaunchedEffect
        val visible = listState.layoutInfo.visibleItemsInfo.map { it.key }
        val index = queueIndexOf(latestRows, key, hasBanner)
        if (index < 0 || key in visible.drop(1).dropLast(1)) return@LaunchedEffect
        when {
            previous == null -> listState.scrollToItem((index - 1).coerceAtLeast(0))
            previous in visible -> listState.animateScrollToItem((index - 1).coerceAtLeast(0))
        }
    }
    LaunchedEffect(hasBanner) {
        if (hasBanner && !dragging) listState.animateScrollToItem(0)
    }
}

/** True while the current track's row is on screen (or there is none): hides scroll-to-current. */
@Composable
fun rememberCurrentTrackVisible(listState: LazyListState, currentKey: String?): State<Boolean> =
    remember(listState, currentKey) {
        derivedStateOf { currentKey == null || listState.layoutInfo.visibleItemsInfo.any { it.key == currentKey } }
    }

/** The LazyColumn index of the row with [key], counting the banner. -1 if absent. */
internal fun queueIndexOf(rows: List<QueueListItem>, key: String, hasBanner: Boolean): Int {
    val index = rows.indexOfFirst { it.key == key }
    return if (index < 0) -1 else index + if (hasBanner) 1 else 0
}

/** Undo for removals and the save results, as snackbars. */
@Composable
internal fun QueueMessages(
    message: QueueMessage?,
    hostState: SnackbarHostState,
    onUndo: () -> Unit,
    onShow: () -> Unit,
) {
    val resources = LocalResources.current
    val latestMessage by rememberUpdatedState(message)
    val latestOnUndo by rememberUpdatedState(onUndo)
    val latestOnShown by rememberUpdatedState(onShow)
    LaunchedEffect(hostState) {
        // Clearing the message must not cancel the snackbar on screen; a newer message replaces it.
        snapshotFlow { latestMessage }.filterNotNull().collectLatest { shown ->
            latestOnShown()
            val text = when (shown) {
                is QueueMessage.Removed -> resources.getString(R.string.feature_queue_removed, shown.title)
                is QueueMessage.SavedAsPlaylist -> resources.getString(R.string.feature_queue_saved, shown.name)
                QueueMessage.SaveFailed -> resources.getString(R.string.feature_queue_save_failed)
            }
            val undoable = shown is QueueMessage.Removed
            val result = hostState.showSnackbar(
                message = text,
                actionLabel = if (undoable) resources.getString(R.string.feature_queue_undo) else null,
                duration = if (undoable) SnackbarDuration.Long else SnackbarDuration.Short,
            )
            if (undoable && result == SnackbarResult.ActionPerformed) latestOnUndo()
        }
    }
}

/**
 * "Nude · 2:31 · 5 min ago". The age compares the server's save time with this device's clock;
 * under a minute, or in the future (a skewed clock), it reads "Just now". Display only: sync
 * decisions never use the device clock.
 */
@Composable
private fun RemoteQueueOffer.toBannerState(): ContinueFromDeviceState {
    val now = System.currentTimeMillis()
    val justNow = stringResource(R.string.feature_queue_just_now)
    val detail = listOfNotNull(
        currentTitle,
        formatDuration(positionMillis / MILLIS_PER_SECOND).takeIf { positionMillis > 0 },
        changedAt?.let {
            if (now - it < DateUtils.MINUTE_IN_MILLIS) {
                justNow
            } else {
                DateUtils.getRelativeTimeSpanString(it, now, DateUtils.MINUTE_IN_MILLIS).toString()
            }
        },
    ).joinToString(" · ")
    return ContinueFromDeviceState(deviceName = deviceName, detail = detail)
}

private const val BANNER_KEY = "banner"
private const val MILLIS_PER_SECOND = 1_000L

internal val PreviewQueue = QueueUiState(
    items = persistentListOf(
        QueueListItem.History(count = 3, expanded = false),
        QueueListItem.AlbumHeader("header:a#1", "In Rainbows", "Radiohead", null, 3),
        QueueListItem.Track(
            key = "s1#1",
            playPosition = 3,
            timelineIndex = 5,
            row = TrackRowState("s1#1", "Nude", "Radiohead", null, 255, isCurrent = true, isPlaying = true),
            blockKey = "a#1",
        ),
        QueueListItem.Track(
            key = "s2#1",
            playPosition = 4,
            timelineIndex = 6,
            row = TrackRowState("s2#1", "Weird Fishes/Arpeggi", "Radiohead", null, 318),
            blockKey = "a#1",
        ),
        QueueListItem.AlbumHeader("header:b#1", "Kid A", "Radiohead", null, 1),
        QueueListItem.Track(
            key = "s3#1",
            playPosition = 5,
            timelineIndex = 0,
            row = TrackRowState("s3#1", "Everything in Its Right Place", "Radiohead", null, 251),
            blockKey = "b#1",
        ),
    ),
    currentKey = "s1#1",
    trackCount = 6,
    shuffleMode = ShuffleMode.Albums,
    remoteOffer = RemoteQueueOffer("Feishin", 12, "Nude", positionMillis = 151_000, changedAt = null),
    isLoading = false,
)

@PreviewSegueThemes
@Composable
private fun QueueContentPreview() {
    SeguePreview { QueueContent(state = PreviewQueue, actions = QueueActions()) }
}

@PreviewSegueThemes
@Composable
private fun QueueContentEmptyPreview() {
    SeguePreview { QueueContent(state = QueueUiState(isLoading = false), actions = QueueActions()) }
}
