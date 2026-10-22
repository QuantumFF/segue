package com.quantumff.segue.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.quantumff.segue.core.designsystem.theme.SegueSizes
import kotlinx.coroutines.delay

/** How long buffering lasts before the ring shows, so quick rebuffers don't flash it. */
const val BUFFERING_RING_DELAY_MILLIS = 300L

/**
 * Draws an indeterminate ring around a play/pause button while the stream is loading
 * (DESIGN.md §5.5, and §4.7 for Now Playing). The ring shows once [isBuffering] has stayed true for
 * [BUFFERING_RING_DELAY_MILLIS] and goes as soon as it turns false.
 *
 * The ring is centered on [content] at [diameter] and never changes the layout: a ring larger
 * than the button draws outside it, around a filled circle; a smaller one sits inside an icon
 * button's touch target.
 */
@Composable
fun BufferingRing(
    isBuffering: Boolean,
    diameter: Dp,
    modifier: Modifier = Modifier,
    color: Color = LocalContentColor.current,
    content: @Composable () -> Unit,
) {
    var waited by remember { mutableStateOf(false) }
    LaunchedEffect(isBuffering) {
        if (isBuffering) delay(BUFFERING_RING_DELAY_MILLIS)
        waited = isBuffering
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        content()
        // Checks isBuffering too, so the ring goes in the same frame buffering ends.
        if (isBuffering && waited) {
            CircularProgressIndicator(
                color = color,
                strokeWidth = SegueSizes.bufferingRingStroke,
                modifier = Modifier
                    .matchParentSize()
                    .requiredSize(diameter),
            )
        }
    }
}
