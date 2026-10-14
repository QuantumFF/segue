package com.quantumff.segue.core.designsystem.component

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.compositionLocalOf

/**
 * Space the app shell's bottom chrome (mini player, navigation bar, system navigation) covers.
 * Content scrolls under the chrome (DESIGN.md §3), so scrolling screens add this to their list
 * `contentPadding`. Provided by the app shell; zero elsewhere.
 */
val LocalShellContentPadding = compositionLocalOf { PaddingValues() }
