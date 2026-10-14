package com.quantumff.segue.feature.playlist

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.quantumff.segue.core.designsystem.component.AccountAction
import com.quantumff.segue.core.designsystem.component.PlaceholderScreen
import com.quantumff.segue.core.designsystem.component.SegueIconButton
import com.quantumff.segue.core.designsystem.component.TopLevelTopBar
import com.quantumff.segue.core.designsystem.icon.SegueIcons
import com.quantumff.segue.core.designsystem.preview.PreviewSegueThemes
import com.quantumff.segue.core.designsystem.preview.SeguePreview

/** The Playlists tab. Placeholder; rows follow docs/DESIGN.md §4.2 (Playlists). */
@Composable
fun PlaylistsScreen(onAccountClick: () -> Unit, onPlaylistClick: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize()) {
        TopLevelTopBar(title = stringResource(R.string.feature_playlist_list_title)) {
            SegueIconButton(
                icon = SegueIcons.Plus,
                contentDescription = stringResource(R.string.feature_playlist_create),
                onClick = {},
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AccountAction(serverName = null, onClick = onAccountClick)
        }
        PlaceholderScreen(
            title = stringResource(R.string.feature_playlist_list_placeholder),
            modifier = Modifier.weight(1f),
        ) {
            TextButton(onClick = { onPlaylistClick(SAMPLE_PLAYLIST_ID) }) {
                Text(stringResource(R.string.feature_playlist_open_sample))
            }
        }
    }
}

/** Lets the shell's back stacks be exercised before real data exists. */
private const val SAMPLE_PLAYLIST_ID = "sample-playlist"

@PreviewSegueThemes
@Composable
private fun PlaylistsScreenPreview() {
    SeguePreview { PlaylistsScreen(onAccountClick = {}, onPlaylistClick = {}) }
}
