package com.quantumff.segue.feature.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.quantumff.segue.core.designsystem.component.AccountAction
import com.quantumff.segue.core.designsystem.component.PlaceholderScreen
import com.quantumff.segue.core.designsystem.component.TopLevelTopBar
import com.quantumff.segue.core.designsystem.preview.PreviewSegueThemes
import com.quantumff.segue.core.designsystem.preview.SeguePreview

/** Placeholder. Real UI follows docs/DESIGN.md §4.1. */
@Composable
fun HomeScreen(onAccountClick: () -> Unit, onAlbumClick: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize()) {
        TopLevelTopBar(title = stringResource(R.string.feature_home_title)) {
            AccountAction(serverName = null, onClick = onAccountClick)
        }
        PlaceholderScreen(title = stringResource(R.string.feature_home_placeholder), modifier = Modifier.weight(1f)) {
            TextButton(onClick = { onAlbumClick(SAMPLE_ALBUM_ID) }) {
                Text(stringResource(R.string.feature_home_open_sample_album))
            }
        }
    }
}

/** Lets the shell's back stacks be exercised before real data exists. */
private const val SAMPLE_ALBUM_ID = "sample-album"

@PreviewSegueThemes
@Composable
private fun HomeScreenPreview() {
    SeguePreview { HomeScreen(onAccountClick = {}, onAlbumClick = {}) }
}
