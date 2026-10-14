package com.quantumff.segue.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.quantumff.segue.core.designsystem.theme.SegueMotion

/**
 * Tap plus long-press (with a haptic) that scales the content to 0.97 while pressed (`medium`).
 * The scale is read in the draw phase, so pressing does not recompose the content.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun Modifier.pressableCard(onClick: () -> Unit, onLongClick: (() -> Unit)?): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) SegueMotion.PRESS_SCALE else 1f,
        animationSpec = SegueMotion.medium(),
        label = "pressScale",
    )
    val longClickWithHaptic = onLongClick.withLongPressHaptic()
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .combinedClickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick,
            onLongClick = longClickWithHaptic,
        )
}

/** Tap plus long-press with a haptic and the default ripple. For rows. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun Modifier.rowClickable(enabled: Boolean, onClick: () -> Unit, onLongClick: (() -> Unit)?): Modifier =
    combinedClickable(
        enabled = enabled,
        onClick = onClick,
        onLongClick = onLongClick.withLongPressHaptic(),
    )

/** Wraps a long-press action so it performs the long-press haptic first. */
@Composable
private fun (() -> Unit)?.withLongPressHaptic(): (() -> Unit)? {
    val longClick = this ?: return null
    val haptics = LocalHapticFeedback.current
    return {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        longClick()
    }
}
