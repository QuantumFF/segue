package com.quantumff.segue.core.designsystem.component

import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.layout
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import com.quantumff.segue.core.designsystem.theme.SegueSizes
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Gives a control the 48dp minimum touch target (DESIGN.md §2.3) without changing how it is drawn:
 * the layout grows to at least 48x48 and the content keeps its designed size, centred inside.
 *
 * Put it right after the control's `clickable` (or other pointer and semantics modifiers) and
 * before its visual size, e.g. `Modifier.clickable(...).minimumTouchTarget().height(SegueSizes.pill)`.
 * A `clickable` takes the bounds of the next layout modifier inward, so only then are the tap area
 * and the node TalkBack focuses 48dp too; placed before the `clickable`, the node keeps the visual
 * size. A ripple drawn by that `clickable` fills the 48dp slot; to keep it on the drawn shape, give
 * the `clickable` `indication = null` and draw it with `Modifier.indication` after the `clip`.
 *
 * It grows the layout slot, so a 28dp pill takes a 48dp row. Where that would push a layout apart,
 * use [overflowingTouchTarget]. Leave it off controls that only inform.
 */
@Stable
fun Modifier.minimumTouchTarget(): Modifier = minimumInteractiveComponentSize()

/**
 * Like [minimumTouchTarget], but the control keeps its designed slot in the layout: its tap area
 * and the node TalkBack focuses grow to at least 48x48 around the content and overlap the
 * neighbours. Where they overlap, the one on top takes the touch: a neighbour placed after this
 * control, or raised with `Modifier.zIndex`; otherwise this control. Raise a neighbour that must win,
 * such as a seek line. For a short line of text in a dense column, such as a hero's artist link.
 *
 * Pass the control's `clickable` (or other pointer and semantics modifiers) as [input], e.g.
 * `Modifier.fillMaxWidth().overflowingTouchTarget(Modifier.clickable(onClick = onClick))`.
 */
@Stable
fun Modifier.overflowingTouchTarget(input: Modifier): Modifier = this
    .layout { measurable, constraints ->
        // The content's own size, read through the target below, whose intrinsics are the content's.
        val width = if (constraints.hasFixedWidth) {
            constraints.maxWidth
        } else {
            constraints.constrainWidth(measurable.maxIntrinsicWidth(constraints.maxHeight))
        }
        val height = constraints.constrainHeight(measurable.minIntrinsicHeight(width))
        val touch = SegueSizes.touchTarget.roundToPx()
        val placeable = measurable.measure(
            constraints.copy(
                maxWidth = if (constraints.hasBoundedWidth) max(constraints.maxWidth, touch) else Constraints.Infinity,
                maxHeight = if (constraints.hasBoundedHeight) {
                    max(constraints.maxHeight, touch)
                } else {
                    Constraints.Infinity
                },
            ),
        )
        layout(width, height) {
            placeable.place(
                ((width - placeable.width) / 2f).roundToInt(),
                ((height - placeable.height) / 2f).roundToInt(),
            )
        }
    }
    .then(input)
    .then(TouchTargetElement)

/** Grows its content to at least 48x48, centred, while reporting the content's own intrinsics. */
private data object TouchTargetElement : ModifierNodeElement<TouchTargetNode>() {
    override fun create() = TouchTargetNode()

    override fun update(node: TouchTargetNode) = Unit
}

private class TouchTargetNode :
    Modifier.Node(),
    LayoutModifierNode {
    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        val placeable = measurable.measure(constraints)
        val touch = SegueSizes.touchTarget.roundToPx()
        val width = max(placeable.width, touch).coerceAtMost(constraints.maxWidth)
        val height = max(placeable.height, touch).coerceAtMost(constraints.maxHeight)
        return layout(width, height) {
            placeable.place(
                ((width - placeable.width) / 2f).roundToInt(),
                ((height - placeable.height) / 2f).roundToInt(),
            )
        }
    }

    override fun IntrinsicMeasureScope.minIntrinsicWidth(measurable: IntrinsicMeasurable, height: Int) =
        measurable.minIntrinsicWidth(height)

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(measurable: IntrinsicMeasurable, height: Int) =
        measurable.maxIntrinsicWidth(height)

    override fun IntrinsicMeasureScope.minIntrinsicHeight(measurable: IntrinsicMeasurable, width: Int) =
        measurable.minIntrinsicHeight(width)

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(measurable: IntrinsicMeasurable, width: Int) =
        measurable.maxIntrinsicHeight(width)
}
