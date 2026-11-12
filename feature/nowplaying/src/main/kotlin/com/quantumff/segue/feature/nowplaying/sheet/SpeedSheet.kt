package com.quantumff.segue.feature.nowplaying.sheet

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.quantumff.segue.core.designsystem.component.PickerSheet
import com.quantumff.segue.core.model.UserPreferences
import com.quantumff.segue.feature.nowplaying.R
import java.text.NumberFormat
import java.util.Locale
import kotlinx.collections.immutable.toImmutableList

private val SpeedPresets = UserPreferences.PLAYBACK_SPEEDS.toImmutableList()

/**
 * The ⋮ menu's Speed sheet (DESIGN.md §4.7): radio rows of the preset speeds, the current one
 * selected. Choosing one applies it at once and closes the sheet.
 */
@Composable
internal fun SpeedSheet(speed: Float, onSelect: (Float) -> Unit, onDismissRequest: () -> Unit) {
    PickerSheet(
        title = stringResource(R.string.feature_nowplaying_speed_title),
        options = SpeedPresets,
        selected = speed,
        label = { speedLabel(it) },
        onSelect = onSelect,
        onDismissRequest = onDismissRequest,
    )
}

/** "0.75×", "1×", "1.5×": the speed as a multiplier, in the app's locale. */
@Composable
internal fun speedLabel(speed: Float): String = stringResource(
    R.string.feature_nowplaying_speed_value,
    speedMultiplier(speed, LocalConfiguration.current.locales[0]),
)

/** [speed] as [locale] writes numbers, without trailing zeros: "0.75", "1", "1,5". */
internal fun speedMultiplier(speed: Float, locale: Locale): String = NumberFormat.getNumberInstance(locale)
    .apply {
        minimumFractionDigits = 0
        maximumFractionDigits = SPEED_FRACTION_DIGITS
    }
    .format(speed)

/** The presets step by 0.25. */
private const val SPEED_FRACTION_DIGITS = 2
