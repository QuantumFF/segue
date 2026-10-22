package com.quantumff.segue.core.designsystem.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import com.quantumff.segue.core.designsystem.R
import com.quantumff.segue.core.designsystem.theme.SegueShapes
import com.quantumff.segue.core.designsystem.theme.SegueSpacing
import com.quantumff.segue.core.model.PlaybackError
import com.quantumff.segue.core.model.PlaybackErrorKind

/**
 * Snackbars (DESIGN.md §5.7): `inverseSurface`, radius `sm`, placed above the mini player and
 * navigation bar using [LocalShellContentPadding]. Align it to the bottom of the screen.
 * [ActionsSnackbarVisuals] show their actions on a line of their own.
 */
@Composable
fun SegueSnackbarHost(hostState: SnackbarHostState, modifier: Modifier = Modifier) {
    val bottom = LocalShellContentPadding.current.calculateBottomPadding()
    SnackbarHost(
        hostState = hostState,
        modifier = modifier.padding(bottom = bottom + SegueSpacing.sm),
    ) { data ->
        val visuals = data.visuals
        if (visuals is ActionsSnackbarVisuals) {
            ActionsSnackbar(visuals, onDismiss = data::dismiss)
        } else {
            Snackbar(
                snackbarData = data,
                shape = SegueShapes.sm,
                containerColor = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                modifier = Modifier.padding(horizontal = SegueSpacing.lg),
            )
        }
    }
}

/** One action of an [ActionsSnackbarVisuals] snackbar. */
@Immutable
class SnackbarAction(val label: String, val onClick: () -> Unit)

/**
 * A snackbar with more than one action, which Material's single `actionLabel` cannot carry. Each
 * action runs its own [SnackbarAction.onClick] and dismisses the snackbar, so `showSnackbar`
 * returns `Dismissed` whichever is tapped.
 */
@Immutable
class ActionsSnackbarVisuals(
    override val message: String,
    val actions: List<SnackbarAction>,
    override val duration: SnackbarDuration = SnackbarDuration.Long,
) : SnackbarVisuals {
    override val actionLabel: String? get() = null
    override val withDismissAction: Boolean get() = false
}

@Composable
private fun ActionsSnackbar(visuals: ActionsSnackbarVisuals, onDismiss: () -> Unit) {
    Snackbar(
        modifier = Modifier.padding(horizontal = SegueSpacing.lg),
        action = {
            Row {
                for (action in visuals.actions) {
                    TextButton(
                        onClick = {
                            action.onClick()
                            onDismiss()
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.inversePrimary,
                        ),
                    ) { Text(action.label) }
                }
            }
        },
        actionOnNewLine = true,
        shape = SegueShapes.sm,
        containerColor = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
    ) { Text(visuals.message) }
}

/**
 * Shows [error] in [hostState] (DESIGN.md §4.7): "Couldn't play <title>" with the reason, Skip, and
 * Retry for a network error only. Shown once per error, and taken down when the error clears.
 */
@Composable
fun PlaybackErrorSnackbar(
    error: PlaybackError?,
    hostState: SnackbarHostState,
    onSkip: () -> Unit,
    onRetry: () -> Unit,
) {
    val resources = LocalResources.current
    val skip by rememberUpdatedState(onSkip)
    val retry by rememberUpdatedState(onRetry)
    LaunchedEffect(error, hostState) {
        if (error == null) return@LaunchedEffect
        val message = resources.getString(error.kind.messageRes, error.title)
        val actions = buildList {
            add(SnackbarAction(resources.getString(R.string.ds_skip)) { skip() })
            if (error.canRetry) add(SnackbarAction(resources.getString(R.string.ds_retry)) { retry() })
        }
        // Cancelled when the error clears or changes, which takes the snackbar down.
        hostState.showSnackbar(ActionsSnackbarVisuals(message, actions))
    }
}

private val PlaybackErrorKind.messageRes: Int
    get() = when (this) {
        PlaybackErrorKind.Network -> R.string.ds_playback_error_network
        PlaybackErrorKind.Unsupported -> R.string.ds_playback_error_unsupported
        PlaybackErrorKind.NotFound -> R.string.ds_playback_error_not_found
        PlaybackErrorKind.Other -> R.string.ds_playback_error_other
    }
