package com.quantumff.segue.core.designsystem.component

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.quantumff.segue.core.designsystem.theme.SegueShapes
import com.quantumff.segue.core.designsystem.theme.SegueSpacing

/**
 * Snackbars (DESIGN.md §5.7): `inverseSurface`, radius `sm`, placed above the mini player and
 * navigation bar using [LocalShellContentPadding]. Align it to the bottom of the screen.
 */
@Composable
fun SegueSnackbarHost(hostState: SnackbarHostState, modifier: Modifier = Modifier) {
    val bottom = LocalShellContentPadding.current.calculateBottomPadding()
    SnackbarHost(
        hostState = hostState,
        modifier = modifier.padding(bottom = bottom + SegueSpacing.sm),
    ) { data ->
        Snackbar(
            snackbarData = data,
            shape = SegueShapes.sm,
            containerColor = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            modifier = Modifier.padding(horizontal = SegueSpacing.lg),
        )
    }
}
