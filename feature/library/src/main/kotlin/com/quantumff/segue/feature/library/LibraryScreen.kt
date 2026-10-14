package com.quantumff.segue.feature.library

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

/** Placeholder. Real UI follows docs/DESIGN.md §4.2. */
@Composable
fun LibraryScreen(
    onAccountClick: () -> Unit,
    onArtistClick: (String) -> Unit,
    onDownloadsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        TopLevelTopBar(title = stringResource(R.string.feature_library_title)) {
            val muted = MaterialTheme.colorScheme.onSurfaceVariant
            SegueIconButton(
                icon = SegueIcons.Sort,
                contentDescription = stringResource(R.string.feature_library_sort),
                onClick = {},
                tint = muted,
            )
            SegueIconButton(
                icon = SegueIcons.GridView,
                contentDescription = stringResource(R.string.feature_library_view_mode),
                onClick = {},
                tint = muted,
            )
            AccountAction(serverName = null, onClick = onAccountClick)
        }
        PlaceholderScreen(
            title = stringResource(R.string.feature_library_placeholder),
            modifier = Modifier.weight(1f),
        ) {
            TextButton(onClick = { onArtistClick(SAMPLE_ARTIST_ID) }) {
                Text(stringResource(R.string.feature_library_open_sample_artist))
            }
            TextButton(onClick = onDownloadsClick) {
                Text(stringResource(R.string.feature_library_open_downloads))
            }
        }
    }
}

/** Lets the shell's back stacks be exercised before real data exists. */
private const val SAMPLE_ARTIST_ID = "sample-artist"

@PreviewSegueThemes
@Composable
private fun LibraryScreenPreview() {
    SeguePreview { LibraryScreen(onAccountClick = {}, onArtistClick = {}, onDownloadsClick = {}) }
}
