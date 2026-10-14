package com.quantumff.segue.feature.queue

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.quantumff.segue.core.designsystem.component.PlaceholderScreen
import com.quantumff.segue.core.designsystem.component.ShuffleModeControl
import com.quantumff.segue.core.designsystem.component.SubScreenTopBar
import com.quantumff.segue.core.designsystem.preview.PreviewSegueThemes
import com.quantumff.segue.core.designsystem.preview.SeguePreview
import com.quantumff.segue.core.designsystem.theme.SegueSpacing
import com.quantumff.segue.core.model.ShuffleMode

/** Placeholder. Real UI follows docs/DESIGN.md §4.8. Shuffle mode moves to the player with playback. */
@Composable
fun QueueScreen(onBackClick: () -> Unit, modifier: Modifier = Modifier) {
    var shuffleMode by rememberSaveable { mutableStateOf(ShuffleMode.Off) }
    Column(modifier.fillMaxSize()) {
        SubScreenTopBar(title = stringResource(R.string.feature_queue_title), onBackClick = onBackClick)
        ShuffleModeControl(
            selected = shuffleMode,
            onSelect = { shuffleMode = it },
            modifier = Modifier.padding(horizontal = SegueSpacing.lg),
        )
        PlaceholderScreen(title = stringResource(R.string.feature_queue_placeholder), modifier = Modifier.weight(1f))
    }
}

@PreviewSegueThemes
@Composable
private fun QueueScreenPreview() {
    SeguePreview { QueueScreen(onBackClick = {}) }
}
