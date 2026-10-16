package com.quantumff.segue.core.designsystem.theme

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp

/** Motion tokens, DESIGN.md §2.5. Animate with transform and alpha only. */
object SegueMotion {
    const val SHORT_MS = 100
    const val MEDIUM_MS = 200
    const val LONG_MS = 300
    const val LYRIC_MS = 600

    val Standard = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    val ExpoOut = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

    /** Press states, icon toggles. */
    fun <T> short(): FiniteAnimationSpec<T> = tween(SHORT_MS, easing = Standard)

    /** Fades, image crossfade, chip select, card press scale. */
    fun <T> medium(): FiniteAnimationSpec<T> = tween(MEDIUM_MS, easing = Standard)

    /** Screen transitions, panels, sheets: entering. */
    fun <T> longEnter(): FiniteAnimationSpec<T> = tween(LONG_MS, easing = EmphasizedDecelerate)

    /** Screen transitions, panels, sheets: leaving. */
    fun <T> longExit(): FiniteAnimationSpec<T> = tween(LONG_MS, easing = EmphasizedAccelerate)

    /** Lyric line opacity and scale. */
    fun <T> lyric(): FiniteAnimationSpec<T> = tween(LYRIC_MS, easing = ExpoOut)

    /** Mini player to Now Playing, settle after drag. */
    fun <T> player(): AnimationSpec<T> = spring(dampingRatio = PLAYER_DAMPING, stiffness = PLAYER_STIFFNESS)

    /** Shared-axis X slide distance. */
    val sharedAxisOffset = 30.dp

    /** Card press scale. */
    const val PRESS_SCALE = 0.97f

    private const val PLAYER_DAMPING = 0.85f
    private const val PLAYER_STIFFNESS = 400f
}

/**
 * True when animations should collapse to a `medium` crossfade: the system animator scale is 0,
 * or the user turned on reduce motion.
 */
val LocalReduceMotion = staticCompositionLocalOf { false }

internal fun Context.systemAnimationsDisabled(): Boolean =
    Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
