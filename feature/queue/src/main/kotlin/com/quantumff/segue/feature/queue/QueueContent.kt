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
import androidx.compose.runtime.rememberCoroutineScope
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
import com.quantumff.segue.core.designsystem.component.TrackRowState
import com.quantumff.segue.core.designsystem.component.rememberDelayedVisibility
import com.quantumff.segue.core.designsystem.icon.SegueIcons
import com.quantumff.segue.core.designsystem.preview.PreviewSegueThemes
import com.quantumff.segue.core.designsystem.preview.SeguePreview
import com.quantumff.segue.core.designsystem.theme.SegueSpacing
import com.quantumff.segue.core.designsystem.util.formatDuration
import com.quantumff.segue.core.model.RemoteQueueOffer
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/** What the queue list can ask for. Grouped so the list takes one stable parameter. */
@Immutable
data class QueueActions(
    val onTrackClick: (QueueListItem.Track) -> Unit = {},
    val onMove: (QueueMove) -> Unit = {},
    val onPlayNext: (QueueListItem.Track) -> Unit = {},
    val onRemove: (QueueListItem.Track) -> Unit = {},
    val onHistoryClick: () -> Unit = {},
    val onContinueFromDevice: () -> Unit = {},
    val onDismissDevice: () -> Unit = {},
)

internal fun QueueViewModel.actions() = QueueActions(
    onTrackClick = ::skipTo,
    onMove = ::move,
    onPlayNext = ::playNext,
    onRemove = ::remove,
    onHistoryClick = ::toggleHistory,
    onContinueFromDevice = ::continueFromOtherDevice,
    onDismissDevice = ::dismissOtherDevice,
)

/**
 * The queue (DESIGN.md §4.8), ready to embed in Now Playing's Queue panel: the header (jump to
 * current, upload, download), the "Continue from <device>" banner, and the reorderable list,
 * with its own snackbar unless [snackbarHostState] is given (then the host shows it).
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
    val headerActions = remember(viewModel) { viewModel.headerActions() }
    QueueMessages(state.message, hostState, onUndo = viewModel::undoRemove, onShow = viewModel::onMessageShown)
    Box(modifier) {
        QueueContent(state = state, actions = actions, headerActions = headerActions, contentPadding = contentPadding)
        if (snackbarHostState == null) SegueSnackbarHost(hostState, Modifier.align(Alignment.BottomCenter))
    }
}

/** Stateless queue: see the stateful overload. */
@Composable
fun QueueContent(
    state: QueueUiState,
    actions: QueueActions,
    modifier: Modifier = Modifier,
    headerActions: QueueHeaderActions = QueueHeaderActions(),
    listState: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(),
) {
    val scope = rememberCoroutineScope()
    val currentVisible by rememberCurrentTrackVisible(listState, state.currentKey)
    Column(modifier.fillMaxSize()) {
        QueueHeader(
            state = state,
            actions = headerActions,
            showJumpToCurrent = !currentVisible,
            onJumpToCurrent = {
                val key = state.currentKey
                val index = if (key ==
                    null
                ) {
                    -1
                } else {
                    queueIndexOf(state.items, key, hasBanner = state.remoteOffer != null)
                }
                if (index >= 0) scope.launch { listState.animateScrollToItem((index - 1).coerceAtLeast(0)) }
            },
        )
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
            ReorderableQueueList(state, actions, listState, contentPadding)
        }
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
private fun ReorderableQueueList(
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

    /** The reorder gesture for the row [key]: on its grip handle, or after a long press anywhere. */
    fun ReorderableCollectionItemScope.dragModifier(key: Any, longPress: Boolean = false): Modifier = if (longPress) {
        Modifier.longPressDraggableHandle(onDragStarted = { startDrag(key) }, onDragStopped = ::endDrag)
    } else {
        Modifier.draggableHandle(onDragStarted = { startDrag(key) }, onDragStopped = ::endDrag)
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
                            modifier = dragModifier(row.key, longPress = true),
                            handleModifier = dragModifier(row.key),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Keeps the current track in view, with the row above it: on first show, and when the track
 * changes while the previous current track was on screen (so browsing further down is not
 * interrupted). A newly offered queue from another device scrolls its banner into view.
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
    LaunchedEffect(state.currentKey) {
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

/** True while the current track's row is on screen (or there is none): hides jump to current. */
@Composable
private fun rememberCurrentTrackVisible(listState: LazyListState, currentKey: String?): State<Boolean> =
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

                QueueMessage.Uploaded -> resources.getString(R.string.feature_queue_uploaded)

                QueueMessage.UploadFailed -> resources.getString(R.string.feature_queue_upload_failed)

                is QueueMessage.Downloaded -> resources.getQuantityString(
                    R.plurals.feature_queue_downloaded,
                    shown.trackCount,
                    shown.trackCount,
                )

                QueueMessage.NothingToDownload -> resources.getString(R.string.feature_queue_nothing_to_download)

                QueueMessage.DownloadFailed -> resources.getString(R.string.feature_queue_download_failed)
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
        QueueListItem.Track(
            key = "s1#1",
            position = 3,
            row = TrackRowState("s1#1", "Nude", "Radiohead", "In Rainbows", 255, isCurrent = true, isPlaying = true),
        ),
        QueueListItem.Track(
            key = "s2#1",
            position = 4,
            row = TrackRowState("s2#1", "Weird Fishes/Arpeggi", "Radiohead", "In Rainbows", 318),
        ),
        QueueListItem.Track(
            key = "s3#1",
            position = 5,
            row = TrackRowState("s3#1", "Everything in Its Right Place", "Radiohead", "Kid A", 251),
        ),
    ),
    currentKey = "s1#1",
    trackCount = 6,
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
