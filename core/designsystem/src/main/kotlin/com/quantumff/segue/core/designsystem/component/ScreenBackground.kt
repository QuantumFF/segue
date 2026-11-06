package com.quantumff.segue.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The root of every nav destination: fills it with the app background, so the screen hides the one
 * drawn beneath it. During the back gesture the screen behind is drawn from the start, under the
 * screen that is leaving.
 */
@Composable
fun ScreenBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        content()
    }
}
