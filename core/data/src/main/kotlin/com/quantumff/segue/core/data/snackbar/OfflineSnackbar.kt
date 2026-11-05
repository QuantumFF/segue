package com.quantumff.segue.core.data.snackbar

import com.quantumff.segue.core.data.R

/**
 * "You're offline": what a screen posts when the user asks it for something only the server has,
 * such as a pull to refresh, while Offline (DESIGN.md §5.8), so the gesture is not silently ignored.
 */
val SnackbarText.Companion.offline: SnackbarText
    get() = SnackbarText.of(R.string.core_data_offline)
