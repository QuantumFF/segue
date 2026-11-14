package com.quantumff.segue.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * How far the app shell lifts its snackbar host (DESIGN.md §5.7) above its usual place, so that
 * snackbars clear something a screen floats at its bottom, like [FloatingAlbumPlayButtons]. Screens
 * ask for a lift with [LiftShellSnackbars]; the highest one asked for wins.
 */
@Stable
class ShellSnackbarLift {
    private val requests = mutableStateMapOf<Any, Dp>()

    /** The lift to add under the shell's snackbars: 0 while no screen asks for one. */
    val bottom: Dp get() = requests.values.maxOrNull() ?: 0.dp

    internal fun request(owner: Any, bottom: Dp) {
        requests[owner] = bottom
    }

    internal fun release(owner: Any) {
        requests.remove(owner)
    }
}

/** The shell's [ShellSnackbarLift]. Provided by the app shell; null elsewhere. */
val LocalShellSnackbarLift = staticCompositionLocalOf<ShellSnackbarLift?> { null }

/**
 * Lifts the shell's snackbars by [bottom] while this is in the composition. Does nothing outside
 * the app shell. A screen's own snackbar host adds the same inset itself.
 */
@Composable
fun LiftShellSnackbars(bottom: Dp) {
    val lift = LocalShellSnackbarLift.current ?: return
    val owner = remember { Any() }
    DisposableEffect(lift, owner, bottom) {
        lift.request(owner, bottom)
        onDispose { lift.release(owner) }
    }
}
